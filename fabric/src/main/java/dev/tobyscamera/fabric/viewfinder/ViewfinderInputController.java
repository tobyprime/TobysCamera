package dev.tobyscamera.fabric.viewfinder;

import java.util.Objects;

public final class ViewfinderInputController {
    private final ViewfinderSession session;
    private final Runnable captureRequester;

    public ViewfinderInputController(ViewfinderSession session, Runnable captureRequester) {
        this.session = Objects.requireNonNull(session, "session");
        this.captureRequester = Objects.requireNonNull(captureRequester, "captureRequester");
    }

    public boolean pressShutter() {
        if (!session.pressShutter()) return false;
        captureRequester.run();
        return true;
    }

    public boolean close() {
        if (session.state() == ViewfinderState.CLOSED || session.state() == ViewfinderState.PREVIEW) return false;
        session.close();
        return true;
    }
}
