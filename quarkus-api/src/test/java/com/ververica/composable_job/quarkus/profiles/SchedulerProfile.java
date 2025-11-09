package com.ververica.composable_job.quarkus.profiles;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class SchedulerProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.scheduler.enabled", "true");
    }
}
