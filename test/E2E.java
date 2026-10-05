import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Component;
import java.util.List;
import javax.swing.JList;
import javax.swing.SwingUtilities;

/**
 * Kiem thich hop toan dien: protocol + realtime + co giac cua GUI.
 *
 * <p>Chay server trong cung JVM va tao that {@link MailClientFrame}, bam nut qua
 * {@code FlatButton.doClick()} — dung duong di cua nguoi dung, khong goi ham loi
 * cua tang giao dien.
 */
public class E2E {

    static final int PORT = 24567;
    static final Path DATA = Path.of("/tmp/e2e_data");

    static int pass = 0;
    static int fail = 0;

    public static void main(String[] args) throws Exception {
        deleteTree(DATA);
        Files.createDirectories(DATA);

        MailServer server = GuiHelper.startServer(PORT, DATA.toString());
        try {
            protocolChecks();
            sentMailboxChecks();
            securityChecks();
            logoutChecks();
            guiChecks(server);
            selfSendChecks(server);
            sentGuiChecks();
            sanityChecks();
        } finally {
            server.shutdown();
        }

        System.out.println("\n================ KET QUA ================");
        System.out.println("  PASS: " + pass + "   FAIL: " + fail);
        System.out.println("=========================================");
        System.exit(fail == 0 ? 0 : 1);
    }

    static MailClient a;

    // ==================== A. REGISTER / LOGIN ====================

    static void protocolChecks() throws Exception {
        section("A. REGISTER / LOGIN");
        a = new MailClient("localhost", PORT);
        check("REGISTER hung01 -> 200", "200", a.register("hung01", "matkhau123")[0]);
        check("REGISTER trung ten -> 409", "409", a.register("hung01", "matkhau123")[0]);
        check("REGISTER ten sai dinh dang -> 400", "400", a.register("a", "x")[0]);
        check("REGISTER sinh new_email.txt", true,
                Files.exists(DATA.resolve("hung01/new_email.txt")));
        check("REGISTER sinh thu muc rieng", true,
                Files.isDirectory(DATA.resolve("hung01")));
        check("LOGIN sai mat khau -> 401", "401", a.login("hung01", "sai")[0]);
        check("LOGIN tai khoan khong ton tai -> 404", "404", a.login("khongco", "x")[0]);
        check("LOGIN dung mat khau -> 200", "200", a.login("hung01", "matkhau123")[0]);
        check("LOGIN tra danh sach ten file", true,
                a.getCurrentMailList().contains("new_email.txt"));

        section("B. LIST");
        check("LIST -> 200", "200", a.list("hung01")[0]);
        check("LIST chua new_email.txt", true,
                a.getCurrentMailList().contains("new_email.txt"));
        check("LIST tai khoan khong ton tai -> 404", "404", a.list("khongco")[0]);
        check("LIST ten sai -> 400", "400", a.list("a b")[0]);

        section("C. FETCH");
        String[] f1 = a.fetch("hung01", "new_email.txt");
        check("FETCH new_email.txt -> 200", "200", f1[0]);
        check("FETCH tra ve header Subject:", true, f1[1].contains("Subject:"));
        check("FETCH tra ve header From:", true,
                f1[1].contains("From: system@mailserver.local"));
        check("FETCH tra ve header To:", true,
                f1[1].contains("To: hung01@mailserver.local"));
        check("FETCH tra ve header Date:", true, f1[1].contains("Date: "));
        check("FETCH file khong ton tai -> 404", "404",
                a.fetch("hung01", "khong_co.txt")[0]);
        check("FETCH path traversal bi chan -> 400", "400",
                a.fetch("hung01", "../../accounts.dat")[0]);
        check("FETCH duong dan tuy doi -> 400", "400",
                a.fetch("hung01", "/etc/passwd")[0]);
        check("FETCH tai khoan khong ton tai -> 404", "404",
                a.fetch("khongco", "new_email.txt")[0]);

        section("D. SEND + danh sach cap nhat");
        check("SEND cho chinh minh -> 200", "200",
                a.send("hung01", "hung01", "Chao ban", "Dong 1\nDong 2\nDong 3")[0]);
        check("SEND tao file mail_0001.txt", true,
                Files.exists(DATA.resolve("hung01/mail_0001.txt")));
        // Phai LIST lai sau SEND: danh sach chi doi khi co lenh goi moi cap nhat.
        check("LIST thay doi -> co mail_0001.txt", true,
                a.list("hung01") != null
                        && a.getCurrentMailList().contains("mail_0001.txt"));

        String[] f2 = a.fetch("hung01", "mail_0001.txt");
        check("FETCH mail_0001.txt -> 200", "200", f2[0]);
        check("FETCH giu xuong dong that (>=3 dong)", true,
                f2[1].split("\n", -1).length >= 4);
        check("FETCH khong bien xuong dong thanh <BR>", false, f2[1].contains("<BR>"));
        check("FETCH giu dau phan cach nguyen", false, f2[1].contains("\\|"));
        check("FETCH noi dung doc duoc qua escape/unescape", true,
                f2[1].contains("Dong 2") && f2[1].contains("Subject: Chao ban"));

        section("E. Realtime giua 2 client");
        check("REGISTER nguoinhan -> 200", "200",
                a.register("nguoinhan", "matkhau456")[0]);
        MailClient b = new MailClient("localhost", PORT);
        check("Client B LOGIN -> 200", "200", b.login("nguoinhan", "matkhau456")[0]);
        int before = b.getCurrentMailList().size();

        long t0 = System.currentTimeMillis();
        check("Client A SEND cho B -> 200", "200",
                a.send("hung01", "nguoinhan", "Thu gap", "Cam on ban")[0]);
        boolean arrived = GuiHelper.waitUntil(() ->
                b.list("nguoinhan") != null
                        && b.getCurrentMailList().size() > before, 4000);
        check("Client B thay thu moi qua LIST (trong "
                + (System.currentTimeMillis() - t0) + " ms)", true, arrived);
        check("Client B thay dung ten file moi", true,
                b.getCurrentMailList().contains("mail_0001.txt"));
        check("Client B FETCH duoc noi dung thu nhan duoc", true,
                b.fetch("nguoinhan", "mail_0001.txt")[1].contains("Cam on ban"));
    }

    // ==================== F2. HOP THU "DA GUI" ====================

    /**
     * Kiem tra hop thu "da gui" phia nguoi gui.
     *
     * <p>Yeu cau cua nguoi dung: truoc day nguoi gui gui thu xong thi khong xem
     * duoc thu do o dau ca, vi thu chi duoc ghi vao thu muc nguoi nhan. Nay
     * kiem tra lai ca duong: server luu ban gui, protocol doc duoc, va ban gui
     * <b>khong</b> bi ghi them IP nguoi nhan (vi khong ai "doc" ban ghi lai).
     */
    static void sentMailboxChecks() throws Exception {
        section("L. Hop thu 'da gui' phia nguoi gui");

        MailClient minh = new MailClient("localhost", PORT);
        MailClient nhu = new MailClient("localhost", PORT);
        check("REGISTER minh -> 200", "200", minh.register("minh", "matkhau123")[0]);
        check("REGISTER nhu -> 200", "200", nhu.register("nhu", "matkhau123")[0]);

        // Thoi diem nao cung duoc: hop thu "da gui" cua tai khoan moi tao van rong.
        check("Hop thu 'da gui' tao san khi dang ky", true,
                Files.isDirectory(DATA.resolve("minh/sent")));
        check("Hop thu 'da gui' moi tao rong", true,
                minh.request("LIST|minh|sent", 1000)[1].isEmpty());

        check("LOGIN minh -> 200", "200", minh.login("minh", "matkhau123")[0]);
        // Client tu lay danh sach "da gui" ngay khi dang nhap, khong can bam them.
        check("Dang nhap xong da co san danh sach 'da gui'", true,
                minh.getCurrentSentList() != null);
        check("Dang nhap: hop thu den va 'da gui' tach biet", false,
                minh.getCurrentMailList().contains("sent"));

        check("SEND minh -> nhu -> 200", "200",
                minh.send("minh", "nhu", "Bai tap 5", "Viet bao cao")[0]);

        // Ben nhan van chi nhan duoc ten file inbox, khong doi response.
        // [0] la ma, [1] moi la thong diep co ten file.
        check("SEND tra ve ten file inbox cua nguoi nhan", true,
                minh.send("minh", "nhu", "Thu 2", "Noi dung 2")[1]
                        .contains("Delivered to 'nhu'"));

        // Ban gui phai nam trong data/<nguoi gui>/sent/, khong phai thu muc goc.
        check("Ban gui luu trong sent/ cua nguoi gui", true,
                Files.exists(DATA.resolve("minh/sent/mail_0001.txt")));
        check("Ban gui khong lam nhieu thu muc goc", false,
                Files.exists(DATA.resolve("minh/mail_0001.txt")));

        check("LIST|minh|sent -> 200", "200", minh.listSent("minh")[0]);
        check("LIST|sent thay 2 ban gui", true,
                minh.getCurrentSentList().contains("mail_0001.txt")
                        && minh.getCurrentSentList().contains("mail_0002.txt"));
        check("LIST|sent khong lo file tam", false,
                minh.getCurrentSentList().contains(".pending.txt"));
        check("LIST|sent khong tron thu muc con", false,
                minh.getCurrentSentList().contains("sent"));

        // Doc ban gui: dung lenh FETCH co folder.
        String[] sf = minh.fetchSent("minh", "mail_0001.txt");
        check("FETCH|minh|sent|mail_0001.txt -> 200", "200", sf[0]);
        check("Ban gui giu dung tieu de", true, sf[1].contains("Subject: Bai tap 5"));
        check("Ban gui giu dung nguoi nhan", true,
                sf[1].contains("To: nhu@mailserver.local"));
        check("Ban gui co Sender-IP", true, sf[1].contains("Sender-IP: "));
        // Ban ghi lai phia nguoi gui: chua co ai doc no theo nghia "den" thi
        // khong duoc gan IP nguoi nhan. Neu gan, client se hieu nham rang
        // "minh" da doc thu cua chinh minh.
        check("Ban gui KHONG co Receiver-IP", false, sf[1].contains("Receiver-IP:"));
        check("Doc ban gui khong them Receiver-IP vao file", false,
                Files.readString(DATA.resolve("minh/sent/mail_0001.txt"))
                        .contains("Receiver-IP:"));

        // Hop thu den cua nguoi nhan van dung nhu cu.
        check("Nguoi nhan doc duoc thu nhan duoc", "200",
                nhu.fetch("nhu", "mail_0001.txt")[0]);

        // Tu gui: phai co HAI ban, mot ben moi hop thu. So thu moi do server
        // quyet dinh nen so sanh chenh lech danh sach, khong doan ten file.
        minh.list("minh");
        minh.listSent("minh");
        java.util.List<String> inboxBefore = new java.util.ArrayList<>(minh.getCurrentMailList());
        java.util.List<String> sentBefore = new java.util.ArrayList<>(minh.getCurrentSentList());

        check("SEND cho chinh minh -> 200", "200",
                minh.send("minh", "minh", "Tu gui", "Dong 1\nDong 2")[0]);
        minh.list("minh");
        minh.listSent("minh");

        java.util.List<String> inboxNew = diff(minh.getCurrentMailList(), inboxBefore);
        java.util.List<String> sentNew = diff(minh.getCurrentSentList(), sentBefore);
        check("Tu gui: hop thu den co dung 1 ban moi", 1, inboxNew.size());
        check("Tu gui: hop thu 'da gui' co dung 1 ban moi", 1, sentNew.size());
        check("Tu gui: ban moi ben hop thu den ton tai that", true,
                Files.exists(DATA.resolve("minh").resolve(inboxNew.get(0))));
        check("Tu gui: ban moi ben 'da gui' ton tai that", true,
                Files.exists(DATA.resolve("minh/sent").resolve(sentNew.get(0))));
        // Hai ban cung noi dung nhung o hai file khac nhau, dung nhu Gmail.
        check("Tu gui: hai ban la hai file khac nhau", false,
                DATA.resolve("minh").resolve(inboxNew.get(0))
                        .equals(DATA.resolve("minh/sent").resolve(sentNew.get(0))));

        // Hop thu "da gui" ton tai truoc tinh nang nay: LIST rong, khong loi.
        // Tai khoan tao tu truoc khi co tinh nang nay: khong co thu muc sent/.
        // LIST|sent phai tra ve danh sach rong chu khong loi.
        Files.createDirectories(DATA.resolve("cuo"));
        MailClient old = new MailClient("localhost", PORT);
        check("Tai khoan tao truoc (chua co sent/) khong loi", "200",
                old.request("LIST|cuo|sent", 1000)[0]);
        check("Tai khoan tao truoc: danh sach 'da gui' rong", true,
                old.getCurrentSentList().isEmpty());

        // Hop thu khong hop le: phai bi chan, khong duoc doc file ngoai data/.
        check("LIST|folder sai -> 400", "400",
                minh.request("LIST|minh|bogus", 1000)[0]);
        check("LIST|folder la duong dan -> 400", "400",
                minh.request("LIST|minh|..%2f..", 1000)[0]);
        check("FETCH|folder sai -> 400", "400",
                minh.request("FETCH|minh|bogus|mail_0001.txt", 1000)[0]);
        check("LIST|folder hop le 'inbox' van dung", "200",
                minh.request("LIST|minh|inbox", 1000)[0]);

        // "sent" la ten hop thu nen khong duoc lam ten tai khoan.
        check("REGISTER ten 'sent' bi tu choi", "400",
                minh.register("sent", "matkhau123")[0]);

        // SEND khong xac thuc nguoi gui nen "from" co the gia mao. Khong duoc
        // de thu muc do bien thanh tai khoan that.
        MailClient spoof = new MailClient("localhost", PORT);
        nhu.list("nhu");
        int nhuBefore = nhu.getCurrentMailList().size();
        check("SEND voi from gia mao van giao cho nguoi nhan -> 200", "200",
                spoof.send("khongco", "nhu", "Gia mao", "Body")[0]);
        check("Gia mao: khong tao thu muc tai khoan gia", false,
                Files.exists(DATA.resolve("khongco")));
        nhu.list("nhu");
        check("Gia mao: thu van den duoc nguoi nhan", nhuBefore + 1,
                nhu.getCurrentMailList().size());

        // LOGOUT phai xoa ca hai danh sach.
        minh.logout();
        check("LOGOUT xoa danh sach 'da gui'", true,
                minh.getCurrentSentList().isEmpty());
    }

    // ==================== F. BAO MAT ====================

    static void securityChecks() throws Exception {
        section("F. Bao mat / chong gia mao");
        // Tai khoan "ghost" co thu muc nhung khong co dong trong accounts.dat
        Files.createDirectories(DATA.resolve("ghost"));
        check("Tai khoan thieu hash bi tu choi", "401",
                a.login("ghost", "batkymatkhau")[0]);
        check("Khong doc duoc thu tai khoan ngoai danh sach", "404",
                a.fetch("khongco", "new_email.txt")[0]);
    }

    // ==================== G. LOGOUT ====================

    static void logoutChecks() throws Exception {
        section("G. LOGOUT");
        MailClient b = new MailClient("localhost", PORT);
        b.login("nguoinhan", "matkhau456");
        check("LOGOUT -> 200", "200", b.logout()[0]);
        check("LOGOUT xoa phien (isLoggedIn = false)", false, b.isLoggedIn());
        check("LOGOUT xoa danh sach thu", true, b.getCurrentMailList().isEmpty());
    }

    // ==================== H. GUI ====================

    static void guiChecks(MailServer server) throws Exception {
        section("H. Co giac cua GUI");

        MailClientFrame f = new MailClientFrame();
        SwingUtilities.invokeAndWait(() -> f.setVisible(true));

        // Chua ket noi / chua dang nhap
        check("Chua cong: chi 2 tab Dang ky + Dang nhap", 2, visibleTabs(f));
        check("Chua dang nhap: an card hop thu", false, visible(f, "mailboxCard"));
        check("Chua dang nhap: an nut Dang xuat", false, visible(f, "logoutButton"));

        // Sai mat khau: loi phai o tab DANG NHAP
        SwingUtilities.invokeAndWait(() -> {
            GuiHelper.setText(f, "hostField", "localhost");
            GuiHelper.setText(f, "portField", String.valueOf(PORT));
            GuiHelper.setText(f, "logUserField", "hung01");
            GuiHelper.setText(f, "logPassField", "sai-mat-khau");
        });
        SwingUtilities.invokeAndWait(GuiHelper.button(f, "connectButton")::doClick);
        check("Ket noi xong", true, GuiHelper.waitUntil(() -> GuiHelper.connected(f), 3000));

        GuiHelper.clickText(f, "V\u00e0o h\u1ed9p th\u01b0");
        check("Sai mat khau: khong treo", true,
                GuiHelper.waitUntil(() -> !busy(f), 5000));
        check("Sai mat khau: bao loi o tab Dang nhap", true,
                GuiHelper.text(f, "logResult").contains("sai"));
        check("Sai mat khau: van chua dang nhap (2 tab)", 2, visibleTabs(f));
        check("Sai mat khau: khong lo card hop thu", false, visible(f, "mailboxCard"));

        // Dang nhap dung
        SwingUtilities.invokeAndWait(() ->
                GuiHelper.setText(f, "logPassField", "matkhau123"));
        GuiHelper.clickText(f, "V\u00e0o h\u1ed9p th\u01b0");
        // isLoggedIn() duoc gan o thread nen, LEN TRUOC khi done() tren EDT cap
        // nhat giao dien. Cho bang chinh trang thai UI cuoi cung.
        check("Dang nhap xong", true, GuiHelper.waitUntil(() ->
                GuiHelper.connected(f)
                        && visible(f, "mailboxCard")
                        && GuiHelper.currentTab(f) == 2
                        && !GuiHelper.text(f, "fromField").isEmpty(), 5000));
        check("Dang nhap xong: hien 5 tab", 5, visibleTabs(f));
        check("Dang nhap xong: hien card hop thu", true, visible(f, "mailboxCard"));
        check("Dang nhap xong: hien nut Dang xuat", true, visible(f, "logoutButton"));
        check("Dang nhap xong: chuyen sang tab Gui thu", 2, GuiHelper.currentTab(f));
        check("Dang nhap xong: o nhan 'hung01' duoc dien", "hung01",
                GuiHelper.text(f, "fromField"));
        check("Dang nhap xong: danh sach thu co new_email.txt", true,
                GuiHelper.mailboxNames(f).contains("new_email.txt"));

        // REALTIME: thu tu client khac, khong can bam gi
        MailClient other = new MailClient("localhost", PORT);
        other.send("nguoinhan", "hung01", "Tu nguoi khac",
                "Dong 1 cap nhat tu client khac\nDong 2");
        check("REALTIME: thu moi tu client khac tu xuat hien", true,
                GuiHelper.waitUntil(() ->
                        GuiHelper.mailboxNames(f).contains("mail_0002.txt"), 5000));

        // Bam thu -> tab Doc thu
        SwingUtilities.invokeAndWait(() -> {
            JList<String> list = mailboxList(f);
            int i = indexOf(list, "mail_0002.txt");
            list.setSelectedIndex(i);
            list.ensureIndexIsVisible(i);
        });
        check("Bam thu: sang tab Doc thu", 3,
                GuiHelper.waitUntilValue(() -> GuiHelper.currentTab(f), 3, 5000));
        check("Bam thu: hien dung tieu de", "Tu nguoi khac",
                GuiHelper.waitUntilValue(() -> GuiHelper.text(f, "readSubject"),
                        "Tu nguoi khac", 5000));
        check("Bam thu: hien dung From", true, GuiHelper.waitUntil(() ->
                GuiHelper.text(f, "readFrom").contains("nguoinhan"), 5000));
        check("Bam thu: hien dung noi dung", true, GuiHelper.waitUntil(() ->
                GuiHelper.text(f, "readBody").contains("Dong 1 cap nhat"), 5000));
        check("Bam thu: giu xuong dong that", true, GuiHelper.waitUntil(() ->
                GuiHelper.text(f, "readBody").contains("Dong 2"), 5000));

        // Dang xuat
        SwingUtilities.invokeAndWait(GuiHelper.button(f, "logoutButton")::doClick);
        check("Dang xuat xong", true, GuiHelper.waitUntil(() ->
                !loggedIn(f) && !visible(f, "logoutButton"), 5000));
        check("Dang xuat: an nut Dang xuat", false, visible(f, "logoutButton"));
        check("Dang xuat: an card hop thu", false, visible(f, "mailboxCard"));
        check("Dang xuat: chi con 2 tab", 2, visibleTabs(f));
        check("Dang xuat: quay ve tab Dang nhap", 1, GuiHelper.currentTab(f));
        check("Dang xuat: xoa danh sach thu", true,
                GuiHelper.mailboxNames(f).isEmpty());
        check("Dang xuat: xoa noi dung thu dang xem", "",
                GuiHelper.text(f, "readSubject"));
        check("Dang xuat: client bao het phien", false, loggedIn(f));
        check("Sau dang xuat: chi 2 nut tab", 2, visibleTabs(f));
    }

    /**
     * K. Nguoi gui xem duoc thu chinh minh vua gui + thu den giua luc dang doc.
     *
     * <p>Mot loi nguoi dung that: "gui cho chinh minh khong xem duoc thu do", va mot
     * loi tinh vi: thu vua gui bi dan dau chua doc roi nhay cho sau mot giay. Hai
     * thu nay cung mot nguyen nhan — danh sach client phai duoc cap nhat ngay khi
     * server giao file.
     */
    static void selfSendChecks(MailServer server) throws Exception {
        section("K. Tu gui thu cho chinh minh + thu den giua luc dang doc");

        MailClientFrame f = new MailClientFrame();
        SwingUtilities.invokeAndWait(() -> f.setVisible(true));
        GuiHelper.login(f, "hung01", "matkhau123", PORT);

        MailClient probe = new MailClient("localhost", PORT);

        // Thu do may chu tu tao (thu chao mung) khong co dong IP -> hien "(thu cu)"
        // thay vi hien so 0.0.0.0 hay chu rong.
        selectMail(f, "new_email.txt");
        check("Thu khong co IP: sang tab Doc thu", true,
                GuiHelper.waitUntil(() -> GuiHelper.currentTab(f) == 3, 5000));
        check("Thu server tao: khong co Sender-IP -> hien '(thu cu)'", "(thư cũ)",
                GuiHelper.text(f, "readSenderIp"));
        // Nguoc lai, IP nguoi nhan lai BIET: thu chao mung da duoc doc trong
        // chinh lan nay nen server da ghi Receiver-IP vao file.
        check("Thu server tao: da ghi Receiver-IP khi doc", true,
                mailFileHas("hung01", "new_email.txt", "Receiver-IP:"));
        check("Thu server tao: IP nguoi nhan hien gia tri thật", "127.0.0.1",
                GuiHelper.text(f, "readReceiverIp"));

        java.util.List<String> before = GuiHelper.mailboxNames(f);
        String sentName = "";   // xac dinh tu chinh danh sach, khong doan theo so luu

        // Dien "Nguoi nhan" = chinh minh roi bam nut that.
        SwingUtilities.invokeAndWait(() -> {
            GuiHelper.setText(f, "toField", "hung01");
            GuiHelper.setText(f, "subjectField", "Thu tu gui cho chinh minh");
            GuiHelper.setArea(f, "bodyArea", "Dong 1\nDong 2");
        });
        GuiHelper.clickText(f, "Gửi thư đi");
        check("Gui cho chinh minh: co phan hoi 200", true,
                GuiHelper.waitUntil(() -> GuiHelper.text(f, "sendResult")
                        .startsWith("200"), 5000));

        java.util.List<String> after = GuiHelper.mailboxNames(f);
        check("Thu vua gui xuat hien ngay tren danh sach",
                before.size() + 1, after.size());
        // Khong duoc doan ten file: hop thu con chua "new_email.txt" nen
        // "mail_000N" khong suy ra duoc tu so phan tu.
        sentName = after.stream().filter(x -> !before.contains(x))
                .findFirst().orElse("");
        check("Ten thu moi dung dinh dang mail_NNNN.txt", true,
                sentName.matches("mail_\\d{4}\\.txt"));
        check("Danh sach dung thu tu server (khong nhay cho)",
                serverOrder(probe, "hung01"), String.valueOf(after));
        check("Thu nguoi dung vua gui KHONG bi danh chua doc",
                false, GuiHelper.unreadDot(f, sentName));

        // Cho 2 vong poll: danh sach phi dung y nguyen.
        Thread.sleep(2500);
        check("Sau 2 vong poll: danh sach khong doi", String.valueOf(after),
                String.valueOf(GuiHelper.mailboxNames(f)));
        check("Sau 2 vong poll: van khong danh chua doc",
                false, GuiHelper.unreadDot(f, sentName));

        // Bam thi thu vua gui.
        selectMail(f, sentName);
        // Phai CHO: FETCH chay trong SwingWorker, "select tab Doc thu" chi chay o
        // done() tren EDT. Do ngay se luon ra tab cu.
        check("Bam thu vua gui: sang tab Doc thu", true,
                GuiHelper.waitUntil(() -> GuiHelper.currentTab(f) == 3, 5000));
        check("Bam thu vua gui: tieu de dung", true,
                GuiHelper.waitUntil(() -> GuiHelper.text(f, "readSubject")
                        .contains("chinh minh"), 5000));
        check("Bam thu vua gui: body nhieu dong giu newline that",
                true, GuiHelper.text(f, "readBody").contains("Dong 1\nDong 2"));
        check("Bam thu vua gui: danh dau chua doc tat",
                false, GuiHelper.unreadDot(f, sentName));

        // Hai dong IP: server ghi vao file thu, GUI hien len o tab Doc thu.
        check("File thu co dong Sender-IP (may chu tu ghi)", true,
                mailFileHas("hung01", sentName, "Sender-IP:"));
        check("File thu co dong Receiver-IP (ghi khi doc)", true,
                mailFileHas("hung01", sentName, "Receiver-IP:"));
        check("File thu: dong IP nam trong header, khong roi vao body", true,
                mailBodyIsClean("hung01", sentName, "Dong 1\nDong 2"));
        check("Tab Doc thu hien IP nguoi gui", "127.0.0.1",
                GuiHelper.text(f, "readSenderIp"));
        check("Tab Doc thu hien IP nguoi nhan", "127.0.0.1",
                GuiHelper.text(f, "readReceiverIp"));

        // May khac gui thu moi DUNG luc dang doc thu: poll phai them thu vao danh
        // sach nhung khong duoc lam mau noi dung dang xem.
        String[] r = probe.send("nguoi_ta", "hung01",
                "Thu den giua luc dang doc", "Xin chao");
        check("May khac gui thu cho hung01", "200", r[0]);
        check("Poll nhan duoc thu moi", true, GuiHelper.waitUntil(
                () -> GuiHelper.mailboxNames(f).size() == after.size() + 1, 5000));
        check("Poll khong lam mau noi dung dang xem", "Thu tu gui cho chinh minh",
                GuiHelper.text(f, "readSubject"));
        check("Poll khim dung thu dang chon", sentName, GuiHelper.selectedMail(f));
        check("Poll giu nguyen tab Doc thu", 3, GuiHelper.currentTab(f));
        check("Danh sach van dung thu tu server", serverOrder(probe, "hung01"),
                String.valueOf(GuiHelper.mailboxNames(f)));

        // Bam thu moi (chua doc) phai duoc danh dau.
        final String justSent = sentName;
        String newest = GuiHelper.mailboxNames(f).stream()
                .filter(x -> !before.contains(x) && !x.equals(justSent))
                .findFirst().orElse("");
        check("Thu moi den co dau chua doc", true,
                !newest.isEmpty() && GuiHelper.unreadDot(f, newest));

        SwingUtilities.invokeAndWait(GuiHelper.button(f, "logoutButton")::doClick);
        check("Ket thuc K: dang xuat xong", true, GuiHelper.waitUntil(
                () -> !loggedIn(f), 5000));
        // KHONG System.exit o day: phai chay tiep sang sanityChecks() va in
        // bang tong ket qua cua ca bo E2E.
    }

    /**
     * Thu tu server tra ve — dung mot client "hoi tham" rieng de lay.
     *
     * <p>Khong goi truc tiep vao {@code MailServer}: server khong phai lo tra ve danh
     * sach (chi co {@code handleList}), va doi chieu bang chinh danh sach cua GUI thi
     * moi dang tin. Client nay chi goi LIST nen khong dung dau chon cua frame.
     */
    /** @return noi dung file thu tren dia, dung de kiem tra header server ghi */
    static String mailFile(String user, String name) throws Exception {
        return Files.readString(DATA.resolve(user).resolve(name));
    }

    static boolean mailFileHas(String user, String name, String line) throws Exception {
        return mailFile(user, name).contains(line);
    }

    /** @return true neu phan body dung nguyen, khong bi lan dong header vao */
    static boolean mailBodyIsClean(String user, String name, String expectedBody)
            throws Exception {
        String c = mailFile(user, name).replace("\r\n", "\n");
        int split = c.indexOf("\n\n");
        return split > 0 && c.substring(split + 2).stripTrailing().equals(expectedBody);
    }

    static String serverOrder(MailClient probe, String user) throws Exception {
        return String.valueOf(Protocol.parseFileList(probe.list(user)[1]));
    }

    static void selectMail(MailClientFrame f, String name) throws Exception {
        GuiHelper.openMail(f, name);
    }

    static int visibleTabs(javax.swing.JFrame f) throws Exception {
        return GuiHelper.visibleTabs(f);
    }

    static boolean visible(javax.swing.JFrame f, String field) throws Exception {
        return GuiHelper.visible(f, field);
    }

    static boolean loggedIn(MailClientFrame f) throws Exception {
        return GuiHelper.onEdt(() -> {
            MailClient c = (MailClient) GuiHelper.field(f, "client");
            return c != null && c.isLoggedIn();
        });
    }

    static boolean busy(MailClientFrame f) throws Exception {
        return GuiHelper.onEdt(() -> (Boolean) GuiHelper.field(f, "busy"));
    }

    @SuppressWarnings("unchecked")
    static JList<String> mailboxList(MailClientFrame f) {
        try {
            return (JList<String>) GuiHelper.field(f, "mailboxList");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int indexOf(JList<String> list, String name) {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (name.equals(list.getModel().getElementAt(i))) return i;
        }
        return -1;
    }

    // ==================== I. SERVER ====================

    // ==================== M. TAB "THU DA GUI" TRONG GUI ====================

    /**
     * Kiem tra tab "Thu da gui" cua GUI: hien danh sach ban gui, bam duoc vao
     * thu, sang tab "Doc thu", va hien dung hop thu.
     */
    static void sentGuiChecks() throws Exception {
        section("M. Tab 'Thu da gui' trong GUI");

        // GuiHelper.login chi dang nhap, khong tao tai khoan — phai tao truoc.
        MailClient owner = new MailClient("localhost", PORT);
        check("REGISTER nguoigui -> 200", "200", owner.register("nguoigui", "matkhau789")[0]);
        MailClient friend = new MailClient("localhost", PORT);
        check("REGISTER banthan -> 200", "200", friend.register("banthan", "matkhau789")[0]);

        MailClientFrame f = new MailClientFrame();
        SwingUtilities.invokeAndWait(() -> f.setVisible(true));
        GuiHelper.login(f, "nguoigui", "matkhau789", PORT);

        check("Sau dang nhap: co nut tab 'Thu da gui'", true,
                GuiHelper.waitUntil(() -> {
                    try {
                        List<Component> bs = GuiHelper.tabButtons(f);
                        // Theme.FlatButton tu ve chu (khong phai JButton nen khong
                        // goi duoc getText()) -> phai dung helper doc qua reflection.
                        return bs.size() > 4 && bs.get(4).isVisible()
                                && String.valueOf(GuiHelper.buttonText(
                                        (Theme.FlatButton) bs.get(4)))
                                        .contains("Thư đã gửi");
                    } catch (Exception e) {
                        return false;
                    }
                }, 5000));
        check("Sau dang nhap: hop thu den rong (khong thua muc con)", false,
                GuiHelper.mailboxNames(f).contains("sent"));
        check("Sau dang nhap: danh sach 'da gui' rong", true,
                GuiHelper.sentNames(f).isEmpty());

        // Gui mot thu cho nguoi khac: ban gui phai xuat hien o tab rieng.
        List<String> inboxBeforeSend = GuiHelper.mailboxNames(f);
        SwingUtilities.invokeAndWait(() -> {
            GuiHelper.setText(f, "toField", "banthan");
            GuiHelper.setText(f, "subjectField", "Bai tap 5");
            GuiHelper.setArea(f, "bodyArea", "Xin chao ban");
        });
        GuiHelper.clickText(f, "Gửi thư đi");
        check("Gui cho nguoi khac: co phan hoi 200", true,
                GuiHelper.waitUntil(() ->
                        GuiHelper.text(f, "sendResult").startsWith("200"), 5000));
        check("Ban gui xuat hien o tab 'Thu da gui'", true,
                GuiHelper.waitUntil(() -> GuiHelper.sentNames(f).size() == 1, 6000));
        // Hop thu den phai Y NGUYEN: ban gui khong duoc do vao hop thu den.
        check("Ban gui KHONG xuat hien o hop thu den", inboxBeforeSend,
                GuiHelper.mailboxNames(f));

        // Bam vao ban gui -> sang tab "Doc thu" va nhan hop thu phai la "da gui".
        GuiHelper.clickTab(f, 4);
        check("Bam nut tab 4: sang tab Thu da gui", 4, GuiHelper.currentTab(f));
        String sentFile = GuiHelper.sentNames(f).get(0);
        GuiHelper.openSentMail(f, sentFile);
        check("Bam ban gui: sang tab Doc thu", true,
                GuiHelper.waitUntil(() -> GuiHelper.currentTab(f) == 3, 5000));
        check("Bam ban gui: hien dung tieu de", "Bai tap 5",
                GuiHelper.text(f, "readSubject"));
        check("Bam ban gui: hien dung nguoi nhan", true,
                GuiHelper.text(f, "readTo").contains("banthan"));
        check("Bam ban gui: nhan hop thu la 'thu da gui'", "thư đã gửi",
                GuiHelper.text(f, "readFolderLabel"));
        check("Bam ban gui: co Sender-IP", true,
                GuiHelper.text(f, "readSenderIp").startsWith("127."));

        // Quay lai hop thu den: nhan hop thu doi sang "hop thu den".
        GuiHelper.clickTab(f, 4);
        GuiHelper.openSentMail(f, sentFile);
        selectMail(f, "new_email.txt");
        check("Mo thu o hop thu den: nhan hop thu doi lai", true,
                GuiHelper.waitUntil(() -> "hộp thư đến".equals(
                        GuiHelper.text(f, "readFolderLabel")), 5000));

        // Xoa phien: danh sach "da gui" phai bi xoa theo.
        SwingUtilities.invokeAndWait(GuiHelper.button(f, "logoutButton")::doClick);
        check("Dang xuat: danh sach 'da gui' duoc xoa", true,
                GuiHelper.waitUntil(() -> !loggedIn(f)
                        && GuiHelper.sentNames(f).isEmpty(), 5000));
    }

    static void sanityChecks() throws Exception {
        section("I. Server van con chay");
        check("Server con phan hoi sau lenh loi", "200", a.list("hung01")[0]);
    }

    // ==================== TIEN ICH ====================

    /**
     * Tra ve cac phan tu co trong {@code after} nhung khong co trong {@code before}.
     *
     * <p>Dung de biet "file moi" ma khong phai doan ten file theo so thu tu.
     *
     * @param after  danh sach moi nhat tu server
     * @param before danh sach truoc do
     * @return cac phan tu chi xuat hien trong {@code after}
     */
    static java.util.List<String> diff(java.util.List<String> after,
                                       java.util.List<String> before) {
        java.util.List<String> fresh = new java.util.ArrayList<>(after);
        fresh.removeAll(before);
        return fresh;
    }

    static void section(String name) {
        System.out.println("\n" + name);
        System.out.println("-".repeat(Math.max(24, name.length())));
    }

    static void check(String name, Object want, Object got) {
        boolean ok = String.valueOf(want).equals(String.valueOf(got));
        if (ok) pass++;
        else fail++;
        System.out.printf("  %s  %-52s %s%n", ok ? "PASS" : "FAIL", name,
                ok ? "" : "  <- can '" + want + "', nhan '" + got + "'");
    }

    static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                    // thu lai o lan sau
                }
            });
        }
    }
}