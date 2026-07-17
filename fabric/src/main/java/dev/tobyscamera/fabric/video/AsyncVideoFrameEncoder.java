package dev.tobyscamera.fabric.video;

import dev.tobyscamera.fabric.camera.MapTileEncoder;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

/** Encodes one video frame at a time and exposes completed work through non-blocking polling. */
final class AsyncVideoFrameEncoder {
    private final Executor executor;
    private int requestedFrame = -1;
    private int generation;
    private boolean complete;
    private MapTileEncoder.EncodedPhoto result;
    private IOException failure;
    private FutureTask<Void> task;

    AsyncVideoFrameEncoder(Executor executor) { this.executor = executor; }

    synchronized boolean request(int frame, FrameEncoder encoder) {
        if (requestedFrame >= 0) return false;
        requestedFrame = frame;
        int requestGeneration = ++generation;
        task = new FutureTask<>(() -> {
            encode(frame, requestGeneration, encoder);
            return null;
        });
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException exception) {
            requestedFrame = -1;
            task = null;
            return false;
        }
    }

    synchronized MapTileEncoder.EncodedPhoto poll(int frame) throws IOException {
        if (requestedFrame != frame || !complete) return null;
        requestedFrame = -1;
        complete = false;
        task = null;
        if (failure != null) {
            IOException exception = failure;
            failure = null;
            throw exception;
        }
        MapTileEncoder.EncodedPhoto encoded = result;
        result = null;
        return encoded;
    }

    synchronized void clear() {
        generation++;
        requestedFrame = -1;
        complete = false;
        result = null;
        failure = null;
        if (task != null) task.cancel(true);
        task = null;
    }

    private void encode(int frame, int requestGeneration, FrameEncoder encoder) {
        synchronized (this) {
            if (!active(frame, requestGeneration)) return;
        }
        MapTileEncoder.EncodedPhoto encoded = null;
        IOException exception = null;
        try {
            encoded = encoder.encode(frame);
        } catch (IOException failure) {
            exception = failure;
        }
        synchronized (this) {
            if (!active(frame, requestGeneration)) return;
            result = encoded;
            failure = exception;
            complete = true;
        }
    }

    private boolean active(int frame, int requestGeneration) {
        return requestedFrame == frame && generation == requestGeneration;
    }

    @FunctionalInterface
    interface FrameEncoder {
        MapTileEncoder.EncodedPhoto encode(int frame) throws IOException;
    }
}
