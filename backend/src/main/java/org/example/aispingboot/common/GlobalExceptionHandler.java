package org.example.aispingboot.common;

import org.example.aispingboot.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // 处理参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handlerException(MethodArgumentNotValidException e) {
        // 处理异常数据的处理
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.PARAM_ERROR.getCode(), ResultCode.PARAM_ERROR.getMsg(), message);
    }

    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        // 如果异样携带额外的数据
        if (e.getData() != null) {
            return Result.error(e.getCode(), e.getMessage(), e.getData());
        }
        return Result.error(e.getCode(), e.getMessage(), null);
    }

    // 处理数据库唯一键冲突
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "记录已存在，请勿重复提交", null);
    }

    // 兜底处理其他系统异常
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        e.printStackTrace();
        return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "系统繁忙，请稍后重试", null);
    }
}