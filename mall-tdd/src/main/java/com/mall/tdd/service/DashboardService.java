package com.mall.tdd.service;

import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.dto.HourAmount;
import com.mall.tdd.dto.MessageVO;
import com.mall.tdd.dto.MetricVO;
import com.mall.tdd.dto.MetricsVO;
import com.mall.tdd.dto.TrendVO;
import com.mall.tdd.entity.Message;
import com.mall.tdd.mapper.MessageMapper;
import com.mall.tdd.mapper.OrderMapper;
import com.mall.tdd.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 店铺 Dashboard：状态卡片 / 24h 趋势 / 四项经营指标 / 消息通知。
 *
 * <p>权限模型：全部方法只读 {@link UserContext#getShopId()}，<b>不接受调用方传 shopId</b>。
 * 一旦把 shopId 做成方法参数，越权查询就只是「传错一个数字」的问题了。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 一天 24 小时，趋势图点数是固定的 */
    private static final int HOURS_IN_DAY = 24;

    /** 消息最多返回条数。需求没提分页，给个上限防止全量返回 */
    private static final int MESSAGE_LIMIT = 20;

    private static final String MSG_NO_SHOP = "未登录或会话已失效，请重新登录";

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final MessageMapper messageMapper;
    /**
     * 注入而不是直接 {@code LocalDate.now()}。
     *
     * <p>★ 原来这里用系统时钟，测试也照着算「今天」，两边看着一致 ——
     * 但「零点前后执行」会让它们算出不同的日期，断言莫名其妙变红。
     * 同一个项目里 {@link OrderQueryService} 早就注入了 Clock，两种做法并存
     * 本身就是隐患：下次改一边忘另一边。
     */
    private final Clock clock;

    /**
     * 顶部 6 张状态卡片。
     *
     * <p>返回 {@code LinkedHashMap} 是为了让前端拿到的字段顺序稳定（待付款→待发货→…），
     * 但后端契约只保证<b>恰好这 6 个 key</b>。
     */
    public Result<Map<String, Long>> cards() {
        Long shopId = currentShopId();
        if (shopId == null) {
            return Result.unauthorized(MSG_NO_SHOP);
        }

        Map<String, Long> data = new LinkedHashMap<String, Long>();
        data.put("pendingPay", Long.valueOf(orderMapper.countByStatus(shopId, "PENDING_PAY")));
        data.put("pendingShip", Long.valueOf(orderMapper.countByStatus(shopId, "PENDING_SHIP")));
        data.put("pendingReceive", Long.valueOf(orderMapper.countByStatus(shopId, "PENDING_RECEIVE")));
        // ★ 「已完成」对应 SUCCESS —— 状态机里没有 COMPLETED 这个值
        data.put("completed", Long.valueOf(orderMapper.countByStatus(shopId, "SUCCESS")));
        // ★ 退款/售后合并为一个状态：库里没有独立售后单，需求缺口，本期不拆
        data.put("refundAfterSale", Long.valueOf(orderMapper.countByStatus(shopId, "REFUNDING")));
        data.put("stockWarning", Long.valueOf(productMapper.countStockWarning(shopId)));
        return Result.ok(data);
    }

    /**
     * 24 小时双曲线。
     *
     * <p>今日、昨日<b>各查一次</b>，不做「查最近两天再内存分组」—— 后者数据量一大就崩。
     * 两次都补齐 24 个点，缺口补 {@code 0.0}（不是 {@code null}）。
     */
    public Result<TrendVO> trend() {
        Long shopId = currentShopId();
        if (shopId == null) {
            return Result.unauthorized(MSG_NO_SHOP);
        }

        LocalDate now = LocalDate.now(clock);
        String today = now.toString();
        String yesterday = now.minusDays(1).toString();

        TrendVO data = new TrendVO();
        data.setToday(toHourlyPoints(orderMapper.sumPaidAmountByHour(shopId, today)));
        data.setYesterday(toHourlyPoints(orderMapper.sumPaidAmountByHour(shopId, yesterday)));
        return Result.ok(data);
    }

    /**
     * 四项核心经营指标，每项带同比昨日变化率。
     *
     * <p>客单价由「支付总额 ÷ 支付订单数」算出，没有现成查询 —— 分子分母各查一次。
     */
    public Result<MetricsVO> metrics() {
        Long shopId = currentShopId();
        if (shopId == null) {
            return Result.unauthorized(MSG_NO_SHOP);
        }

        LocalDate now = LocalDate.now(clock);
        String today = now.toString();
        String yesterday = now.minusDays(1).toString();

        long todayCustomers = orderMapper.countDistinctPaidCustomers(shopId, today);
        long yesterdayCustomers = orderMapper.countDistinctPaidCustomers(shopId, yesterday);
        long todayOrders = orderMapper.countPaidOrders(shopId, today);
        long yesterdayOrders = orderMapper.countPaidOrders(shopId, yesterday);
        // SUM 在无数据时返回 NULL，统一兜成 0：null 会让前端格式化直接崩
        double todayRefund = nullToZero(orderMapper.sumRefundAmount(shopId, today));
        double yesterdayRefund = nullToZero(orderMapper.sumRefundAmount(shopId, yesterday));
        double todayPaidSum = nullToZero(orderMapper.sumPaidAmount(shopId, today));
        double yesterdayPaidSum = nullToZero(orderMapper.sumPaidAmount(shopId, yesterday));

        double todayAverage = average(todayPaidSum, todayOrders);
        double yesterdayAverage = average(yesterdayPaidSum, yesterdayOrders);

        MetricsVO data = new MetricsVO();
        data.setPaidCustomers(MetricVO.of(todayCustomers, yesterdayCustomers));
        data.setPaidOrders(MetricVO.of(todayOrders, yesterdayOrders));
        data.setRefundAmount(MetricVO.of(todayRefund, yesterdayRefund));
        data.setAverageOrderValue(MetricVO.of(todayAverage, yesterdayAverage));
        return Result.ok(data);
    }

    /**
     * 消息通知，按时间倒序。
     *
     * <p>排序与条数都在 SQL 里完成，这里只做实体到 VO 的搬运 ——
     * 一旦在这层 {@code sorted()}，分页就会算错。
     */
    public Result<List<MessageVO>> messages() {
        Long shopId = currentShopId();
        if (shopId == null) {
            return Result.unauthorized(MSG_NO_SHOP);
        }

        List<Message> rows = messageMapper.selectByShop(shopId, MESSAGE_LIMIT);
        List<MessageVO> data = new ArrayList<MessageVO>();
        if (rows != null) {
            for (Message row : rows) {
                data.add(toVO(row));
            }
        }
        return Result.ok(data);
    }

    // ==========================================================
    // 内部工具
    // ==========================================================

    /**
     * 当前登录店铺 id。
     *
     * <p>返回 null 时调用方<b>必须立刻失败</b>，不能继续查库：
     * {@code WHERE shop_id = NULL} 查不到任何行，页面会变成一张全 0 的空表，
     * 商家会误以为「今天一单没有」，实际是登录态丢了（D01）。
     */
    private Long currentShopId() {
        return UserContext.getShopId();
    }

    /** 把稀疏的小时聚合结果，补齐成 24 个点（下标 = 小时，缺口填 0.0） */
    private List<Double> toHourlyPoints(List<HourAmount> rows) {
        List<Double> points = new ArrayList<Double>(HOURS_IN_DAY);
        for (int hour = 0; hour < HOURS_IN_DAY; hour++) {
            points.add(Double.valueOf(0D));
        }
        if (rows == null) {
            return points;
        }
        for (HourAmount row : rows) {
            Integer hour = row.getHour();
            if (hour == null || hour.intValue() < 0 || hour.intValue() >= HOURS_IN_DAY) {
                continue;
            }
            double amount = row.getAmount() == null ? 0D : row.getAmount().doubleValue();
            points.set(hour.intValue(), Double.valueOf(amount));
        }
        return points;
    }

    /**
     * 客单价。分母为 0 时返回 0.00。
     *
     * <p>{@code sum / 0} 在 double 下不会抛异常，而是得到 {@code Infinity} 或 {@code NaN}，
     * 前端会显示成 "Infinity%" / "NaN%" —— 所以必须显式拦。
     */
    private double average(double sum, long count) {
        if (count == 0L) {
            return 0D;
        }
        return sum / count;
    }

    /** SUM 在无数据时返回 null，这里统一兜成 0 */
    private double nullToZero(Double value) {
        return value == null ? 0D : value.doubleValue();
    }

    private MessageVO toVO(Message m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setType(m.getType());
        vo.setOrderNo(m.getOrderNo());
        vo.setContent(m.getContent());
        vo.setIsRead(m.getIsRead());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }
}
