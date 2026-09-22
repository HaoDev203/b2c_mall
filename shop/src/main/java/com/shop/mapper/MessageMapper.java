package com.shop.mapper;

import com.shop.entity.Message;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface MessageMapper {

    @Insert("INSERT INTO tb_messages(shop_id, sender_id, title, content, msg_type, is_read) " +
            "VALUES(#{shopId}, #{senderId}, #{title}, #{content}, #{msgType}, #{isRead})")
    int insert(Message message);

    /** 阶段 2 的 Dashboard 消息列表要用 */
    @Select("SELECT * FROM tb_messages WHERE shop_id = #{shopId} ORDER BY id DESC")
    List<Message> selectByShopId(Long shopId);

    /** 未读数 —— 需求 Step 2.3 里说的「统计结果」，会被缓存 60 秒 */
    @Select("SELECT COUNT(*) FROM tb_messages WHERE shop_id = #{shopId} AND is_read = 0")
    int countUnread(@Param("shopId") Long shopId);

    /**
     * 标记已读。
     * ★ WHERE 里带上 shop_id：防止 A 店把 B 店的消息改掉（越权）。
     *   返回受影响行数，返回 0 说明消息不存在或不属于当前店铺。
     */
    @Update("UPDATE tb_messages SET is_read = 1, read_time = #{readTime}, updated_at = #{readTime} " +
            "WHERE id = #{id} AND shop_id = #{shopId}")
    int markRead(@Param("id") Long id, @Param("shopId") Long shopId, @Param("readTime") String readTime);
}
