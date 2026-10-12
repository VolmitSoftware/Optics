package art.arcane.optics.demo;

import art.arcane.optics.demo.scene.CameraPose;
import art.arcane.optics.demo.scene.DemoScene;
import art.arcane.optics.demo.scene.SceneFrame;
import art.arcane.optics.math.Vec3d;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class DemoSession {
    private final OpticsDemoPlugin plugin;
    private final Player player;
    private final Location origin;
    private final Location returnLocation;
    private final GameMode returnMode;
    private final boolean returnAllowFlight;
    private final boolean returnFlying;
    private final boolean returnGravity;
    private final boolean returnInvulnerable;
    private final long returnTime;
    private final boolean returnTimeRelative;
    private final WeatherType returnWeather;
    private final DemoSceneRenderer renderer;
    private DemoClientView.Projection projection;
    private SceneFrame frame;
    private boolean nativeProjection;
    private Location camera;
    private DemoScene scene;
    private int tick;
    private boolean finished;
    private boolean running;
    private boolean closed;

    public DemoSession(OpticsDemoPlugin plugin, SessionContext context) {
        this.plugin = plugin;
        player = context.player();
        origin = context.origin().clone();
        returnLocation = player.getLocation();
        returnMode = player.getGameMode();
        returnAllowFlight = player.getAllowFlight();
        returnFlying = player.isFlying();
        returnGravity = player.hasGravity();
        returnInvulnerable = player.isInvulnerable();
        returnTimeRelative = player.isPlayerTimeRelative();
        returnTime = returnTimeRelative ? player.getPlayerTimeOffset() : player.getPlayerTime();
        returnWeather = player.getPlayerWeather();
        renderer = new DemoSceneRenderer(plugin, new DemoSceneRenderer.RenderContext(player, origin));
        try {
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setGravity(false);
            player.setInvulnerable(true);
            player.setPlayerTime(6000L, false);
            player.setPlayerWeather(WeatherType.CLEAR);
            camera = cameraLocation(new CameraPose(new Vec3d(0.0D, 2.0D, 6.0D), new Vec3d(0.0D, 2.0D, 0.0D)));
            if (!player.teleport(camera)) {
                throw new IllegalStateException("Player teleport to the demo stage was cancelled");
            }
            player.setVelocity(new Vector());
            projection = plugin.clientView().open(player, origin);
        } catch (RuntimeException exception) {
            close(true);
            throw exception;
        }
    }

    public void play(DemoScene next) {
        preview(next);
        running = true;
        plugin.getLogger().info("Optics demo started: player=" + player.getName() + " scene=" + scene.id()
            + " durationTicks=" + scene.durationTicks());
    }

    public void preview(DemoScene next) {
        requireOpen();
        Location nextCamera = cameraLocation(next.camera());
        if (camera.distanceSquared(nextCamera) > 1.0E-8D || camera.getYaw() != nextCamera.getYaw()
            || camera.getPitch() != nextCamera.getPitch()) {
            camera = nextCamera;
            if (!player.teleport(camera)) {
                throw new IllegalStateException("Player teleport to the scene camera was cancelled");
            }
        }
        scene = next;
        tick = 0;
        finished = false;
        running = false;
        frame = scene.sample(0.0D);
        projection.render(frame);
        renderer.render(frame, nativeProjection);
    }

    public void tick() {
        if (closed || scene == null) {
            return;
        }
        projection.tick();
        boolean selected = projection.nativeSelected();
        if (selected != nativeProjection) {
            nativeProjection = selected;
            renderer.render(frame, nativeProjection);
        }
        if (finished || !running) {
            return;
        }
        tick++;
        if ((tick & 1) == 0 || tick >= scene.durationTicks()) {
            frame = scene.sample(tick / 20.0D);
            projection.render(frame);
            renderer.render(frame, nativeProjection);
        }
        if (tick >= scene.durationTicks()) {
            finished = true;
            running = false;
            plugin.getLogger().info("Optics demo finished: player=" + player.getName() + " scene=" + scene.id()
                + " tick=" + tick + " displays=" + renderer.displayCount());
        }
    }

    public void close(boolean returnPlayer) {
        if (closed) {
            return;
        }
        closed = true;
        if (projection != null) {
            restore("native projection", projection::close);
        }
        restore("display removal", renderer::close);
        restore("gravity", () -> player.setGravity(returnGravity));
        restore("invulnerability", () -> player.setInvulnerable(returnInvulnerable));
        restore("game mode", () -> player.setGameMode(returnMode));
        restore("flight allowance", () -> player.setAllowFlight(returnAllowFlight));
        restore("flight", () -> player.setFlying(returnFlying && returnAllowFlight));
        restore("time", () -> player.setPlayerTime(returnTime, returnTimeRelative));
        restore("weather", () -> {
            if (returnWeather == null) {
                player.resetPlayerWeather();
            } else {
                player.setPlayerWeather(returnWeather);
            }
        });
        restore("return location", () -> {
            if (returnPlayer && player.isOnline() && !player.teleport(returnLocation)) {
                plugin.getLogger().warning("Return teleport was cancelled for " + player.getName());
            }
        });
        plugin.getLogger().info("Optics demo cleaned: player=" + player.getName() + " displays=" + renderer.displayCount());
    }

    static boolean cameraMatches(Location camera, Location target) {
        return camera.getWorld() == target.getWorld()
            && Math.abs(camera.getX() - target.getX()) <= 1.0E-6D
            && Math.abs(camera.getY() - target.getY()) <= 1.0E-6D
            && Math.abs(camera.getZ() - target.getZ()) <= 1.0E-6D
            && Math.abs(Math.IEEEremainder(camera.getYaw() - target.getYaw(), 360.0D)) <= 1.0E-4D
            && Math.abs(camera.getPitch() - target.getPitch()) <= 1.0E-4D;
    }

    public Location camera() {
        return camera.clone();
    }

    public boolean isClosed() {
        return closed;
    }

    public String status() {
        double eyeError = player.getEyeLocation().distance(camera.clone().add(0.0D, player.getEyeHeight(), 0.0D));
        return "player=" + player.getName() + " scene=" + (scene == null ? "ready" : scene.id()) + " tick=" + tick
            + " durationTicks=" + (scene == null ? 0 : scene.durationTicks())
            + " duration=" + (scene == null ? 0.0D : scene.durationSeconds()) + " completed=" + finished
            + " displays=" + renderer.displayCount() + " visible=" + renderer.visibleCount()
            + " cameraError=" + String.format(Locale.ROOT, "%.4f", eyeError)
            + " packetBatches=" + renderer.packetBatches() + " packetChanges=" + renderer.packetChanges()
            + (projection == null ? " renderer=block-packets ready=false" : projection.status());
    }

    private Location cameraLocation(CameraPose pose) {
        Vec3d eye = pose.eye();
        Vec3d direction = pose.target().subtract(eye);
        Location location = origin.clone().add(eye.x(), eye.y() - player.getEyeHeight(), eye.z());
        location.setDirection(new Vector(direction.x(), direction.y(), direction.z()));
        return location;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Demo session is closed");
        }
    }

    private void restore(String field, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not restore " + field + " for " + player.getName(), exception);
        }
    }

    public record SessionContext(Player player, Location origin) {}
}
