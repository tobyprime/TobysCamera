package dev.tobyscamera.fabric.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoUploadControllerTest {
    @Test
    void pacesChunksSoNoStrictOneSecondWindowExceedsTheGrantedRate() {
        List<CameraPacket> sent = new ArrayList<>();
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();
        PhotoUploadController controller = new PhotoUploadController(sent::add, now::get);
        controller.confirm(photo(12), new byte[16_384]);
        controller.handleServerPacket(new Packets.UploadGranted(UUID.randomUUID(), Long.MAX_VALUE, 16_384, 32));

        List<Long> chunkTimes = new ArrayList<>();
        int observedPackets = sent.size();
        for (long millis = 0; millis <= 12_000; millis += 50) {
            now.set(millis);
            controller.tick();
            for (int index = observedPackets; index < sent.size(); index++) {
                if (sent.get(index) instanceof Packets.UploadPreviewChunk || sent.get(index) instanceof Packets.UploadTileChunk) {
                    chunkTimes.add(millis);
                }
            }
            observedPackets = sent.size();
        }

        assertEquals(290, chunkTimes.size());
        for (long timestamp : chunkTimes) {
            long inStrictWindow = chunkTimes.stream().filter(time -> time > timestamp - 1_000 && time <= timestamp).count();
            assertTrue(inStrictWindow <= 32, () -> "sent " + inStrictWindow + " chunks in the one-second window ending at " + timestamp);
        }
    }

    @Test
    void waitsForPacedAllowanceBeforeSendingTheFirstPhotoChunk() {
        java.util.List<CameraPacket> sent = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000L);
        PhotoUploadController controller = new PhotoUploadController(sent::add, now::get);
        controller.confirm(new MapTileEncoder.EncodedPhoto(1, 1, java.util.List.of(new byte[16_384])), new byte[16_384]);
        controller.handleServerPacket(new Packets.UploadGranted(UUID.randomUUID(), Long.MAX_VALUE, 16_384, 1));

        controller.tick();

        assertEquals(1, sent.size());
        now.set(2_000L);
        controller.tick();

        assertEquals(2, sent.size());
        assertEquals(Packets.UploadPreviewChunk.class, sent.getLast().getClass());
    }

    @Test
    void sendsClientGeneratedPreviewBeforeAnyPhotoTile() {
        List<CameraPacket> sent = new ArrayList<>();
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000L);
        PhotoUploadController controller = new PhotoUploadController(sent::add, now::get);
        MapTileEncoder.EncodedPhoto photo = new MapTileEncoder.EncodedPhoto(1, 1, List.of(filled((byte) 43)));
        byte[] preview = filled((byte) 19);

        controller.confirm(photo, preview);
        controller.handleServerPacket(new Packets.UploadGranted(UUID.randomUUID(), Long.MAX_VALUE, 16_384, 120));
        now.set(1_050L);
        controller.tick();

        assertEquals(Packets.UploadBegin.class, sent.get(0).getClass());
        assertEquals(Packets.UploadPreviewChunk.class, sent.get(1).getClass());
        assertEquals(Packets.UploadPreviewChunk.class, sent.get(2).getClass());
        assertEquals(Packets.UploadTileChunk.class, sent.get(3).getClass());
        assertEquals(19, Byte.toUnsignedInt(((Packets.UploadPreviewChunk) sent.get(1)).data()[0]));
    }

    private static byte[] filled(byte value) {
        byte[] pixels = new byte[16_384];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }

    private static MapTileEncoder.EncodedPhoto photo(int gridSize) {
        List<byte[]> tiles = new ArrayList<>();
        for (int index = 0; index < gridSize * gridSize; index++) tiles.add(new byte[16_384]);
        return new MapTileEncoder.EncodedPhoto(gridSize, gridSize, tiles);
    }
}
