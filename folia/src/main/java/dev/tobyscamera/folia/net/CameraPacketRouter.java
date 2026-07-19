package dev.tobyscamera.folia.net;

import dev.tobyscamera.common.protocol.CameraPacket;
import java.util.function.Consumer;

final class CameraPacketRouter {
    private final Consumer<CameraPacket> photoHandler;

    CameraPacketRouter(Consumer<CameraPacket> photoHandler) {
        this.photoHandler = photoHandler;
    }

    void route(CameraPacket packet) {
        photoHandler.accept(packet);
    }
}
