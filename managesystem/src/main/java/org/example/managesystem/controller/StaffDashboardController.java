package org.example.managesystem.controller;

import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.dto.StaffDashboardVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        Integer elderTotal = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM elder", Integer.class);
        vo.setElderTotal(elderTotal == null ? 0 : elderTotal);

        List<Map<String, Object>> careRaw = jdbcTemplate.queryForList(
                "SELECT COALESCE(care_level, '未分级') AS name, COUNT(*) AS cnt FROM elder GROUP BY care_level"
        );
        List<Map<String, Object>> careLevelDist = new ArrayList<>();
        for (Map<String, Object> row : careRaw) {
            Map<String, Object> m = new HashMap<>(2);
            m.put("name", row.get("name"));
            m.put("value", row.get("cnt"));
            careLevelDist.add(m);
        }
        vo.setCareLevelDist(careLevelDist);

        int beds = totalBeds <= 0 ? 200 : totalBeds;
        double rate = vo.getElderTotal() == 0 ? 0.0 : Math.min(100.0, vo.getElderTotal() * 100.0 / beds);
        vo.setBedRate(Math.round(rate * 10.0) / 10.0);

        Integer unpaid = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bill WHERE status = 0", Integer.class);
        vo.setUnpaidBills(unpaid == null ? 0 : unpaid);

        Integer pending = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM care_task WHERE status = 0", Integer.class);
        vo.setTaskPending(pending == null ? 0 : pending);

        YearMonth end = YearMonth.now();
        DateTimeFormatter ym = DateTimeFormatter.ofPattern("yyyy-MM");
        List<String> labels = new ArrayList<>();
        List<Double> income = new ArrayList<>();
        List<Double> expense = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = end.minusMonths(i);
            labels.add(m.getMonthValue() + "月");
            String key = m.format(ym);
            Double sum = jdbcTemplate.query(
                    "SELECT COALESCE(SUM(total_amount),0) FROM bill WHERE status = 1 AND DATE_FORMAT(create_time, '%Y-%m') = ?",
                    rs -> {
                        if (!rs.next()) {
                            return 0.0;
                        }
                        return rs.getDouble(1);
                    },
                    key
            );
            double wan = Math.round(sum / 10000.0 * 100.0) / 100.0;
            income.add(wan);
            expense.add(Math.round(sum * 0.35 / 10000.0 * 100.0) / 100.0);
        }
        vo.setTrendLabels(labels);
        vo.setTrendIncome(income);
        vo.setTrendExpense(expense);

        return ApiResponse.success(vo);
    }
}
