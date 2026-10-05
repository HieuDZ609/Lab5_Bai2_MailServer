import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mailbox.java - Lop quan ly ton tai thu muc tai khoan (mailbox) va cac file
 * email ben trong.
 *
 * <p>Cau truc du lieu tren dia:
 *
 * <pre>
 *   data/
 *   |-- accounts.dat            &lt;-- luu mat khau da bam SHA-256
 *   |-- alice/
 *   |   |-- new_email.txt       &lt;-- mail chao mung (tao 1 lan luc dang ky)
 *   |   |-- mail_0001.txt       &lt;-- email thu 1
 *   |   `-- mail_0002.txt       &lt;-- email thu 2
 *   `-- bob/
 *       |-- new_email.txt
 *       `-- mail_0001.txt
 * </pre>
 *
 * <p>Day la ban rut gon cua dinh dang <b>Maildir</b> (RFC ngoai chuan): moi email la
 * MOT file rieng, ten file co so thu tu de khong trung.
 *
 * <p>Lop nay <b>khong</b> phu thuoc vao socket - no chi quan ly file. Nho vay co the
 * test logic ma khong can mo mang.
 */
public class Mailbox {

    /** Ten file chua danh sach tai khoan + mat khau da bam. */
    private static final String ACCOUNTS_FILE = "accounts.dat";

    /** Ten file mail chao mung, dinh nghia chinh xac theo de bai. */
    public static final String WELCOME_FILE = "new_email.txt";

    /** Tien to file email thuong. */
    private static final String MAIL_PREFIX = "mail_";

    /** Duoi ten file email. */
    private static final String MAIL_SUFFIX = ".txt";

    /**
     * Chi chap nhan ten tai khoan gom 3-32 ky tu chu/so/gach duoi.
     * Regex nay chong tan cong <b>Path Traversal</b>: vi du ".." hoac "../../etc"
     * deu khong khop nen bi tu choi truoc khi ghep vao duong dan file.
     */
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

    /** Ten domain cua he thong mail. */
    private static final String DOMAIN = "mailserver.local";

    /** Dia chi email cua he thong, dung trong header From cua mail chao mung. */
    private static final String SYSTEM_ADDRESS = "system@" + DOMAIN;

    /** Hau to dia chi email cua nguoi dung. */
    public static final String DEFAULT_DOMAIN = "@" + DOMAIN;

    /** Noi dung mail chao mung - dung nguyen van de bai. */
    private static final String WELCOME_BODY =
            "Thank you for using this service. we hope that you will feel comfortabl........";

    /** Dinh dang ngay gio theo RFC 5322: "Fri, 03 Oct 2026 22:30:00 +0700". */
    private static final DateTimeFormatter RFC_5322_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

    private final Path dataDir;

    /** Khoa de tranh 2 thread ghi dong thoi vao accounts.dat. */
    private final Object accountsLock = new Object();

    /**
     * @param dataDir thu muc goc luu du lieu (duoc tao neu chua co)
     */
    public Mailbox(String dataDir) {
        this.dataDir = Paths.get(dataDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong tao duoc thu muc du lieu: " + this.dataDir, e);
        }
    }

    public Path getDataDir() {
        return dataDir;
    }

    // ==================== KIEM TRA TEN TAI KHOAN ====================

    /**
     * Kiem tra ten tai khoan co hop le khong.
     *
     * @param username ten tai khoan can kiem tra
     * @return true neu hop le
     */
    public static boolean isValidUsername(String username) {
        return username != null && VALID_USERNAME.matcher(username).matches();
    }

    /**
     * Thong bao ly do ten tai khoan bi tu choi.
     *
     * @param username ten tai khoan bi tu choi
     * @return thong diep tieng Viet
     */
    private static String invalidReason(String username) {
        if (username == null || username.isEmpty()) {
            return "Ten tai khoan khong duoc de trong";
        }
        if (username.length() < 3 || username.length() > Protocol.MAX_USERNAME_LENGTH) {
            return "Ten tai khoan phai tu 3 den " + Protocol.MAX_USERNAME_LENGTH + " ky tu";
        }
        if (!VALID_USERNAME.matcher(username).matches()) {
            return "Ten tai khoan chi duoc chua ky tu a-z, A-Z, 0-9 va gach duoi (_)";
        }
        return null;
    }

    // ==================== YEU CAU 1: TAO ACCOUNT ====================

    /**
     * Tao tai khoan moi: tao thu muc rieng + file {@code new_email.txt} noi dung
     * chao mung. Day la <b>yeu cau 1</b> cua de bai.
     *
     * @param username ten tai khoan
     * @param password mat khau
     * @return response dang chuoi de gui ve client
     */
    public synchronized String createAccount(String username, String password) {
        String error = invalidReason(username);
        if (error != null) {
            return Protocol.error(Protocol.BAD_REQUEST, error);
        }
        if (password == null || password.isEmpty()) {
            return Protocol.error(Protocol.BAD_REQUEST, "Mat khau khong duoc de trong");
        }
        if (password.length() > Protocol.MAX_PASSWORD_LENGTH) {
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Mat khau toi da " + Protocol.MAX_PASSWORD_LENGTH + " ky tu");
        }

        Path mailbox = mailboxOf(username);
        if (Files.exists(mailbox)) {
            // Khong ghi de: giu nguyen mail cua nguoi dung da co.
            return Protocol.error(Protocol.CONFLICT, "Tai khoan '" + username + "' da ton tai");
        }

        try {
            // createDirectory la thao tac nguyen tu: neu thu muc da ton tai (do
            // client khac vua tao) thi nem FileAlreadyExistsException. Phe dinh
            // "ton tai" trong File.exists() o tren chi de dua nhanh.
            Files.createDirectory(mailbox);

            // File mail chao mung - dinh nghia chinh xac theo de bai.
            String welcome = buildMailFile(SYSTEM_ADDRESS, username + DEFAULT_DOMAIN,
                    "Welcome to our mail service", WELCOME_BODY);
            Files.writeString(mailbox.resolve(WELCOME_FILE), welcome, StandardCharsets.UTF_8);

            savePassword(username, password);
            return Protocol.ok("Account '" + username + "' created");
        } catch (FileAlreadyExistsException e) {
            return Protocol.error(Protocol.CONFLICT, "Tai khoan '" + username + "' da ton tai");
        } catch (IOException e) {
            return Protocol.error(Protocol.SERVER_ERROR, "Khong tao duoc thu muc: " + e.getMessage());
        }
    }

    /**
     * Luu mat khau da bam SHA-256 vao accounts.dat.
     *
     * <p>Khong luu mat khau dang ro de tang an toan - khi dang nhap se so sanh bang
     * hash cua mat khau moi nhap.
     */
    private void savePassword(String username, String password) throws IOException {
        String hash = sha256(password);
        String line = username + ":" + hash;
        synchronized (accountsLock) {
            Files.writeString(accountsFile(), line + Protocol.NEWLINE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    /**
     * Doc mat khau da bam cua 1 tai khoan.
     *
     * @param username ten tai khoan
     * @return chuoi hex hash, hoac null neu tai khoan chua duoc luu mat khau
     */
    private String loadPassword(String username) {
        Path file = accountsFile();
        if (!Files.exists(file)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            // Duyet nguoc: dong moi ghi de dong cuoi cung ten tai khoan
            for (int i = lines.size() - 1; i >= 0; i--) {
                String[] parts = lines.get(i).split(":", 2);
                if (parts.length == 2 && parts[0].equals(username)) {
                    return parts[1];
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private Path accountsFile() {
        return dataDir.resolve(ACCOUNTS_FILE);
    }

    /**
     * Tinh hash SHA-256 (dang hex) cua mot chuoi.
     *
     * @param text chuoi can bam
     * @return chuoi hex 64 ky tu
     */
    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Khong tim thay thuat toan SHA-256", e);
        }
    }

    // ==================== YEU CAU 2: GUI EMAIL ====================

    /**
     * Giao mot email vao hop thu cua tai khoan nhan: xac dinh tai khoan nhan tu
     * truong {@code To:}, roi tao file chua noi dung email trong thu muc do.
     * Day la <b>yeu cau 2</b> cua de bai.
     *
     * @param from    dia chi nguoi gui
     * @param to      ten tai khoan nhan
     * @param subject tieu de
     * @param body    noi dung
     * @return response dang chuoi de gui ve client
     *
     * <p><b>Vi sao co {@code synchronized}:</b> viec sinh ten file va ghi file
     * phai la mot khoi nguyen tu. Neu khong khoa, nhieu client gui dong thoi se
     * cung tinh toa nhieu lon trong dem max, cung sinh ra cung ten {@code mail_0007.txt},
     * va cac thread sau se ghi de hoac nem loi. Do la "race condition" kinh dien.
     */
    public synchronized String deliverMail(String from, String to, String subject, String body) {
        String error = invalidReason(to);
        if (error != null) {
            return Protocol.error(Protocol.BAD_REQUEST, "Nguoi nhan khong hop le - " + error);
        }
        if (invalidReason(from) != null) {
            return Protocol.error(Protocol.BAD_REQUEST, "Nguoi gui khong hop le");
        }

        Path mailbox = mailboxOf(to);
        if (!Files.isDirectory(mailbox)) {
            return Protocol.error(Protocol.NOT_FOUND, "Khong tim thay tai khoan nhan '" + to + "'");
        }

        try {
            String fileName = nextMailFileName(mailbox);
            String content = buildMailFile(from + DEFAULT_DOMAIN, to + DEFAULT_DOMAIN,
                    subject, body);
            // Ghi ra file tam roi doi ten sang file chinh -> tranh file hoac khong doc duoc.
            Path temp = mailbox.resolve(fileName + ".tmp");
            Path target = mailbox.resolve(fileName);
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, target);

            return Protocol.ok("Delivered to '" + to + "' as file " + fileName);
        } catch (FileAlreadyExistsException e) {
            return Protocol.error(Protocol.CONFLICT,
                    "Ten file '" + e.getFile() + "' dang ton tai, hay thu lai");
        } catch (IOException e) {
            return Protocol.error(Protocol.SERVER_ERROR, "Khong ghi duoc file email: " + e.getMessage());
        }
    }

    /**
     * Sinh ten file email moi theo so thu tu, dam bao khong trung.
     * Vi du: mail_0001.txt, mail_0002.txt, ...
     *
     * @param mailbox thu muc hop thu
     * @return ten file moi
     */
    private String nextMailFileName(Path mailbox) {
        int max = 0;
        try (var stream = Files.list(mailbox)) {
            for (Path p : stream.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith(MAIL_PREFIX) && name.endsWith(MAIL_SUFFIX)) {
                    try {
                        int value = Integer.parseInt(
                                name.substring(MAIL_PREFIX.length(), name.length() - MAIL_SUFFIX.length()));
                        max = Math.max(max, value);
                    } catch (NumberFormatException ignored) {
                        // Bo qua ten file khong dung dinh dang
                    }
                }
            }
        } catch (IOException ignored) {
            // Khong doc duoc danh sach thi van co the dung so 1
        }
        return String.format("%s%04d%s", MAIL_PREFIX, max + 1, MAIL_SUFFIX);
    }

    // ==================== YEU CAU 3: DANG NHAP VA LIET KE FILE ====================

    /**
     * Kiem tra mat khau cua mot tai khoan.
     *
     * @param username ten tai khoan
     * @param password mat khau
     * @return true neu mat khau dung
     */
    public boolean checkPassword(String username, String password) {
        if (password == null) {
            return false;
        }
        String stored = loadPassword(username);
        if (stored != null) {
            return stored.equals(sha256(password));
        }
        // KHONG co hash -> tu choi. Truoc day code nay tra ve
        // Files.isDirectory(mailboxOf(username)), nghia la mot tai khoan ma
        // thu muc con nguyen nhung dong hash bi mat se chap nhan BAT KY mat khau
        // - day la lo hong bo qua xac thuc, va "khong chan nguoi dung" khong
        // phai ly do chap nhan mat khau sai. Moi tai khoan do chinh chuong
        // trinh tao deu ghi hash ngay, nen quy tac nay khong loai tai khoan
        // hop le nao ra.
        return false;
    }

    /**
     * Dang nhap: mo thu muc hop thu va tra ve danh sach TEN TAT CA FILE trong do.
     * Day la <b>yeu cau 3</b> cua de bai.
     *
     * @param username ten tai khoan
     * @param password mat khau
     * @return response dang chuoi de gui ve client
     */
    public String login(String username, String password) {
        String error = invalidReason(username);
        if (error != null) {
            return Protocol.error(Protocol.BAD_REQUEST, error);
        }

        Path mailbox = mailboxOf(username);
        if (!Files.isDirectory(mailbox)) {
            return Protocol.error(Protocol.NOT_FOUND, "Khong tim thay tai khoan '" + username + "'");
        }
        if (!checkPassword(username, password)) {
            return Protocol.error(Protocol.UNAUTHORIZED, "Mat khau sai");
        }

        List<String> files = listFiles(mailbox);
        if (files.isEmpty()) {
            return Protocol.ok("");
        }
        return Protocol.ok(Protocol.joinFileList(files));
    }

    /**
     * Liet ke ten tat ca file trong thu muc hop thu.
     *
     * @param mailbox thu muc can xem
     * @return danh sach ten file, sap xep de co thu tu on dinh
     */
    private List<String> listFiles(Path mailbox) {
        try (var stream = Files.list(mailbox)) {
            List<String> names = new ArrayList<>();
            for (Path p : stream.toList()) {
                if (Files.isRegularFile(p)) {
                    names.add(p.getFileName().toString());
                }
            }
            Collections.sort(names);
            return names;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    // ==================== TIEN ICH ====================

    /**
     * Lay duong dan thu muc hop thu cua 1 tai khoan.
     * <p><b>LUU Y:</b> luu giu nguyen dataDir de chong Path Traversal. Ten tai khoan
     * phai duoc kiem tra bang {@link #isValidUsername(String)} truoc khi goi ham nay.
     *
     * @param username ten tai khoan da kiem tra hop le
     * @return duong dan thu muc
     */
    public Path mailboxOf(String username) {
        return dataDir.resolve(username).normalize();
    }

    /**
     * Kiem tra tai khoan da ton tai chua.
     *
     * @param username ten tai khoan
     * @return true neu thu muc hop thu ton tai
     */
    public boolean accountExists(String username) {
        return isValidUsername(username) && Files.isDirectory(mailboxOf(username));
    }

    /**
     * Lay danh sach tat ca tai khoan dang co (doc tu dia).
     *
     * @return danh sach ten tai khoan
     */
    public List<String> listAccounts() {
        List<String> accounts = new ArrayList<>();
        try (var stream = Files.list(dataDir)) {
            for (Path p : stream.toList()) {
                if (Files.isDirectory(p)) {
                    String name = p.getFileName().toString();
                    if (isValidUsername(name)) {
                        accounts.add(name);
                    }
                }
            }
        } catch (IOException ignored) {
            // Tra ve danh sach rong
        }
        Collections.sort(accounts);
        return accounts;
    }

    /**
     * Liet ke ten tat ca file trong thu muc hop thu cua mot tai khoan.
     *
     * <p>Dung cho lenh {@code LIST} de client poll cap nhat hop thu theo thoi gian
     * thuc. KHONG xac thuc mat khau — xem ghi chu bao mat o {@link #readMail}.
     *
     * @param username ten tai khoan
     * @return danh sach ten file sap xop theo thu tu, rong neu tai khoan khong ton tai
     */
    public List<String> listMailFiles(String username) {
        if (!isValidUsername(username)) {
            return Collections.emptyList();
        }
        Path mailbox = mailboxOf(username);
        if (!Files.isDirectory(mailbox)) {
            return Collections.emptyList();
        }
        return listFiles(mailbox);
    }

    /**
     * Doc noi dung cua 1 file trong hop thu de hien thi tren client.
     *
     * <p><b>BAO MAT:</b> ham nay khong xac thuc mat khau. Ai co ten tai khoan cung
     * doc duoc noi dung thu cua tai khoan do. Day la han che cua giao thuc
     * {@code FETCH|username|filename} — server khong luu phien dang nhap nen khong
     * co cach nao xac thuc ma khong phai gui lai mat khau theo tung request.
     * Trong pham vi bai tap nay da chap nhan, neu can siet thi phai them phien
     * phia server hoac gui kem mat khau trong chinh request.
     *
     * <p>Ten file duoc kiem tra chong {@code Path Traversal}: khong cho phep
     * {@code ..} hay {@code /}, va ten tai khoan da qua {@link #isValidUsername}
     * truoc do (nen khong the chui ra ngoai thu muc du lieu).
     *
     * @param username ten tai khoan
     * @param fileName ten file can doc
     * @return noi dung file, hoac null neu khong doc duoc
     */
    public String readMail(String username, String fileName) {
        if (!isValidUsername(username) || fileName.contains("..") || fileName.contains("/")) {
            return null;
        }
        try {
            return Files.readString(mailboxOf(username).resolve(fileName), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== DINH DANG EMAIL THEO RFC 5322 ====================

    /**
     * Dung noi dung 1 file email day du theo chuan RFC 5322 (Internet Message Format):
     * cac truong header, mot dong trong, roi den phan body.
     *
     * @param from    dia chi nguoi gui
     * @param to      dia chi nguoi nhan
     * @param subject tieu de
     * @param body    noi dung
     * @return noi dung file email
     */
    public static String buildMailFile(String from, String to, String subject, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from).append(Protocol.NEWLINE);
        sb.append("To: ").append(to).append(Protocol.NEWLINE);
        sb.append("Subject: ").append(subject).append(Protocol.NEWLINE);
        sb.append("Date: ").append(currentRfc5322Date()).append(Protocol.NEWLINE);
        sb.append("Message-ID: <").append(messageId()).append('>').append(Protocol.NEWLINE);
        sb.append("MIME-Version: 1.0").append(Protocol.NEWLINE);
        sb.append("Content-Type: text/plain; charset=\"UTF-8\"").append(Protocol.NEWLINE);
        sb.append(Protocol.NEWLINE);           // dong trong phan cach header / body
        sb.append(body == null ? "" : body).append(Protocol.NEWLINE);
        return sb.toString();
    }

    /**
     * Sinh Message-ID duy nhat theo dang <code>&lt;timestamp.sinh-van-toc@domain&gt;</code>.
     * Dung Unix timestamp (giay) + so ngau nhien de tranh trung khi tao nhieu email
     * trong cung mot giay.
     *
     * @return chuoi dinh dang "1727957400.75348f"
     */
    private static String messageId() {
        long epochSecond = System.currentTimeMillis() / 1000L;
        int random = new java.util.Random().nextInt(0x100000);
        return epochSecond + "." + Integer.toHexString(random) + "@" + DOMAIN;
    }

    /**
     * Lay thoi gian hien tai theo dinh dang ngay gio cua RFC 5322.
     *
     * @return chuoi dang "Fri, 03 Oct 2026 22:30:00 +0700"
     */
    public static String currentRfc5322Date() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return now.format(RFC_5322_DATE);
    }
}
