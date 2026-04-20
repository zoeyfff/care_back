package org.example.managesystem.dto;

import lombok.Data;

@Data
public class LoginTokenVo {
    private String token;
    private UserInfoVo user;
}
