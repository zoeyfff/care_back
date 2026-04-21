package org.example.managesystem;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class test {
    public static void main(String[] args) {
        // 创建 BCrypt 编码器，参数 10 表示加密强度（2^10 轮）
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        String rawPassword = "123456";

        // 加密（每次结果不同）
        String encodedPassword = encoder.encode(rawPassword);
        System.out.println("加密结果: " + encodedPassword);

        // 验证密码
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("密码匹配: " + matches);

        // 演示多次加密结果不同
        System.out.println("\n多次加密同一密码的结果：");
        for (int i = 0; i < 3; i++) {
            System.out.println(encoder.encode("123456"));
        }
    }
}