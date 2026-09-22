-- ============================================================
-- tb_employee 建表脚本 —— MySQL 版
-- 只有当你的连接确实是 MySQL（不是 SQLite）时才用这个
-- 本项目的 application.yml 配的是 jdbc:sqlite:，不需要这个文件
-- ============================================================

CREATE TABLE IF NOT EXISTS tb_employee (
    id              INT          NOT NULL AUTO_INCREMENT,  -- MySQL 是 AUTO_INCREMENT（带下划线）
    shop_id         INT          NOT NULL,                 -- 所属店铺
    username        VARCHAR(50)  NOT NULL,                 -- 账号
    password        VARCHAR(255) NOT NULL,                 -- 密码哈希
    avatar_url      VARCHAR(500) DEFAULT '/avatars/default.png',  -- 头像地址
    last_login_time DATETIME     NULL,                     -- 最后登录时间
    login_count     INT          DEFAULT 0,                -- 累计登录次数
    status          TINYINT      DEFAULT 1,                -- 状态：1-正常 0-禁用
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_shop_username (shop_id, username),
    KEY idx_employee_shop_id (shop_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
