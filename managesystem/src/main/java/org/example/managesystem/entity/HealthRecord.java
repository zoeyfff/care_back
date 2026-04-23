package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("health_record")
public class HealthRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long elderId;
    private BigDecimal temperature;
    private String bloodPressure;
    private Integer heartRate;
    private LocalDateTime recordTime;
    private Long recordedBy;
    private String recordedByName;
    private Integer abnormalFlag;
    private String followUpAction;
    @TableField(exist = false)
    private String roomNo;
    private LocalDateTime createTime;
}
