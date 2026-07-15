package dev.tobyscamera.folia.delivery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PendingDeliveryRepository {
    private final Path file;
    private final List<Entry> entries = new ArrayList<>();

    public PendingDeliveryRepository(Path dataDirectory) throws IOException {
        file = dataDirectory.resolve("pending-deliveries.txt");
        if (Files.exists(file)) for (String line : Files.readAllLines(file)) {
            String[] pieces = line.split(" ");
            if (pieces.length == 2) entries.add(new Entry(UUID.fromString(pieces[0]), UUID.fromString(pieces[1])));
        }
    }

    public synchronized void add(UUID playerId, UUID photoId) throws IOException { entries.add(new Entry(playerId, photoId)); save(); }
    public synchronized List<UUID> take(UUID playerId) throws IOException {
        List<UUID> result = entries.stream().filter(entry -> entry.playerId.equals(playerId)).map(Entry::photoId).toList();
        entries.removeIf(entry -> entry.playerId.equals(playerId)); save(); return result;
    }
    private void save() throws IOException {
        Files.write(file, entries.stream().map(entry -> entry.playerId + " " + entry.photoId).toList(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    private record Entry(UUID playerId, UUID photoId) { }
}
