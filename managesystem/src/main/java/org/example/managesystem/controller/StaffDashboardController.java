package org.example.managesystem.controller;

import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.dto.StaffDashboardVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff/dashboard")
public class StaffDashboardController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.stats.total-beds:200}")
    private int totalBeds;

    public StaffDashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/stats")
    public ApiResponse<StaffDashboardVo> stats() {
        StaffDashboardVo vo = new StaffDashboardVo();
        int elderTotal = safeCount("SELECT COUNT(*) FROM elder");
        vo.setElderTotal(elderTotal);

        vo.setCareLevelDist(nameValueList(
                "SELECT COALESCE(care_level, '未分级') AS name, COUNT(*) AS value FROM elder GROUP BY care_level"
        ));

        vo.setGenderDist(nameValueList(
                "SELECT COALESCE(gender, '未知') AS name, COUNT(*) AS value FROM elder GROUP BY gender"
        ));

        vo.setAgeDist(buildAgeDist());

        Integer bedTotal = safeInt("SELECT COALESCE(SUM(bed_total),0) FROM room");
        Integer bedOccupied = safeInt("SELECT COALESCE(SUM(bed_occupied),0) FROM room");
        Integer roomAvailable = safeInt("SELECT COALESCE(COUNT(*),0) FROM room WHERE status = 1 AND bed_occupied < bed_total");

        int bedTotalFinal = (bedTotal == null || bedTotal <= 0) ? (totalBeds <= 0 ? 200 : totalBeds) : bedTotal;
        int bedOccupiedFinal = (bedOccupied == null) ? elderTotal : bedOccupied;
        if (bedOccupiedFinal < 0) bedOccupiedFinal = 0;
        if (bedOccupiedFinal > bedTotalFinal) bedOccupiedFinal = bedTotalFinal;

        vo.setBedTotal(bedTotalFinal);
        vo.setBedOccupied(bedOccupiedFinal);
        vo.setRoomAvailable(roomAvailable == null ? 0 : roomAvailable);

        double rate = bedTotalFinal == 0 ? 0.0 : (bedOccupiedFinal * 100.0 / bedTotalFinal);
        vo.setBedRate(Math.round(rate * 10.0) / 10.0);

        vo.setUnpaidBills(safeCount("SELECT COUNT(*) FROM bill WHERE status = 0"));

        vo.setTaskPending(safeCount("SELECT COUNT(*) FROM care_task WHERE status = 0"));

        YearMonth end = YearMonth.now();
        List<String> labels = new ArrayList<>();
        List<Integer> inpatient = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = end.minusMonths(i);
            labels.add(m.getMonthValue() + "月");
            LocalDate start = m.atDay(1);
            LocalDate endDay = m.atEndOfMonth();
            Integer cnt = safeInt(
                    "SELECT COUNT(DISTINCT c.elder_id) FROM checkin c " +
                            "WHERE c.start_date <= ? AND (c.end_date IS NULL OR c.end_date >= ?) AND c.status = 1",
                    endDay, start
            );
            inpatient.add(cnt == null ? 0 : cnt);
        }
        vo.setTrendLabels(labels);
        vo.setTrendInpatient(inpatient);

        return ApiResponse.success(vo);
    }

    private List<Map<String, Object>> nameValueList(String sql) {
        List<Map<String, Object>> raw = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        for (Map<String, Object> row : raw) {
            Map<String, Object> m = new HashMap<>(2);
            m.put("name", row.get("name"));
            m.put("value", row.get("value"));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> buildAgeDist() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id_card FROM elder");
        int lt60 = 0, a60 = 0, a70 = 0, a80 = 0, a90 = 0, unknown = 0;
        LocalDate today = LocalDate.now();
        for (Map<String, Object> r : rows) {
            String idCard = r.get("id_card") == null ? null : String.valueOf(r.get("id_card"));
            Integer age = parseAgeFromIdCard(idCard, today);
            if (age == null) {
                unknown++;
                continue;
            }
            if (age < 60) lt60++;
            else if (age <= 69) a60++;
            else if (age <= 79) a70++;
            else if (age <= 89) a80++;
            else a90++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(nv("<60", lt60));
        out.add(nv("60-69", a60));
        out.add(nv("70-79", a70));
        out.add(nv("80-89", a80));
        out.add(nv("90+", a90));
        out.add(nv("未知", unknown));
        return out;
    }

    private Integer parseAgeFromIdCard(String idCard, LocalDate today) {
        if (idCard == null) return null;
        String s = idCard.trim();
        if (s.length() < 14) return null;
        try {
            String birthStr;
            if (s.length() == 18) {
                birthStr = s.substring(6, 14); // yyyyMMdd
            } else if (s.length() == 15) {
                birthStr = "19" + s.substring(6, 12); // yyMMdd -> 19yyMMdd
            } else {
                return null;
            }
            int y = Integer.parseInt(birthStr.substring(0, 4));
            int m = Integer.parseInt(birthStr.substring(4, 6));
            int d = Integer.parseInt(birthStr.substring(6, 8));
            LocalDate birth = LocalDate.of(y, m, d);
            int age = Period.between(birth, today).getYears();
            if (age < 0 || age > 130) return null;
            return age;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> nv(String name, int value) {
        Map<String, Object> m = new HashMap<>(2);
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private int safeCount(String sql, Object... args) {
        Integer v = safeInt(sql, args);
        return v == null ? 0 : v;
    }

    private Integer safeInt(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, args, Integer.class);
        } catch (Exception e) {
            return 0;
        }
    }
}
