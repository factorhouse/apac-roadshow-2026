package com.ververica.composable_job.quarkus.websocket;

import com.ververica.composable_job.model.ChatMessage;
import com.ververica.composable_job.model.ProcessingEvent;

public class EventRouter {

    public static boolean isPublicMessage(ProcessingEvent<?> event) {
        return event.toUserId == null || !ProcessingEvent.Type.CHAT_MESSAGE.equals(event.eventType);
    }

    public static boolean shouldSendToUser(ProcessingEvent<ChatMessage> event, String user) {
        return user.equals(event.toUserId) || user.equals(event.payload.userId);
    }


}
