package com.ververica.composable_job.quarkus.api;

import com.ververica.composable_job.model.RawChatMessage;
import com.ververica.composable_job.test.KafkaAdmin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.quarkus.logging.Log;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.quarkus.websockets.next.WebSocketClientConnection;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ChatWebsocketTestBase {

    private static final LinkedBlockingQueue<String> MESSAGES = new LinkedBlockingQueue<>();

    private static final List<WebSocketClientConnection> CONNECTIONS = new LinkedList<>();

    @Inject
    BasicWebSocketConnector connector;

    @TestHTTPResource("/chat/user1")
    protected URI uriUser1;

    @TestHTTPResource("/chat/user2")
    protected URI uriUser2;

    protected static final TypeReference<RawChatMessage> RAW_CHAT_MESSAGE_TYPE_REF = new TypeReference<>() {
    };

    protected static KafkaAdmin KAFKA_ADMIN;

    protected static class Topic {
        public static final String WEBSOCKET_FANOUT = "websocket_fanout";
        public static final String PROCESSING_FANOUT = "processing_fanout";
    }

    @BeforeAll
    protected static void setUpKafka() {
        KAFKA_ADMIN = new KafkaAdmin("localhost:19092");
        KAFKA_ADMIN.createTopic(Topic.WEBSOCKET_FANOUT);
        KAFKA_ADMIN.createTopic(Topic.PROCESSING_FANOUT);
    }

    @BeforeAll
    protected static void awaitHealthcheck() throws InterruptedException {
        int retries = 0;
        while (!isHealthCheckUp() && retries < 10) {
            Log.warn("Health check not ready. Retrying in 2 seconds...");
            TimeUnit.SECONDS.sleep(2);
            retries++;
        }
    }

    private static boolean isHealthCheckUp() {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) new URL("http://localhost:8081/q/health").openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            if (connection.getResponseCode() == 200)
                return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    @BeforeEach
    protected void cleanUpKafkaLeftovers() throws JsonProcessingException {
        KAFKA_ADMIN.getRecordValues(Topic.WEBSOCKET_FANOUT, RAW_CHAT_MESSAGE_TYPE_REF);
    }

    @AfterEach
    protected void cleanUpConnections() {
        CONNECTIONS.forEach(con -> {
            if (con.isOpen()) {
                con.closeAndAwait();
            }
        });
        CONNECTIONS.clear();
    }

    protected WebSocketClientConnection openConnection(URI uri) {
        WebSocketClientConnection connection = connector
                .baseUri(uri)
                .executionModel(BasicWebSocketConnector.ExecutionModel.BLOCKING)
                .onTextMessage((c, message) -> {
                    Log.infof("receiving some: " + message);
                    MESSAGES.add(message);
                })
                .connectAndAwait();
        CONNECTIONS.add(connection);
        return connection;
    }

    protected String getWebsocketResponse() {
        try {
            return MESSAGES.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
