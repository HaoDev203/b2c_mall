package com.shop.mapper;

import com.shop.dto.CartItemVO;
import com.shop.entity.Cart;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface CartMapper {

    /** 查某买家某商品的购物车行 —— 加购时先查，有就累加，没有就插入 */
    @Select("SELECT * FROM tb_cart WHERE user_id = #{userId} AND product_id = #{productId}")
    Cart selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    @Select("SELECT * FROM tb_cart WHERE id = #{id}")
    Cart selectById(Long id);

    @Insert("INSERT INTO tb_cart(user_id, product_id, quantity, create_time, update_time) " +
            "VALUES(#{userId}, #{productId}, #{quantity}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Cart cart);

    @Update("UPDATE tb_cart SET quantity = #{quantity}, update_time = #{updateTime} WHERE id = #{id}")
    int updateQuantity(@Param("id") Long id, @Param("quantity") Integer quantity,
                       @Param("updateTime") String updateTime);

    /** ★ WHERE 带 user_id：防止改到别人的购物车（和 MessageMapper.markRead 一个思路） */
    @Delete("DELETE FROM tb_cart WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * ★★ 购物车列表：一次 JOIN 把商品信息拼出来。
     *    这比"先查购物车拿到 10 个 productId，再循环查 10 次商品"好得多 ——
     *    那是经典的 N+1 查询问题（1 次 + N 次 = 11 次数据库往返）。
     */
    @Select("SELECT c.id AS cartId, c.product_id AS productId, c.quantity, " +
            "       p.name AS productName, p.main_image AS productImage, p.price, p.stock, " +
            "       (p.price * c.quantity) AS subtotal, " +
            "       CASE WHEN c.quantity <= p.stock THEN 1 ELSE 0 END AS stockEnough " +
            "FROM tb_cart c JOIN tb_product p ON c.product_id = p.id " +
            "WHERE c.user_id = #{userId} ORDER BY c.id DESC")
    List<CartItemVO> selectItemsByUser(@Param("userId") Long userId);

    /** 按 id 集合查购物车行（下单时用）—— 必须带 user_id 防越权 */
    @Select("<script>" +
            "SELECT * FROM tb_cart WHERE user_id = #{userId} AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " ORDER BY id" +
            "</script>")
    List<Cart> selectByIdsAndUser(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
