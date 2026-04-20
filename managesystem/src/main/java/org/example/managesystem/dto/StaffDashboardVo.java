package org.example.managesystem.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StaffDashboardVo {
    private Integer elderTotal;
    private List<Map<String, Object>> careLevelDist;
    private Double bedRate;
    private Integer unpaidBills;
    private Integer taskPending;
    private List<String> trendLabels;
    private List<Double> trendIncome;
    private List<Double> trendExpense;
}
