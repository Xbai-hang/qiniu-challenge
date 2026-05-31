package com.qiniu.challenge.tts;

import java.util.Optional;

public interface TtsRepository {

    long create(CreateTtsCacheCommand command);

    Optional<TtsCacheEntry> findActive(long id, long userId);

    boolean messageBelongsToUser(long messageId, long userId);
}
