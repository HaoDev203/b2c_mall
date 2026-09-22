package com.shop.template;

import com.shop.common.BizException;
import com.shop.dto.ProductDTO;
import com.shop.entity.Message;
import com.shop.entity.Product;
import com.shop.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 虚拟商品 / 电子卡券发布实现。
 */
@Slf4j
@Service("virtualProductPublish")
public class VirtualProductPublishService extends AbstractProductPublishTemplate {

    @Autowired
    private MessageMapper messageMapper;

    /** ★ 虚拟商品的钩子：不校验库存，只校验合规 */
    @Override
    protected void auditContent(ProductDTO dto) {
        // 注意：这里完全没有出现 stock —— 虚拟商品无库存概念
        if (!StringUtils.hasText(dto.getProductCode())) {
            throw new BizException("虚拟商品必须填写卡券编号 / 资质编号（productCode）");
        }
        if (!StringUtils.hasText(dto.getValidEndTime())) {
            throw new BizException("虚拟商品必须填写有效期（validEndTime，格式 yyyy-MM-dd）");
        }
        LocalDate end;
        try {
            end = LocalDate.parse(dto.getValidEndTime().trim());
        } catch (DateTimeParseException e) {
            throw new BizException("有效期格式错误，应为 yyyy-MM-dd：" + dto.getValidEndTime());
        }
        if (end.isBefore(LocalDate.now())) {
            throw new BizException("卡券已过期，不能上架：" + dto.getValidEndTime());
        }
        log.info("【模板方法·虚拟】④ 合规校验通过：productCode={}，有效期至 {}",
                dto.getProductCode(), dto.getValidEndTime());
    }

    /**
     * ★★ 可选钩子的实战演示：父类默认什么都不做（空实现），这里覆盖它，
     *    在虚拟商品上架后给店铺发一条站内信 —— 正好能被阶段 2 的 Dashboard 消息列表看到。
     *
     * ★ 关键点：父类的 publish() 骨架**一个字都没改**，就多出了一个新行为。
     *   这就是需求文档说的"开闭原则落到代码上"。
     */
    @Override
    protected void afterProcess(Product product) {
        Message msg = new Message();
        msg.setShopId(product.getShopId());
        msg.setSenderId(null);                       // null = 系统消息
        msg.setTitle("虚拟商品已上架");
        msg.setContent("您的商品【" + product.getName()
                + "】已自动上架，卡券有效期至 " + product.getValidEndTime() + "。");
        msg.setMsgType(1);
        msg.setIsRead(0);
        messageMapper.insert(msg);
        log.info("【模板方法·虚拟】⑦ afterProcess 钩子触发：已发送站内信（商品 id={}）", product.getId());
    }
}
