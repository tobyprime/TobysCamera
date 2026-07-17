package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoUploadControllerTest {
    @Test
    void honorsTheServerGrantedPhotoChunkRate() {
        java.util.List<CameraPacket> sent = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000L);
        PhotoUploadController controller = new PhotoUploadController(sent::add, now::get);
        controller.confirm(new MapTileEncoder.EncodedPhoto(1, 1, java.util.List.of(new byte[16_384])));
        controller.handleServerPacket(new Packets.UploadGranted(UUID.randomUUID(), Long.MAX_VALUE, 16_384, 1));

        controller.tick();

        assertEquals(2, sent.size());
        assertEquals(Packets.UploadPreviewChunk.class, sent.getLast().getClass());
    }

    @Test
    void sendsClientGeneratedPreviewBeforeAnyPhotoTile() {
        List<CameraPacket> sent = new ArrayList<>();
        PhotoUploadController controller = new PhotoUploadController(sent::add);
        MapTileEncoder.EncodedPhoto photo = new MapTileEncoder.EncodedPhoto(1, 1, List.of(filled((byte) 43)));

        controller.confirm(photo);
        controller.handleServerPacket(new Packets.UploadGranted(UUID.randomUUID(), Long.MAX_VALUE, 16_384, 120));
        controller.tick();

        assertEquals(Packets.UploadBegin.class, sent.get(0).getClass());
        assertEquals(Packets.UploadPreviewChunk.class, sent.get(1).getClass());
        assertEquals(Packets.UploadPreviewChunk.class, sent.get(2).getClass());
        assertEquals(Packets.UploadTileChunk.class, sent.get(3).getClass());
    }

    private static byte[] filled(byte value) {
        byte[] pixels = new byte[16_384];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }
}
