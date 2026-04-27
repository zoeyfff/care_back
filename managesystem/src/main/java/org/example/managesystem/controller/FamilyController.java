package org.example.managesystem.controller;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 家属端 API Controller
 * 负责家属查看关联长者信息、健康数据、账单、提交反馈等
 */
@RestController
@RequestMapping("/api/family")
public class FamilyController {

    private final JdbcTemplate jdbcTemplate;

    public FamilyController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 关联长者 ====================

    @GetMapping("/linked-elders")
    public ApiResponse<Map<String, Object>> linkedElders() {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        // 通过 family_elder 关联表查询该家属关联的长者
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT e.id, e.name, e.gender, e.id_card, e.phone, e.family_contact, " +
                        "e.health_info, e.care_level, e.room_no, e.bed_no, e.checkin_date, " +
                        "e.care_plan, e.create_time, " +
                        "(SELECT status FROM checkin c WHERE c.elder_id = e.id ORDER BY c.create_time DESC LIMIT 1) AS checkin_status " +
                        "FROM family_elder fe " +
                        "JOIN elder e ON fe.elder_id = e.id " +
                        "WHERE fe.user_id = ? " +
                        "ORDER BY e.name",
                user.getId()
        );
        // 如果没有关联记录，返回空列表
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    // ==================== 体征数据 ====================

    @GetMapping("/elder-health-records")
    public ApiResponse<Map<String, Object>> elderHealthRecords(
            @RequestParam(required = false) Long elder_id,
            @RequestParam(required = false) String start_time,
            @RequestParam(required = false) String end_time
    ) {
        if (elder_id == null) {
            return ApiResponse.success(ListPage.of(Collections.emptyList(), 0));
        }
        StringBuilder sql = new StringBuilder(
                "SELECT hr.id, hr.elder_id, e.name AS elder_name, hr.temperature, hr.blood_pressure, hr.heart_rate, " +
                        "DATE_FORMAT(hr.record_time, '%Y-%m-%d %H:%i:%s') AS record_time, " +
                        "hr.recorded_by, hr.recorded_by_name, hr.abnormal_flag, hr.follow_up_action, " +
                        "DATE_FORMAT(hr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM health_record hr LEFT JOIN elder e ON hr.elder_id = e.id " +
                        "WHERE hr.elder_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(elder_id);
        if (start_time != null && !start_time.trim().isEmpty()) {
            sql.append(" AND hr.record_time >= ?");
            args.add(start_time.trim());
        }
        if (end_time != null && !end_time.trim().isEmpty()) {
            sql.append(" AND hr.record_time <= ?");
            args.add(end_time.trim());
        }
        sql.append(" ORDER BY hr.record_time DESC, hr.id DESC LIMIT 200");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @GetMapping("/latest-vitals")
    public ApiResponse<Map<String, Object>> latestVitals(
            @RequestParam(required = false) Long elder_id
    ) {
        if (elder_id == null) {
            return ApiResponse.success(ListPage.of(Collections.emptyList(), 0));
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT hr.id, hr.elder_id, hr.temperature, hr.blood_pressure, hr.heart_rate, " +
                        "DATE_FORMAT(hr.record_time, '%Y-%m-%d %H:%i:%s') AS record_time, " +
                        "hr.abnormal_flag, hr.recorded_by_name " +
                        "FROM health_record hr WHERE hr.elder_id = ? " +
                        "ORDER BY hr.record_time DESC LIMIT 1",
                elder_id
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    // ==================== 用药记录 ====================

    @GetMapping("/elder-medication-records")
    public ApiResponse<Map<String, Object>> elderMedicationRecords(
            @RequestParam(required = false) Long elder_id
    ) {
        if (elder_id == null) {
            return ApiResponse.success(ListPage.of(Collections.emptyList(), 0));
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT mr.id, mr.elder_id, e.name AS elder_name, mr.medicine_name, mr.dosage, " +
                        "mr.frequency, mr.status, mr.need_confirm, " +
                        "DATE_FORMAT(mr.take_time, '%Y-%m-%d %H:%i:%s') AS take_time, " +
                        "DATE_FORMAT(mr.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, " +
                        "mr.remark, mr.reject_reason, mr.confirm_by_name, " +
                        "DATE_FORMAT(mr.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirm_time, " +
                        "DATE_FORMAT(mr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM medication_record mr LEFT JOIN elder e ON mr.elder_id = e.id " +
                        "WHERE mr.elder_id = ? ORDER BY mr.take_time DESC LIMIT 200",
                elder_id
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    // ==================== 账单 ====================

    @GetMapping("/bills")
    public ApiResponse<Map<String, Object>> myBills(
            @RequestParam(required = false) Long elder_id
    ) {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        StringBuilder sql = new StringBuilder(
                "SELECT b.id, b.elder_id, e.name AS elder_name, b.total_amount, b.status, " +
                        "DATE_FORMAT(b.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM bill b LEFT JOIN elder e ON b.elder_id = e.id " +
                        "JOIN family_elder fe ON b.elder_id = fe.elder_id " +
                        "WHERE fe.user_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(user.getId());
        if (elder_id != null) {
            sql.append(" AND b.elder_id = ?");
            args.add(elder_id);
        }
        sql.append(" ORDER BY b.create_time DESC");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @GetMapping("/bill-items")
    public ApiResponse<Map<String, Object>> billItems(
            @RequestParam(required = false) Long bill_id
    ) {
        if (bill_id == null) {
            return ApiResponse.success(ListPage.of(Collections.emptyList(), 0));
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT bi.id, bi.bill_id, bi.item_name, bi.amount " +
                        "FROM bill_item bi WHERE bi.bill_id = ? ORDER BY bi.id",
                bill_id
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping("/bills/{id}/pay")
    public ApiResponse<Map<String, Object>> payBill(@PathVariable Long id) {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        // 验证该账单属于该家属关联的长者
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bill b " +
                        "JOIN family_elder fe ON b.elder_id = fe.elder_id " +
                        "WHERE b.id = ? AND fe.user_id = ?",
                Integer.class, id, user.getId()
        );
        if (count == null || count <= 0) {
            return ApiResponse.fail(ApiCodes.FORBIDDEN, "无权支付此账单");
        }
        int updated = jdbcTemplate.update(
                "UPDATE bill SET status = 1 WHERE id = ? AND status = 0",
                id
        );
        if (updated <= 0) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "账单不存在或已支付");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT b.id, b.elder_id, e.name AS elder_name, b.total_amount, b.status, " +
                        "DATE_FORMAT(b.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM bill b LEFT JOIN elder e ON b.elder_id = e.id WHERE b.id = ?",
                id
        );
        return ApiResponse.success(rows.isEmpty() ? null : rows.get(0));
    }

    // ==================== 反馈 ====================

    @PostMapping("/feedback")
    public ApiResponse<Map<String, Object>> submitFeedback(@RequestBody Map<String, Object> body) {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        String content = body == null ? null : String.valueOf(body.get("content"));
        String type = body == null ? "服务反馈" : String.valueOf(body.getOrDefault("type", "服务反馈"));
        Object elderIdObj = body == null ? null : body.get("elder_id");
        String familyName = user.getRealName() == null ? user.getUsername() : user.getRealName();
        Long elderId = (elderIdObj instanceof Number) ? ((Number) elderIdObj).longValue() : null;
        if (content == null || content.trim().isEmpty()) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "反馈内容不能为空");
        }
        int updated = jdbcTemplate.update(
                "INSERT INTO feedback(user_id, type, content, status, create_time, family_name) " +
                        "VALUES(?, ?, ?, 0, NOW(), ?)",
                user.getId(), type.trim(), content.trim(), familyName
        );
        Map<String, Object> result = new HashMap<>();
        result.put("id", updated);
        result.put("type", type);
        result.put("content", content);
        result.put("status", 0);
        result.put("family_name", familyName);
        return ApiResponse.success(result);
    }

    @GetMapping("/feedbacks")
    public ApiResponse<Map<String, Object>> myFeedbacks() {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, type, content, reply, status, family_name, " +
                        "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM feedback WHERE user_id = ? ORDER BY create_time DESC",
                user.getId()
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    // ==================== 公告 ====================

    @GetMapping("/recent-notices")
    public ApiResponse<Map<String, Object>> recentNotices(
            @RequestParam(required = false, defaultValue = "6") Integer limit
    ) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, title, content, type, " +
                        "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM notice " +
                        "WHERE is_deleted = 0 AND publish_status = 1 " +
                        "ORDER BY create_time DESC LIMIT ?",
                limit
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    // ==================== 管理端：为家属关联长者 ====================

    @PostMapping("/link-elder")
    public ApiResponse<Void> linkElder(@RequestBody Map<String, Object> body) {
        Object elderIdObj = body.get("elder_id");
        Object userIdObj = body.get("user_id");
        Object relationObj = body.get("relation");
        if (!(elderIdObj instanceof Number) || !(userIdObj instanceof Number)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "elder_id 和 user_id 必填");
        }
        Long elderId = ((Number) elderIdObj).longValue();
        Long userId = ((Number) userIdObj).longValue();
        String relation = relationObj == null ? null : String.valueOf(relationObj);
        jdbcTemplate.update(
                "INSERT INTO family_elder(user_id, elder_id, relation, create_time) VALUES(?,?,?,NOW()) " +
                        "ON DUPLICATE KEY UPDATE relation = COALESCE(VALUES(relation), relation)",
                userId, elderId, relation
        );
        return ApiResponse.success();
    }

    @DeleteMapping("/unlink-elder")
    public ApiResponse<Void> unlinkElder(@RequestBody Map<String, Object> body) {
        Object elderIdObj = body.get("elder_id");
        Object userIdObj = body.get("user_id");
        if (!(elderIdObj instanceof Number) || !(userIdObj instanceof Number)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "elder_id 和 user_id 必填");
        }
        Long elderId = ((Number) elderIdObj).longValue();
        Long userId = ((Number) userIdObj).longValue();
        jdbcTemplate.update("DELETE FROM family_elder WHERE user_id = ? AND elder_id = ?", userId, elderId);
        return ApiResponse.success();
    }

    // ==================== 家属绑定长者（工作人员操作） ====================

    /**
     * 工作人员为家属关联长者
     * 约束：一老最多绑一家属；一家属可绑多老
     * 若该长者已关联其他家属，先自动解除旧关联再绑定新家属
     */
    @PostMapping("/bind")
    public ApiResponse<Void> bindElderToFamily(@RequestBody Map<String, Object> body) {
        LoginUser user = currentUser();
        if (user == null) return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");

        Object elderIdObj = body.get("elder_id");
        Object familyUserIdObj = body.get("family_user_id");
        Object relationObj = body.get("relation");
        if (!(elderIdObj instanceof Number) || !(familyUserIdObj instanceof Number)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "elder_id 和 family_user_id 必填");
        }
        Long elderId = ((Number) elderIdObj).longValue();
        Long familyUserId = ((Number) familyUserIdObj).longValue();
        String relation = relationObj == null ? null : String.valueOf(relationObj);

        // 查找该家属的角色，确认是家属角色
        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_role ur JOIN role r ON ur.role_id = r.id "
                        + "WHERE ur.user_id = ? AND r.role_code = 'family'",
                Integer.class, familyUserId);
        if (roleCount == null || roleCount == 0) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "指定用户不是家属角色，无法关联");
        }

        // 检查该长者是否已关联其他家属
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id, user_id FROM family_elder WHERE elder_id = ?", elderId);
        if (!existing.isEmpty()) {
            Long oldFamilyId = ((Number) existing.get(0).get("user_id")).longValue();
            if (oldFamilyId.equals(familyUserId)) {
                // 已经绑了同一家属，只需更新关系
                jdbcTemplate.update(
                        "UPDATE family_elder SET relation = ? WHERE elder_id = ? AND user_id = ?",
                        relation, elderId, familyUserId);
            } else {
                // 换家属：先删旧关联，再建新关联
                jdbcTemplate.update("DELETE FROM family_elder WHERE elder_id = ?", elderId);
                jdbcTemplate.update(
                        "INSERT INTO family_elder(user_id, elder_id, relation, create_time) VALUES(?,?,?,NOW())",
                        familyUserId, elderId, relation);
            }
        } else {
            jdbcTemplate.update(
                    "INSERT INTO family_elder(user_id, elder_id, relation, create_time) VALUES(?,?,?,NOW())",
                    familyUserId, elderId, relation);
        }
        return ApiResponse.success();
    }

    /**
     * 工作人员解除家属与长者的关联
     */
    @DeleteMapping("/unbind")
    public ApiResponse<Void> unbindElder(
            @RequestParam Long elder_id,
            @RequestParam Long family_user_id
    ) {
        LoginUser user = currentUser();
        if (user == null) return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        jdbcTemplate.update(
                "DELETE FROM family_elder WHERE elder_id = ? AND user_id = ?",
                elder_id, family_user_id);
        return ApiResponse.success();
    }

    /**
     * 列出所有家属用户（供工作人员选择）
     */
    @GetMapping("/family-users")
    public ApiResponse<Map<String, Object>> listFamilyUsers() {
        LoginUser user = currentUser();
        if (user == null) return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");

        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.real_name, u.phone, u.email, u.status "
                        + "FROM `user` u "
                        + "JOIN user_role ur ON u.id = ur.user_id "
                        + "JOIN role r ON ur.role_id = r.id "
                        + "WHERE r.role_code = 'family' "
                        + "ORDER BY u.id"
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    /**
     * 列出某长者的当前绑定情况
     */
    @GetMapping("/binding/{elderId}")
    public ApiResponse<Map<String, Object>> getBinding(@PathVariable Long elderId) {
        LoginUser user = currentUser();
        if (user == null) return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT fe.id, fe.user_id, fe.relation, fe.create_time, "
                        + "u.username, u.real_name, u.phone, u.email "
                        + "FROM family_elder fe "
                        + "JOIN `user` u ON fe.user_id = u.id "
                        + "WHERE fe.elder_id = ?",
                elderId);
        return ApiResponse.success(rows.isEmpty() ? null : rows.get(0));
    }

    private LoginUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
    }
}
