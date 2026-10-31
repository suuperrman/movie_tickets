package org.javaup.utils;

import java.util.Base64;

/**
 * 座位状态Bitmap工具类。
 * <p>
 * 编码规则（与前端解码对应）：
 *   - 每个座位占 2 bit：00=可售(0) 01=锁定(1) 10=已售(2)
 *   - 大端序：每个byte的高2位存索引小的座位
 *   - Base64编码传输
 *   - 索引 = rowNo × colCount + colNo（从0开始）
 * </p>
 */
public final class SeatBitmapUtil {

    private SeatBitmapUtil() {}

    /**
     * 从Base64编码的Bitmap中解析指定索引座位的状态
     *
     * @param base64Bitmap Base64编码的bitmap字符串
     * @param seatIndex    座位索引（从0开始，= rowNo × colCount + colNo）
     * @return 0=可售 1=锁定 2=已售，解析失败返回-1
     */
    public static int getSeatStatus(String base64Bitmap, int seatIndex) {
        if (base64Bitmap == null || base64Bitmap.isEmpty() || seatIndex < 0) {
            return -1;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Bitmap);
            int byteIndex = seatIndex / 4;
            if (byteIndex >= bytes.length) {
                return -1;
            }
            int bitOffset = (3 - seatIndex % 4) * 2;
            return (bytes[byteIndex] >> bitOffset) & 0x03;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 构建Bitmap字节数组
     *
     * @param seatCount 座位总数
     * @return 足够容纳所有座位的byte数组
     */
    public static byte[] createBitmap(int seatCount) {
        int byteCount = (seatCount + 3) / 4;
        return new byte[byteCount];
    }

    /**
     * 设置指定索引座位的状态
     *
     * @param bitmap    Bitmap字节数组
     * @param seatIndex 座位索引
     * @param status    状态值（0/1/2）
     */
    public static void setSeatStatus(byte[] bitmap, int seatIndex, int status) {
        if (bitmap == null || seatIndex < 0) {
            return;
        }
        int byteIndex = seatIndex / 4;
        if (byteIndex >= bitmap.length) {
            return;
        }
        int bitOffset = (3 - seatIndex % 4) * 2;
        // 先清零再设值
        bitmap[byteIndex] = (byte) ((bitmap[byteIndex] & ~(0x03 << bitOffset)) | ((status & 0x03) << bitOffset));
    }

    /**
     * 将Bitmap字节数组编码为Base64字符串
     */
    public static String toBase64(byte[] bitmap) {
        if (bitmap == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bitmap);
    }
}
