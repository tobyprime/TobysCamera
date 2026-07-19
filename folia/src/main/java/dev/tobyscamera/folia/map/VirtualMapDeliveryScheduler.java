package dev.tobyscamera.folia.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Queues virtual-map reads and sends so visible media loads progressively. */
public final class VirtualMapDeliveryScheduler {
    public static final int TILE_BYTES = 16_384;

    private final Consumer<Runnable> async;
    private final Consumer<Runnable> global;
    private final Consumer<IOException> failures;
    private final VirtualMapPacketSender sender;
    private final Map<String, Source> sourcesById = new HashMap<>();
    private final Map<DemandKey, Demand> demandsByKey = new LinkedHashMap<>();
    private final Map<UUID, Long> dispatchCounts = new HashMap<>();
    private Limits limits;
    private int inFlightReads;
    private long nextSequence;

    public VirtualMapDeliveryScheduler(Consumer<Runnable> async, Consumer<Runnable> global,
            Consumer<IOException> failures, VirtualMapPacketSender sender, Limits limits) {
        this.async = async;
        this.global = global;
        this.failures = failures;
        this.sender = sender;
        this.limits = limits;
    }

    public synchronized void setLimits(Limits limits) { this.limits = limits; }

    public synchronized void attach(String sourceId, Player player, int mapId, Priority priority,
            long distanceSquared, PixelLoader loader) {
        if (mapId < 1) throw new IllegalArgumentException("virtual map id must be positive");
        if (distanceSquared < 0L) throw new IllegalArgumentException("distance cannot be negative");
        detach(sourceId);
        DemandKey key = new DemandKey(player.getUniqueId(), mapId);
        Demand demand = demandsByKey.computeIfAbsent(key, ignored -> new Demand(key, nextSequence++));
        Source source = new Source(sourceId, player, mapId, priority, distanceSquared, loader);
        sourcesById.put(sourceId, source);
        demand.sources.put(sourceId, source);
    }

    public synchronized void detach(String sourceId) {
        Source source = sourcesById.remove(sourceId);
        if (source == null) return;
        DemandKey key = new DemandKey(source.player.getUniqueId(), source.mapId());
        Demand demand = demandsByKey.get(key);
        if (demand == null) return;
        demand.sources.remove(sourceId);
        if (demand.sources.isEmpty()) demandsByKey.remove(key);
    }

    public synchronized void clear() {
        sourcesById.clear();
        demandsByKey.clear();
    }

    /** Must run once per global server tick. */
    public synchronized void tick() {
        TickBudget budget = new TickBudget(limits);
        while (true) {
            Demand demand = selectReadyDemand(budget);
            if (demand == null) break;
            send(demand, budget);
        }
        ReadAdmission admission = new ReadAdmission(limits);
        for (Demand demand : demandsByKey.values()) {
            if (!demand.sent && (demand.pixels != null || demand.reading)) admission.reserve(demand.key.playerId());
        }
        while (inFlightReads < limits.maxConcurrentReads()) {
            Demand demand = selectUnreadDemand(admission);
            if (demand == null) break;
            startRead(demand);
            admission.reserve(demand.key.playerId());
        }
    }

    private Demand selectReadyDemand(TickBudget budget) {
        return demandsByKey.values().stream()
                .filter(demand -> demand.pixels != null && !demand.sent && budget.canSend(demand.key.playerId()))
                .min(demandOrder())
                .orElse(null);
    }

    private Demand selectUnreadDemand(ReadAdmission admission) {
        return demandsByKey.values().stream()
                .filter(demand -> !demand.sent && demand.pixels == null && !demand.reading
                        && admission.canReserve(demand.key.playerId()))
                .min(demandOrder())
                .orElse(null);
    }

    private Comparator<Demand> demandOrder() {
        return Comparator.comparing((Demand demand) -> demand.bestSource().priority)
                .thenComparingLong(demand -> demand.bestSource().distanceSquared)
                .thenComparingLong(demand -> dispatchCounts.getOrDefault(demand.key.playerId(), 0L))
                .thenComparingLong(demand -> demand.sequence);
    }

    private void startRead(Demand demand) {
        demand.reading = true;
        inFlightReads++;
        Source source = demand.bestSource();
        async.accept(() -> {
            byte[] pixels = null;
            IOException failure = null;
            try {
                pixels = source.loader.load();
                if (pixels == null || pixels.length != TILE_BYTES) throw new IOException("media tile is unavailable");
            } catch (IOException exception) {
                failure = exception;
            }
            byte[] result = pixels;
            IOException error = failure;
            global.accept(() -> completeRead(demand, result, error));
        });
        countDispatch(demand.key.playerId());
    }

    private synchronized void completeRead(Demand demand, byte[] pixels, IOException failure) {
        inFlightReads--;
        if (demandsByKey.get(demand.key) != demand) return;
        demand.reading = false;
        if (failure != null) {
            failures.accept(failure);
            return;
        }
        demand.pixels = pixels;
    }

    private void send(Demand demand, TickBudget budget) {
        sender.sendFull(demand.bestSource().player, demand.key.mapId(), demand.pixels);
        demand.sent = true;
        demand.pixels = null;
        budget.consume(demand.key.playerId());
        countDispatch(demand.key.playerId());
    }

    private void countDispatch(UUID playerId) {
        dispatchCounts.merge(playerId, 1L, Long::sum);
    }

    public enum Priority { MAIN_HAND, OFF_HAND, FRAME }

    public record DemandKey(UUID playerId, int mapId) { }

    public record Limits(int maxConcurrentReads, int perPlayerMapsPerTick,
            long perPlayerBytesPerTick, long globalBytesPerTick) {
        public Limits {
            if (maxConcurrentReads < 1 || perPlayerMapsPerTick < 1
                    || perPlayerBytesPerTick < TILE_BYTES || globalBytesPerTick < TILE_BYTES) {
                throw new IllegalArgumentException("virtual map delivery limits are invalid");
            }
        }
    }

    @FunctionalInterface
    public interface PixelLoader { byte[] load() throws IOException; }

    private static final class Source {
        final String id;
        final Player player;
        final int mapId;
        final Priority priority;
        final long distanceSquared;
        final PixelLoader loader;

        Source(String id, Player player, int mapId, Priority priority, long distanceSquared, PixelLoader loader) {
            this.id = id;
            this.player = player;
            this.mapId = mapId;
            this.priority = priority;
            this.distanceSquared = distanceSquared;
            this.loader = loader;
        }

        int mapId() { return mapId; }
    }

    private static final class Demand {
        final DemandKey key;
        final long sequence;
        final Map<String, Source> sources = new LinkedHashMap<>();
        boolean reading;
        boolean sent;
        byte[] pixels;

        Demand(DemandKey key, long sequence) { this.key = key; this.sequence = sequence; }

        Source bestSource() {
            return sources.values().stream().min(Comparator.comparing((Source source) -> source.priority)
                    .thenComparingLong(source -> source.distanceSquared)).orElseThrow();
        }
    }

    private static final class TickBudget {
        private final Limits limits;
        private final Map<UUID, Integer> mapsByPlayer = new HashMap<>();
        private final Map<UUID, Long> bytesByPlayer = new HashMap<>();
        private long globalBytes;

        TickBudget(Limits limits) { this.limits = limits; }

        boolean canSend(UUID playerId) {
            return mapsByPlayer.getOrDefault(playerId, 0) < limits.perPlayerMapsPerTick()
                    && bytesByPlayer.getOrDefault(playerId, 0L) + TILE_BYTES <= limits.perPlayerBytesPerTick()
                    && globalBytes + TILE_BYTES <= limits.globalBytesPerTick();
        }

        void consume(UUID playerId) {
            mapsByPlayer.merge(playerId, 1, Integer::sum);
            bytesByPlayer.merge(playerId, (long) TILE_BYTES, Long::sum);
            globalBytes += TILE_BYTES;
        }
    }

    private static final class ReadAdmission {
        private final Limits limits;
        private final Map<UUID, Integer> mapsByPlayer = new HashMap<>();
        private final Map<UUID, Long> bytesByPlayer = new HashMap<>();
        private long globalBytes;

        ReadAdmission(Limits limits) { this.limits = limits; }

        boolean canReserve(UUID playerId) {
            return mapsByPlayer.getOrDefault(playerId, 0) < limits.perPlayerMapsPerTick()
                    && bytesByPlayer.getOrDefault(playerId, 0L) + TILE_BYTES <= limits.perPlayerBytesPerTick()
                    && globalBytes + TILE_BYTES <= limits.globalBytesPerTick();
        }

        void reserve(UUID playerId) {
            mapsByPlayer.merge(playerId, 1, Integer::sum);
            bytesByPlayer.merge(playerId, (long) TILE_BYTES, Long::sum);
            globalBytes += TILE_BYTES;
        }
    }
}
