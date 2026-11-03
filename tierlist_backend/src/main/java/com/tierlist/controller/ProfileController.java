package com.tierlist.controller;

import com.tierlist.service.CollabService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private CollabService collabService;

    private Long myId(String u) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, u);
    }

    @GetMapping
    public String profile(HttpSession session, Model model) {
        String me = (String) session.getAttribute("username");
        if (me == null) return "redirect:/login";
        Long uid = myId(me);

        List<Map<String, Object>> friends = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.last_seen, " +
                        "       ((NOW() - u.last_seen) < INTERVAL '90 seconds') AS online, " +
                        "       a.tierlist_id, a.last_ping, t.name AS tierlist_name " +
                        "FROM users u " +
                        "JOIN friendships f " +
                        "  ON (f.user_id1 = u.id AND f.user_id2 = ?) " +
                        "  OR (f.user_id2 = u.id AND f.user_id1 = ?) " +
                        "LEFT JOIN user_activity a " +
                        "  ON a.user_id = u.id AND a.last_ping > NOW() - INTERVAL '15 seconds' " +
                        "LEFT JOIN tierlists t ON t.id = a.tierlist_id " +
                        "WHERE u.id != ? " +
                        "ORDER BY online DESC, u.username",
                uid, uid, uid);

        friends.sort((a, b) -> Boolean.compare(
                Boolean.TRUE.equals(b.get("online")), Boolean.TRUE.equals(a.get("online"))));

        List<Map<String, Object>> incoming = jdbcTemplate.queryForList(
                "SELECT fr.id, u.username FROM friend_requests fr " +
                        "JOIN users u ON fr.from_user_id = u.id " +
                        "WHERE fr.to_user_id = ? AND fr.status = 'PENDING'", uid);

        model.addAttribute("username", me);
        model.addAttribute("friends", friends);
        model.addAttribute("incoming", incoming);
        return "profile";
    }

    @PostMapping("/rename")
    public String rename(@RequestParam String newUsername, HttpSession session, RedirectAttributes ra) {
        String old = (String) session.getAttribute("username");
        if (old == null) return "redirect:/login";
        newUsername = newUsername.trim();
        if (newUsername.isEmpty() || newUsername.equals(old)) return "redirect:/profile";
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class, newUsername) > 0) {
            ra.addFlashAttribute("error", "Имя занято");
            return "redirect:/profile";
        }
        jdbcTemplate.update("UPDATE users SET username = ? WHERE username = ?", newUsername, old);
        session.setAttribute("username", newUsername);
        ra.addFlashAttribute("message", "Имя изменено");
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String password(@RequestParam String oldPassword, @RequestParam String newPassword,
                           HttpSession session, RedirectAttributes ra) {
        String me = (String) session.getAttribute("username");
        if (me == null) return "redirect:/login";
        String hash = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE username = ?", String.class, me);
        if (!passwordEncoder.matches(oldPassword, hash)) {
            ra.addFlashAttribute("error", "Старый пароль неверный");
            return "redirect:/profile";
        }
        jdbcTemplate.update("UPDATE users SET password = ? WHERE username = ?",
                passwordEncoder.encode(newPassword), me);
        ra.addFlashAttribute("message", "Пароль обновлён");
        return "redirect:/profile";
    }

    @PostMapping("/add-friend")
    public String addFriend(@RequestParam String friendUsername, HttpSession session, RedirectAttributes ra) {
        String me = (String) session.getAttribute("username");
        if (me == null) return "redirect:/login";
        List<Map<String, Object>> f = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE username = ?", friendUsername.trim());
        if (f.isEmpty()) { ra.addFlashAttribute("error", "Пользователь не найден"); return "redirect:/profile"; }

        Long myUid = myId(me);
        Long toUid = ((Number) f.get(0).get("id")).longValue();
        if (myUid.equals(toUid)) { ra.addFlashAttribute("error", "Нельзя добавить себя"); return "redirect:/profile"; }

        int friends = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM friendships WHERE (user_id1=? AND user_id2=?) OR (user_id1=? AND user_id2=?)",
                Integer.class, myUid, toUid, toUid, myUid);
        if (friends > 0) { ra.addFlashAttribute("error", "Уже друзья"); return "redirect:/profile"; }

        int req = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM friend_requests WHERE from_user_id=? AND to_user_id=? AND status='PENDING'",
                Integer.class, myUid, toUid);
        if (req > 0) { ra.addFlashAttribute("error", "Запрос уже отправлен"); return "redirect:/profile"; }

        jdbcTemplate.update(
                "INSERT INTO friend_requests (from_user_id, to_user_id) VALUES (?, ?)", myUid, toUid);
        ra.addFlashAttribute("message", "Запрос отправлен");
        return "redirect:/profile";
    }

    @PostMapping("/accept")
    public String accept(@RequestParam Long requestId, HttpSession session) {
        String me = (String) session.getAttribute("username");
        if (me == null) return "redirect:/login";
        Long myUid = myId(me);
        List<Map<String, Object>> r = jdbcTemplate.queryForList(
                "SELECT from_user_id FROM friend_requests WHERE id=? AND to_user_id=? AND status='PENDING'",
                requestId, myUid);
        if (r.isEmpty()) return "redirect:/profile";
        Long fromUid = ((Number) r.get(0).get("from_user_id")).longValue();
        long a = Math.min(fromUid, myUid), b = Math.max(fromUid, myUid);
        jdbcTemplate.update("UPDATE friend_requests SET status='ACCEPTED' WHERE id=?", requestId);
        jdbcTemplate.update("INSERT INTO friendships (user_id1, user_id2) VALUES (?, ?)", a, b);
        return "redirect:/profile";
    }

    @PostMapping("/reject")
    public String reject(@RequestParam Long requestId, HttpSession session) {
        String me = (String) session.getAttribute("username");
        if (me == null) return "redirect:/login";
        jdbcTemplate.update(
                "UPDATE friend_requests SET status='REJECTED' " +
                        "WHERE id=? AND to_user_id=(SELECT id FROM users WHERE username=?)", requestId, me);
        return "redirect:/profile";
    }
}