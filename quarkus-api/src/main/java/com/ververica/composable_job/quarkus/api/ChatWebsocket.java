package com.ververica.composable_job.quarkus.api;

import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.GeoCoordinate;
import com.ververica.composable_job.model.OnlineUsers;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.RawChatMessage;
import com.ververica.composable_job.quarkus.geo.GeoLocationService;
import com.ververica.composable_job.quarkus.kafka.streams.CacheQueryService;
import com.ververica.composable_job.quarkus.websocket.WebsocketEmitter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import io.smallrye.reactive.messaging.annotations.Broadcast;

@WebSocket(path = "/chat/{username}")
@ApplicationScoped
public class ChatWebsocket {

    private final static ObjectMapper MAPPER = new ObjectMapper();

    @Channel("websocket_fanout")
    @Broadcast
    Emitter<RawChatMessage> emitter;

    @Inject
    WebSocketConnection connection;

    @Inject
    OpenConnections connections;

    @Inject
    CacheQueryService cacheQueryService;

    @Inject
    WebsocketEmitter websocketEmitter;

    @Inject
    GeoLocationService geoLocationService;

    @Inject
    LanguageStateService languageStateService;

    @OnOpen(broadcast = true)
    public void onOpen() throws InterruptedException {
        String username = connection.pathParam("username");

        RawChatMessage userHasJoined = new RawChatMessage(username, null, ChatMessageType.USER_JOINED, null, connection.creationTime().getEpochSecond());

        // TODO improve
        do {
            try {
                cacheQueryService.getLastChatMessages().forEach(message -> websocketEmitter.sendMessageToConnection(message, connection));
                cacheQueryService.getLastDataPoints().forEach(dataPoint -> websocketEmitter.sendMessageToConnection(dataPoint, connection));

                geoLocationService.getReadCoordinates().forEach(geoCoordinate -> connection.sendTextAndAwait(ProcessingEvent.of(geoCoordinate)));

                break;
            } catch (Exception e) {
                Log.warn("Failed to recover cache", e);
                Thread.sleep(1000);
            }
        } while (true);

        emitter.send(userHasJoined);
    }

    @OnClose
    public void onClose() {
        RawChatMessage departure = new RawChatMessage(connection.pathParam("username"), null, ChatMessageType.USER_LEFT, null, System.currentTimeMillis());

        Log.debugf("Offloading `OnClose` message {}", departure);
        emitter.send(departure);
    }

    @OnTextMessage(broadcast = true)
    public void onMessage(RawChatMessage message) {
        if (ChatMessageType.LANGUAGE_SET.equals(message.type)) {
            languageStateService.setUserLanguage(connection.pathParam("username"), message.preferredLanguage);
            return;
        }

        Log.debugf("Offloading `OnTextMessage` message {}", message);
        emitter.send(message.withLanguages(languageStateService.getLanguages()));
    }

    @Incoming("processing_fanout")
    @Blocking
    public void consumeMessage(String message) throws JsonProcessingException {
//        websocketEmitter.emmit(message);
    }

    @Scheduled(every = "1s")
    public void sendOnlineUsers() throws JsonProcessingException {
        OnlineUsers users = new OnlineUsers(
                connections.stream()
                        .map(webSocketConnection -> webSocketConnection.pathParam("username"))
                        .toList()
        );

        ProcessingEvent<OnlineUsers> onlineUsersProcessingEvent = ProcessingEvent.of(users);
        websocketEmitter.emmit(MAPPER.writeValueAsString(onlineUsersProcessingEvent));
    }

    @Scheduled(every = "1s")
    public void geoLocationPush() throws JsonProcessingException {
        GeoCoordinate geoCoordinate = geoLocationService.collectGeoCoordinate();

        if (geoCoordinate == null) {
            return;
        }

        websocketEmitter.emmit(MAPPER.writeValueAsString(ProcessingEvent.of(geoCoordinate)));
    }
}
