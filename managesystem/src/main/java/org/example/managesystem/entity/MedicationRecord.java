package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medication_record")
public class MedicationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long elderId;
    private String medicineName;
    private String dosage;
    private LocalDateTime takeTime;
    private String remark;
    private LocalDateTime createTime;
}
