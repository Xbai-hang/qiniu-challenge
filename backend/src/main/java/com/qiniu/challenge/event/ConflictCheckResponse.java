package com.qiniu.challenge.event;

import java.util.List;

public record ConflictCheckResponse(
        boolean hasConflict,
        List<EventConflict> conflicts,
        boolean requiresConfirmation) {

    public static ConflictCheckResponse of(List<EventConflict> conflicts) {
        List<EventConflict> normalized = conflicts == null ? List.of() : conflicts;
        return new ConflictCheckResponse(!normalized.isEmpty(), normalized, !normalized.isEmpty());
    }
}
