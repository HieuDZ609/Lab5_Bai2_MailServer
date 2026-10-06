import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 *   |-- accounts.dat            &lt;-- "user:hash SHA-256:thoi gian tao"
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

    /**
     * Ten thu muc con: chua ban gui cua chinh tai khoan.
     *
     * <p><b>Viet sao luu o rieng thu muc con, khong danh dau ten file:</b> danh dau
     * ten file se lam hong giao thuc — {@code LOGIN} phai tra ve
     * {@code mail_0001.txt~mail_0002.txt}, neu chen them tien to thi client khong con
     * cach nao phan biet thu nao la thu den. Thay vao do tach theo <b>thu muc</b>: mot
     * lenh {@code LIST}/{@code FETCH} co them tham so {@code folder} de chon.
     *
     * <p>Cung vi ly do nay, {@link #FOLDER_SENT} <b>khong duoc phep la ten tai khoan</b>
     * (xem {@link #isValidUsername}).
     */
    public static final String FOLDER_SENT = "sent";

    /** Hop thu den (thu muc goc cua tai khoan). */
    public static final String FOLDER_INBOX = "inbox";

    /**
     * Ten file tam khi luu ban gui — <b>co dau cham ngan</b> nen khong khop
     * {@code mail_*.txt}, vai thay vi duoi {@code .txt} nen {@code LIST} khong bao
     * gio thay no la thu moi.
     */
    private static final String SENT_TEMP_NAME = ".pending.txt";

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

    /**
     * Chi chap nhan hai hop thu nay. Moi truong ten thu muc <b>phai</b> kiem tra
     * {@code == null} truoc khi ghep vao duong dan — neu bo qua, lenh
     * {@code LIST|alice|../../etc} se chui ra ngoai thu muc du lieu. Day la
     * {@code Path Traversal} do chinh ta tu tao ra.
     */
    public static String folderOf(String name) {
        if (FOLDER_INBOX.equals(name) || FOLDER_SENT.equals(name)) return name;
        return null;
    }

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
        // "sent" hop le ve regex nhung <b>khong duoc</b> la ten tai khoan: no la ten
        // thu muc con dung de luu ban gui. Neu cho qua, lenh LIST se hien "sent" nhu
        // mot tai khoan co the dang nhap, va tai khoan that cu lai khong tao duoc.
        if (FOLDER_SENT.equals(username)) return false;
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
        if (FOLDER_SENT.equals(username)) {
            return "'" + FOLDER_SENT + "' la ten hop thu, khong phai ten tai khoan";
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
            // Tao san thu muc con "sent" de cau truc du lieu luon ro rang
            // ngay tu luc dang ky, khong phai doi den lan gui thu dau tien moi co.
            Files.createDirectory(folderDirOf(username, FOLDER_SENT));

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
     * Dinh dang thoi diem tao tai khoan ghi trong accounts.dat.
     *
     * <p>Ghi dang chuoi doc duoc (khong phai epoch) de ai mo file ra cung thay
     * ngay -- theo yeu cau "accounts.dat hien thi thoi gian tao".
     */
    private static final DateTimeFormatter CREATED_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Luu mat khau da bam SHA-256 vao accounts.dat.
     *
     * <p>Dong ghi co 3 truong: {@code user:hash:thoiGianTao}. Khong luu mat khau
     * dang ro de tang an toan - khi dang nhap se so sanh bang hash cua mat khau
     * moi nhap.
     */
    private void savePassword(String username, String password) throws IOException {
        String hash = sha256(password);
        String line = username + ":" + hash + ":"
                + LocalDateTime.now().format(CREATED_FMT);
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
                String[] parts = lines.get(i).split(":", 3);
                if (parts.length >= 2 && parts[0].equals(username)) {
                    return parts[1];
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * Tai khoan co that su khong: co thu muc hop thu <b>va</b> co dong hash mat khau.
     *
     * <p>Cham hon {@link #accountExists} (cai do chi kiem tra thu muc) nen dung de
     * quyet dinh co <i>phep tao du lieu cho</i> tai khoan do hay khong.
     *
     * @param username ten tai khoan can kiem tra
     * @return {@code true} neu la tai khoan da duoc tao qua {@link #createAccount}
     */
    private boolean hasAccount(String username) {
        return accountExists(username) && loadPassword(username) != null;
    }

    /**
     * Thoi diem tai khoan duoc tao (epoch millis), doc tu dong accounts.dat.
     *
     * <p>Tai khoan tao TU TRUOC khi tinh nang nay ra mat (dong chi co 2 truong)
     * hoac dong hash bi mat thi tra ve -1, de code goi biet khong co so lieu.
     *
     * @param username ten tai khoan
     * @return epoch millis, hoac {@code -1L} neu khong biet
     */
    public long accountCreatedAt(String username) {
        Path file = accountsFile();
        if (!Files.exists(file)) {
            return -1L;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String[] parts = lines.get(i).split(":", 3);
                if (parts.length >= 2 && parts[0].equals(username)) {
                    if (parts.length < 3) {
                        return -1L;
                    }
                    try {
                        return LocalDateTime.parse(parts[2], CREATED_FMT)
                                .atZone(ZoneId.systemDefault())
                                .toInstant().toEpochMilli();
                    } catch (DateTimeParseException e) {
                        return -1L;
                    }
                }
            }
        } catch (IOException e) {
            return -1L;
        }
        return -1L;
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
        return deliverMail(from, to, subject, body, null);
    }

    /**
     * Ban nhan {@code senderIp} — may chu biet IP nguoi gui tu chinh datagram
     * {@code SEND} vua nhan, nen ghi thang vao header file thu.
     */
    public synchronized String deliverMail(String from, String to, String subject,
                                          String body, String senderIp) {
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
                    subject, body, senderIp);
            // Ghi ra file tam roi doi ten sang file chinh -> tranh file hoac khong doc duoc.
            Path temp = mailbox.resolve(fileName + ".tmp");
            Path target = mailbox.resolve(fileName);
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, target);

            // Luu them MOT ban gui vao hop thu "sent" cua nguoi gui, de nguoi gui
            // xem lai duoc thu minh vua gui. Khong doi response: client tu tim ban
            // gui bang lenh LIST|user|sent o vong poll tiep theo.
            saveSentCopy(from, to, subject, body, senderIp);

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
    /**
     * Luu ban gui cua thu vao {@code data/<nguoi gui>/sent/}.
     *
     * <p><b>Viet ban gui la COPY chu khong phai "chuyen thu":</b> thu that da vao hop
     * thu nguoi nhan roi. Neu gui cho chinh minh thi se co <b>hai</b> file — mot ben
     * hop thu den, mot ben thu da gui — dung nhu Gmail/Outlook.
     *
     * <p>Ban gui ghi nguyen dung header cua thu goc (ke ca {@code Sender-IP}), nhung
     * <b>khong co {@code Receiver-IP}</b>: day la ban ghi lai phia nguoi gui, chua co
     * ai "doc" ban nay theo nghia den thi co IP nguoi nhan.
     *
     * <p>Khong duoc lam hong viec giao chinh: moi loi o day deu bi bo qua va van tra
     * ve thanh cong, vi thu da den nguoi nhan roi — mat ban gui khong nghiem trong
     * bang mat thu.
     *
     * @return {@code true} neu ghi ban gui thanh cong
     */
    private boolean saveSentCopy(String from, String to, String subject,
                                 String body, String senderIp) {
        // KHONG cap nhat ma: lenh SEND khong xac thuc nguoi gui, "from" chi la
        // chuoi client tu khai. Neu khong kiem tra tai khoan co that su khong,
        // bat ky ai cung co the "gui" voi from la mot tai khoan nguoi khac va tu
        // tao ra data/<from>/sent/ — thu muc do lai duoc doc nhu la tai khoan
        // (listAccounts chi dem thu muc), nen nghia la tao tan cong vao may chu.
        if (!hasAccount(from)) return false;
        try {
            Path sentDir = folderDirOf(from, FOLDER_SENT);
            Files.createDirectories(sentDir);
            String content = buildMailFile(from + DEFAULT_DOMAIN, to + DEFAULT_DOMAIN,
                    subject, body, senderIp);
            writeSentWithFinalName(sentDir, content);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ghi vao 1 ten file <b>khong duoc danh so truoc</b> {@code mail_NNNN.txt}, roi
     * <b>doi ten thanh ten that</b> khi da biet so thu tu tu nhat den lon.
     *
     * <p>Viet thang ra {@code mail_NNNN.txt} se canh do so thu tu voi cac request
     * {@code LIST}/{@code FETCH} dang chay song song: client co the nhan danh sach
     * chua co file moi roi thu moi xuat hien o lan poll sau. Ghi bang ten tam
     * {@code .pending} (khong dung dinh dang {@code mail_*.txt}) nen {@code LIST}
     * khong bao gio thay, chi khi doi ten xong moi thay.
     */
    private String writeSentWithFinalName(Path sentDir, String content) throws IOException {
        Path temp = sentDir.resolve(SENT_TEMP_NAME);
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        String fileName = nextMailFileName(sentDir);
        Path target = sentDir.resolve(fileName);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

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
                // Bo file tam: ghi atomic se tao file phu tam trong chinh thu muc
                // hop thu. Neu liet ke ca no, client co the thay ".pending.txt" nhu
                // mot thu moi, va "khong doi gi" co the bien doi gi.
                if (Files.isRegularFile(p) && !isTempName(p.getFileName().toString())) {
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
     * Duong dan cua 1 hop thu ({@code inbox} = thu muc goc, {@code sent} = thu muc con).
     *
     * <p><b>BAO MAT:</b> {@code folder} phai da qua {@link #folderOf(String)} (chi nhan
     * {@code inbox}/{@code sent}). Neu goi ham nay voi chuoi tu do nguon thi
     * {@code LIST|alice|../../etc} se doc duoc file ngoai thu muc du lieu. Vi vay ham nay
     * <b>nen loi</b> khi gap folder la — neu chi im lang quay ve thu muc goc, mot loi goi
     * sai se doc duoc hop thu den thay vi bao loi, va lenh co folder rac se "chay" ma
     * khong bao loi gi.
     *
     * @param username ten tai khoan da qua {@link #isValidUsername}
     * @param folder   {@code inbox} hoac {@code sent}, da qua {@link #folderOf}
     * @return duong dan thu muc hop thu
     * @throws IllegalArgumentException khi {@code folder} khong phai hop thu hop le
     */
    public Path folderDirOf(String username, String folder) {
        String safe = folderOf(folder);
        if (safe == null) {
            throw new IllegalArgumentException("Hop thu khong hop le: " + folder);
        }
        Path base = mailboxOf(username);
        // "inbox" khong phai thu muc con: no chinh la thu muc goc cua tai khoan
        // (data/<user>). Chi resolve khi folder that su la thu muc con.
        return FOLDER_INBOX.equals(safe) ? base : base.resolve(safe);
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
        return listMailFiles(username, FOLDER_INBOX);
    }

    /**
     * Liet ke ten file cua 1 hop thu ({@code inbox} hoac {@code sent}).
     *
     * <p>Hop thu chua ton tai (vi du {@code sent} cua tai khoan tao truoc khi co tinh
     * nang nay) se tra ve danh sach rong thay vi loi — dung vi client poll
     * {@code sent} ngay cang khi moi dang nhap.
     *
     * @param username ten tai khoan
     * @param folder   {@code inbox} hoac {@code sent}; gia tri khac hop le -> rong
     * @return danh sach ten file sap xop theo thu tu
     */
    public List<String> listMailFiles(String username, String folder) {
        if (!isValidUsername(username) || folderOf(folder) == null) {
            return Collections.emptyList();
        }
        Path dir = folderDirOf(username, folder);
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        return listFiles(dir);
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
     * Doc thu va **ghi lai IP nguoi doc** vao file thu (chi lan dau tien).
     *
     * <p><b>Viet khi doc la dung:</b> IP nguoi nhan chi biet o thoi diem thu bi
     * <i>doc</i>, khong biet luc thu duoc <i>gui</i> — may chu khong the doan. Neu
     * doi chieu "lan doc dau tien" thi moi dung con so IP nguoi nhan.
     *
     * <p><b>Chi ghi mot lan:</b> thu se duoc mo nhieu lan (va boi nhieu may), nhung
     * dong nay phai phan anh nguoi nhan dau tien. Lan sau thay co san nen khong ghi
     * de.
     *
     * <p><b>An toan khi ghi:</b> ghi ra file tam roi {@code move} de dan — dung
     * nguyen tac giong {@link #deliverMail}, neu may chut bi tat giua chung thi file
     * thu van nguyen, khong bi cat nua dong. Het duoc {@code synchronized} cung
     * {@link #deliverMail} nen hai may doc cung mot thu khong ghi de nhau.
     *
     * <p><b>Khong ghi IP cho ban gui:</b> thu muc {@code sent} la ban ghi lai phia
     * nguoi gui, khong co "nguoi doc theo nghia den thi co IP" — nen chi hop thu den
     * moi duoc them dong nay.
     *
     * @param readerIp IP may dang doc thu
     * @return noi dung thu (da bao dam co dong {@code Receiver-IP}), hoac {@code null}
     *         neu khong tim thay file
     */
    public synchronized String readMailWithReceiverIp(String username, String fileName,
                                                      String readerIp) {
        return readMailWithReceiverIp(username, FOLDER_INBOX, fileName, readerIp);
    }

    /**
     * @param folder {@code inbox} hoac {@code sent}
     * @see #readMailWithReceiverIp(String, String, String)
     */
    public synchronized String readMailWithReceiverIp(String username, String folder,
                                                      String fileName, String readerIp) {
        String content = readMail(username, folder, fileName);
        if (content == null) return null;
        // Ban gui khong co nguoi nhan -> khong them dong Receiver-IP.
        if (FOLDER_SENT.equals(folder)) return content;
        if (readerIp == null || readerIp.isBlank()) return content;
        // File da co dong nay rong -> gi nguyen, khong ghi de.
        if (hasHeader(content, "Receiver-IP")) return content;

        String updated = insertHeader(content, "Receiver-IP", readerIp);
        try {
            Path dir = folderDirOf(username, folder);
            Path target = dir.resolve(fileName);
            Path temp = dir.resolve(fileName + ".tmp");
            Files.writeString(temp, updated, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Ghi khong duoc khong duoc de khong doc: van tra ve noi dung da doc duoc,
            // chi la thieu dong IP hien thi.
            return content;
        }
        return updated;
    }

    /**
     * @param fileName ten file can kiem tra
     * @return {@code true} neu la file tam cua co ghi atomic, {@code false} neu la thu that
     */
    private static boolean isTempName(String fileName) {
        return fileName.equals(SENT_TEMP_NAME) || fileName.endsWith(".tmp");
    }

    /**
     * @return {@code true} neu noi dung thu da co dong header nay
     */
    private static boolean hasHeader(String content, String key) {
        String needle = key + ":";
        for (String line : headerBlockOf(content).split("\n")) {
            // Bo \r neu file duoc ghi theo chuan RFC (CRLF).
            if (line.startsWith(needle)) return true;
        }
        return false;
    }

    /**
     * @return phan header cua file thu, dung truoc dong trong phan cach header/body
     */
    private static String headerBlockOf(String content) {
        // File tao boi buildMailFile() dung \n; file cu doi chuan RFC dung \r\n.
        // Phai thu ca hai, neu khong se khong tim thay dong trong khi gap file CRLF.
        int split = content.indexOf("\n\n");
        if (split < 0) split = content.indexOf("\r\n\r\n");
        return split < 0 ? content : content.substring(0, split);
    }

    /**
     * Chen mot dong header moi vao cuoi phan header, dung truoc dong trong.
     *
     * <p>Phai chen <b>truoc</b> dong trong: neu chen sau, dong nay se rơi xuong
     * thanh <b>noi dung thu</b> va hiien thi ra nhu mot doan van cua thu.
     *
     * <p>File thu trong may duoc ghi boi {@link #buildMailFile} bang
     * {@link Protocol#NEWLINE} ({@code \n}), nhung van kiem tra {@code \r\n} truoc
     * de khong lam hong file cu do chuong trinh khac tao.
     */
    private static String insertHeader(String content, String key, String value) {
        String nl = content.contains("\r\n") ? "\r\n" : Protocol.NEWLINE;
        int split = content.indexOf("\n\n");
        if (split < 0) split = content.indexOf("\r\n\r\n");
        if (split < 0) {
            // Khong co dong trong -> khong tach duoc header/body. Ghi them vao
            // cuoi thay vi chen, con hon la lam hong file.
            return content.stripTrailing() + nl + key + ": " + value + nl;
        }
        return content.substring(0, split) + nl + key + ": " + value
                + content.substring(split);
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
        return readMail(username, FOLDER_INBOX, fileName);
    }

    /**
     * Doc noi dung cua 1 file trong hop thu {@code inbox} hoac {@code sent}.
     *
     * <p><b>BAO MAT:</b> {@code folder} phai qua {@link #folderOf} va {@code fileName}
     * phai qua kiem tra {@code ..} / {@code /}. Day la chong {@code Path Traversal}.
     *
     * @param username ten tai khoan
     * @param folder   {@code inbox} hoac {@code sent}
     * @param fileName ten file can doc
     * @return noi dung file, hoac null neu khong doc duoc / tham so sai
     */
    public String readMail(String username, String folder, String fileName) {
        if (!isValidUsername(username) || folderOf(folder) == null) {
            return null;
        }
        if (fileName == null || fileName.contains("..") || fileName.contains("/")
                || fileName.contains("\\")) {
            return null;
        }
        try {
            return Files.readString(folderDirOf(username, folder).resolve(fileName),
                    StandardCharsets.UTF_8);
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
        return buildMailFile(from, to, subject, body, null);
    }

    /**
     * Ban co ghi {@code Sender-IP} — dung khi thu do do chinh may may chu tao ra.
     *
     * @param senderIp dia chi IP may gui, hoac {@code null} de khong ghi (vi du thu
     *                 chao mung do may chu tu sinh)
     */
    public static String buildMailFile(String from, String to, String subject, String body,
                                       String senderIp) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from).append(Protocol.NEWLINE);
        sb.append("To: ").append(to).append(Protocol.NEWLINE);
        sb.append("Subject: ").append(subject).append(Protocol.NEWLINE);
        sb.append("Date: ").append(currentRfc5322Date()).append(Protocol.NEWLINE);
        // Chi ghi khi biet IP: thu cu khong co dong nay (cu) van parse duoc.
        if (senderIp != null && !senderIp.isBlank()) {
            sb.append("Sender-IP: ").append(senderIp).append(Protocol.NEWLINE);
        }
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
