package com.tierlist.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class PresenceController {

    @Autowired private JdbcTemplate jdbcTemplate;

    private Long myId(HttpSession s) {
        String u = (String) s.getAttribute("username");
        if (u == null) return null;
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, u);
    }

    /** Редактор шлёт каждые 5 сек */
    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(@RequestBody(required = false) Map<String, Object> body,
                                         HttpSession session) {
        Long uid = myId(session);
        if (uid == null) return Map.of("ok", false, "error", "not authenticated");

        // Всегда обновляем last_seen (онлайн)
        jdbcTemplate.update("UPDATE users SET last_seen = NOW() WHERE id = ?", uid);

        // Проверяем тело
        if (body == null) {
            System.out.println("[HEARTBEAT] uid=" + uid + " body=null → только last_seen");
            return Map.of("ok", true, "activity", false);
        }
        Object raw = body.get("tierlistId");
        long tid = (raw instanceof Number) ? ((Number) raw).longValue() : 0;

        if (tid <= 0) {
            System.out.println("[HEARTBEAT] uid=" + uid + " tid=" + tid + " → пропуск user_activity");
            return Map.of("ok", true, "activity", false);
        }

        // Проверим, что тирлист существует
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tierlists WHERE id = ?", Integer.class, tid);
        if (exists == null || exists == 0) {
            System.out.println("[HEARTBEAT] uid=" + uid + " tid=" + tid + " → тирлист не существует");
            return Map.of("ok", true, "activity", false);
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO user_activity (user_id, tierlist_id, last_ping) " +
                            "VALUES (?, ?, NOW()) " +
                            "ON CONFLICT (user_id) DO UPDATE " +
                            "  SET tierlist_id = EXCLUDED.tierlist_id, last_ping = NOW()",
                    uid, tid);
            System.out.println("[HEARTBEAT] uid=" + uid + " tid=" + tid + " ok");
            return Map.of("ok", true, "activity", true);
        } catch (Exception e) {
            System.out.println("[HEARTBEAT] uid=" + uid + " tid=" + tid + " ERROR: " + e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** Редактор закрылся — убираем из "в редакторе" */
    @PostMapping("/leave")
    public Map<String, Object> leave(HttpSession session) {
        Long uid = myId(session);
        if (uid != null) {
            jdbcTemplate.update("DELETE FROM user_activity WHERE user_id = ?", uid);
            jdbcTemplate.update("UPDATE users SET last_seen = NOW() WHERE id = ?", uid);
        }
        return Map.of("ok", true);
    }

    /** Профиль опрашивает каждые 3 сек */
    @GetMapping("/friends")
    public List<Map<String, Object>> friends(HttpSession session) {
        Long uid = myId(session);
        if (uid == null) return List.of();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT u.username, u.last_seen, " +
                        "       a.tierlist_id, a.last_ping, t.name AS tierlist_name " +
                        "FROM users u " +
                        "JOIN friendships f ON (f.user_id1 = u.id AND f.user_id2 = ?) " +
                        "                   OR (f.user_id2 = u.id AND f.user_id1 = ?) " +
                        "LEFT JOIN user_activity a ON a.user_id = u.id " +
                        "                        AND a.last_ping > NOW() - INTERVAL '15 seconds' " +
                        "LEFT JOIN tierlists t ON t.id = a.tierlist_id " +
                        "WHERE u.id != ?",
                uid, uid, uid);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            boolean inTierlist = r.get("tierlist_id") != null;
            boolean online = false;
            Object lastSeen = r.get("last_seen");
            if (lastSeen instanceof java.sql.Timestamp ts) {
                online = ts.toInstant().isAfter(
                        java.time.Instant.now().minusSeconds(90));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", r.get("username"));
            item.put("online", online);
            item.put("inTierlist", inTierlist);
            item.put("tierlistId", r.get("tierlist_id"));
            item.put("tierlistName", r.get("tierlist_name"));
            result.add(item);
        }
        result.sort((a, b) -> {
            boolean aOnline = (Boolean) a.get("online");
            boolean bOnline = (Boolean) b.get("online");
            if (aOnline != bOnline) return aOnline ? -1 : 1;
            return ((String) a.get("username")).compareTo((String) b.get("username"));
        });
        return result;
    }
}