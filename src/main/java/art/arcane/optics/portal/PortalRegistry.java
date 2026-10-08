package art.arcane.optics.portal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

import art.arcane.optics.aperture.ObserverGeometry;
import art.arcane.optics.frame.QuarterTurn;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.CellKeys;
import art.arcane.optics.math.Vec3d;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class PortalRegistry<W> {
    private static final int CHUNK_SHIFT = 4;
    private static final double QUERY_MARGIN = 1.0E-7D;

    private final LinkedHashMap<UUID, Entry<W>> entries;
    private final HashMap<W, WorldIndex<W>> worlds;
    private final ArrayList<Listener<W>> listeners;
    private final ArrayList<Entry<W>> scratch;
    private final PortalDirectory<W> directory;
    private List<PortalDefinition> snapshot;
    private long revision;
    private long visit;

    public PortalRegistry() {
        entries = new LinkedHashMap<UUID, Entry<W>>();
        worlds = new HashMap<W, WorldIndex<W>>(4);
        listeners = new ArrayList<Listener<W>>(2);
        scratch = new ArrayList<Entry<W>>();
        directory = new PortalDirectory<W>(this);
    }

    public PortalDefinition add(W world, PortalDefinition portal) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(portal, "portal");
        Entry<W> existing = entries.get(portal.id());
        if (existing == null) {
            Entry<W> entry = new Entry<W>(portal, world);
            entries.put(portal.id(), entry);
            index(entry);
            stamp(entry);
            notifyAdded(world, portal);
            return null;
        }
        PortalDefinition previous = existing.portal;
        if (existing.world.equals(world)) {
            if (!previous.equals(portal)) {
                replace(existing, portal);
            }
            return previous;
        }
        W previousWorld = existing.world;
        unindex(existing);
        existing.portal = portal;
        existing.world = world;
        index(existing);
        stamp(existing);
        notifyRemoved(previousWorld, previous);
        notifyAdded(world, portal);
        return previous;
    }

    public PortalDefinition remove(UUID id) {
        Entry<W> entry = entries.remove(id);
        if (entry == null) {
            return null;
        }
        unindex(entry);
        revision++;
        snapshot = null;
        notifyRemoved(entry.world, entry.portal);
        return entry.portal;
    }

    public PortalDefinition get(UUID id) {
        Entry<W> entry = entries.get(id);
        return entry == null ? null : entry.portal;
    }

    public W world(UUID id) {
        Entry<W> entry = entries.get(id);
        return entry == null ? null : entry.world;
    }

    public boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    public int size() {
        return entries.size();
    }

    public List<PortalDefinition> all() {
        List<PortalDefinition> cached = snapshot;
        if (cached != null) {
            return cached;
        }
        ArrayList<PortalDefinition> portals = new ArrayList<PortalDefinition>(entries.size());
        for (Entry<W> entry : entries.values()) {
            portals.add(entry.portal);
        }
        snapshot = List.copyOf(portals);
        return snapshot;
    }

    public List<PortalDefinition> in(W world) {
        WorldIndex<W> index = worlds.get(world);
        return index == null ? List.of() : index.snapshot();
    }

    public PortalDefinition update(UUID id, UnaryOperator<PortalDefinition> change) {
        Entry<W> entry = entries.get(id);
        if (entry == null) {
            return null;
        }
        PortalDefinition next = Objects.requireNonNull(change.apply(entry.portal), "updated portal");
        if (!next.id().equals(id)) {
            throw new IllegalArgumentException("An update must keep the portal id " + id + ", got " + next.id());
        }
        if (next.equals(entry.portal)) {
            return entry.portal;
        }
        replace(entry, next);
        return next;
    }

    public void link(UUID from, UUID to) {
        Entry<W> source = linkable(from, to);
        Entry<W> target = require(to);
        relink(source, retarget(source.portal.link(), to));
        relink(target, retarget(target.portal.link(), from));
    }

    public void linkOneWay(UUID from, UUID to) {
        Entry<W> source = linkable(from, to);
        require(to);
        relink(source, retarget(source.portal.link(), to));
    }

    public void unlink(UUID id) {
        ArrayList<Entry<W>> linked = new ArrayList<Entry<W>>();
        Entry<W> own = entries.get(id);
        if (own != null && own.portal.link() != null) {
            linked.add(own);
        }
        for (Entry<W> entry : entries.values()) {
            PortalLink link = entry.portal.link();
            if (entry != own && link != null && id.equals(link.target())) {
                linked.add(entry);
            }
        }
        for (Entry<W> entry : linked) {
            if (entries.get(entry.portal.id()) == entry && entry.portal.link() != null) {
                replace(entry, entry.portal.unlinked());
            }
        }
    }

    public void mirror(UUID id, QuarterTurn turns) {
        Objects.requireNonNull(turns, "turns");
        Entry<W> entry = require(id);
        PortalLink link = entry.portal.link();
        relink(entry, link == null ? PortalLink.mirror(turns) : link.mirrored(turns));
    }

    public PortalDefinition destination(PortalDefinition portal) {
        PortalLink link = portal.link();
        if (link == null) {
            return null;
        }
        if (link.isMirror()) {
            return portal;
        }
        Entry<W> target = entries.get(link.target());
        return target == null ? null : target.portal;
    }

    public PortalDefinition at(W world, Vec3d point, double planeTolerance) {
        WorldIndex<W> index = worlds.get(world);
        if (index == null || !(planeTolerance >= 0.0D)) {
            return null;
        }
        gather(index, point.x() - planeTolerance, point.z() - planeTolerance, point.x() + planeTolerance, point.z() + planeTolerance);
        PortalDefinition best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int candidate = 0; candidate < scratch.size(); candidate++) {
            PortalDefinition portal = scratch.get(candidate).portal;
            if (!portal.containsPoint(point, planeTolerance)) {
                continue;
            }
            double distance = portal.planeDistance(point.x(), point.y(), point.z());
            if (distance < bestDistance) {
                best = portal;
                bestDistance = distance;
            }
        }
        scratch.clear();
        return best;
    }

    public int at(W world, Vec3d point, double planeTolerance, List<PortalDefinition> out) {
        WorldIndex<W> index = worlds.get(world);
        if (index == null || !(planeTolerance >= 0.0D)) {
            return 0;
        }
        gather(index, point.x() - planeTolerance, point.z() - planeTolerance, point.x() + planeTolerance, point.z() + planeTolerance);
        int count = 0;
        for (int candidate = 0; candidate < scratch.size(); candidate++) {
            PortalDefinition portal = scratch.get(candidate).portal;
            if (portal.containsPoint(point, planeTolerance)) {
                out.add(portal);
                count++;
            }
        }
        scratch.clear();
        return count;
    }

    public PortalCrossing firstCrossing(W world, Vec3d start, Vec3d end, Vec3d velocity, Vec3d look) {
        WorldIndex<W> index = worlds.get(world);
        if (index == null) {
            return null;
        }
        gatherSegment(index, start, end);
        PortalDefinition best = null;
        double bestFraction = Double.POSITIVE_INFINITY;
        for (int candidate = 0; candidate < scratch.size(); candidate++) {
            PortalDefinition portal = scratch.get(candidate).portal;
            double fraction = portal.crossingFraction(start, end);
            if (fraction < bestFraction) {
                best = portal;
                bestFraction = fraction;
            }
        }
        scratch.clear();
        return best == null ? null : best.crossing(start, end, velocity, look);
    }

    public int crossings(W world, Vec3d start, Vec3d end, Vec3d velocity, Vec3d look, List<PortalCrossing> out) {
        WorldIndex<W> index = worlds.get(world);
        if (index == null) {
            return 0;
        }
        gatherSegment(index, start, end);
        int base = out.size();
        for (int candidate = 0; candidate < scratch.size(); candidate++) {
            PortalCrossing crossing = scratch.get(candidate).portal.crossing(start, end, velocity, look);
            if (crossing == null) {
                continue;
            }
            int slot = out.size();
            while (slot > base && out.get(slot - 1).fraction() > crossing.fraction()) {
                slot--;
            }
            out.add(slot, crossing);
        }
        scratch.clear();
        return out.size() - base;
    }

    public int visible(W world, Vec3d eye, Vec3d look, double fovDegrees, double range, List<PortalDefinition> out) {
        WorldIndex<W> index = worlds.get(world);
        double length = Math.sqrt(look.x() * look.x() + look.y() * look.y() + look.z() * look.z());
        if (index == null || !(length > 0.0D) || !(fovDegrees > 0.0D) || !(range >= 0.0D)) {
            return 0;
        }
        double lookX = look.x() / length;
        double lookY = look.y() / length;
        double lookZ = look.z() / length;
        double minimumDot = fovDegrees >= 360.0D ? Double.NEGATIVE_INFINITY : Math.cos(Math.toRadians(fovDegrees) * 0.5D);
        double rangeSquared = range * range;
        int count = 0;
        for (int candidate = 0; candidate < index.entries.size(); candidate++) {
            PortalDefinition portal = index.entries.get(candidate).portal;
            Vec3d center = portal.origin();
            double dx = center.x() - eye.x();
            double dy = center.y() - eye.y();
            double dz = center.z() - eye.z();
            if (dx * dx + dy * dy + dz * dz > rangeSquared) {
                continue;
            }
            if (ObserverGeometry.isLookingTowardPortal(eye.x(), eye.y(), eye.z(), center.x(), center.y(), center.z(), lookX, lookY, lookZ,
                minimumDot)) {
                out.add(portal);
                count++;
            }
        }
        return count;
    }

    public int intersecting(W world, Box box, List<PortalDefinition> out) {
        WorldIndex<W> index = worlds.get(world);
        if (index == null) {
            return 0;
        }
        gather(index, box.getXa(), box.getZa(), box.getXb(), box.getZb());
        int count = 0;
        for (int candidate = 0; candidate < scratch.size(); candidate++) {
            PortalDefinition portal = scratch.get(candidate).portal;
            if (portal.areaOverlaps(box)) {
                out.add(portal);
                count++;
            }
        }
        scratch.clear();
        return count;
    }

    public long revision() {
        return revision;
    }

    public long revision(UUID id) {
        Entry<W> entry = entries.get(id);
        return entry == null ? 0L : entry.revision;
    }

    public void addListener(Listener<W> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Listener<W> listener) {
        listeners.remove(listener);
    }

    public PortalDirectory<W> directory() {
        return directory;
    }

    private Entry<W> require(UUID id) {
        Entry<W> entry = entries.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("No portal " + id + " in the registry");
        }
        return entry;
    }

    private Entry<W> linkable(UUID from, UUID to) {
        if (Objects.requireNonNull(from, "from").equals(Objects.requireNonNull(to, "to"))) {
            throw new IllegalArgumentException("Portal " + from + " cannot link to itself");
        }
        return require(from);
    }

    private void relink(Entry<W> entry, PortalLink link) {
        PortalDefinition next = entry.portal.linked(link);
        if (next != entry.portal) {
            replace(entry, next);
        }
    }

    private void replace(Entry<W> entry, PortalDefinition next) {
        PortalDefinition previous = entry.portal;
        WorldIndex<W> index = worlds.get(entry.world);
        if (spanChanged(entry, next)) {
            unindex(entry);
            entry.portal = next;
            index(entry);
        } else {
            entry.portal = next;
            index.snapshot = null;
        }
        stamp(entry);
        notifyChanged(entry.world, previous, next);
    }

    private void stamp(Entry<W> entry) {
        revision++;
        entry.revision = revision;
        snapshot = null;
    }

    private void index(Entry<W> entry) {
        WorldIndex<W> index = worlds.computeIfAbsent(entry.world, world -> new WorldIndex<W>());
        PortalDefinition portal = entry.portal;
        entry.minChunkX = chunk(portal.boundMin(0));
        entry.maxChunkX = chunk(portal.boundMax(0));
        entry.minChunkZ = chunk(portal.boundMin(2));
        entry.maxChunkZ = chunk(portal.boundMax(2));
        for (int chunkX = entry.minChunkX; chunkX <= entry.maxChunkX; chunkX++) {
            for (int chunkZ = entry.minChunkZ; chunkZ <= entry.maxChunkZ; chunkZ++) {
                long key = CellKeys.chunkKey(chunkX, chunkZ);
                ArrayList<Entry<W>> bucket = index.chunks.get(key);
                if (bucket == null) {
                    bucket = new ArrayList<Entry<W>>(2);
                    index.chunks.put(key, bucket);
                }
                bucket.add(entry);
            }
        }
        index.entries.add(entry);
        index.snapshot = null;
    }

    private void unindex(Entry<W> entry) {
        WorldIndex<W> index = worlds.get(entry.world);
        if (index == null) {
            return;
        }
        for (int chunkX = entry.minChunkX; chunkX <= entry.maxChunkX; chunkX++) {
            for (int chunkZ = entry.minChunkZ; chunkZ <= entry.maxChunkZ; chunkZ++) {
                long key = CellKeys.chunkKey(chunkX, chunkZ);
                ArrayList<Entry<W>> bucket = index.chunks.get(key);
                if (bucket != null && bucket.remove(entry) && bucket.isEmpty()) {
                    index.chunks.remove(key);
                }
            }
        }
        index.entries.remove(entry);
        index.snapshot = null;
        if (index.entries.isEmpty()) {
            worlds.remove(entry.world);
        }
    }

    private void gatherSegment(WorldIndex<W> index, Vec3d start, Vec3d end) {
        gather(index, Math.min(start.x(), end.x()), Math.min(start.z(), end.z()), Math.max(start.x(), end.x()), Math.max(start.z(), end.z()));
    }

    private void gather(WorldIndex<W> index, double minX, double minZ, double maxX, double maxZ) {
        scratch.clear();
        if (!(minX <= maxX) || !(minZ <= maxZ)) {
            return;
        }
        int minChunkX = chunk(minX - QUERY_MARGIN);
        int maxChunkX = chunk(maxX + QUERY_MARGIN);
        int minChunkZ = chunk(minZ - QUERY_MARGIN);
        int maxChunkZ = chunk(maxZ + QUERY_MARGIN);
        long span = ((long) maxChunkX - minChunkX + 1L) * ((long) maxChunkZ - minChunkZ + 1L);
        if (span >= index.entries.size()) {
            for (int candidate = 0; candidate < index.entries.size(); candidate++) {
                Entry<W> entry = index.entries.get(candidate);
                if (entry.maxChunkX >= minChunkX && entry.minChunkX <= maxChunkX && entry.maxChunkZ >= minChunkZ && entry.minChunkZ <= maxChunkZ) {
                    scratch.add(entry);
                }
            }
            return;
        }
        long stamp = ++visit;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ArrayList<Entry<W>> bucket = index.chunks.get(CellKeys.chunkKey(chunkX, chunkZ));
                if (bucket == null) {
                    continue;
                }
                for (int slot = 0; slot < bucket.size(); slot++) {
                    Entry<W> entry = bucket.get(slot);
                    if (entry.visit != stamp) {
                        entry.visit = stamp;
                        scratch.add(entry);
                    }
                }
            }
        }
    }

    private void notifyAdded(W world, PortalDefinition portal) {
        for (Listener<W> listener : List.copyOf(listeners)) {
            listener.added(world, portal);
        }
    }

    private void notifyRemoved(W world, PortalDefinition portal) {
        for (Listener<W> listener : List.copyOf(listeners)) {
            listener.removed(world, portal);
        }
    }

    private void notifyChanged(W world, PortalDefinition previous, PortalDefinition current) {
        for (Listener<W> listener : List.copyOf(listeners)) {
            listener.changed(world, previous, current);
        }
    }

    private static boolean spanChanged(Entry<?> entry, PortalDefinition next) {
        return entry.minChunkX != chunk(next.boundMin(0)) || entry.maxChunkX != chunk(next.boundMax(0))
            || entry.minChunkZ != chunk(next.boundMin(2)) || entry.maxChunkZ != chunk(next.boundMax(2));
    }

    private static PortalLink retarget(PortalLink link, UUID target) {
        return link == null ? PortalLink.to(target) : link.retargeted(target);
    }

    private static int chunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> CHUNK_SHIFT;
    }

    public interface Listener<W> {
        void added(W world, PortalDefinition portal);

        void removed(W world, PortalDefinition portal);

        void changed(W world, PortalDefinition previous, PortalDefinition current);
    }

    private static final class Entry<W> {
        private PortalDefinition portal;
        private W world;
        private long revision;
        private long visit;
        private int minChunkX;
        private int maxChunkX;
        private int minChunkZ;
        private int maxChunkZ;

        private Entry(PortalDefinition portal, W world) {
            this.portal = portal;
            this.world = world;
        }
    }

    private static final class WorldIndex<W> {
        private final Long2ObjectOpenHashMap<ArrayList<Entry<W>>> chunks = new Long2ObjectOpenHashMap<ArrayList<Entry<W>>>();
        private final ArrayList<Entry<W>> entries = new ArrayList<Entry<W>>();
        private List<PortalDefinition> snapshot;

        private List<PortalDefinition> snapshot() {
            List<PortalDefinition> cached = snapshot;
            if (cached != null) {
                return cached;
            }
            ArrayList<PortalDefinition> portals = new ArrayList<PortalDefinition>(entries.size());
            for (Entry<W> entry : entries) {
                portals.add(entry.portal);
            }
            snapshot = List.copyOf(portals);
            return snapshot;
        }
    }
}
