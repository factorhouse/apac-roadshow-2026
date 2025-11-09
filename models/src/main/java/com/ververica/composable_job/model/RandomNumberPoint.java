package com.ververica.composable_job.model;

import java.time.Instant;
import java.util.Random;

public class RandomNumberPoint {

    private static final Random RANDOM = new Random();

    public Integer value;
    public Long timestamp;

    public RandomNumberPoint() {
    }

    public RandomNumberPoint(Integer value, Long timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }

    public static RandomNumberPoint generate() {
        return new RandomNumberPoint(RANDOM.nextInt(100), Instant.now().toEpochMilli());
    }
}
