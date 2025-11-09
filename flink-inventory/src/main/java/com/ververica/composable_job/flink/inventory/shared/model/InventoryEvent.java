package com.ververica.composable_job.flink.inventory.shared.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Main event class representing inventory state changes.
 * Immutable and thread-safe with builder pattern for construction.
 */
public final class InventoryEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String productId;
    private final String productName;
    private final EventType eventType;
    private final Integer previousInventory;
    private final Integer currentInventory;
    private final Double previousPrice;
    private final Double currentPrice;
    private final long timestamp;
    private final String changeReason;
    private final String alertLevel;

    private InventoryEvent(Builder builder) {
        this.productId = builder.productId;
        this.productName = builder.productName;
        this.eventType = builder.eventType;
        this.previousInventory = builder.previousInventory;
        this.currentInventory = builder.currentInventory;
        this.previousPrice = builder.previousPrice;
        this.currentPrice = builder.currentPrice;
        this.timestamp = builder.timestamp;
        this.changeReason = builder.changeReason;
        this.alertLevel = builder.alertLevel;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Integer getPreviousInventory() {
        return previousInventory;
    }

    public Integer getCurrentInventory() {
        return currentInventory;
    }

    public Double getPreviousPrice() {
        return previousPrice;
    }

    public Double getCurrentPrice() {
        return currentPrice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    /**
     * Calculate the inventory change delta
     */
    public int getInventoryChange() {
        if (previousInventory == null || currentInventory == null) {
            return 0;
        }
        return currentInventory - previousInventory;
    }

    /**
     * Calculate the price change delta
     */
    public double getPriceChange() {
        if (previousPrice == null || currentPrice == null) {
            return 0.0;
        }
        return currentPrice - previousPrice;
    }

    /**
     * Check if this event represents a critical alert
     */
    public boolean isCritical() {
        return "CRITICAL".equalsIgnoreCase(alertLevel) ||
               eventType == EventType.OUT_OF_STOCK;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryEvent that = (InventoryEvent) o;
        return timestamp == that.timestamp &&
               Objects.equals(productId, that.productId) &&
               Objects.equals(productName, that.productName) &&
               eventType == that.eventType &&
               Objects.equals(previousInventory, that.previousInventory) &&
               Objects.equals(currentInventory, that.currentInventory) &&
               Objects.equals(previousPrice, that.previousPrice) &&
               Objects.equals(currentPrice, that.currentPrice) &&
               Objects.equals(changeReason, that.changeReason) &&
               Objects.equals(alertLevel, that.alertLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, productName, eventType, previousInventory,
                          currentInventory, previousPrice, currentPrice,
                          timestamp, changeReason, alertLevel);
    }

    @Override
    public String toString() {
        return "InventoryEvent{" +
               "productId='" + productId + '\'' +
               ", productName='" + productName + '\'' +
               ", eventType=" + eventType +
               ", previousInventory=" + previousInventory +
               ", currentInventory=" + currentInventory +
               ", previousPrice=" + previousPrice +
               ", currentPrice=" + currentPrice +
               ", timestamp=" + Instant.ofEpochMilli(timestamp) +
               ", changeReason='" + changeReason + '\'' +
               ", alertLevel='" + alertLevel + '\'' +
               '}';
    }

    /**
     * Builder for InventoryEvent with validation
     */
    public static final class Builder {
        private String productId;
        private String productName;
        private EventType eventType;
        private Integer previousInventory;
        private Integer currentInventory;
        private Double previousPrice;
        private Double currentPrice;
        private long timestamp = System.currentTimeMillis();
        private String changeReason;
        private String alertLevel;

        private Builder() {}

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }

        public Builder eventType(EventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder previousInventory(Integer previousInventory) {
            this.previousInventory = previousInventory;
            return this;
        }

        public Builder currentInventory(Integer currentInventory) {
            this.currentInventory = currentInventory;
            return this;
        }

        public Builder previousPrice(Double previousPrice) {
            this.previousPrice = previousPrice;
            return this;
        }

        public Builder currentPrice(Double currentPrice) {
            this.currentPrice = currentPrice;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder changeReason(String changeReason) {
            this.changeReason = changeReason;
            return this;
        }

        public Builder alertLevel(String alertLevel) {
            this.alertLevel = alertLevel;
            return this;
        }

        public InventoryEvent build() {
            // Validate required fields
            validateRequired(productId, "productId");
            validateRequired(productName, "productName");

            if (eventType == null) {
                throw new IllegalArgumentException("eventType is required");
            }

            if (timestamp <= 0) {
                throw new IllegalArgumentException("Timestamp must be positive");
            }

            // Validate inventory values if present
            if (previousInventory != null && previousInventory < 0) {
                throw new IllegalArgumentException("previousInventory cannot be negative");
            }

            if (currentInventory != null && currentInventory < 0) {
                throw new IllegalArgumentException("currentInventory cannot be negative");
            }

            // Validate price values if present
            if (previousPrice != null && previousPrice < 0) {
                throw new IllegalArgumentException("previousPrice cannot be negative");
            }

            if (currentPrice != null && currentPrice < 0) {
                throw new IllegalArgumentException("currentPrice cannot be negative");
            }

            // Validate event type specific requirements
            validateEventTypeRequirements();

            return new InventoryEvent(this);
        }

        private void validateRequired(String value, String fieldName) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
        }

        private void validateEventTypeRequirements() {
            switch (eventType) {
                case INVENTORY_INCREASED:
                case INVENTORY_DECREASED:
                    if (currentInventory == null) {
                        throw new IllegalArgumentException(
                            eventType + " requires currentInventory");
                    }
                    break;

                case PRICE_CHANGED:
                    if (currentPrice == null) {
                        throw new IllegalArgumentException(
                            eventType + " requires currentPrice");
                    }
                    break;

                case OUT_OF_STOCK:
                    if (currentInventory != null && currentInventory > 0) {
                        throw new IllegalArgumentException(
                            "OUT_OF_STOCK event should have currentInventory of 0");
                    }
                    break;

                case LOW_STOCK_ALERT:
                    if (alertLevel == null || alertLevel.trim().isEmpty()) {
                        this.alertLevel = "WARNING";
                    }
                    break;

                default:
                    // Other event types don't have special requirements
                    break;
            }
        }
    }
}
