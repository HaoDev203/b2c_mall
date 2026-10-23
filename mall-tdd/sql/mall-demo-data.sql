-- ============================================================
-- mall-tdd / 演示数据（SQLite）
-- ------------------------------------------------------------
-- 用途：
--   1. 验证 88 条里「Mock 测不到」的 SQL 语义
--      · COUNT(DISTINCT user_id)   —— D14 的痛点
--      · stock <= stock_warn       —— D08 的痛点（含等号、NULL 不预警）
--      · ORDER BY create_time DESC —— D20 的痛点
--   2. 后续页面联调与端到端验收
--
-- 日期一律用相对日期（date('now','localtime')），
-- 所以这份数据永远「今天/昨天/前天」都有，不会隔天失效。
--
-- 可重复执行：开头先清空 6 张表（只清本模块自己的库，与 shop/shop.db 无关）。
-- 执行：见 mall-tdd/README.md
-- ============================================================

PRAGMA foreign_keys = OFF;

DELETE FROM tb_message;
DELETE FROM tb_order_item;
DELETE FROM tb_order;
DELETE FROM tb_product;
DELETE FROM tb_category;
DELETE FROM tb_shop;

-- ============================================================
-- ① 店铺：1 = 主店（要验的那个），2 = 干扰店（防跨店混入）
-- ============================================================
INSERT INTO tb_shop (id, name, logo, phone, address, status, create_time) VALUES
(1, '云边优选旗舰店', '/img/shop1.png', '13800000001', '杭州市余杭区', 'normal', datetime('now','localtime')),
(2, '隔壁老王杂货铺', '/img/shop2.png', '13800000002', '杭州市西湖区', 'normal', datetime('now','localtime'));

-- ============================================================
-- ② 分类：故意放入「同名不同 id」的两行
--    → Service 层必须按 name 保留首条（LinkedHashMap），SQL 的 DISTINCT * 去不掉
--    → 期望 C 端导航返回 3 个名字：手机数码 / 家用电器 / 服饰鞋包
-- ============================================================
INSERT INTO tb_category (id, parent_id, name, level, type, sort) VALUES
(1, 0, '手机数码', 1, 'platform', 1),
(2, 0, '手机数码', 1, 'platform', 2),   -- ★ 同名不同 id，去重靶子
(3, 0, '家用电器', 1, 'platform', 3),
(4, 0, '服饰鞋包', 1, 'platform', 4);

-- ============================================================
-- ③ 商品
--    库存预警口径：stock <= stock_warn（含等号）
--    ★ stock_warn IS NULL 的商品有意不预警（不配预警线 = 不参与）
--    → shop 1 期望预警 3 件：p2(5<=10)、p3(10<=10 等号)、p4(2<=5)
--    → p5 阈值 NULL、p6 库存充足、p7/p8 属 shop 2（混入就会变 5，一眼可见）
-- ============================================================
INSERT INTO tb_product
(id, shop_id, name, keyword, selling_point, category_id, main_image, price, market_price,
 stock, warehouse_stock, store_stock, stock_warn, sales, inflated_sales,
 goods_type, category_kind, delivery_types, product_code, status, sort, create_time) VALUES
-- ---- shop 1 ----
(1, 1, '智能手机 Pro',   '手机,5G', '旗舰芯片',   1, '/img/p1.png', 2999.00, 3299.00, 100,  60,  40, 10,   320,  500, 'normal',  'physical', 'express',                'SC-P1', 'on_sale',  100, datetime('now','localtime','-30 day')),
(2, 1, '无线耳机',       '耳机',   '主动降噪',   1, '/img/p2.png',  199.00,  299.00,   5,   3,   2, 10,   890, 1200, 'seckill', 'physical', 'express',                'SC-P2', 'on_sale',   90, datetime('now','localtime','-25 day')),
(3, 1, '电饭煲',         '电饭煲', '4L 大容量',  3, '/img/p3.png',  399.00,  499.00,  10,   6,   4, 10,   150,  200, 'normal',  'physical', 'express,self_pickup',    'SC-P3', 'on_sale',   80, datetime('now','localtime','-20 day')),
(4, 1, '变频空调',       '空调',   '一级能效',   3, '/img/p4.png', 3299.00, 3899.00,   2,   1,   1,  5,    40,   60, 'normal',  'physical', 'express',                'SC-P4', 'on_sale',   70, datetime('now','localtime','-18 day')),
(5, 1, '限量球鞋',       '球鞋',   '联名款',     4, '/img/p5.png',  899.00, 1099.00,   0,   0,   0, NULL,  5,   30, 'normal',  'physical', 'express',                'SC-P5', 'on_sale',   60, datetime('now','localtime','-15 day')),
(6, 1, '旧款手机 X',     '手机',   '清仓',       1, '/img/p6.png',  999.00, 1299.00,  50,  30,  20,  3,     0,    0, 'normal',  'physical', 'express',                'SC-P6', 'off_sale',  10, datetime('now','localtime','-60 day')),
(7, 1, '电子卡券·洗车',   '卡券',   '虚拟发货',   4, '/img/p7.png',   59.00,   79.00, 999,   0,   0, 10,    12,   20, 'normal',  'card',     'virtual',                'SC-P7', 'on_sale',   50, datetime('now','localtime','-10 day')),
-- ---- shop 2（干扰店）----
(8, 2, '隔壁耳机',       '耳机',   '便宜',       1, '/img/p8.png',   99.00,  129.00,   1,   1,   0,  5,    10,   10, 'normal',  'physical', 'express',                'SC-P8', 'on_sale',   40, datetime('now','localtime','-9 day')),
(9, 2, '隔壁数据线',     '数据线', '快充',       1, '/img/p9.png',   19.00,   29.00,   1,   1,   0,  5,    20,   20, 'normal',  'physical', 'express',                'SC-P9', 'on_sale',   30, datetime('now','localtime','-8 day'));

-- ============================================================
-- ④ 订单 —— 今天 13 单 / 昨天 15 单 / 前天 10 单（shop 1）
--
--    「已支付」口径 = pay_time IS NOT NULL（不看 status）
--    今天已支付 9 单，来自 7 个不同 user_id（u1001、u1002 各两单）
--      → 订单数 9 ≠ 客户数 7，这正是 D14 要钉死的 COUNT(DISTINCT)
--    今天支付总额 = 5117.90，客单价 = 5117.90 / 9 = 568.6555…
--    今天 9 时两笔（128.00 + 356.50 = 484.50），验「同一小时累加不是覆盖」
-- ============================================================

-- ---------- 今天 13 单 ----------
INSERT INTO tb_order
(order_no, user_id, shop_id, total_amount, pay_amount, status, pay_type, order_type, order_category,
 logistics_type, store_name, tracking_no, receiver_name, receiver_phone, receiver_address,
 shipping_status, after_sale_status, is_deleted, remark, create_time, pay_time, ship_time) VALUES
-- 已支付 9 单
('SO-D0-001', 1001, 1, 128.00, 128.00, 'PENDING_SHIP',    'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '张三', '13900001001', '杭州市余杭区文一西路 1 号', 'PENDING', 'none', 0, NULL,
 datetime('now','localtime','start of day','+9 hours'),    datetime('now','localtime','start of day','+9 hours','+15 minutes'),  NULL),
('SO-D0-002', 1002, 1, 356.50, 356.50, 'PENDING_RECEIVE',  'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000002', '李四', '13900001002', '杭州市余杭区文一西路 2 号', 'SHIPPED', 'none', 0, NULL,
 datetime('now','localtime','start of day','+9 hours'),    datetime('now','localtime','start of day','+9 hours','+40 minutes'),
 datetime('now','localtime','start of day','+13 hours')),
('SO-D0-003', 1001, 1,  99.00,  99.00, 'SUCCESS',          'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000003', '张三', '13900001001', '杭州市余杭区文一西路 1 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','+10 hours'),   datetime('now','localtime','start of day','+10 hours','+20 minutes'),
 datetime('now','localtime','start of day','+12 hours')),
('SO-D0-004', 1003, 1, 199.00, 199.00, 'SUCCESS',          'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000004', '王五', '13900001003', '杭州市西湖区文三路 3 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','+11 hours'),   datetime('now','localtime','start of day','+11 hours','+5 minutes'),
 datetime('now','localtime','start of day','+13 hours')),
('SO-D0-005', 1004, 1, 799.00, 799.00, 'PENDING_SHIP',    'wechat', 'normal', 'normal', 'city',
 '云边优选·城西店', NULL, '赵六', '13900001004', '杭州市拱墅区莫干山路 4 号', 'PENDING', 'none', 0, NULL,
 datetime('now','localtime','start of day','+14 hours'),   datetime('now','localtime','start of day','+14 hours','+30 minutes'), NULL),
('SO-D0-006', 1005, 1,  49.90,  49.90, 'SUCCESS',          'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000006', '孙七', '13900001005', '杭州市滨江区江南大道 5 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','+15 hours'),   datetime('now','localtime','start of day','+15 hours'),
 datetime('now','localtime','start of day','+17 hours')),
('SO-D0-007', 1006, 1, 2999.00, 2999.00, 'SUCCESS',        'paypal', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000007', '周八', '13900001006', '杭州市上城区延安路 6 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','+16 hours'),   datetime('now','localtime','start of day','+16 hours','+45 minutes'),
 datetime('now','localtime','start of day','+18 hours')),
('SO-D0-008', 1007, 1, 399.00, 399.00, 'SUCCESS',          'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000008', '吴九', '13900001007', '杭州市钱塘区一号大街 7 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','+20 hours'),   datetime('now','localtime','start of day','+20 hours','+10 minutes'),
 datetime('now','localtime','start of day','+21 hours')),
-- ★ 退款单：pay_time 非空（钱确实收了），status = REFUNDING，create_time 归今天
('SO-D0-009', 1002, 1,  88.50,  88.50, 'REFUNDING',        'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000009', '李四', '13900001002', '杭州市余杭区文一西路 2 号', 'RECEIVED', 'applying', 0, '尺码不合适',
 datetime('now','localtime','start of day','+18 hours'),   datetime('now','localtime','start of day','+18 hours'),
 datetime('now','localtime','start of day','+19 hours')),
-- 未支付 4 单（pay_time 为 NULL）→ 待付款卡片 +4
('SO-D0-010', 1001, 1, 199.00, 199.00, 'PENDING_PAY', 'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '张三', '13900001001', '杭州市余杭区文一西路 1 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','+10 hours','+50 minutes'), NULL, NULL),
('SO-D0-011', 1008, 1,  59.00,  59.00, 'PENDING_PAY', 'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '郑十', '13900001008', '杭州市萧山区市心路 8 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','+12 hours'), NULL, NULL),
('SO-D0-012', 1009, 1, 1299.00, 1299.00, 'PENDING_PAY', 'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '钱十一', '13900001009', '杭州市临平区南苑街道 9 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','+13 hours'), NULL, NULL),
('SO-D0-013', 1010, 1,  25.00,  25.00, 'PENDING_PAY', 'other', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '孙十二', '13900001010', '杭州市富阳区桂花路 10 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','+16 hours'), NULL, NULL);

-- ---------- 昨天 15 单 ----------
INSERT INTO tb_order
(order_no, user_id, shop_id, total_amount, pay_amount, status, pay_type, order_type, order_category,
 logistics_type, store_name, tracking_no, receiver_name, receiver_phone, receiver_address,
 shipping_status, after_sale_status, is_deleted, remark, create_time, pay_time, ship_time) VALUES
-- 已支付 10 单，客户 9 个（u1003 两单）
('SO-D1-001', 1001, 1,  210.00,  210.00, 'SUCCESS',        'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000101', '张三', '13900001001', '杭州市余杭区文一西路 1 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+8 hours'),   datetime('now','localtime','start of day','-1 day','+8 hours','+30 minutes'), datetime('now','localtime','start of day','-1 day','+10 hours')),
('SO-D1-002', 1002, 1,   88.00,   88.00, 'SUCCESS',        'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000102', '李四', '13900001002', '杭州市余杭区文一西路 2 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+9 hours'),   datetime('now','localtime','start of day','-1 day','+9 hours','+10 minutes'),  datetime('now','localtime','start of day','-1 day','+11 hours')),
('SO-D1-003', 1003, 1,  560.00,  560.00, 'SUCCESS',        'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000103', '王五', '13900001003', '杭州市西湖区文三路 3 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+10 hours'),  datetime('now','localtime','start of day','-1 day','+10 hours'),              datetime('now','localtime','start of day','-1 day','+12 hours')),
('SO-D1-004', 1003, 1,   45.00,   45.00, 'SUCCESS',        'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000104', '王五', '13900001003', '杭州市西湖区文三路 3 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+11 hours'),  datetime('now','localtime','start of day','-1 day','+11 hours','+30 minutes'), datetime('now','localtime','start of day','-1 day','+13 hours')),
('SO-D1-005', 1004, 1, 1200.00, 1200.00, 'SUCCESS',        'wechat', 'normal', 'normal', 'city',
 '云边优选·城西店', 'SF1000000105', '赵六', '13900001004', '杭州市拱墅区莫干山路 4 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+12 hours'),  datetime('now','localtime','start of day','-1 day','+12 hours','+20 minutes'), datetime('now','localtime','start of day','-1 day','+14 hours')),
('SO-D1-006', 1005, 1,  320.00,  320.00, 'PENDING_SHIP',   'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '孙七', '13900001005', '杭州市滨江区江南大道 5 号', 'PENDING', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+14 hours'),  datetime('now','localtime','start of day','-1 day','+14 hours'),              NULL),
('SO-D1-007', 1011, 1,   76.50,   76.50, 'SUCCESS',        'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000107', '冯十三', '13900001011', '杭州市余杭区良渚街道 11 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+15 hours'),  datetime('now','localtime','start of day','-1 day','+15 hours','+40 minutes'), datetime('now','localtime','start of day','-1 day','+17 hours')),
('SO-D1-008', 1012, 1,  430.00,  430.00, 'SUCCESS',        'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000108', '陈十四', '13900001012', '杭州市钱塘区二号大街 12 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+17 hours'),  datetime('now','localtime','start of day','-1 day','+17 hours'),              datetime('now','localtime','start of day','-1 day','+19 hours')),
('SO-D1-009', 1013, 1,  150.00,  150.00, 'PENDING_RECEIVE','alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000109', '褚十五', '13900001013', '杭州市萧山区金城路 13 号', 'SHIPPED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+19 hours'),  datetime('now','localtime','start of day','-1 day','+19 hours','+25 minutes'), datetime('now','localtime','start of day','-1 day','+22 hours')),
('SO-D1-010', 1014, 1,  999.00,  999.00, 'SUCCESS',        'paypal', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000110', '卫十六', '13900001014', '杭州市上城区解放路 14 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+21 hours'),  datetime('now','localtime','start of day','-1 day','+21 hours','+50 minutes'), datetime('now','localtime','start of day','-1 day','+23 hours')),
-- 未支付 5 单 → 待付款卡片 +5
('SO-D1-011', 1015, 1,  66.00,  66.00, 'PENDING_PAY', 'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '蒋十七', '13900001015', '杭州市临平区星桥街道 15 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+9 hours'),  NULL, NULL),
('SO-D1-012', 1016, 1,  77.00,  77.00, 'PENDING_PAY', 'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '沈十八', '13900001016', '杭州市富阳区春江街道 16 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+11 hours'), NULL, NULL),
('SO-D1-013', 1017, 1,  88.00,  88.00, 'PENDING_PAY', 'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '韩十九', '13900001017', '杭州市桐庐县迎春南路 17 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+13 hours'), NULL, NULL),
('SO-D1-014', 1018, 1,  99.00,  99.00, 'PENDING_PAY', 'other', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '杨二十', '13900001018', '杭州市建德市新安东路 18 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+16 hours'), NULL, NULL),
('SO-D1-015', 1019, 1, 111.00, 111.00, 'PENDING_PAY', 'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '朱廿一', '13900001019', '杭州市淳安县千岛湖镇 19 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','-1 day','+18 hours'), NULL, NULL);

-- ---------- 前天 10 单（主要给 B5 订单查询的日期筛选当靶子）----------
INSERT INTO tb_order
(order_no, user_id, shop_id, total_amount, pay_amount, status, pay_type, order_type, order_category,
 logistics_type, store_name, tracking_no, receiver_name, receiver_phone, receiver_address,
 shipping_status, after_sale_status, is_deleted, remark, create_time, pay_time, ship_time) VALUES
('SO-D2-001', 1001, 1,  520.00,  520.00, 'SUCCESS',        'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000201', '张三', '13900001001', '杭州市余杭区文一西路 1 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+9 hours'),  datetime('now','localtime','start of day','-2 day','+9 hours','+20 minutes'),  datetime('now','localtime','start of day','-2 day','+11 hours')),
('SO-D2-002', 1002, 1,  310.00,  310.00, 'SUCCESS',        'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000202', '李四', '13900001002', '杭州市余杭区文一西路 2 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+10 hours'), datetime('now','localtime','start of day','-2 day','+10 hours','+15 minutes'), datetime('now','localtime','start of day','-2 day','+12 hours')),
('SO-D2-003', 1003, 1,  180.00,  180.00, 'SUCCESS',        'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000203', '王五', '13900001003', '杭州市西湖区文三路 3 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+11 hours'), datetime('now','localtime','start of day','-2 day','+11 hours'),              datetime('now','localtime','start of day','-2 day','+13 hours')),
('SO-D2-004', 1004, 1,  650.00,  650.00, 'SUCCESS',        'wechat', 'normal', 'normal', 'city',
 '云边优选·城西店', 'SF1000000204', '赵六', '13900001004', '杭州市拱墅区莫干山路 4 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+13 hours'), datetime('now','localtime','start of day','-2 day','+13 hours','+30 minutes'), datetime('now','localtime','start of day','-2 day','+15 hours')),
('SO-D2-005', 1005, 1,  240.00,  240.00, 'SUCCESS',        'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000205', '孙七', '13900001005', '杭州市滨江区江南大道 5 号', 'RECEIVED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+15 hours'), datetime('now','localtime','start of day','-2 day','+15 hours','+10 minutes'), datetime('now','localtime','start of day','-2 day','+17 hours')),
('SO-D2-006', 1006, 1,  880.00,  880.00, 'PENDING_SHIP',   'paypal', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '周八', '13900001006', '杭州市上城区延安路 6 号', 'PENDING', 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+17 hours'), datetime('now','localtime','start of day','-2 day','+17 hours','+40 minutes'), NULL),
('SO-D2-007', 1007, 1,  420.00,  420.00, 'PENDING_RECEIVE','alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000207', '吴九', '13900001007', '杭州市钱塘区一号大街 7 号', 'SHIPPED', 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+19 hours'), datetime('now','localtime','start of day','-2 day','+19 hours','+5 minutes'),  datetime('now','localtime','start of day','-2 day','+21 hours')),
-- 前天也有一条退款（让「退款金额按 create_time 归属」能跨天验证）
('SO-D2-008', 1008, 1,  188.00,  188.00, 'REFUNDING',      'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', 'SF1000000208', '郑十', '13900001008', '杭州市萧山区市心路 8 号', 'RECEIVED', 'applying', 0, '七天无理由',
 datetime('now','localtime','start of day','-2 day','+20 hours'), datetime('now','localtime','start of day','-2 day','+20 hours'),             datetime('now','localtime','start of day','-2 day','+22 hours')),
('SO-D2-009', 1009, 1,   66.00,   66.00, 'PENDING_PAY',    'wechat', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '钱十一', '13900001009', '杭州市临平区南苑街道 9 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+10 hours'), NULL, NULL),
('SO-D2-010', 1010, 1,   99.00,   99.00, 'PENDING_PAY',    'alipay', 'normal', 'normal', 'express',
 '云边优选·总仓', NULL, '孙十二', '13900001010', '杭州市富阳区桂花路 10 号', NULL, 'none', 0, NULL,
 datetime('now','localtime','start of day','-2 day','+14 hours'), NULL, NULL);

-- ---------- shop 2 干扰订单：金额写成 9999，混入时一眼可见 ----------
INSERT INTO tb_order
(order_no, user_id, shop_id, total_amount, pay_amount, status, create_time, pay_time) VALUES
('SO-X2-001', 2001, 2, 9999.00, 9999.00, 'SUCCESS',       datetime('now','localtime','start of day','+9 hours'),  datetime('now','localtime','start of day','+9 hours')),
('SO-X2-002', 2002, 2, 9999.00, 9999.00, 'SUCCESS',       datetime('now','localtime','start of day','+10 hours'), datetime('now','localtime','start of day','+10 hours')),
('SO-X2-003', 2003, 2, 9999.00, 9999.00, 'SUCCESS',       datetime('now','localtime','start of day','+11 hours'), datetime('now','localtime','start of day','+11 hours')),
('SO-X2-004', 2004, 2, 9999.00, 9999.00, 'PENDING_SHIP',  datetime('now','localtime','start of day','+12 hours'), datetime('now','localtime','start of day','+12 hours')),
('SO-X2-005', 2005, 2, 9999.00, 9999.00, 'PENDING_RECEIVE',datetime('now','localtime','start of day','+13 hours'),datetime('now','localtime','start of day','+13 hours')),
('SO-X2-006', 2006, 2, 9999.00, 9999.00, 'SUCCESS',       datetime('now','localtime','start of day','-1 day','+9 hours'),  datetime('now','localtime','start of day','-1 day','+9 hours')),
('SO-X2-007', 2007, 2, 9999.00, 9999.00, 'SUCCESS',       datetime('now','localtime','start of day','-1 day','+10 hours'), datetime('now','localtime','start of day','-1 day','+10 hours')),
('SO-X2-008', 2008, 2, 9999.00, 9999.00, 'SUCCESS',       datetime('now','localtime','start of day','-1 day','+11 hours'), datetime('now','localtime','start of day','-1 day','+11 hours')),
('SO-X2-009', 2009, 2, 9999.00, 9999.00, 'PENDING_PAY',   datetime('now','localtime','start of day','-1 day','+12 hours'), NULL),
('SO-X2-010', 2010, 2, 9999.00, 9999.00, 'REFUNDING',     datetime('now','localtime','start of day','-1 day','+13 hours'), datetime('now','localtime','start of day','-1 day','+13 hours')),
('SO-X2-011', 2011, 2, 9999.00, 9999.00, 'PENDING_PAY',   datetime('now','localtime','start of day','-2 day','+9 hours'),  NULL);

-- ============================================================
-- ⑤ 订单明细（只给几张代表性的单，够验「共 N 件 = SUM(quantity)」）
-- ============================================================
INSERT INTO tb_order_item (order_id, product_id, product_name, product_image, price, quantity, subtotal)
SELECT id, 1, '智能手机 Pro', '/img/p1.png', 128.00, 1, 128.00 FROM tb_order WHERE order_no = 'SO-D0-001';
INSERT INTO tb_order_item (order_id, product_id, product_name, product_image, price, quantity, subtotal)
SELECT id, 2, '无线耳机',     '/img/p2.png', 178.25, 2, 356.50 FROM tb_order WHERE order_no = 'SO-D0-002';
INSERT INTO tb_order_item (order_id, product_id, product_name, product_image, price, quantity, subtotal)
SELECT id, 7, '电子卡券·洗车','/img/p7.png',  88.50, 1,  88.50 FROM tb_order WHERE order_no = 'SO-D0-009';

-- ============================================================
-- ⑥ 消息：shop 1 三条（三种类型齐活）/ shop 2 五条干扰
--    按 create_time 倒序期望：18:00(退款) → 今天 09:15(支付) → 昨天 13:00(收货)
-- ============================================================
INSERT INTO tb_message (shop_id, type, order_no, content, is_read, create_time) VALUES
(1, 'pay_success',     'SO-D0-001', '用户 张三 已支付成功，订单号 SO-D0-001，请及时处理',   0, datetime('now','localtime','start of day','+9 hours','+15 minutes')),
(1, 'confirm_receive', 'SO-D1-004', '订单 SO-D1-004 用户 王五 已经确认收货',                 1, datetime('now','localtime','start of day','-1 day','+13 hours')),
(1, 'refund_apply',    'SO-D0-009', '有买家发起退款，订单编号 SO-D0-009，请在 7 天内处理',   0, datetime('now','localtime','start of day','+18 hours')),
-- shop 2 的干扰消息：order_no 也带上，但 shop_id 不同，绝不该出现在 shop 1 结果里
(2, 'pay_success',     'SO-X2-001', '用户 隔壁甲 已支付成功，订单号 SO-X2-001，请及时处理',  0, datetime('now','localtime','start of day','+9 hours','+30 minutes')),
(2, 'pay_success',     'SO-X2-002', '用户 隔壁乙 已支付成功，订单号 SO-X2-002，请及时处理',  0, datetime('now','localtime','start of day','+10 hours')),
(2, 'refund_apply',    'SO-X2-010', '有买家发起退款，订单编号 SO-X2-010，请在 7 天内处理',   0, datetime('now','localtime','start of day','+11 hours')),
(2, 'confirm_receive', 'SO-X2-003', '订单 SO-X2-003 用户 隔壁丙 已经确认收货',               1, datetime('now','localtime','start of day','+12 hours')),
(2, 'pay_success',     'SO-X2-006', '用户 隔壁丁 已支付成功，订单号 SO-X2-006，请及时处理',  0, datetime('now','localtime','start of day','-1 day','+9 hours'));
