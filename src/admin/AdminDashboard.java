package admin;

import domain.UserSession;
import domain.Student;
import admin.services.AdminService;
import admin.services.BackupService;
import admin.services.ReportsService;
import login.DatabaseConfig;
import login.LoginWindow;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.sql.*;

/**
 * AdminDashboard - updated to use AdminService + maintenance & audit.
 * Replace your old file with this one.
 */
public class AdminDashboard extends JFrame {
    // --- Meta-App UI Theme Colors ---
    private static final Color DARK_BG = new Color(24, 25, 26);
    private static final Color DARK_HOVER = new Color(58, 59, 60);
    private static final Color LIGHT_TEXT = Color.WHITE;
    private static final Color LOGOUT_RED = new Color(220, 53, 69); // Red color for logout
    private static final Color LIGHT_BG = new Color(235, 237, 240);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color HEADER_TEXT = new Color(20, 20, 20);
    private static final Color BORDER_GRAY = new Color(204, 204, 204);
    private static final Color ACCENT_BLUE = new Color(27, 116, 228);

    // UI controllers
    private final UserSession userSession;
    private final CardLayout mainCardLayout;
    private final JPanel mainContentPanel;
    // keep a reference to the sidebar so quick-links can activate its buttons reliably
    private JPanel sidebarPanel;
    private JButton activeModuleButton;
    private JLabel totalStudentsValueLabel;
    private JLabel totalInstructorsValueLabel;

    // class-level action buttons (so maintenance state can disable them)
    private JButton addStudentButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton refreshButton;
    private JButton updateFeesButton;
    private JButton backupButton;
    private JButton exportAllButton;
    private JCheckBox maintenanceToggle;
    private JLabel maintenanceBanner = new JLabel();

    // Services
    private final AdminService adminService = new AdminService();
    private final BackupService backupService = new BackupService();
    private final ReportsService reportsService = new ReportsService();
    // Runnable to refresh courses table; set by course panel so other panels can trigger it
    private Runnable reloadCourses;

    public AdminDashboard(UserSession session) {
        this.userSession = session;

        // Basic auth check
        if (session == null || !session.isAuthenticated() || !session.hasAnyRole("ADMIN", "SUPERADMIN")) {
            JOptionPane.showMessageDialog(null, "Access denied. Admin login required.", "Unauthorized", JOptionPane.ERROR_MESSAGE);
            throw new SecurityException("Unauthorized access to Admin Dashboard");
        }

        setTitle("Admin Dashboard | University ERP");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        mainCardLayout = new CardLayout();
        mainContentPanel = new JPanel(mainCardLayout);
        mainContentPanel.setBackground(LIGHT_BG);

        sidebarPanel = createSidebar();

        // --- Add all panels to the CardLayout ---
        mainContentPanel.add(createDashboardPanel(), "Dashboard");
        mainContentPanel.add(createManageStudentsPanel(), "Students");
        mainContentPanel.add(createInstructorManagementPanel(), "Instructors");
        mainContentPanel.add(createManagementPanel("Admins"), "Admins");
        mainContentPanel.add(createCourseManagementPanel(), "Courses");
        mainContentPanel.add(createOfferCoursesPanel(), "OfferCourses");
        mainContentPanel.add(createOfferedCoursesPanel(), "OfferedCourses");
        mainContentPanel.add(createStudentEnrollmentsPanel(), "StudentEnrollments");
        mainContentPanel.add(createFeeStructurePanel(), "FeeStructure");
        mainContentPanel.add(createMaintenancePanel(), "Maintenance");
        mainContentPanel.add(createReportsPanel(), "Reports");

        add(sidebarPanel, BorderLayout.WEST);
        add(mainContentPanel, BorderLayout.CENTER);

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setVisible(true);

        SwingUtilities.invokeLater(() -> {
            if (sidebarPanel != null) {
                for (Component comp : sidebarPanel.getComponents()) {
                    if (comp instanceof JButton && "Dashboard".equals(((JButton) comp).getName())) {
                        selectButton((JButton) comp, "Dashboard");
                        break;
                    }
                }
            }
        });
    }

    private void saveFeesToCsv(javax.swing.table.DefaultTableModel tuitionModel, javax.swing.table.DefaultTableModel partModel, javax.swing.table.DefaultTableModel hostelModel) throws java.io.IOException {
        java.nio.file.Path base = java.nio.file.Path.of("fees_export");
        java.nio.file.Files.createDirectories(base);

        // Tuition
        try (java.io.FileWriter fw = new java.io.FileWriter(base.resolve("tuition_fees.csv").toFile())) {
            fw.write("Program,Year,Tuition Fee,Total\n");
            for (int i = 0; i < tuitionModel.getRowCount(); i++) {
                fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    safeString(tuitionModel.getValueAt(i,0)), safeString(tuitionModel.getValueAt(i,1)), safeString(tuitionModel.getValueAt(i,2)), safeString(tuitionModel.getValueAt(i,3))));
            }
        }

        // Part-wise
        try (java.io.FileWriter fw = new java.io.FileWriter(base.resolve("part_fees.csv").toFile())) {
            fw.write("Program,Part,Fee,Notes\n");
            for (int i = 0; i < partModel.getRowCount(); i++) {
                fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    safeString(partModel.getValueAt(i,0)), safeString(partModel.getValueAt(i,1)), safeString(partModel.getValueAt(i,2)), safeString(partModel.getValueAt(i,3))));
            }
        }

        // Hostel
        try (java.io.FileWriter fw = new java.io.FileWriter(base.resolve("hostel_fees.csv").toFile())) {
            fw.write("Program,Period,Hostel Fee,Notes\n");
            for (int i = 0; i < hostelModel.getRowCount(); i++) {
                fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    safeString(hostelModel.getValueAt(i,0)), safeString(hostelModel.getValueAt(i,1)), safeString(hostelModel.getValueAt(i,2)), safeString(hostelModel.getValueAt(i,3))));
            }
        }
    }

    private String safeString(Object o) { return o == null ? "" : o.toString().replace("\"", "\"\""); }

    /** Creates the dark, structured sidebar for navigation. */
    private JPanel createSidebar() {
        JPanel panel = new JPanel();
        panel.setBackground(DARK_BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(240, getHeight()));

        // --- Profile/Header Area ---
        JLabel profileLabel = new JLabel("Admin Portal");
        profileLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        profileLabel.setForeground(LIGHT_TEXT);
        profileLabel.setBorder(new EmptyBorder(30, 20, 10, 20));
        profileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(profileLabel);

        JLabel userLabel = new JLabel("User: " + userSession.getUsername());
        userLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        userLabel.setForeground(new Color(180, 180, 180));
        userLabel.setBorder(new EmptyBorder(0, 20, 20, 20));
        userLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(userLabel);

        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        // --- Navigation Buttons ---
        panel.add(createNavButton("Dashboard", "Dashboard", "⌂"));
        panel.add(createNavButton("Students", "Students", "🎓"));
        // In createSidebar() method, change this line:
        panel.add(createNavButton("Instructors", "Instructors", "📚"));
        panel.add(createNavButton("Courses", "Courses", "|C|"));
        panel.add(createNavButton("Offer Courses", "OfferCourses", "📚"));
        panel.add(createNavButton("Offered Courses", "OfferedCourses", "📚"));
        panel.add(createNavButton("Student Enrollments", "StudentEnrollments", "📋"));
        panel.add(createNavButton("Fee Structure", "FeeStructure", "💰"));
        // Place Reports as second-last and Maintenance as last
        panel.add(createNavButton("Reports", "Reports", "📊"));
        panel.add(createNavButton("Maintenance", "Maintenance", "⚙"));

        panel.add(Box.createVerticalGlue());
        // Add change-password quick action above logout
        JButton changePass = new JButton(" 🗝  Change Password");
        changePass.setFont(new Font("SansSerif", Font.BOLD, 14));
        changePass.setForeground(LIGHT_TEXT);
        changePass.setBackground(new Color(60,60,60));
        changePass.setFocusPainted(false);
        changePass.setBorder(BorderFactory.createEmptyBorder(10,20,10,20));
        changePass.setHorizontalAlignment(SwingConstants.LEFT);
        changePass.setMaximumSize(new Dimension(240, 45));
        changePass.setMinimumSize(new Dimension(240, 45));
        changePass.addActionListener(e -> showChangePasswordAdmin());
        panel.add(changePass);
        panel.add(createLogoutButton());
        panel.add(Box.createRigidArea(new Dimension(0, 20)));
        return panel;
    }

    private JButton createNavButton(String text, String cardName, String iconSymbol) {
        JButton button = new JButton(" " + iconSymbol + "  " + text);
        button.setName(cardName);
        button.setFont(new Font("SansSerif", Font.BOLD, 16));
        button.setForeground(LIGHT_TEXT);
        button.setBackground(DARK_BG);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        Dimension buttonSize = new Dimension(240, 45);
        button.setMaximumSize(buttonSize);
        button.setMinimumSize(buttonSize);
        button.addActionListener(e -> selectButton(button, cardName));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (button != activeModuleButton) {
                    button.setBackground(DARK_HOVER);
                }
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (button != activeModuleButton) {
                    button.setBackground(DARK_BG);
                }
            }
        });
        return button;
    }

    private JButton createLogoutButton() {
        JButton logoutButton = new JButton(" 🚪  Logout");
        logoutButton.setName("Logout");
        logoutButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        logoutButton.setForeground(LIGHT_TEXT);
        logoutButton.setBackground(LOGOUT_RED);
        logoutButton.setFocusPainted(false);
        logoutButton.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        logoutButton.setHorizontalAlignment(SwingConstants.LEFT);
        Dimension buttonSize = new Dimension(240, 45);
        logoutButton.setMaximumSize(buttonSize);
        logoutButton.setMinimumSize(buttonSize);

        logoutButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to log out?", "Logout", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // ...existing code...
                // Close dashboard and reopen login window so user may log in again
                this.dispose();
                try {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        try {
                            LoginWindow lw = new LoginWindow();
                            // Open the login window maximized so it occupies the whole screen
                            lw.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
                            lw.setVisible(true);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    });
                } catch (Exception ex) {
                    // As a fallback, exit if reopening login fails
                    System.exit(0);
                }
            }
        });

        logoutButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                logoutButton.setBackground(new Color(200, 35, 51));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                logoutButton.setBackground(LOGOUT_RED);
            }
        });

        return logoutButton;
    }

    private void showChangePasswordAdmin() {
        JPasswordField curr = new JPasswordField();
        JPasswordField np = new JPasswordField();
        JPasswordField conf = new JPasswordField();

        JPanel p = new JPanel(new GridLayout(0,1,6,6));
        p.add(new JLabel("Current Password:")); p.add(curr);
        p.add(new JLabel("New Password:")); p.add(np);
        p.add(new JLabel("Confirm New Password:")); p.add(conf);

        int ok = JOptionPane.showConfirmDialog(this, p, "Change Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        String current = new String(curr.getPassword());
        String newp = new String(np.getPassword());
        String confs = new String(conf.getPassword());

        if (current.isEmpty() || newp.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill both current and new passwords.", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!newp.equals(confs)) {
            JOptionPane.showMessageDialog(this, "New password and confirmation do not match.", "Mismatch", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (current.equals(newp)) {
            JOptionPane.showMessageDialog(this, "New password cannot be the same as current password.", "Same Password", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Verify current password is correct
        if (!auth.PasswordManager.verifyPassword(userSession.getUsername(), current)) {
            JOptionPane.showMessageDialog(this, "Current password is incorrect.", "Authentication Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Update password using PasswordManager (syncs across all auth providers)
        if (auth.PasswordManager.updatePassword(userSession.getUsername(), newp)) {
            JOptionPane.showMessageDialog(this, "Password changed successfully. You will be logged out.", "Success", JOptionPane.INFORMATION_MESSAGE);
            // Force logout
            this.dispose();
            SwingUtilities.invokeLater(() -> new LoginWindow().setVisible(true));
        } else {
            JOptionPane.showMessageDialog(this, "Failed to change password. Please try again.", "Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectButton(JButton button, String cardName) {
    // Reset previous active button
    if (activeModuleButton != null) {
        activeModuleButton.setBackground(DARK_BG);
        activeModuleButton.setForeground(LIGHT_TEXT);
    }
    
    // Set new active button
    activeModuleButton = button;
    activeModuleButton.setBackground(DARK_HOVER);
    activeModuleButton.setForeground(LIGHT_TEXT);
    
    // Show the corresponding card
    mainCardLayout.show(mainContentPanel, cardName);
}

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel topHeader = new JPanel(new BorderLayout());
        topHeader.setBackground(LIGHT_BG);
        JLabel welcomeMsg = new JLabel("Good Afternoon, " + userSession.getUsername() + "! Welcome to University ERP.");
        welcomeMsg.setFont(new Font("SansSerif", Font.PLAIN, 18));
        welcomeMsg.setForeground(HEADER_TEXT);
        topHeader.add(welcomeMsg, BorderLayout.WEST);

        JLabel dateLabel = new JLabel(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")));
        dateLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        dateLabel.setForeground(new Color(120,120,120));
        topHeader.add(dateLabel, BorderLayout.EAST);

        panel.add(topHeader, BorderLayout.NORTH);
        JPanel centralContent = new JPanel(new GridBagLayout());
        centralContent.setBackground(LIGHT_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.3;
        centralContent.add(createSummaryPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.4;
        centralContent.add(createAdminAppsPanel(), gbc);

        // (Reports quick-link remains in the apps grid) — do not add Reports panel inside dashboard center

        panel.add(centralContent, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(new EmptyBorder(20, 0, 20, 0));
        // Total students box (keeps a reference so we can update after adds/refreshes)
        int total = adminService.getAllStudents().size();
        JPanel totalBox = createDetailBox("Total Students", String.valueOf(total), ACCENT_BLUE);
        // find its value label (second component) and keep reference to update later
        Component[] comps = totalBox.getComponents();
        // The createDetailBox places title at NORTH and value at CENTER; center component is value label
        for (Component c : comps) {
            if (c instanceof JLabel) {
                // prefer the big value label (font size 36)
                JLabel l = (JLabel) c;
                if (l.getFont().getSize() >= 30) {
                    totalStudentsValueLabel = l;
                    break;
                }
            }
        }
        panel.add(totalBox);
        // compute instructors via DAO
        int instructorCount = 0;
        try { instructorCount = new admin.dao.InstructorDAO().listAll().size(); } catch (Exception ignore) {}
        JPanel instBox = createDetailBox("Total Instructors", String.valueOf(instructorCount), new Color(40, 167, 69));
        // capture label inside the box
        for (Component c : instBox.getComponents()) {
            if (c instanceof JLabel) {
                JLabel l = (JLabel) c;
                if (l.getFont().getSize() >= 30) { totalInstructorsValueLabel = l; break; }
            }
        }
        panel.add(instBox);
        panel.add(createDetailBox("Total Courses", "120", new Color(255, 193, 7)));

        return panel;
    }

    private JPanel createDetailBox(String title, String value, Color accent) {
        JPanel box = new JPanel(new BorderLayout(5, 5));
        box.setBackground(CARD_BG);
        box.setPreferredSize(new Dimension(200, 100));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
            new EmptyBorder(15, 20, 15, 15)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        titleLabel.setForeground(new Color(100, 100, 100));
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        valueLabel.setForeground(HEADER_TEXT);
        box.add(titleLabel, BorderLayout.NORTH);
        box.add(valueLabel, BorderLayout.CENTER);
        return box;
    }

    private JPanel createAdminAppsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(20, 20, 20, 20)
        ));
        JLabel title = new JLabel("ADMIN QUICK LINKS");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(HEADER_TEXT);
        panel.add(title, BorderLayout.NORTH);

        JPanel appsGrid = new JPanel(new GridLayout(2, 3, 20, 20));
        appsGrid.setBackground(CARD_BG);

        appsGrid.add(createAppServiceCard("Manage Students", "Students", "🎓"));
        appsGrid.add(createAppServiceCard("Instructor Management", "Instructors", "📚"));
        appsGrid.add(createAppServiceCard("Course Management", "Courses", "|C|"));
        appsGrid.add(createAppServiceCard("Fee Structure", "FeeStructure", "💰"));
        appsGrid.add(createAppServiceCard("Reports", "Reports", "📊"));
        // Hostel quick-link removed
        appsGrid.add(createAppServiceCard("System Maintenance", "Maintenance", "⚙"));

        panel.add(appsGrid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAppServiceCard(String name, String targetCard, String icon) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(LIGHT_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(15, 15, 15, 15)
        ));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font(Font.SERIF, Font.PLAIN, 40));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(iconLabel, BorderLayout.CENTER);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        nameLabel.setForeground(HEADER_TEXT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(nameLabel, BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (sidebarPanel != null) {
                    for (Component comp : sidebarPanel.getComponents()) {
                        if (comp instanceof JButton && targetCard.equals(((JButton) comp).getName())) {
                            selectButton((JButton) comp, targetCard);
                            break;
                        }
                    }
                }
                mainCardLayout.show(mainContentPanel, targetCard);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(230, 230, 230));
                card.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(LIGHT_BG);
                card.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

        return card;
    }

    /** Manage Students panel now wired to AdminService */
    
    private JPanel createManageStudentsPanel() {
        // create a local AdminService instance (uses the JDBC-backed StudentDAO)
        admin.services.AdminService adminService = new admin.services.AdminService();

        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("Manage Student Accounts");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        // Content card
        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(20, 20, 20, 20)
        ));

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        searchPanel.setBackground(CARD_BG);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JTextField searchField = new JTextField(30);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JButton searchButton = new JButton("🔍 Search");
        searchButton.setFont(new Font("SansSerif", Font.PLAIN, 13));

        // Section filter dropdown
        JLabel sectionLabel = new JLabel("Filter by Section:");
        sectionLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        String[] sectionOptions = {"All Sections", "Section A", "Section B"};
        JComboBox<String> sectionFilter = new JComboBox<>(sectionOptions);
        sectionFilter.setFont(new Font("SansSerif", Font.PLAIN, 14));
        sectionFilter.setPreferredSize(new Dimension(150, 30));

        JButton addStudentButton = new JButton("🎓 Add Student");
        addStudentButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        addStudentButton.setBackground(new Color(60, 150, 60));
        addStudentButton.setForeground(Color.WHITE);
        addStudentButton.setFocusPainted(false);

        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(Box.createHorizontalStrut(10));
        searchPanel.add(sectionLabel);
        searchPanel.add(sectionFilter);
        searchPanel.add(Box.createHorizontalStrut(20));
        searchPanel.add(addStudentButton);

        contentCard.add(searchPanel, BorderLayout.NORTH);

        // Student table with additional columns (Degree, Branch, Year, AdmissionYear)
        String[] columns = {"Student ID", "Name", "Email", "Section", "Degree", "Branch", "Year", "Admission Year", "Status"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Load actual students from DB (AdminService should return extended Student objects)
        java.util.List<domain.Student> students = adminService.getAllStudents();
        for (domain.Student s : students) {
            // NOTE: If your Student class doesn't have degree/branch/year/admissionYear yet,
            // update domain.Student and DAO to include them. For now we attempt to read getters,
            // and fallback to empty strings if they don't exist (you'll need to update backend later).
            String degree = safeGetString(s, "getDegree");
            String branch = safeGetString(s, "getBranch");
            String year = safeGetString(s, "getYearOfStudy");
            String admission = safeGetString(s, "getAdmissionYear");
            tableModel.addRow(new Object[]{s.getId(), s.getName(), s.getEmail(), s.getSection(), degree, branch, year, admission, s.getStatus()});
        }

        JTable studentTable = new JTable(tableModel);
        studentTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        studentTable.setRowHeight(35);
        studentTable.setBackground(Color.WHITE);
        studentTable.setSelectionBackground(new Color(220, 220, 220));
        studentTable.setSelectionForeground(Color.BLACK);
        studentTable.setGridColor(BORDER_GRAY);

        // Allow multi-selection
        studentTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Table header styling
        studentTable.getTableHeader().setBackground(new Color(50, 50, 50));
        studentTable.getTableHeader().setForeground(Color.WHITE);
        studentTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        studentTable.getTableHeader().setPreferredSize(new Dimension(0, 40));

        JScrollPane tableScrollPane = new JScrollPane(studentTable);
        tableScrollPane.setBorder(BorderFactory.createLineBorder(BORDER_GRAY));
        contentCard.add(tableScrollPane, BorderLayout.CENTER);

        // Bottom panel with action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.setBackground(CARD_BG);

        JButton editButton = new JButton("✏️ Edit Student");
        editButton.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JButton deleteButton = new JButton("🗑️ Delete Student(s)");
        deleteButton.setFont(new Font("SansSerif", Font.PLAIN, 13));
        deleteButton.setBackground(new Color(200, 60, 60));
        deleteButton.setForeground(Color.WHITE);
        deleteButton.setFocusPainted(false);

        JButton refreshButton = new JButton("🔄 Refresh");
        refreshButton.setFont(new Font("SansSerif", Font.PLAIN, 13));

        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);

        contentCard.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(contentCard, BorderLayout.CENTER);

        // Add button functionality
        addStudentButton.addActionListener(e -> showAddStudentDialog(tableModel));

        editButton.addActionListener(e -> {
            int[] selectedRows = studentTable.getSelectedRows();
            if (selectedRows.length == 1) {
                showEditStudentDialog(tableModel, selectedRows[0]);
            } else if (selectedRows.length > 1) {
                JOptionPane.showMessageDialog(this, "Please select only one student to edit.",
                    "Multiple Selection", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Please select a student to edit.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        });

        // ====== DELETE handler (DB-backed) supports multiple deletion ======
        deleteButton.addActionListener(e -> {
            int[] selectedRows = studentTable.getSelectedRows();

            if (selectedRows.length > 0) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to delete the selected student(s)?",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (confirm == JOptionPane.YES_OPTION) {
                    // Delete IDs and then remove rows from model (remove in descending order)
                    java.util.List<String> idsToDelete = new java.util.ArrayList<>();
                    for (int r : selectedRows) {
                        idsToDelete.add(tableModel.getValueAt(r, 0).toString());
                    }

                    boolean anyFailed = false;
                    for (String id : idsToDelete) {
                        boolean ok = adminService.deleteStudent(id);
                        if (!ok) anyFailed = true;
                    }

                    // Remove rows from model (descending index to avoid reindexing issues)
                    java.util.Arrays.sort(selectedRows);
                    for (int i = selectedRows.length - 1; i >= 0; i--) {
                        tableModel.removeRow(selectedRows[i]);
                    }

                    if (anyFailed) {
                        JOptionPane.showMessageDialog(this, "Some deletes failed. Check DB logs.", "Partial Failure", JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Selected students deleted successfully!");
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select one or more students to delete.",
                        "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        });

        refreshButton.addActionListener(e -> {
            // reload from DB
            tableModel.setRowCount(0);
            java.util.List<domain.Student> refreshed = adminService.getAllStudents();
            for (domain.Student s : refreshed) {
                String degree = safeGetString(s, "getDegree");
                String branch = safeGetString(s, "getBranch");
                String year = safeGetString(s, "getYearOfStudy");
                String admission = safeGetString(s, "getAdmissionYear");
                tableModel.addRow(new Object[]{s.getId(), s.getName(), s.getEmail(), s.getSection(), degree, branch, year, admission, s.getStatus()});
            }
            // update dashboard total
            updateTotalStudentsCount();
            JOptionPane.showMessageDialog(this, "Student list refreshed!");
        });

        searchButton.addActionListener(e -> {
            String searchText = searchField.getText().toLowerCase().trim();
            if (searchText.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter search text.",
                    "Empty Search", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            java.util.List<domain.Student> results = adminService.searchStudents(searchText);
            if (results.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No matching student found.",
                    "Search Result", JOptionPane.INFORMATION_MESSAGE);
            } else {
                // populate table with results
                tableModel.setRowCount(0);
                for (domain.Student s : results) {
                    String degree = safeGetString(s, "getDegree");
                    String branch = safeGetString(s, "getBranch");
                    String year = safeGetString(s, "getYearOfStudy");
                    String admission = safeGetString(s, "getAdmissionYear");
                    tableModel.addRow(new Object[]{s.getId(), s.getName(), s.getEmail(), s.getSection(), degree, branch, year, admission, s.getStatus()});
                }
            }
        });

        // Section filter action listener
        sectionFilter.addActionListener(e -> {
            String selectedSection = (String) sectionFilter.getSelectedItem();
            tableModel.setRowCount(0);

            if ("All Sections".equals(selectedSection)) {
                // Load all students
                java.util.List<domain.Student> allStudents = adminService.getAllStudents();
                for (domain.Student s : allStudents) {
                    String degree = safeGetString(s, "getDegree");
                    String branch = safeGetString(s, "getBranch");
                    String year = safeGetString(s, "getYearOfStudy");
                    String admission = safeGetString(s, "getAdmissionYear");
                    tableModel.addRow(new Object[]{s.getId(), s.getName(), s.getEmail(), s.getSection(), degree, branch, year, admission, s.getStatus()});
                }
            } else {
                // Filter by selected section (A or B)
                String filterSection = selectedSection.equals("Section A") ? "A" : "B";
                java.util.List<domain.Student> allStudents = adminService.getAllStudents();
                for (domain.Student s : allStudents) {
                    if (s.getSection() != null && s.getSection().equals(filterSection)) {
                        String degree = safeGetString(s, "getDegree");
                        String branch = safeGetString(s, "getBranch");
                        String year = safeGetString(s, "getYearOfStudy");
                        String admission = safeGetString(s, "getAdmissionYear");
                        tableModel.addRow(new Object[]{s.getId(), s.getName(), s.getEmail(), s.getSection(), degree, branch, year, admission, s.getStatus()});
                    }
                }
            }
            searchField.setText(""); // Clear search field when filtering by section
        });

        return mainPanel;
    }


        private void showAddStudentDialog(DefaultTableModel model) {
            JDialog dialog = new JDialog(this, "Add New Student", true);
            dialog.setLayout(new BorderLayout(10, 10));
            dialog.setResizable(false);

            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
            mainPanel.setBackground(Color.WHITE);

            // Title
            JLabel titleLabel = new JLabel("Add New Student", JLabel.CENTER);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
            titleLabel.setForeground(HEADER_TEXT);
            mainPanel.add(titleLabel, BorderLayout.NORTH);

            // Form panel with proper layout
            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setBackground(Color.WHITE);
            formPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_GRAY, 1),
                new EmptyBorder(20, 20, 20, 20)
            ));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 8, 8, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;

            // Row 0: Student ID
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("Student ID:"), gbc);
            JTextField idField = new JTextField(20);
            gbc.gridx = 1;
            formPanel.add(idField, gbc);

            // Row 1: Name
            gbc.gridx = 0; gbc.gridy = 1;
            formPanel.add(new JLabel("Name:"), gbc);
            JTextField nameField = new JTextField(20);
            gbc.gridx = 1;
            formPanel.add(nameField, gbc);

            // Row 2: Email
            gbc.gridx = 0; gbc.gridy = 2;
            formPanel.add(new JLabel("Email:"), gbc);
            JTextField emailField = new JTextField(20);
            gbc.gridx = 1;
            formPanel.add(emailField, gbc);

            // Row 3: Section
            gbc.gridx = 0; gbc.gridy = 3;
            formPanel.add(new JLabel("Section:"), gbc);
            JTextField sectionField = new JTextField(20);
            gbc.gridx = 1;
            formPanel.add(sectionField, gbc);

            // Row 4: Degree
            gbc.gridx = 0; gbc.gridy = 4;
            formPanel.add(new JLabel("Degree:"), gbc);
            JComboBox<String> degreeCombo = new JComboBox<>(new String[]{"BTech", "MTech"});
            degreeCombo.setSelectedIndex(0);
            gbc.gridx = 1;
            formPanel.add(degreeCombo, gbc);

            // Row 5: Branch
            gbc.gridx = 0; gbc.gridy = 5;
            formPanel.add(new JLabel("Branch:"), gbc);
            JComboBox<String> branchCombo = createBranchComboBox("BTech");
            gbc.gridx = 1;
            formPanel.add(branchCombo, gbc);

            // Row 6: Year of Study
            gbc.gridx = 0; gbc.gridy = 6;
            formPanel.add(new JLabel("Year of Study:"), gbc);
            JComboBox<String> yearCombo = new JComboBox<>(new String[]{"1st", "2nd", "3rd", "4th"});
            gbc.gridx = 1;
            formPanel.add(yearCombo, gbc);

            // Row 7: Admission Year
            gbc.gridx = 0; gbc.gridy = 7;
            formPanel.add(new JLabel("Admission Year:"), gbc);
            JComboBox<String> admissionCombo = new JComboBox<>(getAdmissionYears(10));
            gbc.gridx = 1;
            formPanel.add(admissionCombo, gbc);

            // Row 8: Status
            gbc.gridx = 0; gbc.gridy = 8;
            formPanel.add(new JLabel("Status:"), gbc);
            JComboBox<String> statusCombo = new JComboBox<>(new String[]{"Active", "Inactive"});
            gbc.gridx = 1;
            formPanel.add(statusCombo, gbc);

            mainPanel.add(formPanel, BorderLayout.CENTER);

            // Button panel
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
            buttonPanel.setBackground(Color.WHITE);
            
            JButton saveButton = new JButton("Save");
            saveButton.setBackground(new Color(60, 150, 60));
            saveButton.setForeground(Color.WHITE);
            saveButton.setFocusPainted(false);
            
            JButton cancelButton = new JButton("Cancel");
            cancelButton.setBackground(new Color(200, 60, 60));
            cancelButton.setForeground(Color.WHITE);
            cancelButton.setFocusPainted(false);

            buttonPanel.add(saveButton);
            buttonPanel.add(cancelButton);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            dialog.add(mainPanel);

            // Degree combo listener
            degreeCombo.addActionListener(e -> {
                String deg = (String) degreeCombo.getSelectedItem();
                DefaultComboBoxModel<String> modelBranches = createBranchComboBoxModel(deg);
                branchCombo.setModel(modelBranches);
                branchCombo.setSelectedIndex(0);
            });

            // Save button action
            saveButton.addActionListener(e -> {
                if (idField.getText().trim().isEmpty() || nameField.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Please fill in Student ID and Name fields.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (SystemState.isMaintenance()) {
                    JOptionPane.showMessageDialog(this, "System is in maintenance mode. Mutating operations are disabled.", "Maintenance", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                String idVal = idField.getText().trim();
                String nameVal = nameField.getText().trim();
                String emailVal = emailField.getText().trim();
                String sectionVal = sectionField.getText().trim();
                String statusVal = (String) statusCombo.getSelectedItem();
                String degreeVal = (String) degreeCombo.getSelectedItem();
                String branchVal = (String) branchCombo.getSelectedItem();
                String yearVal = (String) yearCombo.getSelectedItem();
                String admissionVal = (String) admissionCombo.getSelectedItem();

                // If email is empty, auto-generate as firstName+rollNo@iiitd.ac.in
                if (emailVal.isEmpty()) {
                    String first = nameVal.split("\\s+")[0].toLowerCase().replaceAll("[^a-z]", "");
                    String roll = idVal.replaceAll("[^0-9a-zA-Z]", "");
                    emailVal = first + roll + "@iiitd.ac.in";
                }

                // Build Student object with extended fields
                Student s = new Student(idVal, nameVal, emailVal, sectionVal, statusVal, degreeVal, branchVal, yearVal, admissionVal);

                try {
                    Authz.requireAnyRole(userSession, "SUPERADMIN", "ADMIN");
                    boolean ok = adminService.addStudent(s);
                    if (ok) {
                        model.addRow(new Object[]{s.getId(), s.getName(), s.getEmail(), s.getSection(), degreeVal, branchVal, yearVal, admissionVal, s.getStatus()});
                        JOptionPane.showMessageDialog(dialog, "Student added successfully!");
                        // ...existing code...
                        dialog.dispose();
                        // update dashboard total count if visible
                        updateTotalStudentsCount();
                    } else {
                        JOptionPane.showMessageDialog(dialog, "Failed to add student. ID might already exist.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (SecurityException ex) {
                    JOptionPane.showMessageDialog(this, "Permission denied: " + ex.getMessage(), "Permission Denied", JOptionPane.WARNING_MESSAGE);
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(this, "Validation error: " + ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
                }
            });

            cancelButton.addActionListener(e -> dialog.dispose());

            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        }

        private void showEditStudentDialog(DefaultTableModel model, int row) {
        JDialog dialog = new JDialog(this, "Edit Student", true);
        dialog.setLayout(new BorderLayout());

        JPanel formPanel = new JPanel(new GridLayout(9, 2, 10, 15));
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel idLabel = new JLabel("Student ID:");
        JTextField idField = new JTextField(model.getValueAt(row, 0).toString());
        idField.setEditable(false);
        idField.setBackground(new Color(240, 240, 240));

        JLabel nameLabel = new JLabel("Name:");
        JTextField nameField = new JTextField(model.getValueAt(row, 1).toString());
        JLabel emailLabel = new JLabel("Email:");
        JTextField emailField = new JTextField(model.getValueAt(row, 2).toString());
        JLabel sectionLabel = new JLabel("Section:");
        JTextField sectionField = new JTextField(model.getValueAt(row, 3).toString());

        JLabel degreeLabel = new JLabel("Degree:");
        JComboBox<String> degreeCombo = new JComboBox<>(new String[]{"BTech", "MTech"});
        String curDegree = model.getValueAt(row, 4) != null ? model.getValueAt(row, 4).toString() : "BTech";
        degreeCombo.setSelectedItem(curDegree);

        JLabel branchLabel = new JLabel("Branch:");
        JComboBox<String> branchCombo = createBranchComboBox((String) degreeCombo.getSelectedItem());
        String curBranch = model.getValueAt(row, 5) != null ? model.getValueAt(row, 5).toString() : "";
        branchCombo.setSelectedItem(curBranch);

        JLabel yearLabel = new JLabel("Year of Study:");
        JComboBox<String> yearCombo = new JComboBox<>(new String[]{"1st", "2nd", "3rd", "4th"});
        String curYear = model.getValueAt(row, 6) != null ? model.getValueAt(row, 6).toString() : "1st";
        yearCombo.setSelectedItem(curYear);

        JLabel admissionLabel = new JLabel("Admission Year:");
        JComboBox<String> admissionCombo = new JComboBox<>(getAdmissionYears(10));
        String curAdmission = model.getValueAt(row, 7) != null ? model.getValueAt(row, 7).toString() : null;
        if (curAdmission != null) admissionCombo.setSelectedItem(curAdmission);

        JLabel statusLabel = new JLabel("Status:");
        JComboBox<String> statusCombo = new JComboBox<>(new String[]{"Active", "Inactive"});
        statusCombo.setSelectedItem(model.getValueAt(row, 8));

        formPanel.add(idLabel); formPanel.add(idField);
        formPanel.add(nameLabel); formPanel.add(nameField);
        formPanel.add(emailLabel); formPanel.add(emailField);
        formPanel.add(sectionLabel); formPanel.add(sectionField);
        formPanel.add(degreeLabel); formPanel.add(degreeCombo);
        formPanel.add(branchLabel); formPanel.add(branchCombo);
        formPanel.add(yearLabel); formPanel.add(yearCombo);
        formPanel.add(admissionLabel); formPanel.add(admissionCombo);
        formPanel.add(statusLabel); formPanel.add(statusCombo);

        dialog.add(formPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.WHITE);
        JButton saveButton = new JButton("Save Changes");
        JButton cancelButton = new JButton("Cancel");

        degreeCombo.addActionListener(e -> {
            String deg = (String) degreeCombo.getSelectedItem();
            DefaultComboBoxModel<String> cbm = (DefaultComboBoxModel<String>) createBranchComboBoxModel(deg);
            branchCombo.setModel(cbm);
            branchCombo.setSelectedIndex(0);
        });

        saveButton.addActionListener(e -> {
            if (SystemState.isMaintenance()) {
                JOptionPane.showMessageDialog(this, "System is in maintenance mode. Mutating operations are disabled.", "Maintenance", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Student s = new Student(idField.getText().trim(), nameField.getText().trim(), emailField.getText().trim(), sectionField.getText().trim(), (String)statusCombo.getSelectedItem());
            try {
                // Try to set via reflection if setters exist
                try {
                    java.lang.reflect.Method m;
                    m = s.getClass().getMethod("setDegree", String.class);
                    m.invoke(s, (String) degreeCombo.getSelectedItem());
                    m = s.getClass().getMethod("setBranch", String.class);
                    m.invoke(s, (String) branchCombo.getSelectedItem());
                    m = s.getClass().getMethod("setYearOfStudy", String.class);
                    m.invoke(s, (String) yearCombo.getSelectedItem());
                    m = s.getClass().getMethod("setAdmissionYear", String.class);
                    m.invoke(s, (String) admissionCombo.getSelectedItem());
                } catch (NoSuchMethodException ignore) {
                    // backend not updated yet; fine for now (UI works)
                }
            } catch (Exception ex) {
                // reflection failed, continue
            }

            try {
                Authz.requireAnyRole(userSession, "SUPERADMIN", "ADMIN");
                boolean ok = adminService.updateStudent(s);
                if (ok) {
                    model.setValueAt(s.getName(), row, 1);
                    model.setValueAt(s.getEmail(), row, 2);
                    model.setValueAt(s.getSection(), row, 3);
                    model.setValueAt((String) degreeCombo.getSelectedItem(), row, 4);
                    model.setValueAt((String) branchCombo.getSelectedItem(), row, 5);
                    model.setValueAt((String) yearCombo.getSelectedItem(), row, 6);
                    model.setValueAt((String) admissionCombo.getSelectedItem(), row, 7);
                    model.setValueAt(s.getStatus(), row, 8);
                    JOptionPane.showMessageDialog(dialog, "Student updated successfully!");
                    // ...existing code...
                    // refresh dashboard counts
                    updateTotalStudentsCount();
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(dialog, "Failed to update student.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (SecurityException ex) {
                JOptionPane.showMessageDialog(this, "Permission denied: " + ex.getMessage(), "Permission Denied", JOptionPane.WARNING_MESSAGE);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "Validation error: " + ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveButton); buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setSize(550, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel createInstructorManagementPanel() {
        admin.dao.InstructorDAO dao = new admin.dao.InstructorDAO();
        admin.dao.CourseDAO courseDao = new admin.dao.CourseDAO();

        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("📖👤 Instructor Management");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(20, 20, 20, 20)
        ));

        // Top controls: search + add
        JPanel topControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topControls.setBackground(CARD_BG);
        JLabel searchLabel = new JLabel("Search:");
        JTextField searchField = new JTextField(24);
        JButton searchBtn = new JButton("Search");
        JButton addInstructorButton = new JButton("👨‍🏫 Add Instructor");
        addInstructorButton.setBackground(new Color(60,150,60)); addInstructorButton.setForeground(Color.WHITE);
        JButton refreshInstructorsButton = new JButton("🔄 Refresh");
        refreshInstructorsButton.setToolTipText("Refresh instructor list");
        topControls.add(searchLabel); topControls.add(searchField); topControls.add(searchBtn); topControls.add(Box.createHorizontalStrut(10)); topControls.add(addInstructorButton); topControls.add(refreshInstructorsButton);

        contentCard.add(topControls, BorderLayout.NORTH);

        String[] columns = {"Instructor ID", "Name", "Department", "Email", "Courses Assigned"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        JTable instructorTable = new JTable(tableModel);
        instructorTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        instructorTable.setRowHeight(35);
        instructorTable.setBackground(Color.WHITE);

        JScrollPane tableScrollPane = new JScrollPane(instructorTable);
        contentCard.add(tableScrollPane, BorderLayout.CENTER);

        // Bottom actions
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton assignCoursesButton = new JButton("📚 Assign Courses");
        JButton delButton = new JButton("🗑️ Delete Instructor(s)");
        delButton.setBackground(new Color(200,60,60)); delButton.setForeground(Color.WHITE);
        bottom.add(assignCoursesButton); bottom.add(delButton);
        contentCard.add(bottom, BorderLayout.SOUTH);

        // load initial list
        Runnable reload = () -> {
            tableModel.setRowCount(0);
            for (domain.Instructor ins : dao.listAll()) {
                java.util.List<String> assigned = dao.getAssignedCourses(ins.getId());
                // Fallback: if no instructor_courses mapping exists, derive assignments from sections table
                if ((assigned == null || assigned.isEmpty())) {
                    try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                         PreparedStatement ps = c.prepareStatement("SELECT DISTINCT course_code FROM sections WHERE instructor_id = ?")) {
                        ps.setString(1, ins.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String cc = rs.getString(1);
                                if (cc != null && !cc.trim().isEmpty()) assigned.add(cc);
                            }
                        }
                    } catch (SQLException ignored) {}
                }
                String asg = String.join(", ", assigned);
                tableModel.addRow(new Object[]{ins.getId(), ins.getName(), ins.getDepartment(), ins.getEmail(), asg});
            }
            updateInstructorCount(dao.listAll().size());
        };
        reload.run();

        // Search
        searchBtn.addActionListener(e -> {
            String q = searchField.getText().trim();
            tableModel.setRowCount(0);
            if (q.isEmpty()) { reload.run(); return; }
            for (domain.Instructor ins : dao.search(q)) {
                java.util.List<String> assigned = dao.getAssignedCourses(ins.getId());
                if ((assigned == null || assigned.isEmpty())) {
                    try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                         PreparedStatement ps = c.prepareStatement("SELECT DISTINCT course_code FROM sections WHERE instructor_id = ?")) {
                        ps.setString(1, ins.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String cc = rs.getString(1);
                                if (cc != null && !cc.trim().isEmpty()) assigned.add(cc);
                            }
                        }
                    } catch (SQLException ignored) {}
                }
                tableModel.addRow(new Object[]{ins.getId(), ins.getName(), ins.getDepartment(), ins.getEmail(), String.join(", ", assigned)});
            }
        });

        // Refresh button - reload instructor list and update dashboard count
        refreshInstructorsButton.addActionListener(e -> {
            reload.run();
            JOptionPane.showMessageDialog(this, "Instructor list refreshed!");
        });

        // Add instructor dialog
        addInstructorButton.addActionListener(e -> {
            JDialog d = new JDialog(this, "Add Instructor", true);
            JPanel p = new JPanel(new GridLayout(6,2,8,8)); p.setBorder(new EmptyBorder(12,12,12,12));
            JTextField idf = new JTextField(); JTextField namef = new JTextField(); JTextField emailf = new JTextField();
            JComboBox<String> dept = new JComboBox<>(new String[]{"COMPUTER SCIENCE","MATHEMATICS","human centred design","computational biology","ECE"});
            JComboBox<String> status = new JComboBox<>(new String[]{"Active","Inactive"});
            p.add(new JLabel("Instructor ID:")); p.add(idf);
            p.add(new JLabel("Name:")); p.add(namef);
            p.add(new JLabel("Email:")); p.add(emailf);
            p.add(new JLabel("Department:")); p.add(dept);
            p.add(new JLabel("Status:")); p.add(status);
            JButton save = new JButton("Save"); JButton cancel = new JButton("Cancel");
            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bp.add(save); bp.add(cancel);
            d.add(p, BorderLayout.CENTER); d.add(bp, BorderLayout.SOUTH);
            d.setSize(700, 480);
            d.setResizable(true);
            d.setLocationRelativeTo(this);

            save.addActionListener(ae -> {
                String id = idf.getText().trim(); String nm = namef.getText().trim(); String em = emailf.getText().trim();
                String dep = (String)dept.getSelectedItem(); String st = (String)status.getSelectedItem();
                if (id.isEmpty() || nm.isEmpty()) { JOptionPane.showMessageDialog(d, "Please fill ID and Name.", "Validation", JOptionPane.WARNING_MESSAGE); return; }
                if (!em.endsWith("@iiitd.ac.in")) { JOptionPane.showMessageDialog(d, "Email must end with @iiitd.ac.in", "Validation", JOptionPane.WARNING_MESSAGE); return; }
                domain.Instructor ins = new domain.Instructor(id, nm, em, dep, st);
                boolean ok = dao.create(ins);
                if (ok) { JOptionPane.showMessageDialog(d, "Instructor added."); d.dispose(); reload.run(); } else { JOptionPane.showMessageDialog(d, "Failed to add instructor.", "Error", JOptionPane.ERROR_MESSAGE); }
            });
            cancel.addActionListener(ae -> d.dispose());
            d.setVisible(true);
        });

        // Delete
        delButton.addActionListener(e -> {
            int[] sel = instructorTable.getSelectedRows();
            if (sel.length == 0) { JOptionPane.showMessageDialog(this, "Select instructor(s) to delete."); return; }
            int confirm = JOptionPane.showConfirmDialog(this, "Delete selected instructor(s)?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            for (int r = sel.length-1; r>=0; r--) {
                String id = (String)tableModel.getValueAt(sel[r], 0);
                dao.delete(id);
                tableModel.removeRow(sel[r]);
            }
            updateInstructorCount(dao.listAll().size());
        });

        // Edit instructor button
        JButton editInstructorButton = new JButton("✏️ Edit Instructor");
        editInstructorButton.setFont(new Font("SansSerif", Font.PLAIN, 13));
        bottom.add(editInstructorButton);
        editInstructorButton.addActionListener(e -> {
            int r = instructorTable.getSelectedRow();
            if (r == -1) { JOptionPane.showMessageDialog(this, "Select one instructor to edit."); return; }
            String id = (String)tableModel.getValueAt(r, 0);
            String name = (String)tableModel.getValueAt(r, 1);
            String dept = (String)tableModel.getValueAt(r, 2);
            String email = (String)tableModel.getValueAt(r, 3);
            // show edit dialog
            JDialog d = new JDialog(this, "Edit Instructor", true);
            JPanel p = new JPanel(new GridLayout(6,2,8,8)); p.setBorder(new EmptyBorder(12,12,12,12));
            JTextField idf = new JTextField(id); idf.setEditable(false); idf.setBackground(new Color(240,240,240));
            JTextField namef = new JTextField(name); JTextField emailf = new JTextField(email);
            JComboBox<String> deptc = new JComboBox<>(new String[]{"COMPUTER SCIENCE","MATHEMATICS","HUMAN CENTRED DESIGN","COMPUTATIONAL BIOLOGY","ECE"});
            deptc.setSelectedItem(dept);
            JComboBox<String> statusc = new JComboBox<>(new String[]{"Active","Inactive"});
            p.add(new JLabel("Instructor ID:")); p.add(idf);
            p.add(new JLabel("Name:")); p.add(namef);
            p.add(new JLabel("Email:")); p.add(emailf);
            p.add(new JLabel("Department:")); p.add(deptc);
            p.add(new JLabel("Status:")); p.add(statusc);
            JButton save = new JButton("Save"); JButton cancel = new JButton("Cancel");
            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bp.add(save); bp.add(cancel);

            // Build assigned-courses panel (shows current assignments and allows unassign)
            DefaultListModel<String> assignedModel = new DefaultListModel<>();
            java.util.List<String> assignedCourses = new java.util.ArrayList<>(dao.getAssignedCourses(id));
            // Fallback: if no instructor_courses mapping exists, derive assignments from sections table
            if (assignedCourses.isEmpty()) {
                try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                     PreparedStatement ps = c.prepareStatement("SELECT DISTINCT course_code FROM sections WHERE instructor_id = ?")) {
                    ps.setString(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String cc = rs.getString(1);
                            if (cc != null && !cc.trim().isEmpty()) assignedCourses.add(cc);
                        }
                    }
                } catch (SQLException ignored) {}
            }
            for (String cc : assignedCourses) {
                String cTitle = "";
                for (admin.dao.CourseDAO.Course c : courseDao.listAllCourses()) if (c.code.equalsIgnoreCase(cc)) { cTitle = c.title; break; }
                assignedModel.addElement(cc + (cTitle.isEmpty() ? "" : " - " + cTitle));
            }
            JList<String> assignedList = new JList<>(assignedModel);
            assignedList.setVisibleRowCount(8);
            JScrollPane assignedScroll = new JScrollPane(assignedList);
            JButton unassignBtn = new JButton("Unassign Selected");
            unassignBtn.addActionListener(ae -> {
                String sel = assignedList.getSelectedValue();
                if (sel == null) { JOptionPane.showMessageDialog(d, "Select a course to unassign."); return; }
                String code = sel.split(" - ")[0].trim();
                boolean ok = false;
                try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl())) {
                    // Try to delete from instructor_courses table
                    String delSql = "DELETE FROM instructor_courses WHERE instructor_id = ? AND course_code = ?";
                    try (PreparedStatement stmt = c.prepareStatement(delSql)) {
                        stmt.setString(1, id);
                        stmt.setString(2, code);
                        int rows = stmt.executeUpdate();
                        ok = (rows > 0);
                    }
                    // Also clear section-level assignments for this instructor+course
                    String clearSql = "UPDATE sections SET instructor_id = NULL WHERE course_code = ? AND instructor_id = ?";
                    try (PreparedStatement stmt = c.prepareStatement(clearSql)) {
                        stmt.setString(1, code);
                        stmt.setString(2, id);
                        int rows = stmt.executeUpdate();
                        ok = ok || (rows > 0);  // Success if either table had the assignment
                    }
                } catch (SQLException ex) { 
                    JOptionPane.showMessageDialog(d, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (ok) {
                    assignedModel.removeElement(sel);
                    JOptionPane.showMessageDialog(d, "Course unassigned.");
                    reload.run();
                    if (this.reloadCourses != null) this.reloadCourses.run();
                } else {
                    JOptionPane.showMessageDialog(d, "Failed to unassign course.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            JPanel rightPanel = new JPanel(new BorderLayout(6,6));
            rightPanel.add(new JLabel("Assigned Courses:"), BorderLayout.NORTH);
            rightPanel.add(assignedScroll, BorderLayout.CENTER);
            JPanel rb = new JPanel(new FlowLayout(FlowLayout.RIGHT)); rb.add(unassignBtn); rightPanel.add(rb, BorderLayout.SOUTH);

            JPanel leftPanel = new JPanel(new BorderLayout(6,6));
            leftPanel.add(new JLabel("Instructor Details:"), BorderLayout.NORTH);
            leftPanel.add(p, BorderLayout.CENTER);

            JPanel centerPanel = new JPanel(new BorderLayout(10,10));
            centerPanel.add(leftPanel, BorderLayout.WEST);
            centerPanel.add(rightPanel, BorderLayout.CENTER);

            d.add(centerPanel, BorderLayout.CENTER);
            d.add(bp, BorderLayout.SOUTH);
            d.setSize(900, 500); d.setResizable(true); d.setLocationRelativeTo(this);

            save.addActionListener(ae -> {
                String nm = namef.getText().trim(); String em = emailf.getText().trim(); String dp = (String)deptc.getSelectedItem(); String st = (String)statusc.getSelectedItem();
                if (nm.isEmpty()) { JOptionPane.showMessageDialog(d, "Name required", "Validation", JOptionPane.WARNING_MESSAGE); return; }
                if (!em.endsWith("@iiitd.ac.in")) { JOptionPane.showMessageDialog(d, "Email must end with @iiitd.ac.in", "Validation", JOptionPane.WARNING_MESSAGE); return; }
                domain.Instructor ins = new domain.Instructor(id, nm, em, dp, st);
                boolean ok = dao.update(ins);
                if (ok) {
                    JOptionPane.showMessageDialog(d, "Instructor updated"); d.dispose(); reload.run();
                } else {
                    JOptionPane.showMessageDialog(d, "Failed to update instructor", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            cancel.addActionListener(ae -> d.dispose());
            d.setVisible(true);
        });

        // Assign courses / sections (show course filtered by instructor department, then section A/B)
        assignCoursesButton.addActionListener(e -> {
            int r = instructorTable.getSelectedRow();
            if (r == -1) { JOptionPane.showMessageDialog(this, "Select one instructor to assign courses/sections."); return; }
            String insId = (String)tableModel.getValueAt(r, 0);
            String instDept = (String)tableModel.getValueAt(r, 2);

            // Determine course department code from instructor's department
            String courseDept = mapInstructorDeptToCourseDept(instDept);

            // Build list of courses filtered by department
            java.util.List<admin.dao.CourseDAO.Course> courses = courseDao.listAllCourses();
            java.util.List<admin.dao.CourseDAO.Course> filtered = new java.util.ArrayList<>();
            for (admin.dao.CourseDAO.Course c : courses) {
                if (courseDept == null || courseDept.isEmpty() || c.department.equalsIgnoreCase(courseDept)) filtered.add(c);
            }
            if (filtered.isEmpty()) { JOptionPane.showMessageDialog(this, "No courses available for instructor's department."); return; }

            // Build dialog with Course combo and Section combo
            JDialog d = new JDialog(this, "Assign Course & Section", true);
            JPanel p = new JPanel(new GridLayout(0,1,8,8)); p.setBorder(new EmptyBorder(12,12,12,12));
            JComboBox<String> courseCombo = new JComboBox<>();
            for (admin.dao.CourseDAO.Course c : filtered) courseCombo.addItem(c.code + " - " + c.title);
            JComboBox<String> sectionCombo = new JComboBox<>();
            p.add(new JLabel("Course:")); p.add(courseCombo);
            p.add(new JLabel("Section:")); p.add(sectionCombo);

            // helper to populate sections for a course (fetch all sections and map to A, B, C...)
            final java.util.List<Integer> sectionIds = new java.util.ArrayList<>();
            Runnable populateSections = () -> {
                sectionCombo.removeAllItems();
                sectionIds.clear();
                String sel = (String) courseCombo.getSelectedItem();
                if (sel == null) return;
                String code = sel.split(" - ")[0].trim();
                String courseTitle = sel.contains(" - ") ? sel.substring(sel.indexOf(" - ") + 3) : code;
                String dbUrl = DatabaseConfig.getDatabaseUrl();
                
                // First, try to fetch existing sections
                String sql = "SELECT section_id, day_time, instructor_id FROM sections WHERE course_code = ? ORDER BY section_id";
                try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        int idx = 0;
                        while (rs.next()) {
                            int sid = rs.getInt("section_id");
                            String dt = rs.getString("day_time");
                            String instr = rs.getString("instructor_id");
                            sectionIds.add(sid);
                            
                            // Map to SECTION A, B, C, ... based on order
                            char letter = (char)('A' + idx);
                            String status = (instr == null || instr.trim().isEmpty()) ? "Unassigned" : ("Assigned to " + instr);
                            String display = "SECTION " + letter + (dt == null ? "" : " (" + dt + ")") + " - " + status;
                            sectionCombo.addItem(display);
                            idx++;
                        }
                        
                        // If no sections exist, auto-create SECTION A and SECTION B
                        if (idx == 0) {
                            try {
                                String insertSql = "INSERT INTO sections (course_code, title, instructor_id, term, year, day_time, room, capacity) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                                
                                // Create SECTION A
                                try (PreparedStatement insSql = conn.prepareStatement(insertSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                                    insSql.setString(1, code);
                                    insSql.setString(2, courseTitle + " - Section A");
                                    insSql.setString(3, null);
                                    insSql.setString(4, "Fall");
                                    insSql.setInt(5, 2025);
                                    insSql.setString(6, "");
                                    insSql.setString(7, "");
                                    insSql.setInt(8, 60);
                                    insSql.executeUpdate();
                                    try (ResultSet keys = insSql.getGeneratedKeys()) {
                                        if (keys.next()) sectionIds.add(keys.getInt(1));
                                    }
                                }
                                
                                // Create SECTION B
                                try (PreparedStatement insSql = conn.prepareStatement(insertSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                                    insSql.setString(1, code);
                                    insSql.setString(2, courseTitle + " - Section B");
                                    insSql.setString(3, null);
                                    insSql.setString(4, "Fall");
                                    insSql.setInt(5, 2025);
                                    insSql.setString(6, "");
                                    insSql.setString(7, "");
                                    insSql.setInt(8, 60);
                                    insSql.executeUpdate();
                                    try (ResultSet keys = insSql.getGeneratedKeys()) {
                                        if (keys.next()) sectionIds.add(keys.getInt(1));
                                    }
                                }
                                
                                // Add to combo
                                sectionCombo.addItem("SECTION A - Unassigned");
                                sectionCombo.addItem("SECTION B - Unassigned");
                            } catch (SQLException creEx) {
                                sectionCombo.addItem("Error creating sections: " + creEx.getMessage());
                            }
                        }
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "Error loading sections: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
                }
            };

            courseCombo.addItemListener(ev -> {
                if (ev.getStateChange() == java.awt.event.ItemEvent.SELECTED) populateSections.run();
            });

            // initialize sections for first course
            if (courseCombo.getItemCount() > 0) populateSections.run();

            JButton assignBtn = new JButton("Assign"); JButton cancelBtn = new JButton("Cancel");
            assignBtn.addActionListener(ae -> {
                String selCourse = (String) courseCombo.getSelectedItem();
                int selIndex = sectionCombo.getSelectedIndex();
                if (selCourse == null || selIndex < 0 || selIndex >= sectionIds.size()) { 
                    JOptionPane.showMessageDialog(d, "Select a valid course and section."); 
                    return; 
                }
                try {
                    String courseCode = selCourse.split(" - ")[0].trim();
                    int sectionId = sectionIds.get(selIndex);
                    String dbUrl = DatabaseConfig.getDatabaseUrl();

                    // If already assigned to someone else, confirm overwrite
                    String currentInstructor = null;
                    String q = "SELECT instructor_id FROM sections WHERE section_id = ?";
                    try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement qps = conn.prepareStatement(q)) {
                        qps.setInt(1, sectionId);
                        try (ResultSet rs = qps.executeQuery()) {
                            if (rs.next()) currentInstructor = rs.getString("instructor_id");
                        }
                    }

                    if (currentInstructor != null && !currentInstructor.trim().isEmpty() && !currentInstructor.equals(insId)) {
                        int conf = JOptionPane.showConfirmDialog(d, "Section is currently assigned to " + currentInstructor + ". Overwrite assignment?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
                        if (conf != JOptionPane.YES_OPTION) return;
                    } else if (currentInstructor != null && currentInstructor.equals(insId)) {
                        JOptionPane.showMessageDialog(d, "This section is already assigned to the selected instructor.");
                        return;
                    }

                    String upd = "UPDATE sections SET instructor_id = ? WHERE section_id = ?";
                    try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement ps = conn.prepareStatement(upd)) {
                        ps.setString(1, insId); ps.setInt(2, sectionId);
                        int updated = ps.executeUpdate();
                        if (updated == 1) {
                            dao.assignCourse(insId, courseCode);
                            JOptionPane.showMessageDialog(d, "Assigned " + courseCode + " section to instructor.");
                            d.dispose(); reload.run();
                        } else JOptionPane.showMessageDialog(d, "Failed to assign section.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, "Error during assign: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            cancelBtn.addActionListener(ae -> d.dispose());

            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bp.add(assignBtn); bp.add(cancelBtn);
            d.add(p, BorderLayout.CENTER); d.add(bp, BorderLayout.SOUTH); d.setSize(520,260); d.setLocationRelativeTo(this); d.setVisible(true);
        });

        mainPanel.add(contentCard, BorderLayout.CENTER);
        return mainPanel;
    }

    /** Return a combo box model for branches constrained by degree */
    private DefaultComboBoxModel<String> createBranchComboBoxModel(String degree) {
        java.util.List<String> branches = new java.util.ArrayList<>();
        if ("MTech".equalsIgnoreCase(degree)) {
            // MTech allowed branches: ECE, CSE
            branches.add("CSE");
            branches.add("ECE");
        } else {
            // BTech allowed branches
            branches.add("CSE");
            branches.add("CSB");
            branches.add("ECE");
            branches.add("CSAM");
            branches.add("CSAI");
        }
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String b : branches) model.addElement(b);
        return model;
    }

private JComboBox<String> createBranchComboBox(String degree) {
    DefaultComboBoxModel<String> model = createBranchComboBoxModel(degree);
    JComboBox<String> combo = new JComboBox<>(model);
    return combo;
}


/** Create array of admission years (last n years) */
private String[] getAdmissionYears(int lastN) {
    int current = java.time.LocalDate.now().getYear();
    String[] arr = new String[lastN];
    for (int i = 0; i < lastN; i++) arr[i] = String.valueOf(current - i);
    return arr;
}

    /** Map human-friendly instructor department to course department code used in CourseDAO */
    private String mapInstructorDeptToCourseDept(String dept) {
        if (dept == null) return null;
        String d = dept.toLowerCase();
        if (d.contains("computer") || d.contains("science") || d.contains("cse")) return "CSE";
        if (d.contains("math") || d.contains("mathematics") || d.contains("mth")) return "MTH";
        if (d.contains("bio") || d.contains("biology")) return "BIO";
        if (d.contains("ece")) return "ECE";
        if (d.contains("design") || d.contains("human centred") || d.contains("hcd")) return "DES";
        if (d.contains("ssh") || d.contains("communication") ) return "SSH";
        // fallback: no filter
        return null;
    }

/** Update dashboard total students value (if panel is present) */
private void updateTotalStudentsCount() {
    try {
        if (totalStudentsValueLabel != null) {
            int total = adminService.getAllStudents().size();
            totalStudentsValueLabel.setText(String.valueOf(total));
        }
    } catch (Exception ignored) {}
}

private void updateInstructorCount(int n) {
    try {
        if (totalInstructorsValueLabel != null) totalInstructorsValueLabel.setText(String.valueOf(n));
    } catch (Exception ignored) {}
}

/** Safe reflection getter helper: tries to call a getter and return string value, otherwise empty. */
private String safeGetString(Object obj, String getterName) {
    if (obj == null) return "";
    try {
        java.lang.reflect.Method m = obj.getClass().getMethod(getterName);
        Object v = m.invoke(obj);
        return v == null ? "" : v.toString();
    } catch (Exception e) {
        return "";
    }
}

    private JPanel createCourseManagementPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("📚 Course Management");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(20, 20, 20, 20)
        ));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        actionPanel.setBackground(CARD_BG);

        // Filters like the uploaded image (checkboxes for departments)
        JCheckBox cse = new JCheckBox("CSE");
        JCheckBox ece = new JCheckBox("ECE");
        JCheckBox mth = new JCheckBox("MTH");
        JCheckBox bio = new JCheckBox("BIO");
        JCheckBox des = new JCheckBox("DES");
        JCheckBox ssh = new JCheckBox("SSH");
        JCheckBox others = new JCheckBox("OTHERS");

        JTextField courseSearch = new JTextField(20);
        JButton searchCourseBtn = new JButton("Search");
        JButton addCourseButton = new JButton("📚 Add Course");
        addCourseButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        addCourseButton.setBackground(new Color(60, 150, 60));
        addCourseButton.setForeground(Color.WHITE);
        addCourseButton.setFocusPainted(false);
        JButton refreshCoursesBtn = new JButton("🔄 Refresh");
        JButton deleteCourseBtn = new JButton("🗑️ Delete Course(s)");
        deleteCourseBtn.setBackground(new Color(200,60,60)); deleteCourseBtn.setForeground(Color.WHITE);
        JButton editCourseBtn = new JButton("✏️ Edit Course");
        editCourseBtn.setFont(new Font("SansSerif", Font.PLAIN, 13));

        actionPanel.add(cse); actionPanel.add(ece); actionPanel.add(mth); actionPanel.add(bio); actionPanel.add(des); actionPanel.add(ssh); actionPanel.add(others);
        actionPanel.add(Box.createHorizontalStrut(10));
        actionPanel.add(new JLabel("Search:")); actionPanel.add(courseSearch); actionPanel.add(searchCourseBtn);
        actionPanel.add(Box.createHorizontalStrut(10)); actionPanel.add(addCourseButton); actionPanel.add(refreshCoursesBtn); actionPanel.add(editCourseBtn); actionPanel.add(deleteCourseBtn);

        contentCard.add(actionPanel, BorderLayout.NORTH);

        String[] columns = {"Course Name", "Course Code", "Prerequisites", "Assigned Instructor(s)"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        admin.dao.CourseDAO courseDao = new admin.dao.CourseDAO();

        JTable courseTable = new JTable(tableModel);
        courseTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        courseTable.setRowHeight(35);

        JScrollPane tableScrollPane = new JScrollPane(courseTable);
        contentCard.add(tableScrollPane, BorderLayout.CENTER);

        // reload runnable
        admin.dao.InstructorDAO insDaoForCourses = new admin.dao.InstructorDAO();
        this.reloadCourses = () -> {
            tableModel.setRowCount(0);
            java.util.List<admin.dao.CourseDAO.Course> all = courseDao.listAllCourses();
            // build a map code->title for pretty prerequisite display
            java.util.Map<String,String> codeToTitle = new java.util.HashMap<>();
            for (admin.dao.CourseDAO.Course cc : all) codeToTitle.put(cc.code, cc.title);
            for (admin.dao.CourseDAO.Course c : all) {
                String prereqDisplay = "None";
                if (c.prerequisites != null && !c.prerequisites.trim().isEmpty() && !"None".equalsIgnoreCase(c.prerequisites)) {
                    String[] parts = c.prerequisites.split(",");
                    java.util.List<String> out = new java.util.ArrayList<>();
                    for (String p : parts) {
                        String code = p.trim();
                        if (code.isEmpty()) continue;
                        String t = codeToTitle.getOrDefault(code, "");
                        if (!t.isEmpty()) out.add(code + " (" + t + ")"); else out.add(code);
                    }
                    prereqDisplay = String.join(", ", out);
                }
                java.util.List<String> instructors = insDaoForCourses.getInstructorsForCourse(c.code);
                String instructorsDisplay = instructors.isEmpty() ? "-" : String.join(", ", instructors);
                tableModel.addRow(new Object[]{c.title, c.code, prereqDisplay, instructorsDisplay});
            }
        };
        if (this.reloadCourses != null) this.reloadCourses.run();

        // search action
        searchCourseBtn.addActionListener(e -> {
            java.util.List<String> depts = new java.util.ArrayList<>();
            if (cse.isSelected()) depts.add("CSE"); if (ece.isSelected()) depts.add("ECE"); if (mth.isSelected()) depts.add("MTH");
            if (bio.isSelected()) depts.add("BIO"); if (des.isSelected()) depts.add("DES"); if (ssh.isSelected()) depts.add("SSH");
            if (others.isSelected()) depts.add("OTHER");
            String q = courseSearch.getText().trim();
            tableModel.setRowCount(0);
            for (admin.dao.CourseDAO.Course c : courseDao.search(q, depts)) {
                // resolve prereqs with course names
                String prereqDisplay = "None";
                if (c.prerequisites != null && !c.prerequisites.trim().isEmpty() && !"None".equalsIgnoreCase(c.prerequisites)) {
                    String[] parts = c.prerequisites.split(",");
                    java.util.List<String> out = new java.util.ArrayList<>();
                    for (String p : parts) {
                        String code = p.trim();
                        String cTitle = "";
                        for (admin.dao.CourseDAO.Course cc : courseDao.listAllCourses()) if (cc.code.equalsIgnoreCase(code)) { cTitle = cc.title; break; }
                        if (!cTitle.isEmpty()) out.add(code + " (" + cTitle + ")"); else out.add(code);
                    }
                    prereqDisplay = String.join(", ", out);
                }
                java.util.List<String> instrs = insDaoForCourses.getInstructorsForCourse(c.code);
                String instructorsDisplay = instrs.isEmpty() ? "-" : String.join(", ", instrs);
                tableModel.addRow(new Object[]{c.title, c.code, prereqDisplay, instructorsDisplay});
            }
        });

        // Make filters live: whenever a checkbox toggles, trigger the search action
        java.awt.event.ItemListener il = ev -> searchCourseBtn.doClick();
        cse.addItemListener(il); ece.addItemListener(il); mth.addItemListener(il); bio.addItemListener(il); des.addItemListener(il); ssh.addItemListener(il); others.addItemListener(il);

        // refresh
        refreshCoursesBtn.addActionListener(e -> {
            if (this.reloadCourses != null) this.reloadCourses.run();
            JOptionPane.showMessageDialog(this, "Course list refreshed!");
        });

        // add course dialog
        addCourseButton.addActionListener(e -> {
            JDialog d = new JDialog(this, "Add Course", true);
            JPanel p = new JPanel(new GridLayout(5,2,8,8)); p.setBorder(new EmptyBorder(12,12,12,12));
            JTextField codef = new JTextField(); JTextField titlef = new JTextField(); JTextField prereqf = new JTextField();
            JComboBox<String> dept = new JComboBox<>(new String[]{"CSE","ECE","MTH","BIO","DES","SSH","OTHER"});
            p.add(new JLabel("Course Code:")); p.add(codef);
            p.add(new JLabel("Course Title:")); p.add(titlef);
            p.add(new JLabel("Department:")); p.add(dept);
            p.add(new JLabel("Prerequisites:")); p.add(prereqf);
            JButton save = new JButton("Save"); JButton cancel = new JButton("Cancel");
            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bp.add(save); bp.add(cancel);
            d.add(p, BorderLayout.CENTER); d.add(bp, BorderLayout.SOUTH);
            d.setSize(700, 450);
            d.setResizable(true);
            d.setLocationRelativeTo(this);

            save.addActionListener(ae -> {
                String code = codef.getText().trim(); String courseTitle = titlef.getText().trim(); String pr = prereqf.getText().trim(); String dp = (String)dept.getSelectedItem();
                if (code.isEmpty() || courseTitle.isEmpty()) { JOptionPane.showMessageDialog(d, "Code and Title required", "Validation", JOptionPane.WARNING_MESSAGE); return; }
                admin.dao.CourseDAO.Course nc = new admin.dao.CourseDAO.Course(code, courseTitle, dp, pr.isEmpty() ? "None" : pr);
                boolean ok = courseDao.addCourse(nc);
                if (ok) { JOptionPane.showMessageDialog(d, "Course added"); d.dispose(); if (this.reloadCourses != null) this.reloadCourses.run(); } else { JOptionPane.showMessageDialog(d, "Course code already exists", "Error", JOptionPane.ERROR_MESSAGE); }
            });
            cancel.addActionListener(ae -> d.dispose());
            d.setVisible(true);
        });

        // delete courses
        deleteCourseBtn.addActionListener(e -> {
            int[] sel = courseTable.getSelectedRows();
            if (sel.length == 0) { JOptionPane.showMessageDialog(this, "Select course(s) to delete."); return; }
            int confirm = JOptionPane.showConfirmDialog(this, "Delete selected course(s)?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            for (int i = sel.length-1; i>=0; i--) {
                String code = tableModel.getValueAt(sel[i], 1).toString();
                // remove instructor assignments first
                insDaoForCourses.removeAssignmentsByCourse(code);
                courseDao.deleteCourse(code);
                tableModel.removeRow(sel[i]);
            }
        });

        // edit course
        editCourseBtn.addActionListener(e -> {
            int r = courseTable.getSelectedRow();
            if (r == -1) { JOptionPane.showMessageDialog(this, "Select one course to edit."); return; }
            String code = tableModel.getValueAt(r, 1).toString();
            // preload course
            admin.dao.CourseDAO.Course cur = null;
            for (admin.dao.CourseDAO.Course cc : courseDao.listAllCourses()) if (cc.code.equalsIgnoreCase(code)) { cur = cc; break; }
            if (cur == null) { JOptionPane.showMessageDialog(this, "Course not found."); return; }
            JDialog d = new JDialog(this, "Edit Course", true);
            JPanel p = new JPanel(new GridLayout(5,2,8,8)); p.setBorder(new EmptyBorder(12,12,12,12));
            JTextField codef = new JTextField(cur.code); codef.setEditable(false); codef.setBackground(new Color(240,240,240));
            JTextField titlef = new JTextField(cur.title);
            JComboBox<String> deptc = new JComboBox<>(new String[]{"CSE","ECE","MTH","BIO","DES","SSH","OTHER"}); deptc.setSelectedItem(cur.department);
            JTextField prereqf = new JTextField(cur.prerequisites == null ? "" : cur.prerequisites);
            p.add(new JLabel("Course Code:")); p.add(codef);
            p.add(new JLabel("Course Title:")); p.add(titlef);
            p.add(new JLabel("Department:")); p.add(deptc);
            p.add(new JLabel("Prerequisites (comma-separated codes):")); p.add(prereqf);
            JButton save = new JButton("Save"); JButton cancel = new JButton("Cancel");
            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bp.add(save); bp.add(cancel);
            d.add(p, BorderLayout.CENTER); d.add(bp, BorderLayout.SOUTH);
            d.setSize(700,450); d.setResizable(true); d.setLocationRelativeTo(this);

            save.addActionListener(ae -> {
                String newTitle = titlef.getText().trim(); String dp = (String)deptc.getSelectedItem(); String pr = prereqf.getText().trim();
                if (newTitle.isEmpty()) { JOptionPane.showMessageDialog(d, "Title required", "Validation", JOptionPane.WARNING_MESSAGE); return; }
                admin.dao.CourseDAO.Course updated = new admin.dao.CourseDAO.Course(code, newTitle, dp, pr.isEmpty() ? "None" : pr);
                boolean ok = courseDao.updateCourse(code, updated);
                if (ok) { JOptionPane.showMessageDialog(d, "Course updated"); d.dispose(); if (this.reloadCourses != null) this.reloadCourses.run(); } else { JOptionPane.showMessageDialog(d, "Failed to update course", "Error", JOptionPane.ERROR_MESSAGE); }
            });
            cancel.addActionListener(ae -> d.dispose());
            d.setVisible(true);
        });

        mainPanel.add(contentCard, BorderLayout.CENTER);
        return mainPanel;
    }

    private JPanel createFeeStructurePanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("💰 Fee Structure Management");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(20, 20, 20, 20)
        ));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        actionPanel.setBackground(CARD_BG);

        updateFeesButton = new JButton("💰 Update Fee Structure");
        updateFeesButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        updateFeesButton.setBackground(new Color(60, 150, 60));
        updateFeesButton.setForeground(Color.WHITE);
        updateFeesButton.setFocusPainted(false);

        actionPanel.add(updateFeesButton);

        contentCard.add(actionPanel, BorderLayout.NORTH);

        // We'll split the fee structure into three distinct sections:
        // 1) Tuition fees (B.Tech year-wise)
        // 2) Part-wise / first-year breakdown
        // 3) Hostel fees
        DefaultTableModel tuitionModel = new DefaultTableModel(new String[]{"Program","Year","Tuition Fee","Total"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        DefaultTableModel partModel = new DefaultTableModel(new String[]{"Program","Part","Tuition Fee","Notes"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        DefaultTableModel hostelModel = new DefaultTableModel(new String[]{"Program","Period","Hostel Fee","Notes"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        // Seed Tuition (B.Tech year-wise)
        tuitionModel.addRow(new Object[]{"B.Tech - Tuition", "Year 1", "450000", "450000"});
        tuitionModel.addRow(new Object[]{"B.Tech - Tuition", "Year 2", "475000", "475000"});
        tuitionModel.addRow(new Object[]{"B.Tech - Tuition", "Year 3", "500000", "500000"});
        tuitionModel.addRow(new Object[]{"B.Tech - Tuition", "Year 4", "530000", "530000"});

        // Seed Part-wise / First year breakdown
        partModel.addRow(new Object[]{"B.Tech - First Year", "Admission (part)", "225000", "Admission installment"});
        partModel.addRow(new Object[]{"B.Tech - First Year", "Semester 1", "225000", "First semester"});
        partModel.addRow(new Object[]{"B.Tech - First Year", "Semester 2", "225000", "Second semester"});
        partModel.addRow(new Object[]{"Misc", "Alumni Fee (one-time)", "2000", "One-time"});
        partModel.addRow(new Object[]{"Misc", "Security Money (Refundable)", "10000", "Refundable"});

        // Seed Hostel fees
        hostelModel.addRow(new Object[]{"Hostel - Double Sharing Room", "Per Semester", "38250", "Room charges"});
        hostelModel.addRow(new Object[]{"Hostel - Security", "One-time", "10000", "Refundable"});
        hostelModel.addRow(new Object[]{"Hostel - Mess Charges", "15 days coupon", "8000", "Mess coupon"});
        hostelModel.addRow(new Object[]{"Hostel - Total", "Per Semester", "56250", "Total per semester"});

        JTable tuitionTable = new JTable(tuitionModel);
        tuitionTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tuitionTable.setRowHeight(32);

        JTable partTable = new JTable(partModel);
        partTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        partTable.setRowHeight(30);

        JTable hostelTable = new JTable(hostelModel);
        hostelTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        hostelTable.setRowHeight(30);

        JPanel multiPanel = new JPanel();
        multiPanel.setLayout(new BoxLayout(multiPanel, BoxLayout.Y_AXIS));

        // Tuition section
        JPanel tPanel = new JPanel(new BorderLayout(6,6));
        tPanel.setBackground(CARD_BG);
        JLabel tLabel = new JLabel("Tuition Fees (B.Tech Year-wise)"); tLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        tPanel.add(tLabel, BorderLayout.NORTH);
        tPanel.add(new JScrollPane(tuitionTable), BorderLayout.CENTER);
        tPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        multiPanel.add(tPanel);

        multiPanel.add(Box.createRigidArea(new Dimension(0,10)));

        // Part-wise section
        JPanel pPanel = new JPanel(new BorderLayout(6,6));
        pPanel.setBackground(CARD_BG);
        JLabel pLabel = new JLabel("Part-wise / First-year Breakdown"); pLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        pPanel.add(pLabel, BorderLayout.NORTH);
        pPanel.add(new JScrollPane(partTable), BorderLayout.CENTER);
        pPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        multiPanel.add(pPanel);

        multiPanel.add(Box.createRigidArea(new Dimension(0,10)));

        // Hostel section
        JPanel hPanel = new JPanel(new BorderLayout(6,6));
        hPanel.setBackground(CARD_BG);
        JLabel hLabel = new JLabel("Hostel Fee Structure"); hLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        hPanel.add(hLabel, BorderLayout.NORTH);
        hPanel.add(new JScrollPane(hostelTable), BorderLayout.CENTER);
        hPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        multiPanel.add(hPanel);

        contentCard.add(multiPanel, BorderLayout.CENTER);

        // Update dialog: allow editing each section in its own tab
        updateFeesButton.addActionListener(e -> {
            JDialog dlg = new JDialog(this, "Update Fee Structure", true);
            dlg.setSize(1000, 600);
            dlg.setLocationRelativeTo(this);

            JTabbedPane tabs = new JTabbedPane();

            // Editable models for dialog copies
            DefaultTableModel editTuition = new DefaultTableModel(new String[]{"Program","Year","Tuition Fee","Total"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return c != 3; }
            };
            DefaultTableModel editPart = new DefaultTableModel(new String[]{"Program","Part","Tuition Fee","Notes"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return true; }
            };
            DefaultTableModel editHostel = new DefaultTableModel(new String[]{"Program","Period","Hostel Fee","Notes"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return c != 3 ? true : true; }
            };

            // copy data
            for (int i = 0; i < tuitionModel.getRowCount(); i++) editTuition.addRow(new Object[]{tuitionModel.getValueAt(i,0), tuitionModel.getValueAt(i,1), tuitionModel.getValueAt(i,2), tuitionModel.getValueAt(i,3)});
            for (int i = 0; i < partModel.getRowCount(); i++) editPart.addRow(new Object[]{partModel.getValueAt(i,0), partModel.getValueAt(i,1), partModel.getValueAt(i,2), partModel.getValueAt(i,3)});
            for (int i = 0; i < hostelModel.getRowCount(); i++) editHostel.addRow(new Object[]{hostelModel.getValueAt(i,0), hostelModel.getValueAt(i,1), hostelModel.getValueAt(i,2), hostelModel.getValueAt(i,3)});

            JTable editTuitionTable = new JTable(editTuition); editTuitionTable.setRowHeight(30);
            JTable editPartTable = new JTable(editPart); editPartTable.setRowHeight(28);
            JTable editHostelTable = new JTable(editHostel); editHostelTable.setRowHeight(28);

            JPanel tuitionTab = new JPanel(new BorderLayout(8,8)); tuitionTab.add(new JScrollPane(editTuitionTable), BorderLayout.CENTER);
            JPanel partTab = new JPanel(new BorderLayout(8,8)); partTab.add(new JScrollPane(editPartTable), BorderLayout.CENTER);
            JPanel hostelTab = new JPanel(new BorderLayout(8,8)); hostelTab.add(new JScrollPane(editHostelTable), BorderLayout.CENTER);

            tabs.addTab("Tuition (Year-wise)", tuitionTab);
            tabs.addTab("Part-wise", partTab);
            tabs.addTab("Hostel", hostelTab);

            JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton addRow = new JButton("+ Add Row");
            JButton delRow = new JButton("- Delete Row");
            JButton save = new JButton("Save");
            JButton cancel = new JButton("Cancel");
            bp.add(addRow); bp.add(delRow); bp.add(Box.createHorizontalStrut(10)); bp.add(cancel); bp.add(save);

            dlg.setLayout(new BorderLayout(8,8));
            dlg.add(tabs, BorderLayout.CENTER);
            dlg.add(bp, BorderLayout.SOUTH);

            addRow.addActionListener(ae -> {
                int idx = tabs.getSelectedIndex();
                if (idx == 0) editTuition.addRow(new Object[]{"B.Tech - Tuition","Year","0","0"});
                else if (idx == 1) editPart.addRow(new Object[]{"B.Tech - First Year","Part","0",""});
                else editHostel.addRow(new Object[]{"Hostel","Period","0",""});
            });
            delRow.addActionListener(ae -> {
                int idx = tabs.getSelectedIndex();
                if (idx == 0) { int r = editTuitionTable.getSelectedRow(); if (r != -1) editTuition.removeRow(r); }
                else if (idx == 1) { int r = editPartTable.getSelectedRow(); if (r != -1) editPart.removeRow(r); }
                else { int r = editHostelTable.getSelectedRow(); if (r != -1) editHostel.removeRow(r); }
            });

            save.addActionListener(ae -> {
                try {
                    // validate numeric fields and compute totals where applicable
                    for (int i = 0; i < editTuition.getRowCount(); i++) {
                        String tStr = String.valueOf(editTuition.getValueAt(i,2)).replaceAll("[^0-9.]", "");
                        double t = tStr.isEmpty() ? 0 : Double.parseDouble(tStr);
                        editTuition.setValueAt(String.format("%.2f", t), i, 2);
                        editTuition.setValueAt(String.format("%.2f", t), i, 3); // total == tuition for tuition rows
                    }
                    for (int i = 0; i < editPart.getRowCount(); i++) {
                        String tStr = String.valueOf(editPart.getValueAt(i,2)).replaceAll("[^0-9.]", "");
                        double t = tStr.isEmpty() ? 0 : Double.parseDouble(tStr);
                        editPart.setValueAt(String.format("%.2f", t), i, 2);
                    }
                    for (int i = 0; i < editHostel.getRowCount(); i++) {
                        String hStr = String.valueOf(editHostel.getValueAt(i,2)).replaceAll("[^0-9.]", "");
                        double h = hStr.isEmpty() ? 0 : Double.parseDouble(hStr);
                        editHostel.setValueAt(String.format("%.2f", h), i, 2);
                    }

                    // apply back to main models
                    tuitionModel.setRowCount(0);
                    for (int i = 0; i < editTuition.getRowCount(); i++) tuitionModel.addRow(new Object[]{editTuition.getValueAt(i,0), editTuition.getValueAt(i,1), editTuition.getValueAt(i,2), editTuition.getValueAt(i,3)});
                    partModel.setRowCount(0);
                    for (int i = 0; i < editPart.getRowCount(); i++) partModel.addRow(new Object[]{editPart.getValueAt(i,0), editPart.getValueAt(i,1), editPart.getValueAt(i,2), editPart.getValueAt(i,3)});
                    hostelModel.setRowCount(0);
                    for (int i = 0; i < editHostel.getRowCount(); i++) hostelModel.addRow(new Object[]{editHostel.getValueAt(i,0), editHostel.getValueAt(i,1), editHostel.getValueAt(i,2), editHostel.getValueAt(i,3)});

                    // persist fee tables to CSV so exporter can include current values
                    try {
                        saveFeesToCsv(tuitionModel, partModel, hostelModel);
                    } catch (Exception ioe) {
                        // non-fatal; show a message but keep dialog closing
                        JOptionPane.showMessageDialog(dlg, "Warning: could not save fee CSVs: " + ioe.getMessage(), "Warning", JOptionPane.WARNING_MESSAGE);
                    }

                    JOptionPane.showMessageDialog(dlg, "Fee structure updated.");
                    dlg.dispose();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(dlg, "Please enter valid numeric values for fees.", "Validation", JOptionPane.ERROR_MESSAGE);
                }
            });

            cancel.addActionListener(ae -> dlg.dispose());

            dlg.setVisible(true);
        });

        mainPanel.add(contentCard, BorderLayout.CENTER);
        return mainPanel;
    }

    

    private JPanel createReportsPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("📊 System Reports & Analytics");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);

        // Top bar with title and refresh button
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(LIGHT_BG);
        topBar.add(title, BorderLayout.WEST);
        JButton refreshAll = new JButton("Refresh");
        refreshAll.setToolTipText("Reload all reports with latest data");
        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnWrap.setBackground(LIGHT_BG);
        btnWrap.add(refreshAll);
        topBar.add(btnWrap, BorderLayout.EAST);

        mainPanel.add(topBar, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new GridLayout(2, 2, 20, 20));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(20, 20, 20, 20)
        ));

        // helper to populate sections so we can refresh on demand
        Runnable populate = () -> {
            contentCard.removeAll();
            contentCard.add(createStudentReportSection());
            contentCard.add(createInstructorReportSection());
            contentCard.add(createCourseReportSection());
            contentCard.add(createHostelReportSection());
            contentCard.revalidate();
            contentCard.repaint();
        };

        // initial population
        populate.run();

        // refresh action
        refreshAll.addActionListener(ae -> {
            refreshAll.setEnabled(false);
            try {
                populate.run();
            } finally {
                refreshAll.setEnabled(true);
            }
        });

        mainPanel.add(contentCard, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createStudentReportSection() {
        java.util.List<domain.Student> students = adminService.getAllStudents();
        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1), new EmptyBorder(15,15,15,15)
        ));
        JLabel titleLabel = new JLabel("\uD83D\uDCDA Student Reports"); titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        JLabel summaryLabel = new JLabel("Total Students: " + students.size()); summaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel top = new JPanel(new BorderLayout()); top.setBackground(LIGHT_BG); top.add(titleLabel, BorderLayout.NORTH); top.add(summaryLabel, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);

        DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Name","Email"}, 0) { @Override public boolean isCellEditable(int r,int c){return false;} };
        for (domain.Student s : students) m.addRow(new Object[]{s.getId(), s.getName(), s.getEmail()});
        JTable t = new JTable(m); t.setRowHeight(24);
        panel.add(new JScrollPane(t), BorderLayout.CENTER);

        JButton export = new JButton("Export CSV");
        export.addActionListener(e -> {
            try {
                File file = reportsService.exportStudentsToCSV(students, userSession.getUsername());
                JOptionPane.showMessageDialog(this, "Exported to: " + file.getAbsolutePath());
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
        });
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER)); bp.setBackground(LIGHT_BG); bp.add(export);
        panel.add(bp, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createInstructorReportSection() {
        admin.dao.InstructorDAO dao = new admin.dao.InstructorDAO();
        java.util.List<domain.Instructor> ins = dao.listAll();
        JPanel panel = new JPanel(new BorderLayout(10,10)); panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_GRAY,1), new EmptyBorder(15,15,15,15)));
        JLabel titleLabel = new JLabel("\uD83D\uDC68\u200D\uD83C\uDF93 Instructor Reports"); titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        JLabel summary = new JLabel("Total Instructors: " + ins.size()); summary.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel top = new JPanel(new BorderLayout()); top.setBackground(LIGHT_BG); top.add(titleLabel, BorderLayout.NORTH); top.add(summary, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);

        DefaultTableModel m = new DefaultTableModel(new String[]{"ID","Name","Dept","Email"}, 0) { @Override public boolean isCellEditable(int r,int c){return false;} };
        for (domain.Instructor i : ins) m.addRow(new Object[]{i.getId(), i.getName(), i.getDepartment(), i.getEmail()});
        JTable t = new JTable(m); t.setRowHeight(24);
        panel.add(new JScrollPane(t), BorderLayout.CENTER);

        JButton export = new JButton("Export CSV"); export.addActionListener(e -> {
            try {
                File f = reportsService.exportInstructorsToCSV(ins, userSession.getUsername());
                JOptionPane.showMessageDialog(this, "Exported to: " + f.getAbsolutePath());
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
        });
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER)); bp.setBackground(LIGHT_BG); bp.add(export);
        panel.add(bp, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createCourseReportSection() {
        admin.dao.CourseDAO courseDao = new admin.dao.CourseDAO();
        java.util.List<admin.dao.CourseDAO.Course> courses = courseDao.listAllCourses();
        JPanel panel = new JPanel(new BorderLayout(10,10)); panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_GRAY,1), new EmptyBorder(15,15,15,15)));
        JLabel titleLabel = new JLabel("\uD83D\uDCD6 Course Reports"); titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        JLabel summary = new JLabel("Total Courses: " + courses.size()); summary.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel top = new JPanel(new BorderLayout()); top.setBackground(LIGHT_BG); top.add(titleLabel, BorderLayout.NORTH); top.add(summary, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);

        DefaultTableModel m = new DefaultTableModel(new String[]{"Code","Title","Dept","Prereqs"}, 0) { @Override public boolean isCellEditable(int r,int c){return false;} };
        for (admin.dao.CourseDAO.Course c : courses) m.addRow(new Object[]{c.code, c.title, c.department, c.prerequisites});
        JTable t = new JTable(m); t.setRowHeight(24);
        panel.add(new JScrollPane(t), BorderLayout.CENTER);

        JButton export = new JButton("Export CSV"); export.addActionListener(e -> {
            try { File f = reportsService.exportCoursesToCSV(courses, userSession.getUsername()); JOptionPane.showMessageDialog(this, "Exported to: " + f.getAbsolutePath()); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
        });
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER)); bp.setBackground(LIGHT_BG); bp.add(export);
        panel.add(bp, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createHostelReportSection() {
        // Build fee-structure view (tuition, part-wise, hostel) for reports
        DefaultTableModel tuitionModel = new DefaultTableModel(new String[] {"Program", "Year", "Tuition Fee", "Total"}, 0) { @Override public boolean isCellEditable(int r,int c){return false;} };
        DefaultTableModel partModel = new DefaultTableModel(new String[] {"Program", "Part", "Tuition Fee", "Notes"}, 0) { @Override public boolean isCellEditable(int r,int c){return false;} };
        DefaultTableModel hostelModel = new DefaultTableModel(new String[] {"Program", "Period", "Hostel Fee", "Notes"}, 0) { @Override public boolean isCellEditable(int r,int c){return false;} };

        // Seed same defaults as fee editor (these are also editable in Fee UI during runtime)
        tuitionModel.addRow(new Object[]{"B.Tech - Tuition", "Year 1", "450000", "450000"});
        tuitionModel.addRow(new Object[]{"B.Tech - Tuition", "Year 2", "475000", "475000"});
        tuitionModel.addRow(new Object[]{"B.Tech - Tuition", "Year 3", "500000", "500000"});
        tuitionModel.addRow(new Object[]{"B.Tech - Tuition", "Year 4", "530000", "530000"});

        partModel.addRow(new Object[]{"B.Tech - First Year", "Admission (part)", "225000", "Admission installment"});
        partModel.addRow(new Object[]{"B.Tech - First Year", "Semester 1", "225000", "First semester"});
        partModel.addRow(new Object[]{"B.Tech - First Year", "Semester 2", "225000", "Second semester"});
        partModel.addRow(new Object[]{"Misc", "Alumni Fee (one-time)", "2000", "One-time"});
        partModel.addRow(new Object[]{"Misc", "Security Money (Refundable)", "10000", "Refundable"});

        hostelModel.addRow(new Object[]{"Hostel - Double Sharing Room", "Per Semester", "38250", "Room charges"});
        hostelModel.addRow(new Object[]{"Hostel - Security", "One-time", "10000", "Refundable"});
        hostelModel.addRow(new Object[]{"Hostel - Mess Charges", "15 days coupon", "8000", "Mess coupon"});
        hostelModel.addRow(new Object[]{"Hostel - Total", "Per Semester", "56250", "Total per semester"});

        JTable tuitionTable = new JTable(tuitionModel); tuitionTable.setRowHeight(28); tuitionTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JTable partTable = new JTable(partModel); partTable.setRowHeight(26); partTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JTable hostelTable = new JTable(hostelModel); hostelTable.setRowHeight(26); hostelTable.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel multiPanel = new JPanel(); multiPanel.setLayout(new BoxLayout(multiPanel, BoxLayout.Y_AXIS)); multiPanel.setBackground(LIGHT_BG);

        JPanel tPanel = new JPanel(new BorderLayout(6,6)); tPanel.setBackground(CARD_BG); JLabel tLabel = new JLabel("Tuition Fees (B.Tech Year-wise)"); tLabel.setFont(new Font("SansSerif", Font.BOLD, 14)); tPanel.add(tLabel, BorderLayout.NORTH); tPanel.add(new JScrollPane(tuitionTable), BorderLayout.CENTER); tPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160)); multiPanel.add(tPanel);
        multiPanel.add(Box.createRigidArea(new Dimension(0,10)));
        JPanel pPanel = new JPanel(new BorderLayout(6,6)); pPanel.setBackground(CARD_BG); JLabel pLabel = new JLabel("Part-wise / First-year Breakdown"); pLabel.setFont(new Font("SansSerif", Font.BOLD, 14)); pPanel.add(pLabel, BorderLayout.NORTH); pPanel.add(new JScrollPane(partTable), BorderLayout.CENTER); pPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180)); multiPanel.add(pPanel);
        multiPanel.add(Box.createRigidArea(new Dimension(0,10)));
        JPanel hPanel = new JPanel(new BorderLayout(6,6)); hPanel.setBackground(CARD_BG); JLabel hLabel = new JLabel("Hostel Fee Structure"); hLabel.setFont(new Font("SansSerif", Font.BOLD, 14)); hPanel.add(hLabel, BorderLayout.NORTH); hPanel.add(new JScrollPane(hostelTable), BorderLayout.CENTER); hPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160)); multiPanel.add(hPanel);

        JPanel panel = new JPanel(new BorderLayout(10,10)); panel.setBackground(LIGHT_BG); panel.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_GRAY,1), new EmptyBorder(15,15,15,15)));
        JLabel titleLabel = new JLabel("💰 Fee Structure Reports"); titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(multiPanel, BorderLayout.CENTER);

        JButton export = new JButton("Export CSV"); export.addActionListener(e -> {
            try {
                java.util.List<String[]> trows = new java.util.ArrayList<>();
                for (int i = 0; i < tuitionModel.getRowCount(); i++) trows.add(new String[]{String.valueOf(tuitionModel.getValueAt(i,0)), String.valueOf(tuitionModel.getValueAt(i,1)), String.valueOf(tuitionModel.getValueAt(i,2)), String.valueOf(tuitionModel.getValueAt(i,3))});
                java.util.List<String[]> prows = new java.util.ArrayList<>();
                for (int i = 0; i < partModel.getRowCount(); i++) prows.add(new String[]{String.valueOf(partModel.getValueAt(i,0)), String.valueOf(partModel.getValueAt(i,1)), String.valueOf(partModel.getValueAt(i,2)), String.valueOf(partModel.getValueAt(i,3))});
                java.util.List<String[]> hrows = new java.util.ArrayList<>();
                for (int i = 0; i < hostelModel.getRowCount(); i++) hrows.add(new String[]{String.valueOf(hostelModel.getValueAt(i,0)), String.valueOf(hostelModel.getValueAt(i,1)), String.valueOf(hostelModel.getValueAt(i,2)), String.valueOf(hostelModel.getValueAt(i,3))});
                File f = reportsService.exportFeesToCSV(trows, prows, hrows, userSession.getUsername());
                JOptionPane.showMessageDialog(this, "Exported to: " + f.getAbsolutePath());
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
        });
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER)); bp.setBackground(LIGHT_BG); bp.add(export);
        panel.add(bp, BorderLayout.SOUTH);
        return panel;
    }

    /** Maintenance panel with toggle and backup/cleanup */
    private JPanel createMaintenancePanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("⚙ System Maintenance & Configuration");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(20, 20));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(40, 40, 40, 40)
        ));

        JPanel controlsPanel = new JPanel(new GridLayout(6, 1, 15, 15));
        controlsPanel.setBackground(CARD_BG);

        maintenanceToggle = new JCheckBox("Enable System-wide Maintenance Mode");
        maintenanceToggle.setFont(new Font("SansSerif", Font.BOLD, 16));
        maintenanceToggle.setToolTipText("When enabled, mutating actions become read-only for non-admins.");
        maintenanceToggle.setSelected(SystemState.isMaintenance());

        backupButton = new JButton("💾 Perform Database Backup");
        backupButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        backupButton.setToolTipText("Creates a timestamped backup of the database.");

        // cleanup button removed — feature omitted per user request

        exportAllButton = new JButton("📦 Export All Data");
        exportAllButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        exportAllButton.setToolTipText("Export full ERP workspace data as a zip file.");

        JButton importStudentsButton = new JButton("📥 Import Students from CSV");
        importStudentsButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        importStudentsButton.setToolTipText("Import student data from a CSV file into the database.");

        maintenanceBanner.setHorizontalAlignment(SwingConstants.CENTER);
        maintenanceBanner.setFont(new Font("SansSerif", Font.BOLD, 13));
        maintenanceBanner.setForeground(new Color(180, 30, 30));
        maintenanceBanner.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        maintenanceBanner.setText(SystemState.isMaintenance() ? "MAINTENANCE MODE ENABLED — Read-only for non-admin users" : "");

        controlsPanel.add(maintenanceToggle);
        controlsPanel.add(backupButton);
        // cleanup button removed
        controlsPanel.add(exportAllButton);
        controlsPanel.add(importStudentsButton);
        controlsPanel.add(new JPanel());

        contentCard.add(controlsPanel, BorderLayout.CENTER);
        contentCard.add(maintenanceBanner, BorderLayout.SOUTH);

        mainPanel.add(contentCard, BorderLayout.CENTER);

        importStudentsButton.addActionListener(e -> {
            if (!Authz.hasAnyRole(userSession, "SUPERADMIN", "ADMIN")) {
                JOptionPane.showMessageDialog(this, "Only admins can import student data.", "Permission Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files", "csv"));
            int result = fileChooser.showOpenDialog(this);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                importStudentsButton.setEnabled(false);

                SwingWorker<Integer, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Integer doInBackground() throws Exception {
                        return importStudentsFromCSV(selectedFile);
                    }

                    @Override
                    protected void done() {
                        importStudentsButton.setEnabled(true);
                        try {
                            int count = get();
                            JOptionPane.showMessageDialog(AdminDashboard.this, 
                                "Successfully imported " + count + " student(s).", 
                                "Import Success", 
                                JOptionPane.INFORMATION_MESSAGE);
                            // Refresh the Students panel if it's visible
                            refreshStudentsPanel();
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(AdminDashboard.this, 
                                "Import failed: " + ex.getMessage(), 
                                "Import Error", 
                                JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                worker.execute();
            }
        });

        maintenanceToggle.addActionListener(e -> {
            boolean allowed = Authz.hasAnyRole(userSession, "SUPERADMIN", "ADMIN");
            if (!allowed) {
                JOptionPane.showMessageDialog(this, "You do not have permission to change maintenance mode.", "Permission Denied", JOptionPane.WARNING_MESSAGE);
                maintenanceToggle.setSelected(!maintenanceToggle.isSelected());
                return;
            }

            boolean enabled = maintenanceToggle.isSelected();
            SystemState.setMaintenance(enabled);
            applyMaintenanceState(enabled);
            maintenanceBanner.setText(enabled ? "MAINTENANCE MODE ENABLED — Read-only for non-admin users" : "");
            // ...existing code...
            JOptionPane.showMessageDialog(this, "Maintenance mode " + (enabled ? "ENABLED" : "DISABLED") + ".");
        });

        backupButton.addActionListener(e -> {
            if (!Authz.hasAnyRole(userSession, "SUPERADMIN", "ADMIN")) {
                JOptionPane.showMessageDialog(this, "Only admins can perform backups.", "Permission Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            backupButton.setEnabled(false);
            // Enhanced backup UX: run & show progress dialog
            final JDialog progress = new JDialog(this, "Running Backup", true);
            JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); progress.add(new JLabel("Backing up..."), BorderLayout.NORTH); progress.add(bar, BorderLayout.CENTER); progress.setSize(300, 100); progress.setLocationRelativeTo(this);
            SwingWorker<File, Void> bw = new SwingWorker<>() {
                @Override protected File doInBackground() throws Exception {
                    try {
                        return backupService.performBackup(userSession.getUsername());
                    } catch (Exception ex) {
                        throw ex;
                    }
                }
                @Override protected void done() {
                    progress.dispose();
                    try {
                        File f = get();
                        if (f != null) JOptionPane.showMessageDialog(AdminDashboard.this, "Backup completed: " + f.getAbsolutePath());
                        else JOptionPane.showMessageDialog(AdminDashboard.this, "Backup completed (no file created).");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(AdminDashboard.this, "Backup failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        backupButton.setEnabled(true);
                    }
                }
            };
            bw.execute();
            progress.setVisible(true);
        });

        // cleanup action removed per request

        exportAllButton.addActionListener(e -> {
            if (!Authz.hasAnyRole(userSession, "SUPERADMIN", "ADMIN")) {
                JOptionPane.showMessageDialog(this, "Only admins can export data.", "Permission Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            exportAllButton.setEnabled(false);
            SwingWorker<File, Void> w = new SwingWorker<>() {
                @Override protected File doInBackground() throws Exception {
                    return backupService.exportAllData(userSession.getUsername());
                }
                @Override protected void done() {
                    try {
                        File f = get();
                        JOptionPane.showMessageDialog(AdminDashboard.this, "Exported to: " + f.getAbsolutePath());
                    } catch (Exception ex) { JOptionPane.showMessageDialog(AdminDashboard.this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
                    exportAllButton.setEnabled(true);
                }
            };
            w.execute();
        });

        // import action removed

        applyMaintenanceState(SystemState.isMaintenance());
        return mainPanel;
    }

    private JPanel createManagementPanel(String moduleName) {
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel(moduleName + " Management");
        title.setFont(new Font("SansSerif", Font.BOLD, 36));
        title.setForeground(HEADER_TEXT);
        panel.add(title, BorderLayout.NORTH);
        JPanel contentCard = new JPanel(new GridBagLayout());
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(40, 40, 40, 40)
        ));

        JLabel comingSoonLabel = new JLabel("Content for " + moduleName + " management coming soon...");
        comingSoonLabel.setFont(new Font("SansSerif", Font.ITALIC, 24));
        comingSoonLabel.setForeground(new Color(100, 100, 100));
        contentCard.add(comingSoonLabel);
        panel.add(contentCard, BorderLayout.CENTER);

        return panel;
    }

    /** helper for reports */
    private void applyMaintenanceState(boolean maintenanceEnabled) {
        try { if (addStudentButton != null) addStudentButton.setEnabled(!maintenanceEnabled); } catch (Exception ignored) {}
        try { if (editButton != null) editButton.setEnabled(!maintenanceEnabled); } catch (Exception ignored) {}
        try { if (deleteButton != null) deleteButton.setEnabled(!maintenanceEnabled); } catch (Exception ignored) {}
        try { if (refreshButton != null) refreshButton.setEnabled(!maintenanceEnabled); } catch (Exception ignored) {}
        try { if (updateFeesButton != null) updateFeesButton.setEnabled(!maintenanceEnabled); } catch (Exception ignored) {}
        try { if (backupButton != null) backupButton.setEnabled(!maintenanceEnabled); } catch (Exception ignored) {}
        // cleanup button removed
        try { if (exportAllButton != null) exportAllButton.setEnabled(!maintenanceEnabled); } catch (Exception ignored) {}
        // importDataButton removed

        maintenanceBanner.setText(maintenanceEnabled ? "MAINTENANCE MODE ENABLED — Read-only for non-admin users" : "");
    }

    // --- OFFER COURSES PANEL ---
    private JPanel createOfferCoursesPanel() {
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("📢 Offer Courses to Students");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        panel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_GRAY, 1),
                new EmptyBorder(20, 20, 20, 20)
        ));

        // Top Filter Panel - Year, Branch
        JPanel filterPanel = new JPanel(new BorderLayout(15, 10));
        filterPanel.setBackground(CARD_BG);
        filterPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel filterTopPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        filterTopPanel.setBackground(CARD_BG);
        
        // MODIFICATION: Use numeric years for DB consistency
        JComboBox<String> yearCombo = new JComboBox<>(new String[]{"1", "2", "3", "4"}); 
        JComboBox<String> branchCombo = new JComboBox<>(new String[]{"CSE", "CSAI", "CSAM", "CSB", "ECE"});

        filterTopPanel.add(new JLabel("Select Year:"));
        filterTopPanel.add(yearCombo);
        filterTopPanel.add(new JLabel("Select Branch:"));
        filterTopPanel.add(branchCombo);

        filterPanel.add(filterTopPanel, BorderLayout.WEST);

        // Department filter with checkboxes (Copied from Course Management)
        JPanel deptFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        deptFilterPanel.setBackground(CARD_BG);
        deptFilterPanel.setBorder(BorderFactory.createTitledBorder("Filter by Department"));
        
        String[] departments = {"CSE", "ECE", "MTH", "BIO", "DES", "SSH", "OTHERS"};
        java.util.Map<String, JCheckBox> deptCheckboxes = new java.util.HashMap<>();
        
        for (String dept : departments) {
            JCheckBox checkbox = new JCheckBox(dept);
            checkbox.setSelected(true);
            checkbox.setBackground(CARD_BG);
            deptCheckboxes.put(dept, checkbox);
            deptFilterPanel.add(checkbox);
        }

        filterPanel.add(deptFilterPanel, BorderLayout.CENTER);
        contentCard.add(filterPanel, BorderLayout.NORTH);

        // Split view: Courses only
        JPanel coursesPanel = new JPanel(new BorderLayout(10, 10));
        coursesPanel.setBackground(CARD_BG);
        coursesPanel.setBorder(BorderFactory.createTitledBorder("Available Courses"));

        // Course Search Bar
        JPanel courseSearchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        courseSearchPanel.setBackground(CARD_BG);
        JLabel searchLabel = new JLabel("Search:");
        JTextField courseSearchField = new JTextField(20);
        courseSearchPanel.add(searchLabel);
        courseSearchPanel.add(courseSearchField);
        coursesPanel.add(courseSearchPanel, BorderLayout.NORTH);

        // Courses Table
        String[] courseCols = {"Course Code", "Title", "Department", "Select"};
        DefaultTableModel coursesModel = new DefaultTableModel(courseCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3;
            }
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 3 ? Boolean.class : String.class;
            }
        };

        JTable coursesTable = new JTable(coursesModel);
        coursesTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        coursesTable.setRowHeight(25);
        JScrollPane coursesScroll = new JScrollPane(coursesTable);
        coursesPanel.add(coursesScroll, BorderLayout.CENTER);

        // Load all courses initially with department filter
        Runnable loadAllCourses = () -> {
            coursesModel.setRowCount(0);
            try {
                admin.dao.CourseDAO courseDaoLocal = new admin.dao.CourseDAO();
                for (admin.dao.CourseDAO.Course course : courseDaoLocal.listAllCourses()) {
                    String dept = extractBranchFromCode(course.code); // Use the existing helper
                    String courseDept = course.department;
                    
                    // Check if department is selected
                    JCheckBox deptBox = deptCheckboxes.getOrDefault(courseDept, deptCheckboxes.get("OTHERS"));
                    
                    if (deptBox != null && deptBox.isSelected()) {
                        coursesModel.addRow(new Object[]{course.code, course.title, courseDept, false});
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading courses: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        };
        loadAllCourses.run();

        // Add listeners to department checkboxes to reload courses
        for (JCheckBox checkbox : deptCheckboxes.values()) {
            checkbox.addActionListener(e -> {
                courseSearchField.setText("");
                loadAllCourses.run();
            });
        }

        // Search functionality
        courseSearchField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                String searchTerm = courseSearchField.getText().toLowerCase();
                coursesModel.setRowCount(0);
                try {
                    admin.dao.CourseDAO courseDaoLocal = new admin.dao.CourseDAO();
                    for (admin.dao.CourseDAO.Course course : courseDaoLocal.listAllCourses()) {
                        if (course.code.toLowerCase().contains(searchTerm) || 
                            course.title.toLowerCase().contains(searchTerm)) {
                            
                            String courseDept = course.department;
                            JCheckBox deptBox = deptCheckboxes.getOrDefault(courseDept, deptCheckboxes.get("OTHERS"));

                            if (deptBox != null && deptBox.isSelected()) {
                                coursesModel.addRow(new Object[]{course.code, course.title, courseDept, false});
                            }
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AdminDashboard.this, "Error searching courses: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        contentCard.add(coursesPanel, BorderLayout.CENTER);

        // Action Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttonPanel.setBackground(CARD_BG);

        JButton clearBtn = new JButton("Clear Selection");
        clearBtn.setBackground(new Color(230, 126, 34));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.addActionListener(e -> {
            for (int i = 0; i < coursesModel.getRowCount(); i++) {
                coursesModel.setValueAt(false, i, 3);
            }
        });

        JButton offerBtn = new JButton("Offer Selected Courses");
        offerBtn.setBackground(new Color(52, 152, 219));
        offerBtn.setForeground(Color.WHITE);
        offerBtn.addActionListener(e -> {
            if (SystemState.isMaintenance()) {
                 JOptionPane.showMessageDialog(this, "System is in maintenance mode. Cannot offer courses.", "Maintenance", JOptionPane.WARNING_MESSAGE);
                 return;
            }
            
            try {
                java.util.List<String> selectedCourses = new java.util.ArrayList<>();
                for (int i = 0; i < coursesModel.getRowCount(); i++) {
                    if ((Boolean) coursesModel.getValueAt(i, 3)) {
                        selectedCourses.add((String) coursesModel.getValueAt(i, 0));
                    }
                }
                
                if (selectedCourses.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please select at least one course.", "Selection Required", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                String yearStr = (String) yearCombo.getSelectedItem();
                String branch = (String) branchCombo.getSelectedItem();
                
                // Publish offerings (inserts into `offerings` table)
                try (java.sql.Connection c = java.sql.DriverManager.getConnection(DatabaseConfig.getDatabaseUrl())) {
                    // Ensure offerings table exists
                    try (java.sql.Statement s = c.createStatement()) {
                        s.execute("CREATE TABLE IF NOT EXISTS offerings (course_code TEXT, branch TEXT, year INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, created_by TEXT, PRIMARY KEY(course_code, branch, year))");
                    }
                    
                    String insertOfferSql = "INSERT OR IGNORE INTO offerings (course_code, branch, year, created_by) VALUES (?, ?, ?, ?)";
                    try (java.sql.PreparedStatement offerStmt = c.prepareStatement(insertOfferSql)) {
                        int year = Integer.parseInt(yearStr);
                        String createdBy = userSession == null ? "Admin" : userSession.getUsername();
                        
                        for (String courseCode : selectedCourses) {
                            offerStmt.setString(1, courseCode);
                            offerStmt.setString(2, branch);
                            offerStmt.setInt(3, year);
                            offerStmt.setString(4, createdBy);
                            offerStmt.executeUpdate();
                        }
                    }
                }

                // Update settings table flag (optional, for real-time refresh cues)
                try (java.sql.Connection c2 = java.sql.DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                     java.sql.PreparedStatement ps = c2.prepareStatement("INSERT OR REPLACE INTO settings (key_name, value) VALUES ('last_offered_at', datetime('now'))")) {
                    ps.executeUpdate();
                } catch (java.sql.SQLException ex) {
                    System.err.println("Warning: failed to update last_offered_at setting: " + ex.getMessage());
                }

                JOptionPane.showMessageDialog(this, "Successfully offered " + selectedCourses.size() + " course(s) to branch " + branch + " year " + yearStr + ".", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadAllCourses.run(); // Reload to clear selections
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error offering courses: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        buttonPanel.add(clearBtn);
        buttonPanel.add(offerBtn);
        contentCard.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(contentCard, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStudentEnrollmentsPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("📋 Student Enrollments");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_GRAY, 1),
                new EmptyBorder(20, 20, 20, 20)
        ));

        // Search and filter panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        searchPanel.setBackground(CARD_BG);

        JLabel searchLabel = new JLabel("Search by Student ID or Name:");
        searchLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JTextField searchField = new JTextField(20);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JButton searchBtn = new JButton("🔍 Search");
        searchBtn.setBackground(ACCENT_BLUE);
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setFont(new Font("SansSerif", Font.BOLD, 12));

        JButton refreshBtn = new JButton("🔄 Refresh");
        refreshBtn.setBackground(ACCENT_BLUE);
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFont(new Font("SansSerif", Font.BOLD, 12));

        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchPanel.add(refreshBtn);

        // Enrollments table
        String[] columns = {"Student ID", "Student Name", "Enrolled Courses"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable enrollmentsTable = new JTable(tableModel);
        enrollmentsTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        enrollmentsTable.setRowHeight(30);
        enrollmentsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        enrollmentsTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        enrollmentsTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        enrollmentsTable.getColumnModel().getColumn(2).setPreferredWidth(400);

        // Load all enrollments initially
        Runnable loadEnrollments = () -> {
            tableModel.setRowCount(0);
            String filterText = searchField.getText().trim().toLowerCase();

            String sql = "SELECT DISTINCT e.student_id, s.name, " +
                    "GROUP_CONCAT(COALESCE(e.course_code, sec.course_code) || ' (Section ' || COALESCE(SUBSTR(sec.title, -1), '-') || ')', ', ') as courses " +
                    "FROM enrollments e " +
                    "LEFT JOIN students s ON e.student_id = s.id " +
                    "LEFT JOIN sections sec ON e.section_id = sec.section_id " +
                    "GROUP BY e.student_id " +
                    "ORDER BY e.student_id";

            try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                 PreparedStatement ps = c.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String studentId = rs.getString("student_id");
                        String studentName = rs.getString("name");
                        String courses = rs.getString("courses");

                        // Apply filter if search text is provided
                        if (!filterText.isEmpty()) {
                            if (!studentId.toLowerCase().contains(filterText) &&
                                !((studentName != null) ? studentName.toLowerCase().contains(filterText) : false)) {
                                continue;
                            }
                        }

                        tableModel.addRow(new Object[]{
                                studentId,
                                studentName != null ? studentName : "N/A",
                                courses != null ? courses : "No courses"
                        });
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(mainPanel, "Error loading enrollments: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        searchBtn.addActionListener(e -> loadEnrollments.run());
        refreshBtn.addActionListener(e -> {
            searchField.setText("");
            loadEnrollments.run();
        });

        contentCard.add(searchPanel, BorderLayout.NORTH);
        contentCard.add(new JScrollPane(enrollmentsTable), BorderLayout.CENTER);

        mainPanel.add(contentCard, BorderLayout.CENTER);

        // Load data on panel creation
        SwingUtilities.invokeLater(loadEnrollments);

        return mainPanel;
    }

    private JPanel createOfferedCoursesPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("📚 Offered Courses");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_GRAY, 1),
                new EmptyBorder(20, 20, 20, 20)
        ));

        // Filter and search panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        filterPanel.setBackground(CARD_BG);

        JLabel searchLabel = new JLabel("Search by Code/Name:");
        JTextField searchField = new JTextField(15);

        JLabel branchLabel = new JLabel("Branch:");
        JComboBox<String> branchCombo = new JComboBox<>(new String[]{"All", "CSE", "CSAM", "CSAI", "CSB", "ECE"});

        JLabel yearLabel = new JLabel("Year:");
        JComboBox<String> yearCombo = new JComboBox<>(new String[]{"All", "1", "2", "3", "4"}); 

        JButton filterBtn = new JButton("🔍 Filter");
        filterBtn.setBackground(ACCENT_BLUE);
        filterBtn.setForeground(Color.WHITE);

        JButton refreshBtn = new JButton("🔄 Refresh");
        refreshBtn.setBackground(ACCENT_BLUE);
        refreshBtn.setForeground(Color.WHITE);

        filterPanel.add(searchLabel);
        filterPanel.add(searchField);
        filterPanel.add(branchLabel);
        filterPanel.add(branchCombo);
        filterPanel.add(yearLabel);
        filterPanel.add(yearCombo);
        filterPanel.add(filterBtn);
        filterPanel.add(refreshBtn);

        // Offered courses table
        String[] columns = {"Course Code", "Branch", "Year", "Credits", "Status", "Action"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable offeredTable = new JTable(tableModel);
        offeredTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        offeredTable.setRowHeight(28);
        offeredTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        
        // Load offered courses
        Runnable loadOfferedCourses = () -> {
            tableModel.setRowCount(0);
            String searchText = searchField.getText().trim().toLowerCase();
            String selectedBranch = (String) branchCombo.getSelectedItem();
            String selectedYear = (String) yearCombo.getSelectedItem();

            // FIX: Correctly JOIN the offerings table with the persistent courses table
            String sql = "SELECT o.course_code, c.name AS name, COALESCE(c.credits,4) AS credits, o.branch, o.year " +
                    "FROM offerings o LEFT JOIN courses c ON o.course_code = c.code ORDER BY o.course_code";

            try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                 PreparedStatement ps = c.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    java.util.Set<String> addedCourses = new java.util.HashSet<>();
                    while (rs.next()) {
                        String courseCode = rs.getString("course_code");
                        String courseName = rs.getString("name");
                        String credits = String.valueOf(rs.getInt("credits")); 

                        String uniqueKey = courseCode + rs.getString("branch") + rs.getInt("year");
                        if (addedCourses.contains(uniqueKey)) continue;

                        // Apply search filter
                        if (!searchText.isEmpty()) {
                            if (!courseCode.toLowerCase().contains(searchText) &&
                                !(courseName != null && courseName.toLowerCase().contains(searchText))) {
                                continue;
                            }
                        }

                        // Get branch/year details
                        String branchDisplay = rs.getString("branch") == null || rs.getString("branch").isEmpty() ? "All" : rs.getString("branch");
                        int yearInt = rs.getInt("year");
                        
                        // FIX: Correct Year Display Format (1 -> 1st Year)
                        String yearDisplay;
                        if (yearInt == 1) yearDisplay = "1st Year";
                        else if (yearInt == 2) yearDisplay = "2nd Year";
                        else if (yearInt == 3) yearDisplay = "3rd Year";
                        else if (yearInt == 4) yearDisplay = "4th Year";
                        else yearDisplay = "All";
                        
                        // Apply branch filter
                        if (!"All".equals(selectedBranch) && !branchDisplay.equalsIgnoreCase(selectedBranch)) continue;
                        
                        // Apply year filter
                        if (!"All".equals(selectedYear)) {
                            try {
                                int selectedYearInt = Integer.parseInt(selectedYear.replaceAll("[^0-9]", ""));
                                if (yearInt > 0 && yearInt != selectedYearInt) continue;
                            } catch (NumberFormatException ignored) {}
                        }

                        addedCourses.add(uniqueKey);
                        String actionLabel = "<html><span style='color:#E74C3C; font-weight:bold;'>Un-offer</span></html>";

                        tableModel.addRow(new Object[]{
                                courseCode,  // Column 0: Course Code
                                branchDisplay,
                                yearDisplay,
                                credits,
                                "Active",
                                actionLabel
                        });
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(mainPanel, "Error loading offered courses: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        // Add action listener to table for Un-offer button
        offeredTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = offeredTable.rowAtPoint(e.getPoint());
                int col = offeredTable.columnAtPoint(e.getPoint());
                if (col == 5 && row != -1) { // Action column
                    String courseCode = (String) tableModel.getValueAt(row, 0);
                    String branch = (String) tableModel.getValueAt(row, 1);
                    String yearDisplay = (String) tableModel.getValueAt(row, 2);
                    int year = 0;
                    try { year = Integer.parseInt(yearDisplay.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                    
                    String confirmationMsg = String.format("Are you sure you want to stop offering %s for %s %s?", courseCode, branch, yearDisplay);

                    if (JOptionPane.showConfirmDialog(mainPanel, confirmationMsg, "Confirm Un-offer", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        if (SystemState.isMaintenance()) {
                            JOptionPane.showMessageDialog(mainPanel, "System is in maintenance mode. Cannot un-offer courses.", "Maintenance", JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        
                        try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl())) {
                            // Delete based on the composite key (Code, Branch, Year)
                            String deleteSql = "DELETE FROM offerings WHERE course_code = ? AND branch = ? AND year = ?";
                            try (PreparedStatement ps = c.prepareStatement(deleteSql)) {
                                ps.setString(1, courseCode);
                                ps.setString(2, branch.equalsIgnoreCase("All") ? "" : branch);
                                ps.setInt(3, year); 
                                ps.executeUpdate();
                            }
                            JOptionPane.showMessageDialog(mainPanel, "Course un-offered successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                            loadOfferedCourses.run();
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(mainPanel, "Error un-offering course: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });

        filterBtn.addActionListener(e -> loadOfferedCourses.run());
        refreshBtn.addActionListener(e -> {
            searchField.setText("");
            branchCombo.setSelectedIndex(0);
            yearCombo.setSelectedIndex(0);
            loadOfferedCourses.run();
        });

        contentCard.add(filterPanel, BorderLayout.NORTH);
        contentCard.add(new JScrollPane(offeredTable), BorderLayout.CENTER);

        mainPanel.add(contentCard, BorderLayout.CENTER);

        // Load data on panel creation
        SwingUtilities.invokeLater(loadOfferedCourses);

        return mainPanel;
    }

    private String extractBranchFromCode(String courseCode) {
        if (courseCode == null || courseCode.isEmpty()) return "Unknown";
        String prefix = courseCode.replaceAll("[0-9]", "").toUpperCase();
        java.util.Map<String, String> branchMap = new java.util.HashMap<>();
        branchMap.put("CS", "CSE");
        branchMap.put("EC", "ECE");
        branchMap.put("MT", "MTH");
        branchMap.put("BI", "BIO");
        branchMap.put("DE", "DES");
        branchMap.put("SS", "SSH");
        branchMap.put("HC", "DES");
        branchMap.put("CB", "BIO");
        branchMap.put("PH", "DES");
        branchMap.put("SC", "SSH");
        return branchMap.getOrDefault(prefix, "Unknown");
    }

    private int importStudentsFromCSV(File file) throws Exception {
        int importedCount = 0;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
             Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl())) {

            String line;
            boolean isFirstLine = true;
            
            // Expected CSV format: id, name, email, branch, year_of_study, section, admission_year, status
            String insertSQL = "INSERT OR REPLACE INTO students (id, name, email, branch, year_of_study, section, admission_year, status, degree) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                while ((line = reader.readLine()) != null) {
                    // Skip header line
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }

                    String[] parts = line.split(",");
                    if (parts.length < 8) {
                        continue; // Skip invalid lines
                    }

                    try {
                        String id = parts[0].trim();
                        String name = parts[1].trim();
                        String email = parts[2].trim();
                        String branch = parts[3].trim();
                        String yearOfStudy = parts[4].trim();
                        String section = parts[5].trim();
                        String admissionYear = parts[6].trim();
                        String status = parts[7].trim();

                        pstmt.setString(1, id);
                        pstmt.setString(2, name);
                        pstmt.setString(3, email);
                        pstmt.setString(4, branch);
                        pstmt.setString(5, yearOfStudy);
                        pstmt.setString(6, section);
                        pstmt.setString(7, admissionYear);
                        pstmt.setString(8, status);
                        pstmt.setString(9, "Bachelor");

                        pstmt.addBatch();
                        importedCount++;

                        if (importedCount % 100 == 0) {
                            pstmt.executeBatch();
                        }
                    } catch (Exception ex) {
                        System.err.println("Error parsing line: " + line + " - " + ex.getMessage());
                    }
                }

                // Execute remaining batch
                pstmt.executeBatch();
            }
        }

        return importedCount;
    }

    private void refreshStudentsPanel() {
        // Find and refresh the students panel if it's currently displayed
        Component[] components = mainContentPanel.getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel && mainContentPanel.getComponentCount() > 0) {
                // Trigger a refresh by reloading the panel
                mainContentPanel.revalidate();
                mainContentPanel.repaint();
                break;
            }
        }
    }
}
