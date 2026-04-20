package org.example.managesystem.controller;

import org.example.managesystem.common.ApiResponse;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/notify")
public class NotifyController {

    private final JavaMailSender mailSender;

    public NotifyController(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostMapping("/email")
    public ApiResponse<?> sendEmail(@RequestParam @Email String to,
                                    @RequestParam @NotBlank String subject,
                                    @RequestParam @NotBlank String content,
                                    @RequestParam(defaultValue = "your_email@qq.com") String from) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
        return ApiResponse.success();
    }
}
