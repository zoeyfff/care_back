package org.example.managesystem.controller;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * 账单与费用管理 Controller
 * 包含：账单列表、生成月度账单、额外费用、费用标准、营收统计、邮件推送
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final JdbcTemplate jdbcTemplate;
    private final Optional<JavaMailSender> mailSender;

    public BillController(JdbcTemplate jdbcTemplate,
                          Optional<JavaMailSender> mailSender) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
    }

    // ==================== 账单列表 ====================

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(value = "elder_id", required = false) Long elderId,
            @RequestParam(value = "billing_cycle", required = false) String billingCycle,
            @RequestParam(value = "status", required = false) Integer status
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT b.id, b.elder_id, b.billing_cycle, b.total_amount, b.status, "
                        + "b.email_sent, DATE_FORMAT(b.email_sent_time, '%Y-%m-%d %H:%i:%s') AS email_sent_time, "
                        + "DATE_FORMAT(b.paid_time, '%Y-%m-%d %H:%i:%s') AS paid_time, "
                        + "b.pay_method, b.operator_name, DATE_FORMAT(b.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name, e.room_no, e.care_level "
                        + "FROM bill b LEFT JOIN elder e ON b.elder_id = e.id WHERE 1=1"
        );
        List<Object> args = new ArrayList<>();
        if (elderId != null) {
            sql.append(" AND b.elder_id = ?");
            args.add(elderId);
        }
        if (billingCycle != null && !billingCycle.trim().isEmpty()) {
            sql.append(" AND b.billing_cycle = ?");
            args.add(billingCycle.trim());
        }
        if (status != null) {
            sql.append(" AND b.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY b.billing_cycle DESC, b.id DESC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                    "SELECT id, item_name, amount FROM bill_item WHERE bill_id = ?", id);
            row.put("items", items);
        }
        return ApiResponse.success(ListPage.of(rows, rows.size()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT b.id, b.elder_id, b.billing_cycle, b.total_amount, b.status, "
                        + "b.email_sent, DATE_FORMAT(b.email_sent_time, '%Y-%m-%d %H:%i:%s') AS email_sent_time, "
                        + "DATE_FORMAT(b.paid_time, '%Y-%m-%d %H:%i:%s') AS paid_time, "
                        + "b.pay_method, b.operator_name, DATE_FORMAT(b.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name, e.room_no, e.care_level "
                        + "FROM bill b LEFT JOIN elder e ON b.elder_id = e.id WHERE b.id = ?", id);
        if (rows.isEmpty()) return ApiResponse.fail(ApiCodes.NOT_FOUND, "账单不存在");
        Map<String, Object> row = rows.get(0);
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id, item_name, amount FROM bill_item WHERE bill_id = ?", id);
        row.put("items", items);
        return ApiResponse.success(row);
    }

    // ==================== 入住记录 ====================

    @GetMapping("/checkins")
    public ApiResponse<Map<String, Object>> checkinList() {
        String sql = "SELECT c.id, c.elder_id, DATE_FORMAT(c.start_date, '%Y-%m-%d') AS start_date, "
                + "DATE_FORMAT(c.end_date, '%Y-%m-%d') AS end_date, c.status, "
                + "DATE_FORMAT(c.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                + "e.name AS elder_name, e.room_no "
                + "FROM checkin c LEFT JOIN elder e ON c.elder_id = e.id ORDER BY c.start_date DESC, c.id DESC";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    // ==================== 生成月度账单 ====================

    /**
     * 按指定月份生成账单。
     * billing_cycle 如 "2025-03"，会为所有当月在住长者生成该月账单。
     * 计费项目：床位费（按房间类型）+ 护理费（按护理等级）+ 餐饮费（固定）+ 额外费用（本月入账）
     */
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generateMonthlyBill(
            @RequestParam("billingCycle") String billingCycle
    ) {
        LoginUser user = currentUser();
        if (user == null) return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");

        // 解析计费周期
        String[] parts = billingCycle.split("-");
        if (parts.length != 2) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "billing_cycle 格式应为 YYYY-MM");
        }
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        if (month < 1 || month > 12) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "月份无效");
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthFirst = ym.atDay(1);
        LocalDate monthLast = ym.atEndOfMonth();

        List<Map<String, Object>> stats = new ArrayList<>();
        // 找出在 billingCycle 周期内实际在院的长者：
        // start_date <= 周期末 AND (end_date IS NULL OR end_date >= 周期初) AND status = 1
        List<Map<String, Object>> allElders = jdbcTemplate.queryForList(
                "SELECT e.id AS elder_id, e.name, e.care_level, e.room_no, "
                        + "r.room_type, c.start_date, c.end_date, c.status AS checkin_status "
                        + "FROM elder e "
                        + "LEFT JOIN room r ON e.room_no = r.room_no "
                        + "JOIN checkin c ON e.id = c.elder_id "
                        + "WHERE c.status = 1 "
                        + "AND c.start_date <= ? "
                        + "AND (c.end_date IS NULL OR c.end_date >= ?) "
                        + "ORDER BY c.start_date DESC",
                monthLast, monthFirst);

        int generated = 0;
        int skipped = 0;

        for (Map<String, Object> elder : allElders) {
            Long elderId = ((Number) elder.get("elder_id")).longValue();
            String elderName = String.valueOf(elder.get("name"));
            String careLevel = elder.get("care_level") == null ? "" : String.valueOf(elder.get("care_level"));
            String roomType = elder.get("room_type") == null ? "" : String.valueOf(elder.get("room_type"));

            // 检查该长者在该周期是否已有账单
            Integer existCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bill WHERE elder_id = ? AND billing_cycle = ?",
                    Integer.class, elderId, billingCycle);
            if (existCount != null && existCount > 0) {
                skipped++;
                continue;
            }

            BigDecimal careFee = getCurrentFee("billing_standard", "care_fee", careLevel);
            BigDecimal mealFee = getCurrentFee("billing_standard", "meal_fee", careLevel);
            BigDecimal bedFee = getCurrentFee("room_fee_standard", "bed_fee", roomType);
            BigDecimal extraFee = getUnbilledExtraChargeTotal(elderId, billingCycle);

            BigDecimal total = Optional.ofNullable(careFee).orElse(BigDecimal.ZERO)
                    .add(Optional.ofNullable(mealFee).orElse(BigDecimal.ZERO))
                    .add(Optional.ofNullable(bedFee).orElse(BigDecimal.ZERO))
                    .add(Optional.ofNullable(extraFee).orElse(BigDecimal.ZERO));

            // 插入账单
            jdbcTemplate.update(
                    "INSERT INTO bill (elder_id, billing_cycle, total_amount, status, operator_id, operator_name, create_time) "
                            + "VALUES (?, ?, ?, 0, ?, ?, NOW())",
                    elderId, billingCycle, total, user.getId(), user.getRealName()
            );
            Long billId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            // 插入费用明细
            insertBillItem(billId, "床位费", bedFee);
            insertBillItem(billId, "护理费", careFee);
            insertBillItem(billId, "餐饮费", mealFee);
            if (extraFee != null && extraFee.compareTo(BigDecimal.ZERO) > 0) {
                insertBillItem(billId, "额外费用", extraFee);
            }

            // 将额外费用标记为已入账
            jdbcTemplate.update(
                    "UPDATE extra_charge SET billed = 1, bill_id = ? WHERE elder_id = ? AND billing_cycle = ? AND billed = 0",
                    billId, elderId, billingCycle);

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("elder_id", elderId);
            s.put("elder_name", elderName);
            s.put("care_level", careLevel);
            s.put("room_type", roomType);
            s.put("bed_fee", bedFee);
            s.put("care_fee", careFee);
            s.put("meal_fee", mealFee);
            s.put("extra_fee", extraFee);
            s.put("total", total);
            s.put("bill_id", billId);
            stats.add(s);
            generated++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billing_cycle", billingCycle);
        result.put("generated_count", generated);
        result.put("skipped_count", skipped);
        result.put("details", stats);
        return ApiResponse.success(result);
    }

    // ==================== 费用统计 ====================

    /**
     * 费用统计概览：总收入、待收款、已收款、未付款账单数
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam(value = "billing_cycle", required = false) String billingCycle
    ) {
        String cycleFilter = "";
        List<Object> args = new ArrayList<>();
        if (billingCycle != null && !billingCycle.trim().isEmpty()) {
            cycleFilter = " AND b.billing_cycle = ?";
            args.add(billingCycle.trim());
        }

        BigDecimal totalRevenue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0) FROM bill b WHERE status = 1" + cycleFilter,
                BigDecimal.class, args.toArray());

        BigDecimal totalUnpaid = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0) FROM bill b WHERE status = 0" + cycleFilter,
                BigDecimal.class, args.toArray());

        Integer unpaidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bill b WHERE status = 0" + cycleFilter,
                Integer.class, args.toArray());

        Integer paidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bill b WHERE status = 1" + cycleFilter,
                Integer.class, args.toArray());

        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bill b WHERE 1=1" + cycleFilter,
                Integer.class, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_revenue", totalRevenue);
        result.put("total_unpaid", totalUnpaid);
        result.put("unpaid_count", unpaidCount);
        result.put("paid_count", paidCount);
        result.put("total_count", totalCount);
        return ApiResponse.success(result);
    }

    /**
     * 按月统计近12个月的账单金额（用于图表）
     */
    @GetMapping("/monthly-stats")
    public ApiResponse<Map<String, Object>> monthlyStats() {
        String sql = "SELECT billing_cycle, "
                + "SUM(CASE WHEN status = 1 THEN total_amount ELSE 0 END) AS paid_amount, "
                + "SUM(CASE WHEN status = 0 THEN total_amount ELSE 0 END) AS unpaid_amount, "
                + "SUM(total_amount) AS total_amount, "
                + "COUNT(*) AS bill_count "
                + "FROM bill "
                + "WHERE billing_cycle IS NOT NULL "
                + "GROUP BY billing_cycle "
                + "ORDER BY billing_cycle DESC LIMIT 12";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        Collections.reverse(list);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    // ==================== 额外费用 ====================

    @GetMapping("/extra-charges")
    public ApiResponse<Map<String, Object>> extraCharges(
            @RequestParam(value = "elder_id", required = false) Long elderId,
            @RequestParam(value = "billing_cycle", required = false) String billingCycle
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT ec.id, ec.elder_id, ec.charge_date, ec.charge_type, ec.item_name, "
                        + "ec.amount, ec.remark, ec.billed, ec.bill_id, ec.creator_name, "
                        + "DATE_FORMAT(ec.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name "
                        + "FROM extra_charge ec LEFT JOIN elder e ON ec.elder_id = e.id WHERE 1=1"
        );
        List<Object> args = new ArrayList<>();
        if (elderId != null) {
            sql.append(" AND ec.elder_id = ?");
            args.add(elderId);
        }
        if (billingCycle != null && !billingCycle.trim().isEmpty()) {
            sql.append(" AND ec.billing_cycle = ?");
            args.add(billingCycle.trim());
        }
        sql.append(" ORDER BY ec.charge_date DESC, ec.id DESC");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping("/extra-charges")
    public ApiResponse<Map<String, Object>> addExtraCharge(
            @RequestBody Map<String, Object> body
    ) {
        LoginUser user = currentUser();
        if (user == null) return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");

        Object elderIdObj = body.get("elder_id");
        String chargeDate = body == null ? null : String.valueOf(body.get("charge_date"));
        String chargeType = body == null ? null : String.valueOf(body.get("charge_type"));
        String itemName = body == null ? null : String.valueOf(body.get("item_name"));
        Object amountObj = body == null ? null : body.get("amount");
        String remark = body == null ? null : String.valueOf(body.get("remark"));

        if (!(elderIdObj instanceof Number) || itemName == null || itemName.trim().isEmpty() || amountObj == null) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "参数不完整：elder_id, item_name, amount 必填");
        }

        Long elderId = ((Number) elderIdObj).longValue();
        BigDecimal amount = new BigDecimal(String.valueOf(amountObj));
        String creatorName = user.getRealName() == null ? user.getUsername() : user.getRealName();

        // 自动推断计费周期
        String billingCycle = chargeDate != null && chargeDate.length() >= 7
                ? chargeDate.substring(0, 7) : YearMonth.now().toString();

        jdbcTemplate.update(
                "INSERT INTO extra_charge (elder_id, charge_date, charge_type, item_name, amount, remark, "
                        + "creator_id, creator_name, billing_cycle, billed, create_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW())",
                elderId, chargeDate, chargeType, itemName.trim(), amount, remark,
                user.getId(), creatorName, billingCycle);

        Long insertedId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", insertedId);
        result.put("elder_id", elderId);
        result.put("item_name", itemName);
        result.put("amount", amount);
        result.put("billing_cycle", billingCycle);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/extra-charges/{id}")
    public ApiResponse<Void> deleteExtraCharge(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM extra_charge WHERE id = ? AND billed = 0", id);
        return ApiResponse.success();
    }

    // ==================== 费用标准 ====================

    @GetMapping("/standards")
    public ApiResponse<Map<String, Object>> billingStandards() {
        String careSql = "SELECT id, care_level AS type_name, care_fee, meal_fee, effective_date, remark "
                + "FROM billing_standard WHERE expire_date IS NULL OR expire_date >= CURDATE() ORDER BY id";
        String roomSql = "SELECT id, room_type AS type_name, bed_fee, effective_date, remark "
                + "FROM room_fee_standard WHERE expire_date IS NULL OR expire_date >= CURDATE() ORDER BY id";
        List<Map<String, Object>> care = jdbcTemplate.queryForList(careSql);
        List<Map<String, Object>> room = jdbcTemplate.queryForList(roomSql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("care", care);
        result.put("room", room);
        return ApiResponse.success(result);
    }

    // ==================== 确认支付 ====================

    @PatchMapping("/{id}/pay")
    public ApiResponse<Map<String, Object>> pay(
            @PathVariable Long id,
            @RequestParam(required = false) String payMethod
    ) {
        LoginUser user = currentUser();
        if (user == null) return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        String operatorName = user.getRealName() == null ? user.getUsername() : user.getRealName();
        String method = payMethod == null || payMethod.trim().isEmpty() ? "其他" : payMethod.trim();

        int updated = jdbcTemplate.update(
                "UPDATE bill SET status = 1, paid_time = NOW(), pay_method = ?, operator_id = ?, operator_name = ? "
                        + "WHERE id = ? AND status = 0",
                method, user.getId(), operatorName, id);
        if (updated <= 0) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "账单不存在或已支付");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT b.id, b.elder_id, b.billing_cycle, b.total_amount, b.status, "
                        + "b.pay_method, b.operator_name, DATE_FORMAT(b.paid_time, '%Y-%m-%d %H:%i:%s') AS paid_time, "
                        + "DATE_FORMAT(b.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name "
                        + "FROM bill b LEFT JOIN elder e ON b.elder_id = e.id WHERE b.id = ?", id);
        return ApiResponse.success(rows.isEmpty() ? null : rows.get(0));
    }

    // ==================== 推送账单邮件 ====================

    /**
     * 推送账单邮件给关联长者的家属。
     * 支持单账单推送（billId）和批量推送（billingCycle 整月）
     */
    @PostMapping("/push-email")
    public ApiResponse<Map<String, Object>> pushBillEmail(
            @RequestParam(value = "bill_id", required = false) Long billId,
            @RequestParam(value = "billing_cycle", required = false) String billingCycle
    ) {
        LoginUser user = currentUser();
        if (user == null) return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");

        List<Map<String, Object>> bills;
        if (billId != null) {
            bills = jdbcTemplate.queryForList(
                    "SELECT b.id AS bill_id, b.elder_id, e.name AS elder_name, b.billing_cycle, b.total_amount, "
                            + "e.family_contact, e.care_level, e.room_no "
                            + "FROM bill b LEFT JOIN elder e ON b.elder_id = e.id WHERE b.id = ?",
                    billId);
        } else if (billingCycle != null && !billingCycle.trim().isEmpty()) {
            bills = jdbcTemplate.queryForList(
                    "SELECT b.id AS bill_id, b.elder_id, e.name AS elder_name, b.billing_cycle, b.total_amount, "
                            + "e.family_contact, e.care_level, e.room_no "
                            + "FROM bill b LEFT JOIN elder e ON b.elder_id = e.id "
                            + "WHERE b.billing_cycle = ? ORDER BY b.id",
                    billingCycle.trim());
        } else {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "bill_id 或 billing_cycle 必填其一");
        }

        int sent = 0;
        int failed = 0;
        int skipped = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        // 记录查到的账单数，便于调试
        System.out.println("[BillController] pushBillEmail 被调用，bills 数量=" + bills.size()
                + " (billId=" + billId + ", billingCycle=" + billingCycle + ")");

        for (Map<String, Object> bill : bills) {
            Long elderId = ((Number) bill.get("elder_id")).longValue();
            Long bId = ((Number) bill.get("bill_id")).longValue();
            String elderName = bill.get("elder_name") == null ? "未知长者"
                    : String.valueOf(bill.get("elder_name"));
            String bCycle = bill.get("billing_cycle") == null ? ""
                    : String.valueOf(bill.get("billing_cycle"));
            BigDecimal totalAmt = bill.get("total_amount") == null ? BigDecimal.ZERO
                    : new BigDecimal(String.valueOf(bill.get("total_amount")));

            // 查找关联的家属（从 family_elder 表找到 user_id，再关联 user 表取 email）
            List<Map<String, Object>> families = jdbcTemplate.queryForList(
                    "SELECT fe.user_id, u.username, u.real_name, u.phone, u.email "
                            + "FROM family_elder fe "
                            + "LEFT JOIN `user` u ON fe.user_id = u.id "
                            + "WHERE fe.elder_id = ?",
                    elderId);

            System.out.println("[BillController] 长者 id=" + elderId + " (" + elderName
                    + ") 找到关联家属数量=" + families.size());

            if (families.isEmpty()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("bill_id", bId);
                r.put("elder_name", elderName);
                r.put("status", "跳过");
                r.put("reason", "该长者尚未绑定家属，请在「家属绑定管理」中进行绑定");
                results.add(r);
                skipped++;
                continue;
            }

            for (Map<String, Object> family : families) {
                Long familyUserId = ((Number) family.get("user_id")).longValue();
                String familyName = family.get("real_name") == null
                        ? String.valueOf(family.get("username"))
                        : String.valueOf(family.get("real_name"));

                // 优先取 user.email，其次取 username 作为邮箱
                String email = family.get("email") != null
                        ? String.valueOf(family.get("email"))
                        : null;
                if (email == null || email.trim().isEmpty()) {
                    email = String.valueOf(family.get("username"));
                }

                System.out.println("[BillController] 尝试发送邮件给家属 id=" + familyUserId
                        + " (" + familyName + ")，邮箱候选=" + email);

                if (email == null || !email.trim().contains("@") || !email.contains(".")) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("bill_id", bId);
                    r.put("elder_name", elderName);
                    r.put("family_name", familyName);
                    r.put("status", "失败");
                    r.put("reason", "家属用户邮箱未填写，请在「权限与用户」中为 " + familyName + " 补充邮箱地址");
                    results.add(r);
                    failed++;
                    continue;
                }

                // 查询费用明细
                List<Map<String, Object>> items = jdbcTemplate.queryForList(
                        "SELECT item_name, amount FROM bill_item WHERE bill_id = ?", bId);
                StringBuilder itemsHtml = new StringBuilder();
                for (Map<String, Object> item : items) {
                    String name = String.valueOf(item.get("item_name"));
                    BigDecimal amt = item.get("amount") == null ? BigDecimal.ZERO
                            : new BigDecimal(String.valueOf(item.get("amount")));
                    itemsHtml.append(String.format(
                            "<tr><td style='padding:6px 12px;border-bottom:1px solid #eee'>%s</td>"
                                    + "<td style='padding:6px 12px;border-bottom:1px solid #eee;text-align:right'>%.2f 元</td></tr>",
                            name, amt));
                }

                String subject = String.format("【康养护老院】%s %s月账单通知",
                        elderName, bCycle);
                String htmlContent = buildBillEmailHtml(
                        elderName, familyName, bCycle, totalAmt, itemsHtml.toString());

                String sendResult = sendEmail(email.trim(), subject, htmlContent);
                boolean ok = sendResult == null;

                // 记录邮件日志
                jdbcTemplate.update(
                        "INSERT INTO bill_email_log (bill_id, elder_id, elder_name, family_user_id, family_email, "
                                + "family_name, billing_cycle, total_amount, send_status, send_result, send_time, create_time) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                        bId, elderId, elderName, familyUserId, email.trim(), familyName, bCycle,
                        totalAmt, ok ? 1 : 2, ok ? "发送成功" : sendResult);

                // 更新账单邮件状态
                if (ok) {
                    jdbcTemplate.update(
                            "UPDATE bill SET email_sent = 1, email_sent_time = NOW() WHERE id = ?",
                            bId);
                }

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("bill_id", bId);
                r.put("elder_name", elderName);
                r.put("family_name", familyName);
                r.put("email", email.trim());
                r.put("status", ok ? "成功" : "失败");
                r.put("reason", ok ? null : sendResult);
                results.add(r);
                System.out.println("[BillController] 邮件发送结果："
                        + (ok ? "成功" : "失败：" + sendResult));
                if (ok) sent++; else failed++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billing_cycle", billingCycle);
        result.put("sent_count", sent);
        result.put("failed_count", failed);
        result.put("skipped_count", skipped);
        result.put("results", results);
        System.out.println("[BillController] 邮件推送完成：成功=" + sent
                + "，失败=" + failed + "，跳过=" + skipped);
        return ApiResponse.success(result);
    }

    // ==================== 工具方法 ====================

    private BigDecimal getCurrentFee(String table, String feeColumn, String type) {
        if (type == null || type.trim().isEmpty()) return BigDecimal.ZERO;
        String col = "billing_standard".equals(table) ? "care_level" : "room_type";
        String sql = String.format(
                "SELECT %s FROM %s WHERE %s = ? AND (expire_date IS NULL OR expire_date >= CURDATE()) ORDER BY id DESC LIMIT 1",
                feeColumn, table, col);
        BigDecimal fee = jdbcTemplate.queryForObject(sql, BigDecimal.class, type.trim());
        return fee == null ? BigDecimal.ZERO : fee;
    }

    private BigDecimal getUnbilledExtraChargeTotal(Long elderId, String billingCycle) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM extra_charge "
                        + "WHERE elder_id = ? AND billing_cycle = ? AND billed = 0",
                BigDecimal.class, elderId, billingCycle);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private void insertBillItem(Long billId, String itemName, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        jdbcTemplate.update(
                "INSERT INTO bill_item (bill_id, item_name, amount) VALUES (?, ?, ?)",
                billId, itemName, amount);
    }

    private String sendEmail(String to, String subject, String html) {
        try {
            if (mailSender.isPresent()) {
                javax.mail.internet.MimeMessage msg = mailSender.get().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                helper.setFrom("2295039049@qq.com");
                mailSender.get().send(msg);
                return null;
            } else {
                System.err.println("[BillController] JavaMailSender 未配置，邮件内容如下：");
                System.err.println("To: " + to);
                System.err.println("Subject: " + subject);
                return "JavaMailSender 未配置（演示环境）";
            }
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String buildBillEmailHtml(String elderName, String familyName,
                                       String billingCycle, BigDecimal totalAmount, String itemsHtml) {
        String statusColor = "#d97706";
        String s = "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px\">" +
                "<div style=\"background:#134e4a;padding:20px;border-radius:8px 8px 0 0\">" +
                "<h2 style=\"color:#fff;margin:0\">康养护老院 - 月度账单</h2>" +
                "<p style=\"color:#99f6e4;margin:4px 0 0\">给您带来安心，替您守护亲人</p>" +
                "</div>" +
                "<div style=\"background:#fff;padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px\">" +
                "<p style=\"margin:0 0 16px\">尊敬的 <strong>" + familyName + "</strong> 您好：</p>" +
                "<p style=\"margin:0 0 20px\">以下是您家人在我院长者的 <strong>" + billingCycle + "</strong> 月费用账单，请查收。</p>" +
                "<table style=\"width:100%;border-collapse:collapse;margin-bottom:20px\">" +
                "<tr style=\"background:#f0fdf4\">" +
                "<td style=\"padding:10px 12px;font-size:14px\">长者姓名</td>" +
                "<td style=\"padding:10px 12px;font-size:14px;text-align:right\"><strong>" + elderName + "</strong></td>" +
                "</tr>" +
                "<tr>" +
                "<td style=\"padding:10px 12px;font-size:14px\">计费周期</td>" +
                "<td style=\"padding:10px 12px;font-size:14px;text-align:right\">" + billingCycle + "</td>" +
                "</tr>" +
                "</table>" +
                "<table style=\"width:100%;border-collapse:collapse;margin-bottom:20px\">" +
                "<thead><tr style=\"background:#f9fafb\">" +
                "<th style=\"padding:8px 12px;text-align:left;font-size:13px;color:#6b7280\">费用项目</th>" +
                "<th style=\"padding:8px 12px;text-align:right;font-size:13px;color:#6b7280\">金额</th>" +
                "</tr></thead>" +
                "<tbody>" + itemsHtml +
                "<tr style=\"background:#fef3c7\">" +
                "<td style=\"padding:10px 12px;font-weight:700\">合计</td>" +
                "<td style=\"padding:10px 12px;text-align:right;font-weight:700;color:" + statusColor + "\">" + totalAmount + " 元</td>" +
                "</tr></tbody></table>" +
                "<p style=\"background:#fef9c3;border-left:4px solid " + statusColor + ";padding:12px;border-radius:4px;font-size:14px;color:#92400e;margin-bottom:20px\">" +
                "请于收到账单后 <strong>7日内</strong> 完成缴费，缴费方式可选择：微信、支付宝、银行转账或至前台现金缴费。</p>" +
                "<p style=\"font-size:13px;color:#6b7280;margin-bottom:0\">" +
                "如有疑问，请拨打养老院服务热线：<strong>021-1234-5678</strong><br>此邮件由系统自动发送，请勿直接回复。</p>" +
                "</div></div>";
        return s;
    }

    private LoginUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) return (LoginUser) principal;
        return null;
    }
}
