package dev.tobyscamera.common.protocol;

enum PacketType {
    CAPTURE_INTENT(1),
    UPLOAD_GRANTED(2),
    RATE_LIMITED(3),
    UPLOAD_BEGIN(4),
    UPLOAD_TILE_CHUNK(5),
    UPLOAD_FINISH(6),
    PHOTO_CREATED(7),
    UPLOAD_REJECTED(8),
    VIDEO_BEGIN(9), VIDEO_GRANTED(10), VIDEO_TILE_CHUNK(11), VIDEO_FINISH(12), VIDEO_CREATED(13);

    private final byte id;

    PacketType(int id) {
        this.id = (byte) id;
    }

    byte id() {
        return id;
    }

    static PacketType fromId(byte id) {
        for (PacketType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new ProtocolException("unknown packet type " + Byte.toUnsignedInt(id));
    }
}
