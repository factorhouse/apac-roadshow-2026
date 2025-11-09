package com.ververica.composable_job.quarkus.api;

import com.ververica.composable_job.model.ChatMessage;
import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.ProcessingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class MessageRoutingTest extends ChatWebsocketTestBase {

    @Test
    public void receiverShouldGetMessageTest() throws JsonProcessingException {
        openConnection(uriUser1);
        openConnection(uriUser2);

        ChatMessage chatMessage = new ChatMessage(null, "SomeSender", ChatMessageType.CHAT_MESSAGE, null, 0L);

        KAFKA_ADMIN.publishRecords(List.of(ProcessingEvent.of(chatMessage, "user2")), Topic.PROCESSING_FANOUT);

        String messageToUser2 = getWebsocketResponse();
        String nullMessage = getWebsocketResponse();
        assertTrue(messageToUser2.contains("\"type\":\"CHAT_MESSAGE\""));
        assertTrue(messageToUser2.contains("\"toUserId\":\"user2\""));
        assertNull(nullMessage, "Should receive message only once, as user2 is the receiver");
    }

    @Test
    public void senderShouldGetMessageTest() throws JsonProcessingException {
        openConnection(uriUser1);
        openConnection(uriUser2);

        ChatMessage chatMessage = new ChatMessage(null, "user1", ChatMessageType.CHAT_MESSAGE, null, 0L);

        KAFKA_ADMIN.publishRecords(List.of(ProcessingEvent.of(chatMessage, "SomeReceiver")), Topic.PROCESSING_FANOUT);

        String messageToUser1 = getWebsocketResponse();
        String nullMessage = getWebsocketResponse();
        assertTrue(messageToUser1.contains("\"type\":\"CHAT_MESSAGE\""));
        assertTrue(messageToUser1.contains("\"toUserId\":\"SomeReceiver\""));
        assertNull(nullMessage, "Should receive message only once, as user1 is the sender");
    }

    @Test
    public void otherUserShouldNotGetMessageTest() throws JsonProcessingException {
        openConnection(uriUser1);

        ChatMessage chatMessage = new ChatMessage(null, "SomeSender", ChatMessageType.CHAT_MESSAGE, null, 0L);

        KAFKA_ADMIN.publishRecords(List.of(ProcessingEvent.of(chatMessage, "SomeReceiver")), Topic.PROCESSING_FANOUT);

        String nullMessage = getWebsocketResponse();
        assertNull(nullMessage, "Should not receive any message, user1 is neither the sender nor the receiver");
    }

    @Test
    public void broadcastMessageTest() throws JsonProcessingException {
        openConnection(uriUser1);
        openConnection(uriUser2);
        ChatMessage userJoined = new ChatMessage(null, "user2", ChatMessageType.USER_JOINED, null, 0L);
        String broadcast = null;

        KAFKA_ADMIN.publishRecords(List.of(ProcessingEvent.of(userJoined, broadcast)), Topic.PROCESSING_FANOUT);

        String user2JoinForUser1 = getWebsocketResponse();
        String user2JoinForUser2 = getWebsocketResponse();

        assertTrue(user2JoinForUser1.contains("\"type\":\"USER_JOINED\""));
        assertTrue(user2JoinForUser1.contains("\"userId\":\"user2\""));
        assertTrue(user2JoinForUser2.contains("\"type\":\"USER_JOINED\""));
        assertTrue(user2JoinForUser2.contains("\"userId\":\"user2\""));
    }
}
