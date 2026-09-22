-- ============================================================
-- 阶段 6：购物车 + 满减促销规则
-- 说明：全新表，不改任何已有表结构（商品库存字段阶段 3 已有）
-- 本脚本可重复执行（CREATE IF NOT EXISTS + INSERT 前先清同名）
-- ============================================================

-- ① 购物车
CREATE TABLE IF NOT EXISTS tb_cart (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL,          -- 买家（tb_user.id）
  product_id  INTEGER NOT NULL,
  quantity    INTEGER NOT NULL DEFAULT 1,
  create_time TEXT,
  update_time TEXT,
  UNIQUE(user_id, product_id)            -- ★ 同一买家同一商品只留一行，重复加购改为累加数量
);

-- ② 促销规则表（需求 Step 6.1 说"满减可放配置表"）
--    设计成"通用阶梯满减"：以后加「满 3000 减 400」只需 INSERT 一行
CREATE TABLE IF NOT EXISTS tb_promotion (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  shop_id      INTEGER,                  -- 归属店铺（0 = 全平台通用）
  name         TEXT,
  threshold    REAL,                     -- 门槛金额
  discount     REAL,                     -- 减免金额
  status       INTEGER DEFAULT 1,        -- 1-启用 0-停用
  sort         INTEGER DEFAULT 0
);

-- ③ 灌促销规则：原型图 10 的「满 ¥1200 已优惠 ¥120」
--    先删同名再插，保证脚本重复执行不会灌出两条一样的规则
DELETE FROM tb_promotion WHERE name = '满 1200 减 120';
INSERT INTO tb_promotion(shop_id, name, threshold, discount, status, sort)
VALUES (0, '满 1200 减 120', 1200.00, 120.00, 1, 10);
