package com.tierlist.controller;

import com.tierlist.service.PoolService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/pools")
public class PoolController {

    @Autowired
    private PoolService poolService;

    // Список пулов
    @GetMapping
    public String listPools(HttpSession session, Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login";
        model.addAttribute("pools", poolService.getAllPools());
        return "pools-list";
    }

    // Форма создания пула
    @GetMapping("/new")
    public String newPoolForm(HttpSession session) {
        if (session.getAttribute("username") == null) return "redirect:/login";
        return "new-pool";
    }

    // Создание пула
    @PostMapping("/new")
    public String createPool(@RequestParam String name, HttpSession session) {
        if (session.getAttribute("username") == null) return "redirect:/login";
        poolService.createPool(name);
        return "redirect:/pools";
    }

    @GetMapping("/{poolId}/objects")
    public String viewObjects(@PathVariable Long poolId, HttpSession session, Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login";

        List<Map<String, Object>> objects = poolService.getObjectsByPoolId(poolId);
        String poolName = poolService.getPoolNameById(poolId);  // может выбросить исключение, если пул не найден

        model.addAttribute("poolId", poolId);
        model.addAttribute("poolName", poolName);
        model.addAttribute("objects", objects);
        return "pool-objects";
    }

    @PostMapping("/{poolId}/add-object")
    public String addObject(@PathVariable Long poolId,
                            @RequestParam String name,
                            @RequestParam MultipartFile imageFile,
                            @RequestParam(required = false) List<String> attrKey,
                            @RequestParam(required = false) List<String> attrValue,
                            HttpSession session,
                            Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login";

        Map<String, String> attrs = new HashMap<>();
        if (attrKey != null && attrValue != null && attrKey.size() == attrValue.size()) {
            for (int i = 0; i < attrKey.size(); i++) {
                String key = attrKey.get(i);
                String value = attrValue.get(i);
                if (key != null && !key.isBlank()) {
                    attrs.put(key, value);
                }
            }
        }

        try {
            poolService.addObject(poolId, name, imageFile, attrs);
            return "redirect:/pools/" + poolId + "/objects";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Ошибка при добавлении объекта: " + e.getMessage());
            model.addAttribute("poolId", poolId);
            return "add-object";
        }
    }

    @GetMapping("/{poolId}/add-object")
    public String addObjectForm(@PathVariable Long poolId, HttpSession session, Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login";
        model.addAttribute("poolId", poolId);
        try {
            String poolName = poolService.getPoolNameById(poolId);
            model.addAttribute("poolName", poolName);
        } catch (Exception e) {
        }
        return "add-object";
    }
}