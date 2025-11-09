package com.ververica.composable_job.flink.inventory.shared.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents an alert event for side output processing.
 * Used to notify about critical inventory conditions.
 */
public final class AlertEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String alertType;
    private final String productId;
    private final String message;
    private final String severity;
    private final long timestamp;

    private AlertEvent(Builder builder) {
        this.alertType = builder.alertType;
        this.productId = builder.productId;
        this.message = builder.message;
        this.severity = builder.severity;
        this.timestamp = builder.timestamp;
    }

    public String getAlertType() {
        return alertType;
    }

    public String getProductId() {
        return productId;
    }

    public String getMessage() {
        return message;
    }

    public String getSeverity() {
        return severity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertEvent that = (AlertEvent) o;
        return timestamp == that.timestamp &&
               Objects.equals(alertType, that.alertType) &&
               Objects.equals(productId, that.productId) &&
               Objects.equals(message, that.message) &&
               Objects.equals(severity, that.severity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertType, productId, message, severity, timestamp);
    }

    @Override
    public String toString() {
        return "AlertEvent{" +
               "alertType='" + alertType + '\'' +
               ", productId='" + productId + '\'' +
               ", message='" + message + '\'' +
               ", severity='" + severity + '\'' +
               ", timestamp=" + Instant.ofEpochMilli(timestamp) +
               '}';
    }

    /**
     * Builder for AlertEvent with validation
     */
    public static final class Builder {
        private String alertType;
        private String productId;
        private String message;
        private String severity = "MEDIUM";
        private long timestamp = System.currentTimeMillis();

        private Builder() {}

        public Builder alertType(String alertType) {
            this.alertType = alertType;
            return this;
        }

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder severity(String severity) {
            this.severity = severity;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AlertEvent build() {
            validateRequired(alertType, "alertType");
            validateRequired(productId, "productId");
            validateRequired(message, "message");
            validateRequired(severity, "severity");

            if (timestamp <= 0) {
                throw new IllegalArgumentException("Timestamp must be positive");
            }

            return new AlertEvent(this);
        }

        private void validateRequired(String value, String fieldName) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
        }
    }
}
