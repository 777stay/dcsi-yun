package com.whu.yun.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultMap<T> {
//    private int code;
//    private String message;
//    //private T data;
//    private Map<String, Object> data = new HashMap<String, Object>();
//    public Map<String, Object> getData() {return data;}
//    public void setData(Map<String, Object> data) {this.data = data;}
//
//    public Result(T data) {
//        this.code = 200;
//        this.message = "success";
//        this.data = data;
//    }
//    public Result(int code, String message) {
//        this.code = code;
//        this.message = message;
//        this.data = null;
//    }
//    public static <T> Result<T> success(T data) {
//        return new Result<>(data);
//    }
//    public static <T> Result<T> fail(String message) {
//        return new Result<>(500, message);
//    }
    private Integer code;
    private Boolean success;
    private String msg;
    private Map<String, Object> data = new HashMap<>();

    public static ResultMap ok(){
        ResultMap result = new ResultMap();
        result.setSuccess(true);
        result.setCode(200);
        result.setMsg("成功");
        return result;
    }
    public static ResultMap error(){
        ResultMap result = new ResultMap();
        result.setSuccess(false);
        result.setCode(500);
        result.setMsg("失败");
        return result;
    }
    public ResultMap success(Boolean success){
        this.setSuccess(success);
        return this;
    }
    public ResultMap msg(String msg){
        this.setMsg(msg);
        return this;
    }
    public ResultMap code(Integer code){
        this.setCode(code);
        return this;
    }
    public ResultMap data(String key, Object value){
        this.data.put(key, value);
        return this;
    }
    public ResultMap data(Map<String, Object> data){
        this.setData(data);
        return this;
    }
}