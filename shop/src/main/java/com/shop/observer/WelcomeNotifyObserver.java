package com.shop.observer;

import com.shop.account.UserType;
import com.shop.entity.Message;
import com.shop.event.UserRegisteredEvent;
import com.shop.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeNotifyObserver {

    private final MessageMapper messageMapper;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        // ★★ 阶段 5 新增：前台买家不属于任何店铺，不发店铺欢迎信
        //    原因：① tb_messages.shop_id 是店铺维度的，买家没有 shop_id
        //         ② 前台没有站内信入口，发了也没人看得见
        //    ★ 这是业务取舍，不是技术做不到 —— 如果将来前台要加消息中心，删掉这两行即可。
        if (!UserType.ADMIN.equals(event.getUserType())) {
            return;
        }

        Message msg = new Message();
        msg.setShopId(event.getShopId());
        msg.setSenderId(null);                                    // null = 系统消息
        msg.setTitle("欢迎入驻");
        msg.setContent("欢迎 " + event.getUsername() + "，您的店铺已创建成功！");
        msg.setMsgType(1);                                        // 1-系统通知
        msg.setIsRead(0);
        messageMapper.insert(msg);

        log.info("【观察者-欢迎通知】已向店铺 {} 发送欢迎站内信", event.getShopId());
    }
}
