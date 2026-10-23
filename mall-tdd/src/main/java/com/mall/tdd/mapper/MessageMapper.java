package com.mall.tdd.mapper;

import com.mall.tdd.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper {

    /**
     * 本店最近 N 条消息。
     *
     * <p>★ {@code shop_id} 条件不能省 —— 消息表不像订单表那样有天然的店铺语义，
     * 最容易写成「查全部消息」，于是 A 店看到 B 家的退款通知（D23）。
     *
     * <p>★ 排序写在 SQL 里（{@code ORDER BY create_time DESC}），不在 Service 里
     * {@code stream().sorted()} —— 分页时内存排序会错。
     */
    @Select("SELECT * FROM tb_message "
            + "WHERE shop_id = #{shopId} "
            + "ORDER BY create_time DESC "
            + "LIMIT #{limit}")
    List<Message> selectByShop(@Param("shopId") Long shopId, @Param("limit") int limit);
}
