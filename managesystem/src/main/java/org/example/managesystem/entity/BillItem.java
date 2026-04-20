package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("bill_item")
public class BillItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long billId;
    private String itemName;
    private BigDecimal amount;
}
