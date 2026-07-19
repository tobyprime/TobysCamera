package dev.tobyscamera.folia.map;

import java.io.IOException;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Maintains virtual-map source lifetimes while the delivery scheduler owns I/O and packets. */
public final class VirtualStillMapService {
    private final VirtualMapDeliveryScheduler scheduler;

    public VirtualStillMapService(Consumer<Runnable> async, Consumer<Runnable> sync, Consumer<IOException> failures,
            VirtualMapPacketSender sender) {
        this(new VirtualMapDeliveryScheduler(async, sync, failures, sender,
                new VirtualMapDeliveryScheduler.Limits(12, 4, 65_536L, 2_097_152L)));
    }

    VirtualStillMapService(VirtualMapDeliveryScheduler scheduler) { this.scheduler = scheduler; }

    public void setLimits(VirtualMapDeliveryScheduler.Limits limits) { scheduler.setLimits(limits); }

    public void attach(String source, Player player, MediaMapDescriptor descriptor,
            VirtualMapDeliveryScheduler.Priority priority, long distanceSquared, PixelLoader loader) {
        scheduler.attach(source, player, descriptor.mapId(), priority, distanceSquared, loader::load);
    }

    /** Retained for callers that have no source metadata; normal activation supplies an explicit priority. */
    public void attach(String source, Player player, MediaMapDescriptor descriptor, PixelLoader loader) {
        attach(source, player, descriptor, VirtualMapDeliveryScheduler.Priority.FRAME, 0L, loader);
    }

    public void detach(String source) { scheduler.detach(source); }

    public void clear() { scheduler.clear(); }

    public void tick() { scheduler.tick(); }

    @FunctionalInterface public interface PixelLoader { byte[] load() throws IOException; }
}
