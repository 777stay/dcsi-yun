package com.whu.yun.common.exception;

import com.whu.yun.entity.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;


/*
`@ControllerAdvice`用于声明处理全局Controller方法异常的类

`@ExceptionHandler`用于声明处理异常的方法，`value`属性用于声明该方法处理的异常类型

`@ResponseBody`表示将方法的返回值作为HTTP的响应体
 */

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result error(Exception e){
        e.printStackTrace();
        return Result.fail();
    }

    @ExceptionHandler(YunException.class)
    @ResponseBody
    public Result error(YunException e){
        e.printStackTrace();
        return Result.fail(e.getCode(), e.getMessage());
    }
}
