package com.tierlist.controller;

import com.tierlist.service.CollabService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/collab")
public class CollabController {

    @Autowired private CollabService collabService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long myId(HttpSession s) {
        String u = (String) s.getAttribute("username");
        if (u == null) return null;
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, u);
    }

    @PostMapping("/request")
    public Map<String, Object> request(@RequestParam String friendUsername, HttpSession session) {
        String me = (String) session.getAttribute("username");
        System.out.println("[COLLAB/request] me=" + me + " friend=" + friendUsername);
        if (me == null) return Map.of("ok", false, "error", "not authorized");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT a.tierlist_id, t.name AS tierlist_name, u.id AS owner_id " +
                        "FROM user_activity a " +
                        "JOIN users u ON u.username = ? " +
                        "JOIN tierlists t ON t.id = a.tierlist_id " +
                        "WHERE a.user_id = u.id " +
                        "  AND a.last_ping > NOW() - INTERVAL '15 seconds' " +
                        "  AND t.owner_id = u.id",
                friendUsername);
        System.out.println("[COLLAB/request] rows found: " + rows.size());
        if (rows.isEmpty())
            return Map.of("ok", false, "error", "друг не в сети / не в редакторе своего тирлиста");

        Long tierlistId = ((Number) rows.get(0).get("tierlist_id")).longValue();
        String tierlistName = (String) rows.get(0).get("tierlist_name");
        System.out.println("[COLLAB/request] tierlistId=" + tierlistId + " name=" + tierlistName);

        CollabService.JoinRequest r = collabService.createRequest(tierlistId, tierlistName, me, friendUsername);
        return Map.of("ok", true, "requestId", r.id);
    }

    @PostMapping("/accept")
    public Map<String, Object> accept(@RequestParam String requestId, HttpSession session) {
        String me = (String) session.getAttribute("username");
        System.out.println("[COLLAB/accept] me=" + me + " requestId=" + requestId);
        if (me == null) return Map.of("ok", false);

        CollabService.JoinRequest r = collabService.takePending(me, requestId);
        if (r == null) {
            System.out.println("[COLLAB/accept] заявка не найдена/устарела");
            return Map.of("ok", false, "error", "заявка устарела");
        }
        System.out.println("[COLLAB/accept] OK: guest=" + r.fromUsername + " tierlist=" + r.tierlistId);

        Long ownerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username=?", Long.class, me);
        collabService.openSession(r.tierlistId, ownerId);

        Long guestId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username=?", Long.class, r.fromUsername);
        collabService.grantAccess(r.tierlistId, guestId);

        collabService.notifyGuest(guestId, r.tierlistId);
        return Map.of("ok", true, "tierlistId", r.tierlistId, "guest", r.fromUsername);
    }

    @GetMapping("/pending")
    public List<CollabService.JoinRequest> pending(HttpSession session) {
        String me = (String) session.getAttribute("username");
        return me == null ? List.of() : collabService.getPending(me);
    }

    @PostMapping("/reject")
    public Map<String, Object> reject(@RequestParam String requestId, HttpSession session) {
        String me = (String) session.getAttribute("username");
        if (me != null) collabService.reject(me, requestId);
        return Map.of("ok", true);
    }

    /** Гость опрашивает: пустили или нет */
    @GetMapping("/my-access/{tierlistId}")
    public Map<String, Object> myAccess(@PathVariable Long tierlistId, HttpSession session) {
        Long uid = myId(session);
        if (uid == null) return Map.of("access", false);
        return Map.of("access", collabService.hasAccess(tierlistId, uid));
    }

    /** Хост уходит — снести сессии (страховка; Python делает это же при дисконнекте) */
    @PostMapping("/close/{tierlistId}")
    public Map<String, Object> close(@PathVariable Long tierlistId, HttpSession session) {
        String me = (String) session.getAttribute("username");
        if (me == null) return Map.of("ok", false);
        String owner = jdbcTemplate.queryForObject(
                "SELECT u.username FROM tierlists t JOIN users u ON t.owner_id=u.id WHERE t.id = ?",
                String.class, tierlistId);
        if (!me.equals(owner)) return Map.of("ok", false, "error", "не хост");
        jdbcTemplate.update("DELETE FROM collab_sessions WHERE tierlist_id = ?", tierlistId);
        return Map.of("ok", true);
    }

    @GetMapping("/waiting")
    public Map<String, Object> waiting(HttpSession session) {
        Long uid = myId(session);
        if (uid == null) return Map.of("tierlistId", 0);
        Long tid = collabService.consumeNotification(uid);
        return Map.of("tierlistId", tid != null ? tid : 0);
    }
}