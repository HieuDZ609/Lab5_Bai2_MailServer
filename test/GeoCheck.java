import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Kiem tra HINH HOC: text co bi cat/ke do ra khong, va component co chong
 * lan nhau khong.
 *
 * <p>Chi do kich thuoc component la khong du - phai so voi kich thuoc text that
 * su can de ve. Swing tu tinh {@code preferredSize} cua minh nen dung chinh no
 * lam chuan, khong tu tinh lai bang FontMetrics (se lech 2-4px do padding va bo
 * qua so do font that su).
 *
 * <p>Luu y: {@code FlowLayout} co the lam component <b>xuong dong</b> ma khong
 * chong lan - loai loi nay cong cu nay khong bat duoc. Phai kiem tra truc tiep
 * toa do cac nut trong cung mot hang.
 *
 * <p>Chay: {@code GeoCheck <FrameClass> <width> <height> [login]}
 */
public class GeoCheck {

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
        boolean login = a.length > 3 && a[3].equals("login");

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
            box[0].setSize(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            box[0].setVisible(true);
        });
        Thread.sleep(1200);

        if (login) {
            int port = 24602;
            MailServer server = GuiHelper.startServer(port, "/tmp/geo_data");
            try {
                MailClient c = new MailClient("localhost", port);
                c.register("hung01", "matkhau123");
                SwingUtilities.invokeAndWait(() -> {
                    GuiHelper.setText(box[0], "hostField", "localhost");
                    GuiHelper.setText(box[0], "portField", String.valueOf(port));
                });
                GuiHelper.login((MailClientFrame) box[0], "hung01", "matkhau123", port);
                // MO THU THAT: chi mo moi danh sach thi tab "Doc thu" chua duoc
                // sap xep (validate) nen component cua no khong co to do that ->
                // moi loi hinh hoc trong tab nay deu bi bo qua.
                c.send("hung01", "hung01", "Kiem tra hinh hoc", "Dong 1\nDong 2");
                MailClient other = new MailClient("localhost", port);
                other.send("minh", "hung01", "Thu den giua luc doc", "Khach mo");
                MailClientFrame mf = (MailClientFrame) box[0];
                // Danh sach trong GUI do vong poll cua app quyet dinh, khong phai
                // client cua bo test, nen phai CHO no xuat hien chu khong sleep co
                // dinh (neu khong, danh sach rong -> openMail(null) -> NPE).
                final String[] newest = {null};
                GuiHelper.waitUntil(() -> {
                    newest[0] = null;
                    for (String n : GuiHelper.mailboxNames(mf)) {
                        if (n.startsWith("mail_")) newest[0] = n;
                    }
                    return newest[0] != null;
                }, 10000);
                if (newest[0] == null) {
                    throw new IllegalStateException("Khong thay thu nao trong hop thu");
                }
                GuiHelper.openMail(box[0], newest[0]);
                Thread.sleep(600);

                // Mo ca hop thu "da gui" roi sang tab "Doc thu" tu hop thu do:
                // tab moi co the bi keo hep / tran chu ma khong ai do hoac.
                c.send("hung01", "hung01", "Kiem tra hop thu da gui", "Dong A\nDong B");
                GuiHelper.waitUntil(() -> {
                    return GuiHelper.sentNames((MailClientFrame) box[0]).stream()
                            .anyMatch(n -> n.startsWith("mail_"));
                }, 10000);
                java.util.List<String> sentList =
                        GuiHelper.sentNames((MailClientFrame) box[0]);
                String sentNewest = sentList.stream()
                        .filter(n -> n.startsWith("mail_"))
                        .reduce((x, y) -> y).orElse(null);
                if (sentNewest != null) {
                    GuiHelper.clickTab(box[0], 4);
                    GuiHelper.openSentMail(box[0], sentNewest);
                    Thread.sleep(600);
                }
                System.out.println("  (da dang nhap, mo thu " + newest[0]
                        + " va ban gui " + sentNewest
                        + ": kiem ca 5 tab, gom ca 6 dong header cua tab Doc thu)");
            } finally {
                server.shutdown();
            }
        }

        final List<Component> all = new ArrayList<>();
        SwingUtilities.invokeAndWait(() ->
                GuiHelper.collect(box[0].getContentPane(), all));

        System.out.println("--- 1. Text co bi cat hay ke do khong ---");
        for (Component c : all) {
            if (!(c instanceof JComponent jc) || !jc.isShowing()) continue;
            // Chi kiem tra nhan va nut. O nhap bi hep hon preferred size van OK
            // (chu cuon theo chieu ngang), con nhan/nut bi hep thi chu bi cat.
            if (!(jc instanceof javax.swing.JLabel) && !(jc instanceof Theme.FlatButton)) {
                continue;
            }
            String t = textOf(jc);
            if (t == null || t.isEmpty()) continue;

            Dimension need = jc.getPreferredSize();
            int dw = need.width - jc.getWidth();
            int dh = need.height - jc.getHeight();
            if (dw > 1 || dh > 1) {
                System.out.printf("  !! %-40s can %dx%d, co %dx%d -> cat/ked %dx%d%n",
                        trunc(t), need.width, need.height, jc.getWidth(),
                        jc.getHeight(), dw, dh);
                problems++;
            }
        }
        if (problems == 0) System.out.println("  ok - khong co text nao bi cat");

        int before = problems;
        System.out.println("--- 2. Component co chong lan nhau khong ---");
        for (Component c : all) {
            if (!(c instanceof Container ct)) continue;
            List<Component> kids = new ArrayList<>();
            for (Component k : ct.getComponents()) if (k.isShowing()) kids.add(k);
            for (int i = 0; i < kids.size(); i++) {
                for (int j = i + 1; j < kids.size(); j++) {
                    Rectangle r1 = new Rectangle(kids.get(i).getX(), kids.get(i).getY(),
                            kids.get(i).getWidth(), kids.get(i).getHeight());
                    Rectangle r2 = new Rectangle(kids.get(j).getX(), kids.get(j).getY(),
                            kids.get(j).getWidth(), kids.get(j).getHeight());
                    Rectangle is = r1.intersection(r2);
                    if (is.width > 1 && is.height > 1) {
                        System.out.printf("  !! %s <-> %s chong %dx%d%n",
                                name(kids.get(i)), name(kids.get(j)),
                                is.width, is.height);
                        problems++;
                    }
                }
            }
        }
        if (problems == before) {
            System.out.println("  ok - khong co component nao chong lan");
        }

        System.out.println(problems == 0
                ? "=> HINH HOC SAN" : "=> " + problems + " LOI");
    }

    static String textOf(JComponent c) {
        if (c instanceof javax.swing.text.JTextComponent t) {
            return t instanceof javax.swing.JPasswordField ? null : t.getText();
        }
        if (c instanceof javax.swing.JLabel l) return l.getText();
        if (c instanceof Theme.FlatButton) return GuiHelper.buttonText((Theme.FlatButton) c);
        return null;
    }

    static String name(Component c) {
        String t = c instanceof JComponent ? textOf((JComponent) c) : null;
        return c.getClass().getSimpleName()
                + (t == null || t.isEmpty() ? "" : " \"" + trunc(t) + "\"");
    }

    static String trunc(String s) {
        s = s.replaceAll("<[^>]*>", "");
        return s.length() > 26 ? s.substring(0, 26) + "..." : s;
    }
}