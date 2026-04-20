package org.example.managesystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVo {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private Integer status;
}
