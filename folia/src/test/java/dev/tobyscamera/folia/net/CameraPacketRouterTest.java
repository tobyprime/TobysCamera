package dev.tobyscamera.folia.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CameraPacketRouterTest {
    @Test
    void routesVideoPacketsOnlyToTheVideoHandler() {
        List<CameraPacket> photos = new ArrayList<>();
        List<CameraPacket> videos = new ArrayList<>();
        CameraPacketRouter router = new CameraPacketRouter(photos::add, videos::add);

        router.route(new Packets.VideoBegin(1, 1, 1, 1));

        assertEquals(List.of(), photos);
        assertEquals(List.of(new Packets.VideoBegin(1, 1, 1, 1)), videos);
    }

    @Test
    void routesPhotoPacketsOnlyToThePhotoHandler() {
        List<CameraPacket> photos = new ArrayList<>();
        List<CameraPacket> videos = new ArrayList<>();
        CameraPacketRouter router = new CameraPacketRouter(photos::add, videos::add);

        router.route(new Packets.UploadBegin(1, 1));

        assertEquals(List.of(new Packets.UploadBegin(1, 1)), photos);
        assertEquals(List.of(), videos);
    }

    @Test
    void routesVideoPreviewPacketsOnlyToTheVideoHandler() {
        List<CameraPacket> photos = new ArrayList<>();
        List<CameraPacket> videos = new ArrayList<>();
        CameraPacketRouter router = new CameraPacketRouter(photos::add, videos::add);
        CameraPacket packet = new Packets.VideoPreviewChunk(java.util.UUID.randomUUID(), 0, new byte[8_192]);

        router.route(packet);

        assertEquals(List.of(), photos);
        assertEquals(List.of(packet), videos);
    }
}
