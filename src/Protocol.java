import java.util.ArrayList;
import java.util.List;

/**
 * Protocol.java - Lop chuan hoa giao thuc (Application Protocol) cho
 * chuong trinh Mail Server.
 *
 * <p>Vi du ve giao thuc (text-based, phan tach bang dau '|', ket thuc bang CRLF):
 *
 * <pre>
 *   REQUEST :  &lt;OP&gt;|&lt;arg1&gt;|&lt;arg2&gt;|...|&lt;CRLF&gt;
 *   RESPONSE:  &lt;STATUS&gt;|&lt;message&gt;&lt;CRLF&gt;
 * </pre>
 *
 * <p>Vi du giao dich that:
 *
 * <pre>
 *   Client --&gt; REGISTER|alice|123&lt;CRLF&gt;
 *   Client &lt;-- 200|Account alice created&lt;CRLF&gt;
 *
 *   Client --&gt; LOGIN|bob|123&lt;CRLF&gt;
 *   Client &lt;-- 200|new_email.txt~mail_0001.txt&lt;CRLF&gt;
 * </pre>
 *
 * <p>Vi sao phai tu thiet ke protocol?
 * UDP khong co connection, khong bao dam tin cay va khong dung thu tu.
 * => Tang 7 (ung dung) phai tu lo chuyen hoa cac van de nay.
 */
public final class Protocol {

    private Protocol() {
    }

    // ==================== TACH / GHEP KY TU ====================

    /** Dau phan tach cac truong. */
    public static final char FIELD_SEPARATOR = '|';

    /** Dau phan tach cac phan tu trong phan message cua response (danh sach ten file). */
    public static final char LIST_SEPARATOR = '~';

    /** Ket thuc mot message. */
    public static final String CRLF = "\r\n";

    /** Ket thuc dong, dung de tach tung dong khi doc file mail. */
    public static final String NEWLINE = "\n";

    /** Chu thay the cho ky tu xuong dong trong du lieu nguoi dung nhap. */
    public static final String BR = "<BR>";

    // ==================== MA LENH (OPERATION CODE) ====================

    public static final String OP_REGISTER = "REGISTER";
    public static final String OP_LOGIN = "LOGIN";
    public static final String OP_SEND = "SEND";
    public static final String OP_LOGOUT = "LOGOUT";

    /**
     * {@code LIST|username} — liet ke ten file trong hop thu (khong can mat khau).
     * <p>
     * <b>KHONG phai yeu cau cua de bai.</b> De bai chi yeu cau {@code LOGIN} tra
     * ve danh sach ten file. Lenh nay duoc them de client poll dinh ky, tu cap
     * nhat hop thu khi co thu moi ma khong can bam.
     */
    public static final String OP_LIST = "LIST";

    /**
     * {@code FETCH|username|filename} — tra ve noi dung 1 file thu (khong can mat khau).
     * <p>
     * <b>KHONG phai yeu cau cua de bai.</b> De bai chi yeu cau gui ten file, khong
     * yeu cau gui noi dung. Lenh nay duoc them de client hien thi duoc noi dung thu.
     */
    public static final String OP_FETCH = "FETCH";

    // ==================== MA TRANG THAI (STATUS CODE) ====================

    /** Thanh cong. */
    public static final int OK = 200;
    /** Du lieu khong hop le (sai dinh dang, ten tai khoan khong hop le). */
    public static final int BAD_REQUEST = 400;
    /** Mat khau sai. */
    public static final int UNAUTHORIZED = 401;
    /** Khong tim thay tai khoan. */
    public static final int NOT_FOUND = 404;
    /** Tai khoan da ton tai. */
    public static final int CONFLICT = 409;
    /** Loi phia server (khong doc/ghi duoc file). */
    public static final int SERVER_ERROR = 500;

    /**
     * Kiem tra 1 chuoi status (lay tu {@link #parseResponse(String)}) co bang
     * {@link #OK} khong.
     *
     * @param status chuoi status can kiem tra
     * @return true neu thanh cong
     */
    public static boolean isOk(String status) {
        return status != null && status.equals(String.valueOf(OK));
    }

    // ==================== GIOI HAN KICH THUOC ====================

    /**
     * Kich thuoc toi da cua mot UDP datagram (RFC 768):
     * 65535 (IP max) - 20 (IP header) - 8 (UDP header) = 65507.
     * De an toan, protocol nay chi cho phep gui toi da 60000 byte.
     */
    public static final int MAX_DATAGRAM_SIZE = 65507;

    /** Do dai toi da cua buffer dem, nho hon MAX_DATAGRAM_SIZE de cat an toan. */
    public static final int BUFFER_SIZE = MAX_DATAGRAM_SIZE;

    public static final int MAX_USERNAME_LENGTH = 32;
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_BODY_LENGTH = 8000;

    /**
     * Han khuyen dich cho noi dung 1 file thu tra ve qua {@link #OP_FETCH}.
     *
     * <p>Mot file thu gom header + body. Body bi chan o {@link #MAX_BODY_LENGTH}
     * (8000) nen file thu thuong <b>mot</b> datagram la du — nhanh va khong lo
     * ban cat nho. Han nay van cần: mot file bi ghi tay ben ngoai co the lon hon
     * nhieu, va khong gui datagram vuot qua {@link #MAX_DATAGRAM_SIZE} se khong
     * duoc phong giao.
     */
    public static final int MAX_MAIL_CONTENT_SIZE = 60000;

    // ==================== HAM BIEN DOI KY TU (ESCAPE) ====================

    /**
     * Escape du lieu do nguoi dung nhap de khong pha hong cau truc message.
     * - Ky tu '|' bi thay bang "\|"
     * - Ky tu xuong dong / carriage return bi thay bang &lt;BR&gt;
     *
     * @param s chuoi can escape (co the null)
     * @return chuoi da escape, null/neutral hoa neu s null
     */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == FIELD_SEPARATOR) {
                sb.append('\\').append(c);
            } else if (c == '\r') {
                // bo qua, se append '\n' o nhanh truong xuong dong
            } else if (c == '\n') {
                sb.append(BR);
            } else if (c < 0x20 || c == 0x7F) {
                // bo qua ky tu dieu khien (trong \0 null byte, TAB, ...)
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Unescape chuoi nhan duoc tu server (chuyen nguoc lai escape).
     *
     * @param s chuoi can unescape (co the null)
     * @return chuoi da unescape
     */
    public static String unescape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                if (next == FIELD_SEPARATOR || next == '\\') {
                    sb.append(next);
                } else {
                    sb.append(c).append(next);
                }
            } else if (BR.equals(s.substring(i, Math.min(s.length(), i + BR.length())))) {
                sb.append('\n');
                i += BR.length() - 1;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ==================== PHAN TICH REQUEST ====================

    /**
     * Phan tich 1 request thanh danh sach truong, hoan tat ca bo giai ma.
     *
     * <p>Hai viec duoc lam trong cung mot lan quet:
     * <ul>
     *   <li><b>Tach truong</b>: dau {@code |} (khong escape) la ranh giori giua cac truong.</li>
     *   <li><b>Giai ma</b>: {@code \|} -&gt; {@code |}, {@code <BR>} -&gt; {@code \n}.</li>
     * </ul>
     * Neu chi tach ma khong giai ma, du lieu nguoi dung nhap se con nguyen ky tu
     * {@code <BR>} va bi ghi sai vao file email.
     *
     * @param raw noi dung request tho (co the co CRLF thua o cuoi)
     * @return List truong da tach va da giai ma
     */
    public static List<String> parseRequest(String raw) {
        String text = strip(raw);
        List<String> fields = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == FIELD_SEPARATOR) {
                fields.add(current.toString());
                current.setLength(0);
            } else if (text.startsWith(BR, i)) {
                current.append('\n');
                i += BR.length() - 1;
            } else {
                current.append(c);
            }
        }
        if (escaped) {
            current.append('\\');
        }
        fields.add(current.toString());
        return fields;
    }

    /**
     * Loai bo CRLF / CR / LF o cuoi chuoi (client co the gui them cho an toan).
     *
     * @param raw chuoi tho
     * @return chuoi da lam sach
     */
    public static String strip(String raw) {
        if (raw == null) {
            return "";
        }
        int end = raw.length();
        while (end > 0) {
            char c = raw.charAt(end - 1);
            if (c == '\r' || c == '\n') {
                end--;
            } else {
                break;
            }
        }
        return raw.substring(0, end);
    }

    /**
     * Loc khoi xuong dong trong noi dung nhap cua nguoi dung de hien thi tren console.
     *
     * @param s chuoi can loc
     * @return chuoi 1 dong duy nhat
     */
    public static String toSingleLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }

    // ==================== TAO RESPONSE ====================

    /**
     * Tao response thanh cong.
     *
     * @param message noi dung thong bao
     * @return chuoi response hoan chinh
     */
    public static String ok(String message) {
        return OK + String.valueOf(FIELD_SEPARATOR) + escape(message) + CRLF;
    }

    /**
     * Tao response loi.
     *
     * @param status ma loi (400, 401, 404, 409, 500)
     * @param message ly do
     * @return chuoi response hoan chinh
     */
    public static String error(int status, String message) {
        return status + String.valueOf(FIELD_SEPARATOR) + escape(message) + CRLF;
    }

    // ==================== TIEN ICH DOC RESPONSE ====================

    /**
     * Doc 1 response tu client.
     *
     * @param raw noi dung nhan duoc tu server
     * @return mang 2 phan tu: [status, message]; neu hong thi [status, null]
     */
    public static String[] parseResponse(String raw) {
        String text = strip(raw);
        int idx = text.indexOf(FIELD_SEPARATOR);
        if (idx < 0) {
            return new String[]{"0", text};
        }
        String status = text.substring(0, idx).trim();
        String message = unescape(text.substring(idx + 1));
        return new String[]{status, message};
    }

    /**
     * Lay danh sach ten file tu message cua response 200 cua lenh LOGIN.
     *
     * @param message message sau khi da unescape
     * @return danh sach ten file (rong neu khong co mail nao)
     */
    public static List<String> parseFileList(String message) {
        List<String> files = new ArrayList<>();
        if (message == null || message.isBlank()) {
            return files;
        }
        for (String name : message.split(String.valueOf(LIST_SEPARATOR))) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                files.add(trimmed);
            }
        }
        return files;
    }

    /**
     * Ghep danh sach thanh 1 message theo dau LIST_SEPARATOR.
     *
     * @param items danh sach ten file
     * @return chuoi "f1~f2~f3"
     */
    public static String joinFileList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(LIST_SEPARATOR);
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
