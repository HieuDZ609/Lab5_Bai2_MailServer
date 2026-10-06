import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;

/**
 * Theme.java - he token cho toan bo giao dien Swing cua bai Lab 5.
 *
 * <p><b>Quy hoac thiet ke:</b>
 * <ul>
 *   <li><b>2 ho gia dinh chu</b>: {@code Lato} cho moi thu, {@code JetBrains Mono}
 *       cho vung nhat ky. Ca hai deu duoc kiem tra phu day dau tieng Viet truoc khi
 *       dung, neu thieu thi tu lap xuong font thay the.</li>
 *   <li><b>1 mau nhan duy nhat</b>: xanh nhuc sam {@link #ACCENT}. Tat ca phan con lai
 *       la mau trung tinh - khong tien, khong gradient, khong mau neon.</li>
 *   <li><b>Mau trung tinh phai am</b>: nen giong giay an ({@link #PAPER}), chu khong
 *       phai den tuyet doi ({@link #INK}), duong kẻ hairline 1px ({@link #LINE}).</li>
 *   <li><b>Bo goc 2 gia tri</b>: {@link #R_SMALL} cho o nhap/chip,
 *       {@link #R_MEDIUM} cho panel va nut.</li>
 *   <li><b>Luoi 4pt</b>: moi hang/bien deu la boi so cua 4.</li>
 * </ul>
 *
 * <p>Swing khong ho tro bo goc hay chuyen mau hover san, nen lop nay tu ve bang
 * {@link RoundedBorder} va {@link FlatButton}.
 */
public final class Theme {

    private Theme() {
    }

    // ==================== MAU ====================

    /** Nen cua cua so - giay an, khong phai trang thuan tuy. */
    public static final Color PAPER = new Color(0xF7, 0xF4, 0xEF);

    /** Be mat noi dung (panel, the) - sang hon nen mot chut. */
    public static final Color SURFACE = new Color(0xFF, 0xFD, 0xF9);

    /** Be mat lui xuong (o nhap, vung log) - toi hon nen. */
    public static final Color SUNKEN = new Color(0xEF, 0xEA, 0xE1);

    /** Duong ke hairline. */
    public static final Color LINE = new Color(0xE4, 0xDC, 0xD1);

    /** Duong ke manh hon, dung cho vien o nhap khi focus. */
    public static final Color LINE_STRONG = new Color(0xC7, 0xBB, 0xA8);

    /** Chu chinh - den off, pha nau. */
    public static final Color INK = new Color(0x22, 0x1F, 0x1A);

    /** Chu thu cap. */
    public static final Color INK_2 = new Color(0x53, 0x4B, 0x41);

    /** Nhan phu, chu thong tin. */
    public static final Color INK_3 = new Color(0x8A, 0x80, 0x73);

    /** Mau nhan duy nhat cua toan bo giao dien. */
    public static final Color ACCENT = new Color(0x14, 0x65, 0x5A);

    /** Bien the cua mau nhan khi hover. */
    public static final Color ACCENT_HOVER = new Color(0x0E, 0x50, 0x47);

    /** Bien the cua mau nhan khi nhan (xoay xuong 1px). */
    public static final Color ACCENT_PRESS = new Color(0x0A, 0x3D, 0x36);

    /** Be mat nhuot nhat trong mau nhan (dang chon, dong hoat). */
    public static final Color ACCENT_WASH = new Color(0xE2, 0xEE, 0xEB);

    // ==================== MAU NGAN NGHIA ====================

    public static final Color OK = new Color(0x2E, 0x6B, 0x3E);
    public static final Color OK_WASH = new Color(0xE6, 0xEE, 0xE6);
    public static final Color WARN = new Color(0x8F, 0x5C, 0x0E);
    public static final Color WARN_WASH = new Color(0xF6, 0xEE, 0xDC);
    public static final Color ERR = new Color(0x9E, 0x35, 0x2A);
    public static final Color ERR_WASH = new Color(0xF7, 0xE8, 0xE5);
    public static final Color IDLE = new Color(0x9A, 0x90, 0x83);
    public static final Color IDLE_WASH = new Color(0xEB, 0xE6, 0xDD);

    /** Mau phu thuoc ma tra loi cua server. */
    public static Color forStatus(String status) {
        if (Protocol.isOk(status)) {
            return OK;
        }
        return "TIMEOUT".equals(status) ? WARN : ERR;
    }

    public static Color washForStatus(String status) {
        if (Protocol.isOk(status)) {
            return OK_WASH;
        }
        return "TIMEOUT".equals(status) ? WARN_WASH : ERR_WASH;
    }

    // ==================== CHU ====================

    private static final String FONT_UI = pick("Lato", "Noto Sans", "Segoe UI", "Dialog");
    private static final String FONT_MONO = pick("JetBrains Mono", "Noto Sans Mono",
            "Liberation Mono", "Monospaced");

    /** Chu 12 moi ky tu - dung de kiem tra font co day du dau tieng Viet. */
    private static final String VIETNAMESE_PROBE =
            "ăâêôđĂÂÊÔĐăạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ";

    /**
     * Chon font dau tien trong danh sach co that su ve duoc dau tieng Viet.
     * <p>
     * Rat quan trong: nhieu font mono pho bien (DejaVu Sans Mono, Ubuntu Mono)
     * <b>khong</b> co dau tieng Viet, nen dung may se hien o vuong. Thay vi tin
     * ten font, ta kiem tra truc tiep bang {@link Font#canDisplayUpTo}.
     */
    private static String pick(String... candidates) {
        Set<String> available = new HashSet<>();
        for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            available.add(name);
        }
        for (String name : candidates) {
            if (!available.contains(name)) {
                continue;
            }
            // Font dang dialog mac dinh cua he luon hien dung, nen khong can kiem tra
            if ("Dialog".equals(name) || "Monospaced".equals(name)) {
                return name;
            }
            if (new Font(name, Font.PLAIN, 12).canDisplayUpTo(VIETNAMESE_PROBE) < 0) {
                return name;
            }
        }
        return candidates[candidates.length - 1];
    }

    /** Tieu de lon nhat - moi noi dung noi bat trong cua so. */
    public static final Font DISPLAY = new Font(FONT_UI, Font.BOLD, 23);
    /** Tieu de phu. */
    public static final Font TITLE = new Font(FONT_UI, Font.BOLD, 15);
    /** Chu dung chung. */
    public static final Font BODY = new Font(FONT_UI, Font.PLAIN, 13);
    /** Chu dung, dam hon mot chut. */
    public static final Font BODY_BOLD = new Font(FONT_UI, Font.BOLD, 13);
    /** Nhan nho, mau nhat - chu "phu" de tao chu tieu de chinh. */
    public static final Font LABEL = new Font(FONT_UI, Font.PLAIN, 11);
    /** Nhan can dam hon. */
    public static final Font LABEL_STRONG = new Font(FONT_UI, Font.BOLD, 11);
    /** Ten tep trong danh sach (co chu monospace de thang hang doc). */
    public static final Font FILENAME = new Font(FONT_MONO, Font.PLAIN, 12);
    /** Vung nhat ky - monospace de request/response thang hang. */
    public static final Font MONO = new Font(FONT_MONO, Font.PLAIN, 12);
    /** Chi so khong nho, dung o chan trang. */
    public static final Font MICRO = new Font(FONT_UI, Font.PLAIN, 10);

    /** Ho gia dinh font thi dung - hien thi khi can bao loi font. */
    public static String uiFamily() {
        return FONT_UI;
    }

    public static String monoFamily() {
        return FONT_MONO;
    }

    // ==================== BO GOC + KHOANG CACH ====================

    /** Bo goc nho - o nhap, chip. */
    public static final int R_SMALL = 6;
    /** Bo goc vua - panel, nut. */
    public static final int R_MEDIUM = 10;

    public static final int S1 = 4;
    public static final int S2 = 8;
    public static final int S3 = 12;
    public static final int S4 = 16;
    public static final int S5 = 24;
    public static final int S6 = 32;

    /** Chieu cao logo truong VKU trong header cua ca hai cua so. */
    public static final int LOGO_H = 72;

    /** Ten truong, hien thi ngay duoi ten ung dung trong header. */
    public static final String SCHOOL =
            "Trường Đại học Công nghệ Thông tin và Truyền thông Việt – Hàn, Đại học Đà Nẵng";

    // ==================== THIET LAP CHUNG ====================

    /**
     * Dat cac thuoc tinh chung cho moi component Swing.
     * <p>
     * Phai goi {@code SwingUtilities.invokeLater} truoc khi tao bat ky component nao.
     */
    public static void apply() {
        UIManager.put("Panel.background", PAPER);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Label.font", BODY);
        UIManager.put("TextField.background", SUNKEN);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("TextField.font", BODY);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", ACCENT_WASH);
        UIManager.put("TextField.selectionForeground", INK);
        UIManager.put("TextArea.background", SUNKEN);
        UIManager.put("TextArea.foreground", INK);
        UIManager.put("TextArea.font", MONO);
        UIManager.put("TextArea.caretForeground", ACCENT);
        UIManager.put("TextArea.selectionBackground", ACCENT_WASH);
        UIManager.put("TextArea.selectionForeground", INK);
        UIManager.put("PasswordField.background", SUNKEN);
        UIManager.put("PasswordField.foreground", INK);
        UIManager.put("PasswordField.font", BODY);
        UIManager.put("PasswordField.caretForeground", ACCENT);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.background", SUNKEN);
        UIManager.put("ScrollBar.thumb", LINE_STRONG);
        UIManager.put("ScrollBar.thumbShadow", LINE_STRONG);
        UIManager.put("ScrollBar.track", PAPER);
        // Bat buoc: mac dinh cua Nimbus cho ScrollPane/Viewport la #FDFDFC - mot
        // trang nguoi lan, khong khop nen giay am. Neu bo qua, con cua so Nhat ky
        // bi mot lat trang phu len vung log va bien mau chu thanh "nhieu mau".
        // Dat ca hai ve SUNKEN de noi dung lui xuong noi bat mau am.
        UIManager.put("ScrollPane.background", SUNKEN);
        UIManager.put("ScrollPane.opaque", Boolean.TRUE);
        UIManager.put("Viewport.background", SUNKEN);
        UIManager.put("Viewport.opaque", Boolean.TRUE);
        UIManager.put("TabbedPane.background", PAPER);
        UIManager.put("TabbedPane.foreground", INK_2);
        UIManager.put("TabbedPane.selected", PAPER);
        UIManager.put("TabbedPane.contentAreaInsets", new Insets(S2, 0, 0, 0));
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.focus", PAPER);
        UIManager.put("ToolTip.background", SURFACE);
        UIManager.put("ToolTip.foreground", INK);
        UIManager.put("ToolTip.font", LABEL);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(LINE_STRONG));
    }

    // ==================== TIEN ICH BO GOC ====================

    /**
     * Vien bo goc. Swing mac dinh khong ho tro bo goc nen phai tu ve.
     * Ve ca nen va duong kẻ hairline 1px trong cung mot {@code paintComponent}.
     */
    public static Border rounded(Color fill, Color stroke, int radius, int pad) {
        return new RoundedBorder(fill, stroke, radius, pad);
    }

    /** Vien chi ve duong kẻ, nen trong suot. */
    public static Border hairline(Color stroke) {
        return new RoundedBorder(null, stroke, R_SMALL, 0);
    }

    /**
     * Panel be mat, bo goc vua, vien hairline.
     */
    public static JPanel card() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setBorder(rounded(SURFACE, LINE, R_MEDIUM, S3));
        return p;
    }

    /**
     * O nhap 1 dong, nen lui xuong, bo goc nho, vien chuyen mau khi focus.
     */
    /**
     * Vien cho o nhap nhieu dong (noi dung thu). Cung kieu vien focus voi
     * {@link #field()} de moi vung nhap cua GUI co cung nhip thiet ke.
     */
    public static Border inputArea() {
        return new FocusBorder(LINE_STRONG, R_SMALL);
    }

    /**
     * To nen bo goc TRUOC khi ve chu.
     *
     * <p>Chuoi ve cua {@code Component.paint()} la
     * {@code paintComponent()} &#8594; {@code paintBorder()} &#8594;
     * {@code paintChildren()}. Nen neu to nen trong {@code Border} thi no se
     * to de len chu component vua ve. To nen trong {@code paintComponent()}
     * roi goi {@code super} thi thu tu dung: nen bo goc, sau do chu, sau do
     * vien.
     */
    private static void paintRoundedFill(Graphics g, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create();
        antiAlias(g2);
        g2.setColor(SUNKEN);
        g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f,
                R_SMALL, R_SMALL));
        g2.dispose();
    }

    /** {@link JTextField} co nen bo goc. */
    static class RoundedField extends JTextField {
        RoundedField() {
            setFont(BODY);
            setForeground(INK);
            setCaretColor(ACCENT);
            setOpaque(false);
            setBorder(new FocusBorder(LINE_STRONG, R_SMALL));
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintRoundedFill(g, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    /**
     * {@link JPasswordField} co nen bo goc.
     * <p>
     * Dung {@link JPasswordField} vi chi loai nay co {@code setEchoChar} va
     * {@code getPassword()} — đọc bằng {@code getText()} sẽ để mật khẩu nằm
     * trong bộ đệm của mọi tiến trình trên máy.
     */
    static class RoundedPasswordField extends JPasswordField {

        private boolean inRow;

        RoundedPasswordField() {
            setFont(BODY);
            setForeground(INK);
            setCaretColor(ACCENT);
            setOpaque(false);
            setEchoChar('•');
            setBorder(new FocusBorder(LINE_STRONG, R_SMALL));
        }

        /** Chuyen sang che do "noi trong" {@code PasswordRow}: row ve nen bo goc
         *  va vien, field chi con chu. Bỏ border de khong bi vien doi. */
        void setInRow(boolean value) {
            this.inRow = value;
            setBorder(value ? null : new FocusBorder(LINE_STRONG, R_SMALL));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!inRow) {
                paintRoundedFill(g, getWidth(), getHeight());
            }
            super.paintComponent(g);
        }
    }

    /** {@link JTextArea} co nen bo goc — dung cho o nhap noi dung thu. */
    static class RoundedTextArea extends javax.swing.JTextArea {
        RoundedTextArea() {
            setFont(BODY);
            setForeground(INK);
            setCaretColor(ACCENT);
            setOpaque(false);
            setBorder(new FocusBorder(LINE_STRONG, R_SMALL));
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintRoundedFill(g, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    public static JTextField field() {
        return new RoundedField();
    }

    public static JPasswordField passwordField() {
        return new RoundedPasswordField();
    }

    /**
     * Icon con mat tu ve cho nut cua {@link PasswordRow}.
     *
     * <p>Ve bang Graphics thay vi emoji de khong phu thuoc font cua may khac:
     * "mat thuong" ngu y co the bam de HIEN mat khau, "mat gach cheo" ngu y
     * co the bam de AN.
     */
    static class EyeIcon implements Icon {

        private final boolean slash;

        EyeIcon(boolean slash) {
            this.slash = slash;
        }

        @Override
        public int getIconWidth() {
            return 22;
        }

        @Override
        public int getIconHeight() {
            return 22;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color col = (c instanceof JButton b && b.getModel().isRollover())
                    ? ACCENT : INK_3;
            g2.setColor(col);
            float w = 18f, h = 10f;
            float ox = x + (getIconWidth() - w) / 2f;
            float oy = y + (getIconHeight() - h) / 2f;
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(new Ellipse2D.Float(ox, oy, w, h));
            float pr = 3f;
            float cx = ox + w / 2f;
            float cy = oy + h / 2f;
            g2.fill(new Ellipse2D.Float(cx - pr, cy - pr, pr * 2f, pr * 2f));
            if (slash) {
                g2.setStroke(new BasicStroke(2.2f));
                g2.draw(new Line2D.Float(ox, oy + h, ox + w, oy));
            }
            g2.dispose();
        }
    }

    /**
     * O mat khau gom field + nut con mat, nhom thanh MOT o nhap theo dung nhip
     * thiet ke (cung kieu {@link FocusBorder} voi {@link #field()}).
     */
    static class PasswordRow extends JPanel {

        private final JPasswordField field;
        private final JButton eye;
        private boolean visible;

        PasswordRow(JPasswordField field, boolean visible) {
            super(new BorderLayout());
            this.field = field;
            this.visible = visible;
            setOpaque(false);
            setBorder(new FocusBorder(LINE_STRONG, R_SMALL));
            applyEcho();

            eye = new JButton(new EyeIcon(!visible));
            eye.setFocusable(false);
            eye.setOpaque(false);
            eye.setContentAreaFilled(false);
            eye.setBorderPainted(false);
            eye.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            eye.setToolTipText("Hiện/Ẩn mật khẩu");
            eye.addActionListener(e -> toggle());
            eye.setPreferredSize(new Dimension(34, 26));

            add(field, BorderLayout.CENTER);
            add(eye, BorderLayout.EAST);
        }

        private void applyEcho() {
            // Echo char = 0 nghia la khong co echo: Java hien dung chu da go.
            field.setEchoChar(visible ? (char) 0 : '•');
        }

        private void toggle() {
            visible = !visible;
            applyEcho();
            eye.setIcon(new EyeIcon(!visible));
            eye.repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintRoundedFill(g, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    /**
     * Goi o mat khau co con mat.
     *
     * <p>Yeu cau "go vao la THAY mat khau, khong bi an" nen mac dinh
     * {@code visible = true} -- hien chu that, bam con mat de an danh dau cham.
     * {@code field} duoc chuyen sang che do noi trong de row ve nhu mot o.
     */
    public static JComponent passwordRow(JPasswordField field, boolean visible) {
        if (field instanceof RoundedPasswordField rpf) {
            rpf.setInRow(true);
        } else {
            field.setOpaque(false);
            field.setBorder(null);
        }
        return new PasswordRow(field, visible);
    }

    /**
     * Nhan chu nho phu - dung cho ten truong nhap.
     */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(LABEL);
        l.setForeground(INK_3);
        return l;
    }

    /**
     * Nhan chu nho phu nhung noi bat hon (tieu de muc, tieu de danh sach).
     */
    public static JLabel labelStrong(String text) {
        JLabel l = new JLabel(text);
        l.setFont(LABEL_STRONG);
        l.setForeground(INK_2);
        return l;
    }

    /**
     * Nhan chua logo truong, can theo chieu cao {@link #LOGO_H}.
     *
     * <p>Tra {@code null} khi khong nap duoc anh, de header bo trong chay tiep
     * thay vi lam hong ca cua so. Chu canh bao lay tu {@link LogoAssets#warning()}.
     */
    public static JLabel logoLabel() {
        Image img = LogoAssets.scaledToHeight(LOGO_H);
        if (img == null) {
            return null;
        }
        JLabel l = new JLabel(new ImageIcon(img));
        l.setPreferredSize(new Dimension(img.getWidth(null), LOGO_H));
        return l;
    }

    // ==================== CHI BAO TRANG THAI ====================

    /**
     * Vong tròn 8px + chu, dung cho dong trang thai.
     * <p>
     * Vong tron thay cho emoji/icon: nho gon, canh theo mau, khong ton tai mau
     * khi doi font.
     */
    public static JPanel dot(Color color, String text, Color textColor) {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        JComponent dot = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                antiAlias(g2);
                g2.setColor(color);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(8, 8));
        row.add(dot);
        JLabel l = new JLabel("  " + text);
        l.setFont(BODY);
        l.setForeground(textColor);
        row.add(l);
        return row;
    }

    // ==================== NOI BO ====================

    /** Bat antialias cho 2D - mac dinh Java hay co rang, bo di moi mau dep. */
    static void antiAlias(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }

    /**
     * Vien bo goc tu ve. Ve nen + duong ke, ho tro focus bo doi vien sang mau nhan.
     */
    static class RoundedBorder extends AbstractBorder {

        private final Color fill;
        private final Color stroke;
        private final int radius;
        private final int pad;

        RoundedBorder(Color fill, Color stroke, int radius, int pad) {
            this.fill = fill;
            this.stroke = stroke;
            this.radius = radius;
            this.pad = pad;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            if (fill == null && stroke == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            antiAlias(g2);
            // -1 de khong cat mat bo goc
            RoundRectangle2D r = new RoundRectangle2D.Float(
                    x + 0.5f, y + 0.5f, w - 1f, h - 1f, radius, radius);
            if (fill != null) {
                g2.setColor(fill);
                g2.fill(r);
            }
            if (stroke != null) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(r);
            }
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.right = pad;
            insets.top = insets.bottom = pad;
            return insets;
        }
    }

    /**
     * Vien o nhap: nen lui xuong + vien doi mau khi focus.
     *
     * <p><b>Phai override {@code getBorderInsets}:</b> {@link AbstractBorder} mac dinh
     * tra ve Insets rong, nghia la o nhap khong duoc nao padding nao. Hai hau qua
     * nhin thay duoc ngay: chu dung sat vien, va chieu cao o rut xuong con bang
     * chieu cao dong chu (~16px) thay vi ~30px — trong {@code GridBagLayout} component
     * duoc dat bang preferred size nen o se bi beo lai. Day la ly do phai khai bao
     * tay phan de khong phai so sanh preferred size o moi layout.
     *
     * <p><b>Khong duoc {@code fill} trong {@code paintBorder}:</b> chuoi khu vuc ve
     * cua {@code Component.paint()} la {@code paintComponent()} &#8594;
     * {@code paintBorder()} &#8594; {@code paintChildren()}. Mot {@code Border} that
     * nen se to mau dinh phai, che mat dung chu ma component vua ve. Component con
     * (nut cua card) duoc ve o {@code paintChildren()} nen khong bi anh huong — chi
     * component tu ve noi dung trong {@code paintComponent()} bi. Nen o nhanh day
     * la nen nen no nen {@code setOpaque(true)} + {@code setBackground(SUNKEN)}, con
     * vien chi ke duong bao quanh.
     */
    static class FocusBorder extends AbstractBorder {

        private static final int PAD_X = 8;
        private static final int PAD_Y = 7;

        private final Color focusStroke;
        private final int radius;

        FocusBorder(Color focusStroke, int radius) {
            this.focusStroke = focusStroke;
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = PAD_X;
            insets.right = PAD_X;
            insets.top = PAD_Y;
            insets.bottom = PAD_Y;
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(PAD_Y, PAD_X, PAD_Y, PAD_X);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            antiAlias(g2);
            boolean focused = c.isFocusOwner();
            // Chi ve duong bao, KHONG to nen - xem ghi chu tren.
            g2.setColor(focused ? Theme.ACCENT : LINE_STRONG);
            g2.setStroke(new BasicStroke(focused ? 1.6f : 1f));
            g2.draw(new RoundRectangle2D.Float(
                    x + 0.5f, y + 0.5f, w - 1f, h - 1f, radius, radius));
            g2.dispose();
        }
    }

    /**
     * Nut phang tu ve: bo goc, hover chuyen nen, nhan xoay xuong 1px.
     * <p>
     * Thay the JButton mac dinh cua Nimbus/LAF, vi ban mac dinh do co vien cong,
     * gradient va do bo dong rat "mac dinh Swing".
     */
    public static class FlatButton extends JComponent {

        private final String text;
        private Color baseFill;
        private Color hoverFill;
        private Color pressFill;
        private Color textColor;
        private final boolean filled;

        private boolean hover;
        private boolean pressed;
        private float hoverAmount;
        private final java.util.List<Runnable> actions = new java.util.ArrayList<>();

        public FlatButton(String text, boolean filled) {
            this.text = text;
            this.filled = filled;
            if (filled) {
                this.baseFill = Theme.ACCENT;
                this.hoverFill = Theme.ACCENT_HOVER;
                this.pressFill = Theme.ACCENT_PRESS;
                this.textColor = Color.WHITE;
            } else {
                this.baseFill = Theme.SURFACE;
                this.hoverFill = Theme.ACCENT_WASH;
                this.pressFill = Theme.LINE;
                this.textColor = Theme.INK_2;
            }
            setFont(Theme.BODY_BOLD);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // KHONG dung rounded(...) o day: border do to nen va se to mau
            // len chu ma paintComponent() vua ve (paintBorder chay sau
            // paintComponent). Nut tu to nen trong paintComponent, border chi
            // lo phan dem inset de chu co khoang cach.
            setBorder(BorderFactory.createEmptyBorder(S2, S3, S2, S3));

            // Phai focusable va co key binding neu muon dung bang ban phim:
            // nguoi dung khong the do chuot. Day la dieu kien bat buoc de dung
            // chuan cua nut, khong phai tinh nang phu.
            setFocusable(true);

            // Space va Enter kich hoat nhu nut binh thuong.
            getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "flatButtonPress");
            getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "flatButtonPress");
            getActionMap().put("flatButtonPress", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    fire();
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!isEnabled()) return;
                    hover = true;
                    startFade();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    pressed = false;
                    startFade();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    pressed = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!isEnabled()) return;
                    // Chi kich hoat khi nha chuot trongran cua nut. Neu dung
                    // mousePressed thi keo chuot ra ngoai roi tha van bam nham -
                    // nguoi dung thieu suot se khong mong doi.
                    boolean inside = contains(e.getPoint());
                    pressed = false;
                    repaint();
                    if (inside) fire();
                }
            });
        }

        /** Gan hành vi khi nút được bấm. */
        public FlatButton onClick(Runnable r) {
            actions.add(r);
            return this;
        }

        /** Kich hoat nút lap trinh (giong do nguoi dung bấm). */
        public void doClick() {
            fire();
        }

        private void fire() {
            if (!isEnabled()) return;
            for (Runnable r : actions) r.run();
        }

        public FlatButton setFill(Color fill, Color hover, Color press, Color text) {
            this.baseFill = fill;
            this.hoverFill = hover;
            this.pressFill = press;
            this.textColor = text;
            repaint();
            return this;
        }

        /**
         * Hover chuyen nen trong ~180ms thay vi doi gay gap ngay - day la chi tiet
         * nho nhung phan biet giao dien "may" voi giao dien lam nhap.
         */
        private void startFade() {
            final int steps = 10;
            final float delta = hover ? 1f / steps : -1f / steps;
            Timer fade = new Timer(180 / steps, e -> {
                hoverAmount = Math.max(0f, Math.min(1f, hoverAmount + delta));
                repaint();
                if (hoverAmount <= 0.001f || hoverAmount >= 0.999f) {
                    ((Timer) e.getSource()).stop();
                }
            });
            fade.setRepeats(true);
            fade.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            antiAlias(g2);

            // noi suyen mau nen giua base va hover theo hoverAmount
            Color bg = blend(baseFill, hoverFill, hoverAmount);
            if (pressed) {
                bg = pressFill;
            }

            // Nut bi khoa: ha nen ve phia SUNKEN va lam chu mo nhat di. Neu giu
            // nguyen mau accent, nguoi dung khong phan biet duoc nut dang bat
            // hay da khoa - va duoi day la loai nhat can mot su phan biet ro.
            Color fg = textColor;
            if (!isEnabled()) {
                bg = blend(bg, Theme.SUNKEN, 0.72f);
                fg = blend(textColor, Theme.INK_3, 0.75f);
            }

            int inset = pressed ? 1 : 0;
            RoundRectangle2D r = new RoundRectangle2D.Float(
                    0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, R_MEDIUM, R_MEDIUM);
            g2.setColor(bg);
            g2.fill(r);
            if (!filled) {
                Color edge = LINE_STRONG;
                if (!isEnabled()) {
                    edge = blend(LINE_STRONG, Theme.SUNKEN, 0.6f);
                } else if (hoverAmount > 0.02f) {
                    edge = blend(LINE_STRONG, ACCENT, hoverAmount);
                }
                g2.setColor(edge);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(r);
            }

            // Vong tieu de focus: bat buoc de nguoi dung ban phim biet nut nao
            // dang co focus. Vien trong 1px de khong lam doi layout.
            if (isFocusOwner()) {
                g2.setColor(Theme.ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(2.5f, 2.5f,
                        getWidth() - 5f, getHeight() - 5f, R_SMALL, R_SMALL));
            }

            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(text)) / 2;
            int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent() + inset;
            g2.drawString(text, tx, ty);
            g2.dispose();
        }

        @Override
        public void setEnabled(boolean b) {
            super.setEnabled(b);
            if (!b) {
                hover = false;
                pressed = false;
                hoverAmount = 0f;
            }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + S5 * 2,
                    fm.getHeight() + S2 * 2 - 2);
        }
    }

    /** Tron hai mau theo ti le t (0..1). */
    static Color blend(Color a, Color b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }
}