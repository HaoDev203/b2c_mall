package com.shop.mapper;

import com.shop.entity.StateMachineRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface StateMachineRuleMapper {

    /**
     * ★ 状态机的核心查询：给定「当前状态 + 事件」，找出「允许去的目标状态」。
     * 查不到（返回 null）= 这次流转非法 → 由 OrderStateMachine 抛异常。
     */
    @Select("SELECT * FROM tb_state_machine " +
            "WHERE biz_type = #{bizType} AND from_status = #{fromStatus} AND event = #{event}")
    StateMachineRule selectRule(@Param("bizType") String bizType,
                                @Param("fromStatus") String fromStatus,
                                @Param("event") String event);

    /** 把全部规则列出来（调试 / 答辩展示用） */
    @Select("SELECT * FROM tb_state_machine WHERE biz_type = #{bizType} ORDER BY id")
    List<StateMachineRule> selectByBizType(@Param("bizType") String bizType);
}
