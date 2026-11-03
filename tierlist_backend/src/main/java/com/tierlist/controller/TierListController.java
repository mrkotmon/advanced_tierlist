package com.tierlist.controller;

import com.tierlist.service.CollabService;
import com.tierlist.service.PoolService;
import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tierlist")
public class TierListController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PoolService poolService;

    @Autowired
    private CollabService collabService;

    // ─────────────────────────────────────────────────────────────
    // Создание
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/new")
    public String newTierListForm(HttpSession session, Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login";
        model.addAttribute("pools", poolService.getAllPools());
        return "new-tierlist";
    }

    @PostMapping("/new")
    public String createTierList(@RequestParam String name,
                                 @RequestParam(required = false) List<String> poolNames,
                                 HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";

        Long ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);

        Map<String, Object> data = new HashMap<>();
        data.put("pools", poolNames != null ? poolNames : new ArrayList<>());
        data.put("displayAttribute", null);
        data.put("tiers", new HashMap<>());
        data.put("tierColors", new HashMap<>());

        String json = new JSONObject(data).toString();
        jdbcTemplate.update(
                "INSERT INTO tierlists (name, tierlist_data, owner_id) VALUES (?, ?::jsonb, ?)",
                name, json, ownerId);

        Long id = jdbcTemplate.queryForObject(
                "SELECT currval(pg_get_serial_sequence('tierlists','id'))", Long.class);
        return "redirect:/tierlist/" + id;
    }

    // ─────────────────────────────────────────────────────────────
    // Список (свои + куда дали доступ)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/list")
    public String listTierLists(HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";

        Long ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);

        List<Map<String, Object>> tierlists = jdbcTemplate.queryForList(
                "SELECT t.id, t.name, " +
                        "       (t.owner_id = ?) AS is_owner " +
                        "FROM tierlists t " +
                        "WHERE t.owner_id = ? " +
                        "   OR t.id IN (SELECT tierlist_id FROM collab_sessions WHERE user_id = ?) " +
                        "ORDER BY t.id",
                ownerId, ownerId, ownerId);

        model.addAttribute("tierlists", tierlists);
        return "tierlist-list";
    }

    // ─────────────────────────────────────────────────────────────
    // Просмотр
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String viewTierList(@PathVariable Long id, HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        if (!canAccess(id, username)) return "redirect:/tierlist/list";

        Map<String, Object> tierlist = jdbcTemplate.queryForMap(
                "SELECT * FROM tierlists WHERE id = ?", id);
        Object dataObj = tierlist.get("tierlist_data");
        String dataJson = (dataObj != null) ? dataObj.toString() : "{}";
        JSONObject data = new JSONObject(dataJson);

        JSONArray poolsArr = data.optJSONArray("pools");
        List<String> poolNames = new ArrayList<>();
        if (poolsArr != null) {
            for (int i = 0; i < poolsArr.length(); i++) {
                poolNames.add(poolsArr.getString(i));
            }
        }

        Long myId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        Long ownerId = ((Number) tierlist.get("owner_id")).longValue();

        model.addAttribute("tierlist", tierlist);
        model.addAttribute("poolNames", poolNames);
        model.addAttribute("isOwner", myId.equals(ownerId));
        return "tierlist-detail";
    }

    // ─────────────────────────────────────────────────────────────
    // Редактор
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String editTierList(@PathVariable Long id, HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) return "redirect:/login";
        if (!canAccess(id, username)) return "redirect:/tierlist/list";

        Map<String, Object> tierlist = jdbcTemplate.queryForMap(
                "SELECT * FROM tierlists WHERE id = ?", id);
        Object dataObj = tierlist.get("tierlist_data");
        String jsonStr = (dataObj != null) ? dataObj.toString() : "{}";
        JSONObject data = new JSONObject(jsonStr);

        // Список пулов
        JSONArray poolNames = data.optJSONArray("pools");
        List<String> poolNamesList = new ArrayList<>();
        if (poolNames != null) {
            for (int i = 0; i < poolNames.length(); i++) {
                poolNamesList.add(poolNames.getString(i));
            }
        }

        List<Map<String, Object>> allObjects = new ArrayList<>();
        for (String poolName : poolNamesList) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM pools WHERE name = ?", Long.class, poolName);
            if (!ids.isEmpty()) {
                allObjects.addAll(poolService.getObjectsByPoolId(ids.get(0)));
            }
        }

        JSONObject tiersJson = data.optJSONObject("tiers");
        Map<String, List<Map<String, Object>>> tiers = new LinkedHashMap<>();
        if (tiersJson != null) {
            for (String key : tiersJson.keySet()) {
                JSONArray arr = tiersJson.getJSONArray(key);
                List<Map<String, Object>> objList = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ref = arr.getJSONObject(i);
                    long objId = ref.getLong("objectId");
                    allObjects.stream()
                            .filter(o -> ((Number) o.get("id")).longValue() == objId)
                            .findFirst()
                            .ifPresent(objList::add);
                }
                tiers.put(key, objList);
            }
        }

        Set<Long> assignedIds = new HashSet<>();
        if (tiersJson != null) {
            for (String key : tiersJson.keySet()) {
                JSONArray arr = tiersJson.getJSONArray(key);
                for (int i = 0; i < arr.length(); i++) {
                    assignedIds.add(arr.getJSONObject(i).getLong("objectId"));
                }
            }
        }
        List<Map<String, Object>> unassigned = allObjects.stream()
                .filter(o -> !assignedIds.contains(((Number) o.get("id")).longValue()))
                .collect(Collectors.toList());

        Set<String> availableAttributes = new LinkedHashSet<>();
        for (Map<String, Object> obj : allObjects) {
            Object attrsObj = obj.get("attributes");
            if (attrsObj != null) {
                JSONObject attrs = new JSONObject(attrsObj.toString());
                attrs.keySet().forEach(availableAttributes::add);
            }
        }
        String displayAttribute = data.optString("displayAttribute", null);

        Long myId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        Long ownerId = ((Number) tierlist.get("owner_id")).longValue();
        boolean isOwner = myId.equals(ownerId);

        // Владелец зашёл в редактор → открываем сессию
        if (isOwner) {
            collabService.openSession(id, ownerId);
        }

        model.addAttribute("tierlistId", id);
        model.addAttribute("tierlistName", tierlist.get("name"));
        model.addAttribute("tiers", tiers);
        model.addAttribute("unassigned", unassigned);
        model.addAttribute("availableAttributes", availableAttributes);
        model.addAttribute("displayAttribute", displayAttribute);
        model.addAttribute("tierColors", data.optJSONObject("tierColors"));
        model.addAttribute("isOwner", isOwner);

        return "tierlist-editor";
    }

    // ─────────────────────────────────────────────────────────────
    // Настройки отображения и тиры (AJAX)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/display-attribute")
    @ResponseBody
    public ResponseEntity<?> updateDisplayAttribute(@PathVariable Long id,
                                                    @RequestParam String attribute,
                                                    HttpSession session) {
        if (!canAccess(id, (String) session.getAttribute("username")))
            return ResponseEntity.status(403).build();

        Map<String, Object> tierlist = jdbcTemplate.queryForMap(
                "SELECT tierlist_data FROM tierlists WHERE id = ?", id);
        Object dataObj = tierlist.get("tierlist_data");
        String jsonStr = (dataObj != null) ? dataObj.toString() : "{}";
        JSONObject data = new JSONObject(jsonStr);
        data.put("displayAttribute", attribute.isEmpty() ? JSONObject.NULL : attribute);
        jdbcTemplate.update("UPDATE tierlists SET tierlist_data = ?::jsonb WHERE id = ?",
                data.toString(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/tiers/add")
    @ResponseBody
    public ResponseEntity<?> addTier(@PathVariable Long id,
                                     @RequestParam String tierName,
                                     HttpSession session) {
        if (!canAccess(id, (String) session.getAttribute("username")))
            return ResponseEntity.status(403).build();

        Map<String, Object> tierlist = jdbcTemplate.queryForMap(
                "SELECT tierlist_data FROM tierlists WHERE id = ?", id);
        Object dataObj = tierlist.get("tierlist_data");
        String jsonStr = (dataObj != null) ? dataObj.toString() : "{}";
        JSONObject data = new JSONObject(jsonStr);

        JSONObject tiers = data.optJSONObject("tiers");
        if (tiers == null) {
            tiers = new JSONObject();
            data.put("tiers", tiers);
        }
        if (tiers.has(tierName)) {
            return ResponseEntity.badRequest().body("Тир с таким именем уже существует");
        }
        tiers.put(tierName, new JSONArray());
        jdbcTemplate.update("UPDATE tierlists SET tierlist_data = ?::jsonb WHERE id = ?",
                data.toString(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/tiers/remove")
    @ResponseBody
    public ResponseEntity<?> removeTier(@PathVariable Long id,
                                        @RequestParam String tierName,
                                        HttpSession session) {
        if (!canAccess(id, (String) session.getAttribute("username")))
            return ResponseEntity.status(403).build();

        Map<String, Object> tierlist = jdbcTemplate.queryForMap(
                "SELECT tierlist_data FROM tierlists WHERE id = ?", id);
        Object dataObj = tierlist.get("tierlist_data");
        String jsonStr = (dataObj != null) ? dataObj.toString() : "{}";
        JSONObject data = new JSONObject(jsonStr);

        JSONObject tiers = data.optJSONObject("tiers");
        if (tiers != null && tiers.has(tierName)) {
            tiers.remove(tierName);
            jdbcTemplate.update("UPDATE tierlists SET tierlist_data = ?::jsonb WHERE id = ?",
                    data.toString(), id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Проверка доступа
    // ─────────────────────────────────────────────────────────────

    private boolean canAccess(Long tierlistId, String username) {
        if (username == null) {
            System.out.println("[ACCESS] tid=" + tierlistId + " user=null → false");
            return false;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT t.owner_id, u.id AS my_id " +
                        "FROM tierlists t, users u " +
                        "WHERE t.id = ? AND u.username = ?",
                tierlistId, username);
        if (rows.isEmpty()) {
            System.out.println("[ACCESS] tid=" + tierlistId + " user=" + username
                    + " → тирлист или пользователь не найден");
            return false;
        }
        Long ownerId = ((Number) rows.get(0).get("owner_id")).longValue();
        Long myId    = ((Number) rows.get(0).get("my_id")).longValue();

        if (ownerId.equals(myId)) {
            System.out.println("[ACCESS] tid=" + tierlistId + " user=" + username
                    + " myId=" + myId + " owner=" + ownerId + " → OWNER true");
            return true;
        }
        boolean has = collabService.hasAccess(tierlistId, myId);
        System.out.println("[ACCESS] tid=" + tierlistId + " user=" + username
                + " myId=" + myId + " owner=" + ownerId + " hasAccess=" + has);
        return has;
    }
}