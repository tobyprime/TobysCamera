package dev.tobyscamera.folia.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerLocalChunkRangeTest {
    @Test
    void includesOnlyChunksWhoseAreaCanIntersectThe128BlockRadius() {
        var chunks = PlayerLocalChunkRange.around(0, 0, 128);

        assertTrue(chunks.contains(new PlayerLocalChunkRange.Chunk(8, 0)));
        assertTrue(chunks.contains(new PlayerLocalChunkRange.Chunk(-8, 0)));
        assertTrue(!chunks.contains(new PlayerLocalChunkRange.Chunk(8, 8)));
    }

    @Test
    void checksTheActualFrameDistanceAfterChunkSelection() {
        assertTrue(PlayerLocalChunkRange.withinRadius(0, 64, 0, 128, 64, 0, 128));
        assertTrue(!PlayerLocalChunkRange.withinRadius(0, 64, 0, 128, 64, 128, 128));
    }
}
