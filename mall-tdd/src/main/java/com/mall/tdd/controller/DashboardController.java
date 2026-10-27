package com.mall.tdd.controller;

import com.mall.tdd.common.Result;
import com.mall.tdd.dto.MessageVO;
import com.mall.tdd.dto.MetricsVO;
import com.mall.tdd.dto.TrendVO;
import com.mall.tdd.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 店铺 Dashboard：状态卡、经营指标、24h 趋势、消息通知。4 个接口。
 *
 * <p>纯透传。折线颜色、卡片字体、Y 轴自适应这些是前端配置项，
 * 后端只交付<b>数据契约</b>（见拆解-00 §五）。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 6 张状态卡：只返回 key 与数字 */
    @GetMapping("/cards")
    public Result<Map<String, Long>> cards() {
        return dashboardService.cards();
    }

    /** 4 项经营指标：value 与 dod（同比）都是 double */
    @GetMapping("/metrics")
    public Result<MetricsVO> metrics() {
        return dashboardService.metrics();
    }

    /** 24h 双曲线：2 条 × 24 点，缺口补 0.0（补 null 会让 ECharts 断线） */
    @GetMapping("/trend")
    public Result<TrendVO> trend() {
        return dashboardService.trend();
    }

    @GetMapping("/messages")
    public Result<List<MessageVO>> messages() {
        return dashboardService.messages();
    }
}
