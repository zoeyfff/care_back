package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
    private LocalDateTime createTime;
}
