package com.tierlist.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
public class PoolService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getAllPools() {
        return jdbcTemplate.queryForList("SELECT * FROM pools ORDER BY name");
    }

    @Transactional
    public void createPool(String name) {
        jdbcTemplate.update("INSERT INTO pools (name) VALUES (?)", name);
    }

    public void deletePool(Long id) {
        // Каскадное удаление объектов настроено через ON DELETE CASCADE
        jdbcTemplate.update("DELETE FROM pools WHERE id = ?", id);
    }
    public List<Map<String, Object>> getObjectsByPoolId(Long poolId) {
        return jdbcTemplate.queryForList("SELECT * FROM objects WHERE pool_id = ? ORDER BY id", poolId);
    }

    public Map<String, Object> getObjectById(Long objectId) {
        return jdbcTemplate.queryForMap("SELECT * FROM objects WHERE id = ?", objectId);
    }

    public String getPoolNameById(Long poolId) {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM pools WHERE id = ?", String.class, poolId);
    }

    @Value("${file.upload-dir}")
    private String uploadDirProperty;

    public void addObject(Long poolId, String name, MultipartFile imageFile, Map<String, String> attributes) throws IOException {
        String imageName = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            String originalFilename = imageFile.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            imageName = UUID.randomUUID().toString() + extension;
            File dir = new File(uploadDirProperty);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File dest = new File(dir, imageName);
            imageFile.transferTo(dest);
            System.out.println("Сохранён файл: " + dest.getAbsolutePath()); // лог
        }

        String json = "{}";
        if (attributes != null && !attributes.isEmpty()) {
            json = new JSONObject(attributes).toString();
        }

        jdbcTemplate.update(
                "INSERT INTO objects (pool_id, name, image, attributes) VALUES (?, ?, ?, ?::jsonb)",
                poolId, name, imageName, json
        );
    }
    //надо бы пофиксить траблы с относительным путем
}



