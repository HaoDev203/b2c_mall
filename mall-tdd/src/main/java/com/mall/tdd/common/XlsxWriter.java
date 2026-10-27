package com.mall.tdd.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 把「表头 + 二维数据」写成 .xlsx 的字节流。
 *
 * <p>★ 返回 {@code byte[]} 而不是 {@code Workbook}：Service 层不碰 POI 的生命周期，
 * 也就不存在「谁负责 close」的问题。Controller 把这段字节塞进响应体即可。
 *
 * <p>★ 数值写成 <b>数值单元格</b>不是字符串：商家导出后要能直接在 Excel 里求和、
 * 排序、做透视。全写成文本看着一样，一求和就是 0。
 *
 * <p>★ 样式只做两件必要的事（表头加粗、冻结首行），不做配色/边框：
 * 需求没要求，且样式无法用单元测试可靠断言，写了也只是无人守护的代码。
 */
public final class XlsxWriter {

    private XlsxWriter() {
    }

    /**
     * @param sheetName 工作表名
     * @param headers   表头，顺序即列顺序
     * @param rows      数据行；单元格为 {@code null} 时留空
     */
    public static byte[] write(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(sheetName);

            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);

            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headStyle);
            }

            for (int r = 0; r < rows.size(); r++) {
                Row line = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    setValue(line.createCell(c), values.get(c));
                }
            }

            // 导出几百行时首行不跟着滚走，否则商家看到第 50 行就不知道哪列是哪列
            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // 写内存流理论上不会失败（磁盘满之类才会）。抛运行时异常让上层看到 500，
            // 不要吞掉后返回一个 0 字节文件 —— 那样前端会下载到一个打不开的 .xlsx。
            throw new IllegalStateException("生成 Excel 失败", e);
        }
    }

    private static void setValue(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue(((Boolean) value).booleanValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
}
