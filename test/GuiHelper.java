import java.awt.Component;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Tien ich dung chung cho bo kiem thu GUI.
 *
 * <p>Gom phan "dua {@link MailClientFrame} vao trang thai da dang nhap" va cac
 * phep doc field/cay component bang reflection, de {@code E2E}, {@code InkCheck}
 * va {@code GeoCheck} khong phai viet lai.
 */
final class GuiHelper {

    private GuiHelper() { }

    /** Mo mot {@link MailServer} tren cong rieng. */
    static MailServer startServer(int port, String dataDir) throws Exception {
        MailServer s = new MailServer(port, dataDir, m -> { });
        s.start();
        Thread.sleep(300);
        return s;
    }

    /** Tao mot client da dang ky san mot tai khoan. */
    static MailClient registered(int port, String user, String pass)
            throws Exception {
        MailClient c = new MailClient("localhost", port);
        c.register(user, pass);
        return c;
    }

    /**
     * Dien du lieu, bam {@code Ket noi}, bam nut dang nhap, cho den khi GUI vao phien.
     *
     * <p>Chay tren EDT nhu nguoi dung that: bam nut qua {@code doClick()}.
     */
    static void login(javax.swing.JFrame frame, String user, String pass, int port)
            throws Exception {
        MailClientFrame f = (MailClientFrame) frame;
        SwingUtilities.invokeAndWait(() -> {
            setText(f, "hostField", "localhost");
            setText(f, "portField", String.valueOf(port));
            setText(f, "logUserField", user);
            setText(f, "logPassField", pass);
        });

        SwingUtilities.invokeAndWait(button(f, "connectButton")::doClick);
        long end = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < end && !connected(f)) Thread.sleep(60);
        if (!connected(f)) throw new IllegalStateException("khong ket noi duoc");

        Theme.FlatButton login = find(f.getContentPane(), "V\u00e0o h\u1ed9p th\u01b0");
        if (login == null) throw new IllegalStateException("khong thay nut dang nhap");
        SwingUtilities.invokeAndWait(login::doClick);

        end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end && !visible(f, "mailboxCard")) {
            Thread.sleep(60);
        }
        if (!visible(f, "mailboxCard")) throw new IllegalStateException("khong vao duoc phien");
    }

    // ==================== doc trang thai Swing ====================
    // Luu y: isVisible()/setText() deu phai hoi tren EDT, neu hoi tu thread khac
    // se thay trang thai truoc khi SwingWorker.done() chay xong.

    static boolean visible(javax.swing.JFrame frame, String field) throws Exception {
        return onEdt(() -> ((Component) field(frame, field)).isVisible());
    }

    static boolean connected(javax.swing.JFrame frame) throws Exception {
        return onEdt(() -> field(frame, "client") != null);
    }

    static String text(javax.swing.JFrame frame, String field) throws Exception {
        return onEdt(() -> rawText(field(frame, field)));
    }

    static int visibleTabs(javax.swing.JFrame frame) throws Exception {
        return onEdt(() -> {
            int n = 0;
            for (Component b : tabButtons((MailClientFrame) frame)) if (b.isVisible()) n++;
            return n;
        });
    }

    /** Tab dang xem, doc tu field {@code currentTab} cua app. */
    static int currentTab(javax.swing.JFrame frame) throws Exception {
        return onEdt(() -> ((Integer) field(frame, "currentTab")));
    }

    /**
     * Bam vao mot thu trong danh sach -> app tu chuyen sang tab "Doc thu".
     *
     * <p>De trong {@code GuiHelper} (khong phai trong {@code E2E}) vi ca {@code GeoCheck}
     * va {@code InkCheck} deu can mo thu thật. Truoc day hai bo kiem do chi dang nhap
     * roi dung lai, nen **tab "Doc thu" chua bao gio duoc do hinh hoc** — chinh vi vay
     * loi hai dong IP bi de chong len o cua so hay the noi dung bi keo nhan khong ai
     * bat duoc.
     */
    static void openMail(javax.swing.JFrame frame, String name) throws Exception {
        selectInList((javax.swing.JList<String>) field(frame, "mailboxList"), name);
    }

    /**
     * Bam vao 1 thu trong danh sach <b>"thu da gui"</b> (tab so 4).
     *
     * <p>Khac {@link #openMail} o cho danh sach nam o tab rieng, nen dung
     * {@code sentList} chu khong phai {@code mailboxList}.
     *
     * @param frame khung client
     * @param name  ten file can mo
     */
    static void openSentMail(javax.swing.JFrame frame, String name) throws Exception {
        selectInList((javax.swing.JList<String>) field(frame, "sentList"), name);
    }

    private static void selectInList(javax.swing.JList<String> list, String name)
            throws Exception {
        onEdt(() -> {
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (name.equals(list.getModel().getElementAt(i))) {
                    list.setSelectedIndex(i);
                    list.ensureIndexIsVisible(i);
                    return null;
                }
            }
            return null;
        });
    }

    /** Chay mot phep tinh co the nem loi tren EDT. */
    static <T> T onEdt(ThrowingSupplier<T> body) throws Exception {
        Object[] out = {null};
        RuntimeException[] err = {null};
        SwingUtilities.invokeAndWait(() -> {
            try {
                out[0] = body.get();
            } catch (Exception e) {
                err[0] = new RuntimeException(e);
            }
        });
        if (err[0] != null) throw err[0];
        @SuppressWarnings("unchecked")
        T r = (T) out[0];
        return r;
    }

    interface ThrowingSupplier<T> { T get() throws Exception; }

    interface Cond { boolean test() throws Exception; }

    /** Cho den khi dieu kien dung, hoac het thoi gian. */
    static boolean waitUntil(Cond cond, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            try {
                if (cond.test()) return true;
            } catch (Exception ignored) {
                // component chua san sang - thu lai
            }
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }

    /** Cho den khi gia tri tra ve bang {@code want}, tra ve gia tri cuoi cung. */
    static int waitUntilValue(ThrowingSupplier<Integer> get, int want, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        int last = -1;
        while (System.currentTimeMillis() < end) {
            try {
                last = get.get();
                if (last == want) return last;
            } catch (Exception ignored) {
                // chua san sang
            }
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                break;
            }
        }
        return last;
    }

    /** Cho den khi gia tri tra ve bang {@code want}, tra ve gia tri cuoi cung. */
    static String waitUntilValue(ThrowingSupplier<String> get, String want,
            long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        String last = "";
        while (System.currentTimeMillis() < end) {
            try {
                last = get.get();
                if (want.equals(last)) return last;
            } catch (Exception ignored) {
                // chua san sang
            }
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                break;
            }
        }
        return last;
    }

    // ==================== tim / bam ====================

    /**
     * Dien mot truong neu khung co no.
     *
     * <p>Khong nem loi khi field khong ton tai: {@code MailServerFrame} khong co
     * {@code hostField}/{@code logUserField}... nen cong cu nay dung chung cho ca
     * hai loai khung. Field ton tai nhung sai kieu thi moi la loi that.
     */
    static void setText(javax.swing.JFrame frame, String field, String v) {
        Object o = maybe(frame, field);
        if (o instanceof JPasswordField p) p.setText(v);
        else if (o instanceof JTextField t) t.setText(v);
        else if (o != null) throw new IllegalArgumentException(field + " khong phai o nhap");
    }

    static void setLabel(javax.swing.JFrame frame, String field, String v) {
        Object o = maybe(frame, field);
        if (o instanceof javax.swing.JLabel l) l.setText(v);
        else if (o != null) throw new IllegalArgumentException(field + " khong phai nhan");
    }

    static void setArea(javax.swing.JFrame frame, String field, String v) {
        Object o = maybe(frame, field);
        if (o instanceof javax.swing.JTextArea t) t.setText(v);
        else if (o != null) throw new IllegalArgumentException(field + " khong phai vung nhap");
    }

    /** Field neu co, {@code null} neu khung nay khong co field do. */
    static Object maybe(javax.swing.JFrame frame, String field) {
        try {
            return field(frame, field);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Theme.FlatButton button(javax.swing.JFrame frame, String field) {
        try {
            return (Theme.FlatButton) field(frame, field);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Bam nut theo chu hien thi. Nut cua tab la bien cuc bo, khong phai field. */
    static void clickText(javax.swing.JFrame frame, String caption) throws Exception {
        Theme.FlatButton b = find(frame.getContentPane(), caption);
        if (b == null) throw new IllegalStateException("khong tim thay nut \"" + caption + "\"");
        SwingUtilities.invokeAndWait(b::doClick);
    }

    static Theme.FlatButton find(Component c, String caption) {
        if (c instanceof Theme.FlatButton b && caption.equals(buttonText(b))) return b;
        if (c instanceof java.awt.Container ct) {
            for (Component k : ct.getComponents()) {
                Theme.FlatButton r = find(k, caption);
                if (r != null) return r;
            }
        }
        return null;
    }

    static String buttonText(Theme.FlatButton b) {
        try {
            Field fd = Theme.FlatButton.class.getDeclaredField("text");
            fd.setAccessible(true);
            return String.valueOf(fd.get(b));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== reflection ====================

    static Object field(Object o, String name)
            throws IllegalAccessException, NoSuchFieldException {
        Field fd = o.getClass().getDeclaredField(name);
        fd.setAccessible(true);
        return fd.get(o);
    }

    @SuppressWarnings("unchecked")
    /**
     * Bam nut chuyen tab theo chi so (0=Dang ky, 1=Dang nhap, 2=Gui thu, 3=Doc thu,
     * 4=Thu da gui) — khop voi hang so TAB_* cua {@code MailClientFrame}.
     *
     * <p>Dung {@code doClick()} — cung duong di voi nguoi dung that, dung cach goi
     * {@code selectTab()} noi bo se bo qua hien ung phu cua viec chuyen tab.
     */
    static void clickTab(javax.swing.JFrame frame, int index) throws Exception {
        Component b = onEdt(() -> {
            List<Component> bs = tabButtons((MailClientFrame) frame);
            return index >= 0 && index < bs.size() ? bs.get(index) : null;
        });
        if (b == null) throw new IllegalStateException("Khong co nut tab " + index);
        SwingUtilities.invokeAndWait(((Theme.FlatButton) b)::doClick);
    }

    /**
     * @param frame khung client
     * @param index chi so tab
     * @return {@code true} neu nut cua tab dang hien (chi hien khi da dang nhap)
     */
    static boolean tabVisible(javax.swing.JFrame frame, int index) throws Exception {
        return onEdt(() -> {
            List<Component> bs = tabButtons((MailClientFrame) frame);
            return index >= 0 && index < bs.size() && bs.get(index).isVisible();
        });
    }

    static List<Component> tabButtons(MailClientFrame f)
            throws IllegalAccessException, NoSuchFieldException {
        return (List<Component>) field(f, "tabButtons");
    }

    @SuppressWarnings("unchecked")
    static javax.swing.DefaultListModel<String> mailboxModel(MailClientFrame f)
            throws IllegalAccessException, NoSuchFieldException {
        return (javax.swing.DefaultListModel<String>) field(f, "mailboxModel");
    }

    /**
     * @return danh sach ten thu theo thu tu hien thi tren man hinh
     *
     * <p>Phai doc <b>tren EDT</b>: {@code DefaultListModel} bi Swing doi truc tiep khi
     * poll them thu, nen doc tu thread test co the gap mot muc hoac thay
     * {@code IndexOutOfBounds}.
     */
    static List<String> mailboxNames(javax.swing.JFrame frame) throws Exception {
        return onEdt(() -> {
            List<String> out = new ArrayList<>();
            javax.swing.DefaultListModel<String> m = mailboxModel((MailClientFrame) frame);
            for (int i = 0; i < m.size(); i++) out.add(m.get(i));
            return out;
        });
    }

    /** @return danh sach ten thu da gui theo thu tu hien thi tren man hinh */
    static List<String> sentNames(javax.swing.JFrame frame) throws Exception {
        return onEdt(() -> {
            List<String> out = new ArrayList<>();
            javax.swing.DefaultListModel<String> m =
                    (javax.swing.DefaultListModel<String>) field(frame, "sentModel");
            for (int i = 0; i < m.size(); i++) out.add(m.get(i));
            return out;
        });
    }

    static String rawText(Object o) {
        if (o instanceof javax.swing.text.JTextComponent t) return t.getText();
        if (o instanceof javax.swing.JLabel l) return l.getText().replaceAll("<[^>]*>", "");
        return "";
    }

    static void collect(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof java.awt.Container ct) {
            for (Component k : ct.getComponents()) collect(k, out);
        }
    }

    /** @return ten thu dang duoc chon trong danh sach, hoac null */
    static String selectedMail(javax.swing.JFrame f) throws Exception {
        @SuppressWarnings("unchecked")
        JList<String> l = (JList<String>) field(f, "mailboxList");
        return onEdt(() -> {
            String v = l.getSelectedValue();
            return v == null ? "" : v;
        });
    }

    /**
     * Kiem tra dau "●" cua 1 dong trong danh sach.
     *
     * <p>Phai ve that qua {@code getCellRenderer(...)}: chi doc {@code getText()} cua
     * renderer se cho ra chuoi rong, vi renderer chi doi mau chu khi Swing that su
     * ve dong do.
     */
    static boolean unreadDot(javax.swing.JFrame f, String name) throws Exception {
        @SuppressWarnings("unchecked")
        JList<String> l = (JList<String>) field(f, "mailboxList");
        return onEdt(() -> {
            for (int i = 0; i < l.getModel().getSize(); i++) {
                if (!name.equals(l.getModel().getElementAt(i))) continue;
                java.awt.Component c = l.getCellRenderer()
                        .getListCellRendererComponent(l, l.getModel().getElementAt(i),
                                i, false, false);
                return c instanceof JLabel
                        && ((JLabel) c).getText().startsWith("\u25cf");
            }
            return false;
        });
    }

}
