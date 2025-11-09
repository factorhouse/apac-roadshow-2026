package com.ververica.composable_job.model;

import com.ververica.composable_job.model.ecommerce.*;
import java.time.Instant;
import java.util.UUID;

public class ProcessingEvent<T> {

    public String uuid;
    public long timestamp;
    public String key;
    public String toUserId;
    public Type eventType;
    public T payload;

    public ProcessingEvent() {
    }

    public ProcessingEvent(String uuid, long timestamp, String key, String toUserId, Type eventType, T payload) {
        this.uuid = uuid;
        this.timestamp = timestamp;
        this.key = key;
        this.toUserId = toUserId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public static ProcessingEvent<ChatMessage> of(ChatMessage message, String toUserId) {
        return new ProcessingEvent<>(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), null, toUserId, Type.CHAT_MESSAGE, message);
    }

    public static ProcessingEvent<RandomNumberPoint> of(RandomNumberPoint point) {
        return new ProcessingEvent<>(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), null, null, Type.RANDOM_NUMBER_POINT, point);
    }

    public static ProcessingEvent<OnlineUsers> of(OnlineUsers users) {
        return new ProcessingEvent<>(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), null, null, Type.ONLINE_USERS, users);
    }

    public static ProcessingEvent<GeoCoordinate> of(GeoCoordinate geoCoordinate) {
        return new ProcessingEvent<>(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), null, null, Type.GEO_COORDINATE, geoCoordinate);
    }

    public static ProcessingEvent<EnrichedChatMessage> of(EnrichedChatMessage enrichedChatMessage) {
        return new ProcessingEvent<>(
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(),
                null,
                enrichedChatMessage.toUserId,
                Type.CHAT_MESSAGE,
                enrichedChatMessage
        );
    }

    public static ProcessingEvent<EcommerceEvent> ofEcommerce(EcommerceEvent event) {
        return new ProcessingEvent<>(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), 
                event.sessionId, event.userId, Type.ECOMMERCE_EVENT, event);
    }

    public static ProcessingEvent<ShoppingCart> ofCart(ShoppingCart cart) {
        return new ProcessingEvent<>(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), 
                cart.sessionId, cart.userId, Type.SHOPPING_CART, cart);
    }

    public static ProcessingEvent<Recommendation> ofRecommendation(Recommendation rec) {
        return new ProcessingEvent<>(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), 
                rec.sessionId, rec.userId, Type.RECOMMENDATION, rec);
    }

    public static ProcessingEvent<Order> ofOrder(Order order) {
        return new ProcessingEvent<>(UUID.randomUUID().toString(), Instant.now().toEpochMilli(), 
                order.sessionId, order.userId, Type.ORDER_UPDATE, order);
    }

    public enum Type {
        CHAT_MESSAGE,
        RANDOM_NUMBER_POINT,
        ONLINE_USERS,
        GEO_COORDINATE,
        ECOMMERCE_EVENT,
        SHOPPING_CART,
        PRODUCT_UPDATE,
        RECOMMENDATION,
        ORDER_UPDATE,
    }
}
