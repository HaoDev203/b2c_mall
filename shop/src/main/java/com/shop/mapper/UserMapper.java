package com.shop.mapper;

import com.shop.entity.User;
import org.apache.ibatis.annotations.*;

public interface UserMapper {

    /** 按用户名查 —— 登录 / 注册查重用 */
    @Select("SELECT * FROM tb_user WHERE username = #{username} LIMIT 1")
    User selectByUsername(String username);

    @Select("SELECT * FROM tb_user WHERE id = #{id}")
    User selectById(Long id);

    /** 新增买家，自增 id 自动回填到 user.id */
    @Insert("INSERT INTO tb_user(username, password, nickname, phone, avatar_url, login_count, status, created_at) " +
            "VALUES(#{username}, #{password}, #{nickname}, #{phone}, #{avatarUrl}, 0, 1, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /** ↓↓↓ 下面三个方法签名和 EmployeeMapper 完全一致 —— 这是 BuyerAccountStore 能替换 EmployeeMapper 的前提 */

    @Update("UPDATE tb_user SET last_login_time = #{time}, updated_at = #{time} WHERE id = #{id}")
    int updateLastLogin(@Param("id") Long id, @Param("time") String time);

    @Update("UPDATE tb_user SET login_count = login_count + 1 WHERE id = #{id}")
    int increaseLoginCount(Long id);

    @Update("UPDATE tb_user SET avatar_url = #{avatarUrl} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);
}
