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
    private static final UUID VIDEO = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @Test
    void roundTripsEveryProtocolPacket() {
        List<CameraPacket> packets = List.of(
                new Packets.CaptureIntent(),
                new Packets.UploadGranted(TOKEN, 1_700_000_000_000L, 16_384),
                new Packets.RateLimited(1_000L),
                new Packets.UploadBegin(2, 2),
                new Packets.UploadTileChunk(TOKEN, 1, 0, 8_192, new byte[8_192]),
                new Packets.UploadFinish(TOKEN),
                new Packets.PhotoCreated(PHOTO, List.of(10, 11, 12, 13), 2, 2),
                new Packets.VideoBegin(3, 4, 10, 20),
                new Packets.VideoGranted(TOKEN, 1_700_000_000_000L, 16_384, 120),
                new Packets.VideoTileChunk(TOKEN, 19, 2, 3, 8_192, new byte[8_192]),
                new Packets.VideoFinish(TOKEN),
                new Packets.VideoCreated(VIDEO, List.of(20, 21), 2, 1, 10, 20),
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
        if (expected instanceof Packets.VideoTileChunk chunk) {
            Packets.VideoTileChunk decoded = assertInstanceOf(Packets.VideoTileChunk.class, actual);
            assertEquals(chunk.token(), decoded.token()); assertEquals(chunk.frameIndex(), decoded.frameIndex());
            assertEquals(chunk.tileX(), decoded.tileX()); assertEquals(chunk.tileY(), decoded.tileY());
            assertEquals(chunk.offset(), decoded.offset()); assertArrayEquals(chunk.data(), decoded.data());
            return;
        }
        assertEquals(expected, actual);
    }
}
