package dev.tobyscamera.folia.gallery;

import dev.tobyscamera.folia.delivery.MapItemDelivery;
import dev.tobyscamera.folia.map.MapPhotoService;
import dev.tobyscamera.folia.map.MediaMapActivationListener;
import dev.tobyscamera.folia.scheduler.ServerTaskScheduler;
import dev.tobyscamera.folia.storage.PhotoPage;
import dev.tobyscamera.folia.storage.PhotoOwner;
import dev.tobyscamera.folia.storage.PhotoOwnerPage;
import dev.tobyscamera.folia.storage.PhotoQuery;
import dev.tobyscamera.folia.storage.PhotoRecord;
import dev.tobyscamera.folia.storage.PhotoRepository;
import dev.tobyscamera.folia.storage.UploadBlock;
import dev.tobyscamera.folia.upload.PhotoMetadata;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Admin-only, read-mostly inventory UI for stored photos. */
public final class PhotoGalleryListener implements Listener {
    private static final Component LIST_TITLE = Component.text("TobysCamera 地图画图库", NamedTextColor.DARK_AQUA);
    private static final Component OWNER_PHOTOS_TITLE = Component.text("玩家地图画", NamedTextColor.DARK_AQUA);
    private static final Component DETAIL_TITLE = Component.text("地图画详情", NamedTextColor.DARK_AQUA);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final PhotoRepository repository;
    private final MapPhotoService photos;
    private final MediaMapActivationListener activation;
    private final ServerTaskScheduler scheduler;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Session> awaitingSearch = new ConcurrentHashMap<>();

    public PhotoGalleryListener(PhotoRepository repository, MapPhotoService photos, MediaMapActivationListener activation,
            ServerTaskScheduler scheduler) {
        this.repository = repository;
        this.photos = photos;
        this.activation = activation;
        this.scheduler = scheduler;
    }

    public void open(Player player) {
        if (!player.hasPermission("tobyscamera.admin")) {
            player.sendMessage(Component.text("You do not have permission to open the TobysCamera gallery."));
            return;
        }
        closeSession(player.getUniqueId());
        Session session = new Session(player.getUniqueId());
        sessions.put(player.getUniqueId(), session);
        showOwners(player, session);
    }

    private void showOwners(Player player, Session session) {
        if (!active(player, session)) return;
        session.selected = null;
        session.selectedOwner = null;
        session.allPhotos = false;
        session.page = null;
        activation.detachGalleryPreview(session.previewSource());
        long generation = ++session.generation;
        var query = new PhotoQuery(session.state.term(), session.state.sort(), session.ownerPage,
                PhotoGalleryState.PAGE_SIZE);
        scheduler.runAsync(() -> {
            try {
                PhotoOwnerPage owners = repository.findOwners(query);
                scheduler.runEntity(player, () -> {
                    if (!active(player, session) || session.generation != generation) return;
                    session.owners = owners;
                    Inventory inventory = inventory(session, LIST_TITLE);
                    for (int slot = 0; slot < owners.owners().size(); slot++) inventory.setItem(slot, ownerItem(owners.owners().get(slot)));
                    inventory.setItem(45, item(Material.ARROW, "上一页", "当前: " + (session.ownerPage + 1)));
                    inventory.setItem(46, item(Material.NAME_TAG, "按玩家/ID 搜索", session.state.term().isEmpty() ? "点击后在聊天栏输入" : "当前: " + session.state.term()));
                    inventory.setItem(47, item(Material.BARRIER, "清除筛选", "恢复全部玩家"));
                    inventory.setItem(48, item(Material.CLOCK, session.state.sort().name().equals("NEWEST") ? "排序: 最新优先" : "排序: 最早优先", "点击切换"));
                    inventory.setItem(49, item(Material.RED_STAINED_GLASS_PANE, "关闭", ""));
                    inventory.setItem(50, item(Material.ARROW, "下一页", owners.hasNext() ? "还有更多玩家" : "已到最后一页"));
                    inventory.setItem(51, item(Material.FILLED_MAP, "全部照片", "不按玩家聚合浏览"));
                    player.openInventory(inventory);
                }, () -> { });
            } catch (IOException exception) {
                failure(player, "Could not load photo owners", exception);
            }
        });
    }

    private void showAllPhotos(Player player, Session session, boolean resetPage) {
        if (!active(player, session)) return;
        session.selected = null;
        session.selectedOwner = null;
        session.allPhotos = true;
        if (resetPage) session.state.setTerm(session.state.term());
        activation.detachGalleryPreview(session.previewSource());
        long generation = ++session.generation;
        var query = session.state.query();
        scheduler.runAsync(() -> {
            try {
                PhotoPage page = repository.findPage(query);
                scheduler.runEntity(player, () -> {
                    if (!active(player, session) || session.generation != generation || !session.allPhotos) return;
                    session.page = page;
                    Inventory inventory = inventory(session, LIST_TITLE);
                    for (int slot = 0; slot < page.records().size(); slot++) inventory.setItem(slot, listItem(page.records().get(slot)));
                    inventory.setItem(45, item(Material.ARROW, "上一页", "当前: " + (session.state.page() + 1)));
                    inventory.setItem(46, item(Material.NAME_TAG, "按玩家/ID 搜索", session.state.term().isEmpty() ? "点击后在聊天栏输入" : "当前: " + session.state.term()));
                    inventory.setItem(47, item(Material.BARRIER, "清除筛选", "恢复全部照片"));
                    inventory.setItem(48, item(Material.CLOCK, session.state.sort().name().equals("NEWEST") ? "排序: 最新优先" : "排序: 最早优先", "点击切换"));
                    inventory.setItem(49, item(Material.RED_STAINED_GLASS_PANE, "关闭", ""));
                    inventory.setItem(50, item(Material.ARROW, "下一页", page.hasNext() ? "还有更多照片" : "已到最后一页"));
                    inventory.setItem(51, item(Material.FILLED_MAP, "玩家列表", "按玩家聚合浏览"));
                    player.openInventory(inventory);
                }, () -> { });
            } catch (IOException exception) {
                failure(player, "Could not load stored photos", exception);
            }
        });
    }

    private void showOwnerPhotos(Player player, Session session, PhotoOwner owner, boolean resetPage) {
        if (!active(player, session)) return;
        session.selected = null;
        session.selectedOwner = owner;
        session.allPhotos = false;
        if (resetPage) session.state.setTerm(session.state.term());
        long generation = ++session.generation;
        var query = session.state.query();
        scheduler.runAsync(() -> {
            try {
                PhotoPage page = repository.findPageForOwner(owner.ownerId(), query);
                scheduler.runEntity(player, () -> {
                    if (!active(player, session) || session.generation != generation || session.selectedOwner != owner) return;
                    session.page = page;
                    Inventory inventory = inventory(session, OWNER_PHOTOS_TITLE);
                    for (int slot = 0; slot < page.records().size(); slot++) inventory.setItem(slot, listItem(page.records().get(slot)));
                    inventory.setItem(45, item(Material.ARROW, "上一页", "当前: " + (session.state.page() + 1)));
                    inventory.setItem(46, playerHead(owner.ownerId(), "玩家", owner(owner), "共 " + owner.photoCount() + " 张照片"));
                    inventory.setItem(47, item(Material.ARROW, "返回玩家列表", ""));
                    inventory.setItem(48, item(Material.CLOCK, session.state.sort().name().equals("NEWEST") ? "排序: 最新优先" : "排序: 最早优先", "点击切换"));
                    inventory.setItem(49, item(Material.RED_STAINED_GLASS_PANE, "关闭", ""));
                    inventory.setItem(50, item(Material.ARROW, "下一页", page.hasNext() ? "还有更多照片" : "已到最后一页"));
                    player.openInventory(inventory);
                }, () -> { });
            } catch (IOException exception) {
                failure(player, "Could not load owner photos", exception);
            }
        });
    }

    private void showDetail(Player player, Session session, PhotoRecord record) {
        if (!active(player, session)) return;
        session.selected = record;
        long generation = ++session.generation;
        scheduler.runAsync(() -> {
            try {
                boolean blocked = repository.isBlocked(record.ownerId());
                scheduler.runEntity(player, () -> {
                    if (!active(player, session) || session.generation != generation || session.selected != record) return;
                    Inventory inventory = inventory(session, DETAIL_TITLE);
                    ItemStack preview = preview(player, session, record);
                    inventory.setItem(22, preview);
                    activation.attachGalleryPreview(player, session.previewSource(), preview);
                    inventory.setItem(0, playerHead(record.ownerId(), "上传者", owner(record), record.ownerId().toString()));
                    inventory.setItem(1, item(Material.CLOCK, "保存时间", TIME.format(record.createdAt())));
                    inventory.setItem(2, item(Material.FILLED_MAP, "尺寸", record.gridWidth() + " x " + record.gridHeight()));
                    inventory.setItem(3, item(Material.PAPER, "照片 ID", record.photoId().toString()));
                    fillMetadata(inventory, record.metadata());
                    inventory.setItem(45, item(session.deliveryArmed ? Material.REDSTONE_BLOCK : Material.CHEST,
                            session.deliveryArmed ? "再次点击确认领取照片袋" : "领取不可复制照片袋", "不会作为底片交付"));
                    inventory.setItem(46, item(session.blockArmed ? Material.REDSTONE_BLOCK : blocked ? Material.LIME_DYE : Material.RED_DYE,
                            session.blockArmed ? "再次点击确认" + (blocked ? "解除上传禁用" : "禁用该玩家上传")
                                    : blocked ? "解除上传禁用" : "禁用该玩家上传",
                            owner(record)));
                    inventory.setItem(47, item(session.deleteArmed ? Material.REDSTONE_BLOCK : Material.TNT,
                            session.deleteArmed ? "再次点击确认删除" : "删除此照片", "删除后无法恢复"));
                    inventory.setItem(48, item(Material.ARROW, "返回列表", ""));
                    inventory.setItem(49, item(Material.RED_STAINED_GLASS_PANE, "关闭", ""));
                    player.openInventory(inventory);
                }, () -> { });
            } catch (IOException exception) {
                failure(player, "Could not load photo details", exception);
            }
        });
    }

    private void fillMetadata(Inventory inventory, PhotoMetadata metadata) {
        if (metadata == null) {
            inventory.setItem(9, item(Material.GRAY_DYE, "拍摄信息", "旧照片未保存拍摄信息"));
            return;
        }
        inventory.setItem(9, item(Material.WRITABLE_BOOK, "拍摄者", metadata.photographer()));
        inventory.setItem(10, item(Material.COMPASS, "拍摄坐标", metadata.coordinates()));
        inventory.setItem(11, item(Material.CLOCK, "拍摄时间", metadata.capturedTime()));
        inventory.setItem(12, item(Material.NAME_TAG, "照片标题", empty(metadata.presentation().name())));
        inventory.setItem(13, item(Material.PAPER, "描述", empty(metadata.presentation().description())));
        inventory.setItem(14, item(Material.LEVER, "公开信息", "拍摄者: " + yesNo(metadata.presentation().publicPhotographer()),
                "坐标: " + yesNo(metadata.presentation().publicAddress()), "时间: " + yesNo(metadata.presentation().publicCapturedTime())));
    }

    private void handleListClick(Player player, Session session, int slot) {
        if (slot >= 0 && slot < 45) {
            if (!session.allPhotos && session.selectedOwner == null && session.owners != null && slot < session.owners.owners().size()) {
                showOwnerPhotos(player, session, session.owners.owners().get(slot), true);
                return;
            }
            if ((session.allPhotos || session.selectedOwner != null) && session.page != null && slot < session.page.records().size()) {
                showDetail(player, session, session.page.records().get(slot));
                return;
            }
        }
        switch (slot) {
            case 45 -> {
                if (!session.allPhotos && session.selectedOwner == null) {
                    if (session.ownerPage > 0) session.ownerPage--;
                } else session.state.previousPage();
                refreshList(player, session);
            }
            case 46 -> {
                if (session.allPhotos || session.selectedOwner == null) {
                    awaitingSearch.put(player.getUniqueId(), session);
                    player.closeInventory();
                    player.sendMessage(Component.text("输入玩家名称、UUID 前缀或照片 ID 前缀进行搜索。"));
                }
            }
            case 47 -> {
                if (session.allPhotos) {
                    session.state.clearTerm();
                    showAllPhotos(player, session, false);
                } else if (session.selectedOwner == null) {
                    session.state.clearTerm();
                    session.ownerPage = 0;
                    showOwners(player, session);
                } else showOwners(player, session);
            }
            case 48 -> {
                session.state.toggleSort();
                session.ownerPage = 0;
                refreshList(player, session);
            }
            case 49 -> player.closeInventory();
            case 50 -> {
                boolean hasNext = !session.allPhotos && session.selectedOwner == null
                        ? session.owners != null && session.owners.hasNext()
                        : session.page != null && session.page.hasNext();
                if (hasNext) {
                    if (!session.allPhotos && session.selectedOwner == null) session.ownerPage++;
                    else session.state.nextPage();
                    refreshList(player, session);
                }
            }
            case 51 -> {
                if (session.allPhotos) showOwners(player, session);
                else if (session.selectedOwner == null) showAllPhotos(player, session, true);
            }
            default -> { }
        }
    }

    private void refreshList(Player player, Session session) {
        if (session.allPhotos) showAllPhotos(player, session, false);
        else if (session.selectedOwner == null) showOwners(player, session);
        else showOwnerPhotos(player, session, session.selectedOwner, false);
    }

    private void handleDetailClick(Player player, Session session, int slot) {
        PhotoRecord record = session.selected;
        if (record == null) { refreshList(player, session); return; }
        switch (slot) {
            case 45 -> deliver(player, session, record);
            case 46 -> toggleBlock(player, session, record);
            case 47 -> delete(player, session, record);
            case 48 -> {
                session.deliveryArmed = false;
                session.blockArmed = false;
                session.deleteArmed = false;
                refreshList(player, session);
            }
            case 49 -> player.closeInventory();
            default -> { }
        }
    }

    private void deliver(Player player, Session session, PhotoRecord record) {
        if (!session.deliveryArmed) {
            session.blockArmed = false;
            session.deleteArmed = false;
            session.deliveryArmed = true;
            showDetail(player, session, record);
            return;
        }
        if (!active(player, session)) return;
        session.deliveryArmed = false;
        ItemStack bag = photos.adminBag(player.getWorld(), record);
        MapItemDelivery.deliver(java.util.List.of(bag), player.getInventory()::addItem,
                item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Component.text("照片袋已交付。"));
        showDetail(player, session, record);
    }

    private void toggleBlock(Player player, Session session, PhotoRecord record) {
        if (!session.blockArmed) {
            session.deliveryArmed = false;
            session.deleteArmed = false;
            session.blockArmed = true;
            showDetail(player, session, record);
            return;
        }
        if (!active(player, session)) return;
        session.blockArmed = false;
        scheduler.runAsync(() -> {
            try {
                if (repository.isBlocked(record.ownerId())) repository.unblock(record.ownerId());
                else repository.block(new UploadBlock(record.ownerId(), player.getUniqueId(), java.time.Instant.now()));
                scheduler.runEntity(player, () -> {
                    if (active(player, session) && session.selected == record) showDetail(player, session, record);
                }, () -> { });
            } catch (IOException exception) { failure(player, "Could not update photo upload block", exception); }
        });
    }

    private void delete(Player player, Session session, PhotoRecord record) {
        if (!session.deleteArmed) {
            session.deliveryArmed = false;
            session.blockArmed = false;
            session.deleteArmed = true;
            showDetail(player, session, record);
            return;
        }
        if (!active(player, session)) return;
        session.deleteArmed = false;
        scheduler.runAsync(() -> {
            try {
                photos.delete(record.photoId());
                scheduler.runEntity(player, () -> {
                    if (!active(player, session) || session.selected != record) return;
                    player.sendMessage(Component.text("照片已删除。"));
                    refreshList(player, session);
                }, () -> { });
            } catch (IOException exception) { failure(player, "Could not delete photo", exception); }
        });
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof GalleryHolder holder)) return;
        event.setCancelled(true);
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.holder != holder || event.getRawSlot() < 0 || event.getRawSlot() >= 54) return;
        if (!player.hasPermission("tobyscamera.admin")) { player.closeInventory(); return; }
        if (session.selected == null) handleListClick(player, session, event.getRawSlot()); else handleDetailClick(player, session, event.getRawSlot());
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GalleryHolder) event.setCancelled(true);
    }

    @EventHandler public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Session captured = awaitingSearch.get(player.getUniqueId());
        if (captured == null) return;
        event.setCancelled(true);
        String term = PlainTextComponentSerializer.plainText().serialize(event.message());
        scheduler.runEntity(player, () -> {
            if (!awaitingSearch.remove(player.getUniqueId(), captured) || sessions.get(player.getUniqueId()) != captured) return;
            Session session = captured;
            session.state.setTerm(term);
            session.ownerPage = 0;
            if (session.allPhotos) showAllPhotos(player, session, false);
            else showOwners(player, session);
        }, () -> { });
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof GalleryHolder holder)) return;
        scheduler.runEntityDelayed(player, 1L, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == holder) return;
            Session session = sessions.get(player.getUniqueId());
            if (awaitingSearch.containsKey(player.getUniqueId())) return;
            if (session != null && session.holder == holder) closeSession(player.getUniqueId());
        }, () -> closeSession(player.getUniqueId()));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { closeSession(event.getPlayer().getUniqueId()); }

    private Inventory inventory(Session session, Component title) {
        GalleryHolder holder = new GalleryHolder(session.playerId);
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inventory;
        session.holder = holder;
        return inventory;
    }

    private void closeSession(UUID playerId) {
        Session removed = sessions.remove(playerId);
        if (removed != null) awaitingSearch.remove(playerId, removed);
        if (removed != null) activation.detachGalleryPreview(removed.previewSource());
    }

    private boolean active(Player player, Session session) {
        if (sessions.get(player.getUniqueId()) != session) return false;
        if (player.hasPermission("tobyscamera.admin")) return true;
        closeSession(player.getUniqueId());
        player.closeInventory();
        return false;
    }

    private ItemStack preview(Player player, Session session, PhotoRecord record) {
        if (session.previewMapId < 0) session.previewMapId = photos.allocateGalleryPreviewMapId();
        return photos.galleryPreview(record, session.previewMapId);
    }

    private void failure(Player player, String message, IOException exception) {
        scheduler.runEntity(player, () -> player.sendMessage(Component.text(message + ".")), () -> { });
    }

    private static ItemStack ownerItem(PhotoOwner owner) { return playerHead(owner.ownerId(), owner(owner), "照片数量: " + owner.photoCount(),
            "最近保存: " + TIME.format(owner.latestCreatedAt()), "UUID: " + owner.ownerId().toString()); }
    private static ItemStack listItem(PhotoRecord record) { return playerHead(record.ownerId(), owner(record), "时间: " + TIME.format(record.createdAt()),
            "尺寸: " + record.gridWidth() + " x " + record.gridHeight(), "ID: " + record.photoId().toString().substring(0, 8)); }
    private static String owner(PhotoRecord record) { return record.ownerName() == null || record.ownerName().isBlank() ? record.ownerId().toString() : record.ownerName(); }
    private static String owner(PhotoOwner owner) { return owner.ownerName() == null || owner.ownerName().isBlank() ? owner.ownerId().toString() : owner.ownerName(); }
    private static String yesNo(boolean value) { return value ? "是" : "否"; }
    private static String empty(String value) { return value == null || value.isBlank() ? "无" : value; }
    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack result = new ItemStack(material);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        meta.lore(java.util.Arrays.stream(lore).filter(value -> !value.isEmpty()).map(value -> Component.text(value, NamedTextColor.GRAY)).toList());
        result.setItemMeta(meta);
        return result;
    }

    private static ItemStack playerHead(UUID playerId, String name, String... lore) {
        ItemStack result = item(Material.PLAYER_HEAD, name, lore);
        SkullMeta meta = (SkullMeta) result.getItemMeta();
        meta.setPlayerProfile(Bukkit.getOfflinePlayer(playerId).getPlayerProfile());
        result.setItemMeta(meta);
        return result;
    }

    private static final class Session {
        private final UUID playerId;
        private final UUID sessionId = UUID.randomUUID();
        private final PhotoGalleryState state = new PhotoGalleryState();
        private GalleryHolder holder;
        private PhotoOwnerPage owners;
        private PhotoPage page;
        private PhotoOwner selectedOwner;
        private boolean allPhotos;
        private int ownerPage;
        private PhotoRecord selected;
        private boolean deliveryArmed;
        private boolean blockArmed;
        private boolean deleteArmed;
        private long generation;
        private int previewMapId = -1;
        private Session(UUID playerId) { this.playerId = playerId; }
        private String previewSource() { return "gallery:" + playerId + ':' + sessionId; }
    }

    private static final class GalleryHolder implements InventoryHolder {
        private final UUID playerId;
        private Inventory inventory;
        private GalleryHolder(UUID playerId) { this.playerId = playerId; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
