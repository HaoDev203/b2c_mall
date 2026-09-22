package com.shop.mapper;

import com.shop.entity.Employee;
import org.apache.ibatis.annotations.*;

public interface EmployeeMapper {

    /** 按用户名查 —— 登录用 */
    @Select("SELECT * FROM tb_employee WHERE username = #{username} LIMIT 1")
    Employee selectByUsername(String username);

    @Select("SELECT * FROM tb_employee WHERE id = #{id}")
    Employee selectById(Long id);

    /** 新增员工，自增 id 自动回填到 employee.id */
    @Insert("INSERT INTO tb_employee(shop_id, username, password, avatar_url, login_count, status) " +
            "VALUES(#{shopId}, #{username}, #{password}, #{avatarUrl}, 0, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Employee employee);

    /** 更新最后登录时间 —— 用户状态观察者用 */
    @Update("UPDATE tb_employee SET last_login_time = #{time}, updated_at = #{time} WHERE id = #{id}")
    int updateLastLogin(@Param("id") Long id, @Param("time") String time);

    /** 累计登录次数 +1 —— 用户状态观察者用 */
    @Update("UPDATE tb_employee SET login_count = login_count + 1 WHERE id = #{id}")
    int increaseLoginCount(Long id);

    /** 更新头像 —— 账号初始化观察者用 */
    @Update("UPDATE tb_employee SET avatar_url = #{avatarUrl} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);

    /** 兜底：取最后一次插入的自增 id */
    @Select("SELECT last_insert_rowid()")
    Long lastInsertId();
}
