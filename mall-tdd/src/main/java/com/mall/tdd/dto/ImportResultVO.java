package com.mall.tdd.dto;

import lombok.Data;

/**
 * 导入结果：{@code total = insert + update + failed}。
 *
 * <p>★ 四个数都在，不是只回一句「导入成功」：
 * 商家上传 500 行，只想知道「新增多少、改了多少、失败多少」。
 * 只回一个总数，失败了也不知道该去修哪几行。
 *
 * <p>★ {@code total} 是 <b>数据行数</b>（不含表头），失败的行也计入 ——
 * 否则「上传 3 行、结果说 2 行」会让人怀疑文件没读全。
 */
@Data
public class ImportResultVO {

    /** 数据行数，不含表头 */
    private Integer total;
    /** 按商品编码未命中 → 新增 */
    private Integer insert;
    /** 按商品编码命中 → 更新 */
    private Integer update;
    /** 商品编码为空 / 该行解析或写入失败 */
    private Integer failed;
}
