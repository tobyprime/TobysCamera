package dev.tobyscamera.fabric.viewfinder;

/** Keeps an asynchronously prepared preview tied to the current UI selection. */
final class PreviewResultGate<T> {
    private int revision;
    private T result;
    private boolean ready;

    int request() {
        ready = false;
        result = null;
        return ++revision;
    }

    boolean publish(int completedRevision, T completedResult) {
        if (completedRevision != revision) return false;
        result = completedResult;
        ready = true;
        return true;
    }

    boolean ready() { return ready; }
    T result() { return result; }
}
