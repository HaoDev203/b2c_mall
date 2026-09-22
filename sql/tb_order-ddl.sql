-- ============================================================
-- 阶段 4 · 订单 + 状态机 建表脚本（SQLite）
-- 依据：项目需求拆解与实施步骤.md  Step 4.1 / Step 4.3
-- 说明：可重复执行。测试订单每次重建，方便反复练发货。
-- ============================================================

-- ---------- ① 订单主表 ----------
CREATE TABLE IF NOT EXISTS tb_order (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  order_no          TEXT UNIQUE,          -- 订单编号，如 202609180001
  user_id           INTEGER,              -- 买家
  shop_id           INTEGER,              -- ★ 归属店铺：多店铺隔离 + 防越权（和 tb_messages/tb_product 一个思路）
  total_amount      REAL,                 -- 订单总额
  pay_amount        REAL,                 -- 实付金额
  status            TEXT,                 -- ★ 状态机的"当前状态"字段（PENDING_PAY 等 6 个英文码）
  pay_type          TEXT,                 -- alipay / wechat / paypal / other（阶段 6 策略模式用）
  receiver_name     TEXT,
  receiver_phone    TEXT,
  receiver_address  TEXT,
  shipping_status   TEXT,                 -- 物流状态（本项目简化为与 status 同步）
  after_sale_status TEXT,                 -- 售后状态
  remark            TEXT,                 -- 订单备注
  create_time       TEXT,
  pay_time          TEXT,
  ship_time         TEXT
);

-- ---------- ② 订单明细 ----------
CREATE TABLE IF NOT EXISTS tb_order_item (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id      INTEGER,
  product_id    INTEGER,
  product_name  TEXT,                     -- ★ 冗余存的"下单时快照"
  product_image TEXT,                     --   商品改名/改价不影响历史订单
  price         REAL,
  quantity      INTEGER,
  subtotal      REAL
);

-- ---------- ③ 状态流转留痕 ----------
CREATE TABLE IF NOT EXISTS tb_order_status_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id    INTEGER,
  from_status TEXT,
  to_status   TEXT,
  event       TEXT,                       -- ★ 记下是哪个"事件"触发的流转
  operator    TEXT,                       -- 操作人（员工#1 / system）
  remark      TEXT,
  create_time TEXT
);

-- ---------- ④ ★★ 状态机规则表（本阶段的核心）----------
CREATE TABLE IF NOT EXISTS tb_state_machine (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  biz_type    TEXT,                       -- 业务类型：order（以后别的业务也能共用这张表）
  from_status TEXT,                       -- 当前状态
  event       TEXT,                       -- 收到的事件
  to_status   TEXT,                       -- 允许流转到的目标状态
  remark      TEXT
);

-- ---------- ⑤ 灌规则（★ 这是"状态机"，不是 if-else 的关键）----------
DELETE FROM tb_state_machine;

INSERT INTO tb_state_machine (biz_type, from_status, event, to_status, remark) VALUES
  ('order', 'PENDING_PAY',     'PAY_SUCCESS',    'PENDING_SHIP',    '支付成功 → 待发货'),
  ('order', 'PENDING_PAY',     'CANCEL_TIMEOUT', 'FAILED',          '超时未支付 → 交易失败'),
  ('order', 'PENDING_SHIP',    'SHIP',           'PENDING_RECEIVE', '后台点击发货 → 待收货'),
  ('order', 'PENDING_RECEIVE', 'CONFIRM',        'SUCCESS',         '确认收货 → 交易成功'),
  ('order', 'PENDING_PAY',     'APPLY_REFUND',   'REFUNDING',       '待付款申请售后 → 待退款'),
  ('order', 'PENDING_SHIP',    'APPLY_REFUND',   'REFUNDING',       '待发货申请售后 → 待退款'),
  ('order', 'PENDING_RECEIVE', 'APPLY_REFUND',   'REFUNDING',       '待收货申请售后 → 待退款'),
  ('order', 'SUCCESS',         'APPLY_REFUND',   'REFUNDING',       '交易成功后仍可申请售后');

-- ---------- ⑥ 灌 2 笔测试订单（每次重建，方便反复练）----------
DELETE FROM tb_order_status_log;
DELETE FROM tb_order_item;
DELETE FROM tb_order;

-- 订单 1：待发货 —— 用来测「发货成功」
INSERT INTO tb_order (id, order_no, user_id, shop_id, total_amount, pay_amount, status, pay_type,
                      receiver_name, receiver_phone, receiver_address, shipping_status,
                      create_time, pay_time)
VALUES (1, '202609180001', 1001, 1, 5999.00, 5999.00, 'PENDING_SHIP', 'alipay',
        '张三', '13800138000', '广东省深圳市南山区科技园1号', '未发货',
        '2026-09-18 10:00:00', '2026-09-18 10:01:00');

-- 订单 2：待付款 —— 用来测「发货被拒绝」
INSERT INTO tb_order (id, order_no, user_id, shop_id, total_amount, pay_amount, status, pay_type,
                      receiver_name, receiver_phone, receiver_address, shipping_status,
                      create_time)
VALUES (2, '202609180002', 1002, 1, 199.00, 0.00, 'PENDING_PAY', NULL,
        '李四', '13900139000', '北京市海淀区中关村大街1号', '未发货',
        '2026-09-18 11:00:00');

INSERT INTO tb_order_item (order_id, product_id, product_name, product_image, price, quantity, subtotal) VALUES
  (1, 1, '智能手机 Pro', '/img/phone.png', 5999.00, 1, 5999.00),
  (2, 1, '智能手机 Pro', '/img/phone.png',  199.00, 1,  199.00);

-- ---------- ⑦ 自检 ----------
SELECT '订单数（应为 2）' AS item, COUNT(*) AS v FROM tb_order
UNION ALL SELECT '明细数（应为 2）', COUNT(*) FROM tb_order_item
UNION ALL SELECT '规则数（应为 8）', COUNT(*) FROM tb_state_machine;
