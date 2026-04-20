package org.example.managesystem.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.dto.ElderExcelRow;
import org.example.managesystem.entity.Elder;
import org.example.managesystem.mapper.ElderMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elders")
public class ElderController {

    private final ElderMapper elderMapper;

    public ElderController(ElderMapper elderMapper) {
        this.elderMapper = elderMapper;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword) {
        QueryWrapper<Elder> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.lambda().and(w -> w.like(Elder::getName, kw)
                    .or().like(Elder::getRoomNo, kw)
                    .or().like(Elder::getIdCard, kw));
        }
        List<Elder> list = elderMapper.selectList(wrapper);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Elder> add(@RequestBody Elder elder) {
        if (StringUtils.hasText(elder.getIdCard())) {
            QueryWrapper<Elder> dup = new QueryWrapper<>();
            dup.eq("id_card", elder.getIdCard());
            if (elderMapper.selectCount(dup) > 0) {
                return ApiResponse.fail(ApiCodes.CONFLICT, "身份证号已存在");
            }
        }
        elderMapper.insert(elder);
        return ApiResponse.success(elderMapper.selectById(elder.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Elder> update(@PathVariable Long id, @RequestBody Elder elder) {
        if (StringUtils.hasText(elder.getIdCard())) {
            QueryWrapper<Elder> dup = new QueryWrapper<>();
            dup.eq("id_card", elder.getIdCard()).ne("id", id);
            if (elderMapper.selectCount(dup) > 0) {
                return ApiResponse.fail(ApiCodes.CONFLICT, "身份证号已存在");
            }
        }
        elder.setId(id);
        elderMapper.updateById(elder);
        return ApiResponse.success(elderMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        elderMapper.deleteById(id);
        return ApiResponse.success();
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        List<ElderExcelRow> rows = EasyExcel.read(file.getInputStream())
                .head(ElderExcelRow.class)
                .sheet()
                .doReadSync();
        int success = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        int rowNum = 1;
        for (ElderExcelRow row : rows) {
            rowNum++;
            if (!StringUtils.hasText(row.getName())) {
                Map<String, Object> err = new HashMap<>();
                err.put("row", rowNum);
                err.put("message", "姓名不能为空");
                errors.add(err);
                continue;
            }
            try {
                Elder elder = new Elder();
                elder.setName(row.getName());
                elder.setGender(row.getGender());
                elder.setIdCard(row.getIdCard());
                elder.setPhone(row.getPhone());
                elder.setFamilyContact(row.getFamilyContact());
                elder.setCareLevel(row.getCareLevel());
                elder.setRoomNo(row.getRoomNo());
                elder.setBedNo(row.getBedNo());
                elder.setCheckinDate(LocalDate.now());
                elderMapper.insert(elder);
                success++;
            } catch (Exception ex) {
                Map<String, Object> err = new HashMap<>();
                err.put("row", rowNum);
                err.put("message", ex.getMessage() == null ? "导入失败" : ex.getMessage());
                errors.add(err);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("success_count", success);
        data.put("errors", errors);
        return ApiResponse.success(data);
    }

    @GetMapping("/export")
    public void exportExcel(HttpServletResponse response) throws Exception {
        List<Elder> list = elderMapper.selectList(null);
        List<ElderExcelRow> rows = new ArrayList<>();
        for (Elder elder : list) {
            ElderExcelRow row = new ElderExcelRow();
            row.setName(elder.getName());
            row.setGender(elder.getGender());
            row.setIdCard(elder.getIdCard());
            row.setPhone(elder.getPhone());
            row.setFamilyContact(elder.getFamilyContact());
            row.setCareLevel(elder.getCareLevel());
            row.setRoomNo(elder.getRoomNo());
            row.setBedNo(elder.getBedNo());
            rows.add(row);
        }
        String fileName = URLEncoder.encode("长者信息", StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), ElderExcelRow.class).sheet("长者").doWrite(rows);
    }
}
