package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("incident")
public class Incident {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long elderId;
    private String type;
    private String level;
    private LocalDateTime eventTime;
    private String content;
    private Integer status;
    private Long reporterId;
    private LocalDateTime createTime;
}

