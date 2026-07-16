package dev.tobyscamera.fabric.viewfinder;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;

public final class ViewfinderInputController {
    private final ViewfinderSession session;
    private final IntSupplier maximumGridSize;
    private final BooleanSupplier videoSupported;
    private final IntConsumer captureRequester;

    public ViewfinderInputController(ViewfinderSession session, IntSupplier maximumGridSize, IntConsumer captureRequester) {
        this(session, maximumGridSize, () -> true, captureRequester);
    }

    public ViewfinderInputController(ViewfinderSession session, IntSupplier maximumGridSize, BooleanSupplier videoSupported, IntConsumer captureRequester) {
        this.session = Objects.requireNonNull(session, "session");
        this.maximumGridSize = Objects.requireNonNull(maximumGridSize, "maximumGridSize");
        this.videoSupported = Objects.requireNonNull(videoSupported, "videoSupported");
        this.captureRequester = Objects.requireNonNull(captureRequester, "captureRequester");
    }

    public boolean pressShutter() {
        if (session.state() == ViewfinderState.CAPTURING && session.mode() == CaptureMode.VIDEO) {
            return session.pressShutter(maximumGridSize.getAsInt());
        }
        if (session.mode() == CaptureMode.VIDEO && !videoSupported.getAsBoolean()) return false;
        int gridSize = maximumGridSize.getAsInt();
        if (!session.pressShutter(gridSize)) return false;
        captureRequester.accept(gridSize);
        return true;
    }

    public boolean close() {
        if (session.state() == ViewfinderState.CLOSED || session.state() == ViewfinderState.PREVIEW) return false;
        session.close();
        return true;
    }
}
