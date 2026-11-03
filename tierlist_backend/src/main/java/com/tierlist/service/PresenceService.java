package com.tierlist.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    public static class Activity {
        public Long tierlistId;
        public String tierlistName;
        public Instant lastPing;
    }

    // username -> activity
    private final Map<String, Activity> activities = new ConcurrentHashMap<>();
    private static final long STALE_MS = 15_000;  // 15 секунд без пинга = выпал

    public void ping(String username, Long tierlistId, String tierlistName) {
        Activity a = new Activity();
        a.tierlistId = tierlistId;
        a.tierlistName = tierlistName;
        a.lastPing = Instant.now();
        activities.put(username, a);
    }

    public void clear(String username) {
        activities.remove(username);
    }

    public Activity get(String username) {
        Activity a = activities.get(username);
        if (a == null) return null;
        if (Instant.now().toEpochMilli() - a.lastPing.toEpochMilli() > STALE_MS) {
            activities.remove(username);
            return null;
        }
        return a;
    }
}