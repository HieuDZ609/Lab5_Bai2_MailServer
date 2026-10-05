import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.JPasswordField;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Giao dien Swing cua Mail Client.
 *
 * <p><b>Pham vi dung nhung phien ban:</b> dung {@code SwingWorker} de moi request UDP
 * chay tren luong nen. {@link MailClient#request(String)} chan toi da
 * {@link MailClient#TIMEOUT_MS} ms; neu goi truc tiep tren Event Dispatch Thread thi
 * ca cua so se treo — ke ca repaint, bo phim va nut dong cua.
 *
 * <p><b>Mot request tai mot thoi diem:</b> client giu dung mot {@code DatagramSocket}
 * cho ca phien. Hai {@code SwingWorker} chay song song se doi duoc response cua nhau
 * ma khong bao gio phat hien, vi datagram khong mang dinh danh. {@link #busy} khoa
 * lai toan bo giao dien trong khi co request dang chay.
 *
 * <p><b>Phien ban rieng cho tung thao tac:</b> moi request duoc gan mot
 * {@link java.util.concurrent.atomic.AtomicInteger} rieng. Neu nguoi dung doi tab
 * giua luc dang chờ, ket qua van ve dung cho thao tac no, khong lot sang form khac.
 */
public class MailClientFrame extends JFrame {

    private final JTextField hostField = Theme.field();
    private final JTextField portField = Theme.field();
    private final JTextField regUserField = Theme.field();
    private final JPasswordField regPassField = Theme.passwordField();
    private final JTextField logUserField = Theme.field();
    private final JPasswordField logPassField = Theme.passwordField();
    private final JTextField fromField = Theme.field();
    private final JTextField toField = Theme.field();
    private final JTextField subjectField = Theme.field();
    private final JTextArea bodyArea = new Theme.RoundedTextArea();

    private final JLabel statusText = new JLabel();
    // Khai bao bang initializer de san san TRUOC buildLayout() chay. Neu gan
    // trong constructor sau setContentPane() thi buildConnectCard() se gap
    // null - dung cai bay "mo truoc, gan sau" kinh dien cua Swing.
    private final Theme.FlatButton connectButton = new Theme.FlatButton("Kết nối", true);
    private final Theme.FlatButton disconnectButton = new Theme.FlatButton("Ngắt", false);
    private final Theme.FlatButton logoutButton = new Theme.FlatButton("Đăng xuất", false);

    /** Hang rieng chứa nút {@link #logoutButton}; ẩn theo phiên đăng nhập. */
    private JPanel logoutRow;

    private final JLabel regResult = Theme.label("");
    private final JLabel logResult = Theme.label("");
    private final JLabel sendResult = Theme.label("");

    private final JLabel bodyCount = Theme.label("");
    private final JLabel mailboxCount = Theme.label("");

    private final DefaultListModel<String> mailboxModel = new DefaultListModel<>();
    private JPanel tabCards;

    /**
     * Tab dang xem ({@code TAB_*}).
     *
     * <p>Giu rieng de trang thai cua khung hien tai luon doc duoc tu mot noi -
     * {@link #selectTab} cap nhat, {@link #setLoggedInUi} dung de chuyen tab khi
     * dang nhap/xuat, va viec kiem tra khong phai do lai cay component.
     */
    private int currentTab = TAB_REGISTER;
    private java.util.List<Theme.FlatButton> tabButtons;

    /** Chi so cua 4 tab theo thu tu khai bao trong {@link #buildTabs()}. */
    /** Hien thi khi file thu khong co dong IP (thu tao truoc khi tinh nang nay co). */
    private static final String OLD_MAIL_MARK = "(thư cũ)";

    private static final int TAB_REGISTER = 0;
    private static final int TAB_LOGIN = 1;
    private static final int TAB_SEND = 2;
    private static final int TAB_READ = 3;

    /**
     * Chu ky poll hop thu (ms).
     *
     * <p>1 giay la dung gia tri ma de bai Bai 1 dung cho ExchangeRate ("mỗi giây
     * yêu cầu đến máy chủ"). Nhanh hon khong can: thu do nguoi dung gui tay, khong
     * phai qua he thong ben ngoai. Cham hon 1 giay thi thu moi hien lai cham va
     * thay phan tinh bang may chu.
     */
    private static final int POLL_INTERVAL_MS = 1000;

    /**
     * Card hop thu (cot trai). Chi duoc hien sau khi dang nhap — xem
     * {@link #setLoggedInUi(boolean)}.
     */
    private JPanel mailboxCard;

    private final JList<String> mailboxList = new JList<>(mailboxModel);

    /** Ten file chua doc — dung de to dam trong danh sach hop thu. */
    private final Set<String> unread = new HashSet<>();

    /**
     * Co bang {@code true} khi {@link #refreshMailbox()} dang xoa va them lai model.
     *
     * <p><b>Vi sao can:</b> {@code clear()} lam bo chon, {@code setSelectedIndex()} lai
     * chon lai, nen {@link ListSelectionListener} bam "Doc thu" mot lan nua — vong poll
     * 1 giay se <b>tai lai thu dang mo</b> khong ngungung. Co nay tat listener khi
     * chinh ta sua danh sach, khong phai khi nguoi dung bam.
     */
    private boolean restoringSelection;

    // ==================== TAB DOC THU ====================

    private final JLabel readFileName = Theme.labelStrong("");
    private final JLabel readFrom = Theme.label("");
    private final JLabel readTo = Theme.label("");
    private final JLabel readSenderIp = Theme.label("");
    private final JLabel readReceiverIp = Theme.label("");
    private final JLabel readSubject = Theme.label("");
    private final JLabel readDate = Theme.label("");
    private final JTextArea readBody = new Theme.RoundedTextArea();
    private final JLabel readResult = Theme.label("");

    /** Client hien tai; null khi chua bam "Ket noi". */
    private MailClient client;

    /**
     * Khoa giao dien trong luc co request dang chay tren luong nen.
     * <p>
     * {@code volatile} vi luong {@code mail-poll} cung doc co nay: poll phai bi
     * bo qua khi nguoi dung dang bam nut, neu khong thi se chen phien
     * {@code synchronized} cua {@code MailClient} va lam thao tac cham them
     * 800 ms.
     */
    private volatile boolean busy;

    // ==================== POLL HOP THU ====================

    /**
     * Luong nen poll danh sach thu moi giay 1 lan.
     *
     * <p>{@code daemon} de chuong trinh thoat ngay khi dong cua so, khong treo
     * vi luong nay. Moi lan poll chay tren luong rieng, <b>khong</b> phai luong
     * EDT — vi {@code MailClient.list()} chan toi da.
     */
    private final ScheduledExecutorService poller =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mail-poll");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> pollTask;

    public MailClientFrame() {
        // Token dung chung phai duoc dat o constructor, truoc khi bat component
        // nao tao - neu goi tu choi khac thi giao dien van dung font va mau.
        Theme.apply();

        setTitle("Mail Client UDP — Lab 5 Bài 2");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(940, 640));
        setContentPane(buildLayout());

        connectButton.onClick(this::connect);
        disconnectButton.onClick(this::disconnect);
        logoutButton.onClick(this::logout);
        setLoggedInUi(false);

        hostField.setText("localhost");
        portField.setText(String.valueOf(MailServer.DEFAULT_PORT));

        refreshState();
        updateBodyCount();
        updateMailboxCount();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeAll();
            }
        });
    }

    // ==================== BO CUC ====================

    private JPanel buildLayout() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAPER);
        root.setBorder(BorderFactory.createEmptyBorder(Theme.S5, Theme.S5, Theme.S4, Theme.S5));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHeader() {
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.S3, 0));

        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, javax.swing.BoxLayout.Y_AXIS));

        JLabel name = Theme.label("Mail Client UDP");
        name.setFont(Theme.DISPLAY);
        names.add(name);
        names.add(javax.swing.Box.createVerticalStrut(2));

        JLabel sub = Theme.label("REGISTER · LOGIN · SEND — client UDP dùng socket dùng chung cho mỗi phiên");
        sub.setForeground(Theme.INK_3);
        names.add(sub);

        JPanel dotBox = new JPanel(new BorderLayout());
        dotBox.setOpaque(false);
        JPanel d = Theme.dot(Theme.IDLE, "", Theme.INK_3);
        statusText.setFont(Theme.BODY);
        statusText.setForeground(Theme.INK_2);
        dotBox.add(d, BorderLayout.WEST);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(statusText, BorderLayout.CENTER);
        dotBox.add(holder, BorderLayout.CENTER);

        head.add(names, BorderLayout.WEST);
        head.add(dotBox, BorderLayout.EAST);
        return head;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, javax.swing.BoxLayout.X_AXIS));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, javax.swing.BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(268, 10));
        left.setMaximumSize(new Dimension(268, Integer.MAX_VALUE));

        JPanel connCard = buildConnectCard();
        connCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                connCard.getPreferredSize().height));
        left.add(connCard);
        left.add(javax.swing.Box.createVerticalStrut(Theme.S2));

        JPanel boxCard = buildMailboxCard();
        // Phan du khong gian doc thuoc ve hop thu: day la noi duy nhat tren
        // cot trai co the gia tang theo chieu cao cua so.
        boxCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        mailboxCard = boxCard;
        left.add(boxCard);

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        right.add(buildTabs(), BorderLayout.CENTER);

        center.add(left);
        center.add(Box.createHorizontalStrut(Theme.S3));
        center.add(right);
        return center;
    }

    /**
     * Thay {@link JTabbedPane} bang dai nut phang tu ve + {@link CardLayout}.
     *
     * <p><b>Vi sao kh dung JTabbedPane:</b> UI mac dinh cua Nimbus ve dai tab
     * bang mau va nhat khong nam trong he mau cua bai (vang {@code #F6F3C9}, xam
     * {@code #877B6E}) va khong doi duoc qua {@code UIManager}. Neu giu lai, ba
     * chuc nang cua client se no bat trong mot mau khac — dung nhung khong chan
     * nhau. Tu ve dai tab giu duoc dung he mau va khoi dong nhieu mau Nimbus.
     */
    private JPanel buildTabs() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);

        JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        strip.setOpaque(false);
        strip.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.S2, 0));

        tabCards = new JPanel(new CardLayout());
        tabCards.setOpaque(false);

        tabButtons = new java.util.ArrayList<>();
        // 4 tab nhung chi 2 tab dau hien truoc khi dang nhap.
        // KHONG dat setPreferredSize o day: FlatButton da ghi de
        // getPreferredSize() de tu tinh be rong theo do dai chu, nen gan
        // kich thuoc co dinh se bi bo qua.
        String[] titles = {"Đăng ký", "Đăng nhập", "Gửi thư", "Đọc thư"};
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            Theme.FlatButton b = new Theme.FlatButton(titles[i], false);
            b.onClick(() -> selectTab(index));
            strip.add(b);
            tabButtons.add(b);
        }

        tabCards.add(buildRegisterTab(), "tab" + TAB_REGISTER);
        tabCards.add(buildLoginTab(), "tab" + TAB_LOGIN);
        tabCards.add(buildSendTab(), "tab" + TAB_SEND);
        tabCards.add(buildReadTab(), "tab" + TAB_READ);

        wrap.add(strip, BorderLayout.NORTH);
        wrap.add(tabCards, BorderLayout.CENTER);
        selectTab(TAB_REGISTER);
        return wrap;
    }

    /**
     * Bat/tat phan giao dien chi dung khi da dang nhap.
     *
     * <p>Luc moi vao chi co {@code Đăng ký} + {@code Đăng nhập}. Tab
     * {@code Gửi thư}, tab {@code Đọc thư} va card hop thu chi xuat hien sau khi
     * dang nhap thanh cong, cung nut {@code Đăng xuất}.
     *
     * <p>Dung {@code setVisible(false)} chu khong dung {@code setEnabled(false)}:
     * nut/tab bi an biet khoi layout, khong de lai khoang trong rong va khong de lai
     * duong vien cua card — giao dien luc chua dang nhap se gon va sach hon.
     *
     * @param on true sau khi dang nhap thanh cong
     */
    private void setLoggedInUi(boolean on) {
        if (tabButtons != null) {
            tabButtons.get(TAB_SEND).setVisible(on);
            tabButtons.get(TAB_READ).setVisible(on);
        }
        if (mailboxCard != null) {
            mailboxCard.setVisible(on);
        }
        logoutButton.setVisible(on);
        // An ca hang chua nut: khong giu cho trong khi container cha, chi an
        // con lai se de lai mot khoang trong trong card.
        if (logoutRow != null) logoutRow.setVisible(on);

        if (!on) {
            // Neu dang o tab bi an thi phai chuyen ve tab dang hien, neu khong
            // CardLayout se giu nguyen man hinh cua tab da bi an.
            if (tabButtons != null && !tabButtons.get(TAB_SEND).isVisible()) {
                selectTab(TAB_LOGIN);
            }
            unread.clear();
            clearReadView();
        }
        refreshState();
    }

    private void selectTab(int index) {
        ((CardLayout) tabCards.getLayout()).show(tabCards, "tab" + index);
        currentTab = index;
        for (int i = 0; i < tabButtons.size(); i++) {
            boolean on = i == index;
            // Tab dang chon duoc nen ACCENT_WASH + chu accent de doc ngay
            // trang thai, nhung van giu dang nut phang de dong bo voi FlatButton.
            tabButtons.get(i).setFill(
                    on ? Theme.ACCENT_WASH : Theme.SURFACE,
                    on ? Theme.ACCENT_WASH : Theme.ACCENT_WASH,
                    on ? Theme.ACCENT_WASH : Theme.LINE,
                    on ? Theme.ACCENT : Theme.INK_2);
        }
    }

    private JPanel buildConnectCard() {
        JPanel card = new JPanel(new BorderLayout(0, Theme.S2));
        card.setOpaque(false);
        card.setBorder(Theme.rounded(Theme.SURFACE, Theme.LINE, Theme.R_MEDIUM, Theme.S3));

        // Tieu de dat o NORTH chu KHONG dat trong GridBagLayout cua form.
        // GridBagLayout lay do rong cuot chung cho hang: mot JLabel gridwidth=2
        // se quyet dinh luon be rong cua ca hai cot ben duoi. "KẾT NỐI MÁY CHỦ"
        // rong 170px nen se ep hai cot no ra va bop o nhap chi con ~111px -
        // o nho khong tach duoc giua "Khet noi may chu" va "dia chi may chu".
        // Tach tieu de ra khoi form, do rong cot chi con do ten truong quyet dinh.
        card.add(Theme.labelStrong("KẾT NỐI MÁY CHỦ"), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(0, 0, Theme.S1, Theme.S2);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0;
        form.add(Theme.label("Máy chủ"), g);
        g.gridx = 1;
        g.weightx = 1; // o nhap nhan phan du cua be rong
        g.insets = new Insets(0, 0, Theme.S1, 0);
        form.add(hostField, g);

        g.gridx = 0; g.gridy = 1;
        g.weightx = 0;
        g.insets = new Insets(0, 0, Theme.S2, Theme.S2);
        form.add(Theme.label("Cổng UDP"), g);
        g.gridx = 1;
        g.weightx = 1;
        g.insets = new Insets(0, 0, Theme.S2, 0);
        form.add(portField, g);

        // "Ket noi" + "Ngat" + "Dang xuat" rong ~290px ma card chi ~244px.
        // FlowLayout se tu day "Dang xuat" xuong hang hai - nhin ra mot hang le
        // lo. Bo hai hang co y: hang 1 la cap "Ket noi/Ngat", hang 2 la "Dang
        // xuat" tran het be ngang.
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row1.setOpaque(false);
        row1.add(connectButton);
        row1.add(Box.createHorizontalStrut(Theme.S1));
        row1.add(disconnectButton);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row2.setOpaque(false);
        // Chi hien sau khi dang nhap - xem setLoggedInUi(boolean)
        row2.add(logoutButton);
        row2.setVisible(false);

        buttons.add(row1);
        buttons.add(row2);
        logoutRow = row2;

        g.gridx = 0; g.gridy = 2; g.gridwidth = 2;
        g.weightx = 1;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(0, 0, 0, 0);
        form.add(buttons, g);

        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildMailboxCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        card.setBorder(Theme.rounded(Theme.SURFACE, Theme.LINE, Theme.R_MEDIUM, Theme.S3));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.labelStrong("HỘP THƯ"), BorderLayout.WEST);
        mailboxCount.setFont(Theme.MICRO);
        mailboxCount.setForeground(Theme.INK_3);
        head.add(mailboxCount, BorderLayout.EAST);
        head.setToolTipText("Máy chủ được hỏi lại danh sách tệp mỗi giây. "
                + "Thư mới hiện chấm ● và chữ đậm; bấm vào thư để xem nội dung.");
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.S2, 0));

        mailboxList.setFont(Theme.MONO);
        mailboxList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mailboxList.setBackground(Theme.SUNKEN);
        mailboxList.setForeground(Theme.INK_2);
        mailboxList.setOpaque(true);
        mailboxList.setBorder(BorderFactory.createEmptyBorder(Theme.S1, Theme.S2,
                Theme.S1, Theme.S2));

        // Tu ve tung dong de phan biet thu chua doc (dam + mau accent + cham tròn)
        // voi thu da doc. Dung renderer mac dinh thi khong the lam nho doi: no chi
        // doi foreground, khong doi font.
        final java.awt.Font monoBold = Theme.MONO.deriveFont(java.awt.Font.BOLD);
        mailboxList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                String name = String.valueOf(value);
                boolean isUnread = unread.contains(name);
                if (isSelected) {
                    setBackground(Theme.ACCENT);
                    setForeground(Theme.SURFACE);
                    setFont(monoBold);
                } else {
                    setBackground(Theme.SUNKEN);
                    setForeground(isUnread ? Theme.ACCENT : Theme.INK_3);
                    setFont(isUnread ? monoBold : Theme.MONO);
                }
                // Dam "●" + hai khoang trong o "chua doc", o "da doc" de hai
                // dong lua deu nhau.
                setText(isUnread ? "● " + name : "   " + name);
                setBorder(BorderFactory.createEmptyBorder(Theme.S1 - 2, Theme.S2 - 2,
                        Theme.S1 - 2, 0));
                return this;
            }
        });

        // Bam vao thu -> FETCH noi dung va hien o tab "Doc thu".
        // getValueIsAdjusting() loai bo su kien khi keo chuot: chi lay gia tri
        // chot khi nguoi dung da tha chuot.
        mailboxList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            // Dang tu lam moi danh sach, khong phai nguoi dung chon thu.
            if (restoringSelection) return;
            String file = mailboxList.getSelectedValue();
            if (file != null) {
                doFetch(file);
            }
        });

        JScrollPane scroll = new JScrollPane(mailboxList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(Theme.hairline(Theme.LINE));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setOpaque(false);

        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        holder.add(scroll, BorderLayout.CENTER);

        card.add(head, BorderLayout.NORTH);
        card.add(holder, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildRegisterTab() {
        JPanel p = tabPanel();
        int row = 0;
        addRow(p, row++, "Tên đăng nhập", regUserField);
        addRow(p, row++, "Mật khẩu", regPassField);
        addHint(p, row++,
                "Chỉ chữ và số, 3–32 ký tự. Mật khẩu tối đa 64 ký tự.");

        // Hang spacer co weighty = 1 day phan du ra giua form va nut, giu nut
        // o duoi cung cap khi cua so cao. Box glue vo dung o day vi no bi
        // GridBagLayout dat bang preferred size = 0.
        addSpacer(p, row++);
        wide(p, row++, actionRow("Tạo tài khoản", true, () -> doRegister()));
        p.add(gap(Theme.S2));
        wide(p, row++, regResult);
        return p;
    }

    private JPanel buildLoginTab() {
        JPanel p = tabPanel();
        int row = 0;
        addRow(p, row++, "Tên đăng nhập", logUserField);
        addRow(p, row++, "Mật khẩu", logPassField);
        addHint(p, row++,
                "Đăng nhập sẽ lấy danh sách thư trong hộp thư từ máy chủ.");

        addSpacer(p, row++);
        wide(p, row++, actionRow("Vào hộp thư", true, () -> doLogin()));
        p.add(gap(Theme.S2));
        wide(p, row++, logResult);
        return p;
    }

    private JPanel buildSendTab() {
        JPanel p = tabPanel();
        int row = 0;
        addRow(p, row++, "Người gửi", fromField);
        addRow(p, row++, "Người nhận", toField);
        addRow(p, row++, "Tiêu đề", subjectField);

        JPanel bodyHead = new JPanel(new BorderLayout());
        bodyHead.setOpaque(false);
        bodyHead.add(Theme.label("Nội dung"), BorderLayout.WEST);
        bodyCount.setFont(Theme.MICRO);
        bodyCount.setForeground(Theme.INK_3);
        bodyHead.add(bodyCount, BorderLayout.EAST);

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateBodyCount();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateBodyCount();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateBodyCount();
            }
        });

        JScrollPane bodyScroll = new JScrollPane(bodyArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.getVerticalScrollBar().setOpaque(false);

        GridBagConstraints g = baseG();
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        g.insets = new Insets(Theme.S2, 0, 0, 0);
        p.add(bodyHead, g);

        g.gridy = row + 1;
        g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(Theme.S1, 0, Theme.S2, 0);
        p.add(bodyScroll, g);

        JPanel actions = actionRow("Gửi thư đi", true, () -> doSend());
        g.gridy = row + 2;
        g.weighty = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, Theme.S2, 0);
        p.add(actions, g);

        g.gridy = row + 3;
        p.add(sendResult, g);
        return p;
    }

    /**
     * Tab {@code Đọc thư} — xem noi dung 1 file thu da lay qua {@code FETCH}.
     *
     * <p>Chi xuat hien sau khi dang nhap (xem {@link #setLoggedInUi(boolean)}).
     * Noi dung thu duoc tach thanh cac truong header {@code From/To/Subject/Date}
 * *va* phan body, dung dinh dang file ma {@code Mailbox} ghi ra (RFC 5322).
     */
    private JPanel buildReadTab() {
        JPanel p = tabPanel();

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.labelStrong("NỘI DUNG THƯ"), BorderLayout.WEST);
        head.add(readFileName, BorderLayout.EAST);
        readFileName.setFont(Theme.MONO);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.S2, 0));

        GridBagConstraints g = baseG();
        g.gridwidth = 2;
        p.add(head, g);

        // Mot hang "tieu de | gia tri" cua phan header thu. Chi so hang phai chay
        // tu 1 va TANG DAN: cac o ben duoi (the + nut + ket qua) lay so hang
        // tiep theo tu `row` chứ khong ghi so cung. Truoc day chung ghi cu nhung
        // so hang 5/6/7, nen khi them 2 dong IP o giua thi the noi dung bi de
        // CHONG LEN dong IP, va nut bam cung de len dong IP con lai.
        int row = 1;
        g.gridwidth = 1;
        addHeaderRow(p, g, row++, "Từ", readFrom);
        addHeaderRow(p, g, row++, "Đến", readTo);
        addHeaderRow(p, g, row++, "Tiêu đề", readSubject);
        addHeaderRow(p, g, row++, "Ngày", readDate);
        // Hai dong IP: server ghi "Sender-IP" luc giao thu, "Receiver-IP" luc thu
        // duoc doc lan dau. Thu cu khong co dong nay -> hien "(thu cu)".
        addHeaderRow(p, g, row++, "IP người gửi", readSenderIp);
        addHeaderRow(p, g, row++, "IP người nhận", readReceiverIp);

        // Cac gia tri header dung font mono de de doc dia chi va moc thoi gian
        for (JLabel l : new JLabel[]{readFrom, readTo, readSubject, readDate,
                readSenderIp, readReceiverIp}) {
            l.setFont(Theme.MONO);
        }

        readBody.setEditable(false);
        readBody.setLineWrap(true);
        readBody.setWrapStyleWord(true);
        readBody.setOpaque(false);
        JScrollPane bodyScroll = new JScrollPane(readBody,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.setBorder(Theme.inputArea());
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.getVerticalScrollBar().setOpaque(false);

        // Hang tiep theo sau header: noi dung thu.
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(Theme.S2, 0, Theme.S2, 0);
        p.add(bodyScroll, g);

        Theme.FlatButton back = new Theme.FlatButton("← Về hộp thư", false);
        back.onClick(() -> {
            clearReadView();
            mailboxList.clearSelection();
            // Hop thu nam o cot trai luon hien khi da dang nhap: dua focus ve
            // do cho nguoi dung chon thu tiep theo ngay.
            mailboxList.requestFocusInWindow();
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(back);

        g.gridy = row + 1;
        g.weighty = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, Theme.S2, 0);
        p.add(actions, g);

        g.gridy = row + 2;
        p.add(readResult, g);
        return p;
    }

    /** Mot hang "tieu de | gia tri" cua phan header thu. */
    private static void addHeaderRow(JPanel p, GridBagConstraints g, int row,
            String title, JLabel value) {
        g.gridy = row;
        g.gridx = 0;
        g.insets = new Insets(0, Theme.S1, Theme.S1, Theme.S2);
        p.add(Theme.label(title), g);

        g.gridx = 1;
        g.weightx = 1;
        g.insets = new Insets(0, 0, Theme.S1, 0);
        p.add(value, g);
        g.weightx = 0;
    }

    /**
     * Xoa noi dung hien thi cua tab {@code Đọc thư}.
     *
     * <p>Gọi khi dang xuat hoac khi bam "Ve hop thu" — tranh tinh trang "dang
     * doc thu" ma trong thuc te da logout.
     */
    private void clearReadView() {
        readFileName.setText("");
        readFrom.setText("");
        readTo.setText("");
        readSubject.setText("");
        readDate.setText("");
        readSenderIp.setText("");
        readReceiverIp.setText("");
        readBody.setText("");
        readResult.setText("");
    }

    /**
     * Tach noi dung file thu thanh cac truong header va phan body.
     *
     * <p>Dinh dang ghi ra boi {@code Mailbox.buildMailFile()} la:
     * cac dong header, mot dong trong, roi den body. Cac dong header la
     * {@code From:/To:/Subject:/Date:/Message-ID:/MIME-Version:/Content-Type:}.
     * Dong {@code Content-Type} va {@code MIME-Version} khong can hien thi nen
     * bo qua.
     *
     * @param raw noi dung file thu da giai ma
     * @return cac truong tach duoc
     */
    private static MailView parseMail(String raw) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n");
        int split = text.indexOf("\n\n");
        String headerBlock = split < 0 ? text : text.substring(0, split);
        String body = split < 0 ? "" : text.substring(split + 2);

        // "(thu cu)" = file thu duoc tao boi phien ban truoc, khong co dong IP.
        String from = "", to = "", subject = "", date = "";
        String senderIp = OLD_MAIL_MARK, receiverIp = OLD_MAIL_MARK;
        for (String line : headerBlock.split("\n")) {
            int c = line.indexOf(':');
            if (c < 0) continue;
            String key = line.substring(0, c).trim();
            String value = line.substring(c + 1).trim();
            switch (key) {
                case "From" -> from = value;
                case "To" -> to = value;
                case "Subject" -> subject = value;
                case "Date" -> date = value;
                case "Sender-IP" -> senderIp = value;
                case "Receiver-IP" -> receiverIp = value;
                default -> { }
            }
        }
        // Bo 1 dong trong cuoi body (buildMailFile() ghi them \n o cuoi)
        return new MailView(from, to, subject, date, body.stripTrailing(),
                senderIp, receiverIp);
    }

    /** Cac truong cua 1 thu sau khi tach. */
    private record MailView(String from, String to, String subject, String date,
            String body, String senderIp, String receiverIp) {
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Theme.S3, 0, 0, 0));

        JLabel hint = Theme.label(
                "Mọi thao tác chạy ngoài EDT bằng SwingWorker · timeout "
                        + MailClient.TIMEOUT_MS + " ms");
        hint.setFont(Theme.MICRO);
        hint.setForeground(Theme.INK_3);
        footer.add(hint, BorderLayout.WEST);
        return footer;
    }

    // ==================== TIEN ICH BO CUC ====================

    private static JPanel tabPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.PAPER);
        p.setBorder(BorderFactory.createEmptyBorder(Theme.S3, Theme.S4, Theme.S3, Theme.S4));
        return p;
    }

    private static GridBagConstraints baseG() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(0, 0, Theme.S2, 0);
        g.fill = GridBagConstraints.HORIZONTAL;
        return g;
    }

    private void addRow(JPanel p, int row, String label, JComponent field) {
        GridBagConstraints g = baseG();
        g.gridx = 0;
        g.gridy = row;
        g.insets = new Insets(0, 0, Theme.S2, Theme.S3);
        p.add(Theme.label(label), g);

        g.gridx = 1;
        g.weightx = 1;
        g.insets = new Insets(0, 0, Theme.S2, 0);
        p.add(field, g);
    }

    /** Hang rong co the gia doi chieu cao - day het phan du cho noi dung. */
    private void addSpacer(JPanel p, int row) {
        GridBagConstraints g = baseG();
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 2;
        g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        p.add(javax.swing.Box.createVerticalGlue(), g);
    }

    private void addHint(JPanel p, int row, String text) {
        JLabel hint = new JLabel("<html>" + text + "</html>");
        hint.setFont(Theme.MICRO);
        hint.setForeground(Theme.INK_3);
        GridBagConstraints g = baseG();
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 2;
        g.insets = new Insets(0, 0, Theme.S2, 0);
        p.add(hint, g);
    }

    /** Gan component trai het chieu rong cua form (span 2 cot cua GridBag). */
    private void wide(JPanel p, int row, JComponent c) {
        GridBagConstraints g = baseG();
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 2;
        g.weightx = 1;
        p.add(c, g);
    }

    private JPanel actionRow(String text, boolean filled, Runnable action) {
        Theme.FlatButton b = new Theme.FlatButton(text, filled);
        b.setPreferredSize(new Dimension(132, 36));
        b.onClick(action);
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(b);
        return row;
    }

    private static Component gap(int h) {
        return Box.createVerticalStrut(h);
    }

    // ==================== XU LY SU KIEN ====================

    /**
     * Mo socket client. Khong gui request nao - "Ket noi" chi tao {@link MailClient},
     * cong viec that su bat dau o cac nut cua tung tab.
     */
    private void connect() {
        if (busy) return;
        String host = hostField.getText().trim();
        int port = parsePort(portField.getText());

        if (host.isEmpty()) {
            setResult(regResult, "400", "Máy chủ không được để trống.");
            return;
        }
        if (port < 1 || port > 65535) {
            setResult(regResult, "400", "Cổng phải nằm trong khoảng 1–65535.");
            return;
        }

        closeClientQuietly();
        try {
            client = new MailClient(host, port);
            hostField.setEnabled(false);
            portField.setEnabled(false);
            renderStatus();
        } catch (IOException e) {
            client = null;
            setResult(regResult, "500", "Không mở được socket: " + e.getMessage());
            renderStatus();
        }
    }

    private void disconnect() {
        if (busy) return;
        // Ngung poll truoc: neu khong, poll dang chay se thay client == null giua
        // chung va ghi rac trang thai cua client da bi dong.
        stopPolling();
        closeClientQuietly();
        hostField.setEnabled(true);
        portField.setEnabled(true);
        fromField.setText("");
        mailboxModel.clear();
        mailboxList.clearSelection();
        updateMailboxCount();
        setLoggedInUi(false);
        setResult(logResult, "200", "Đã ngắt kết nối.");
    }

    private void closeAll() {
        stopPolling();
        poller.shutdownNow();
        closeClientQuietly();
        dispose();
        System.exit(0);
    }

    private void closeClientQuietly() {
        if (client != null) {
            try {
                client.logout();
            } catch (IOException ignored) {
                // Dang tat - khong can bao loi logout.
            }
            client.close();
            client = null;
        }
    }

    private void doRegister() {
        if (!begin(regResult)) return;
        String user = regUserField.getText().trim();
        String pass = new String(regPassField.getPassword());

        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                return client.register(user, pass);
            }

            @Override
            protected void done() {
                try {
                    String[] r = get();
                    if (Protocol.isOk(r[0])) {
                        regPassField.setText("");
                    }
                    setResult(regResult, r[0], r[1]);
                } catch (Exception e) {
                    showError(regResult, e);
                } finally {
                    // finally bat buoc: neu chi mo khoa trong nhanh try, mot
                    // worker nem loi se de lai busy = true va GUI kẹt vinh vien.
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void doLogin() {
        if (!begin(logResult)) return;
        String user = logUserField.getText().trim();
        String pass = new String(logPassField.getPassword());

        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                return client.login(user, pass);
            }

            @Override
            protected void done() {
                try {
                    String[] r = get();
                    if (Protocol.isOk(r[0])) {
                        fromField.setText(user);
                        logPassField.setText("");
                        // Moi thu trong hop thu deu la thu chua doc.
                        unread.clear();
                        unread.addAll(client.getCurrentMailList());
                        refreshMailbox();
                        setLoggedInUi(true);
                        selectTab(TAB_SEND);
                        startPolling();
                    }
                    setResult(logResult, r[0], r[1]);
                } catch (Exception e) {
                    showError(logResult, e);
                } finally {
                    // finally bat buoc: neu chi mo khoa trong nhanh try, mot
                    // worker nem loi se de lai busy = true va GUI kẹt vinh vien.
                    setBusy(false);
                }
            }
        }.execute();
    }

    /**
     * Đăng xuất: ket thuc phien, xoa phan giao dien rieng tu co phien, dung poll.
     *
     * <p>Giu nguyen ket noi UDP: nguoi dung dang xuat roi dang nhap lai bang tai
     * khoan khac se khong phai bam "Ket noi" lai. {@code MailClient.logout()} da
     * xoa {@code currentUser}, nen {@link #setLoggedInUi(boolean)} lay lai dung
     * trang thai tu client.
     */
    private void logout() {
        if (!begin(logResult)) return;

        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                return client.logout();
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ignored) {
                    // May chu khong phan hoi logout khong chan viec dang xuat o
                    // phia client: server khong luu phien nen an toan xoa o du.
                } finally {
                    stopPolling();
                    mailboxModel.clear();
                    mailboxList.clearSelection();
                    updateMailboxCount();
                    setLoggedInUi(false);
                    setBusy(false);
                    setResult(logResult, "200", "Đã đăng xuất.");
                }
            }
        }.execute();
    }

    /**
     * {@code FETCH} noi dung 1 file thu roi hien o tab {@code Đọc thư}.
     *
     * @param fileName ten file trong hop thu
     */
    private void doFetch(String fileName) {
        if (!begin(readResult)) return;
        String user = client.getCurrentUser();

        setResult(readResult, "", "Đang tải " + fileName + "…");

        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                return client.fetch(user, fileName);
            }

            @Override
            protected void done() {
                try {
                    String[] r = get();
                    if (Protocol.isOk(r[0])) {
                        unread.remove(fileName);
                        MailView v = parseMail(r[1]);
                        readFileName.setText(fileName);
                        readFrom.setText(v.from());
                        readTo.setText(v.to());
                        readSubject.setText(v.subject());
                        readDate.setText(v.date());
                        readSenderIp.setText(v.senderIp());
                        readReceiverIp.setText(v.receiverIp());
                        readBody.setText(v.body());
                        readBody.setCaretPosition(0);
                        selectTab(TAB_READ);
                        setResult(readResult, r[0], "Đã tải " + fileName);
                        // Danh sach phai ve lai de dong chua doc chuyen thanh
                        // da doc ngay tren man hinh.
                        mailboxList.repaint();
                    } else {
                        setResult(readResult, r[0], r[1]);
                    }
                } catch (Exception e) {
                    showError(readResult, e);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    // ==================== POLL HOP THU ====================

    /**
     * Bat vong poll: hoi may chu danh sach thu moi {@link #POLL_INTERVAL_MS} ms.
     *
     * <p>{@code scheduleWithFixedDelay} (bat dau bang do lech sau lan chay truoc)
     * chu khong {@code scheduleAtFixedRate}: neu mot lan poll bi treo thi
     * {@code FixedRate} se dung lai cac lan da len danh sach hang doi, gay dung
     * khi may chu phai tra loi lai ngay lap tuc.
     */
    private void startPolling() {
        stopPolling();
        pollTask = poller.scheduleWithFixedDelay(this::pollOnce,
                POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
    }

    /**
     * Mot lan poll — chay tren luong {@code mail-poll}, KHONG phai EDT.
     *
     * <p>Chi {@code LIST} (danh sach ten file) chu khong {@code FETCH}: thu moi
     * chi can 1 phut hinh, noi dung chi tai khi nguoi dung bam.
     */
    private void pollOnce() {
        MailClient c = client;
        if (c == null || !c.isLoggedIn() || busy) return;

        List<String> before = c.getCurrentMailList();
        List<String> after;
        try {
            String[] r = c.list(c.getCurrentUser());
            // Poll that bai (may chua chay, mat ket noi) thi im lang bo qua —
            // khong ghi de len thanh trang thai, nguoi dung dang dung gi thi
            // khong nen thay bang loi lap lai moi giay.
            if (!Protocol.isOk(r[0])) return;
            after = c.getCurrentMailList();
        } catch (IOException e) {
            return;
        }
        if (after.equals(before)) return;

        List<String> fresh = new ArrayList<>(after);
        fresh.removeAll(before);

        SwingUtilities.invokeLater(() -> {
            // Co the da dang xuat trong luc poll chay tren luong nen nen
            // kiem tra lai truoc khi dong vao giao dien.
            if (client == null || !client.isLoggedIn()) return;
            unread.addAll(fresh);
            refreshMailbox();
            setResult(logResult, "200", "Bạn có " + fresh.size() + " thư mới.");
        });
    }

    private void doSend() {
        if (!begin(sendResult)) return;
        String from = fromField.getText().trim();
        String to = toField.getText().trim();
        String subject = subjectField.getText().trim();
        String body = bodyArea.getText();

        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                return client.send(from, to, subject, body);
            }

            @Override
            protected void done() {
                try {
                    String[] r = get();
                    if (Protocol.isOk(r[0])) {
                        subjectField.setText("");
                        bodyArea.setText("");
                        addDelivered(r[1]);
                    }
                    setResult(sendResult, r[0], r[1]);
                } catch (Exception e) {
                    showError(sendResult, e);
                } finally {
                    // finally bat buoc: neu chi mo khoa trong nhanh try, mot
                    // worker nem loi se de lai busy = true va GUI kẹt vinh vien.
                    setBusy(false);
                }
            }
        }.execute();
    }

    /**
     * Chuan bi cho moi thao tac: phai co client va phai khong co request nao dang chay.
     *
     * <p><b>Vi sao tach ra:</b> truoc day moi noi viet {@code !requireClient(x) ||
     * !setBusy(true)} roi {@code return} — khi dang xu ly, thao tac moi <b>im lang</b>,
     * khong co dong nao hien ra. Nguoi dung bam "Doc thu" khong thay gi, rat de tuong
     * lai la app treo. Bay gio van phai noi ro.
     *
     * @param target nhan cua dung tab dang bam, de bao loi dung cho nhan do
     * @return {@code true} neu san sang bam tiep
     */
    private boolean begin(JLabel target) {
        if (!requireClient(target)) return false;
        if (!setBusy(true)) {
            setResult(target, "", "Đang xử lý yêu cầu trước, thử lại sau.");
            return false;
        }
        return true;
    }

    private boolean requireClient(JLabel target) {
        if (client == null) {
            // Bao loi vao nhan cua DUNG tab dang bam. Truoc day moi noi bat deu
            // ghi vao regResult, nen bam "Gui thu" lai thay "Chua ket noi" o
            // tab Dang ky.
            setResult(target, "400", "Chưa kết nối. Bấm “Kết nối” ở cột bên trái trước.");
            return false;
        }
        return true;
    }

    /**
     * Khoa/mo khoa giao dien trong luc request chay. Tra false neu dang co
     * request chay, de bo qua thao tac moi thay vi cho gui chong.
     */
    private boolean setBusy(boolean on) {
        if (on && busy) return false;
        busy = on;
        refreshState();
        return true;
    }

    // ==================== HIEN THI ====================

    /**
     * Them file vua gui vao danh sach hien thi.
     *
     * <p><b>Vi sao phai lam vay:</b> may chu chi tra danh sach file o phan hoi
     * {@code LOGIN}; phan hoi {@code SEND} chi bao "da luu thanh file nao". Neu
     * chi goi {@link #refreshMailbox()} sau khi gui, panel se khong bao gio doi
     * cho den lan dang nhap ke tiep — nguoi dung vua gui xong ma khong thay thu.
     *
     * <p>Ten file duoc lay tu thong diep phan hoi theo dung dinh dang ma
     * {@code Mailbox} ghi ra ({@code mail_0001.txt}). Neu khong khop dinh dang
     * thi bo qua: giao dien hien danh sach cuoi cung tu lan dang nhap truoc do,
     * khong hong vi du hieu sai.
     */
    private void addDelivered(String serverMessage) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(mail_\\d+\\.txt)$")
                .matcher(serverMessage);
        if (!m.find()) return;
        String name = m.group(1);
        // Ghi nhan vao client TRUOC k do vao model: danh sach phai dung thu tu ma
        // server tra ve, va vong poll phai thay before == after (khong dan dau
        // chua doc cho thu nguoi dung vua tu viet).
        client.noteDelivered(client.getCurrentUser(), name);
        refreshMailbox();
        // Hop thu nam o cot trai nen khong can doi tab — thu da co san ngay ben
        // canh. Chi cuon toi vi tri cua no (khong phai cuon xuong cuoi, vi danh
        // sach da sort theo ten nhu server).
        int at = client.getCurrentMailList().indexOf(name);
        if (at >= 0) mailboxList.ensureIndexIsVisible(at);
        updateMailboxCount();
    }

    private void refreshMailbox() {
        if (client == null) return;
        String selected = mailboxList.getSelectedValue();
        List<String> files = client.getCurrentMailList();
        // Bat listener: clear() + setSelectedIndex() se no "Doc thu" mot lan nua.
        restoringSelection = true;
        try {
            mailboxModel.clear();
            for (String f : files) mailboxModel.addElement(f);
            // Chon lai thu vua xem neu no con ton tai, neu khong danh sach se nhay
            // ve muc dau va nguoi dung mat dau chon dang doc.
            if (selected != null) {
                int at = files.indexOf(selected);
                if (at >= 0) mailboxList.setSelectedIndex(at);
            }
        } finally {
            restoringSelection = false;
        }
        updateMailboxCount();
    }

    private void updateMailboxCount() {
        int n = mailboxModel.getSize();
        mailboxCount.setText(n == 0 ? "chưa có thư" : n + " tệp");
    }

    private void updateBodyCount() {
        int n = bodyArea.getText().length();
        String color = n > Protocol.MAX_BODY_LENGTH ? " (quá giới hạn)" : "";
        bodyCount.setText(n + " / " + Protocol.MAX_BODY_LENGTH + " ký tự" + color);
        bodyCount.setForeground(n > Protocol.MAX_BODY_LENGTH ? Theme.ERR : Theme.INK_3);
    }

    private void refreshState() {
        boolean on = client != null;
        boolean logged = on && client.isLoggedIn();
        connectButton.setEnabled(!on && !busy);
        disconnectButton.setEnabled(on && !busy);
        // Chi cho dang xuat khi dang co phien; hien/anh do setLoggedInUi lo.
        logoutButton.setEnabled(logged && !busy);
        renderStatus();
    }

    private void renderStatus() {
        if (client == null) {
            statusText.setText("Chưa kết nối");
            statusText.setForeground(Theme.INK_3);
        } else if (client.isLoggedIn()) {
            statusText.setText(client.getCurrentUser() + " @ " + client.getServerHost()
                    + ":" + client.getServerPort());
            statusText.setForeground(Theme.INK);
        } else {
            statusText.setText("Đã kết nối · " + client.getServerHost() + ":" + client.getServerPort());
            statusText.setForeground(Theme.INK_2);
        }
    }

    /** Hien thi phan hoi theo mau trang thai: 200 xanh, 4xx/5xx do. */
    private static void setResult(JLabel label, String status, String message) {
        Color c = Protocol.isOk(status) ? Theme.OK : Theme.ERR;
        label.setForeground(c);
        label.setText("<html><b>" + escapeHtml(status) + "</b> · "
                + escapeHtml(message) + "</html>");
    }

    private static void showError(JLabel label, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        label.setForeground(Theme.ERR);
        label.setText("<html><b>Lỗi</b> · " + escapeHtml(String.valueOf(cause.getMessage()))
                + "</html>");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Không có môi trường đồ hoạ. Hãy chạy trên máy có màn hình.");
            System.exit(1);
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Dung look and feel mac dinh neu khong doi duoc.
            }
            Theme.apply();
            new MailClientFrame().setVisible(true);
        });
    }
}