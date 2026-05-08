package org.example.managesystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("elder")
public class Elder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String gender;
    private String idCard;
    private String phone;
    private String familyContact;
    private String healthInfo;
    private String carePlan;
    private String careLevel;
    private String roomNo;
    private String bedNo;
    private LocalDate checkinDate;
    private LocalDateTime createTime;
    /** 状态：null/空=在院，'已出院'=已出院 */
    private String status;
    private LocalDate checkoutDate;
    private String checkoutReason;
    private String checkoutRemark;
}
