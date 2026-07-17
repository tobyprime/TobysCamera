package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class VideoFrameWriteQueueTest {
    @Test
    void keepsAConfiguredNumberOfFramesBeforeApplyingBackpressure() {
        ManualExecutor executor = new ManualExecutor();
        VideoFrameWriteQueue queue = new VideoFrameWriteQueue(executor, 2);

        assertTrue(queue.submit(() -> { }));
        assertTrue(queue.submit(() -> { }));
        assertFalse(queue.submit(() -> { }));
        executor.runNext();
        assertTrue(queue.submit(() -> { }));
    }

    @Test
    void flushRunsAfterEarlierWrites() {
        ManualExecutor executor = new ManualExecutor();
        VideoFrameWriteQueue queue = new VideoFrameWriteQueue(executor);
        StringBuilder order = new StringBuilder();
        queue.submit(() -> order.append('w'));
        queue.flush(() -> order.append('f'));

        executor.runNext();
        executor.runNext();

        org.junit.jupiter.api.Assertions.assertEquals("wf", order.toString());
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runNext() { tasks.removeFirst().run(); }
    }
}
