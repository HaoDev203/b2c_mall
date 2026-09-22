package com.shop.mapper;

import com.shop.entity.Loginlog;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface LoginLogMapper {

    @Insert("INSERT INTO tb_login_logs(user_id, login_ip, login_device, login_location, login_time, user_agent, status, user_type) " +
            "VALUES(#{userId}, #{loginIp}, #{loginDevice}, #{loginLocation}, #{loginTime}, #{userAgent}, #{status}, #{userType})")
    int insert(Loginlog log);

    @Select("SELECT * FROM tb_login_logs WHERE user_id = #{userId} ORDER BY id DESC")
    List<Loginlog> selectByUserId(Long userId);
}
