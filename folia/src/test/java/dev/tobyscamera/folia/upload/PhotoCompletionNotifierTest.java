package dev.tobyscamera.folia.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.storage.TileCoordinate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PhotoCompletionNotifierTest {
    @Test
    void sendsCompletionAndQueuesDeliveryWhenImmediateDeliveryFails() {
        Player player = mock(Player.class);
        PhotoRecord record = record();
        List<CameraPacket> sent = new ArrayList<>();
        AtomicBoolean queued = new AtomicBoolean();
        PhotoCompletionNotifier notifier = new PhotoCompletionNotifier(
                (ignoredPlayer, ignoredRecord) -> { throw new IllegalStateException("inventory unavailable"); },
                (ignoredPlayer, ignoredRecord) -> queued.set(true),
                (ignoredPlayer, packet) -> sent.add(packet),
                ignored -> { });

        notifier.complete(player, record);

        assertTrue(queued.get());
        assertEquals(new Packets.PhotoCreated(record.photoId(), List.of(42), 1, 1), sent.getFirst());
    }

    private static PhotoRecord record() {
        return new PhotoRecord(UUID.randomUUID(), UUID.randomUUID(), "Toby", Instant.parse("2026-07-26T12:00:00Z"),
                1, 1, Map.of(new TileCoordinate(0, 0), 42), null);
    }
}
