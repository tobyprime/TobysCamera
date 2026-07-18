package dev.tobyscamera.folia.map;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.tobyscamera.folia.bag.PhotoBagData;
import dev.tobyscamera.folia.bag.PhotoBagKind;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Activates only camera maps in player hands or item frames from currently loaded chunks. */
public final class MediaMapActivationListener implements Listener {
    private final Plugin plugin;
    private final ServerTaskScheduler scheduler;
    private final MapPhotoService photos;
    private final MapVideoService videos;
    private final StillMapAttachmentService stills;
    private final Map<String, MediaMapDescriptor.VideoTile> pendingVideos = new ConcurrentHashMap<>();
    private volatile Runnable videoIndexRefresh = () -> { };

    public MediaMapActivationListener(Plugin plugin, ServerTaskScheduler scheduler, MapPhotoService photos, MapVideoService videos) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.photos = photos;
        this.videos = videos;
        this.stills = new StillMapAttachmentService(Bukkit::getMap, scheduler::runAsync, scheduler::runGlobal,
                failure -> plugin.getLogger().warning("Could not lazily load camera media: " + failure.getMessage()));
    }

    public void scanLoadedFrames() {
        for (var world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) {
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            scheduler.runRegion(world, chunkX, chunkZ, () -> {
                for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                    if (entity instanceof ItemFrame frame) reconcileFrame(frame, frame.getItem());
                }
            });
        }
    }

    public void refreshFrame(ItemFrame frame) { reconcileFrame(frame, frame.getItem()); }
    public void setVideoIndexRefresh(Runnable videoIndexRefresh) { this.videoIndexRefresh = videoIndexRefresh; }

    public void clear() { pendingVideos.clear(); stills.clear(); videos.clear(); }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) { reconcileHandsNextTick(event.getPlayer()); }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) { detachHands(event.getPlayer()); }
    @EventHandler public void onPlayerItemHeld(PlayerItemHeldEvent event) { reconcileHandsNextTick(event.getPlayer()); }
    @EventHandler public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) { reconcileHandsNextTick(event.getPlayer()); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) reconcileHandsNextTick(player); }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player player) reconcileHandsNextTick(player); }
    @EventHandler public void onEntityAdded(EntityAddToWorldEvent event) { if (event.getEntity() instanceof ItemFrame frame) refreshFrame(frame); }
    @EventHandler public void onEntityRemoved(EntityRemoveFromWorldEvent event) { if (event.getEntity() instanceof ItemFrame frame) detach(frameSource(frame)); }

    @EventHandler public void onItemFrameChanged(PlayerItemFrameChangeEvent event) {
        if (event.isCancelled()) return;
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE) detach(frameSource(event.getItemFrame()));
        else reconcileFrame(event.getItemFrame(), event.getItemStack());
    }

    private void reconcileHandsNextTick(Player player) {
        scheduler.runEntityDelayed(player, 1L, () -> reconcileHands(player), () -> detachHands(player));
    }

    private void reconcileHands(Player player) {
        reconcile(playerSource(player, "main"), player.getInventory().getItemInMainHand());
        reconcile(playerSource(player, "off"), player.getInventory().getItemInOffHand());
    }

    private void detachHands(Player player) {
        detach(playerSource(player, "main"));
        detach(playerSource(player, "off"));
    }

    private void reconcileFrame(ItemFrame frame, ItemStack item) { reconcile(frameSource(frame), item); }

    private void reconcile(String source, ItemStack item) {
        var descriptor = MediaMapDescriptor.from(item);
        if (descriptor.isEmpty()) {
            detach(source);
            return;
        }
        switch (descriptor.get()) {
            case MediaMapDescriptor.PhotoTile photo -> { videos.detach(source); pendingVideos.remove(source); stills.attach(source, photo, () -> photos.tilePixels(photo)); }
            case MediaMapDescriptor.VideoTile video -> activateVideo(source, video);
            case MediaMapDescriptor.PhotoBagPreview photo -> { videos.detach(source); pendingVideos.remove(source); stills.attach(source, photo,
                    () -> photos.previewPixels(new PhotoBagData(photo.mediaId(), PhotoBagKind.PHOTO, photo.mapId(), 1, 1))); }
            case MediaMapDescriptor.VideoBagPreview video -> { videos.detach(source); pendingVideos.remove(source); stills.attach(source, video,
                    () -> videos.previewPixels(new PhotoBagData(video.mediaId(), PhotoBagKind.VIDEO, video.mapId(), 1, 1))); }
        }
    }

    private void activateVideo(String source, MediaMapDescriptor.VideoTile tile) {
        if (tile.equals(pendingVideos.get(source))) return;
        stills.detach(source);
        videos.detach(source);
        pendingVideos.put(source, tile);
        scheduler.runAsync(() -> {
            try {
                MapVideoService.LoadedVideo loaded = videos.load(tile);
                scheduler.runGlobal(() -> {
                    if (!tile.equals(pendingVideos.get(source))) return;
                    videos.attach(source, tile, loaded);
                    videoIndexRefresh.run();
                });
            } catch (IOException exception) {
                if (tile.equals(pendingVideos.remove(source))) plugin.getLogger().warning("Could not lazily load video " + tile.mediaId() + ": " + exception.getMessage());
            }
        });
    }

    private void detach(String source) {
        pendingVideos.remove(source);
        stills.detach(source);
        videos.detach(source);
    }

    private static String playerSource(Player player, String hand) { return "player:" + player.getUniqueId() + ':' + hand; }
    private static String frameSource(ItemFrame frame) { return "frame:" + frame.getUniqueId(); }
}
