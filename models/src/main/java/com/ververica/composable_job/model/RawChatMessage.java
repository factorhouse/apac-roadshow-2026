package com.ververica.composable_job.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RawChatMessage {

    public String userId;
    public String toUserId;
    public ChatMessageType type;
    public String message;
    public long timestamp;
    public Language preferredLanguage;

    // Populated by API
    public List<Language> languages = Collections.synchronizedList(new ArrayList<>());

    public RawChatMessage() {
    }

    public RawChatMessage(String userId, String toUserId, ChatMessageType type, String message, long timestamp) {
        this(userId, toUserId, type, message, timestamp, Language.DEFAULT);
    }

    public RawChatMessage(String userId, String toUserId, ChatMessageType type, String message, long timestamp, Language preferredLanguage) {
        this.userId = userId;
        this.toUserId = toUserId;
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;
        this.preferredLanguage = preferredLanguage;
    }

    private RawChatMessage(String userId, String toUserId, ChatMessageType type, String message, long timestamp, List<Language> languages) {
        this(userId, toUserId, type, message, timestamp, Language.DEFAULT);

        this.languages = languages;
    }

    public RawChatMessage withLanguages(List<Language> languages) {
        RawChatMessage rawChatMessage = new RawChatMessage(userId, toUserId, type, message, timestamp, languages);
        rawChatMessage.preferredLanguage = null;

        return rawChatMessage;
    }
}
