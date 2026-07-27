package dev.tobyscamera.folia.upload;

import dev.tobyscamera.common.protocol.CameraPacket;
import dev.tobyscamera.common.protocol.Packets;
import dev.tobyscamera.folia.storage.PhotoRecord;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Sends upload completion even when immediate bag delivery has to be retried later. */
public final class PhotoCompletionNotifier {
    private final BiConsumer<Player, PhotoRecord> deliver;
    private final DeliveryQueue queue;
    private final BiConsumer<Player, CameraPacket> sender;
    private final Consumer<String> warnings;

    public PhotoCompletionNotifier(BiConsumer<Player, PhotoRecord> deliver, DeliveryQueue queue,
            BiConsumer<Player, CameraPacket> sender, Consumer<String> warnings) {
        this.deliver = deliver;
        this.queue = queue;
        this.sender = sender;
        this.warnings = warnings;
    }

    public void complete(Player player, PhotoRecord record) {
        try {
            deliver.accept(player, record);
        } catch (RuntimeException exception) {
            try {
                queue.queue(player, record);
            } catch (IOException queueException) {
                warnings.accept("Could not queue photo delivery after immediate delivery failed: " + queueException.getMessage());
            }
            warnings.accept("Could not deliver photo immediately; queued for retry: " + exception.getMessage());
        }
        sender.accept(player, new Packets.PhotoCreated(record.photoId(), record.mapIds().values().stream().toList(),
                record.gridWidth(), record.gridHeight()));
    }

    @FunctionalInterface
    public interface DeliveryQueue {
        void queue(Player player, PhotoRecord record) throws IOException;
    }
}
