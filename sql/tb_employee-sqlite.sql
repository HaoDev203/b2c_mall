-- ============================================================
-- tb_employee 建表脚本 —— SQLite 版（项目当前用的就是 SQLite）
-- 用法：在 SQLite 客户端里打开 D:/demo/commerce platform/shop/shop.db 后执行
-- ============================================================

CREATE TABLE IF NOT EXISTS tb_employee (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,   -- 注意：SQLite 是 AUTOINCREMENT（无下划线）
    shop_id         INTEGER      NOT NULL,               -- 所属店铺
    username        VARCHAR(50)  NOT NULL,               -- 账号
    password        VARCHAR(255) NOT NULL,               -- 密码哈希
    avatar_url      VARCHAR(500) DEFAULT '/avatars/default.png',  -- 头像地址
    last_login_time DATETIME,                            -- 最后登录时间
    login_count     INTEGER      DEFAULT 0,              -- 累计登录次数
    status          TINYINT      DEFAULT 1,              -- 状态：1-正常 0-禁用
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP
);

-- 同一店铺下账号不能重复（SQLite 用独立语句建索引）
CREATE UNIQUE INDEX IF NOT EXISTS uk_employee_shop_username
    ON tb_employee (shop_id, username);

-- 常用查询索引
CREATE INDEX IF NOT EXISTS idx_employee_shop_id ON tb_employee (shop_id);


-- ------------------------------------------------------------
-- 验证：插一条再查出来，确认自增和时间默认值都生效
-- ------------------------------------------------------------
-- INSERT INTO tb_employee (shop_id, username, password) VALUES (1, 'admin', 'x');
-- SELECT id, username, status, created_at FROM tb_employee;
-- DELETE FROM tb_employee WHERE username = 'admin';   -- 验证完清掉
