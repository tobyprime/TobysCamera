package dev.tobyscamera.common.upload;

import java.time.Instant;
import java.util.UUID;

public record UploadGrant(UUID token, UUID playerId, Instant issuedAt, Instant expiresAt, int gridSize) {
    public UploadGrant {
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("grant must expire after it is issued");
        }
        if (gridSize < 1) throw new IllegalArgumentException("grid size must be positive");
    }

    public boolean isValidFor(UUID playerId, Instant now) {
        return this.playerId.equals(playerId) && now.isBefore(expiresAt);
    }
}
