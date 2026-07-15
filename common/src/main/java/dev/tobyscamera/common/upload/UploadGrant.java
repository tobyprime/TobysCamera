package dev.tobyscamera.common.upload;

import java.time.Instant;
import java.util.UUID;

public record UploadGrant(UUID token, UUID playerId, Instant issuedAt, Instant expiresAt) {
    public UploadGrant {
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("grant must expire after it is issued");
        }
    }

    public boolean isValidFor(UUID playerId, Instant now) {
        return this.playerId.equals(playerId) && now.isBefore(expiresAt);
    }
}
