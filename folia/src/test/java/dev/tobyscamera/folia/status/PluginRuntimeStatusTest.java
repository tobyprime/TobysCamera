package dev.tobyscamera.folia.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tobyscamera.folia.storage.PhotoStorageStats;
import org.junit.jupiter.api.Test;

class PluginRuntimeStatusTest {
    @Test
    void preservesStoredTotalsAndCountsPersistedUploadsForThisRun() {
        PluginRuntimeStatus status = new PluginRuntimeStatus(new PhotoStorageStats(8, 21));
        status.recordPersisted(4);
        assertEquals(new PluginRuntimeStatus.Totals(1, 4, 9, 25), status.totals());
    }
}
