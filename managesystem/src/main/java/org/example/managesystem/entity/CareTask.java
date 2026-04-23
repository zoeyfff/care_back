package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@TableName("care_task")
public class CareTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long elderId;
    private String taskName;
    private Integer status;
    private LocalDateTime executeTime;
    private String remark;
    private String frequencyType;
    private Long assignedTo;
    private String assignedToName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime preferredTime;
    private Integer priority;
    private LocalDateTime nextExecuteTime;
    private LocalDateTime lastExecuteTime;
    private String instruction;
    private LocalDateTime createTime;
}
