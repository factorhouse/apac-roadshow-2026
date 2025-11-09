package com.ververica.composable_job.flink.inventory.shared.config;

import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.time.Time;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for Flink state management including TTL and cleanup strategies.
 *
 * <p>This class provides fine-grained control over how Flink manages state,
 * including time-to-live settings, state backend selection, and cleanup strategies.</p>
 *
 * <p>Example usage:
 * <pre>{@code
 * StateConfig stateConfig = StateConfig.builder()
 *     .withStateTtl(Time.hours(24))
 *     .withStateBackendType(StateBackendType.ROCKSDB)
 *     .withIncrementalCheckpoints(true)
 *     .build();
 * }</pre>
 *
 * @author Ververica
 * @since 1.0
 */
public class StateConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * State backend types supported by the job.
     */
    public enum StateBackendType {
        /**
         * Heap-based state backend (default).
         * Stores state in Java heap memory. Fast but limited by heap size.
         */
        HEAP,

        /**
         * RocksDB state backend.
         * Stores state in RocksDB, enabling state larger than memory.
         */
        ROCKSDB
    }

    /**
     * Cleanup strategies for expired state.
     */
    public enum CleanupStrategy {
        /**
         * Clean up expired state during full snapshot operations.
         * Lower overhead but less timely cleanup.
         */
        FULL_SNAPSHOT_ONLY,

        /**
         * Incrementally clean up expired state.
         * More aggressive cleanup with some overhead.
         */
        INCREMENTAL_CLEANUP,

        /**
         * Clean up in background (RocksDB only).
         * Continuous background cleanup with minimal impact.
         */
        BACKGROUND
    }

    private final Time stateTtl;
    private final StateBackendType stateBackendType;
    private final CleanupStrategy cleanupStrategy;
    private final boolean incrementalCheckpoints;
    private final boolean enableTtl;
    private final int cleanupSize;
    private final long cleanupPeriodMs;

    private StateConfig(Builder builder) {
        this.stateTtl = builder.stateTtl;
        this.stateBackendType = builder.stateBackendType;
        this.cleanupStrategy = builder.cleanupStrategy;
        this.incrementalCheckpoints = builder.incrementalCheckpoints;
        this.enableTtl = builder.enableTtl;
        this.cleanupSize = builder.cleanupSize;
        this.cleanupPeriodMs = builder.cleanupPeriodMs;
    }

    /**
     * Creates a new builder with default values.
     *
     * @return a new StateConfig builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a StateConfig with default settings.
     *
     * @return StateConfig with default values
     */
    public static StateConfig withDefaults() {
        return builder().build();
    }

    /**
     * Creates a StateTtlConfig from this StateConfig.
     *
     * @return StateTtlConfig for use with Flink state descriptors
     */
    public StateTtlConfig createStateTtlConfig() {
        if (!enableTtl) {
            return null;
        }

        StateTtlConfig.Builder ttlBuilder = StateTtlConfig
            .newBuilder(stateTtl)
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired);

        // Configure cleanup strategy
        switch (cleanupStrategy) {
            case FULL_SNAPSHOT_ONLY:
                ttlBuilder.cleanupFullSnapshot();
                break;
            case INCREMENTAL_CLEANUP:
                ttlBuilder.cleanupIncrementally(cleanupSize, false);
                break;
            case BACKGROUND:
                ttlBuilder.cleanupInRocksdbCompactFilter(cleanupPeriodMs);
                break;
        }

        return ttlBuilder.build();
    }

    // Getters
    public Time getStateTtl() {
        return stateTtl;
    }

    public StateBackendType getStateBackendType() {
        return stateBackendType;
    }

    public CleanupStrategy getCleanupStrategy() {
        return cleanupStrategy;
    }

    public boolean isIncrementalCheckpoints() {
        return incrementalCheckpoints;
    }

    public boolean isTtlEnabled() {
        return enableTtl;
    }

    public int getCleanupSize() {
        return cleanupSize;
    }

    public long getCleanupPeriodMs() {
        return cleanupPeriodMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateConfig that = (StateConfig) o;
        return incrementalCheckpoints == that.incrementalCheckpoints &&
               enableTtl == that.enableTtl &&
               cleanupSize == that.cleanupSize &&
               cleanupPeriodMs == that.cleanupPeriodMs &&
               Objects.equals(stateTtl, that.stateTtl) &&
               stateBackendType == that.stateBackendType &&
               cleanupStrategy == that.cleanupStrategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateTtl, stateBackendType, cleanupStrategy,
                          incrementalCheckpoints, enableTtl, cleanupSize, cleanupPeriodMs);
    }

    @Override
    public String toString() {
        return "StateConfig{" +
               "stateTtl=" + stateTtl +
               ", stateBackendType=" + stateBackendType +
               ", cleanupStrategy=" + cleanupStrategy +
               ", incrementalCheckpoints=" + incrementalCheckpoints +
               ", enableTtl=" + enableTtl +
               ", cleanupSize=" + cleanupSize +
               ", cleanupPeriodMs=" + cleanupPeriodMs +
               '}';
    }

    /**
     * Builder for StateConfig with fluent API.
     */
    public static class Builder {
        private Time stateTtl = Time.hours(24);
        private StateBackendType stateBackendType = StateBackendType.HEAP;
        private CleanupStrategy cleanupStrategy = CleanupStrategy.FULL_SNAPSHOT_ONLY;
        private boolean incrementalCheckpoints = false;
        private boolean enableTtl = false;
        private int cleanupSize = 5;
        private long cleanupPeriodMs = TimeUnit.HOURS.toMillis(1);

        private Builder() {
        }

        /**
         * Sets the time-to-live for state entries.
         *
         * @param stateTtl the TTL duration
         * @return this builder
         */
        public Builder withStateTtl(Time stateTtl) {
            this.stateTtl = Objects.requireNonNull(stateTtl, "stateTtl cannot be null");
            this.enableTtl = true;
            return this;
        }

        /**
         * Sets the time-to-live for state entries.
         *
         * @param duration the TTL duration value
         * @param unit the time unit
         * @return this builder
         */
        public Builder withStateTtl(long duration, TimeUnit unit) {
            return withStateTtl(Time.of(duration, unit));
        }

        /**
         * Sets the state backend type.
         *
         * @param stateBackendType the backend type (HEAP or ROCKSDB)
         * @return this builder
         */
        public Builder withStateBackendType(StateBackendType stateBackendType) {
            this.stateBackendType = Objects.requireNonNull(stateBackendType, "stateBackendType cannot be null");
            return this;
        }

        /**
         * Sets the cleanup strategy for expired state.
         *
         * @param cleanupStrategy the cleanup strategy
         * @return this builder
         */
        public Builder withCleanupStrategy(CleanupStrategy cleanupStrategy) {
            this.cleanupStrategy = Objects.requireNonNull(cleanupStrategy, "cleanupStrategy cannot be null");
            return this;
        }

        /**
         * Enables or disables incremental checkpoints (for RocksDB).
         *
         * @param incrementalCheckpoints true to enable incremental checkpoints
         * @return this builder
         */
        public Builder withIncrementalCheckpoints(boolean incrementalCheckpoints) {
            this.incrementalCheckpoints = incrementalCheckpoints;
            return this;
        }

        /**
         * Enables or disables TTL.
         *
         * @param enableTtl true to enable TTL
         * @return this builder
         */
        public Builder withTtlEnabled(boolean enableTtl) {
            this.enableTtl = enableTtl;
            return this;
        }

        /**
         * Sets the cleanup size for incremental cleanup.
         *
         * @param cleanupSize number of entries to check per cleanup run
         * @return this builder
         */
        public Builder withCleanupSize(int cleanupSize) {
            if (cleanupSize <= 0) {
                throw new IllegalArgumentException("cleanupSize must be positive");
            }
            this.cleanupSize = cleanupSize;
            return this;
        }

        /**
         * Sets the cleanup period for background cleanup (RocksDB only).
         *
         * @param cleanupPeriodMs cleanup period in milliseconds
         * @return this builder
         */
        public Builder withCleanupPeriodMs(long cleanupPeriodMs) {
            if (cleanupPeriodMs <= 0) {
                throw new IllegalArgumentException("cleanupPeriodMs must be positive");
            }
            this.cleanupPeriodMs = cleanupPeriodMs;
            return this;
        }

        /**
         * Builds the StateConfig instance.
         *
         * @return a new StateConfig
         */
        public StateConfig build() {
            validate();
            return new StateConfig(this);
        }

        private void validate() {
            if (enableTtl && stateTtl == null) {
                throw new IllegalStateException("stateTtl must be set when TTL is enabled");
            }
            if (cleanupStrategy == CleanupStrategy.BACKGROUND &&
                stateBackendType != StateBackendType.ROCKSDB) {
                throw new IllegalStateException("BACKGROUND cleanup strategy requires RocksDB state backend");
            }
        }
    }
}
