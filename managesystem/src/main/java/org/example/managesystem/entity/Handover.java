package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("handover")
public class Handover {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate shiftDate;
    private String shift;
    private Long fromId;
    private Long toId;
    private String content;
    private Integer readStatus;
    private LocalDateTime createTime;
}

