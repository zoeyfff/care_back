package org.example.managesystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Notice;
import org.example.managesystem.mapper.NoticeMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeMapper noticeMapper;

    public NoticeController(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        QueryWrapper<Notice> wrapper = new QueryWrapper<>();
        wrapper.lambda().orderByDesc(Notice::getCreateTime);
        List<Notice> list = noticeMapper.selectList(wrapper);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Notice> add(@RequestBody Notice notice) {
        noticeMapper.insert(notice);
        return ApiResponse.success(noticeMapper.selectById(notice.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Notice> update(@PathVariable Long id, @RequestBody Notice notice) {
        notice.setId(id);
        noticeMapper.updateById(notice);
        return ApiResponse.success(noticeMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return ApiResponse.success();
    }
}
