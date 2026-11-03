package com.tierlist.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LastSeenInterceptor implements HandlerInterceptor {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String u = (String) session.getAttribute("username");
            if (u != null) {
                jdbcTemplate.update(
                        "UPDATE users SET last_seen = NOW() WHERE username = ?", u);
            }
        }
        return true;
    }
}