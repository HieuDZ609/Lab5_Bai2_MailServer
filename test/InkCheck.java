import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Kiem tra CHU THUC SU DUOC VE tren man hinh.
 *
 * <p><b>Vi sao can:</b> kich thuoc component khong chung minh duoc chu co hien.
 * Mot vien bo nho co the to de mat chu ma van giu nguyen kich thuoc dung — day
 * chinh la loai bug da gap o {@code FlatButton} va {@code FocusBorder}.
 *
 * <p><b>Cach do:</b> ve rieng component vao anh, chi dem pixel <b>khac mau nen o
 * phan noi</b>, bo qua 3px sats vien. Vien chi nam o mep nen khong anh huong; con
 * chu luon nam ben trong va tao pixel tuong phan manh. Component bi to nen se co
 * dung 0 pixel mau chu.
 *
 * <p>Chay: {@code InkCheck <FrameClass> <width> <height> [login]}
 */
public class InkCheck {

    private static final int INSET = 3;
    private static final int THRESH = 60;
    static int problems = 0;

    public static void main(String[] args) {
        // Luon System.exit o finally: mot JFrame dang hien giu AWT event thread
        // nen JVM khong tu dung neu chuong trinh nem loi gi do.
        try {
            run(args);
        } catch (Exception e) {
            problems++;
            System.out.println("!! LOI: " + e);
            e.printStackTrace(System.out);
        }
        System.exit(problems == 0 ? 0 : 1);
    }

    static void run(String[] a) throws Exception {
        String cls = a[0];
        int w = Integer.parseInt(a[1]);
        int h = Integer.parseInt(a[2]);
        boolean login = a.length > 3 && a[3].equals("login");

        System.out.println("Look and feel: "
                + UIManager.getLookAndFeel().getClass().getName());

        final javax.swing.JFrame[] box = {null};
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // giu LAF mac dinh
            }
            try {
                box[0] = (javax.swing.JFrame) Class.forName(cls)
                        .getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            box[0].setSize(w, h);
            box[0].setVisible(true);
        });
        Thread.sleep(1200);

        fillInitial(box[0]);
        Thread.sleep(400);

        checkLogo(box[0]);

        if (login) {
            int port = 24601;
            MailServer server = GuiHelper.startServer(port, "/tmp/ink_data");
            try {
                MailClient c = new MailClient("localhost", port);
                c.register("hung01", "matkhau123");
                c.send("nguoinhan", "hung01", "Chu de thu",
                        "Dong mot\nDong hai\nDong ba");
                SwingUtilities.invokeAndWait(() -> {
                    GuiHelper.setText(box[0], "hostField", "localhost");
                    GuiHelper.setText(box[0], "portField", String.valueOf(port));
                });
                GuiHelper.login((MailClientFrame) box[0], "hung01", "matkhau123", port);
                // Nap san noi dung tab Doc thu de kiem tra ca vung nhay.
                SwingUtilities.invokeAndWait(() -> {
                    GuiHelper.setLabel(box[0], "readFileName", "mail_0001.txt");
                    GuiHelper.setLabel(box[0], "readFrom", "hung01@mailserver.local");
                    GuiHelper.setLabel(box[0], "readTo", "hung01@mailserver.local");
                    GuiHelper.setLabel(box[0], "readSubject", "Chu de thu");
                    GuiHelper.setLabel(box[0], "readDate", "Sun, 04 Oct 2026 21:14:02 +0700");
                    GuiHelper.setLabel(box[0], "readSenderIp", "172.16.0.252");
                    GuiHelper.setLabel(box[0], "readReceiverIp", "192.168.1.55");
                    GuiHelper.setArea(box[0], "readBody", "Dong mot\nDong hai\nDong ba");
                });
                // Phai MO tab "Doc thu" thi moi kiem duoc chu trong do. Chi nap
                // noi dung ma khong chuyen tab thi moi component cua tab do chua
                // bao gio duoc ve, nen bao loi "chu khong hien" la vo dung.
                // Ban gui thu cho chinh minh de danh sach "Thu da gui" khong rong
                // — danh sach rong khong kiem duoc chu nao.
                c.send("hung01", "hung01", "Ban gui de kiem chu", "Dong A\nDong B");
                GuiHelper.clickTab(box[0], 3);
                Thread.sleep(700);
                System.out.println("  (da dang nhap + mo tab Doc thu: kiem ca 5 tab,"
                        + " gom 6 dong header va vung noi dung thu)");
            } finally {
                server.shutdown();
            }
        }

        // Chi component DANG HIEN moi co y nghia. Card cua 5 tab dung CardLayout
        // nen chi mot tab hien tai duoc ve — phai kiem tung tab rieng, neu khong
        // tab chua bao gio mo se khong bao gio duoc kiem.
        int checked = 0;
        if (box[0] instanceof MailClientFrame) {
            // Client co 5 tab. Chua dang nhap thi an 3 tab cuoi (Gui/Doc/Da gui).
            List<Integer> tabs = login ? List.of(1, 2, 3, 4) : List.of(0, 1, 2, 3);
            for (int tab : tabs) {
                if (tab > 0) {
                    if (!GuiHelper.tabVisible(box[0], tab)) continue;
                    GuiHelper.clickTab(box[0], tab);
                    Thread.sleep(500);
                }
                System.out.println("--- tab " + tab + ": " + TAB_NAMES[tab] + " ---");
                checked += checkTab(box[0], TAB_NAMES[tab]);
            }
        } else {
            // Khung may chu khong co tab: chi mot ve la du.
            System.out.println("--- khung may chu (khong co tab) ---");
            checked += checkTab(box[0], "May chu");
        }

        System.out.println("Da kiem tra " + checked + " component.");
        System.out.println(problems == 0
                ? "=> MOI CHU DEU THUC SU HIEN TREN MAN HINH"
                : "=> " + problems + " COMPONENT BI LO CHU");
    }

    /** Ten hien thi cua tung tab, dung de in ra khi bao loi. */
    private static final String[] TAB_NAMES = {
            "Dang ky", "Dang nhap", "Gui thu", "Doc thu", "Thu da gui"};

    /**
     * Kiem tra moi chu dang hien tren man hinh.
     *
     * @param frame khung client
     * @param tabName ten tab, chi de in ra thong bao
     * @return so component da kiem
     */
    static int checkTab(javax.swing.JFrame frame, String tabName) throws Exception {
        final List<Component> all = new ArrayList<>();
        SwingUtilities.invokeAndWait(() ->
                GuiHelper.collect(frame.getContentPane(), all));

        int checked = 0;
        for (Component c : all) {
            if (c.getWidth() <= INSET * 2 || c.getHeight() <= INSET * 2) continue;
            String what = describe(c);
            if (what == null) continue;
            checked++;

            int ink = countInteriorInk(c);
            String verdict;
            if (ink == 0) {
                verdict = "!! 0 pixel - CHI BI TO MAU, CHU KHONG HIEN";
                problems++;
            } else if (ink < 12) {
                verdict = "!! rat mo (" + ink + " pixel)";
                problems++;
            } else {
                verdict = "ok (" + ink + " pixel)";
            }
            System.out.printf("  %-44s %4dx%-4d %s%s%n", what, c.getWidth(),
                    c.getHeight(), verdict, c.isShowing() ? "" : "  [an]");
        }
        return checked;
    }

    /**
     * Logo phai co that trong khung, dung kich thuoc, va that su ve duoc pixel.
     *
     * <p>Khong co ham nay thi thieu {@code assets/vku-logo.png} van cho InkCheck
     * bao xanh: khung bo trong logo, {@code describe} tra {@code null} cho nhan
     * rong nen khong component nao bi dem, va anh khong ve thi dau co bien loi de
     * bao. Day la dang "test xanh gia" ma chinh InkCheck sinh ra de chong.
     */
    static void checkLogo(javax.swing.JFrame frame) throws Exception {
        System.out.println("--- logo truong ---");
        if (LogoAssets.master() == null) {
            System.out.println("  !! khong nap duoc " + LogoAssets.FILE
                    + " — can chay ./test/run.sh hoac cp -r assets build/");
            problems++;
            return;
        }

        final List<Component> all = new ArrayList<>();
        SwingUtilities.invokeAndWait(() ->
                GuiHelper.collect(frame.getContentPane(), all));

        JLabel logo = null;
        for (Component c : all) {
            if (c instanceof JLabel l && l.getIcon() != null) {
                logo = l;
                break;
            }
        }
        if (logo == null) {
            System.out.println("  !! khung khong co nhan logo nao");
            problems++;
            return;
        }

        int ih = logo.getIcon().getIconHeight();
        if (ih != Theme.LOGO_H) {
            System.out.println("  !! chieu cao logo " + ih
                    + "px, mong doi " + Theme.LOGO_H + "px");
            problems++;
            return;
        }

        int ink = countInteriorInk(logo);
        if (ink < 12) {
            System.out.println("  !! logo " + ink
                    + " pixel — CHI BI TO MAU, ANH KHONG HIEN");
            problems++;
            return;
        }
        System.out.printf("  ok logo %dx%d, %d pixel mau%n",
                logo.getIcon().getIconWidth(), ih, ink);
    }

    /** Dien san cac truong o client; server frame khong co truong nen bo qua. */
    static void fillInitial(javax.swing.JFrame f) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GuiHelper.setText(f, "hostField", "localhost");
            GuiHelper.setText(f, "portField", "2346");
            GuiHelper.setText(f, "regUserField", "hung01");
            GuiHelper.setText(f, "regPassField", "matkhau123");
            GuiHelper.setText(f, "logUserField", "hung01");
            GuiHelper.setText(f, "logPassField", "matkhau123");
            GuiHelper.setText(f, "fromField", "hung01");
            GuiHelper.setText(f, "toField", "nguoinhan");
            GuiHelper.setText(f, "subjectField", "Bai tap Lab 5");
            GuiHelper.setArea(f, "bodyArea",
                    "Chao ban, day la noi dung thu.\nDong thu hai.");
        });
    }

    /** Dem pixel tuong phan manh o phan noi cua component. */
    static int countInteriorInk(Component c) {
        int w = c.getWidth(), h = c.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Theme.PAPER);
        g.fillRect(0, 0, w, h);
        c.paint(g);
        g.dispose();

        Color fill = fillColor(c);
        int ink = 0;
        for (int y = INSET; y < h - INSET; y++) {
            for (int x = INSET; x < w - INSET; x++) {
                if (dist(img.getRGB(x, y), fill) > THRESH) ink++;
            }
        }
        return ink;
    }

    static Color fillColor(Component c) {
        if (c instanceof Theme.FlatButton b) return buttonFill(b);
        if (c instanceof javax.swing.text.JTextComponent) return Theme.SUNKEN;
        if (c.isOpaque() && c.getBackground() != null) return c.getBackground();
        return Theme.PAPER;
    }

    static Color buttonFill(Theme.FlatButton b) {
        try {
            java.lang.reflect.Field f = Theme.FlatButton.class
                    .getDeclaredField("baseFill");
            f.setAccessible(true);
            return (Color) f.get(b);
        } catch (Exception e) {
            return Theme.SURFACE;
        }
    }

    static int dist(int rgb, Color ref) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Math.abs(r - ref.getRed()) + Math.abs(g - ref.getGreen())
                + Math.abs(b - ref.getBlue());
    }

    static String describe(Component c) {
        if (c instanceof Theme.FlatButton b) {
            return "nut \"" + GuiHelper.buttonText(b) + "\"";
        }
        // Nut con mat (doi hien/an mat khau): chi co icon, khong chu — phai dem
        // pixel de xac nhan icon hien ra, khong bao gio de describe lai bo qua.
        if (c instanceof javax.swing.AbstractButton b
                && b.getIcon() != null && strip(b.getText()).isEmpty()) {
            return "nut con mat (icon " + b.getIcon().getIconWidth() + "x"
                    + b.getIcon().getIconHeight() + ")";
        }
        if (c instanceof javax.swing.JPasswordField p) {
            return "truong mat khau (" + p.getEchoChar() + "x"
                    + p.getPassword().length + ")";
        }
        if (c instanceof JLabel l) {
            String t = strip(l.getText());
            if (t.isEmpty() && l.getIcon() != null) {
                return "logo truong (" + l.getIcon().getIconWidth() + "x"
                        + l.getIcon().getIconHeight() + ")";
            }
            return t.isEmpty() ? null : "nhan \"" + trunc(t) + "\"";
        }
        if (c instanceof javax.swing.JTextField f) {
            return "truong nhap \"" + (f.getText().isEmpty() ? "<rong>" : f.getText()) + "\"";
        }
        if (c instanceof javax.swing.text.JTextComponent t) {
            String s = strip(t.getText());
            return s.isEmpty() ? null : "vung nhap \"" + trunc(s) + "\"";
        }
        return null;
    }

    static String strip(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "").trim();
    }

    static String trunc(String s) {
        return s.length() > 20 ? s.substring(0, 20) + "..." : s;
    }
}