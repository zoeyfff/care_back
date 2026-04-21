package org.example.managesystem.controller.nurse;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Incident;
import org.example.managesystem.mapper.IncidentMapper;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nurse/incidents")
public class NurseIncidentController {

    private final IncidentMapper incidentMapper;
    private final JdbcTemplate jdbcTemplate;

    public NurseIncidentController(IncidentMapper incidentMapper, JdbcTemplate jdbcTemplate) {
        this.incidentMapper = incidentMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        String sql = "SELECT i.id, i.elder_id, e.name AS elder_name, i.type, i.level, " +
                "DATE_FORMAT(i.event_time, '%Y-%m-%d %H:%i:%s') AS event_time, " +
                "i.content, i.status, i.reporter_id, u.real_name AS reporter_name, " +
                "DATE_FORMAT(i.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                "FROM incident i " +
                "LEFT JOIN elder e ON i.elder_id = e.id " +
                "LEFT JOIN `user` u ON i.reporter_id = u.id " +
                "ORDER BY i.event_time DESC, i.id DESC";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Incident> add(@RequestBody Incident incident) {
        if (incident.getReporterId() == null) {
            incident.setReporterId(currentUserId());
        }
        if (incident.getEventTime() == null) {
            incident.setEventTime(LocalDateTime.now());
        }
        incidentMapper.insert(incident);
        return ApiResponse.success(incidentMapper.selectById(incident.getId()));
    }

    @PatchMapping("/{id}/done")
    public ApiResponse<Incident> done(@PathVariable Long id) {
        Incident incident = incidentMapper.selectById(id);
        if (incident == null) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "事件不存在");
        }
        incident.setStatus(1);
        incidentMapper.updateById(incident);
        return ApiResponse.success(incidentMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        int n = incidentMapper.deleteById(id);
        if (n <= 0) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "事件不存在");
        }
        return ApiResponse.success();
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return ((LoginUser) principal).getId();
        }
        return null;
    }
}

