package com.tierlist.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CollabService {

    public static class JoinRequest {
        public String id;
        public Long tierlistId;
        public String tierlistName;
        public String fromUsername;
        public String toUsername;
        public long createdAt;
    }

    private final Map<Long, Long> guestNotifications = new ConcurrentHashMap<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, List<JoinRequest>> pending = new ConcurrentHashMap<>();

    public JoinRequest createRequest(Long tierlistId, String tierlistName, String from, String to) {
        JoinRequest r = new JoinRequest();
        r.id = UUID.randomUUID().toString();
        r.tierlistId = tierlistId;
        r.tierlistName = tierlistName;
        r.fromUsername = from;
        r.toUsername = to;
        r.createdAt = System.currentTimeMillis();
        pending.computeIfAbsent(to, k -> Collections.synchronizedList(new ArrayList<>())).add(r);
        return r;
    }

    public List<JoinRequest> getPending(String toUsername) {
        List<JoinRequest> l = pending.get(toUsername);
        return l == null ? List.of() : new ArrayList<>(l);
    }

    public JoinRequest takePending(String toUsername, String requestId) {
        List<JoinRequest> l = pending.get(toUsername);
        if (l == null) return null;
        for (JoinRequest r : l) {
            if (r.id.equals(requestId)) { l.remove(r); return r; }
        }
        return null;
    }

    public void reject(String toUsername, String requestId) {
        takePending(toUsername, requestId);
    }

    /** Хост открыл сессию (owner всегда имеет доступ через owner_id, но для чистки в БД — пишем). */
    public void openSession(Long tierlistId, Long ownerUserId) {
        jdbcTemplate.update(
                "INSERT INTO collab_sessions (tierlist_id, user_id) VALUES (?, ?) " +
                        "ON CONFLICT (tierlist_id, user_id) DO NOTHING",
                tierlistId, ownerUserId);
    }

    public void grantAccess(Long tierlistId, Long userId) {
        jdbcTemplate.update(
                "INSERT INTO collab_sessions (tierlist_id, user_id) VALUES (?, ?) " +
                        "ON CONFLICT (tierlist_id, user_id) DO NOTHING",
                tierlistId, userId);
    }

    public boolean hasAccess(Long tierlistId, Long userId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM collab_sessions WHERE tierlist_id = ? AND user_id = ?",
                Integer.class, tierlistId, userId);
        return n != null && n > 0;
    } // guestId -> tierlistId

    public void notifyGuest(Long guestId, Long tierlistId) {
        guestNotifications.put(guestId, tierlistId);
    }

    public Long consumeNotification(Long guestId) {
        return guestNotifications.remove(guestId);
    }


}