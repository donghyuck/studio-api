package studio.one.platform.mediaio.util;

public final class MediaTypeUtil {
    private MediaTypeUtil() {
    }

    /** 파일명 우선 → contentType → 기본 jpg */
    public static String extFromNameOrType(String fileName, String contentType) {
        if (fileName != null) {
            String n = fileName.toLowerCase();
            for (String e : new String[] { ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp" }) {
                if (n.endsWith(e))
                    return e.substring(1);
            }
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("png"))
                return "png";
            if (ct.contains("webp"))
                return "webp";
            if (ct.contains("gif"))
                return "gif";
            if (ct.contains("bmp"))
                return "bmp";
            if (ct.contains("jpeg") || ct.contains("jpg"))
                return "jpg";
        }
        return "jpg";
    }

    /** ImageIO.write 포맷 추정 */
    public static String guessWriteFormat(String contentType, String fileName) {
        return switch (extFromNameOrType(fileName, contentType)) {
            case "png", "webp", "gif", "bmp" -> extFromNameOrType(fileName, contentType);
            default -> "jpeg";
        };
    }
}
