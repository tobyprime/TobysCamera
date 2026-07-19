package dev.tobyscamera.common.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PacketCodecTest {
    private static final UUID TOKEN = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PHOTO = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void roundTripsEveryProtocolPacket() {
        List<CameraPacket> packets = List.of(
                new Packets.CaptureIntent(),
                new Packets.UploadGranted(TOKEN, 1_700_000_000_000L, 16_384, 120),
                new Packets.RateLimited(1_000L),
                new Packets.UploadBegin(2, 2, new PhotoPresentation("晨雾", "山谷日出", false, true)),
                new Packets.UploadPreviewChunk(TOKEN, 8_192, new byte[8_192]),
                new Packets.UploadTileChunk(TOKEN, 1, 0, 8_192, new byte[8_192]),
                new Packets.UploadFinish(TOKEN),
                new Packets.PhotoCreated(PHOTO, List.of(10, 11, 12, 13), 2, 2),
                new Packets.UploadRejected("tile is incomplete"));

        for (CameraPacket packet : packets) {
            CameraPacket decoded = PacketCodec.decode(PacketCodec.encode(packet));
            assertSamePacket(packet, decoded);
        }
    }

    @Test
    void rejectsUnknownVersionAndOversizedChunk() {
        assertThrows(ProtocolException.class,
                () -> PacketCodec.decode(ByteBuffer.wrap(new byte[] {99, 1})));

        ByteBuffer invalidChunk = ByteBuffer.allocate(1 + 1 + 16 + 4 + 4 + 4 + 4);
        invalidChunk.put(PacketCodec.VERSION).put(PacketType.UPLOAD_TILE_CHUNK.id());
        invalidChunk.putLong(0).putLong(0).putInt(0).putInt(0).putInt(0).putInt(8_193);
        assertThrows(ProtocolException.class, () -> PacketCodec.decode(invalidChunk.array()));

        assertThrows(ProtocolException.class,
                () -> PacketCodec.encode(new Packets.UploadPreviewChunk(TOKEN, 0, new byte[8_193])));
        for (int removedId : new int[] {9, 10, 11, 12, 13, 15}) {
            assertThrows(ProtocolException.class,
                    () -> PacketCodec.decode(ByteBuffer.wrap(new byte[] {PacketCodec.VERSION, (byte) removedId})));
        }
    }

    @Test
    void previewChunksDefensivelyCopyTheirPayloads() {
        byte[] pixels = {1, 2};
        Packets.UploadPreviewChunk photo = new Packets.UploadPreviewChunk(TOKEN, 0, pixels);

        pixels[0] = 9;
        assertArrayEquals(new byte[] {1, 2}, photo.data());
        photo.data()[1] = 9;
        assertArrayEquals(new byte[] {1, 2}, photo.data());
    }

    @Test
    void roundTripsAndNormalizesPhotoPresentationText() {
        Packets.UploadBegin upload = new Packets.UploadBegin(1, 1,
                new PhotoPresentation("  晨雾  ", "   ", false, true));

        Packets.UploadBegin decoded = assertInstanceOf(Packets.UploadBegin.class,
                PacketCodec.decode(PacketCodec.encode(upload)));
        assertEquals(new PhotoPresentation("晨雾", "", false, true), decoded.presentation());
    }

    @Test
    void rejectsPhotoPresentationTextOver512Utf8Bytes() {
        String oversized = "a".repeat(513);

        assertThrows(ProtocolException.class,
                () -> PacketCodec.encode(new Packets.UploadBegin(1, 1,
                        new PhotoPresentation(oversized, "", true, true))));
        assertThrows(ProtocolException.class,
                () -> PacketCodec.encode(new Packets.UploadBegin(1, 1,
                        new PhotoPresentation("", oversized, true, true))));
    }

    private static void assertSamePacket(CameraPacket expected, CameraPacket actual) {
        if (expected instanceof Packets.UploadTileChunk chunk) {
            Packets.UploadTileChunk decoded = assertInstanceOf(Packets.UploadTileChunk.class, actual);
            assertEquals(chunk.token(), decoded.token());
            assertEquals(chunk.tileX(), decoded.tileX());
            assertEquals(chunk.tileY(), decoded.tileY());
            assertEquals(chunk.offset(), decoded.offset());
            assertArrayEquals(chunk.data(), decoded.data());
            return;
        }
        if (expected instanceof Packets.UploadPreviewChunk chunk) {
            Packets.UploadPreviewChunk decoded = assertInstanceOf(Packets.UploadPreviewChunk.class, actual);
            assertEquals(chunk.token(), decoded.token());
            assertEquals(chunk.offset(), decoded.offset());
            assertArrayEquals(chunk.data(), decoded.data());
            return;
        }
        assertEquals(expected, actual);
    }
}
