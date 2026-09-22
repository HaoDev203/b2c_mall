-- ============================================================
-- 阶段 3 · 商品管理 建表脚本（SQLite）
-- 依据：项目需求拆解与实施步骤.md  Step 3.1
-- 说明：可重复执行。类目是固定测试数据，每次会重建；
--       tb_product 用 CREATE TABLE IF NOT EXISTS，不会清掉你已发布的商品。
-- ============================================================

-- ---------- ① 类目表 ----------
CREATE TABLE IF NOT EXISTS tb_category (        -- 类目，支持多级
  id        INTEGER PRIMARY KEY AUTOINCREMENT,  -- ★ SQLite 就是 AUTOINCREMENT（不是 MySQL 的 AUTO_INCREMENT）
  parent_id INTEGER,                            -- 父类目 id，顶级为 0
  name      TEXT,
  level     INTEGER,                            -- 1/2/3 级
  type      TEXT                                -- platform(平台) / shop(店铺)
);

-- ---------- ② 商品表 ----------
CREATE TABLE IF NOT EXISTS tb_product (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  shop_id          INTEGER,   -- ★ 我补的字段：商品属于哪个店铺（多店铺隔离 + 防越权，和 tb_messages 一个思路）
  name             TEXT,
  keyword          TEXT,      -- 搜索关键词
  selling_point    TEXT,      -- 卖点
  category_id      INTEGER,   -- 平台类目
  shop_category_id INTEGER,   -- 店铺自定义类目（本项目暂无 tb_shop_category 表，暂不校验）
  product_type     TEXT,      -- physical实物 / virtual虚拟 / combo组合 / card电子卡券
  main_image       TEXT,
  video            TEXT,
  brand            TEXT,
  price            REAL,      -- 销售价
  market_price     REAL,      -- 市场价
  stock            INTEGER,   -- 库存（虚拟商品可为 0）
  stock_warn       INTEGER,   -- 库存预警值
  product_code     TEXT,      -- 商品编码 / 虚拟商品的卡券编号、资质编号
  valid_end_time   TEXT,      -- ★ 我补的字段：虚拟商品 / 卡券的有效期（yyyy-MM-dd），合规校验要用
  status           TEXT,      -- draft草稿 / on_sale已上架 / off_sale已下架
  sort             INTEGER,   -- 排序
  create_time      TEXT
);

-- ---------- ③ 类目测试数据（可重复执行）----------
DELETE FROM tb_category;

INSERT INTO tb_category (id, parent_id, name, level, type) VALUES
  (1, 0, '数码电器', 1, 'platform'),
  (2, 1, '手机',     2, 'platform'),
  (3, 1, '电脑办公', 2, 'platform'),
  (4, 0, '虚拟服务', 1, 'platform'),
  (5, 4, '电子卡券', 2, 'platform'),
  (6, 0, '服饰鞋包', 1, 'platform');

-- ---------- ④ 自检 ----------
SELECT '类目数（应为 6）' AS item, COUNT(*) AS v FROM tb_category
UNION ALL
SELECT '商品数', COUNT(*) FROM tb_product;
