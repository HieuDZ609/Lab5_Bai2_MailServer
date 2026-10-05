import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MailClient.java - MAY KHACH UDP cua chuong trinh Mail Server.
 *
 * <p>Vai tro (theo mo hinh client/server):
 * <ul>
 *   <li>Chay tren may khach, <b>noi truoc</b> (active).</li>
 *   <li>Khong can ket noi - chi gui datagram den dia chi may chu.</li>
 *   <li>Mo vai tro client: tao account, dang nhap, gui email.</li>
 * </ul>
 *
 * <p>Quy trinh tao UDP socket cua client:
 * {@code socket() -> sendto() -> recvfrom() -> close()}
 * Client KHONG goi {@code bind()} cong co dinh - he dieu hanh tu cap cong ngau nhien.
 *
 * <p>Vi UDP khong dam bao tin cay, client luong dat timeout. Neu may chu khong
 * phan hoi sau {@link #TIMEOUT_MS} thi bao loi thay vi treo vo han.
 *
 * <p><b>LUU Y QUAN TRONG:</b> client dung <b>mot</b> socket cho ca chuong trinh
 * va khong co khoa. Vi vay chi duoc gui <b>mot request tai mot thoi diem</b> — neu
 * gui hai request chong nhau, response cua hai lenh se bi hoan doi. Lop GUI
 * {@link MailClientFrame} co trach nham khoa cac nut bam trong luc cho phan hoi.
 *
 * <p>Lop nay khong con man hinh console: giao dien nam trong {@link MailClientFrame},
 * con {@link #register}, {@link #login}, {@link #send}, {@link #logout} la API cho GUI goi.
 */
public class MailClient {

    /** Dia chi may chu mac dinh. */
    private static final String DEFAULT_HOST = "127.0.0.1";

    /** Cong may chu mac dinh (giong cong cua MailServer). */
    private static final int DEFAULT_PORT = 2346;

    /** Thoi gian cho toi da (ms) truoc khi bao khong phan hoi. */
    public static final int TIMEOUT_MS = 3000;

    /**
     * Thoi gian cho rieng cho lenh poll {@code LIST}.
     *
     * <p>Ngan thoi gian cho cua lenh poll phai nho hon {@link #TIMEOUT_MS}. Client
     * va may chu dung <b>chung mot socket</b>: mot request dang cho se giu khoa
     * {@link #request}, va thao tac cua nguoi dung phai xep hang sau no. Neu poll
     * cho 3 giay thi moi thao tac bam se treo 3 giay khi may chu da chet.
     * Poll nen that nhanh: het 800ms co nghia la "chua co phan hoi", bo qua luon.
     */
    public static final int POLL_TIMEOUT_MS = 800;

    private final String serverHost;
    private final int serverPort;
    private final InetAddress serverAddress;

    /** Socket dung de gui va nhan, tao 1 lan roi dung lai cho ca chuong trinh. */
    private DatagramSocket socket;

    /**
     * Tai khoan hien tai sau khi dang nhap thanh cong (null neu chua dang nhap).
     * <p>
     * <b>{@code volatile}</b>: duoc ghi tu luong {@code SwingWorker} (dang nhap,
     * dang xuat) va doc tu luong {@code mail-poll}, nen moi thay doi phai visible
     * ngay voi ca hai. Neu khong, client co the poll bang ten tai khoan cu.
     */
    private volatile String currentUser = null;

    /**
     * Danh sach file mail cua tai khoan hien tai.
     * <p>{@code volatile} vi cùng lý do: luong poll ghi, luong SwingWorker đọc.
     */
    private volatile List<String> currentMailList = new ArrayList<>();

    /**
     * Danh sach hop thu <b>da gui</b> ({@code sent}) cua tai khoan dang dang nhap.
     *
     * <p>Tach rieng tu {@link #currentMailList} vi day la hai danh sach doc lap —
     * cung ten file co the xuat hien o ca hai (gui cho chinh minh tao
     * {@code data/minh/mail_0007.txt} va {@code data/minh/sent/mail_0003.txt}).
     */
    private volatile List<String> currentSentList = new ArrayList<>();

    public MailClient(String serverHost, int serverPort) throws IOException {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.serverAddress = InetAddress.getByName(serverHost);
        // Client khong bind cong co dinh: DatagramSocket() se duoc OS cap cong ngau nhien
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(TIMEOUT_MS);
    }

    // ==================== API CHO GUI ====================

    /**
     * Yeu cau 1 — Tao account moi.
     *
     * @return {@code [status, message]}
     */
    public String[] register(String username, String password) throws IOException {
        return request(Protocol.OP_REGISTER
                + Protocol.FIELD_SEPARATOR + Protocol.escape(username.trim())
                + Protocol.FIELD_SEPARATOR + Protocol.escape(password));
    }

    /**
     * Yeu cau 3 — Dang nhap va nhan danh sach ten file trong hop thu.
     * <p>
     * Khi thanh cong, {@link #currentUser} va {@link #currentMailList} duoc cap nhat
     * de GUI hien thi; khi that bai thi xoa phien cu.
     *
     * @return {@code [status, message]}
     */
    public String[] login(String username, String password) throws IOException {
        String user = username.trim();
        String[] response = request(Protocol.OP_LOGIN
                + Protocol.FIELD_SEPARATOR + Protocol.escape(user)
                + Protocol.FIELD_SEPARATOR + Protocol.escape(password));

        if (Protocol.isOk(response[0])) {
            currentUser = user;
            currentMailList = new ArrayList<>(Protocol.parseFileList(response[1]));
            // Lay them hop thu da gui ngay luc dang nhap, de tab "Thu da gui"
            // khong bi rong trong khoang thoi gian cho vong poll dau tien chay.
            currentSentList = new ArrayList<>(fetchSentList(user));
        } else {
            currentUser = null;
            currentMailList = new ArrayList<>();
            currentSentList = new ArrayList<>();
        }
        return response;
    }

    /**
     * Yeu cau 2 — Gui email cho mot tai khoan khac.
     * <p>
     * Xu ly newline trong {@code body} thanh {@code <BR>} (Protocol.escape lam viec nay),
     * nen GUI co the dung JTextArea nhieu dong ma khong can tu tach tay.
     *
     * @return {@code [status, message]}
     */
    public String[] send(String from, String to, String subject, String body) throws IOException {
        return request(Protocol.OP_SEND
                + Protocol.FIELD_SEPARATOR + Protocol.escape(from.trim())
                + Protocol.FIELD_SEPARATOR + Protocol.escape(to.trim())
                + Protocol.FIELD_SEPARATOR + Protocol.escape(subject)
                + Protocol.FIELD_SEPARATOR + Protocol.escape(body));
    }

    /**
     * Ket thuc phien tren may chu.
     *
     * <p>Server khong luu trang thai phien nen lenh nay chi xac nhan. Phia client
     * <b>phai</b> xoá {@link #currentUser} va {@link #currentMailList}: neu giu lai
     * thi {@link #isLoggedIn()} van tra true, thanh trang thai cua GUI van hien
     * ten tai khoan da dang xuat, va vong poll van chay tiep.
     */
    public String[] logout() throws IOException {
        String[] response = request(Protocol.OP_LOGOUT);
        currentUser = null;
        currentMailList = new ArrayList<>();
        currentSentList = new ArrayList<>();
        return response;
    }

    /**
     * Mo rong (khong phai yeu cau de bai) — liet ke ten file trong hop thu.
     *
     * <p>Dung cho vong poll de client tu phat hien thu moi. Khong co mat khau: server
     * khong luu phien, xac thuc moi lan gui se phai gui lai mat khau theo tung
     * request. Xem ghi chu bao mat tai {@code Mailbox.readMail}.
     *
     * @param username tai khoan dang xem
     * @return {@code [status, message]}; message la danh sach ten file ngan cach bang {@code ~}
     */
    public String[] list(String username) throws IOException {
        String[] response = request(Protocol.OP_LIST
                + Protocol.FIELD_SEPARATOR + Protocol.escape(username.trim()),
                POLL_TIMEOUT_MS);
        if (Protocol.isOk(response[0])) {
            currentMailList = new ArrayList<>(Protocol.parseFileList(response[1]));
        }
        return response;
    }

    /**
     * Liet ke hop thu <b>da gui</b> (tuong duong {@code LIST|user|sent}).
     *
     * <p>Cung cap {@link #currentSentList} moi khi thanh cong — <b>dung he hon</b>
     * {@link #list(String)}: sau khi goi xong thi {@link #getCurrentSentList()} da
     * phan anh dung thu da gui vua co, dung de so sanh {@code before}/{@code after}
     * trong vong poll.
     */
    public String[] listSent(String username) throws IOException {
        String[] response = request(Protocol.OP_LIST
                + Protocol.FIELD_SEPARATOR + Protocol.escape(username.trim())
                + Protocol.FIELD_SEPARATOR + Mailbox.FOLDER_SENT,
                POLL_TIMEOUT_MS);
        if (Protocol.isOk(response[0])) {
            currentSentList = new ArrayList<>(Protocol.parseFileList(response[1]));
        }
        return response;
    }

    /**
     * Lay danh sach thu da gui, tra ve rong neu that bai.
     *
     * <p>Khong nem loi: danh sach sent khong quan trong bang danh sach hop thu den, nen
     * mot {@code LIST} that bai (hoac may phai server tam unavailable) khong duoc
     * lam {@link #login} that bai — nguoi dung van phai dang nhap duoc va xem thu den.
     */
    private List<String> fetchSentList(String username) {
        try {
            String[] response = listSent(username);
            if (Protocol.isOk(response[0])) {
                return Protocol.parseFileList(response[1]);
            }
        } catch (IOException ignored) {
            // Bo qua: xem ghi chu ben tren.
        }
        return Collections.emptyList();
    }

    /**
     * Ghi nhan file vua duoc giao cho chinh minh.
     *
     * <p><b>Vi sao can:</b> {@link MailClientFrame} them ten file vao danh sach hien thi
     * ngay khi server bao giao thanh cong, nhung {@link #currentMailList} — thu danh sach
     * vong poll so voi de so sanh phat hien thu moi — chua he bi cap nhat. Hai hau qua:
     *
     * <ol>
     *   <li>Vong poll ke tiep thay {@code after != before} va <b>dan dau chua doc</b> cho
     *       dung thu nguoi dung vua tu viet.</li>
     *   <li>Danh sach hien thi phai them o cuoi trong khi server tra ve ten da
     *       {@code Collections.sort}, nen sau mot giay thu bị <b>nhay cho</b>.</li>
     * </ol>
     *
     * <p>Gọi ham nay ngay sau khi gui thanh cong: danh sach phai bang <b>thu tu</b> ma
     * server tra ve, thi {@code before == after} va vong poll im lang.
     *
     * @param username tai khoan nguoi gui (truong hop gui cho chinh minh)
     * @param fileName ten file server vua tao, vi du {@code mail_0001.txt}
     */
    public void noteDelivered(String username, String fileName) {
        if (currentUser == null || fileName == null || fileName.isEmpty()) return;
        if (!currentUser.equals(username)) return;   // gui cho nguoi khac: khong lien quan
        List<String> list = new ArrayList<>(currentMailList);
        if (list.contains(fileName)) return;
        list.add(fileName);
        Collections.sort(list);   // cung thu tu voi Collections.sort cua Mailbox.listFiles
        currentMailList = list;
    }

    /**
     * Mo rong (khong phai yeu cau de bai) — lay noi dung 1 file thu de hien thi.
     *
     * @param username tai khoan so huu thu
     * @param fileName ten file trong hop thu (vi du {@code mail_0001.txt})
     * @return {@code [status, message]}; message la noi dung thu da giai ma
     */
    public String[] fetch(String username, String fileName) throws IOException {
        return request(Protocol.OP_FETCH
                + Protocol.FIELD_SEPARATOR + Protocol.escape(username.trim())
                + Protocol.FIELD_SEPARATOR + Protocol.escape(fileName));
    }

    /**
     * Doc thu o hop thu {@code sent} — tuong duong {@code FETCH|user|sent|filename}.
     *
     * @param username tai khoan so huu thu
     * @param fileName ten file trong thu muc {@code sent/}
     * @return {@code [status, message]}
     */
    public String[] fetchSent(String username, String fileName) throws IOException {
        return request(Protocol.OP_FETCH
                + Protocol.FIELD_SEPARATOR + Protocol.escape(username.trim())
                + Protocol.FIELD_SEPARATOR + Mailbox.FOLDER_SENT
                + Protocol.FIELD_SEPARATOR + Protocol.escape(fileName));
    }

    // ==================== TRUY VAN TRANG THAI (cho status bar cua GUI) ====================

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    /** @return "dia-chi-ip:cong" cua socket client */
    public String getLocalEndpoint() {
        return socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort();
    }

    public int getTimeoutMs() {
        return TIMEOUT_MS;
    }

    /** @return tai khoan dang dang nhap, hoac null */
    public String getCurrentUser() {
        return currentUser;
    }

    /** @return danh sach ten file nhan duoc o lan LOGIN gan nhat */
    public List<String> getCurrentMailList() {
        return currentMailList;
    }

    /** @return danh sach ten file o hop thu "da gui", rong neu chua dang nhap */
    public List<String> getCurrentSentList() {
        return currentSentList;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /** Dong socket. Ket thuc phien UDP. */
    public void close() {
        socket.close();
    }

    // ==================== LOP XU LY UDP ====================

    /**
     * Gui 1 request len may chu va cho response.
     *
     * <p><b>Quy trinh client:</b>
     * <ol>
     *   <li>{@code sendto()} - gui datagram den may chu</li>
     *   <li>{@code recvfrom()} - cho response (co timeout)</li>
     * </ol>
     * <p>
     * <b>Ham nay chan toi da {@link #TIMEOUT_MS} ms</b> nen KHONG duoc goi truc tiep
     * tren Event Dispatch Thread cua Swing — se lam treo toan bo giao dien. GUI phai
     * chay no trong {@code SwingWorker}.
     *
     * @param raw noi dung request (chua CRLF)
     * @return mang 2 phan tu {@code [status, message]}; status la {@code "TIMEOUT"}
     *         neu het thoi gian cho
     * @throws IOException loi I/O
     */
    public synchronized String[] request(String raw) throws IOException {
        return request(raw, TIMEOUT_MS);
    }

    /**
     * Gui 1 request len may chu va cho response,voi thoi gian cho tuy chon.
     *
     * @param raw noi dung request (chua CRLF)
     * @param timeoutMs thoi gian cho toi da (ms)
     * @return mang 2 phan tu {@code [status, message]}; status la {@code "TIMEOUT"}
     *         neu het thoi gian cho
     * @throws IOException loi I/O
     */
    public synchronized String[] request(String raw, int timeoutMs) throws IOException {
        byte[] out = (raw + Protocol.CRLF).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(out, out.length, serverAddress, serverPort));

        byte[] buffer = new byte[Protocol.BUFFER_SIZE];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        // Dat doi thoi gian cho ngay truoc khi receive, khong doi lai sau: neu doi
        // sau khi da co du lieu ve thi may co the doc nham sang request sau do.
        socket.setSoTimeout(timeoutMs);
        try {
            socket.receive(response);
        } catch (SocketTimeoutException e) {
            // UDP khong dam bao tin cay: neu may chu khong phan hoi thi bao loi
            // thay vi cho chuong trinh treo vo han.
            return new String[]{"TIMEOUT",
                    "Không phản hồi từ máy chủ sau " + timeoutMs
                            + " ms. Hãy kiểm tra server đã chạy chưa?"};
        }

        String reply = new String(response.getData(), 0, response.getLength(),
                StandardCharsets.UTF_8);
        return Protocol.parseResponse(reply);
    }
}