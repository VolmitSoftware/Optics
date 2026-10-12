package art.arcane.optics.demo;

import art.arcane.optics.demo.scene.DemoScene;
import art.arcane.optics.demo.scene.DemoScenes;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class OpticsDemoPlugin extends JavaPlugin implements Listener {
    private static final String WORLD_NAME = "optics_demo_stage";
    private static final int MAX_SESSIONS = 4;
    private static final int LANE_SPACING = 96;
    private static final List<String> ACTIONS = List.of("list", "setup", "preview", "play", "stop", "status");

    private final Map<UUID, DemoSession> sessions = new HashMap<>(MAX_SESSIONS);
    private final Map<UUID, Integer> lanes = new HashMap<>(MAX_SESSIONS);
    private final boolean[] preparedLanes = new boolean[MAX_SESSIONS];
    private World stage;
    private BukkitTask ticker;
    private DemoClientView clientView;

    @Override
    public void onEnable() {
        PluginCommand command = Objects.requireNonNull(getCommand("opticsdemo"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        clientView = new DemoClientView(this);
        ticker = getServer().getScheduler().runTaskTimer(this, this::tickSessions, 1L, 1L);
        getLogger().info("Optics demo ready: " + DemoScenes.all().size() + " scenes, renderers=block-packets,clientview-native");
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.cancel();
        }
        List<DemoSession> closing = new ArrayList<>(sessions.values());
        sessions.clear();
        lanes.clear();
        for (DemoSession session : closing) {
            try {
                session.close(true);
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Could not restore an Optics demo session during shutdown", exception);
            }
        }
        if (clientView != null) {
            clientView.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!sender.hasPermission("opticsdemo.use")) {
            sender.sendMessage("Permission required: opticsdemo.use");
            return true;
        }
        if (arguments.length == 0) {
            sender.sendMessage("/opticsdemo list|setup [player]|preview <scene> [player]|play <scene> [player]|stop [player]|status [player]");
            return true;
        }
        try {
            execute(sender, arguments);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(exception.getMessage());
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Optics demo command failed: " + String.join(" ", arguments), exception);
            sender.sendMessage("Optics demo failed; see the server log.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (!sender.hasPermission("opticsdemo.use")) {
            return List.of();
        }
        List<String> choices = new ArrayList<>();
        if (arguments.length == 1) {
            choices.addAll(ACTIONS);
        } else if (arguments.length == 2 && (arguments[0].equalsIgnoreCase("play") || arguments[0].equalsIgnoreCase("preview"))) {
            for (DemoScene scene : DemoScenes.all()) {
                choices.add(scene.id());
            }
        } else if (sender.hasPermission("opticsdemo.others")) {
            for (Player player : getServer().getOnlinePlayers()) {
                choices.add(player.getName());
            }
        }
        String prefix = arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
        choices.removeIf(choice -> !choice.toLowerCase(Locale.ROOT).startsWith(prefix));
        return choices;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        DemoSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.isClosed() || event.getTo() == null) {
            return;
        }
        Location camera = session.camera();
        if (!DemoSession.cameraMatches(camera, event.getTo())) {
            event.setTo(camera);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (stage != null && event.getPlayer().getWorld() != stage) {
            stop(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && sessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld() == stage) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() == stage) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void execute(CommandSender sender, String[] arguments) {
        String action = arguments[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            for (DemoScene scene : DemoScenes.all()) {
                sender.sendMessage(scene.id() + " | " + scene.durationTicks() + " ticks | " + scene.title() + " | " + scene.description());
            }
            return;
        }
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Unknown action; use /opticsdemo list, setup, preview, play, stop or status.");
        }
        boolean sceneAction = action.equals("play") || action.equals("preview");
        if (sceneAction && arguments.length < 2) {
            throw new IllegalArgumentException("Usage: /opticsdemo " + action + " <scene> [player]");
        }
        int targetIndex = sceneAction ? 2 : 1;
        if (arguments.length > targetIndex + 1) {
            throw new IllegalArgumentException("Too many arguments.");
        }
        Player player = target(sender, arguments, targetIndex);
        switch (action) {
            case "setup" -> sender.sendMessage(setup(player).status());
            case "play", "preview" -> {
                DemoScene scene = DemoScenes.find(arguments[1]).orElseThrow(() -> new IllegalArgumentException("Unknown scene: " + arguments[1]));
                DemoSession session = setup(player);
                try {
                    if (action.equals("play")) {
                        session.play(scene);
                    } else {
                        session.preview(scene);
                    }
                } catch (RuntimeException exception) {
                    stop(player, true);
                    throw exception;
                }
                sender.sendMessage(session.status());
            }
            case "stop" -> {
                stop(player, true);
                sender.sendMessage("player=" + player.getName() + " scene=stopped displays=0");
            }
            case "status" -> {
                DemoSession session = sessions.get(player.getUniqueId());
                sender.sendMessage(session == null ? "player=" + player.getName() + " scene=stopped displays=0" : session.status());
            }
            default -> throw new IllegalArgumentException("Unknown action.");
        }
    }

    DemoClientView clientView() {
        return clientView;
    }

    private Player target(CommandSender sender, String[] arguments, int index) {
        if (arguments.length > index) {
            Player player = getServer().getPlayerExact(arguments[index]);
            if (player == null) {
                throw new IllegalArgumentException("Player must be online: " + arguments[index]);
            }
            if (sender != player && !sender.hasPermission("opticsdemo.others")) {
                throw new IllegalArgumentException("Permission required: opticsdemo.others");
            }
            return player;
        }
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalArgumentException("Console commands require an explicit player name.");
    }

    private DemoSession setup(Player player) {
        DemoSession existing = sessions.get(player.getUniqueId());
        if (existing != null) {
            return existing;
        }
        if (sessions.size() >= MAX_SESSIONS) {
            throw new IllegalArgumentException("All four demo stages are occupied; stop a session first.");
        }
        ensureStage();
        int lane = 0;
        while (lanes.containsValue(lane)) {
            lane++;
        }
        prepareLane(lane);
        Location origin = new Location(stage, lane * LANE_SPACING, 64.0D, 0.0D);
        DemoSession session = new DemoSession(this, new DemoSession.SessionContext(player, origin));
        sessions.put(player.getUniqueId(), session);
        lanes.put(player.getUniqueId(), lane);
        return session;
    }

    private void stop(Player player, boolean returnPlayer) {
        DemoSession session = sessions.remove(player.getUniqueId());
        lanes.remove(player.getUniqueId());
        if (session != null) {
            session.close(returnPlayer);
        }
    }

    private void tickSessions() {
        Iterator<Map.Entry<UUID, DemoSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DemoSession> entry = iterator.next();
            try {
                entry.getValue().tick();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Optics demo scene failed for " + entry.getKey(), exception);
                iterator.remove();
                lanes.remove(entry.getKey());
                entry.getValue().close(true);
            }
        }
    }

    private void ensureStage() {
        if (stage != null) {
            return;
        }
        String ownedId = getConfig().getString("stage-world-id", "");
        File worldFolder = new File(getServer().getWorldContainer(), WORLD_NAME);
        if (worldFolder.exists() && ownedId.isEmpty()) {
            throw new IllegalStateException("Existing " + WORLD_NAME + " world is not owned by OpticsDemo");
        }
        World world = new WorldCreator(WORLD_NAME).seed(1372301L).generator(new StageGenerator()).generateStructures(false).createWorld();
        if (world == null) {
            throw new IllegalStateException("Could not create the Optics demo stage world");
        }
        if (!ownedId.isEmpty() && !world.getUID().toString().equals(ownedId)) {
            throw new IllegalStateException("Optics demo stage world identity does not match its saved identity");
        }
        getConfig().set("stage-world-id", world.getUID().toString());
        saveConfig();
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        stage = world;
    }

    private void prepareLane(int lane) {
        if (preparedLanes[lane]) {
            return;
        }
        int center = lane * LANE_SPACING;
        for (int x = -18; x <= 18; x++) {
            for (int z = -12; z <= 18; z++) {
                stage.getBlockAt(center + x, 63, z).setType(Material.POLISHED_DEEPSLATE, false);
            }
        }
        for (int x = -32; x <= 32; x++) {
            for (int y = 64; y <= 86; y++) {
                stage.getBlockAt(center + x, y, -12).setType(Material.BLACK_CONCRETE, false);
            }
        }
        for (int x = -9; x <= 9; x++) {
            stage.getBlockAt(center + x, 63, 0).setType(Material.SMOOTH_QUARTZ, false);
        }
        preparedLanes[lane] = true;
    }

    private static final class StageGenerator extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
