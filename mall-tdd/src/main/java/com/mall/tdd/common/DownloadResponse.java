package com.mall.tdd.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 把 {@code Result<byte[]>} 转成「浏览器会下载的文件」。
 *
 * <p>如果不经这一步直接把 {@code Result<byte[]>} 返回给 Spring，它会被当成普通对象
 * 序列化成 {@code {"code":200,"data":"UEsDBBQ..."}} —— 一大坨 base64 的 JSON，
 * 浏览器既不弹下载，Excel 也打不开。
 *
 * <p>★★ 失败时必须返回 JSON 而<b>不是空文件</b>：Service 返回
 * {@code Result.fail(...)} 时 {@code data} 是 null，直接写出就是 0 字节的 .xlsx，
 * 用户下载后用 Excel 打开提示「文件已损坏」，而日志里一条错误都没有 ——
 * 因为根本没抛异常，是最难查的那类问题。
 */
public final class DownloadResponse {

    /** Excel 的 MIME。写成 {@code application/octet-stream} 也能下载，但 Excel 双击打开会先弹「是否信任」 */
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private DownloadResponse() {
    }

    public static ResponseEntity<?> attachment(Result<byte[]> result, String filename) {
        boolean success = result != null
                && Integer.valueOf(200).equals(result.getCode())
                && result.getData() != null;
        if (!success) {
            // 走正常 JSON 通道，不带下载头 —— 否则浏览器会把错误提示存成 .xlsx
            return ResponseEntity.ok(result == null ? Result.fail("导出失败") : result);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body((Object) result.getData());
    }
}
