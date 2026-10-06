import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;

/**
 * MailServerFrame.java - giao dien Swing cua may chu UDP.
 *
 * <p>Cua so nay chi theo doi va hien thi - moi xu ly protocol nam trong
 * {@link MailServer}. Khi bam "Bat dau", tao mot doi tuong {@code MailServer} moi
 * va truyen cho no mot {@code logSink} dan dong nhat ky vao {@link #logArea}.
 *
 * <p><b>Viet khoang cach an toan voi Swing (EDT):</b>
 * <ul>
 *   <li>{@code MailServer.log()} duoc goi tu thread "mail-listener" va 10 thread
 *       trong worker pool, khong phai EDT. Vi vay {@code logSink} bo qua dong do
 *       va day sang EDT qua {@link SwingUtilities#invokeLater}.</li>
 *   <li>Moi thao tac Swing (doi trang thai nut, them dong log) deu chay tren EDT
 *       vi da nam trong {@code invokeLater} hoac vi la event chuot/keyboard.</li>
 * </ul>
 */
public class MailServerFrame extends JFrame {

    /** So dong toi da giu trong vung nhat ky - tranh bo nho phinh to. */
    private static final int MAX_LOG_LINES = 500;

    private final JTextField portField = Theme.field();
    private final JTextField dataDirField = Theme.field();
    private final Theme.FlatButton browseButton;
    private final Theme.FlatButton startButton;
    private final Theme.FlatButton stopButton;
    private final Theme.FlatButton clearButton;

    private final JTextArea logArea = new JTextArea();
    private final JPanel statusSlot;

    /** Server dang chay, hoac null neu da dung. */
    private MailServer server;

    /** Dong nhat ky chua dua len man hinh, dung de giu thu tu khi nhieu thread ghi cung luc. */
    private final Deque<String> pending = new ConcurrentLinkedDeque<>();

    public MailServerFrame() {
        // Dat token chung ngay trong constructor chu khong phai chi trong main().
        // Neu goi tu choi khac (vi du app nhieu cua so) thi giao dien van dung
        // font va mau cua bai, khong bi ray mau mac dinh cua Nimbus.
        Theme.apply();

        setTitle("Mail Server UDP — Lab 5 Bài 2");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(880, 640);
        setMinimumSize(new Dimension(720, 520));
        Image icon = LogoAssets.windowIcon(128);
        if (icon != null) {
            setIconImage(icon);
        }
        setLocationRelativeTo(null);

        portField.setText(String.valueOf(MailServer.DEFAULT_PORT));
        dataDirField.setText(MailServer.DEFAULT_DATA_DIR);

        browseButton = new Theme.FlatButton("Chọn…", false);
        startButton = new Theme.FlatButton("Bắt đầu", true);
        stopButton = new Theme.FlatButton("Dừng", false);
        clearButton = new Theme.FlatButton("Xoá nhật ký", false);
        statusSlot = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusSlot.setOpaque(false);

        setContentPane(buildLayout());
        setRunningState(false);
        renderStatus();

        portField.addActionListener(e -> startServer());
        dataDirField.addActionListener(e -> startServer());
        browseButton.onClick(this::chooseDirectory);
        startButton.onClick(this::startServer);
        stopButton.onClick(this::stopServer);
        clearButton.onClick(this::clearLog);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopServer();
                dispose();
                System.exit(0);
            }
        });
    }

    // ==================== BO CUC ====================

    private JPanel buildLayout() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAPER);
        root.setBorder(BorderFactory.createEmptyBorder(Theme.S5, Theme.S5, Theme.S4, Theme.S5));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    /**
     * Tieu de ben trai, chi bao trang thai ben phai - mot bo cuc bat doi,
     * khong can giua tieu de nhu mau mac dinh.
     */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(0, Theme.S1, 0, Theme.S3));

        JLabel logo = Theme.logoLabel();
        if (logo != null) {
            left.add(logo, BorderLayout.WEST);
        }

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel name = new JLabel("Mail Server UDP");
        name.setFont(Theme.DISPLAY);
        name.setForeground(Theme.INK);

        JLabel school = new JLabel(Theme.SCHOOL);
        school.setFont(Theme.LABEL);
        school.setForeground(Theme.INK_3);

        String subText = "Lab 5 — Bài 2 · dịch vụ thư điện trên giao thức UDP";
        String warn = LogoAssets.warning();
        if (warn != null) {
            subText = subText + " · " + warn;
        }

        JLabel sub = new JLabel(subText);
        sub.setFont(Theme.LABEL);
        sub.setForeground(Theme.INK_3);

        titles.add(name);
        titles.add(Box.createVerticalStrut(Theme.S1));
        titles.add(school);
        titles.add(Box.createVerticalStrut(Theme.S1));
        titles.add(sub);

        left.add(titles, BorderLayout.CENTER);

        header.add(left, BorderLayout.WEST);
        header.add(statusSlot, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(0, Theme.S1, Theme.S4, Theme.S1));
        return header;
    }

private JPanel buildBody() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel configCard = buildConfigCard();
        // BoxLayout chia phan du theo maxSize. Mac dinh maxSize rat lon, nen the
        // cau hinh se "phinh" ra gap 200px trong khi noi dung chi can ~90px.
        // Ep max = preferred de no om dung noi dung, phan du chuyen cho the log.
        configCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                configCard.getPreferredSize().height));
        body.add(configCard);
        body.add(Box.createVerticalStrut(Theme.S2));
        body.add(buildLogCard());
        return body;
    }

    /** The nhat ky - phan lon man hinh, day phan du khong gian doc cua cua so. */
    private JPanel buildLogCard() {
        JPanel logCard = new JPanel(new BorderLayout());
        logCard.setOpaque(false);
        logCard.setBorder(Theme.rounded(Theme.SURFACE, Theme.LINE, Theme.R_MEDIUM, Theme.S3));

        JPanel logHead = new JPanel(new BorderLayout());
        logHead.setOpaque(false);
        logHead.add(Theme.labelStrong("NHẬT KÝ HOẠT ĐỘNG"), BorderLayout.WEST);
        JLabel logNote = new JLabel("giữ " + MAX_LOG_LINES + " dòng gần nhất");
        logNote.setFont(Theme.MICRO);
        logNote.setForeground(Theme.INK_3);
        logHead.add(logNote, BorderLayout.EAST);
        logHead.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.S2, 0));

        logArea.setEditable(false);
        logArea.setFont(Theme.MONO);
        logArea.setForeground(Theme.INK_2);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(false);
        logArea.setOpaque(false);
        logArea.setBorder(BorderFactory.createEmptyBorder(Theme.S2, Theme.S2,
                Theme.S2, Theme.S2));

        JScrollPane scroll = new JScrollPane(logArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        // Khong tin UIManager cho viewport: Nimbus bo qua "Viewport.background" va
        // to #FDFDFC. Thay vi gan nen, ta tat opacity de nen SUNKEN cua logInner
        // loi len. Cach nay doc lap LAF, dung luon ca voi FlatButton custom.
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setOpaque(false);

        // Vung log lui xuong: nen SUNKEN + vien hairline, bo trong mot chút
        // de duong ke khong cham chu.
        JPanel logInner = new JPanel(new BorderLayout());
        logInner.setBackground(Theme.SUNKEN);
        logInner.setBorder(BorderFactory.createCompoundBorder(
                Theme.hairline(Theme.LINE),
                BorderFactory.createEmptyBorder(Theme.S1, Theme.S1, Theme.S1, Theme.S1)));
        logInner.add(scroll, BorderLayout.CENTER);

        logCard.add(logHead, BorderLayout.NORTH);
        logCard.add(logInner, BorderLayout.CENTER);
        return logCard;
    }

    /**
     * The cau hinh: nhan o ben trai, o nhap ben phai. Nut can o ve ben phai
     * de tao nhip thang chu khong phai can giua.
     */
    private JPanel buildConfigCard() {
        JPanel card = Theme.card();
        card.setLayout(new GridBagLayout());

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(Theme.S1, 0, Theme.S1, Theme.S2);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0;
        g.gridy = 0;
        card.add(Theme.label("Cổng UDP"), g);

        g.gridx = 0;
        g.gridy = 1;
        card.add(Theme.label("Thư mục dữ liệu"), g);

        g.gridx = 1;
        g.gridy = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(Theme.S1, 0, Theme.S1, Theme.S2);
        portField.setPreferredSize(new Dimension(120, 30));
        card.add(portField, g);

        JPanel dirRow = new JPanel(new BorderLayout(Theme.S2, 0));
        dirRow.setOpaque(false);
        dataDirField.setPreferredSize(new Dimension(360, 30));
        dirRow.add(dataDirField, BorderLayout.CENTER);
        browseButton.setPreferredSize(new Dimension(78, 30));
        dirRow.add(browseButton, BorderLayout.EAST);

        g.gridx = 1;
        g.gridy = 1;
        g.insets = new Insets(Theme.S1, 0, Theme.S3, 0);
        card.add(dirRow, g);

        // Nut phai, dung hang voi muc 1
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.S2, 0));
        buttons.setOpaque(false);
        startButton.setPreferredSize(new Dimension(96, 34));
        stopButton.setPreferredSize(new Dimension(72, 34));
        buttons.add(startButton);
        buttons.add(stopButton);

        g.gridx = 2;
        g.gridy = 0;
        g.gridheight = 2;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        g.insets = new Insets(Theme.S1, 0, Theme.S1, 0);
        card.add(buttons, g);

        return card;
    }

    private JPanel buildFooter() {
        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        clearButton.setPreferredSize(new Dimension(116, 30));

        JLabel hint = new JLabel("Nhật ký cập nhật trực tiếp từ luồng nghe UDP và nhóm xử lý");
        hint.setFont(Theme.MICRO);
        hint.setForeground(Theme.INK_3);

        foot.add(clearButton, BorderLayout.WEST);
        foot.add(hint, BorderLayout.EAST);
        foot.setBorder(BorderFactory.createEmptyBorder(Theme.S2, Theme.S1, 0, Theme.S1));
        return foot;
    }

    // ==================== HANH DONG ====================

    /**
     * Kiem tra cong truoc khi bat dau.
     *
     * @return cong hop le, hoac -1 neu khong hop le (da hien hop thong bao)
     */
    private int validatePort() {
        try {
            int p = Integer.parseInt(portField.getText().trim());
            if (p < 1 || p > 65535) {
                throw new NumberFormatException();
            }
            return p;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Cổng phải là số từ 1 đến 65535.", "Cổng không hợp lệ",
                    JOptionPane.WARNING_MESSAGE);
            return -1;
        }
    }

    private void startServer() {
        if (server != null && server.isRunning()) {
            return;
        }
        int port = validatePort();
        if (port < 0) {
            return;
        }
        String dir = dataDirField.getText().trim();
        if (dir.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Chưa nhập thư mục dữ liệu.", "Thiếu thư mục",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            MailServer s = new MailServer(port, dir, this::appendLogLine);
            if (!s.start()) {
                JOptionPane.showMessageDialog(this,
                        s.getLastError() + "\n\nHãy đổi sang cổng khác rồi thử lại.",
                        "Không khởi động được server",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            server = s;
            setRunningState(true);
            renderStatus();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi tạo thư mục dữ liệu: " + ex.getMessage(),
                    "Lỗi hệ thống", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (server != null) {
            server.shutdown();
            server = null;
        }
        setRunningState(false);
        renderStatus();
    }

    private void setRunningState(boolean on) {
        portField.setEnabled(!on);
        dataDirField.setEnabled(!on);
        browseButton.setEnabled(!on);
        startButton.setEnabled(!on);
        stopButton.setEnabled(on);
    }

    /** Vẽ lại dong chi bao trang thai o goc tren phai. */
    private void renderStatus() {
        statusSlot.removeAll();
        if (server != null && server.isRunning()) {
            statusSlot.add(Theme.dot(Theme.OK, "Đang chạy · cổng " + server.getPort(),
                    Theme.OK));
        } else {
            statusSlot.add(Theme.dot(Theme.IDLE, "Đã dừng", Theme.INK_3));
        }
        statusSlot.revalidate();
        statusSlot.repaint();
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser(dataDirField.getText().trim());
        chooser.setDialogTitle("Chọn thư mục lưu dữ liệu");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                && chooser.getSelectedFile() != null) {
            dataDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void clearLog() {
        logArea.setText("");
    }

    // ==================== NHAT KY ====================

    /**
     * Nhan 1 dong nhat ky tu thread server va day sang EDT.
     * <p>
     * Cac dong duoc day vao {@link #pending} truoc, duy tri thu tu, roi moi flush
     * mot lan tren EDT. Neu invokeLater ngay cho tung dong, voi 60 request dong
     thoi se co 60 lan repaint lien tiep va giao dien chop.
     *
     * @param line dong nhat ky da co dau thoi gian
     */
    private void appendLogLine(String line) {
        pending.add(line);
        SwingUtilities.invokeLater(this::flushLog);
    }

    private void flushLog() {
        if (pending.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int added = 0;
        String line;
        while ((line = pending.poll()) != null) {
            sb.append(line).append('\n');
            added++;
        }
        logArea.append(sb.toString());
        trimLog();
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /**
     * Cat nhung dong cuoi khi vuot {@link #MAX_LOG_LINES}.
     * <p>
     * Tinh so dong moi bang cach dem ky tu xuong dong trong toan bo tai lieu —
     * don gian hon la giu lai mot mang dong rieng, va so dong dau tien cua
     * JTextArea hon la 1 dong hien thi.
     */
    private void trimLog() {
        String text = logArea.getText();
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        int excess = lines - MAX_LOG_LINES;
        if (excess <= 0) {
            return;
        }
        int cut = 0;
        for (int i = 0; i < excess && cut < text.length(); i++) {
            int nl = text.indexOf('\n', cut);
            if (nl < 0) {
                return;
            }
            cut = nl + 1;
        }
        try {
            logArea.getDocument().remove(0, cut);
        } catch (BadLocationException ignored) {
            // Khong xoa duoc thi bo qua - khong phai loi nghiem trong
        }
    }

    // ==================== DIEM VAO CHUONG TRINH ====================

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Khong co man hinh de mo giao dien Swing.");
            System.err.println("Hay chay tren may co desktop, hoac dung bien moi truong DISPLAY.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Theme.apply();
            new MailServerFrame().setVisible(true);
        });
    }
}