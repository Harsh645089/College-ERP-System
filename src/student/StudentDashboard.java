package student;

import domain.UserSession;
import domain.Student;
import domain.Course;
import student.services.StudentService;
import login.DatabaseConfig;
import login.LoginWindow;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import instructor.dao.GradingSchemeDAO;
import instructor.dao.AssessmentDAO;


/**
 * StudentDashboard - Implements Profile, Registration, Grades, and Fees features.
 * Maintains UI consistency with AdminDashboard.
 */
public class StudentDashboard extends JFrame {

    // --- UI Theme Colors (Consistent with AdminDashboard) ---
    private static final Color DARK_BG = new Color(28, 29, 30);
    private static final Color DARK_HOVER = new Color(60, 61, 62);
    private static final Color LIGHT_TEXT = Color.WHITE;
    private static final Color LOGOUT_RED = new Color(220, 53, 69);
    // make the main background slightly greyer
    private static final Color LIGHT_BG = new Color(228, 230, 233);
    // card background: a soft grey instead of pure white
    private static final Color CARD_BG = new Color(246, 247, 248);
    private static final Color HEADER_TEXT = new Color(28, 28, 28);
    private static final Color BORDER_GRAY = new Color(200, 200, 200);
    private static final Color ACCENT_BLUE = new Color(27, 116, 228);
    private static final Color ACCENT_GREEN = new Color(40, 167, 69);
    private static final Color ACCENT_YELLOW = new Color(255, 193, 7);

    // UI controllers
    private final UserSession userSession;
    private final Student currentStudent;
    private final CardLayout mainCardLayout;
    private final JPanel mainContentPanel;
    private JPanel sidebarPanel;
    private JButton activeModuleButton;

    // Services
    private final StudentService studentService;
    private final GradingSchemeDAO gradingSchemeDAO = new GradingSchemeDAO();
    private final AssessmentDAO assessmentDAO = new AssessmentDAO();

    // Table Models
    private DefaultTableModel registrationTableModel;
    private JTable registrationTable;
    private DefaultTableModel gradesTableModel;
    private String lastOfferedAt = null;
    private javax.swing.Timer offeredPollTimer;

    public StudentDashboard(UserSession session) {
        this.userSession = session;

        // initialize service bound to this session (enforces access control)
        this.studentService = new StudentService(session);

        // FIX: Use getUsername() instead of getId()
        this.currentStudent = studentService.getStudentProfile(session.getUsername());

        if (currentStudent == null) {
            JOptionPane.showMessageDialog(null, "Error: Could not load student profile. Check database connection or username.", "Fatal Error", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException("Student profile not found for username: " + session.getUsername());
        }

        setTitle("Student Dashboard | University ERP");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        mainCardLayout = new CardLayout();
        mainContentPanel = new JPanel(mainCardLayout);
        mainContentPanel.setBackground(LIGHT_BG);

        sidebarPanel = createSidebar();

        // --- Add all panels to the CardLayout ---
        mainContentPanel.add(createDashboardPanel(), "Dashboard");
        mainContentPanel.add(createProfilePanel(), "Profile");
        mainContentPanel.add(createCourseRegistrationPanel(), "Registration");
        mainContentPanel.add(createGradesPanel(), "Grades");
        mainContentPanel.add(createFeeDetailsPanel(), "FeeDetails");

        add(sidebarPanel, BorderLayout.WEST);
        add(mainContentPanel, BorderLayout.CENTER);

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setVisible(true);

        // Select Dashboard on load and initialize tables
        SwingUtilities.invokeLater(() -> {
            for (Component comp : sidebarPanel.getComponents()) {
                if (comp instanceof JButton && "Dashboard".equals(((JButton) comp).getName())) {
                    selectButton((JButton) comp, "Dashboard");
                    break;
                }
            }
            // Load initial data for the two panels that rely on dynamic data
                loadCourseCatalog();
                loadStudentGrades();
                // Start background poll to refresh offered courses when admin updates them
                startOfferedPoller();
        });
    }

    // =================================================================
    //                            UI UTILITIES
    // =================================================================

    private JPanel createSidebar() {
        JPanel panel = new JPanel();
        panel.setBackground(DARK_BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(240, getHeight()));

        // --- Profile/Header Area ---
        JLabel profileLabel = new JLabel("Student Portal");
        profileLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        profileLabel.setForeground(LIGHT_TEXT);
        profileLabel.setBorder(new EmptyBorder(30, 20, 10, 20));
        profileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(profileLabel);

        JLabel userLabel = new JLabel("Welcome, " + currentStudent.getName().split(" ")[0]);
        userLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        userLabel.setForeground(new Color(180, 180, 180));
        userLabel.setBorder(new EmptyBorder(0, 20, 20, 20));
        userLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(userLabel);

        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        // --- Navigation Buttons ---
        panel.add(createNavButton("Dashboard", "Dashboard", "⌂"));
        panel.add(createNavButton("My Profile", "Profile", "👤"));
        panel.add(createNavButton("Course Registration", "Registration", "📝"));
        panel.add(createNavButton("My Grades", "Grades", "💯"));

        panel.add(Box.createRigidArea(new Dimension(0, 15)));
        panel.add(createChangePasswordButton());

        panel.add(Box.createVerticalGlue());
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
                this.dispose();
                // Reopen the login window
                try {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        try {
                            // Assuming login.LoginWindow is your entry point
                            LoginWindow lw = new LoginWindow();
                            lw.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
                            lw.setVisible(true);
                        } catch (Throwable t) {
                            System.err.println("Could not reopen login window: " + t.getMessage());
                            System.exit(0);
                        }
                    });
                } catch (Exception ex) {
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

    private JButton createChangePasswordButton() {
        JButton changePassButton = new JButton(" 🔑  Change Password");
        changePassButton.setName("ChangePassword");
        changePassButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        changePassButton.setForeground(LIGHT_TEXT);
        changePassButton.setBackground(ACCENT_BLUE);
        changePassButton.setFocusPainted(false);
        changePassButton.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        changePassButton.setHorizontalAlignment(SwingConstants.LEFT);
        Dimension buttonSize = new Dimension(240, 45);
        changePassButton.setMaximumSize(buttonSize);
        changePassButton.setMinimumSize(buttonSize);

        changePassButton.addActionListener(e -> showChangePasswordDialog());

        changePassButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                changePassButton.setBackground(new Color(0, 100, 200));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                changePassButton.setBackground(ACCENT_BLUE);
            }
        });

        return changePassButton;
    }

    private void showChangePasswordDialog() {
        JPasswordField curr = new JPasswordField();
        JPasswordField np = new JPasswordField();
        JPasswordField confirm = new JPasswordField();

        JPanel p = new JPanel(new GridLayout(0,1,6,6));
        p.add(new JLabel("Current Password:")); p.add(curr);
        p.add(new JLabel("New Password:")); p.add(np);
        p.add(new JLabel("Confirm New Password:")); p.add(confirm);

        int ok = JOptionPane.showConfirmDialog(this, p, "Change Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        String current = new String(curr.getPassword());
        String newp = new String(np.getPassword());
        String conf = new String(confirm.getPassword());

        if (current.isEmpty() || newp.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill both current and new passwords.", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!newp.equals(conf)) {
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
            logout();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to change password. Please try again.", "Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void logout() {
        this.dispose();
        SwingUtilities.invokeLater(() -> {
            try {
                LoginWindow lw = new LoginWindow();
                lw.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
                lw.setVisible(true);
            } catch (Throwable t) {
                System.err.println("Could not reopen login window: " + t.getMessage());
                System.exit(0);
            }
        });
    }

    private void selectButton(JButton button, String cardName) {
        if (activeModuleButton != null) {
            activeModuleButton.setBackground(DARK_BG);
            activeModuleButton.setForeground(LIGHT_TEXT);
        }

        activeModuleButton = button;
        activeModuleButton.setBackground(DARK_HOVER);
        activeModuleButton.setForeground(LIGHT_TEXT);

        // Custom logic for panel refresh
        if (cardName.equals("Registration")) {
            loadCourseCatalog();
        } else if (cardName.equals("Grades")) {
            loadStudentGrades();
        }

        mainCardLayout.show(mainContentPanel, cardName);
    }

    private JPanel createDetailBox(String title, String value, Color accent, String icon) {
        // Card with vertical colored strip at left to match admin look
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(CARD_BG);
        outer.setPreferredSize(new Dimension(220, 110));
        outer.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_GRAY, 1), BorderFactory.createEmptyBorder(0,0,0,0)));

        JPanel strip = new JPanel();
        strip.setBackground(accent);
        strip.setPreferredSize(new Dimension(8, 80));
        outer.add(strip, BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(CARD_BG);
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        // Icon + title row
        JPanel headerRow = new JPanel(new BorderLayout(8,0));
        headerRow.setBackground(CARD_BG);
        JLabel iconLabel = new JLabel(icon != null ? icon : "");
        iconLabel.setFont(new Font(Font.SERIF, Font.PLAIN, 20));
        iconLabel.setForeground(accent);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        titleLabel.setForeground(new Color(90, 90, 90));
        headerRow.add(iconLabel, BorderLayout.WEST);
        headerRow.add(titleLabel, BorderLayout.CENTER);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        valueLabel.setForeground(HEADER_TEXT);

        content.add(headerRow, BorderLayout.NORTH);
        content.add(valueLabel, BorderLayout.CENTER);
        outer.add(content, BorderLayout.CENTER);
        return outer;
    }

    // =================================================================
    //                            DASHBOARD PANEL
    // =================================================================

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Top Header
        JPanel topHeader = new JPanel(new BorderLayout());
        topHeader.setBackground(LIGHT_BG);
        JLabel welcomeMsg = new JLabel("Welcome back, " + currentStudent.getName().split(" ")[0] + "! Your quick summary is below.");
        welcomeMsg.setFont(new Font("SansSerif", Font.PLAIN, 18));
        welcomeMsg.setForeground(HEADER_TEXT);
        topHeader.add(welcomeMsg, BorderLayout.WEST);

        JLabel dateLabel = new JLabel(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        dateLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        dateLabel.setForeground(new Color(120,120,120));
        topHeader.add(dateLabel, BorderLayout.EAST);
        panel.add(topHeader, BorderLayout.NORTH);

        // Central Content
        JPanel centralContent = new JPanel(new GridBagLayout());
        centralContent.setBackground(LIGHT_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        // Summary Boxes (3 boxes)
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        summaryPanel.setBackground(LIGHT_BG);

        // Dynamic data retrieval (use service for computed values)
        double cgpaVal = studentService.getCurrentCGPA(currentStudent.getId());
        String cgpa = String.format("%.2f", cgpaVal);
        String courses = String.valueOf(studentService.getRegisteredCoursesCount(currentStudent.getId()));
        int feesAmount = studentService.getFeesDue(currentStudent.getId());
        String feesDue = feesAmount > 0 ? "₹" + feesAmount : "None";

        summaryPanel.add(createDetailBox("Current CGPA", cgpa, ACCENT_BLUE, "🎓"));
        summaryPanel.add(createDetailBox("Active Courses", courses, ACCENT_GREEN, "📚"));
        summaryPanel.add(createDetailBox("Fees Due", feesDue, feesAmount > 0 ? LOGOUT_RED : ACCENT_GREEN, "💳"));

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0; gbc.weighty = 0.3;
        centralContent.add(summaryPanel, gbc);

        // Quick Links/Announcements
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 1.0; gbc.weighty = 0.7;
        centralContent.add(createStudentAppsPanel(), gbc);

        panel.add(centralContent, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStudentAppsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_GRAY, 1),
            new EmptyBorder(16, 16, 16, 16)
        ));
        JLabel title = new JLabel("STUDENT QUICK LINKS");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(HEADER_TEXT);
        panel.add(title, BorderLayout.NORTH);

        JPanel appsGrid = new JPanel(new GridLayout(1, 3, 18, 18));
        appsGrid.setBackground(CARD_BG);

        appsGrid.add(createAppServiceCard("My Profile", "Profile", "👤"));
        appsGrid.add(createAppServiceCard("Register Courses", "Registration", "📝"));
        appsGrid.add(createAppServiceCard("View Grades", "Grades", "💯"));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(CARD_BG);
        wrap.add(appsGrid, BorderLayout.CENTER);
        panel.add(wrap, BorderLayout.CENTER);
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
                // Special handling for tiles that map to local actions
                if ("ShowGradingScheme".equals(targetCard)) {
                    showGradingSchemeDialog();
                    return;
                }
                if ("ChangePassword".equals(targetCard)) {
                    // open change-password dialog directly
                    showChangePasswordDialog();
                    return;
                }

                // Otherwise, find and simulate click on the corresponding sidebar button
                if (sidebarPanel != null) {
                    for (Component comp : sidebarPanel.getComponents()) {
                        if (comp instanceof JButton && targetCard.equals(((JButton) comp).getName())) {
                            selectButton((JButton) comp, targetCard);
                            break;
                        }
                    }
                }
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

    /**
     * Show a modal dialog with the grading scheme and student's averages for each registered course.
     */
    private void showGradingSchemeDialog() {
        try {
            java.util.List<Course> catalog = studentService.getCourseCatalogForStudent(currentStudent.getId());
            java.util.List<Course> regs = new java.util.ArrayList<>();
            for (Course c : catalog) {
                if (c.getStatus() != null && c.getStatus().equalsIgnoreCase("Registered")) regs.add(c);
            }

            if (regs.isEmpty()) {
                JOptionPane.showMessageDialog(this, "You are not registered in any courses.", "No Data", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (Course c : regs) {
                int sectionId = Integer.parseInt(c.getSectionId());
                sb.append("Course: ").append(c.getCourseCode()).append(" - ").append(c.getTitle()).append(" (Section ").append(sectionId).append(")\n");
                java.util.Map<String, Integer> scheme = null;
                try {
                    scheme = gradingSchemeDAO.loadGradingScheme(sectionId);
                } catch (Exception ignored) {}
                if (scheme == null || scheme.isEmpty()) {
                    sb.append("  No grading scheme defined for this section.\n\n");
                    continue;
                }
                double totalWeighted = 0.0;
                for (java.util.Map.Entry<String, Integer> e : scheme.entrySet()) {
                    String comp = e.getKey();
                    int pct = e.getValue();
                    double avg = 0.0;
                    try { avg = assessmentDAO.getStudentAverageForType(sectionId, currentStudent.getId(), comp); } catch (Exception ignored) {}
                    double weighted = avg * (pct / 100.0);
                    totalWeighted += weighted;
                    sb.append(String.format("  %s: %d%%  —  Avg: %.2f  —  Weighted: %.2f\n", comp, pct, avg, weighted));
                }
                sb.append(String.format("  Computed weighted total: %.2f\n\n", totalWeighted));
            }

            JTextArea ta = new JTextArea(sb.toString());
            ta.setEditable(false);
            ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
            JScrollPane sp = new JScrollPane(ta);
            sp.setPreferredSize(new Dimension(700, Math.min(500, regs.size() * 140)));
            JOptionPane.showMessageDialog(this, sp, "Grading Scheme & Student Averages", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load grading scheme: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =================================================================
    //                            PROFILE PANEL
    // =================================================================

    private JPanel createProfilePanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("👤 My Profile & Academic Details");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_GRAY, 1),
                new EmptyBorder(30, 30, 30, 30)
        ));

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 15, 15));
        formPanel.setBackground(CARD_BG);

        // Displaying real data from currentStudent object
        formPanel.add(new JLabel("Student ID:"));
        formPanel.add(new JTextField(currentStudent.getId()));
        formPanel.add(new JLabel("Name:"));
        formPanel.add(new JTextField(currentStudent.getName()));
        formPanel.add(new JLabel("Email:"));
        formPanel.add(new JTextField(currentStudent.getEmail()));
        formPanel.add(new JLabel("Section:"));
        formPanel.add(new JTextField(currentStudent.getSection() != null ? currentStudent.getSection() : "N/A"));
        formPanel.add(new JLabel("Degree:"));
        formPanel.add(new JTextField(currentStudent.getDegree() != null ? currentStudent.getDegree() : "N/A"));
        formPanel.add(new JLabel("Branch:"));
        formPanel.add(new JTextField(currentStudent.getBranch() != null ? currentStudent.getBranch() : "N/A"));
        formPanel.add(new JLabel("Year of Study:"));
        formPanel.add(new JTextField(currentStudent.getYearOfStudy() != null ? currentStudent.getYearOfStudy() : "N/A"));
        formPanel.add(new JLabel("Admission Year:"));
        formPanel.add(new JTextField(currentStudent.getAdmissionYear() != null ? currentStudent.getAdmissionYear() : "N/A"));
        formPanel.add(new JLabel("Current Status:"));
        formPanel.add(new JTextField(currentStudent.getStatus()));

        // Make all fields read-only for a profile view
        for(Component comp : formPanel.getComponents()) {
            if (comp instanceof JTextField) {
                JTextField tf = (JTextField) comp;
                tf.setEditable(false);
                tf.setBackground(new Color(245, 245, 245));
                tf.setBorder(BorderFactory.createLineBorder(BORDER_GRAY));
            }
        }

        contentCard.add(formPanel, BorderLayout.NORTH);

        mainPanel.add(contentCard, BorderLayout.CENTER);
        return mainPanel;
    }


    // =================================================================
    //                       REGISTRATION PANEL
    // =================================================================

        private JPanel createCourseRegistrationPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("📚 Available Courses - Offered by Admin");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_GRAY, 1),
                new EmptyBorder(20, 20, 20, 20)
        ));

        // --- Table Setup ---
        // Keep SectionID as hidden column (index 0), then show course fields
        String[] columns = {"SectionID", "Code", "Course", "Credits", "Instructor", "Capacity", "Status", "Action"};
        registrationTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Only the 'Action' column (last index 7) is editable — only when action is "Register"
                if (column == 7) {
                    Object actionObj = getValueAt(row, 7);
                    String action = actionObj == null ? "" : actionObj.toString();
                    return "Register".equalsIgnoreCase(action);
                }
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                // Keep last column as a button class for renderer/editor convenience
                return columnIndex == 7 ? JButton.class : String.class;
            }
        };

        registrationTable = new JTable(registrationTableModel);
        registrationTable.setRowHeight(35);
        registrationTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
        registrationTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 15));

        // Hide the SectionID column (column 0)
        registrationTable.getColumnModel().getColumn(0).setMaxWidth(0);
        registrationTable.getColumnModel().getColumn(0).setMinWidth(0);
        registrationTable.getColumnModel().getColumn(0).setPreferredWidth(0);

        // --- Button Renderer/Editor ---
        ButtonRenderer buttonRenderer = new ButtonRenderer();
        registrationTable.getColumn("Action").setCellRenderer(buttonRenderer);
        // Pass the handleRegistrationAction method reference to the editor
        registrationTable.getColumn("Action").setCellEditor(new ButtonEditor(new JTextField(), this::handleRegistrationAction));

        // Set column widths (Course at index 2, Instructor at 4, Action at 7)
        registrationTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        registrationTable.getColumnModel().getColumn(4).setPreferredWidth(140);
        registrationTable.getColumnModel().getColumn(7).setPreferredWidth(90);

        contentCard.add(new JScrollPane(registrationTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(CARD_BG);
        JButton refreshButton = new JButton("Refresh Catalog");
        refreshButton.setBackground(ACCENT_BLUE);
        refreshButton.setForeground(LIGHT_TEXT);
        refreshButton.addActionListener(e -> loadCourseCatalog());
        bottomPanel.add(refreshButton);
        contentCard.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.add(contentCard, BorderLayout.CENTER);
        return mainPanel;
    }


    private void loadCourseCatalog() {
    SwingUtilities.invokeLater(() -> {
        registrationTableModel.setRowCount(0); // Clear existing data
        List<domain.Course> catalog = studentService.getCourseCatalogForStudent(currentStudent.getId());

        if (catalog == null || catalog.isEmpty()) {
            return;
        }

        // Track already-enrolled course codes to hide them
        Set<String> enrolledCourseCodes = new HashSet<>();
        try {
            List<String[]> gradeHistory = studentService.getStudentGradeHistory(currentStudent.getId());
            if (gradeHistory != null) {
                for (String[] grade : gradeHistory) {
                    if (grade.length > 0 && grade[0] != null) enrolledCourseCodes.add(grade[0]);
                }
            }
        } catch (Exception ex) {
            System.err.println("Warning: could not get grade history: " + ex.getMessage());
        }

        // Dedupe by section id for actual sections, or by course code for offered courses (sectionId = -1)
        Set<String> addedSectionIds = new HashSet<>();
        Set<String> addedOfferedCourseCodes = new HashSet<>();

        for (domain.Course course : catalog) {
            if (course == null) continue;

            String sectionIdStr = course.getSectionId() == null ? "" : course.getSectionId().trim();
            String code = course.getCourseCode() == null ? "" : course.getCourseCode().trim();
            if (code.isEmpty()) continue;

            // Handle offered courses (sectionId = -1) differently from regular sections
            if ("-1".equals(sectionIdStr)) {
                // For offered courses, dedupe by course code
                if (addedOfferedCourseCodes.contains(code)) continue;
                addedOfferedCourseCodes.add(code);
            } else {
                // For regular sections, dedupe on section id to allow multiple sections per course code
                if (addedSectionIds.contains(sectionIdStr)) continue;
                addedSectionIds.add(sectionIdStr);
            }

            // skip if student already enrolled in this course
            if (enrolledCourseCodes.contains(code)) continue;

            String title = course.getTitle() == null ? "" : course.getTitle();
            int credits = course.getCredits();
            // Use the instructor name if present; otherwise show fallback message
            String instructor = course.getInstructorName();
            if (instructor == null || instructor.trim().isEmpty()) {
                instructor = "Instructor not assigned yet";
            }

            // Use the actual capacity from Course.getCapacity(); if missing or <=0, fall back to 60
            int capacity = course.getCapacity();
            if (capacity <= 0) capacity = 60; // sensible default matching your expectation

            int enrolled = course.getEnrolled();
            String capacityLabel = enrolled + " / " + capacity;

            String status = course.getStatus() == null ? "Open" : course.getStatus();
            String actionLabel;
            if ("Registered".equalsIgnoreCase(status)) {
                actionLabel = "Registered";
            } else if (capacity > 0 && enrolled >= capacity) {
                actionLabel = "Full";
            } else {
                actionLabel = "Register";
            }

            // Add row: hidden SectionID (col 0), then Code, Course (title), Credits, Instructor, Capacity, Status, Action
            registrationTableModel.addRow(new Object[] {
                course.getSectionId(), // column 0 (hidden)
                code,                   // column 1
                title,                  // column 2 (shown as "Course")
                credits,                // column 3
                instructor,             // column 4
                capacityLabel,          // column 5
                status,                 // column 6
                actionLabel             // column 7
            });
        }

        // If table empty after filtering, nothing else to do
        if (registrationTableModel.getRowCount() == 0) return;

        // Renderer: offered rows green, full rows light red
        registrationTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Object actionObj = table.getModel().getValueAt(row, 7);
                String action = actionObj == null ? "" : actionObj.toString();
                if ("Full".equalsIgnoreCase(action)) {
                    c.setBackground(new Color(250, 235, 235));
                } else {
                    c.setBackground(new Color(230, 255, 230));
                }
                return c;
            }
        });

        registrationTable.repaint();
    });
}


    /**
     * Delegator to access values from the registration table model.
     * This resolves calls like getValueAt(row, col) made from within this
     * class (e.g. in `isCellEditable`) by forwarding to
     * `registrationTableModel.getValueAt(...)` with defensive checks.
     */
    public Object getValueAt(int row, int column) {
        if (registrationTableModel == null) return null;
        if (row < 0 || column < 0) return null;
        if (row >= registrationTableModel.getRowCount()) return null;
        if (column >= registrationTableModel.getColumnCount()) return null;
        return registrationTableModel.getValueAt(row, column);
    }
    public boolean isCellEditable(int row, int column) {
        if (column == 8) { // Action column
            Object actionObj = getValueAt(row, 8);
            String action = actionObj == null ? "" : actionObj.toString();
            return "Register".equalsIgnoreCase(action);
        }
        return false;
    }


    private void handleRegistrationAction(int row) {
        String sectionId = (String) registrationTableModel.getValueAt(row, 0);  // Hidden column
        String courseCode = (String) registrationTableModel.getValueAt(row, 1);
        String action = (String) registrationTableModel.getValueAt(row, 7);  // Action column
        String result;
        String studentId = currentStudent.getId();

        if (action.equals("Register")) {
            result = studentService.registerCourse(studentId, sectionId);

            if (result.equals("SUCCESS")) {
                JOptionPane.showMessageDialog(this, "✅ Successfully registered for " + courseCode + ".", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadCourseCatalog(); // Refresh to show updated course list
            } else if (result.equals("Section full.")) {
                JOptionPane.showMessageDialog(this, "❌ Registration failed: Section is full.", "Error", JOptionPane.ERROR_MESSAGE);
            } else if (result.equals("Already registered in this section.")) {
                JOptionPane.showMessageDialog(this, "⚠️ Registration failed: You are already registered in this course.", "Error", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "❌ Registration failed: " + result, "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =================================================================
    //                            GRADES PANEL
    // =================================================================

    private JPanel createGradesPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("💯 My Academic History & Grades");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_GRAY, 1),
                new EmptyBorder(20, 20, 20, 20)
        ));

        // Grades Table
        String[] columns = {"Code", "Title", "Credits", "Grade", "GPA Points"};
        gradesTableModel = new DefaultTableModel(columns, 0);
        JTable table = new JTable(gradesTableModel);
        table.setRowHeight(35);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 15));
        
        // Set column widths for better visibility
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        
        contentCard.add(new JScrollPane(table), BorderLayout.CENTER);

        // Summary Footer
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        summaryPanel.setBackground(CARD_BG);

        JLabel cgpaLabel = new JLabel("Overall CGPA: " + String.format("%.2f", currentStudent.getCurrentCGPA()));
        cgpaLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        summaryPanel.add(cgpaLabel);

        JButton downloadButton = new JButton("Download Transcript (CSV)");
        downloadButton.setBackground(ACCENT_BLUE);
        downloadButton.setForeground(Color.WHITE);
        downloadButton.addActionListener(e -> downloadTranscript());
        summaryPanel.add(Box.createHorizontalStrut(20));
        summaryPanel.add(downloadButton);

        contentCard.add(summaryPanel, BorderLayout.SOUTH);

        mainPanel.add(contentCard, BorderLayout.CENTER);
        return mainPanel;
    }

    private void loadStudentGrades() {
        SwingUtilities.invokeLater(() -> {
            gradesTableModel.setRowCount(0); // Clear existing data
            List<String[]> grades = studentService.getStudentGradeHistory(currentStudent.getId());

            for (String[] row : grades) {
                gradesTableModel.addRow(row);
            }
        });
    }

    private void downloadTranscript() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Specify file to save");
        fileChooser.setSelectedFile(new File(currentStudent.getId() + "_transcript.csv"));

        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(fileToSave))) {
                // Write column headers
                for (int i = 0; i < gradesTableModel.getColumnCount(); i++) {
                    pw.print(gradesTableModel.getColumnName(i) + (i == gradesTableModel.getColumnCount() - 1 ? "" : ","));
                }
                pw.println();

                // Write data rows
                for (int i = 0; i < gradesTableModel.getRowCount(); i++) {
                    for (int j = 0; j < gradesTableModel.getColumnCount(); j++) {
                        pw.print(gradesTableModel.getValueAt(i, j) + (j == gradesTableModel.getColumnCount() - 1 ? "" : ","));
                    }
                    pw.println();
                }
                JOptionPane.showMessageDialog(this, "Transcript successfully downloaded to:\n" + fileToSave.getAbsolutePath(), "Download Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Download Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    // =================================================================
    //                            FEES PANEL
    // =================================================================

    private JPanel createFeeDetailsPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("💳 Fee Payment History & Status");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(HEADER_TEXT);
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel contentCard = new JPanel(new BorderLayout(15, 15));
        contentCard.setBackground(CARD_BG);
        contentCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_GRAY, 1),
                new EmptyBorder(20, 20, 20, 20)
        ));

        int feesAmount = currentStudent.getFeesDue();

        // Fee Summary Box
        JPanel summary = new JPanel(new FlowLayout(FlowLayout.CENTER, 40, 0));
        summary.setBackground(CARD_BG);
        summary.add(createDetailBox("Fees Due", "₹" + feesAmount, feesAmount > 0 ? LOGOUT_RED : ACCENT_GREEN, "💳"));
        summary.add(createDetailBox("Total Paid (Current Year)", "₹" + studentService.getFeesPaid(currentStudent.getId()), ACCENT_BLUE, "💰"));
        contentCard.add(summary, BorderLayout.NORTH);

        // Transaction History Table (Placeholder Data)
        String[] columns = {"Transaction ID", "Semester", "Amount Paid", "Date", "Status"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        model.addRow(new Object[]{"T2025091", "Fall 2025", "₹1,50,000", "2025-08-15", "Completed"});
        model.addRow(new Object[]{"T2025034", "Spring 2025", "₹1,50,000", "2025-01-20", "Completed"});

        JTable table = new JTable(model);
        contentCard.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(CARD_BG);
        JButton payButton = new JButton("Pay Fees Now");
        payButton.setBackground(ACCENT_GREEN);
        payButton.setForeground(Color.WHITE);
        payButton.addActionListener(e -> JOptionPane.showMessageDialog(this, "Payment gateway not yet implemented.", "TODO", JOptionPane.INFORMATION_MESSAGE));
        bottomPanel.add(payButton);
        contentCard.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.add(contentCard, BorderLayout.CENTER);
        return mainPanel;
    }

    // ----------------- Offered poller helpers -----------------
    private void startOfferedPoller() {
        // Initialize last known value
        lastOfferedAt = fetchLastOfferedAt();
        offeredPollTimer = new javax.swing.Timer(5000, e -> {
            String now = fetchLastOfferedAt();
            if (now != null && !now.equals(lastOfferedAt)) {
                lastOfferedAt = now;
                loadCourseCatalog();
            }
        });
        offeredPollTimer.setRepeats(true);
        offeredPollTimer.start();
    }

    private String fetchLastOfferedAt() {
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
             java.sql.PreparedStatement p = c.prepareStatement("SELECT value FROM settings WHERE key_name = 'last_offered_at' LIMIT 1")) {
            try (java.sql.ResultSet r = p.executeQuery()) {
                if (r.next()) return r.getString(1);
            }
        } catch (java.sql.SQLException ex) {
            // ignore
        }
        return null;
    }

    @Override
    public void dispose() {
        if (offeredPollTimer != null && offeredPollTimer.isRunning()) offeredPollTimer.stop();
        super.dispose();
    }


    // =================================================================
    //                INNER CLASSES for Button Table Logic
    // =================================================================

    /** Renders the button in the JTable cell, setting color based on the action text. */
    private class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
            setFont(new Font("SansSerif", Font.BOLD, 12));
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {

            String status = (String) table.getValueAt(row, 6);  // Status column is now at index 6

            if (value != null) {
                setText(value.toString());

                if (status.equals("Full")) {
                    // Gray out if section is full
                    setBackground(Color.LIGHT_GRAY);
                    setForeground(Color.DARK_GRAY);
                } else if (value.toString().equals("Register")) {
                    setBackground(ACCENT_GREEN);
                    setForeground(Color.WHITE);
                } else if (value.toString().equals("Drop")) {
                    setBackground(LOGOUT_RED);
                    setForeground(Color.WHITE);
                } else {
                    setBackground(Color.LIGHT_GRAY);
                    setForeground(Color.BLACK);
                }
            }
            return this;
        }
    }

    /** Handles button clicks inside the JTable cell. */
    private class ButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private String label;
        private boolean isPushed;

        public ButtonEditor(JTextField textField, java.util.function.Consumer<Integer> clickHandler) {
            super(textField);
            button = new JButton();
            button.setOpaque(true);
            button.setFont(new Font("SansSerif", Font.BOLD, 12));

            button.addActionListener(e -> fireEditingStopped());

            delegate = new EditorDelegate() {
                @Override
                public void setValue(Object value) {
                    label = (value != null) ? value.toString() : "";
                    button.setText(label);
                }

                @Override
                public Object getCellEditorValue() {
                    return label;
                }
            };
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            label = (value == null) ? "" : value.toString();
            button.setText(label);
            isPushed = true;

            // Set colors dynamically based on the label, same as renderer (simplified)
            if (label.equals("Register")) {
                button.setBackground(ACCENT_GREEN);
                button.setForeground(Color.WHITE);
            } else if (label.equals("Drop")) {
                button.setBackground(LOGOUT_RED);
                button.setForeground(Color.WHITE);
            } else {
                button.setBackground(Color.LIGHT_GRAY);
                button.setForeground(Color.DARK_GRAY);
            }

            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (isPushed) {
                // Execute the handler BEFORE returning the value
                int selectedRow = registrationTable.getSelectedRow();
                if (selectedRow != -1) {
                    handleRegistrationAction(selectedRow);
                }
            }
            isPushed = false;
            return label; // Return the button label text
        }
    }
}
