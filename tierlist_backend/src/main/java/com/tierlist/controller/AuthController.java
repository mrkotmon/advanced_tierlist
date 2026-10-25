package com.tierlist.controller;

import com.tierlist.model.User;
import com.tierlist.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @GetMapping("/")
    public String index(HttpSession session) {
        //if (session.getAttribute("username") != null) {
        //    return "redirect:/home";
        //}
        //return "redirect:/login";
        return "tier-tool-landing.html";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam String username,
                               @RequestParam String password,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPassword())) {
            session.setAttribute("username", username);
            return "redirect:/home";
        } else {
            redirectAttributes.addFlashAttribute("error", "Неверное имя или пароль");
            return "redirect:/login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@RequestParam String username,
                         @RequestParam String password,
                         RedirectAttributes redirectAttributes) {
        if (userRepository.findByUsername(username).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Пользователь уже существует");
            return "redirect:/signup";
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password)); // обещанный
        userRepository.save(user);
        return "redirect:/login";
    }

    @GetMapping("/api/me")
    @ResponseBody
    public ResponseEntity<?> me(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "not authenticated"));
        }
        return ResponseEntity.ok(Map.of("username", username, "authenticated", true));
    }

    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<?> apiLogin(@RequestBody Map<String, String> body) {
        var userOpt = userRepository.findByUsername(body.get("username"));
        if (userOpt.isEmpty() || !passwordEncoder.matches(body.get("password"), userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid credentials"));
        }
        var user = userOpt.get();
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86_400_000L)) // 24ч
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
        return ResponseEntity.ok(Map.of("token", token, "username", user.getUsername()));
    }
}