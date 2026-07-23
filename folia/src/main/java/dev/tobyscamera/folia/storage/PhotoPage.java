package dev.tobyscamera.folia.storage;

import java.util.List;

public record PhotoPage(List<PhotoRecord> records, boolean hasNext) {
    public PhotoPage {
        records = List.copyOf(records);
    }
}
