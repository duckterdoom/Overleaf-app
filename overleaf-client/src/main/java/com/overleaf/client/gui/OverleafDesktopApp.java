package com.overleaf.client.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * OVERLEAF DESKTOP (PURE JAVA EDITION) - GIAO DIỆN JAVA SWING
 * 
 * Mã nguồn được thiết kế đơn giản, phân chia khối rõ ràng, có chú thích chi tiết
 * bằng tiếng Việt để người mới bắt đầu học Java Swing có thể dễ dàng hiểu,
 * chỉnh sửa và phát triển thêm theo báo cáo dự án (overleaf_desktop_pure_java_project_report.md).
 * 
 * Các tính năng chính mô phỏng theo báo cáo:
 * - M1: Giao diện soạn thảo mã LaTeX (Code Editor), Cây thư mục dự án (File Tree), Xem trước PDF (PDF Preview).
 * - M2: Biên dịch nền (ProcessBuilder / SwingWorker) không làm đơ giao diện.
 * - M3: Quản lý kết nối phòng cộng tác (Room & Collaborators list, TCP Socket status).
 * - M4: Nhật ký đồng bộ thời gian thực (Real-time Sync logs).
 * - M5: Trạng thái cơ sở dữ liệu MongoDB và lưu trữ file.
 */
public class OverleafDesktopApp extends JFrame {

    // --- CÁC THÀNH PHẦN GIAO DIỆN CHÍNH ---
    private JTextArea codeEditor;          // Khung soạn thảo mã LaTeX
    private JTextArea logArea;             // Khung hiển thị log biên dịch (latexmk)
    private JTree fileTree;                // Cây thư mục dự án
    private DefaultListModel<String> collaboratorsModel; // Danh sách người cùng soạn thảo
    private PdfPreviewPanel pdfPreviewPanel; // Khung xem trước tài liệu PDF
    private JLabel statusLabel;            // Thanh trạng thái dưới đáy
    private JLabel connectionLabel;        // Trạng thái kết nối Server/Phòng
    private JProgressBar compileProgressBar; // Thanh tiến trình khi đang biên dịch
    private JButton btnRecompile;          // Nút bấm "Recompile"
    private JComboBox<String> compilerCombo; // Chọn trình biên dịch (pdfLaTeX, XeLaTeX)

    // Dữ liệu mẫu ban đầu cho tài liệu LaTeX
    private static final String DEFAULT_LATEX_CODE = 
        "\\documentclass{article}\n" +
        "\\usepackage[utf8]{vietnam}\n" +
        "\\usepackage{amsmath}\n" +
        "\\usepackage{graphicx}\n\n" +
        "\\title{\\textbf{Overleaf Desktop (Pure Java Edition)}}\n" +
        "\\author{Nhóm XX - Lớp Java Software Development}\n" +
        "\\date{\\today}\n\n" +
        "\\begin{document}\n\n" +
        "\\maketitle\n\n" +
        "\\begin{abstract}\n" +
        "Báo cáo xây dựng trình soạn thảo LaTeX cộng tác thời gian thực\n" +
        "hoàn toàn bằng Java Swing, TCP Socket, ProcessBuilder và MongoDB.\n" +
        "\\end{abstract}\n\n" +
        "\\section{Giới thiệu}\n" +
        "Dự án cho phép người dùng biên dịch tài liệu LaTeX trực tiếp trên\n" +
        "máy tính cá nhân (offline-capable) thông qua ProcessBuilder,\n" +
        "đồng thời kết nối phòng làm việc nhóm qua Java TCP Sockets.\n\n" +
        "\\section{Công thức toán học}\n" +
        "Ví dụ công thức phương trình Einstein:\n" +
        "\\begin{equation}\n" +
        "    E = mc^2\n" +
        "\\end{equation}\n\n" +
        "\\section{Kết luận}\n" +
        "Ứng dụng chạy mượt mà trên môi trường máy bàn không phụ thuộc cloud.\n\n" +
        "\\end{document}\n";

    public OverleafDesktopApp() {
        // Thiết lập tiêu đề cửa sổ, kích thước và hành vi đóng
        setTitle("Overleaf Desktop - Pure Java Edition [Project: Research_Paper_2026]");
        setSize(1200, 750);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Canh giữa màn hình

        // 1. Tạo thanh Menu phía trên cùng
        setJMenuBar(createMenuBar());

        // 2. Tạo thanh công cụ (Toolbar) chứa các nút Recompile, Chọn Compiler, Kết nối
        add(createToolBar(), BorderLayout.NORTH);

        // 3. Tạo khu vực làm việc chính (3 cột: Cây thư mục | Soạn thảo mã | Xem trước PDF)
        add(createMainWorkspace(), BorderLayout.CENTER);

        // 4. Tạo thanh trạng thái phía dưới (Status bar)
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    /**
     * 1. THANH MENU (Menu Bar)
     * Cung cấp các thao tác File, Dự án, Kết nối phòng và Trợ giúp.
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Menu File
        JMenu fileMenu = new JMenu("File");
        JMenuItem itemNew = new JMenuItem("Tạo file mới...");
        JMenuItem itemOpen = new JMenuItem("Mở dự án...");
        JMenuItem itemSave = new JMenuItem("Lưu (Ctrl + S)");
        JMenuItem itemExit = new JMenuItem("Thoát");

        itemSave.addActionListener(e -> saveFile());
        itemExit.addActionListener(e -> System.exit(0));

        fileMenu.add(itemNew);
        fileMenu.add(itemOpen);
        fileMenu.add(itemSave);
        fileMenu.addSeparator();
        fileMenu.add(itemExit);

        // Menu Biên dịch & Chạy
        JMenu projectMenu = new JMenu("Project");
        JMenuItem itemCompile = new JMenuItem("Recompile (F5)");
        itemCompile.addActionListener(e -> triggerRecompile());
        JMenuItem itemClean = new JMenuItem("Dọn dẹp file tạm (.aux, .log)");
        itemClean.addActionListener(e -> appendLog("[CLEAN] Đã dọn dẹp các tệp tạm thời."));
        projectMenu.add(itemCompile);
        projectMenu.add(itemClean);

        // Menu Cộng tác (Networking)
        JMenu roomMenu = new JMenu("Collaboration");
        JMenuItem itemConnect = new JMenuItem("Kết nối phòng (TCP Socket)...");
        itemConnect.addActionListener(e -> showConnectDialog());
        JMenuItem itemDisconnect = new JMenuItem("Ngắt kết nối phòng");
        itemDisconnect.addActionListener(e -> disconnectRoom());
        roomMenu.add(itemConnect);
        roomMenu.add(itemDisconnect);

        // Menu Trợ giúp
        JMenu helpMenu = new JMenu("Help");
        JMenuItem itemAbout = new JMenuItem("Thông tin dự án");
        itemAbout.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Overleaf Desktop (Pure Java Edition)\n" +
                "Môn học: Java Software Development\n" +
                "Công nghệ: Java Swing, ProcessBuilder, TCP Sockets, MongoDB\n" +
                "Phiên bản: 1.0 (Student Edition)",
                "Giới thiệu", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(itemAbout);

        menuBar.add(fileMenu);
        menuBar.add(projectMenu);
        menuBar.add(roomMenu);
        menuBar.add(helpMenu);

        return menuBar;
    }

    /**
     * 2. THANH CÔNG CỤ (ToolBar)
     * Chứa nút Recompile to và rõ ràng, chọn compiler, trạng thái kết nối.
     */
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false); // Cố định toolbar, không cho kéo thả ra ngoài
        toolBar.setBorder(new EmptyBorder(5, 8, 5, 8));

        // Nút Recompile (Màu xanh nổi bật giống Overleaf thật)
        btnRecompile = new JButton("▶ Recompile");
        btnRecompile.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRecompile.setBackground(new Color(40, 167, 69)); // Màu xanh Overleaf
        btnRecompile.setForeground(Color.WHITE);
        btnRecompile.setFocusPainted(false);
        btnRecompile.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRecompile.addActionListener(e -> triggerRecompile());
        toolBar.add(btnRecompile);

        toolBar.add(Box.createHorizontalStrut(10));

        // Hộp chọn trình biên dịch
        toolBar.add(new JLabel("Compiler: "));
        compilerCombo = new JComboBox<>(new String[]{"pdfLaTeX (Local)", "XeLaTeX (Unicode)", "LuaLaTeX"});
        compilerCombo.setMaximumSize(new Dimension(160, 28));
        toolBar.add(compilerCombo);

        toolBar.add(Box.createHorizontalStrut(15));

        // Nút Lưu file nhanh
        JButton btnSave = new JButton("💾 Lưu");
        btnSave.addActionListener(e -> saveFile());
        toolBar.add(btnSave);

        toolBar.add(Box.createHorizontalStrut(8));

        // Nút Kết nối phòng cộng tác
        JButton btnRoom = new JButton("🌐 Kết nối phòng...");
        btnRoom.addActionListener(e -> showConnectDialog());
        toolBar.add(btnRoom);

        toolBar.add(Box.createHorizontalGlue()); // Đẩy các phần tử còn lại về bên phải

        // Nhãn hiển thị trạng thái kết nối
        connectionLabel = new JLabel("● Đang chạy cục bộ (Offline)");
        connectionLabel.setForeground(new Color(108, 117, 125));
        connectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        toolBar.add(connectionLabel);

        return toolBar;
    }

    /**
     * 3. KHU VỰC LÀM VIỆC CHÍNH (Main Workspace)
     * Chia làm 3 phần bằng JSplitPane:
     * - Trái: File Tree & Danh sách người dùng trong phòng
     * - Giữa: Trình soạn thảo mã LaTeX & Khung Log biên dịch
     * - Phải: Xem trước PDF (PDF Preview)
     */
    private JComponent createMainWorkspace() {
        // Cột trái: Quản lý File và Người cộng tác
        JComponent leftPanel = createLeftPanel();

        // Cột giữa: Editor + Console Log
        JComponent centerPanel = createCenterEditorPanel();

        // Cột phải: PDF Preview
        pdfPreviewPanel = new PdfPreviewPanel();

        // SplitPane giữa (Code Editor) và phải (PDF Preview)
        JSplitPane editorAndPreviewSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, centerPanel, pdfPreviewPanel);
        editorAndPreviewSplit.setResizeWeight(0.5); // Chia đều 50% - 50%
        editorAndPreviewSplit.setContinuousLayout(true);

        // SplitPane ngoài cùng: (Trái) và (Giữa + Phải)
        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, leftPanel, editorAndPreviewSplit);
        mainSplit.setResizeWeight(0.18); // Cột trái chiếm ~18% chiều rộng
        mainSplit.setContinuousLayout(true);

        return mainSplit;
    }

    /**
     * CỘT BÊN TRÁI: Cây thư mục dự án + Danh sách thành viên cùng phòng
     */
    private JComponent createLeftPanel() {
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Project Files (Cây thư mục)
        JPanel filesPanel = new JPanel(new BorderLayout());
        filesPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("📁 Research_Paper_2026");
        DefaultMutableTreeNode mainTex = new DefaultMutableTreeNode("📄 main.tex");
        DefaultMutableTreeNode bibFile = new DefaultMutableTreeNode("📄 references.bib");
        DefaultMutableTreeNode figuresDir = new DefaultMutableTreeNode("📁 figures");
        figuresDir.add(new DefaultMutableTreeNode("🖼 architecture.png"));
        figuresDir.add(new DefaultMutableTreeNode("🖼 results_chart.png"));
        DefaultMutableTreeNode sectionsDir = new DefaultMutableTreeNode("📁 sections");
        sectionsDir.add(new DefaultMutableTreeNode("📄 intro.tex"));
        sectionsDir.add(new DefaultMutableTreeNode("📄 evaluation.tex"));

        root.add(mainTex);
        root.add(bibFile);
        root.add(figuresDir);
        root.add(sectionsDir);

        fileTree = new JTree(new DefaultTreeModel(root));
        fileTree.setRootVisible(true);
        // Khi bấm vào file trên cây thư mục
        fileTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selected = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
            if (selected != null && selected.isLeaf()) {
                appendLog("[FILE] Đang mở tệp: " + selected.getUserObject());
            }
        });

        JScrollPane treeScroll = new JScrollPane(fileTree);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Cấu trúc dự án"));
        filesPanel.add(treeScroll, BorderLayout.CENTER);

        // Nút thêm file nhanh
        JPanel fileButtonsPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        JButton btnNewFile = new JButton("+ File");
        btnNewFile.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Nhập tên file mới (ví dụ: chapter1.tex):");
            if (name != null && !name.trim().isEmpty()) {
                root.add(new DefaultMutableTreeNode("📄 " + name.trim()));
                ((DefaultTreeModel) fileTree.getModel()).reload();
                appendLog("[FILE] Đã tạo file mới: " + name.trim());
            }
        });
        JButton btnRefresh = new JButton("🔄 Làm mới");
        btnRefresh.addActionListener(e -> appendLog("[FILE] Đã làm mới danh sách tệp từ ổ cứng."));
        fileButtonsPanel.add(btnNewFile);
        fileButtonsPanel.add(btnRefresh);
        filesPanel.add(fileButtonsPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Tệp tin", filesPanel);

        // Tab 2: Collaborators (Mô phỏng M3 & M4 - Socket & Concurrency)
        JPanel collabPanel = new JPanel(new BorderLayout());
        collabPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        collaboratorsModel = new DefaultListModel<>();
        collaboratorsModel.addElement("👤 Bạn (Chủ phòng - Host)");
        collaboratorsModel.addElement("🟢 Alice (Đang sửa dòng 24)");
        collaboratorsModel.addElement("🟢 Bob (Đang xem PDF)");

        JList<String> collabList = new JList<>(collaboratorsModel);
        collabList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane collabScroll = new JScrollPane(collabList);
        collabScroll.setBorder(BorderFactory.createTitledBorder("Đang trực tuyến (3)"));

        collabPanel.add(collabScroll, BorderLayout.CENTER);

        JLabel collabTip = new JLabel("<html><small>Đồng bộ văn bản thời gian thực qua Java TCP Sockets.</small></html>");
        collabTip.setForeground(Color.GRAY);
        collabPanel.add(collabTip, BorderLayout.SOUTH);

        tabbedPane.addTab("Cộng tác", collabPanel);

        return tabbedPane;
    }

    /**
     * CỘT Ở GIỮA: Soạn thảo mã LaTeX (Trên) + Nhật ký biên dịch Log/Console (Dưới)
     */
    private JComponent createCenterEditorPanel() {
        JPanel editorPanel = new JPanel(new BorderLayout());

        // Khung soạn thảo văn bản
        codeEditor = new JTextArea();
        codeEditor.setText(DEFAULT_LATEX_CODE);
        codeEditor.setFont(new Font("Consolas", Font.PLAIN, 14));
        codeEditor.setTabSize(4);
        codeEditor.setLineWrap(false);

        // Đếm vị trí con trỏ (Dòng, Cột) khi gõ phím
        codeEditor.addCaretListener(e -> {
            try {
                int caretPos = codeEditor.getCaretPosition();
                int line = codeEditor.getLineOfOffset(caretPos) + 1;
                int col = caretPos - codeEditor.getLineStartOffset(line - 1) + 1;
                statusLabel.setText(String.format(" Vị trí: Dòng %d, Cột %d | Tổng ký tự: %d", line, col, codeEditor.getText().length()));
            } catch (Exception ignored) {}
        });

        JScrollPane editorScroll = new JScrollPane(codeEditor);
        editorScroll.setBorder(BorderFactory.createTitledBorder("LaTeX Source Code (main.tex)"));

        // Khung nhật ký log (Console / ProcessBuilder output)
        logArea = new JTextArea(7, 30);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30)); // Nền đen giống terminal
        logArea.setForeground(new Color(220, 220, 220)); // Chữ trắng sáng
        logArea.setText("--- Overleaf Desktop Kernel Ready ---\n[INIT] Bộ máy biên dịch: ProcessBuilder (latexmk/pdflatex ready)\n[INIT] Database kết nối: MongoDB (Local Repository ready)\n");

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Nhật ký biên dịch (Compiler Console Output)"));

        // Chia màn hình giữa thành 2 phần: Editor chiếm 70%, Log chiếm 30%
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, logScroll);
        splitPane.setResizeWeight(0.72);
        splitPane.setContinuousLayout(true);

        editorPanel.add(splitPane, BorderLayout.CENTER);
        return editorPanel;
    }

    /**
     * 4. THANH TRẠNG THÁI (Status Bar)
     * Hiển thị thông tin tiến trình biên dịch, vị trí con trỏ và MongoDB state.
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(5, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                new EmptyBorder(4, 10, 4, 10)
        ));

        statusLabel = new JLabel(" Sẵn sàng. Nhấn 'Recompile' để biên dịch sang PDF.");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Bên phải thanh trạng thái: Progress Bar & Thông báo Database
        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        compileProgressBar = new JProgressBar();
        compileProgressBar.setPreferredSize(new Dimension(140, 16));
        compileProgressBar.setVisible(false); // Ẩn khi không biên dịch

        JLabel dbStatus = new JLabel("MongoDB: Đồng bộ ✓");
        dbStatus.setForeground(new Color(40, 140, 60));
        dbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        rightStatus.add(compileProgressBar);
        rightStatus.add(dbStatus);

        statusBar.add(rightStatus, BorderLayout.EAST);
        return statusBar;
    }

    /**
     * HÀNH ĐỘNG: BIÊN DỊCH TÀI LIỆU (M2 - ProcessBuilder & Concurrency)
     * 
     * Sử dụng SwingWorker để thực hiện tác vụ nặng ở luồng nền (Background Thread),
     * giúp giao diện người dùng (EDT) KHÔNG BAO GIỜ BỊ ĐƠ LAG trong quá trình chạy.
     */
    private void triggerRecompile() {
        btnRecompile.setEnabled(false);
        compileProgressBar.setVisible(true);
        compileProgressBar.setIndeterminate(true); // Hiệu ứng chạy qua lại
        statusLabel.setText(" Đang biên dịch tài liệu LaTeX (ProcessBuilder: " + compilerCombo.getSelectedItem() + ")...");
        
        appendLog("\n==================================================");
        appendLog("[BUILD] Bắt đầu quá trình biên dịch: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        appendLog("[BUILD] Lệnh: latexmk -pdf -interaction=nonstopmode main.tex");

        // SwingWorker<ReturnType, IntermediateType>
        SwingWorker<Boolean, String> compilerTask = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                // Mô phỏng các giai đoạn biên dịch thực tế của LaTeX
                publish("[INFO] Kiểm tra tệp nguồn main.tex và các gói thư viện...");
                Thread.sleep(300);

                publish("[INFO] Chạy pdflatex (Pass 1/2) - Tạo bảng chỉ mục và liên kết...");
                Thread.sleep(600);

                publish("[INFO] Xử lý tài liệu tham khảo BibTeX (references.bib)...");
                Thread.sleep(400);

                publish("[INFO] Chạy pdflatex (Pass 2/2) - Hoàn thiện các tham chiếu chéo...");
                Thread.sleep(500);

                publish("[SUCCESS] Quá trình tạo PDF hoàn tất. Tệp đầu ra: main.pdf (1 trang, 42 KB)");
                return true;
            }

            @Override
            protected void process(List<String> chunks) {
                // Nhận thông tin từ background thread và in ra giao diện log
                for (String msg : chunks) {
                    appendLog(msg);
                }
            }

            @Override
            protected void done() {
                // Khi hoàn tất (chạy trên Main UI Thread)
                btnRecompile.setEnabled(true);
                compileProgressBar.setVisible(false);
                statusLabel.setText(" Biên dịch thành công! Đã cập nhật bản xem trước PDF.");
                
                // Cập nhật lại nội dung trên khung vẽ PDF
                pdfPreviewPanel.updatePreviewContent(codeEditor.getText());
            }
        };

        // Kích hoạt luồng chạy ngầm
        compilerTask.execute();
    }

    /**
     * Hộp thoại kết nối phòng làm việc nhóm (M3 - Networking TCP Sockets)
     */
    private void showConnectDialog() {
        JTextField hostField = new JTextField("127.0.0.1");
        JTextField portField = new JTextField("8080");
        JTextField roomIdField = new JTextField("ROOM-101");
        JTextField userField = new JTextField("SinhVienJava");

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("Địa chỉ Server:"));
        form.add(hostField);
        form.add(new JLabel("Cổng (Port TCP):"));
        form.add(portField);
        form.add(new JLabel("Mã phòng (Room ID):"));
        form.add(roomIdField);
        form.add(new JLabel("Tên hiển thị:"));
        form.add(userField);

        int result = JOptionPane.showConfirmDialog(this, form, 
                "Kết nối Server Cộng tác (TCP Socket)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            String room = roomIdField.getText().trim();
            String user = userField.getText().trim();

            connectionLabel.setText("● Đã kết nối: " + room + " (" + host + ":" + port + ")");
            connectionLabel.setForeground(new Color(40, 167, 69)); // Màu xanh lá
            appendLog(String.format("[NETWORK] Đã kết nối tới Server %s:%s | Phòng: %s | Người dùng: %s", host, port, room, user));
            JOptionPane.showMessageDialog(this, "Kết nối phòng thành công! Sẵn sàng soạn thảo cộng tác.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Ngắt kết nối phòng
     */
    private void disconnectRoom() {
        connectionLabel.setText("● Đang chạy cục bộ (Offline)");
        connectionLabel.setForeground(new Color(108, 117, 125));
        appendLog("[NETWORK] Đã rời phòng làm việc nhóm. Chuyển sang chế độ lưu cục bộ.");
    }

    /**
     * Lưu file
     */
    private void saveFile() {
        appendLog("[SAVE] Đã lưu tệp 'main.tex' thành công vào thư mục dự án.");
        statusLabel.setText(" Đã lưu tệp lúc: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        JOptionPane.showMessageDialog(this, "Tệp đã được lưu thành công!", "Lưu tệp", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Thêm dòng tin nhắn vào khung Log
     */
    private void appendLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength()); // Tự động cuộn xuống dòng mới nhất
    }

    // =========================================================================
    // LỚP KHUNG XEM TRƯỚC PDF (PDF PREVIEW PANEL)
    // Mô phỏng việc hiển thị trang giấy A4 tài liệu LaTeX được render bằng Java Graphics2D
    // =========================================================================
    public static class PdfPreviewPanel extends JPanel {
        private String previewText = "";
        private int zoomLevel = 100; // 100%

        public PdfPreviewPanel() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createTitledBorder("PDF Preview (Trang 1 / 1)"));

            // Thanh công cụ nhỏ phía trên của PDF: Zoom, Trang
            JToolBar pdfBar = new JToolBar();
            pdfBar.setFloatable(false);

            JButton btnZoomIn = new JButton("🔍 +");
            JButton btnZoomOut = new JButton("🔍 -");
            JButton btnFit = new JButton("Vừa trang");
            JLabel zoomLabel = new JLabel(" 100% ");

            btnZoomIn.addActionListener(e -> {
                if (zoomLevel < 150) {
                    zoomLevel += 10;
                    zoomLabel.setText(" " + zoomLevel + "% ");
                    repaint();
                }
            });

            btnZoomOut.addActionListener(e -> {
                if (zoomLevel > 60) {
                    zoomLevel -= 10;
                    zoomLabel.setText(" " + zoomLevel + "% ");
                    repaint();
                }
            });

            btnFit.addActionListener(e -> {
                zoomLevel = 100;
                zoomLabel.setText(" 100% ");
                repaint();
            });

            pdfBar.add(new JLabel("Xem trước: "));
            pdfBar.add(btnZoomIn);
            pdfBar.add(btnZoomOut);
            pdfBar.add(btnFit);
            pdfBar.add(zoomLabel);
            pdfBar.add(Box.createHorizontalGlue());
            pdfBar.add(new JLabel("Độ phân giải: 300 DPI  "));

            add(pdfBar, BorderLayout.NORTH);

            // Bề mặt vẽ trang giấy PDF
            PdfCanvas canvas = new PdfCanvas();
            JScrollPane scrollPane = new JScrollPane(canvas);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            add(scrollPane, BorderLayout.CENTER);
        }

        public void updatePreviewContent(String code) {
            this.previewText = code;
            repaint();
        }

        /**
         * Canvas vẽ giả lập một trang giấy A4 màu trắng với định dạng chuẩn học thuật
         */
        private class PdfCanvas extends JPanel {
            public PdfCanvas() {
                setBackground(new Color(230, 230, 230)); // Nền xám bao quanh tờ giấy trắng
            }

            @Override
            public Dimension getPreferredSize() {
                // Tỉ lệ trang giấy A4 (chuẩn ~ 595 x 842 pt)
                int width = (int) (520 * (zoomLevel / 100.0));
                int height = (int) (740 * (zoomLevel / 100.0));
                return new Dimension(width + 40, height + 40);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                
                // Khử răng cưa chữ và hình để trang PDF hiển thị sắc nét
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                double scale = zoomLevel / 100.0;
                int paperWidth = (int) (500 * scale);
                int paperHeight = (int) (700 * scale);

                // Tọa độ vẽ tờ giấy A4 ở chính giữa
                int x = (getWidth() - paperWidth) / 2;
                int y = 20;

                // 1. Vẽ bóng đổ của tờ giấy (Drop shadow)
                g2.setColor(new Color(180, 180, 180));
                g2.fillRect(x + 5, y + 5, paperWidth, paperHeight);

                // 2. Vẽ tờ giấy trắng
                g2.setColor(Color.WHITE);
                g2.fillRect(x, y, paperWidth, paperHeight);
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawRect(x, y, paperWidth, paperHeight);

                // 3. Vẽ nội dung bài báo khoa học chuẩn LaTeX lên giấy
                int marginX = x + (int) (40 * scale);
                int currentY = y + (int) (50 * scale);

                // Tiêu đề bài viết
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Serif", Font.BOLD, (int) (16 * scale)));
                g2.drawString("Overleaf Desktop (Pure Java Edition)", marginX + (int)(15 * scale), currentY);

                // Tác giả
                currentY += (int) (22 * scale);
                g2.setFont(new Font("Serif", Font.PLAIN, (int) (11 * scale)));
                g2.setColor(new Color(60, 60, 60));
                g2.drawString("Nhóm XX - Lớp Java Software Development", marginX + (int)(60 * scale), currentY);

                // Ngày tháng
                currentY += (int) (16 * scale);
                g2.setFont(new Font("Serif", Font.ITALIC, (int) (10 * scale)));
                g2.drawString(new SimpleDateFormat("dd MMMM yyyy").format(new Date()), marginX + (int)(110 * scale), currentY);

                // Đường kẻ ngang mỏng
                currentY += (int) (18 * scale);
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawLine(marginX, currentY, marginX + paperWidth - (int)(80 * scale), currentY);

                // Phần Tóm tắt (Abstract)
                currentY += (int) (25 * scale);
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Serif", Font.BOLD, (int) (11 * scale)));
                g2.drawString("Abstract", marginX + (int)(140 * scale), currentY);

                currentY += (int) (18 * scale);
                g2.setFont(new Font("Serif", Font.ITALIC, (int) (10 * scale)));
                g2.drawString("This project presents a self-hosted, real-time collaborative LaTeX", marginX, currentY);
                currentY += (int) (15 * scale);
                g2.drawString("editor built entirely in Java Swing, TCP Sockets and MongoDB.", marginX, currentY);

                // Mục 1: Giới thiệu
                currentY += (int) (30 * scale);
                g2.setFont(new Font("Serif", Font.BOLD, (int) (13 * scale)));
                g2.drawString("1. Giới thiệu (Introduction)", marginX, currentY);

                currentY += (int) (20 * scale);
                g2.setFont(new Font("Serif", Font.PLAIN, (int) (10.5 * scale)));
                g2.drawString("A collaborative LaTeX editor combines core Java topics (OOP, GUI,", marginX, currentY);
                currentY += (int) (15 * scale);
                g2.drawString("Networking, Concurrency, I/O, Database) in one cohesive desktop system.", marginX, currentY);

                // Mục 2: Công thức toán học
                currentY += (int) (30 * scale);
                g2.setFont(new Font("Serif", Font.BOLD, (int) (13 * scale)));
                g2.drawString("2. Công thức toán học (Equations)", marginX, currentY);

                currentY += (int) (28 * scale);
                // Vẽ công thức E = mc^2 nổi bật ở giữa
                g2.setFont(new Font("Serif", Font.ITALIC | Font.BOLD, (int) (14 * scale)));
                g2.drawString("E = m c²", marginX + (int)(130 * scale), currentY);
                g2.setFont(new Font("Serif", Font.PLAIN, (int) (10 * scale)));
                g2.drawString("(1)", marginX + paperWidth - (int)(100 * scale), currentY);

                // Số trang dưới chân giấy
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("Serif", Font.PLAIN, (int) (10 * scale)));
                g2.drawString("- 1 -", x + paperWidth / 2 - 10, y + paperHeight - (int)(20 * scale));

                g2.dispose();
            }
        }
    }

    // =========================================================================
    // HÀM MAIN: KHỞI CHẠY ỨNG DỤNG
    // =========================================================================
    public static void main(String[] args) {
        // Thiết lập giao diện theo chuẩn hệ điều hành (Windows Look and Feel)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Chạy ứng dụng trên Event Dispatch Thread (EDT) theo chuẩn Java Swing
        SwingUtilities.invokeLater(() -> {
            OverleafDesktopApp app = new OverleafDesktopApp();
            app.setVisible(true);
        });
    }
}

