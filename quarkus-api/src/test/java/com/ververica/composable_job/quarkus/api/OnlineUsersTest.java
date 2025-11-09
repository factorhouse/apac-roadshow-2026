package com.ververica.composable_job.quarkus.api;

import com.ververica.composable_job.quarkus.profiles.SchedulerProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(SchedulerProfile.class)
public class OnlineUsersTest extends ChatWebsocketTestBase {

    @Test
    public void onlineUsersTest() {
        openConnection(uriUser1);

        String onlineUsers = getWebsocketResponse();
        assertTrue(onlineUsers.contains("\"eventType\":\"ONLINE_USERS\""), "Did not receive an OnlineUsers message");
        assertTrue(onlineUsers.contains("\"users\":[\"user1\"]"), "user1 should be online");
    }

}
