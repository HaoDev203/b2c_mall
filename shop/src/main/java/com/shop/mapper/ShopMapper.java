package com.shop.mapper;

import com.shop.entity.Shop;
import org.apache.ibatis.annotations.*;

public interface ShopMapper {

    @Select("SELECT * FROM tb_shop WHERE shop_name = #{shopName} LIMIT 1")
    Shop selectByName(String shopName);

    @Select("SELECT * FROM tb_shop WHERE id = #{id}")
    Shop selectById(Long id);

    /** 注册建店，自增 id 回填到 shop.id（后面用它设员工的 shop_id） */
    @Insert("INSERT INTO tb_shop(shop_name, admin_account, admin_password, logo_url, status) " +
            "VALUES(#{shopName}, #{adminAccount}, #{adminPassword}, #{logoUrl}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Shop shop);
}
