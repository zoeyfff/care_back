package org.example.managesystem.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public UserDetailsServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, username, password, real_name, phone, status FROM `user` WHERE username = ?",
                username
        );
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("用户不存在");
        }
        Map<String, Object> user = users.get(0);
        Long userId = ((Number) user.get("id")).longValue();

        List<SimpleGrantedAuthority> authorities = jdbcTemplate.queryForList(
                        "SELECT p.permission_code FROM permission p " +
                                "JOIN role_permission rp ON p.id = rp.permission_id " +
                                "JOIN user_role ur ON rp.role_id = ur.role_id " +
                                "WHERE ur.user_id = ? AND p.permission_code IS NOT NULL",
                        String.class,
                        userId
                ).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new LoginUser(
                userId,
                (String) user.get("username"),
                (String) user.get("password"),
                user.get("real_name") == null ? null : String.valueOf(user.get("real_name")),
                user.get("phone") == null ? null : String.valueOf(user.get("phone")),
                ((Number) user.get("status")).intValue(),
                authorities
        );
    }
}
