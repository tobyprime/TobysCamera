package dev.tobyscamera.fabric.video;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serial disk writer with one-frame backpressure so slow PNG compression never accumulates on the render thread. */
public final class VideoFrameWriteQueue {
    private final Executor executor;
    private final AtomicBoolean pendingFrame = new AtomicBoolean();

    public VideoFrameWriteQueue(Executor executor) { this.executor = Objects.requireNonNull(executor, "executor"); }

    public boolean submit(Runnable write) {
        if (!pendingFrame.compareAndSet(false, true)) return false;
        executor.execute(() -> {
            try { write.run(); }
            finally { pendingFrame.set(false); }
        });
        return true;
    }

    /** Adds a barrier after queued writes, used before opening confirmation. */
    public void flush(Runnable continuation) { executor.execute(continuation); }
}
