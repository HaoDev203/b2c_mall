-- ============================================================
-- mall-tdd / mall.db 建表脚本（SQLite）
-- 依据：《项目需求v.1.1》+ .tdd/task/需求拆解-00-总览.md §六
--
-- 与 shop/shop.db 完全独立：shop 是设计模式作业的演示库，本模块一行不碰。
-- 可重复执行（全部 IF NOT EXISTS），不含任何演示数据。
--
-- 执行方式见 mall-tdd/README.md：
--   python -c "import sqlite3;c=sqlite3.connect('mall.db');c.executescript(open('sql/mall-ddl.sql',encoding='utf-8').read());c.commit()"
-- ============================================================

-- ---------- ① 店铺 ----------
-- 存在的意义：三个 TO B 页面都要验「只看本店数据」，隔离维度就是这张表的 id。
CREATE TABLE IF NOT EXISTS tb_shop (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT,
  logo        TEXT,
  phone       TEXT,
  address     TEXT,
  status      TEXT,                 -- normal 正常 / disabled 停用
  create_time TEXT
);

-- ---------- ② 分类 ----------
-- ★ C 端导航要求「分类名去重」：库里允许同名不同 id 的行，
--   去重必须在 Service 层按 name 保留首条（LinkedHashMap），
--   SQL 的 DISTINCT * 去不掉（id 不同）。
CREATE TABLE IF NOT EXISTS tb_category (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  parent_id   INTEGER DEFAULT 0,    -- 父类目，顶级为 0
  name        TEXT,
  level       INTEGER DEFAULT 1,    -- 1/2/3 级
  type        TEXT,                 -- platform 平台 / shop 店铺
  sort        INTEGER DEFAULT 0
);

-- ---------- ③ 商品 ----------
CREATE TABLE IF NOT EXISTS tb_product (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  shop_id         INTEGER,          -- ★ 后台隔离维度
  name            TEXT,
  keyword         TEXT,
  selling_point   TEXT,
  category_id     INTEGER,
  main_image      TEXT,
  price           REAL,             -- 销售价
  market_price    REAL,             -- 市场价（前端划线展示，后端只给数值）
  stock           INTEGER,          -- ★ 总库存：库存预警与 C 端可售量的口径（stock <= stock_warn）
  warehouse_stock INTEGER,          -- 仓库库存：后台列表单独一列，不与门店库存合并
  store_stock     INTEGER,          -- 门店库存：同上
  stock_warn      INTEGER,          -- ★ 预警阈值。NULL 表示该商品不参与预警（有意为之，见拆解 §四）
  sales           INTEGER DEFAULT 0,  -- 真实销量
  inflated_sales  INTEGER DEFAULT 0,  -- ★ 注水销量：与真实销量分开返回，绝不合并
  goods_type      TEXT,             -- ★ 商品类型：normal 普通 / seckill 秒杀
                                    --   不叫 product_type —— shop 里那个名字装的是「类别」，同名必坑
  category_kind   TEXT,             -- ★ 商品类别：physical 实物 / virtual 虚拟 / card 卡券
  delivery_types  TEXT,             -- ★ 配送方式多选，逗号分隔：express,self_pickup,virtual
                                    --   筛选用 IN 拆开匹配，不是 LIKE
  product_code    TEXT,             -- 商品编码：导入 Excel 时用它判定「新增 or 更新」
  status          TEXT,             -- draft 草稿 / on_sale 上架 / off_sale 下架
  sort            INTEGER DEFAULT 0,  -- 排序：数字越大越靠前
  create_time     TEXT
);

-- ---------- ④ 订单主表 ----------
CREATE TABLE IF NOT EXISTS tb_order (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  order_no          TEXT UNIQUE,    -- ★ 订单编号：后台查询要求精确匹配，不是 LIKE
  user_id           INTEGER,        -- ★ C 端隔离维度
  shop_id           INTEGER,        -- ★ 后台隔离维度
  total_amount      REAL,
  pay_amount        REAL,           -- 实付金额
  status            TEXT,           -- ★ 7 个状态：
                                    --   PENDING_PAY / PENDING_SHIP / PENDING_RECEIVE / PENDING_GROUP
                                    --   / SUCCESS / FAILED / REFUNDING
                                    --   ↑ PENDING_GROUP（待成团）是 shop 缺的那个
  pay_type          TEXT,           -- alipay / wechat / paypal / other
  order_type        TEXT,           -- ★ 订单类型（与下面那个是两个不同维度）
  order_category    TEXT,           -- ★ 订单类别：normal 普通 / e_card 电子卡券
  logistics_type    TEXT,           -- ★ 物流类型：express 普通快递 / city 同城配送
  store_name        TEXT,           -- ★ 门店名称（多门店筛选）
  tracking_no       TEXT,           -- ★ 物流单号：发货时写入，发货前为 NULL
  receiver_name     TEXT,
  receiver_phone    TEXT,           -- ★ 出接口前必须脱敏，见 O16
  receiver_address  TEXT,
  shipping_status   TEXT,
  after_sale_status TEXT,           -- none 暂无 / applying 申请中 / done 已完成
  is_deleted        INTEGER DEFAULT 0,  -- ★ 买家软删除：1 = 买家侧隐藏，后台仍可见
  remark            TEXT,
  create_time       TEXT,
  pay_time          TEXT,           -- ★ 「已支付」的唯一口径：pay_time IS NOT NULL（不看 status）
  ship_time         TEXT
);

-- ---------- ⑤ 订单明细 ----------
CREATE TABLE IF NOT EXISTS tb_order_item (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id      INTEGER,
  product_id    INTEGER,
  product_name  TEXT,               -- 下单时快照：商品改名/改价不影响历史订单
  product_image TEXT,
  price         REAL,
  quantity      INTEGER,            -- ★ 「共 N 件」= 本表 quantity 之和，不是行数
  subtotal      REAL
);

-- ---------- ⑥ 消息通知 ----------
CREATE TABLE IF NOT EXISTS tb_message (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  shop_id     INTEGER,              -- ★ 后台消息隔离维度
  type        TEXT,                 -- pay_success 支付成功 / refund_apply 退款申请 / confirm_receive 确认收货
  order_no    TEXT,                 -- ★ 每条消息携带订单编号，点击可跳详情
  content     TEXT,
  is_read     INTEGER DEFAULT 0,
  create_time TEXT                  -- 消息按此倒序返回，最新在最上
);

-- ---------- ⑦ 索引 ----------
-- 只建 88 条里真的会命中的：三个页面的隔离维度 + 趋势图的时间维度
CREATE INDEX IF NOT EXISTS idx_product_shop      ON tb_product (shop_id);
CREATE INDEX IF NOT EXISTS idx_order_shop        ON tb_order (shop_id);
CREATE INDEX IF NOT EXISTS idx_order_user        ON tb_order (user_id);
CREATE INDEX IF NOT EXISTS idx_order_pay_time    ON tb_order (pay_time);
CREATE INDEX IF NOT EXISTS idx_order_item_order  ON tb_order_item (order_id);
CREATE INDEX IF NOT EXISTS idx_message_shop      ON tb_message (shop_id);
