package com.shop.statemachine;

import com.shop.common.BizException;
import com.shop.entity.Order;
import com.shop.entity.OrderStatusLog;
import com.shop.entity.StateMachineRule;
import com.shop.mapper.OrderMapper;
import com.shop.mapper.OrderStatusLogMapper;
import com.shop.mapper.StateMachineRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ★★ 订单状态机（阶段 4 核心考点）
 *
 * 它解决的问题：订单状态不能随便改，必须按规则流转。
 *
 * 和「直接改 status 字段」的本质区别：
 *   ① 调用方只传【事件】，不传【目标状态】—— 改成什么由规则表决定
 *   ② 规则存在 tb_state_machine 表里 —— 加新状态改数据，不改代码
 *   ③ 非法流转直接抛异常 —— 而不是"悄悄改错"
 *   ④ 全项目唯一改 tb_order.status 的地方 —— 状态变更有且只有这一个入口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    /** 业务类型：这张规则表以后也能给别的业务用（biz_type 区分） */
    public static final String BIZ_ORDER = "order";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StateMachineRuleMapper ruleMapper;
    private final OrderMapper            orderMapper;
    private final OrderStatusLogMapper   logMapper;

    /**
     * 执行一次状态流转。
     *
     * @param orderId  订单 id
     * @param event    事件（写 OrderEvent 里的常量，如 OrderEvent.SHIP）
     * @param operator 操作人（如 "员工#1" / "system"）
     * @param remark   备注
     * @return 流转后的新状态码
     */
    public String fire(Long orderId, String event, String operator, String remark) {

        // ---------- ① 拿订单，看它现在是什么状态 ----------
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在：" + orderId);
        }
        String from = order.getStatus();

        // ---------- ② ★ 查规则表：这一步就是"状态机" ----------
        //     注意：不是 if (from.equals("待发货"))，而是"问表"。
        //     表里没有这行 → 这次流转非法。
        StateMachineRule rule = ruleMapper.selectRule(BIZ_ORDER, from, event);
        if (rule == null) {
            throw new BizException("订单当前状态【" + OrderStatus.cn(from)
                    + "】不允许执行【" + OrderEvent.cn(event) + "】");
        }
        String to = rule.getToStatus();

        // ---------- ③ 乐观锁更新（带原状态条件，防并发重复发货） ----------
        String now = LocalDateTime.now().format(FMT);
        int rows = orderMapper.updateStatus(orderId, from, to, now);
        if (rows == 0) {
            // 走到这里说明：查规则的时候还是 from，更新时已经不是了
            // → 有另一个人抢先改了状态。这就是乐观锁在起作用。
            throw new BizException("订单状态已被其他人变更，请刷新后重试");
        }

        // ---------- ④ 留痕（答辩时可以现场展示流转轨迹） ----------
        OrderStatusLog logRow = new OrderStatusLog();
        logRow.setOrderId(orderId);
        logRow.setFromStatus(from);
        logRow.setToStatus(to);
        logRow.setEvent(event);
        logRow.setOperator(operator);
        logRow.setRemark(remark == null ? rule.getRemark() : remark);
        logRow.setCreateTime(now);
        logMapper.insert(logRow);

        log.info("【状态机】订单 {} 流转：{} → {}（事件={} 操作人={}）",
                orderId, OrderStatus.cn(from), OrderStatus.cn(to), event, operator);

        return to;
    }
}
