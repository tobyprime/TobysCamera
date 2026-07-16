package dev.tobyscamera.fabric.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChunkTokenBucketTest {
    @Test
    void allowsOnlyTheGrantedNumberOfChunksPerSecond() {
        ChunkTokenBucket bucket = new ChunkTokenBucket(2, 1_000L);
        assertEquals(2, bucket.takeAvailable(1_000L, 9));
        assertEquals(0, bucket.takeAvailable(1_000L, 9));
        assertEquals(1, bucket.takeAvailable(1_500L, 9));
        assertEquals(1, bucket.takeAvailable(2_000L, 9));
    }
}
