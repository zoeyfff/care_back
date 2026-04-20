package org.example.managesystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Activity;
import org.example.managesystem.mapper.ActivityMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityMapper activityMapper;

    public ActivityController(ActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        QueryWrapper<Activity> wrapper = new QueryWrapper<>();
        wrapper.lambda().orderByDesc(Activity::getStartTime);
        List<Activity> list = activityMapper.selectList(wrapper);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Activity> add(@RequestBody Activity activity) {
        activityMapper.insert(activity);
        return ApiResponse.success(activityMapper.selectById(activity.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Activity> update(@PathVariable Long id, @RequestBody Activity activity) {
        activity.setId(id);
        activityMapper.updateById(activity);
        return ApiResponse.success(activityMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        activityMapper.deleteById(id);
        return ApiResponse.success();
    }
}
