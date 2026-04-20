package org.example.managesystem.controller;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Feedback;
import org.example.managesystem.mapper.FeedbackMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {

    private final FeedbackMapper feedbackMapper;
    private final JdbcTemplate jdbcTemplate;

    public FeedbackController(FeedbackMapper feedbackMapper, JdbcTemplate jdbcTemplate) {
        this.feedbackMapper = feedbackMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public ApiResponse<Feedback> add(@RequestBody Feedback feedback) {
        feedbackMapper.insert(feedback);
        return ApiResponse.success(feedbackMapper.selectById(feedback.getId()));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        String sql = "SELECT f.id, f.user_id, f.content, f.reply, f.status, "
                + "DATE_FORMAT(f.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                + "u.real_name AS family_name FROM feedback f "
                + "LEFT JOIN `user` u ON f.user_id = u.id ORDER BY f.create_time DESC";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PatchMapping("/{id}/reply")
    public ApiResponse<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reply = body.get("reply");
        if (!StringUtils.hasText(reply)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "回复内容不能为空");
        }
        Feedback fb = feedbackMapper.selectById(id);
        if (fb == null) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "反馈不存在");
        }
        fb.setReply(reply);
        fb.setStatus(1);
        feedbackMapper.updateById(fb);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT f.id, f.user_id, f.content, f.reply, f.status, "
                        + "DATE_FORMAT(f.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "u.real_name AS family_name FROM feedback f "
                        + "LEFT JOIN `user` u ON f.user_id = u.id WHERE f.id = ?",
                id
        );
        return ApiResponse.success(rows.isEmpty() ? null : rows.get(0));
    }
}
