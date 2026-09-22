package com.shop.service;

import com.shop.common.BizException;
import com.shop.common.Result;
import com.shop.common.UserContext;
import com.shop.dto.CartAddDTO;
import com.shop.dto.CartItemVO;
import com.shop.dto.CartUpdateDTO;
import com.shop.dto.OrderPreviewVO;
import com.shop.entity.Cart;
import com.shop.entity.Product;
import com.shop.entity.Promotion;
import com.shop.mapper.CartMapper;
import com.shop.mapper.ProductMapper;
import com.shop.mapper.PromotionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 前台购物车（原型图 10）。
 *
 * ★ 所有方法第一件事都是取 userId 并判空 ——
 *   购物车是**私有数据**，没有登录就没有购物车。这个判断必须在 Service 层做，
 *   不能只靠拦截器（万一哪天白名单配错了，Service 层这道坎还能兜住）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallCartService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_QUANTITY = 999;

    private final CartMapper      cartMapper;
    private final ProductMapper   productMapper;
    private final PromotionMapper promotionMapper;

    /** 取当前买家 id，未登录直接抛异常 */
    private Long currentUserId() {
        // ★ 复用 UserContext：买家令牌里存的就是 tb_user.id（名字叫 employeeId 是阶段 1 的历史包袱）
        Long userId = UserContext.getEmployeeId();
        if (userId == null) {
            throw new BizException("未登录或登录已过期");
        }
        return userId;
    }

    // ==================== ① 加入购物车 ====================
    public Result<String> add(CartAddDTO dto) {
        Long userId = currentUserId();
        int qty = (dto.getQuantity() == null || dto.getQuantity() < 1) ? 1 : dto.getQuantity();

        Product product = productMapper.selectOnSaleById(dto.getProductId());
        if (product == null) {
            return Result.fail("商品不存在或已下架");
        }

        String now = LocalDateTime.now().format(FMT);
        Cart exist = cartMapper.selectByUserAndProduct(userId, dto.getProductId());

        if (exist == null) {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(dto.getProductId());
            cart.setQuantity(qty);
            cart.setCreateTime(now);
            cart.setUpdateTime(now);
            cartMapper.insert(cart);
            log.info("【购物车】买家 {} 新增商品 {} × {}", userId, dto.getProductId(), qty);
        } else {
            // ★ 重复加购是"累加"而不是"新增一行" —— tb_cart 上有 UNIQUE(user_id, product_id) 兜底
            int newQty = Math.min(exist.getQuantity() + qty, MAX_QUANTITY);
            cartMapper.updateQuantity(exist.getId(), newQty, now);
            log.info("【购物车】买家 {} 商品 {} 数量 {} → {}", userId, dto.getProductId(), exist.getQuantity(), newQty);
        }
        return Result.ok("已加入购物车");
    }

    // ==================== ② 修改数量（0 = 删除）====================
    public Result<String> updateQuantity(CartUpdateDTO dto) {
        Long userId = currentUserId();
        int qty = dto.getQuantity() == null ? 1 : dto.getQuantity();

        if (qty <= 0) {
            int rows = cartMapper.deleteByIdAndUser(dto.getCartId(), userId);
            return rows > 0 ? Result.ok("已从购物车移除") : Result.fail("购物车条目不存在");
        }
        if (qty > MAX_QUANTITY) {
            return Result.fail("单次最多购买 " + MAX_QUANTITY + " 件");
        }

        Cart cart = cartMapper.selectById(dto.getCartId());
        // ★ 越权校验：不能改别人的购物车
        if (cart == null || !userId.equals(cart.getUserId())) {
            return Result.fail("购物车条目不存在");
        }
        cartMapper.updateQuantity(dto.getCartId(), qty, LocalDateTime.now().format(FMT));
        return Result.ok("已更新数量");
    }

    // ==================== ③ 删除 ====================
    public Result<String> remove(Long cartId) {
        Long userId = currentUserId();
        int rows = cartMapper.deleteByIdAndUser(cartId, userId);   // ★ WHERE 带 user_id
        return rows > 0 ? Result.ok("已删除") : Result.fail("购物车条目不存在");
    }

    // ==================== ④ 列表 ====================
    public Result<List<CartItemVO>> list() {
        Long userId = currentUserId();
        List<CartItemVO> items = cartMapper.selectItemsByUser(userId);
        log.info("【购物车】买家 {} 共 {} 种商品", userId, items.size());
        return Result.ok(items);
    }

    // ==================== ⑤ ★★ 结算预览（原型图 9 的 step01 → step02）====================
    /**
     * 算钱，但**一行数据都不落库**。
     *
     * @param cartIds 逗号分隔的购物车 id；null / 空 = 全选
     */
    public Result<OrderPreviewVO> preview(String cartIds) {
        Long userId = currentUserId();
        List<Long> ids = parseIds(cartIds);

        List<CartItemVO> items = cartMapper.selectItemsByUser(userId).stream()
                .filter(i -> ids == null || ids.contains(i.getCartId()))
                .collect(Collectors.toList());

        if (items.isEmpty()) {
            return Result.fail("请先选择要购买的商品");
        }

        // ① 库存检查 —— 提前发现，别等到提交订单才报错
        for (CartItemVO item : items) {
            if (!Boolean.TRUE.equals(item.getStockEnough())) {
                return Result.fail("「" + item.getProductName() + "」库存不足（剩余 "
                        + item.getStock() + " 件，需要 " + item.getQuantity() + " 件）");
            }
        }

        // ② 算总额
        double total = 0.0;
        for (CartItemVO item : items) {
            total += item.getSubtotal();
        }

        // ③ ★ 查满减规则（配置在 tb_promotion 表里，不是写死的）
        Promotion matched = promotionMapper.selectBestMatch(0L, total);
        double discount = matched == null ? 0.0 : matched.getDiscount();

        // ④ 没达门槛时，算一下"还差多少" —— 原型图 10 提示"满 ¥1200 已优惠 ¥120"
        Promotion next = promotionMapper.selectNextTier(0L, total);

        OrderPreviewVO vo = new OrderPreviewVO();
        vo.setItems(items);
        vo.setTotalAmount(round(total));
        vo.setDiscountAmount(round(discount));
        vo.setPayAmount(round(total - discount));
        vo.setPromotionName(matched == null ? null : matched.getName());
        vo.setReachedThreshold(matched != null);
        vo.setGapToThreshold(next == null ? 0.0 : round(next.getThreshold() - total));

        log.info("【下单预览】买家 {} 商品 {} 种，总额 {}，优惠 {}，应付 {}",
                userId, items.size(), vo.getTotalAmount(), vo.getDiscountAmount(), vo.getPayAmount());
        return Result.ok(vo);
    }

    // ==================== 小工具 ====================
    private List<Long> parseIds(String cartIds) {
        if (cartIds == null || cartIds.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(cartIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toList());
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
