package com.ververica.composable_job.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnrichedChatMessage {

    public String id;
    public String userId;
    public String toUserId;
    public ChatMessageType type;
    public String message;
    public long timestamp;

    // TODO check again
    public Map<Language, String> translations = Collections.synchronizedMap(new HashMap<>());

    public EnrichedChatMessage() {
    }

    public EnrichedChatMessage(String id, String userId, String toUserId, ChatMessageType type, String message, long timestamp, List<Language> languages) {
        this.id = id;
        this.userId = userId;
        this.toUserId = toUserId;
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;

        languages.forEach(language -> this.translations.put(language, null));
    }

    public static EnrichedChatMessage from(RawChatMessage raw, String messageId) {
        return new EnrichedChatMessage(
                messageId,
                raw.userId,
                raw.toUserId,
                raw.type,
                raw.message,
                raw.timestamp,
                raw.languages
        );
    }
}
