package org.example.managesystem.controller;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.dto.LoginRequest;
import org.example.managesystem.dto.LoginTokenVo;
import org.example.managesystem.dto.UserInfoVo;
import org.example.managesystem.security.JwtUtil;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, JdbcTemplate jdbcTemplate) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/login")
    public ApiResponse<LoginTokenVo> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            LoginUser user = (LoginUser) authentication.getPrincipal();
            String token = jwtUtil.generateToken(user.getUsername());
            LoginTokenVo data = new LoginTokenVo();
            data.setToken(token);
            data.setUser(new UserInfoVo(
                    user.getId(),
                    user.getUsername(),
                    user.getRealName(),
                    user.getPhone(),
                    user.getStatus()
            ));
            return ApiResponse.success(data);
        } catch (BadCredentialsException e) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        SecurityContextHolder.clearContext();
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof LoginUser)) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        LoginUser user = (LoginUser) principal;
        UserInfoVo vo = new UserInfoVo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getPhone(),
                user.getStatus()
        );
        List<String> permissions = user.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toList());
        List<String> roleCodes = jdbcTemplate.queryForList(
                "SELECT r.role_code FROM user_role ur " +
                        "JOIN role r ON ur.role_id = r.id " +
                        "WHERE ur.user_id = ? AND r.role_code IS NOT NULL",
                String.class,
                user.getId()
        ).stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(s -> s.trim().toUpperCase())
                .distinct()
                .collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", vo);
        data.put("permissions", permissions);
        data.put("roleCodes", roleCodes);
        return ApiResponse.success(data);
    }
}
