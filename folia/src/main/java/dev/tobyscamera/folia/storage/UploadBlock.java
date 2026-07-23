package dev.tobyscamera.folia.storage;

import java.time.Instant;
import java.util.UUID;

public record UploadBlock(UUID playerId, UUID adminId, Instant blockedAt) { }
