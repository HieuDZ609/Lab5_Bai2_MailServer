import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * MailServer.java - MAY CHU UDP cua chuong trinh Mail Server.
 *
 * <p>Vai tro (theo mo hinh client/server):
 * <ul>
 *   <li>Chay tren may chu, <b>khong phai noi ai</b> (passive).</li>
 *   <li>Lang nghe co dinh tren mot cong (mac dinh 2346).</li>
 *   <li>Nhan request tu client, xu ly, gui response ve lai DUNG DIA CHI
 *       IP + PORT cua nguoi gui (lay tu chinh DatagramPacket vua nhan).</li>
 * </ul>
 *
 * <p>Quy trinh tao UDP socket cua server:
 * {@code socket() -> bind() -> recvfrom() -> sendto() -> close()}
 *
 * <p>Xu ly dong thoi: mot thread "listener" chi nham method {@code receive()},
 * day khung tin sang mot thread pool de xu ly. Cach nay tranh viec mot client
 * gui mail dai se lam nghe trong tat ca client khac.
 */
public class MailServer {

    /** Cong mac dinh cua mail server. */
    public static final int DEFAULT_PORT = 2346;

    /**
     * Thu muc goc luu du lieu mac dinh: thu muc con {@code data/} nam trong thu muc
     * du an.
     *
     * <p>Dung duong dan tuyet doi de du lieu luon nam dung choi du an, khong phu
     * thuoc vao thu muc dang chay tien trinh (neu mo terminal o noi khac thi
     * {@code data/} se bi tao nham o do).
     *
     * <p>Day la <b>nguon duy nhat</b> cua duong dan nay: {@link MailServerFrame}
     * doc tu day de hien thi o o "Thu muc du lieu", tranh truong hop hai noi
     * khai bao hai duong dan khac nhau.
     */
    public static final String DEFAULT_DATA_DIR =
            "/mnt/Nigga/Hoc_Tap/LTM/Lab5_Bai2_MailServer/data";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Chu ky gom dong log cua vong poll.
     *
     * <p>Client poll {@code LIST} moi 1 giay, nen log tung dong se in 2 dong
     * ({@code ←} + {@code →}) cho moi giay — sau 10 phut la 1200 dong chi de
     * noi "van con song". Voi 5 client con dung thi nhieu hon nua.
     */
    private static final long POLL_LOG_INTERVAL_MS = 30_000;

    /** Do dai toi da cho 1 dong log, truoc khi cat bang dau "…". */
    private static final int LOG_MAX_CHARS = 120;

    /**
     * Bat/tat in chi tiet tung request cua vong poll.
     *
     * <p>Mac dinh {@code false}: poll duoc gom lai theo chu ky {@link #POLL_LOG_INTERVAL_MS}.
     * Bat len bang {@code -Dmail.verbosePoll=true} khi can soi tung request (vd dang
     * nhap van mot request nao do len khong doi), bat lai ngay khi xong.
     */
    private static final boolean VERBOSE_POLL = Boolean.getBoolean("mail.verbosePoll");

    /**
     * So {@code LIST} da bo qua tinh tu lan in gan nhat, va danh sach tai khoan
     * dang duoc poll. Chi dung de in dong tong hop theo chu ky.
     */
    private final java.util.Map<String, Integer> pollCounts = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile long lastPollLogAt = 0;

    private final int port;
    private final Mailbox mailbox;

    /**
     * Noi nhan tin nhat ky. Mac dinh in ra console; GUI truyen vao mot lambda
     * de day sang JTextArea (co bo dem de quy ve EDT).
     */
    private final Consumer<String> logSink;

    /**
     * Bo dem request. La <b>instance field</b> chu khong phai static, vi GUI cho
     * phep Bam dau / Dung nhieu lan - moi lan bat dau lai phai dua so thu tu ve 1.
     */
    private final AtomicLong requestCounter = new AtomicLong(0);

    private volatile ExecutorService workerPool;
    private volatile boolean running = false;
    private volatile DatagramSocket serverSocket;

    /** Loi gan cong, giu lai de GUI hien thi sau khi {@link #start()} tra ve false. */
    private volatile String lastError;

    public MailServer(int port, String dataDir) {
        this(port, dataDir, message -> System.out.println(stamp(message)));
    }

    public MailServer(int port, String dataDir, Consumer<String> logSink) {
        this.port = port;
        this.mailbox = new Mailbox(dataDir);
        this.logSink = logSink == null ? message -> { } : logSink;
    }

    private static String stamp(String message) {
        return "[" + LocalTime.now().format(TIME_FMT) + "] " + message;
    }

    public int getPort() {
        return port;
    }

    public String getDataDir() {
        return mailbox.getDataDir().toString();
    }

    public boolean isRunning() {
        return running;
    }

    /** @return mo ta loi gan cong gan nhat, hoac null neu khong co loi */
    public String getLastError() {
        return lastError;
    }

    // ==================== CHAY SERVER ====================

    /**
     * Bat dau vong lap lang nghe trong mot thread rieng.
     *
     * @return {@code true} neu bind thanh cong va server dang chay,
     *         {@code false} neu khong bind duoc cong (da dong, sai dinh dang...)
     */
    public boolean start() {
        if (running) {
            return true;
        }

        DatagramSocket socket;
        try {
            // socket() + bind(): gan socket vao cong da chon
            socket = new DatagramSocket(port);
            this.serverSocket = socket;
            this.lastError = null;
            this.requestCounter.set(0);
            // Reset dong tong hop: neu khong, lan poll dau tien se in dong
            // tong hop vi lastPollLogAt con la 0.
            this.lastPollLogAt = System.currentTimeMillis();
            this.pollCounts.clear();
            this.workerPool = Executors.newFixedThreadPool(10, r -> {
                Thread t = new Thread(r, "mail-worker");
                t.setDaemon(true);
                return t;
            });
            this.running = true;
        } catch (SocketException e) {
            this.lastError = "Khong bind duoc cong " + port + ": " + e.getMessage();
            log(lastError + " — cong co the dang duoc chiem boi chuong trinh khac.");
            return false;
        }

        log("Đang lắng nghe trên cổng " + port);
        log("Thư mục dữ liệu: " + mailbox.getDataDir());
        List<String> accounts = mailbox.listAccounts();
        log("Số tài khoản đã tồn tại: " + accounts.size()
                + (accounts.isEmpty() ? "" : " (" + String.join(", ", accounts) + ")"));

        // Thread rieng cho vong lap, de GUI van con phan hoi su kien
        Thread listener = new Thread(() -> listenLoop(socket), "mail-listener");
        listener.setDaemon(false);
        listener.start();
        return true;
    }

    /**
     * Vong lap chinh: cho den khi server dung.
     *
     * @param socket socket da bind vao cong
     */
    private void listenLoop(DatagramSocket socket) {
        byte[] buffer = new byte[Protocol.BUFFER_SIZE];

        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                // CHAN: cho den khi co datagram gui toi
                socket.receive(packet);

                InetAddress clientIp = packet.getAddress();
                int clientPort = packet.getPort();
                String raw = new String(packet.getData(), 0, packet.getLength(),
                        StandardCharsets.UTF_8);

                long id = requestCounter.incrementAndGet();
                // LIST la vong poll: chi in dong tong hop theo chu ky, khong in
                // tung request (xem POLL_LOG_INTERVAL_MS).
                boolean isPoll = !VERBOSE_POLL
                        && raw.startsWith(Protocol.OP_LIST + Protocol.FIELD_SEPARATOR);
                if (!isPoll) {
                    log("[" + id + "] ← " + clientIp.getHostAddress() + ":" + clientPort
                            + "  " + Protocol.toSingleLine(raw));
                } else {
                    pollCounts.merge(pollUser(raw), 1, Integer::sum);
                }

                final long reqId = id;
                // Day qua xu ly o thread khac de listener khong bi chan boi I/O file
                workerPool.submit(() -> {
                    String response = handle(raw, clientIp);
                    try {
                        send(socket, clientIp, clientPort, response);
                        if (!isPoll) {
                            log("[" + reqId + "] → " + clientIp.getHostAddress() + ":"
                                    + clientPort + "  " + Protocol.toSingleLine(response));
                        }
                    } catch (IOException e) {
                        log("[" + reqId + "] Không gửi được response: " + e.getMessage());
                    }
                });

            } catch (IOException e) {
                if (running) {
                    log("Lỗi khi nhận/gửi: " + e.getMessage());
                } else {
                    break;
                }
            }

            // Dat sau khoi catch: moi vong lap da xu ly xong 1 datagram thi
            // thu in dong tong hop poll. Khong chan nhac poll nen client nhan
            // duoc thu moi ngay.
            logPollSummary(System.currentTimeMillis());
        }

        log("Đang đóng máy chủ…");
        ExecutorService pool = workerPool;
        workerPool = null;
        if (pool != null) {
            pool.shutdown();
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        socket.close();
        log("Máy chủ đã đóng.");
    }

    /**
     * Gui response ve dung dia chi IP + PORT cua nguoi gui.
     * <p>
     * <b>Nguyen tac quan trong:</b> phai dung {@code clientIp}/{@code clientPort} lay tu
     * chinh DatagramPacket vua nhan, khong duoc dung mot dia chi co dinh - neu sai,
     * client se khong bao gio nhan duoc response vi no dang cho o cong cua minh.
     */
    private static void send(DatagramSocket socket, InetAddress clientIp, int clientPort,
                             String response) throws IOException {
        byte[] data = response.getBytes(StandardCharsets.UTF_8);
        if (data.length > Protocol.MAX_DATAGRAM_SIZE) {
            // Cat ngan de khong vuot gioi han 1 datagram
            byte[] cut = new byte[Protocol.MAX_DATAGRAM_SIZE];
            System.arraycopy(data, 0, cut, 0, cut.length);
            data = cut;
        }
        socket.send(new DatagramPacket(data, data.length, clientIp, clientPort));
    }

    // ==================== XU LY REQUEST ====================

    /**
     * Phan tich va xu ly 1 request, tra ve response dang chuoi.
     *
     * @param raw noi dung request tho
     * @return response dang chuoi de gui ve client
     */
    /**
     * @param clientIp dia chi IP may gui request, lay tu {@link DatagramPacket}.
     *                  Truyen xuong {@code SEND}/{@code FETCH} de ghi vao file thu.
     */
    private String handle(String raw, InetAddress clientIp) {
        List<String> fields;
        try {
            fields = Protocol.parseRequest(raw);
        } catch (RuntimeException e) {
            return Protocol.error(Protocol.BAD_REQUEST, "Khong phan tich duoc request");
        }

        if (fields.isEmpty() || fields.get(0).isBlank()) {
            return Protocol.error(Protocol.BAD_REQUEST, "Request rong");
        }
        String op = fields.get(0).trim().toUpperCase();
        if (fields.get(0).length() > 20) {
            return Protocol.error(Protocol.BAD_REQUEST, "Ten lenh qua dai");
        }

        try {
            switch (op) {
                case Protocol.OP_REGISTER:
                    // Yeu cau 1: tao account -> tao thu muc + file new_email.txt
                    return handleRegister(fields);

                case Protocol.OP_LOGIN:
                    // Yeu cau 3: dang nhap -> tra ve danh sach ten file trong thu muc
                    return handleLogin(fields);

                case Protocol.OP_SEND:
                    // Yeu cau 2: gui email -> xac dinh account nhan, tao file noi dung
                    return handleSend(fields, clientIp);

                case Protocol.OP_LOGOUT:
                    return Protocol.ok("Goodbye");

                case Protocol.OP_LIST:
                    // Mo rong (KHONG phai yeu cau de bai): poll danh sach thu moi
                    return handleList(fields);

                case Protocol.OP_FETCH:
                    // Mo rong (KHONG phai yeu cau de bai): lay noi dung 1 file thu
                    return handleFetch(fields, clientIp);

                default:
                    return Protocol.error(Protocol.BAD_REQUEST,
                            "Unknown command '" + op + "'. Supported: REGISTER, LOGIN, SEND, LOGOUT, LIST, FETCH");
            }
        } catch (Exception e) {
            // Loi khong mong doi: dung chung cho moi truong hop de server khong chet
            log("!! Loi khi xu ly: " + e);
            return Protocol.error(Protocol.SERVER_ERROR, "Internal error: " + e.getMessage());
        }
    }

    /**
     * REGISTER|username|password
     */
    private String handleRegister(List<String> fields) {
        if (fields.size() != 3) {
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Cu phap: REGISTER|username|password");
        }
        return mailbox.createAccount(fields.get(1), fields.get(2));
    }

    /**
     * LOGIN|username|password
     */
    private String handleLogin(List<String> fields) {
        if (fields.size() != 3) {
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Cu phap: LOGIN|username|password");
        }
        return mailbox.login(fields.get(1), fields.get(2));
    }

    /**
     * LIST|username — tra ve danh sach ten file trong hop thu.
     *
     * <p>Mo rong cho client poll, khong phai yeu cau cua de bai. Client goi lenh
     * nay moi giay mot lan de tu phat hien thu moi, nen khong xac thuc mat khau.
     * Xem ghi chu bao mat tai {@link Mailbox#readMail}.
     *
     * @param fields truong da tach cua request
     * @return response dang chuoi de gui ve client
     */
    private String handleList(List<String> fields) {
        if (fields.size() != 2) {
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Cu phap: LIST|username");
        }
        String username = fields.get(1);
        if (!Mailbox.isValidUsername(username)) {
            return Protocol.error(Protocol.BAD_REQUEST, "Ten tai khoan khong hop le");
        }
        if (!java.nio.file.Files.isDirectory(mailbox.mailboxOf(username))) {
            return Protocol.error(Protocol.NOT_FOUND,
                    "Khong tim thay tai khoan '" + username + "'");
        }
        return Protocol.ok(Protocol.joinFileList(mailbox.listMailFiles(username)));
    }

    /**
     * FETCH|username|filename — tra ve noi dung 1 file thu.
     *
     * <p>Mo rong de client hien thi noi dung thu, khong phai yeu cau cua de bai.
     * Khong xac thuc mat khau — xem ghi chu bao mat tai {@link Mailbox#readMail}.
     * {@link Mailbox#readMail} da chan {@code ..} va {@code /} nen client khong
     * doc duoc file ngoai thu muc du lieu.
     *
     * @param fields truong da tach cua request
     * @return response dang chuoi de gui ve client
     */
    private String handleFetch(List<String> fields, InetAddress clientIp) {
        if (fields.size() != 3) {
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Cu phap: FETCH|username|filename");
        }
        String username = fields.get(1);
        String fileName = fields.get(2);
        if (!Mailbox.isValidUsername(username)) {
            return Protocol.error(Protocol.BAD_REQUEST, "Ten tai khoan khong hop le");
        }
        if (fileName.isBlank() || fileName.contains("..") || fileName.contains("/")) {
            return Protocol.error(Protocol.BAD_REQUEST, "Ten file khong hop le");
        }

        // Ghi IP nguoi doc vao file thu (lan dau tien) va tra ve noi dung da cap
        // nhat, de client thay ngay dong Receiver-IP tren man hinh.
        String content = mailbox.readMailWithReceiverIp(username, fileName,
                clientIp == null ? null : clientIp.getHostAddress());
        if (content == null) {
            return Protocol.error(Protocol.NOT_FOUND,
                    "Khong tim thay file '" + fileName + "' trong hop thu cua '"
                            + username + "'");
        }
        if (content.length() > Protocol.MAX_MAIL_CONTENT_SIZE) {
            // File thu qua lon (bi ghi tay ben ngoai) se khong vua mot datagram.
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Noi dung thu qua lon (" + content.length()
                            + " ky tu, toi da " + Protocol.MAX_MAIL_CONTENT_SIZE + ")");
        }
        // Protocol.ok() escape noi dung, nen xuong dong thanh <BR> va ky tu '|'
        // duoc bao to; parseResponse() ben client se giai ma nguoc lai.
        return Protocol.ok(content);
    }

    /**
     * SEND|from|to|subject|body
     */
    private String handleSend(List<String> fields, InetAddress clientIp) {
        if (fields.size() != 5) {
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Cu phap: SEND|from|to|subject|body");
        }
        String from = fields.get(1);
        String to = fields.get(2);
        String subject = fields.get(3);
        String body = fields.get(4);

        if (subject.length() > Protocol.MAX_SUBJECT_LENGTH) {
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Tieu de qua dai (toi da " + Protocol.MAX_SUBJECT_LENGTH + " ky tu)");
        }
        if (body.length() > Protocol.MAX_BODY_LENGTH) {
            return Protocol.error(Protocol.BAD_REQUEST,
                    "Noi dung qua dai (toi da " + Protocol.MAX_BODY_LENGTH + " ky tu)");
        }
        if (subject.isBlank()) {
            subject = "(Khong co tieu de)";
        }
        if (body.isBlank()) {
            body = "(Email rong)";
        }
        // IP nguoi gui lay chinh truc tiep tu datagram SEND vua nhan: day la noi
        // duy nhat server biet may nao that su gui thu nay.
        return mailbox.deliverMail(from, to, subject, body,
                clientIp == null ? null : clientIp.getHostAddress());
    }

    // ==================== TIEN ICH ====================

    /**
     * Gui 1 dong nhat ky den {@link #logSink}. Ham nay duoc goi tu nhieu thread
     * (listener + worker pool) nen phai xu ly thread-safe — GUI se chuyen sang EDT.
     */
    private void log(String message) {
        logSink.accept(stamp(shorten(message)));
    }

    /**
     * Cat dong log dai.
     *
     * <p>Response cua {@code FETCH} chua nguyen noi dung thu (co the vai dong),
     * va response cua {@code LIST} la ca danh sach ten file — in nguyen se lam
     * dong nhiet vu khong doc duoc va loang dong chay mau. Do loi nhac toi,
     * phan con lai van con dung nguyen de doc.
     */
    private static String shorten(String s) {
        return s.length() <= LOG_MAX_CHARS
                ? s
                : s.substring(0, LOG_MAX_CHARS) + "… (+" + (s.length() - LOG_MAX_CHARS)
                        + " ký tự)";
    }

    /**
     * @return tai khoan trong yeu cau {@code LIST|username}, hoac {@code "?"} neu
     *         yeu cau sai dinh dang
     */
    private static String pollUser(String raw) {
        int bar = raw.indexOf(Protocol.FIELD_SEPARATOR);
        if (bar < 0 || raw.length() <= bar + 1) return "?";
        String user = Protocol.unescape(raw.substring(bar + 1)).trim();
        // Bo phan du thoi gian neu client gui kem (hien tai khong co, nhung de khong
        // in rac "hung01, 14:32:05" neu LUC NAO doi protocol).
        int comma = user.indexOf(',');
        return (comma > 0 ? user.substring(0, comma) : user).trim();
    }

    /**
     * In dong tong hop cua vong poll, toi da mot lan moi {@link #POLL_LOG_INTERVAL_MS}.
     *
     * <p>Dong nay thay the hang tram dong chi tiet, nhung van cho biet server con
     * phuc vu ai va hop thu dang co bao thu — thong tin duoc gui co y khi can.
     *
     * @return {@code true} neu vua in dong tong hop
     */
    private boolean logPollSummary(long now) {
        long last = lastPollLogAt;
        if (now - last < POLL_LOG_INTERVAL_MS) return false;
        // So sanh va gan atomic: chi mot thread duoc in.
        synchronized (pollCounts) {
            if (now - lastPollLogAt < POLL_LOG_INTERVAL_MS) return false;
            lastPollLogAt = now;
            if (pollCounts.isEmpty()) return false;
            StringBuilder sb = new StringBuilder("⟳ poll LIST ");
            pollCounts.forEach((user, count) -> {
                int mails = mailbox.listMailFiles(user).size();
                if (sb.charAt(sb.length() - 1) != ' ') sb.append(" · ");
                sb.append(user).append(" (").append(count).append(" lần, ")
                        .append(mails).append(" thư)");
            });
            pollCounts.clear();
            log(sb.toString());
            return true;
        }
    }

    /**
     * Dung server.
     * <p>
     * Chi co {@code running = false} thi khong du, vi {@code receive()} dang chan vo
     * han. Phai dong socket de no nem {@link java.net.SocketException}, vong lap
     * {@code while (running)} moi thoat duoc.
     */
    public void shutdown() {
        running = false;
        DatagramSocket socket = this.serverSocket;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
