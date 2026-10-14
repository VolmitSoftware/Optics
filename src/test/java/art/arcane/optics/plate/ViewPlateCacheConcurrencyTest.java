package art.arcane.optics.plate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ViewPlateCacheConcurrencyTest {
    @Test
    void portalInvalidationCannotLoseItsReplacementToAnEarlierPublication() throws Exception {
        assertReplacementSurvives(Invalidation.PORTAL);
    }

    @Test
    void targetInvalidationCannotLoseItsReplacementToAnEarlierPublication() throws Exception {
        assertReplacementSurvives(Invalidation.TARGET);
    }

    @Test
    void clearingCannotLoseItsReplacementOrCorruptByteAccounting() throws Exception {
        assertReplacementSurvives(Invalidation.ALL);
    }

    @Test
    void invalidatedFactoriesCannotScheduleOrOverwriteTheirReplacement() throws Exception {
        for (Invalidation invalidation : Invalidation.values()) {
            assertDelayedFactoryRetired(invalidation);
        }
    }

    @Test
    void duplicateRequestsDoNotInvokeAnotherFactoryAndCanPromoteThePendingBuild() throws Exception {
        ConcurrentLinkedQueue<ViewPlateBuilder.Job<String, String>> scheduled = new ConcurrentLinkedQueue<>();
        ViewPlateCache<String, String> cache = new ViewPlateCache<>(1_000_000L, scheduled::add);
        ViewPlateKey key = new ViewPlateKey(UUID.randomUUID(), "destination", true, 0, 42L);
        ViewPlate<String> result = plate(key, 1L, 400L);
        ViewPlateBuilder.Job<String, String> job = job(result);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicInteger factories = new AtomicInteger();
        FutureTask<Void> request = new FutureTask<>(() -> {
            cache.current(key, 1L, 1L, null, false, previous -> {
                assertFalse(Thread.holdsLock(cache));
                factories.incrementAndGet();
                entered.countDown();
                await(resume);
                return job;
            });
            return null;
        });
        Thread requester = new Thread(request, "pending-plate-factory");
        try {
            requester.start();
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            cache.current(key, 1L, 1L, null, true, previous -> {
                factories.incrementAndGet();
                return job(result);
            });
            assertEquals(1, factories.get());
            assertTrue(scheduled.isEmpty());
            resume.countDown();
            request.get(5L, TimeUnit.SECONDS);
            assertEquals(List.of(job), List.copyOf(scheduled));
            assertTrue(job.urgent());
            assertTrue(cache.isBuilding(job));
            cache.publish(job, result);
            assertSame(result, cache.peek(key));
        } finally {
            resume.countDown();
            requester.join(5_000L);
        }
    }

    @Test
    void nullAndFailedFactoriesReleaseTheirReservationsAndPropagateFailures() {
        ConcurrentLinkedQueue<ViewPlateBuilder.Job<String, String>> scheduled = new ConcurrentLinkedQueue<>();
        ViewPlateCache<String, String> cache = new ViewPlateCache<>(1_000_000L, scheduled::add);
        ViewPlateKey key = new ViewPlateKey(UUID.randomUUID(), "destination", true, 0, 42L);
        cache.current(key, 1L, 1L, null, false, previous -> null);
        IllegalStateException failure = new IllegalStateException("Factory failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> cache.current(key, 1L, 1L, null, false, previous -> { throw failure; })));
        AssertionError error = new AssertionError("Factory failed");
        assertSame(error, assertThrows(AssertionError.class,
            () -> cache.current(key, 1L, 1L, null, false, previous -> { throw error; })));
        ViewPlate<String> result = plate(key, 1L, 400L);
        ViewPlateBuilder.Job<String, String> job = job(result);
        cache.current(key, 1L, 1L, null, false, previous -> job);
        assertEquals(List.of(job), List.copyOf(scheduled));
        assertTrue(cache.isBuilding(job));
        cache.publish(job, result);
        assertSame(result, cache.peek(key));
    }

    @Test
    void aRetiredFactoryFailureCannotRemoveItsReplacementReservation() {
        ViewPlateCache<String, String> cache = new ViewPlateCache<>(1_000_000L, job -> { });
        ViewPlateKey key = new ViewPlateKey(UUID.randomUUID(), "destination", true, 0, 42L);
        ViewPlate<String> replacement = plate(key, 2L, 600L);
        ViewPlateBuilder.Job<String, String> replacementJob = job(replacement);
        IllegalStateException failure = new IllegalStateException("Retired factory failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> cache.current(key, 1L, 1L, null, false, previous -> {
            assertFalse(Thread.holdsLock(cache));
            cache.invalidatePortal(key.portalId());
            cache.current(key, 2L, 2L, null, false, ignored -> replacementJob);
            throw failure;
        })));
        assertTrue(cache.isBuilding(replacementJob));
        cache.publish(replacementJob, replacement);
        assertSame(replacement, cache.peek(key));
        assertEquals(replacement.bytes(), cache.bytes());
    }

    @Test
    void schedulerCallbacksRemainOutsideTheMonitorAndFailuresReleaseReservations() {
        AtomicReference<ViewPlateCache<String, String>> holder = new AtomicReference<>();
        AtomicInteger requests = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("Scheduler failed");
        ViewPlateCache<String, String> cache = new ViewPlateCache<>(1_000_000L, job -> {
            assertFalse(Thread.holdsLock(holder.get()));
            if (requests.incrementAndGet() == 1) {
                throw failure;
            }
        });
        holder.set(cache);
        ViewPlateKey key = new ViewPlateKey(UUID.randomUUID(), "destination", true, 0, 42L);
        ViewPlate<String> oldPlate = plate(key, 1L, 400L);
        ViewPlateBuilder.Job<String, String> oldJob = job(oldPlate);
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> cache.current(key, 1L, 1L, null, false, previous -> oldJob)));
        assertFalse(cache.isBuilding(oldJob));
        ViewPlate<String> replacement = plate(key, 2L, 600L);
        ViewPlateBuilder.Job<String, String> replacementJob = job(replacement);
        cache.current(key, 2L, 2L, null, false, previous -> replacementJob);
        assertTrue(cache.isBuilding(replacementJob));
        cache.publish(replacementJob, replacement);
        assertSame(replacement, cache.peek(key));
    }

    private static void assertDelayedFactoryRetired(Invalidation invalidation) throws Exception {
        ConcurrentLinkedQueue<ViewPlateBuilder.Job<String, String>> scheduled = new ConcurrentLinkedQueue<>();
        ViewPlateCache<String, String> cache = new ViewPlateCache<>(1_000_000L, scheduled::add);
        ViewPlateKey key = new ViewPlateKey(UUID.randomUUID(), "destination", true, 0, 42L);
        ViewPlate<String> oldPlate = plate(key, 1L, 400L);
        ViewPlateBuilder.Job<String, String> oldJob = job(oldPlate);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        FutureTask<Void> request = new FutureTask<>(() -> {
            cache.current(key, 1L, 1L, null, false, previous -> {
                assertFalse(Thread.holdsLock(cache));
                entered.countDown();
                await(resume);
                return oldJob;
            });
            return null;
        });
        Thread requester = new Thread(request, "retired-plate-factory");
        try {
            requester.start();
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            switch (invalidation) {
                case PORTAL -> cache.invalidatePortal(key.portalId());
                case TARGET -> cache.invalidateTarget(key.portalId(), key.targetIdentity());
                case ALL -> cache.clear();
            }
            ViewPlate<String> replacement = plate(key, 2L, 600L);
            ViewPlateBuilder.Job<String, String> replacementJob = job(replacement);
            cache.current(key, 2L, 2L, null, false, previous -> replacementJob);
            cache.publish(replacementJob, replacement);
            resume.countDown();
            request.get(5L, TimeUnit.SECONDS);
            cache.publish(oldJob, oldPlate);
            assertEquals(List.of(replacementJob), List.copyOf(scheduled));
            assertFalse(cache.isBuilding(oldJob));
            assertSame(replacement, cache.peek(key));
            assertEquals(1, cache.size());
            assertEquals(replacement.bytes(), cache.bytes());
        } finally {
            resume.countDown();
            requester.join(5_000L);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Plate factory did not resume");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Plate factory interrupted", failure);
        }
    }

    private static void assertReplacementSurvives(Invalidation invalidation) throws Exception {
        ViewPlateCache<String, String> cache = new ViewPlateCache<>(1_000_000L, job -> { });
        PublicationPause identity = new PublicationPause();
        ViewPlateKey key = new ViewPlateKey(UUID.randomUUID(), identity, true, 0, 42L);
        ViewPlate<String> oldPlate = plate(key, 1L, 400L);
        ViewPlateBuilder.Job<String, String> oldJob = job(oldPlate);
        cache.current(key, 1L, 1L, null, false, previous -> oldJob);
        FutureTask<Void> publication = new FutureTask<>(() -> {
            cache.publish(oldJob, oldPlate);
            return null;
        });
        Thread publisher = new Thread(publication, "plate-publication");
        identity.publisher = publisher;
        ViewPlate<String> replacement = plate(key, 2L, 600L);
        CountDownLatch invalidating = new CountDownLatch(1);
        FutureTask<Void> replacementTask = new FutureTask<>(() -> {
            invalidating.countDown();
            switch (invalidation) {
                case PORTAL -> cache.invalidatePortal(key.portalId());
                case TARGET -> cache.invalidateTarget(key.portalId(), key.targetIdentity());
                case ALL -> cache.clear();
            }
            ViewPlateBuilder.Job<String, String> replacementJob = job(replacement);
            cache.current(key, 2L, 2L, null, false, previous -> replacementJob);
            cache.publish(replacementJob, replacement);
            return null;
        });
        Thread replacing = new Thread(replacementTask, "plate-replacement");
        try {
            publisher.start();
            assertTrue(identity.paused.await(5L, TimeUnit.SECONDS));
            replacing.start();
            assertTrue(invalidating.await(5L, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            while (replacing.isAlive() && replacing.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
            }
            assertTrue(replacementTask.isDone() || replacing.getState() == Thread.State.BLOCKED);
            identity.resume.countDown();
            publication.get(5L, TimeUnit.SECONDS);
            replacementTask.get(5L, TimeUnit.SECONDS);
            assertSame(replacement, cache.peek(key));
            assertEquals(1, cache.size());
            assertEquals(replacement.bytes(), cache.bytes());
            assertFalse(cache.isBuilding(oldJob));
        } finally {
            identity.resume.countDown();
            publisher.join(5_000L);
            replacing.join(5_000L);
        }
    }

    private static ViewPlate<String> plate(ViewPlateKey key, long revision, long bytes) {
        return new ViewPlate<>(key, PlateGrid.empty(), revision, revision, null, Long.MIN_VALUE,
            0, 0, 0, 0, bytes, null);
    }

    private static ViewPlateBuilder.Job<String, String> job(ViewPlate<String> plate) {
        return new ViewPlateBuilder.Job<>(plate.key()) {
            @Override
            public boolean step(int cellBudget) {
                return true;
            }

            @Override
            public ViewPlate<String> result() {
                return plate;
            }
        };
    }

    private enum Invalidation {
        PORTAL,
        TARGET,
        ALL
    }

    private static final class PublicationPause {
        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private Thread publisher;
        private int publicationHashes;

        @Override
        public int hashCode() {
            if (Thread.currentThread() == publisher && ++publicationHashes == 2) {
                paused.countDown();
                try {
                    if (!resume.await(10L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Plate publication did not resume");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
            }
            return 37;
        }
    }
}
