package com.mall.tdd.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读 .xlsx：第 0 行当表头，其余行按<b>表头名</b>映射成 {@code Map<列名, 文本>}。
 *
 * <p>★ 按表头名映射、不按列序号：商家在模板里插一列、调一下顺序，
 * 按序号读的实现会把「销售价」当「市场价」写进去，而且一条报错都不会有 ——
 * 这是最难事后发现的一类数据污染。
 *
 * <p>★ 所有值都转成文本再用 {@link DataFormatter}：单元格可能是数值、公式、
 * 文本混杂，直接 {@code getStringCellValue()} 遇到数值单元格会抛异常。
 *
 * <p>★ 空行整行跳过：Excel 里点一下「删除行」残留的空行很常见，
 * 当成一条数据会变成「商品名为空、编码为空」的垃圾记录。
 */
public final class XlsxReader {

    private XlsxReader() {
    }

    public static List<Map<String, String>> read(InputStream in) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                return Collections.emptyList();
            }

            Row head = sheet.getRow(0);
            if (head == null) {
                return Collections.emptyList();
            }

            DataFormatter formatter = new DataFormatter();
            int width = head.getLastCellNum();

            // 表头名 → 列序号。空表头跳过；重复表头保留第一个（后者多半是空白列）
            Map<String, Integer> colIndex = new LinkedHashMap<String, Integer>();
            for (int c = 0; c < width; c++) {
                String name = textOf(head.getCell(c), formatter);
                if (name == null || name.isEmpty() || colIndex.containsKey(name)) {
                    continue;
                }
                colIndex.put(name, Integer.valueOf(c));
            }

            List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row line = sheet.getRow(r);
                if (line == null) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<String, String>();
                boolean blank = true;
                for (Map.Entry<String, Integer> e : colIndex.entrySet()) {
                    String value = textOf(line.getCell(e.getValue().intValue()), formatter);
                    if (value != null && !value.isEmpty()) {
                        blank = false;
                    }
                    row.put(e.getKey(), value);
                }
                if (blank) {
                    continue;
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("Excel 解析失败", e);
        }
    }

    private static String textOf(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        String text = formatter.formatCellValue(cell);
        return text == null ? null : text.trim();
    }
}
