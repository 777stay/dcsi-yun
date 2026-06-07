package com.whu.yun.mapper;


import com.whu.yun.entity.AccessLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccessLogMapper {

    @Insert("INSERT INTO access_logs (username, ip_address, url, http_method, class_method, request_params, execution_time, timestamp) " +
            "VALUES(#{username}, #{ipAddress}, #{url}, #{httpMethod}, #{classMethod}, #{requestParams}, #{executionTime}, #{timestamp})")
    void save(AccessLog log);

}
