-- ============================================================
-- 阶段 2 测试数据：给 tb_messages 造 5 条消息（3 未读 + 2 已读）
-- 用途：Dashboard 消息列表 / 未读数缓存 的验证数据
--       不插的话列表里只有注册时那 1 条欢迎信，看不出效果
-- 可重复执行：先按标题删掉上次插入的，再重新插入
-- 执行方式：IDEA → Database → shop.db → Query Console → 粘贴执行
-- ============================================================

DELETE FROM tb_messages
WHERE title IN ('平台维护通知', '新品审核通过', '有订单待发货', '中秋活动报名开启', '结算账单已生成');

INSERT INTO tb_messages (shop_id, sender_id, title, content, msg_type, is_read, read_time)
VALUES ((SELECT id FROM tb_shop LIMIT 1), NULL, '平台维护通知',
        '系统将于今晚 23:00-24:00 升级，期间后台暂停使用，请提前保存数据。', 1, 0, NULL),
       ((SELECT id FROM tb_shop LIMIT 1), NULL, '新品审核通过',
        '您提交的商品「秋季新款卫衣」已通过审核，现已上架。', 1, 0, NULL),
       ((SELECT id FROM tb_shop LIMIT 1), NULL, '有订单待发货',
        '您有 3 笔订单待发货，请及时处理以免影响店铺评分。', 2, 0, NULL),
       ((SELECT id FROM tb_shop LIMIT 1), NULL, '中秋活动报名开启',
        '中秋大促报名通道已开启，报名截止 9 月 25 日 18:00。', 3, 1, '2026-09-16 10:12:00'),
       ((SELECT id FROM tb_shop LIMIT 1), NULL, '结算账单已生成',
        '您 8 月的结算账单已生成，请前往财务中心查看。', 1, 1, '2026-09-16 18:30:00');

-- 核对：总数应为 6，未读数应为 4（原有那条「欢迎入驻」也是未读）
SELECT COUNT(*)                                        AS 总数,
       SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END)    AS 未读数
FROM tb_messages;
