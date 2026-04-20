package org.example.managesystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Bill;
import org.example.managesystem.entity.BillItem;
import org.example.managesystem.mapper.BillItemMapper;
import org.example.managesystem.mapper.BillMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillMapper billMapper;
    private final BillItemMapper billItemMapper;
    private final JdbcTemplate jdbcTemplate;

    public BillController(BillMapper billMapper, BillItemMapper billItemMapper, JdbcTemplate jdbcTemplate) {
        this.billMapper = billMapper;
        this.billItemMapper = billItemMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(value = "elder_id", required = false) Long elderId) {
        StringBuilder sql = new StringBuilder(
                "SELECT b.id, b.elder_id, b.total_amount, b.status, "
                        + "DATE_FORMAT(b.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM bill b "
                        + "LEFT JOIN elder e ON b.elder_id = e.id WHERE 1=1");
        List<Map<String, Object>> rows;
        if (elderId != null) {
            sql.append(" AND b.elder_id = ? ORDER BY b.create_time DESC");
            rows = jdbcTemplate.queryForList(sql.toString(), elderId);
        } else {
            sql.append(" ORDER BY b.create_time DESC");
            rows = jdbcTemplate.queryForList(sql.toString());
        }
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
                "SELECT b.id, b.elder_id, b.total_amount, b.status, "
                        + "DATE_FORMAT(b.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM bill b "
                        + "LEFT JOIN elder e ON b.elder_id = e.id WHERE b.id = ?",
                id
        );
        if (rows.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "账单不存在");
        }
        Map<String, Object> row = rows.get(0);
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id, item_name, amount FROM bill_item WHERE bill_id = ?", id);
        row.put("items", items);
        return ApiResponse.success(row);
    }

    @PostMapping
    public ApiResponse<Bill> create(@RequestBody Bill bill) {
        billMapper.insert(bill);
        return ApiResponse.success(billMapper.selectById(bill.getId()));
    }

    @PostMapping("/{billId}/items")
    public ApiResponse<Void> addItem(@PathVariable Long billId, @RequestBody BillItem item) {
        item.setBillId(billId);
        billItemMapper.insert(item);
        rebuildBillAmount(billId);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/pay")
    public ApiResponse<Bill> pay(@PathVariable Long id) {
        Bill bill = billMapper.selectById(id);
        if (bill == null) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "账单不存在");
        }
        bill.setStatus(1);
        billMapper.updateById(bill);
        return ApiResponse.success(billMapper.selectById(id));
    }

    private void rebuildBillAmount(Long billId) {
        QueryWrapper<BillItem> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(BillItem::getBillId, billId);
        BigDecimal amount = billItemMapper.selectList(wrapper).stream()
                .map(BillItem::getAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Bill bill = billMapper.selectById(billId);
        if (bill != null) {
            bill.setTotalAmount(amount);
            billMapper.updateById(bill);
        }
    }
}
