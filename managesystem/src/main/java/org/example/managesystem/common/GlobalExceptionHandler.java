package org.example.managesystem.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::fieldErrorMsg)
                .collect(Collectors.joining("; "));
        if (msg == null || msg.trim().isEmpty()) {
            msg = "参数校验失败";
        }
        return ApiResponse.fail(ApiCodes.BAD_REQUEST, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        if (msg == null || msg.trim().isEmpty()) {
            msg = "参数校验失败";
        }
        return ApiResponse.fail(ApiCodes.BAD_REQUEST, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return ApiResponse.fail(ApiCodes.BAD_REQUEST, "请求体格式错误");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        Throwable root = e.getRootCause();
        if (root instanceof SQLIntegrityConstraintViolationException) {
            return ApiResponse.fail(ApiCodes.CONFLICT, "数据冲突或违反约束");
        }
        return ApiResponse.fail(ApiCodes.CONFLICT, "数据写入失败");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        return ApiResponse.fail(ApiCodes.SERVER_ERROR, e.getMessage() == null ? "服务器异常" : e.getMessage());
    }

    private String fieldErrorMsg(FieldError fe) {
        if (fe == null) return "";
        String field = fe.getField();
        String msg = fe.getDefaultMessage();
        if (msg == null) msg = "不合法";
        return field + ": " + msg;
    }
}

