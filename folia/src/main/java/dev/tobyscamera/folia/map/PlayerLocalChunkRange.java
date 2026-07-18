package dev.tobyscamera.folia.map;

import java.util.LinkedHashSet;
import java.util.Set;

/** Chunk areas that can intersect a player's horizontal block radius. */
final class PlayerLocalChunkRange {
    private static final int CHUNK_SIZE = 16;

    private PlayerLocalChunkRange() { }

    static Set<Chunk> around(int blockX, int blockZ, int radiusBlocks) {
        if (radiusBlocks < 0) throw new IllegalArgumentException("radius must not be negative");
        int minX = Math.floorDiv(blockX - radiusBlocks, CHUNK_SIZE);
        int maxX = Math.floorDiv(blockX + radiusBlocks, CHUNK_SIZE);
        int minZ = Math.floorDiv(blockZ - radiusBlocks, CHUNK_SIZE);
        int maxZ = Math.floorDiv(blockZ + radiusBlocks, CHUNK_SIZE);
        long radiusSquared = (long) radiusBlocks * radiusBlocks;
        Set<Chunk> chunks = new LinkedHashSet<>();
        for (int chunkX = minX; chunkX <= maxX; chunkX++) for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
            long distanceX = distanceToChunk(blockX, chunkX);
            long distanceZ = distanceToChunk(blockZ, chunkZ);
            if (distanceX * distanceX + distanceZ * distanceZ <= radiusSquared) chunks.add(new Chunk(chunkX, chunkZ));
        }
        return Set.copyOf(chunks);
    }

    static boolean withinRadius(double originX, double originY, double originZ,
            double targetX, double targetY, double targetZ, int radiusBlocks) {
        double x = targetX - originX;
        double y = targetY - originY;
        double z = targetZ - originZ;
        return x * x + y * y + z * z <= (double) radiusBlocks * radiusBlocks;
    }

    private static long distanceToChunk(int coordinate, int chunk) {
        int minimum = chunk * CHUNK_SIZE;
        int maximum = minimum + CHUNK_SIZE - 1;
        if (coordinate < minimum) return (long) minimum - coordinate;
        if (coordinate > maximum) return (long) coordinate - maximum;
        return 0L;
    }

    record Chunk(int x, int z) { }
}
