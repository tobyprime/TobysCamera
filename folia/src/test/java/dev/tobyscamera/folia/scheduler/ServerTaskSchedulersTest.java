package dev.tobyscamera.folia.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerTaskSchedulersTest {
    @Test
    void recognizesFoliaByItsServerName() {
        assertTrue(ServerTaskSchedulers.isFolia("Folia"));
        assertTrue(ServerTaskSchedulers.isFolia("folia"));
    }

    @Test
    void treatsPaperAsNonFolia() {
        assertFalse(ServerTaskSchedulers.isFolia("Paper"));
    }
}
