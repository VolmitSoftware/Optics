package art.arcane.optics.fidelity;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.function.LongPredicate;
import java.util.function.BiConsumer;

import art.arcane.optics.claim.WorldOutput;
import art.arcane.optics.math.CellKeys;
import art.arcane.optics.spi.OpticsMetrics;

/**
 * Per-observer block-entity delivery: remembers what each projected cell was last told, queues the
 * cells whose sample changed or disappeared, and drains the queue under the per-tick packet budget.
 * A cell that leaves the projection re-sends the real local block entity when there is one.
 */
public final class BlockEntityLayer<O> {
    public static final String SENT_METRIC = "projection.block-entities.sent";

    private final OpticsMetrics metrics;
    private final Long2ObjectOpenHashMap<BlockEntitySample> desired;
    private final Long2ObjectOpenHashMap<BlockEntitySample> sent;
    private final Long2ObjectOpenHashMap<BlockEntitySample> pending;
    private final LongArrayList pendingOrder;
    private final LongOpenHashSet pendingKeys;
    private final LongOpenHashSet restoring;

    public BlockEntityLayer(OpticsMetrics metrics) {
        this.metrics = metrics;
        this.desired = new Long2ObjectOpenHashMap<BlockEntitySample>(64);
        this.sent = new Long2ObjectOpenHashMap<BlockEntitySample>(64);
        this.pending = new Long2ObjectOpenHashMap<BlockEntitySample>(64);
        this.pendingOrder = new LongArrayList(64);
        this.pendingKeys = new LongOpenHashSet(64);
        this.restoring = new LongOpenHashSet(64);
    }

    public void update(Long2ObjectMap<BlockEntitySample> desired, BlockEntityLookup local) {
        this.desired.clear();
        this.desired.putAll(desired);
        for (Long2ObjectMap.Entry<BlockEntitySample> entry : desired.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            BlockEntitySample sample = entry.getValue();
            if (sample == null || sample.equals(sent.get(key))) {
                dequeue(key);
                continue;
            }
            restoring.remove(key);
            enqueue(key, sample);
        }
        LongIterator iterator = sent.keySet().iterator();
        LongArrayList vanished = new LongArrayList();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            if (!desired.containsKey(key)) {
                vanished.add(key);
            }
        }
        iterator = pendingKeys.iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            if (!sent.containsKey(key) && !desired.containsKey(key)) {
                vanished.add(key);
            }
        }
        for (int index = 0; index < vanished.size(); index++) {
            long key = vanished.getLong(index);
            sent.remove(key);
            BlockEntitySample restore = local == null
                ? null
                : local.sample(CellKeys.unpackX(key), CellKeys.unpackY(key), CellKeys.unpackZ(key));
            if (restore != null) {
                restoring.add(key);
                enqueue(key, restore);
            } else {
                dequeue(key);
            }
        }
    }

    public int flush(O observer, int budget, WorldOutput<O> output) {
        return flush(observer, budget, output, key -> output.chunkSent(observer,
            CellKeys.unpackX(key) >> 4, CellKeys.unpackZ(key) >> 4));
    }

    public int flush(O observer, int budget, WorldOutput<O> output, LongPredicate eligible) {
        return flush(observer, budget, output, eligible, (key, sample) -> output.blockEntity(observer, key.longValue(), sample));
    }

    public int flush(O observer, int budget, WorldOutput<O> output, LongPredicate eligible,
                     BiConsumer<Long, BlockEntitySample> sender) {
        int sentNow = 0;
        int remaining = pendingOrder.size();
        while (sentNow < budget && remaining-- > 0 && !pendingOrder.isEmpty()) {
            long key = pendingOrder.removeLong(0);
            if (!pendingKeys.contains(key)) {
                continue;
            }
            if (!eligible.test(key)) {
                if (pendingKeys.contains(key)) {
                    pendingOrder.add(key);
                }
                continue;
            }
            BlockEntitySample sample = pending.get(key);
            if (sample == null) {
                dequeue(key);
                continue;
            }
            try {
                sender.accept(Long.valueOf(key), sample);
            } catch (RuntimeException failure) {
                pendingOrder.add(0, key);
                throw failure;
            }
            pendingKeys.remove(key);
            pending.remove(key);
            if (!restoring.remove(key)) {
                sent.put(key, sample);
            }
            sentNow++;
        }
        if (sentNow > 0) {
            metrics.count(SENT_METRIC, sentNow);
        }
        return sentNow;
    }

    public void restoreCell(long key, BlockEntitySample sample) {
        desired.remove(key);
        sent.remove(key);
        restoring.add(key);
        enqueue(key, sample);
    }

    public void discardCell(long key) {
        desired.remove(key);
        sent.remove(key);
        dequeue(key);
    }

    public void invalidateAll() {
        LongIterator iterator = desired.keySet().iterator();
        while (iterator.hasNext()) {
            invalidateCell(iterator.nextLong());
        }
    }

    public void invalidateCell(long key) {
        sent.remove(key);
        BlockEntitySample sample = desired.get(key);
        if (sample != null) {
            restoring.remove(key);
            enqueue(key, sample);
        }
    }

    public void invalidateChunk(int chunkX, int chunkZ) {
        LongIterator iterator = desired.keySet().iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            if ((CellKeys.unpackX(key) >> 4) == chunkX && (CellKeys.unpackZ(key) >> 4) == chunkZ) {
                invalidateCell(key);
            }
        }
    }

    public int pendingCount() {
        return pendingKeys.size();
    }

    public boolean hasPending() {
        return !pendingKeys.isEmpty();
    }

    /** Forgets what was sent so the next update repeats every sample (initial resend passes, chunk resends). */
    public void invalidateSent() {
        sent.clear();
    }

    /** Queues the local block entity of every projected cell so a closing view leaves real signs intact. */
    public void retireAll(BlockEntityLookup local) {
        Long2ObjectOpenHashMap<BlockEntitySample> empty = new Long2ObjectOpenHashMap<BlockEntitySample>();
        update(empty, local);
    }

    public void clear() {
        desired.clear();
        sent.clear();
        pending.clear();
        pendingOrder.clear();
        pendingKeys.clear();
        restoring.clear();
    }

    private void enqueue(long key, BlockEntitySample sample) {
        pending.put(key, sample);
        if (pendingKeys.add(key)) {
            pendingOrder.add(key);
        }
    }

    private void dequeue(long key) {
        pending.remove(key);
        pendingKeys.remove(key);
        restoring.remove(key);
    }
}
