package com.mall.tdd.controller;

import com.mall.tdd.common.DownloadResponse;
import com.mall.tdd.common.Result;
import com.mall.tdd.dto.BatchResultVO;
import com.mall.tdd.dto.ImportResultVO;
import com.mall.tdd.dto.ProductBatchCommand;
import com.mall.tdd.dto.ProductPageVO;
import com.mall.tdd.dto.ProductQuery;
import com.mall.tdd.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * B 端（商家侧）商品管理：列表 / 删除 / 批量设置 / 导出 / 导入。5 个接口。
 *
 * <p>★ shopId 由 {@code UserContext} 提供，签名里没有这个参数（W04 反射守着）。
 */
@RestController
@RequestMapping("/api/shop/product")
@RequiredArgsConstructor
public class ShopProductController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductQueryService productQueryService;

    /**
     * 后台商品列表。
     *
     * <p>{@link ProductQuery} 用对象接而不是铺开 6 个 {@code @RequestParam}：
     * 列表和导出用的是同一组筛选条件，铺开写两遍迟早「列表筛了、导出没跟」。
     */
    @GetMapping("/list")
    public Result<ProductPageVO> list(
            ProductQuery query,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return productQueryService.listProducts(query, page, size);
    }

    /** 删除商品。Service 里三步顺序不能反：确认本店 → 查有订单则拒删 → 才删 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return productQueryService.deleteProduct(id);
    }

    /**
     * 批量设置（状态 / 配送方式 / 分类 / 排序 / 两个库存字段）。
     *
     * <p>不加 {@code @Transactional}：逐条独立成败并返回明细，
     * 整体回滚等于商家点一次什么都没发生，还不知道是哪条的问题。
     */
    @PostMapping("/batch")
    public Result<BatchResultVO> batch(@RequestBody(required = false) ProductBatchCommand cmd) {
        if (cmd == null) {
            return Result.fail("缺少批量设置参数");
        }
        return productQueryService.batchUpdate(cmd);
    }

    /**
     * 导出 Excel。
     *
     * <p>必须经 {@link DownloadResponse} 包装：直接返回 {@code Result<byte[]>}
     * 会被序列化成 base64 的 JSON，浏览器不下载，Excel 也打不开。
     */
    @GetMapping("/export")
    public ResponseEntity<?> export(ProductQuery query) {
        String filename = "products_" + LocalDate.now().format(DAY) + ".xlsx";
        return DownloadResponse.attachment(productQueryService.exportProducts(query), filename);
    }

    /**
     * 导入 Excel（按 product_code 做 upsert）。
     *
     * <p>★ 没传文件时给可读提示，而不是让 {@code file.getInputStream()} 抛 NPE 变成 500 ——
     * 商家只是忘了选文件，不该看到「系统错误」。
     */
    @PostMapping("/import")
    public Result<ImportResultVO> importExcel(
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要导入的 Excel 文件");
        }
        try (InputStream in = file.getInputStream()) {
            return productQueryService.importProducts(in);
        }
    }
}
