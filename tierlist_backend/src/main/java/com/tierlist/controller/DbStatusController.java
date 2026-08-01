package com.tierlist.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Controller
public class DbStatusController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/db-status")
    public String dbStatus(Model model) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            model.addAttribute("connected", true);
            model.addAttribute("databaseUrl", metaData.getURL());
            model.addAttribute("databaseProduct", metaData.getDatabaseProductName());
            model.addAttribute("databaseVersion", metaData.getDatabaseProductVersion());
            model.addAttribute("driverName", metaData.getDriverName());
            Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            model.addAttribute("userCount", userCount);
        } catch (Exception e) {
            model.addAttribute("connected", false);
            model.addAttribute("error", e.getMessage());
        }
        return "db-status";
    }
}