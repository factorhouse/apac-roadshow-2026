package com.ververica.composable_job.model;

public class ChatMessage {

    public String id;
    public String userId;
    public ChatMessageType type;
    public String message;
    public long timestamp;

    public boolean translated;

    public ChatMessage() {
    }

    public ChatMessage(String id, String userId, ChatMessageType type, String message, long timestamp) {
        this(id, userId, type, message, timestamp, false);
    }

    public ChatMessage(String id, String userId, ChatMessageType type, String message, long timestamp, boolean translated) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;
        this.translated = translated;
    }

    public static ChatMessage from(RawChatMessage raw, String messageId) {
        return new ChatMessage(
                messageId,
                raw.userId,
                raw.type,
                raw.message,
                raw.timestamp
        );
    }

    public static ChatMessage from(EnrichedChatMessage enrichedChatMessage, Language language) {
        String message = enrichedChatMessage.message;

        boolean isDefaultLanguage = Language.DEFAULT.equals(language);
        System.out.println("Current language: " + language + " isDefaultLanguage: " + isDefaultLanguage);
        if (!isDefaultLanguage) {
            if (enrichedChatMessage.translations.containsKey(language)) {
                message = enrichedChatMessage.translations.get(language);
            } else {
                isDefaultLanguage = true;
            }
        }

        boolean isTranslated = !isDefaultLanguage;


        System.out.println("Message: " + message + " isTranslated: " + isTranslated);

        return new ChatMessage(
                enrichedChatMessage.id,
                enrichedChatMessage.userId,
                enrichedChatMessage.type,
                message,
                enrichedChatMessage.timestamp,
                isTranslated
        );
    }
}
