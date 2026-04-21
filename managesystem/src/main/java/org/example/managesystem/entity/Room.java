package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("room")
public class Room {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String building;
    private Integer floor;
    private String roomNo;
    private String roomType;
    private Integer bedTotal;
    private Integer bedOccupied;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}

