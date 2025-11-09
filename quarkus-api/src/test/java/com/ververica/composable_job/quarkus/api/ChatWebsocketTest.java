package com.ververica.composable_job.quarkus.api;

import com.ververica.composable_job.model.ChatMessage;
import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.RawChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.WebSocketClientConnection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class ChatWebsocketTest extends ChatWebsocketTestBase {

    @Test
    public void onOpenTest() throws JsonProcessingException {

        openConnection(uriUser1);

        List<RawChatMessage> values = KAFKA_ADMIN.getRecordValues(Topic.WEBSOCKET_FANOUT, RAW_CHAT_MESSAGE_TYPE_REF);
        assertEquals(1, values.size());
        RawChatMessage userJoined = values.get(0);
        assertEquals("user1", userJoined.userId);
        assertEquals(ChatMessageType.USER_JOINED, userJoined.type);
    }

    @Test
    public void IncomingUserJoinedTest() throws JsonProcessingException {
        openConnection(uriUser1);
        ChatMessage userJoined = new ChatMessage(null, "user1", ChatMessageType.USER_JOINED, null, 0L);

        KAFKA_ADMIN.publishRecords(List.of(ProcessingEvent.of(userJoined, null)), Topic.PROCESSING_FANOUT);

        String userJoinMsg = getWebsocketResponse();
        assertTrue(userJoinMsg.contains("\"type\":\"USER_JOINED\""));
        assertTrue(userJoinMsg.contains("\"userId\":\"user1\""));

    }

    @Test
    public void onTextMessageTest() throws JsonProcessingException {
        WebSocketClientConnection connection = openConnection(uriUser1);

        connection.sendTextAndAwait("{\"userId\":\"user1\",\"type\":\"CHAT_MESSAGE\",\"message\":\"Hello there!\"}");

        List<RawChatMessage> values = KAFKA_ADMIN.getRecordValues(Topic.WEBSOCKET_FANOUT, RAW_CHAT_MESSAGE_TYPE_REF);
        assertEquals(2, values.size()); // userJoinMsg, chatMessage
        RawChatMessage chatMessage = values.get(1);
        assertEquals("user1", chatMessage.userId);
        assertEquals(ChatMessageType.CHAT_MESSAGE, chatMessage.type);
        assertEquals("Hello there!", chatMessage.message);
    }

    @Test
    public void IncomingChatMessageTest() throws JsonProcessingException {
        openConnection(uriUser1);
        ChatMessage message = new ChatMessage(null, "user1", ChatMessageType.CHAT_MESSAGE, "Hello there!", 0L);

        KAFKA_ADMIN.publishRecords(List.of(ProcessingEvent.of(message, null)), Topic.PROCESSING_FANOUT);

        String textMsg = getWebsocketResponse();
        assertTrue(textMsg.contains("\"type\":\"CHAT_MESSAGE\""));
        assertTrue(textMsg.contains("\"userId\":\"user1\""));
        assertTrue(textMsg.contains("\"message\":\"Hello there!\""));
    }

    @Test
    public void onCloseTest() throws JsonProcessingException {
        WebSocketClientConnection connection = openConnection(uriUser1);

        connection.closeAndAwait();

        List<RawChatMessage> values = KAFKA_ADMIN.getRecordValues(Topic.WEBSOCKET_FANOUT, RAW_CHAT_MESSAGE_TYPE_REF);
        assertEquals(2, values.size()); // userJoinMsg, userLeftMsg
        RawChatMessage userLeft = values.get(1);
        assertEquals("user1", userLeft.userId);
        assertEquals(ChatMessageType.USER_LEFT, userLeft.type);
    }

    @Test
    public void IncomingUserLeftTest() throws JsonProcessingException {
        openConnection(uriUser1);
        ChatMessage userLeft = new ChatMessage(null, "user2", ChatMessageType.USER_LEFT, null, 0L);

        KAFKA_ADMIN.publishRecords(List.of(ProcessingEvent.of(userLeft, null)), Topic.PROCESSING_FANOUT);

        String userLeftMsg = getWebsocketResponse();
        assertTrue(userLeftMsg.contains("\"type\":\"USER_LEFT\""));
        assertTrue(userLeftMsg.contains("\"userId\":\"user2\""));
    }

}
