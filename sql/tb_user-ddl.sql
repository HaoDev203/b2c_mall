-- ============================================================
-- 阶段 5：前台买家表 + 商品销量字段 + 登录日志用户类型
-- 说明：全部是「新增」，不动任何已有数据
-- ============================================================

-- ① 前台买家表
CREATE TABLE IF NOT EXISTS tb_user (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  username        VARCHAR(50)  NOT NULL UNIQUE,
  password        VARCHAR(255) NOT NULL,
  nickname        VARCHAR(50),
  phone           VARCHAR(20),
  avatar_url      VARCHAR(500),
  last_login_time DATETIME,
  login_count     INTEGER DEFAULT 0,
  status          TINYINT DEFAULT 1,
  created_at      DATETIME,
  updated_at      DATETIME
);

-- ② 商品加销量字段
--    sales      = 真实销量（下单支付成功后累加，阶段 6 才会有变化）
--    fake_sales = 注水销量（原型图 6 的商品管理列表里有这一列，运营用来美化数据）
ALTER TABLE tb_product ADD COLUMN sales      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tb_product ADD COLUMN fake_sales INTEGER NOT NULL DEFAULT 0;

-- ③ 登录日志加"用户类型"列
--    已有 25 条历史数据都是后台登录，DEFAULT 'admin' 正好对上
ALTER TABLE tb_login_logs ADD COLUMN user_type VARCHAR(20) DEFAULT 'admin';

-- ④ 给现有商品补销量 + 加几条测试商品，让前台列表有东西可看
UPDATE tb_product SET sales = 328,  fake_sales = 400 WHERE id = 1;
UPDATE tb_product SET sales = 1204, fake_sales = 900 WHERE id = 2;

INSERT INTO tb_product(shop_id, name, keyword, selling_point, category_id, product_type,
                       main_image, price, market_price, stock, sales, fake_sales,
                       status, sort, create_time)
VALUES
 (1, '联想拯救者 Y9000P', '游戏本 笔记本', 'RTX4060 独显',      3, 'physical', '/img/p3.jpg', 8299.00, 8999.00, 50,  86,  100, 'on_sale', 90, '2026-09-18 10:00:00'),
 (1, '机械键盘 K8 Pro',   '键盘 机械',     '三模热插拔',          3, 'physical', '/img/p4.jpg',  499.00,  599.00, 200, 512, 600, 'on_sale', 80, '2026-09-18 10:05:00'),
 (1, '游戏点卡 100 元',   '点卡 充值',     '秒发货',              5, 'virtual',  '/img/p5.jpg',  100.00,  100.00, 999, 3280,3300, 'on_sale', 70, '2026-09-18 10:10:00'),
 (1, '纯棉圆领 T 恤',     'T恤 短袖',      '夏季新款',            6, 'physical', '/img/p6.jpg',   89.00,  129.00, 500, 760, 800, 'on_sale', 60, '2026-09-18 10:15:00'),
 (1, '已下架测试商品',    '下架',          '不该出现在前台',      2, 'physical', '/img/p7.jpg',   50.00,   60.00, 10,   5,   0, 'off_sale', 10, '2026-09-18 10:20:00');
