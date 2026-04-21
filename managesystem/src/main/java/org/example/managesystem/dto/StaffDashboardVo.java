package org.example.managesystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StaffDashboardVo {
    @JsonProperty("elderTotal")
    private Integer elderTotal;

    @JsonProperty("genderDist")
    private List<Map<String, Object>> genderDist;

    @JsonProperty("ageDist")
    private List<Map<String, Object>> ageDist;

    @JsonProperty("careLevelDist")
    private List<Map<String, Object>> careLevelDist;

    @JsonProperty("bedTotal")
    private Integer bedTotal;

    @JsonProperty("bedOccupied")
    private Integer bedOccupied;

    @JsonProperty("bedRate")
    private Double bedRate;

    @JsonProperty("roomAvailable")
    private Integer roomAvailable;

    @JsonProperty("unpaidBills")
    private Integer unpaidBills;

    @JsonProperty("taskPending")
    private Integer taskPending;

    @JsonProperty("trendLabels")
    private List<String> trendLabels;

    @JsonProperty("trendInpatient")
    private List<Integer> trendInpatient;
}
