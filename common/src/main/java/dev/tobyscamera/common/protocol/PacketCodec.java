package dev.tobyscamera.common.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PacketCodec {
    public static final byte VERSION = 3;
    public static final int MAX_CHUNK_BYTES = 8_192;
    private static final int MAX_STRING_BYTES = 512;

    private PacketCodec() {
    }

    public static byte[] encode(CameraPacket packet) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeByte(packet.type().id());
            switch (packet) {
                case Packets.CaptureIntent ignored -> { }
                case Packets.UploadGranted value -> {
                    writeUuid(out, value.token());
                    out.writeLong(value.expiresAtEpochMillis());
                    out.writeInt(value.tileBytes());
                    out.writeInt(value.maxChunksPerSecond());
                }
                case Packets.RateLimited value -> out.writeLong(value.retryAfterMillis());
                case Packets.UploadBegin value -> {
                    out.writeInt(value.gridWidth());
                    out.writeInt(value.gridHeight());
                    writeString(out, value.presentation().name());
                    writeString(out, value.presentation().description());
                    out.writeBoolean(value.presentation().publicAddress());
                    out.writeBoolean(value.presentation().publicPhotographer());
                }
                case Packets.UploadPreviewChunk value -> writePreviewChunk(out, value.token(), value.offset(), value.data());
                case Packets.UploadTileChunk value -> {
                    if (value.data().length > MAX_CHUNK_BYTES) throw new ProtocolException("chunk exceeds limit");
                    writeUuid(out, value.token());
                    out.writeInt(value.tileX()); out.writeInt(value.tileY()); out.writeInt(value.offset());
                    out.writeInt(value.data().length); out.write(value.data());
                }
                case Packets.UploadFinish value -> writeUuid(out, value.token());
                case Packets.PhotoCreated value -> {
                    writeUuid(out, value.photoId()); out.writeInt(value.gridWidth()); out.writeInt(value.gridHeight());
                    out.writeInt(value.mapIds().size());
                    for (int id : value.mapIds()) out.writeInt(id);
                }
                case Packets.UploadRejected value -> writeString(out, value.reason());
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static CameraPacket decode(byte[] bytes) {
        return decode(ByteBuffer.wrap(bytes));
    }

    public static CameraPacket decode(ByteBuffer in) {
        try {
            if (in.get() != VERSION) throw new ProtocolException("unsupported protocol version");
            CameraPacket packet = switch (PacketType.fromId(in.get())) {
                case CAPTURE_INTENT -> new Packets.CaptureIntent();
                case UPLOAD_GRANTED -> new Packets.UploadGranted(readUuid(in), in.getLong(), in.getInt(), in.getInt());
                case RATE_LIMITED -> new Packets.RateLimited(in.getLong());
                case UPLOAD_BEGIN -> new Packets.UploadBegin(
                        in.getInt(), in.getInt(),
                        new PhotoPresentation(readString(in), readString(in), in.get() != 0, in.get() != 0));
                case UPLOAD_PREVIEW_CHUNK -> readUploadPreviewChunk(in);
                case UPLOAD_TILE_CHUNK -> readChunk(in);
                case UPLOAD_FINISH -> new Packets.UploadFinish(readUuid(in));
                case PHOTO_CREATED -> readPhotoCreated(in);
                case UPLOAD_REJECTED -> new Packets.UploadRejected(readString(in));
            };
            if (in.hasRemaining()) throw new ProtocolException("trailing packet bytes");
            return packet;
        } catch (BufferUnderflowException exception) {
            throw new ProtocolException("truncated packet");
        }
    }

    private static Packets.UploadTileChunk readChunk(ByteBuffer in) {
        UUID token = readUuid(in); int x = in.getInt(); int y = in.getInt(); int offset = in.getInt();
        int length = in.getInt();
        if (length < 0 || length > MAX_CHUNK_BYTES || length > in.remaining()) throw new ProtocolException("invalid chunk length");
        byte[] data = new byte[length]; in.get(data);
        return new Packets.UploadTileChunk(token, x, y, offset, data);
    }
    private static Packets.UploadPreviewChunk readUploadPreviewChunk(ByteBuffer in) {
        UUID token = readUuid(in); int offset = in.getInt();
        return new Packets.UploadPreviewChunk(token, offset, readChunkData(in));
    }

    private static void writePreviewChunk(DataOutputStream out, UUID token, int offset, byte[] data) throws IOException {
        if (data.length > MAX_CHUNK_BYTES) throw new ProtocolException("chunk exceeds limit");
        writeUuid(out, token); out.writeInt(offset); out.writeInt(data.length); out.write(data);
    }

    private static byte[] readChunkData(ByteBuffer in) {
        int length = in.getInt();
        if (length < 0 || length > MAX_CHUNK_BYTES || length > in.remaining()) throw new ProtocolException("invalid chunk length");
        byte[] data = new byte[length]; in.get(data);
        return data;
    }

    private static Packets.PhotoCreated readPhotoCreated(ByteBuffer in) {
        UUID photoId = readUuid(in); int width = in.getInt(); int height = in.getInt(); int count = in.getInt();
        if (count < 0 || count > in.remaining() / Integer.BYTES) throw new ProtocolException("invalid map id count");
        List<Integer> mapIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) mapIds.add(in.getInt());
        return new Packets.PhotoCreated(photoId, mapIds, width, height);
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer in) { return new UUID(in.getLong(), in.getLong()); }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new ProtocolException("string exceeds limit");
        out.writeInt(bytes.length); out.write(bytes);
    }

    private static String readString(ByteBuffer in) {
        int length = in.getInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > in.remaining()) throw new ProtocolException("invalid string length");
        byte[] bytes = new byte[length]; in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
