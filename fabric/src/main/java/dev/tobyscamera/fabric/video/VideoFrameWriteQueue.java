package dev.tobyscamera.fabric.video;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/** Serial disk writer with bounded buffering so PNG compression cannot make the recorder drop normal frame bursts. */
public final class VideoFrameWriteQueue {
    private final Executor executor;
    private final int maximumQueuedFrames;
    private final AtomicInteger queuedFrames = new AtomicInteger();

    public VideoFrameWriteQueue(Executor executor) { this(executor, 128); }
    public VideoFrameWriteQueue(Executor executor, int maximumQueuedFrames) { this.executor = Objects.requireNonNull(executor, "executor"); if (maximumQueuedFrames < 1) throw new IllegalArgumentException("maximumQueuedFrames must be positive"); this.maximumQueuedFrames = maximumQueuedFrames; }

    public boolean submit(Runnable write) {
        if (queuedFrames.incrementAndGet() > maximumQueuedFrames) { queuedFrames.decrementAndGet(); return false; }
        executor.execute(() -> {
            try { write.run(); }
            finally { queuedFrames.decrementAndGet(); }
        });
        return true;
    }

    /** Adds a barrier after queued writes, used before opening confirmation. */
    public void flush(Runnable continuation) { executor.execute(continuation); }
}
