package org.example.managesystem.controller;

import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class StaffUserController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public StaffUserController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/api/users")
    public ApiResponse<Map<String, Object>> users() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.real_name, u.phone, u.email, u.status, "
                        + "DATE_FORMAT(u.create_time, '%Y-%m-%d %H:%i:%s') AS create_time "
                        + "FROM `user` u ORDER BY u.id"
        );
        for (Map<String, Object> row : rows) {
            Long uid = ((Number) row.get("id")).longValue();
            List<String> roles = jdbcTemplate.queryForList(
                    "SELECT r.role_name FROM user_role ur JOIN role r ON ur.role_id = r.id WHERE ur.user_id = ?",
                    String.class,
                    uid
            );
            row.put("roles", roles);
        }
        return ApiResponse.success(ListPage.of(rows, rows.size()));
    }

    @PostMapping("/api/users")
    public ApiResponse<Void> addUser(@RequestBody Map<String, Object> body) {
        String username = String.valueOf(body.get("username"));
        String password = String.valueOf(body.get("password"));
        Object rn = body.get("real_name");
        if (rn == null) {
            rn = body.get("realName");
        }
        String realName = rn == null ? null : String.valueOf(rn);
        Object ph = body.get("phone");
        String phone = ph == null ? null : String.valueOf(ph);
        Object em = body.get("email");
        String email = em == null ? null : String.valueOf(em);
        jdbcTemplate.update(
                "INSERT INTO `user`(username, password, real_name, phone, email, status) VALUES (?, ?, ?, ?, ?, 1)",
                username, passwordEncoder.encode(password), realName, phone, email
        );
        return ApiResponse.success();
    }

    @GetMapping("/api/roles")
    public ApiResponse<Map<String, Object>> roles() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, role_name, role_code, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time FROM role ORDER BY id"
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @GetMapping("/api/permissions")
    public ApiResponse<Map<String, Object>> permissions() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, permission_name, permission_code, path, "
                        + "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time FROM permission ORDER BY id"
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping("/api/user-role")
    public ApiResponse<Void> bindUserRole(@RequestParam Long userId, @RequestParam Long roleId) {
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) VALUES (?, ?)", userId, roleId);
        return ApiResponse.success();
    }

    @PostMapping("/api/role-permission")
    public ApiResponse<Void> bindRolePermission(@RequestParam Long roleId, @RequestParam Long permissionId) {
        jdbcTemplate.update("INSERT INTO role_permission(role_id, permission_id) VALUES (?, ?)", roleId, permissionId);
        return ApiResponse.success();
    }
}
