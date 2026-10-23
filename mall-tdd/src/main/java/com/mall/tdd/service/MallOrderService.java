package com.mall.tdd.service;

import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.dto.BuyNowVO;
import com.mall.tdd.dto.OrderItemVO;
import com.mall.tdd.dto.OrderListVO;
import com.mall.tdd.dto.OrderVO;
import com.mall.tdd.dto.TabCountVO;
import com.mall.tdd.entity.Order;
import com.mall.tdd.entity.OrderItem;
import com.mall.tdd.entity.Product;
import com.mall.tdd.mapper.OrderMapper;
import com.mall.tdd.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端订单管理：列表 / Tab 计数 / 删除 / 立即购买。
 *
 * <p>权限模型：<b>登录才能看，且只能看自己的</b>。userId 一律从
 * {@link UserContext} 取，绝不从请求参数取 —— 否则改个 {@code ?userId=} 就能看别人的订单，
 * 这是最典型的水平越权（U01）。
 */
@Service
@RequiredArgsConstructor
public class MallOrderService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 上限防止 {@code ?size=999999} 把库拖垮 */
    private static final int MAX_PAGE_SIZE = 50;

    // Tab 对应的 4 个状态，顺序与需求原文一致
    private static final String TAB_PENDING_PAY = "PENDING_PAY";
    private static final String TAB_PENDING_SHIP = "PENDING_SHIP";
    private static final String TAB_PENDING_RECEIVE = "PENDING_RECEIVE";
    private static final String TAB_SUCCESS = "SUCCESS";

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;

    /**
     * C 端订单列表。
     *
     * @param status  Tab 状态；null = 全部
     * @param orderNo 订单号，精确匹配；null / 空串 = 不按订单号筛
     */
    public Result<OrderListVO> listOrders(String status, String orderNo,
                                          Integer page, Integer pageSize) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // ★ 未登录时一个查询都不许发：发了就会变成 WHERE user_id = NULL，
            //   页面显示 0 条，用户以为订单没了，而不是以为登录过期了。
            return Result.unauthorized("请先登录");
        }

        int size = normalizePageSize(pageSize);
        int offset = (normalizePage(page) - 1) * size;
        String no = trimToNull(orderNo);

        List<Order> orders = orderMapper.selectByUserPage(userId, status, no, size, offset);

        OrderListVO data = new OrderListVO();
        data.setOrders(toVOs(orders));
        // 总数与列表同口径：同一个 userId、同一组筛选条件
        data.setTotal(orderMapper.countByUser(userId, status, no));
        data.setTabs(buildTabs(userId));
        return Result.ok(data);
    }

    /** 删除订单。买家删除 = 仅隐藏，后台仍可见 */
    public Result<Void> deleteOrder(Long orderId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        if (orderId == null) {
            return Result.fail("订单 id 不能为空");
        }

        // 越权由 SQL 的 WHERE id = ? AND user_id = ? 天然挡住，别人的订单影响行数为 0
        int affected = orderMapper.softDeleteByIdAndUser(orderId, userId);
        if (affected == 0) {
            return Result.fail("订单不存在或无权操作");
        }
        return Result.ok(null);
    }

    /** 立即购买：返回下单页需要的商品信息，前提是仍在售且有货 */
    public Result<BuyNowVO> buyNow(Long productId) {
        Product product = productMapper.selectByIdAndOnSale(productId);
        if (product == null) {
            // 已下架与不存在返回同一句：不给探测留口子
            return Result.fail("商品不存在或已下架");
        }
        Integer stock = product.getStock();
        if (stock == null || stock.intValue() <= 0) {
            // ★ 提前拦掉售罄：否则前端跳到下单页再报错，体验断层
            return Result.fail("商品已售罄");
        }

        BuyNowVO vo = new BuyNowVO();
        vo.setProductId(product.getId());
        vo.setName(product.getName());
        vo.setPrice(product.getPrice());
        vo.setStock(stock);
        vo.setStatus(product.getStatus());
        return Result.ok(vo);
    }

    /**
     * Tab 计数。
     *
     * <p>★★ {@code all} 用<b>独立统计</b>，不是 4 个 Tab 相加。
     * 订单有 7 种状态，{@code REFUNDING} / {@code PENDING_GROUP} / {@code FAILED}
     * 不属于任何一个 Tab。真实库实测：shop1 共 38 单、4 个 Tab 之和只有 36，
     * 差的 2 条正是退款中。按求和实现，买家点「全部」就看不到自己正在退款的订单。
     */
    private TabCountVO buildTabs(Long userId) {
        TabCountVO tabs = new TabCountVO();
        tabs.setAll(orderMapper.countByUser(userId, null, null));
        tabs.setPendingPay(orderMapper.countByUser(userId, TAB_PENDING_PAY, null));
        tabs.setPendingShip(orderMapper.countByUser(userId, TAB_PENDING_SHIP, null));
        tabs.setPendingReceive(orderMapper.countByUser(userId, TAB_PENDING_RECEIVE, null));
        tabs.setSuccess(orderMapper.countByUser(userId, TAB_SUCCESS, null));
        return tabs;
    }

    /** 批量装配明细：一次 IN 查询，避免 N+1 */
    private List<OrderVO> toVOs(List<Order> orders) {
        List<OrderVO> data = new ArrayList<OrderVO>();
        if (orders == null || orders.isEmpty()) {
            return data;
        }

        List<Long> ids = new ArrayList<Long>(orders.size());
        for (Order o : orders) {
            ids.add(o.getId());
        }
        Map<Long, List<OrderItemVO>> byOrder = groupItemsById(ids);

        for (Order o : orders) {
            OrderVO vo = toVO(o);
            List<OrderItemVO> items = byOrder.get(o.getId());
            vo.setItems(items == null ? new ArrayList<OrderItemVO>() : items);
            data.add(vo);
        }
        return data;
    }

    private Map<Long, List<OrderItemVO>> groupItemsById(List<Long> orderIds) {
        Map<Long, List<OrderItemVO>> byOrder = new HashMap<Long, List<OrderItemVO>>();
        List<OrderItem> items = orderMapper.selectItemsByOrderIds(orderIds);
        if (items == null) {
            return byOrder;
        }
        for (OrderItem it : items) {
            List<OrderItemVO> list = byOrder.get(it.getOrderId());
            if (list == null) {
                list = new ArrayList<OrderItemVO>();
                byOrder.put(it.getOrderId(), list);
            }
            list.add(toItemVO(it));
        }
        return byOrder;
    }

    private OrderVO toVO(Order o) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        // shop_name 是下单时的快照列，不 JOIN tb_shop —— 店铺改名不该影响历史订单展示
        vo.setShopName(o.getShopName());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setStatus(o.getStatus());
        vo.setPayType(o.getPayType());
        vo.setCreateTime(o.getCreateTime());
        return vo;
    }

    private OrderItemVO toItemVO(OrderItem it) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(it.getProductId());
        vo.setProductName(it.getProductName());
        vo.setProductImage(it.getProductImage());
        vo.setPrice(it.getPrice());
        vo.setQuantity(it.getQuantity());
        return vo;
    }

    private int normalizePage(Integer page) {
        return page == null || page.intValue() < 1 ? 1 : page.intValue();
    }

    private int normalizePageSize(Integer size) {
        if (size == null || size.intValue() < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size.intValue(), MAX_PAGE_SIZE);
    }

    /** 空串当「不筛」：否则 SQL 变成 {@code order_no = ''}，用户会觉得「怎么搜都搜不到」 */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
