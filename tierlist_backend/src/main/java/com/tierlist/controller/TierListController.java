package com.tierlist.controller;

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

    @GetMapping("/new")
    public String newTierListForm(HttpSession session, Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login"; //ну тип пока проверяем на лог ин, потом уберу
        model.addAttribute("pools", poolService.getAllPools());
        return "new-tierlist";
    }

    @PostMapping("/new")
    public String createTierList(@RequestParam String name,
                                 @RequestParam(required = false) List<String> poolNames,
                                 HttpSession session) {
        if (session.getAttribute("username") == null) return "redirect:/login";

        Map<String, Object> data = new HashMap<>();
        data.put("pools", poolNames != null ? poolNames : new ArrayList<>());
        data.put("displayAttribute", null);
        data.put("tiers", new HashMap<>());
        data.put("tierColors", new HashMap<>());

        String json = new JSONObject(data).toString();
        jdbcTemplate.update("INSERT INTO tierlists (name, tierlist_data) VALUES (?, ?::jsonb)", name, json);

        Long id = jdbcTemplate.queryForObject(
                "SELECT currval(pg_get_serial_sequence('tierlists','id'))", Long.class);
        return "redirect:/tierlist/" + id;
    }

    @GetMapping("/{id}")
    public String viewTierList(@PathVariable Long id, HttpSession session, Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login";

        Map<String, Object> tierlist = jdbcTemplate.queryForMap(
                "SELECT * FROM tierlists WHERE id = ?", id);
        Object dataObj = tierlist.get("tierlist_data");
        String dataJson = (dataObj != null) ? dataObj.toString() : "{}";
        JSONObject data = new JSONObject(dataJson);

        // Извлекаем pools как список строк
        JSONArray poolsArr = data.optJSONArray("pools");
        List<String> poolNames = new ArrayList<>();
        if (poolsArr != null) {
            for (int i = 0; i < poolsArr.length(); i++) {
                poolNames.add(poolsArr.getString(i));
            }
        }

        model.addAttribute("tierlist", tierlist);
        model.addAttribute("poolNames", poolNames);
        return "tierlist-detail";
    }

    @GetMapping("/{id}/edit")
    public String editTierList(@PathVariable Long id, HttpSession session, Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login";

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
            Long poolId = jdbcTemplate.queryForObject(
                    "SELECT id FROM pools WHERE name = ?", Long.class, poolName);
            if (poolId != null) {
                allObjects.addAll(poolService.getObjectsByPoolId(poolId));
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
                    // Найдём объект по ID среди allObjects
                    allObjects.stream()
                            .filter(o -> o.get("id").equals(objId))
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
                String attrsStr = attrsObj.toString();    // PGobject.toString() → JSON string
                JSONObject attrs = new JSONObject(attrsStr);
                attrs.keySet().forEach(availableAttributes::add);
            }
        }
        String displayAttribute = data.optString("displayAttribute", null);

        model.addAttribute("tierlistId", id);
        model.addAttribute("tierlistName", tierlist.get("name"));
        model.addAttribute("tiers", tiers);
        model.addAttribute("unassigned", unassigned);
        model.addAttribute("availableAttributes", availableAttributes);
        model.addAttribute("displayAttribute", displayAttribute);
        model.addAttribute("tierColors", data.optJSONObject("tierColors"));

        return "tierlist-editor";
    }

    @PostMapping("/{id}/display-attribute")
    @ResponseBody
    public ResponseEntity<?> updateDisplayAttribute(@PathVariable Long id,
                                                    @RequestParam String attribute) {
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
    public ResponseEntity<?> addTier(@PathVariable Long id, @RequestParam String tierName) {
        // Получаем текущий tierlist_data
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
    public ResponseEntity<?> removeTier(@PathVariable Long id, @RequestParam String tierName) {
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

    @GetMapping("/list")
    public String listTierLists(HttpSession session, Model model) {
        if (session.getAttribute("username") == null) return "redirect:/login";
        List<Map<String, Object>> tierlists = jdbcTemplate.queryForList("SELECT id, name FROM tierlists ORDER BY id");
        model.addAttribute("tierlists", tierlists);
        return "tierlist-list";
    }
}