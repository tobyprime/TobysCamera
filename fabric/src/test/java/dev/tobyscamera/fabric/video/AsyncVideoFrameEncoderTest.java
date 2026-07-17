package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tobyscamera.fabric.camera.MapTileEncoder;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncVideoFrameEncoderTest {
    @Test
    void doesNotEncodeUntilItsBackgroundTaskRuns() throws Exception {
        var queued = new ArrayDeque<Runnable>();
        var encoded = new AtomicInteger();
        var output = new MapTileEncoder.EncodedPhoto(1, 1, List.of(new byte[16_384]));
        AsyncVideoFrameEncoder frames = new AsyncVideoFrameEncoder(queued::add);

        frames.request(0, ignored -> { encoded.incrementAndGet(); return output; });

        assertNull(frames.poll(0));
        assertEquals(0, encoded.get());
        queued.remove().run();
        assertSame(output, frames.poll(0));
    }

    @Test
    void clearInterruptsAnInProgressFrame() throws Exception {
        var started = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var interrupted = new AtomicInteger();
        AsyncVideoFrameEncoder frames = new AsyncVideoFrameEncoder(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.start();
        });

        frames.request(0, ignored -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.incrementAndGet();
            } finally {
                finished.countDown();
            }
            return new MapTileEncoder.EncodedPhoto(1, 1, List.of(new byte[16_384]));
        });
        started.await();

        frames.clear();

        assertTrue(finished.await(1, TimeUnit.SECONDS));
        assertEquals(1, interrupted.get());
    }

    @Test
    void doesNotRemainBusyWhenTheExecutorRejectsItsFrame() {
        AsyncVideoFrameEncoder frames = new AsyncVideoFrameEncoder(runnable -> {
            throw new RejectedExecutionException();
        });

        assertFalse(frames.request(0, ignored -> new MapTileEncoder.EncodedPhoto(1, 1, List.of(new byte[16_384]))));
        assertFalse(frames.request(0, ignored -> new MapTileEncoder.EncodedPhoto(1, 1, List.of(new byte[16_384]))));
    }
}
