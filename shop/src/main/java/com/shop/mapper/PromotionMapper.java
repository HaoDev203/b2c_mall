package com.shop.mapper;

import com.shop.entity.Promotion;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface PromotionMapper {

    /**
     * ★ 查"适用于当前金额"的最高一档满减。
     *   用 ORDER BY threshold DESC LIMIT 1 实现"门槛高的优先"——
     *   以后加了「满 3000 减 400」，买 3500 时会自动优先命中 400 那档，不用改 Java。
     */
    @Select("SELECT * FROM tb_promotion WHERE status = 1 AND (shop_id = 0 OR shop_id = #{shopId}) " +
            "AND threshold <= #{amount} ORDER BY threshold DESC, sort DESC LIMIT 1")
    Promotion selectBestMatch(@Param("shopId") Long shopId, @Param("amount") Double amount);

    /** ★ 查"下一档"满减 —— 没达门槛时前端要提示"再买 ¥xx 可减 ¥120" */
    @Select("SELECT * FROM tb_promotion WHERE status = 1 AND (shop_id = 0 OR shop_id = #{shopId}) " +
            "AND threshold > #{amount} ORDER BY threshold ASC LIMIT 1")
    Promotion selectNextTier(@Param("shopId") Long shopId, @Param("amount") Double amount);

    @Select("SELECT * FROM tb_promotion WHERE status = 1 ORDER BY threshold DESC")
    List<Promotion> selectAllActive();
}
