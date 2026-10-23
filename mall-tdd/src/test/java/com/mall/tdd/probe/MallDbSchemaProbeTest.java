package com.mall.tdd.probe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B0 探针：验证 mall.db 真的按需求拆解 §六 建好了。
 *
 * <p>为什么要有这条：Mock 只能验证「Service 调了 Mapper 什么方法」，
 * 验证不了「库里到底有没有 goods_type 这一列」。88 条里有大量筛选/聚合断言
 * 都建立在这些字段之上，字段一错，后面全绿也是假的。
 *
 * <p>刻意连**真库**而不是内存库：探针的目的就是检查磁盘上那个库。
 * 全程只读（查 sqlite_master / PRAGMA），不写任何数据。
 */
@DisplayName("B0 探针：mall.db 结构与需求字段")
class MallDbSchemaProbeTest {

    /** 与 application.yml 的 spring.datasource.url 保持同一份库 */
    private static final String DB_PATH = "D:/demo/commerce platform/mall-tdd/mall.db";

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    private Set<String> columnsOf(String table) throws Exception {
        Set<String> cols = new LinkedHashSet<String>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cols.add(rs.getString("name"));
            }
        }
        return cols;
    }

    @Test
    @DisplayName("应存在 6 张业务表")
    void 应存在六张业务表() throws Exception {
        List<String> actual = new ArrayList<String>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'tb_%' ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                actual.add(rs.getString(1));
            }
        }

        assertEquals(
                Arrays.asList("tb_category", "tb_message", "tb_order", "tb_order_item", "tb_product", "tb_shop"),
                actual,
                "mall.db 的业务表清单与需求拆解 §六 不一致，实际：" + actual);
    }

    @Test
    @DisplayName("tb_product 应含需求要而 shop 缺的 6 个字段")
    void 商品表应含需求新增字段() throws Exception {
        Set<String> cols = columnsOf("tb_product");
        List<String> required = Arrays.asList(
                "goods_type",       // 商品类型：普通/秒杀（★ 不叫 product_type，那个名字在 shop 里装的是「类别」）
                "category_kind",    // 商品类别：实物/虚拟/卡券
                "warehouse_stock",  // 仓库库存，与门店库存分开返回
                "store_stock",      // 门店库存
                "inflated_sales",   // 注水销量，与真实销量分开返回、不合并
                "delivery_types"    // 配送方式多选，逗号分隔，筛选用 IN
        );

        for (String col : required) {
            assertTrue(cols.contains(col),
                    "tb_product 缺少字段 " + col + "，实际列：" + cols);
        }
    }

    @Test
    @DisplayName("tb_order 应含需求要而 shop 缺的 6 个字段")
    void 订单表应含需求新增字段() throws Exception {
        Set<String> cols = columnsOf("tb_order");
        List<String> required = Arrays.asList(
                "order_type",       // 订单类型
                "order_category",   // 订单类别：普通 / 电子卡券（与订单类型是两个维度）
                "logistics_type",   // 物流类型：普通快递 / 同城配送
                "store_name",       // 门店名称
                "tracking_no",      // 物流单号，发货时写入
                "is_deleted"        // 买家软删除标记，默认 0
        );

        for (String col : required) {
            assertTrue(cols.contains(col),
                    "tb_order 缺少字段 " + col + "，实际列：" + cols);
        }
    }
}
