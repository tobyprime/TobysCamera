package dev.tobyscamera.folia.net;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import java.util.function.Consumer;

final class CameraPacketRouter {
    private final Consumer<CameraPacket> photoHandler;
    private final Consumer<CameraPacket> videoHandler;

    CameraPacketRouter(Consumer<CameraPacket> photoHandler, Consumer<CameraPacket> videoHandler) {
        this.photoHandler = photoHandler;
        this.videoHandler = videoHandler;
    }

    void route(CameraPacket packet) {
        switch (packet) {
            case Packets.VideoBegin ignored -> videoHandler.accept(packet);
            case Packets.VideoPreviewChunk ignored -> videoHandler.accept(packet);
            case Packets.VideoTileChunk ignored -> videoHandler.accept(packet);
            case Packets.VideoFinish ignored -> videoHandler.accept(packet);
            default -> photoHandler.accept(packet);
        }
    }
}
