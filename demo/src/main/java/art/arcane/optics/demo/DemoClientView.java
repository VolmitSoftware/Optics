package art.arcane.optics.demo;

import art.arcane.optics.demo.scene.SceneFrame;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.math.BlockBox;
import art.arcane.optics.stream.ViewStreamCapability;
import art.arcane.optics.stream.ViewStreamCodec;
import art.arcane.optics.stream.ViewStreamInbound;
import art.arcane.optics.stream.ViewStreamLimits;
import art.arcane.optics.stream.ViewStreamMessage;
import art.arcane.optics.stream.ViewStreamMessageType;
import art.arcane.optics.stream.ViewStreamOptions;
import art.arcane.optics.stream.ViewStreamPhase;
import art.arcane.optics.stream.ViewStreamPlatform;
import art.arcane.optics.stream.ViewStreamProtocolException;
import art.arcane.optics.stream.ViewStreamSession;
import art.arcane.optics.stream.ViewStreamSessionRegistry;
import art.arcane.optics.stream.ViewStreamSessionState;
import art.arcane.optics.stream.ViewStreamTransport;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class DemoClientView implements Listener, PluginMessageListener {
    private static final String CHANNEL = "wormholes:v10";
    private static final long REQUIRED = ViewStreamCapability.of(ViewStreamCapability.PLATES,
        ViewStreamCapability.MESH_RENDER, ViewStreamCapability.AFFINE_PORTALS);

    private final OpticsDemoPlugin plugin;
    private final Map<UUID, Projection> projections = new HashMap<>(4);

    public DemoClientView(OpticsDemoPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Projection open(Player player, Location origin) {
        Projection projection = new Projection(player, new Vec3d(origin.getX(), origin.getY(), origin.getZ()));
        projections.put(player.getUniqueId(), projection);
        return projection;
    }

    public void close() {
        for (Projection projection : List.copyOf(projections.values())) {
            projection.close();
        }
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        }
    }

    @EventHandler
    public void onChannel(PlayerRegisterChannelEvent event) {
        Projection projection = projections.get(event.getPlayer().getUniqueId());
        if (projection != null && CHANNEL.equals(event.getChannel())) {
            projection.offer();
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] payload) {
        Projection projection = projections.get(player.getUniqueId());
        if (projection == null || !CHANNEL.equals(channel)) {
            return;
        }
        projection.receive(payload);
    }

    public final class Projection implements AutoCloseable {
        private final Player player;
        private final DemoStreamScene scene;
        private final ViewStreamCodec codec = new ViewStreamCodec(List.of());
        private final ViewStreamSessionRegistry<DemoStreamScene, String> registry;
        private final Set<Integer> active = new HashSet<>();
        private final Map<Integer, Set<BlockBox>> acknowledged = new HashMap<>();
        private final Map<Integer, Long> sentRevisions = new HashMap<>();
        private final Set<BlockBox> requiredSections;
        private ViewStreamSession<DemoStreamScene, String> session;
        private long tick;
        private long meshAcks;
        private long meshSections;
        private long sentRevision;
        private boolean offered;
        private boolean closed;

        private Projection(Player player, Vec3d origin) {
            this.player = player;
            scene = new DemoStreamScene(origin);
            requiredSections = scene.requiredSections();
            ViewStreamTransport<DemoStreamScene> transport = new ViewStreamTransport<>() {
                @Override
                public void send(DemoStreamScene observer, byte[] payload) {
                    player.sendPluginMessage(plugin, CHANNEL, payload);
                    record(payload);
                }

                @Override
                public void flush(DemoStreamScene observer) {
                }
            };
            ViewStreamPlatform<DemoStreamScene, String> platform = new ViewStreamPlatform<>(transport, scene, null, scene, null,
                Runnable::run, state -> state, plugin.getServer().getUnsafe().getDataVersion(), REQUIRED
                    | ViewStreamCapability.of(ViewStreamCapability.DEST_LIGHT, ViewStreamCapability.ATMOSPHERE,
                        ViewStreamCapability.LINK_UNCOMPRESSED),
                null, (message, error) -> plugin.getLogger().log(Level.SEVERE, message, error), List.of(), null);
            ViewStreamOptions options = new ViewStreamOptions(true, false, 100, ViewStreamLimits.MIN_MAX_FRAME_BYTES,
                8, false, true, false, false, false, false, false, false, 0, ViewStreamCapability.NONE);
            registry = new ViewStreamSessionRegistry<>(platform, options);
        }

        public void render(SceneFrame frame) {
            scene.update(frame);
        }

        public void tick() {
            if (closed) {
                return;
            }
            offer();
            if (session != null) {
                session.tick(++tick);
            }
        }

        public boolean nativeSelected() {
            return session != null && session.nativeRendererSelected() && (session.caps() & REQUIRED) == REQUIRED;
        }

        public boolean ready() {
            if (!nativeSelected() || active.size() != scene.portalCount()
                || !active.isEmpty() && sentRevision < scene.sampleRevision()) {
                return false;
            }
            for (int key : active) {
                Set<BlockBox> sections = acknowledged.get(key);
                if (sections == null || !sections.containsAll(requiredSections)
                    || sentRevisions.getOrDefault(key, 0L) < scene.sampleRevision()) {
                    return false;
                }
            }
            return true;
        }

        public String status() {
            ViewStreamSessionState state = session == null ? ViewStreamSessionState.VANILLA : session.state();
            return " renderer=" + (nativeSelected() ? "clientview-native" : "block-packets")
                + " streamState=" + state + " streamCaps=" + (session == null ? 0L : session.caps())
                + " ready=" + (!nativeSelected() || ready()) + " nativePortals=" + active.size()
                + " visiblePortals=" + scene.portalCount() + " meshSections=" + meshSections + " meshAcks=" + meshAcks
                + " sampleRevision=" + scene.sampleRevision() + " sentSampleRevision=" + sentRevision;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            registry.runtimeEnabled(false);
            registry.shutdown();
            projections.remove(player.getUniqueId(), this);
            active.clear();
            acknowledged.clear();
            sentRevisions.clear();
        }

        private void offer() {
            if (closed || offered || !player.getListeningPluginChannels().contains(CHANNEL)) {
                return;
            }
            offered = true;
            session = registry.open(player.getUniqueId(), scene, 0L);
            session.brand("wormholes");
            session.offer(ViewStreamPhase.PLAY);
        }

        private void receive(byte[] payload) {
            if (session == null || closed) {
                return;
            }
            try {
                ViewStreamMessage message = codec.decodeC2S(payload);
                if (message instanceof ViewStreamMessage.Hello hello && (hello.clientCaps() & REQUIRED) != REQUIRED) {
                    session.send(new ViewStreamMessage.Decline(ViewStreamMessage.DeclineReason.DISABLED));
                    registry.forget(player.getUniqueId());
                    session = null;
                    return;
                }
                ViewStreamInbound result = session.receive(payload, 0, payload.length);
                if (message instanceof ViewStreamMessage.MeshAck ack && result == ViewStreamInbound.HANDLED) {
                    meshAcks++;
                    acknowledged.computeIfAbsent(ack.portalKey(), key -> new HashSet<>()).add(
                        new BlockBox(ack.sectionX() << 4, ack.sectionY() << 4, ack.sectionZ() << 4, 16, 16, 16));
                }
            } catch (ViewStreamProtocolException failure) {
                plugin.getLogger().log(Level.WARNING, "Invalid Optics demo ClientView payload from " + player.getName(), failure);
            }
        }

        private void record(byte[] payload) {
            int type = payload[0] & 0xFF;
            if (type == ViewStreamMessageType.MESH_SECTION.id()) {
                meshSections++;
                return;
            }
            if (type != ViewStreamMessageType.PORTAL.id() && type != ViewStreamMessageType.PORTAL_DROP.id()
                && type != ViewStreamMessageType.PORTAL_AFFINE.id()) {
                return;
            }
            try {
                ViewStreamMessage message = codec.decodeS2C(payload, ViewStreamCapability.ALL).message();
                if (message instanceof ViewStreamMessage.Portal portal) {
                    active.add(portal.portalKey());
                } else if (message instanceof ViewStreamMessage.PortalDrop drop) {
                    active.remove(drop.portalKey());
                    acknowledged.remove(drop.portalKey());
                    sentRevisions.remove(drop.portalKey());
                } else if (message instanceof ViewStreamMessage.PortalAffine affine) {
                    sentRevision = Math.max(sentRevision, affine.sampleRevision());
                    sentRevisions.put(affine.portalKey(), affine.sampleRevision());
                }
            } catch (ViewStreamProtocolException failure) {
                throw new IllegalStateException("Could not inspect emitted Optics demo frame", failure);
            }
        }
    }
}
