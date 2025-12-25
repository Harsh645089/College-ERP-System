package instructor;

import domain.UserSession;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.JPasswordField;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.DefaultTableCellRenderer;
import login.LoginWindow;
import login.DatabaseConfig;
import auth.DBAuthService;
import instructor.dao.SectionDAO;
import instructor.dao.GradeDAO;
import instructor.dao.GradingSchemeDAO;
import instructor.dao.AssessmentDAO;
import types.SectionRow;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractCellEditor;
import javax.swing.table.TableCellEditor;

public class InstructorDashboard extends JFrame {
   private final UserSession session;
   private JLabel statusLabel;
   private JPanel mainContentPanel;
   private CardLayout mainContentLayout;
   private JPanel cardsPanel;
   private MySectionsPanel mySectionsPanel;

   // DAOs
   private final SectionDAO sectionDAO = new SectionDAO();
   private final GradeDAO gradeDAO = new GradeDAO();
   private final GradingSchemeDAO gradingSchemeDAO = new GradingSchemeDAO();
   private final AssessmentDAO assessmentDAO = new AssessmentDAO();
   private final admin.dao.InstructorDAO instrDAO = new admin.dao.InstructorDAO();

   // UI Constants with increased font sizes
   private static final Color COLOR_BG = new Color(230, 231, 233);
   private static final Color COLOR_SIDEBAR = new Color(34, 35, 36);
   private static final Color COLOR_CARD_BG = new Color(248, 249, 250);
   private static final Font FONT_HEADER = new Font("SansSerif", Font.BOLD, 20); // Increased from 18
   private static final Font FONT_NAV = new Font("SansSerif", Font.PLAIN, 16); // Increased from 14
   private static final Font FONT_TABLE = new Font("SansSerif", Font.PLAIN, 15); // New for tables
   private static final Font FONT_BUTTON = new Font("SansSerif", Font.BOLD, 15); // New for buttons

   // Define class-level variables for coursesModel and coursesTable
   private DefaultTableModel coursesModel;
   private JTable coursesTable;

   public InstructorDashboard(UserSession var1) {
      super("Instructor Dashboard | University ERP");
      this.session = var1;
      this.setDefaultCloseOperation(3);
      this.setLayout(new BorderLayout());
      JPanel var2 = this.createSidebar();
      this.add(var2, "West");
      this.mainContentLayout = new CardLayout();
      this.cardsPanel = new JPanel(this.mainContentLayout);
      this.cardsPanel.setBackground(COLOR_BG);
      this.statusLabel = new JLabel(" ");
      this.statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
      this.statusLabel.setForeground(new Color(55, 55, 55));
      this.statusLabel.setFont(FONT_NAV); // Apply increased font
      JPanel var3 = this.createDashboardPanel();
      this.cardsPanel.add(var3, "DASHBOARD");
      this.cardsPanel.add(createProfilePanel(), "PROFILE");
      this.mySectionsPanel = new MySectionsPanel(var1, this.statusLabel);
      this.cardsPanel.add(this.mySectionsPanel, "GRADE_MANAGEMENT");
      
      // Add new panels from template
      this.cardsPanel.add(makeCoursesPanel(), "COURSES"); // Changed from SECTIONS to COURSES
      this.cardsPanel.add(makeScoresPanel(), "SCORES");
      this.cardsPanel.add(makeStatsPanel(), "STATS");
      this.cardsPanel.add(makeGradingSchemePanel(), "GRADING_SCHEME"); // New panel
      
      this.mainContentPanel = new JPanel(new BorderLayout());
      this.mainContentPanel.add(this.cardsPanel, "Center");
      this.mainContentPanel.add(this.statusLabel, "South");
      this.add(this.mainContentPanel, "Center");
      this.setExtendedState(6);
      this.setVisible(true);
   }

   private JPanel createDashboardPanel() {
      JPanel var1 = new JPanel(new BorderLayout());
      JPanel var2 = new JPanel(new BorderLayout());
         var2.setBackground(new Color(44, 45, 48));
         JLabel dateLabel = new JLabel(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) + "     " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), SwingConstants.RIGHT);
         dateLabel.setFont(new Font("SansSerif", Font.PLAIN, 14)); // Increased font
         dateLabel.setForeground(new Color(180, 180, 180));
         var2.add(dateLabel, BorderLayout.EAST);
      var1.add(var2, "North");
      JPanel var5 = new JPanel(new BorderLayout());
      var5.setBackground(COLOR_BG);
      // Welcome message and summary boxes - compute live from DB
      JLabel welcome = new JLabel("Welcome back, " + this.session.getUsername() + "! Your teaching summary is below.");
      welcome.setFont(new Font("SansSerif", Font.PLAIN, 17)); // Increased font
      welcome.setForeground(new Color(44, 45, 48));
      welcome.setBorder(BorderFactory.createEmptyBorder(20, 30, 0, 0));
      var5.add(welcome, BorderLayout.NORTH);

      // Summary boxes - compute live from DB
        int activeCourses = 0;
        int totalStudents = 0;
        double avgFinal = 0.0;
        try {
           String mappedIns = mappedInstructorId();
           java.util.List<SectionRow> mySections = sectionDAO.getSectionsForInstructor(mappedIns == null ? String.valueOf(session.getUserId()) : mappedIns, "Fall", 2025);
           activeCourses = mySections.size();
           // total students across all sections taught by this instructor
           for (SectionRow sr : mySections) {
              totalStudents += countStudentsForCourse(sr.courseCode);
           }
           // compute average final grade across these sections
           double sumFinal = 0.0;
           int finalCount = 0;
           for (SectionRow sr : mySections) {
              java.util.List<java.util.Map<String, Object>> scores = gradeDAO.getScoresForSection(sr.sectionId);
              for (java.util.Map<String, Object> row : scores) {
                 Object v = row.get("final");
                 if (v instanceof Number) {
                    sumFinal += ((Number) v).doubleValue();
                    finalCount++;
                 }
              }
           }
           if (finalCount > 0) avgFinal = sumFinal / finalCount;
        } catch (Exception ex) {
           statusLabel.setText("Error computing summaries: " + ex.getMessage());
        }

      // Summary boxes
      JPanel summaryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
      summaryRow.setBackground(COLOR_BG);
      summaryRow.add(createSummaryCard("Active Courses", String.valueOf(activeCourses), new Color(27, 116, 228), true));
      summaryRow.add(createSummaryCard("Total Students", String.valueOf(totalStudents), new Color(46, 204, 113), true));
      summaryRow.add(createSummaryCard("Avg. Course Final", String.format("%.2f", avgFinal), new Color(241, 196, 15), true));
      var5.add(summaryRow, BorderLayout.CENTER);
      JPanel var8 = new JPanel(new GridLayout(2, 4, 20, 20));
      var8.setBackground(COLOR_BG);
      var8.setBorder(BorderFactory.createEmptyBorder(10, 30, 30, 30));
      var8.add(this.createQuickLinkCard("Profile", "𓀀", "", () -> {
         this.showProfilePanel();
      }));
      var8.add(this.createQuickLinkCard("My Courses", "🎓", "", () -> {
         this.showCoursesPanel();
      }));
      var8.add(this.createQuickLinkCard("Enter Scores", "📊", "", () -> {
         this.showScoresPanel();
      }));
      var8.add(this.createQuickLinkCard("Class Stats", "📈", "", () -> {
         this.showStatsPanel();
      }));
      // Additional quick links to mirror admin layout
      var8.add(this.createQuickLinkCard("Grading Scheme", "⚖️", "", () -> { this.showGradingSchemePanel(); }));
      var8.add(this.createQuickLinkCard("Change Password", "🗝", "", () -> { this.showChangePassword(); }));
      JPanel var9 = new JPanel(new BorderLayout());
      var9.setBackground(COLOR_BG);
      JLabel var10 = new JLabel("INSTRUCTOR QUICK LINKS");
      var10.setFont(new Font("SansSerif", Font.BOLD, 16)); // Increased font
      var10.setForeground(new Color(44, 45, 48));
      var9.add(var10, "North");

      // Wrap quick links in a bordered container to match admin UI
      JPanel quickWrap = new JPanel(new BorderLayout());
      quickWrap.setBackground(COLOR_BG);
      quickWrap.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(210,210,210)), BorderFactory.createEmptyBorder(12,12,12,12)));
      quickWrap.add(var8, BorderLayout.CENTER);

      var9.add(quickWrap, "Center");
      var9.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
      JPanel var11 = new JPanel();
      var11.setLayout(new BoxLayout(var11, 1));
      var11.setBackground(COLOR_BG);
      var11.add(var5);
      var11.add(var9);
      var1.add(var11, "Center");
      return var1;
   }

      // Map session username to the seeded instructor id using the firstName+id pattern
      private String mappedInstructorId() {
         if (session == null || session.getUsername() == null) return null;
         String uname = session.getUsername().toLowerCase();
         
         // First, try to find in user_person_map table
         try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
              PreparedStatement p = c.prepareStatement("SELECT person_id FROM user_person_map WHERE username = ? AND person_type = 'instructor' LIMIT 1")) {
            p.setString(1, uname);
            try (ResultSet rs = p.executeQuery()) {
               if (rs.next()) return rs.getString("person_id");
            }
         } catch (Exception ignored) {}
         
         // Fallback: Try name + id match
         try {
            for (domain.Instructor ins : instrDAO.listAll()) {
               String first = (ins.getName() == null || ins.getName().isEmpty()) ? "" : ins.getName().split(" ")[0];
               String candidate = (first + ins.getId()).toLowerCase();
               if (candidate.equalsIgnoreCase(uname)) return ins.getId();
            }
         } catch (Exception ignored) {}
         return null;
      }

   private JPanel createProfilePanel() {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBackground(COLOR_BG);
      panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

      try {
         String instructorId = mappedInstructorId();
         if (instructorId == null) {
            JLabel errorLabel = new JLabel("Could not load instructor profile");
            errorLabel.setFont(FONT_TABLE);
            errorLabel.setForeground(new Color(100, 100, 100));
            panel.add(errorLabel, BorderLayout.CENTER);
            return panel;
         }

         // Load instructor details
         domain.Instructor instructor = instrDAO.getById(instructorId);
         if (instructor == null) {
            JLabel errorLabel = new JLabel("Instructor profile not found");
            errorLabel.setFont(FONT_TABLE);
            errorLabel.setForeground(new Color(100, 100, 100));
            panel.add(errorLabel, BorderLayout.CENTER);
            return panel;
         }

         // Create title
         JLabel profileTitle = new JLabel("Instructor Profile");
         profileTitle.setFont(new Font("SansSerif", Font.BOLD, 28));
         profileTitle.setForeground(new Color(44, 45, 48));
         panel.add(profileTitle, BorderLayout.NORTH);

         // Create profile card with table-like layout
         JPanel cardPanel = new JPanel(new BorderLayout(15, 15));
         cardPanel.setBackground(COLOR_CARD_BG);
         cardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(30, 30, 30, 30)
         ));

         // Create grid for displaying fields
         JPanel fieldsPanel = new JPanel(new GridBagLayout());
         fieldsPanel.setBackground(COLOR_CARD_BG);
         GridBagConstraints gbc = new GridBagConstraints();
         gbc.insets = new Insets(12, 0, 12, 20);
         gbc.anchor = GridBagConstraints.WEST;

         // Helper method to add field rows
         int rowNum = 0;

         // Name
         gbc.gridx = 0; gbc.gridy = rowNum; gbc.weightx = 0.2;
         JLabel nameLabel = new JLabel("Name:");
         nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
         nameLabel.setForeground(new Color(100, 100, 100));
         fieldsPanel.add(nameLabel, gbc);

         gbc.gridx = 1; gbc.weightx = 0.8; gbc.fill = GridBagConstraints.HORIZONTAL;
         JLabel nameValue = new JLabel(instructor.getName() != null ? instructor.getName() : "N/A");
         nameValue.setFont(new Font("SansSerif", Font.PLAIN, 13));
         nameValue.setForeground(new Color(60, 60, 60));
         fieldsPanel.add(nameValue, gbc);

         // ID
         rowNum++;
         gbc.gridx = 0; gbc.gridy = rowNum; gbc.weightx = 0.2; gbc.fill = GridBagConstraints.NONE;
         JLabel idLabel = new JLabel("ID:");
         idLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
         idLabel.setForeground(new Color(100, 100, 100));
         fieldsPanel.add(idLabel, gbc);

         gbc.gridx = 1; gbc.weightx = 0.8; gbc.fill = GridBagConstraints.HORIZONTAL;
         JLabel idValue = new JLabel(instructor.getId() != null ? instructor.getId() : "N/A");
         idValue.setFont(new Font("SansSerif", Font.PLAIN, 13));
         idValue.setForeground(new Color(60, 60, 60));
         fieldsPanel.add(idValue, gbc);

         // Department
         rowNum++;
         gbc.gridx = 0; gbc.gridy = rowNum; gbc.weightx = 0.2; gbc.fill = GridBagConstraints.NONE;
         JLabel deptLabel = new JLabel("Department:");
         deptLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
         deptLabel.setForeground(new Color(100, 100, 100));
         fieldsPanel.add(deptLabel, gbc);

         gbc.gridx = 1; gbc.weightx = 0.8; gbc.fill = GridBagConstraints.HORIZONTAL;
         JLabel deptValue = new JLabel(instructor.getDepartment() != null ? instructor.getDepartment() : "Not specified");
         deptValue.setFont(new Font("SansSerif", Font.PLAIN, 13));
         deptValue.setForeground(new Color(60, 60, 60));
         fieldsPanel.add(deptValue, gbc);

         // Email
         rowNum++;
         gbc.gridx = 0; gbc.gridy = rowNum; gbc.weightx = 0.2; gbc.fill = GridBagConstraints.NONE;
         JLabel emailLabel = new JLabel("Email:");
         emailLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
         emailLabel.setForeground(new Color(100, 100, 100));
         fieldsPanel.add(emailLabel, gbc);

         gbc.gridx = 1; gbc.weightx = 0.8; gbc.fill = GridBagConstraints.HORIZONTAL;
         String email = instructor.getEmail() != null ? instructor.getEmail() : "Not specified";
         JLabel emailValue = new JLabel(email);
         emailValue.setFont(new Font("SansSerif", Font.PLAIN, 13));
         emailValue.setForeground(new Color(60, 60, 60));
         fieldsPanel.add(emailValue, gbc);

         cardPanel.add(fieldsPanel, BorderLayout.CENTER);

         // Scroll and wrap
         JScrollPane scrollPane = new JScrollPane(cardPanel);
         scrollPane.setBackground(COLOR_BG);
         scrollPane.setBorder(null);
         scrollPane.getVerticalScrollBar().setUnitIncrement(16);
         panel.add(scrollPane, BorderLayout.CENTER);

      } catch (Exception e) {
         e.printStackTrace();
         JLabel errorLabel = new JLabel("Error loading profile: " + e.getMessage());
         errorLabel.setFont(FONT_TABLE);
         errorLabel.setForeground(Color.RED);
         panel.add(errorLabel, BorderLayout.CENTER);
      }

      return panel;
   }

   private void showProfilePanel() {
      this.statusLabel.setText("Viewing Instructor Profile");
      this.mainContentLayout.show(this.cardsPanel, "PROFILE");
   }

   private void showCoursesPanel() {
      this.statusLabel.setText("Navigating to My Courses");
      this.mainContentLayout.show(this.cardsPanel, "COURSES");
   }

   private void showScoresPanel() {
      this.statusLabel.setText("Navigating to Enter Scores");
      this.mainContentLayout.show(this.cardsPanel, "SCORES");
   }



   private void showStatsPanel() {
      this.statusLabel.setText("Navigating to Class Statistics");
      this.mainContentLayout.show(this.cardsPanel, "STATS");
   }

   private void showGradingSchemePanel() {
      this.statusLabel.setText("Navigating to Grading Scheme");
      this.mainContentLayout.show(this.cardsPanel, "GRADING_SCHEME");
   }

   private void showGradesPanel() {
      this.statusLabel.setText(" ");
      this.mainContentLayout.show(this.cardsPanel, "GRADE_MANAGEMENT");
   }

   private JButton createSidebarButton(String var1, boolean var2) {
      JButton var3 = new JButton(var1);
      var3.setFont(FONT_BUTTON); // Use increased font
      var3.setForeground(Color.WHITE);
      var3.setBackground(var2 ? new Color(60, 60, 62) : COLOR_SIDEBAR);
      var3.setFocusPainted(false);
      var3.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
      var3.setHorizontalAlignment(2);
      var3.setMaximumSize(new Dimension(220, 45));
      var3.setMinimumSize(new Dimension(220, 45));
      var3.setCursor(new Cursor(Cursor.HAND_CURSOR));
      return var3;
   }

   private JPanel createSidebar() {
      JPanel var1 = new JPanel();
      var1.setBackground(COLOR_SIDEBAR);
      var1.setLayout(new BoxLayout(var1, 1));
      var1.setPreferredSize(new Dimension(220, 0));
      JLabel var2 = new JLabel("Instructor Portal");
      var2.setFont(new Font("SansSerif", Font.BOLD, 20)); // Increased font
      var2.setForeground(Color.WHITE);
      var2.setBorder(BorderFactory.createEmptyBorder(30, 20, 0, 20));
      var2.setAlignmentX(0.0F);
      var1.add(var2);
      JLabel var3 = new JLabel("Welcome, " + this.session.getUsername());
      var3.setFont(new Font("SansSerif", Font.PLAIN, 15)); // Increased font
      var3.setForeground(new Color(200, 200, 200));
      var3.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
      var3.setAlignmentX(0.0F);
      var1.add(var3);
      JButton var4 = this.createSidebarButton("⌂ Dashboard", true);
      var4.addActionListener((var1x) -> {
         this.statusLabel.setText(" ");
         this.mainContentLayout.show(this.cardsPanel, "DASHBOARD");
      });
      var1.add(var4);
      JButton varProfile = this.createSidebarButton("𓀀 Profile", false);
      varProfile.addActionListener((var1x) -> {
         this.showProfilePanel();
      });
      var1.add(varProfile);
      JButton var5 = this.createSidebarButton("🎓 My Courses", false);
      var5.addActionListener((var1x) -> {
         this.showCoursesPanel();
      });
      var1.add(var5);
      JButton var6 = this.createSidebarButton("📊 Enter Scores", false);
      var6.addActionListener((var1x) -> {
         this.showScoresPanel();
      });
      var1.add(var6);
      JButton var8 = this.createSidebarButton("📈 Class Stats", false);
      var8.addActionListener((var1x) -> {
         this.showStatsPanel();
      });
      var1.add(var8);
      JButton var9 = this.createSidebarButton("⚖️ Grading Scheme", false);
      var9.addActionListener((var1x) -> {
         this.showGradingSchemePanel();
      });
      var1.add(var9);
      JButton var10 = this.createSidebarButton("🗝 Change Password", false);
      var10.addActionListener((var1x) -> {
         this.showChangePassword();
      });
      var1.add(var10);
      var1.add(Box.createVerticalGlue());
      var1.add(this.createSidebarLogoutButton());
      return var1;
   }

   private JButton createSidebarLogoutButton() {
      JButton var1 = new JButton("🚪 Logout");
      var1.setFont(FONT_BUTTON); // Use increased font
      var1.setForeground(Color.WHITE);
      var1.setBackground(new Color(176, 44, 54));
      var1.setFocusPainted(false);
      var1.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
      var1.setHorizontalAlignment(2);
      var1.setMaximumSize(new Dimension(220, 40));
      var1.setMinimumSize(new Dimension(220, 40));
      var1.setCursor(new Cursor(Cursor.HAND_CURSOR));
      var1.addActionListener((var1x) -> {
         this.logout();
      });
      return var1;
   }

   private JPanel createSummaryCard(String var1, String var2, Color var3, boolean var4) {
      // Card with a colored vertical strip at left (like admin UI)
      JPanel outer = new JPanel(new BorderLayout());
      outer.setBackground(new Color(240, 241, 243));
      outer.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(210, 210, 210), 1), BorderFactory.createEmptyBorder(0, 0, 0, 0)));
      outer.setPreferredSize(var4 ? new Dimension(180, 84) : new Dimension(260, 120));

      JPanel strip = new JPanel();
      strip.setBackground(var3);
      strip.setPreferredSize(new Dimension(8, 80));
      outer.add(strip, BorderLayout.WEST);

      JPanel content = new JPanel(new BorderLayout());
      content.setBackground(new Color(240, 241, 243));
      content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

      JLabel value = new JLabel(var2);
      value.setFont(new Font("SansSerif", Font.BOLD, var4 ? 22 : 28));
      value.setForeground(new Color(40, 40, 40));
      JLabel title = new JLabel(var1);
      title.setFont(new Font("SansSerif", Font.PLAIN, 13));
      title.setForeground(new Color(80, 80, 80));

      content.add(title, BorderLayout.NORTH);
      content.add(value, BorderLayout.CENTER);

      outer.add(content, BorderLayout.CENTER);
      return outer;
   }

   private JPanel createQuickLinkCard(String var1, String var2, String var3, final Runnable var4) {
      final JPanel card = new JPanel(new BorderLayout());
      card.setBackground(new Color(245, 246, 247));
      card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(210, 210, 210), 1), BorderFactory.createEmptyBorder(20, 20, 20, 20)));
      card.setPreferredSize(new Dimension(320, 180));

      JLabel icon = new JLabel(var2, SwingConstants.CENTER);
      icon.setFont(new Font("SansSerif", Font.PLAIN, 42));
      icon.setForeground(new Color(90, 90, 90));
      card.add(icon, BorderLayout.CENTER);

      JLabel caption = new JLabel(var1, SwingConstants.CENTER);
      caption.setFont(new Font("SansSerif", Font.PLAIN, 14));
      caption.setForeground(new Color(70, 70, 70));
      card.add(caption, BorderLayout.SOUTH);

      card.setCursor(new Cursor(Cursor.HAND_CURSOR));
      card.addMouseListener(new MouseAdapter() {
         public void mouseClicked(MouseEvent e) {
            if (var4 != null) var4.run();
         }
         public void mouseEntered(MouseEvent e) { card.setBackground(new Color(235,235,236)); }
         public void mouseExited(MouseEvent e) { card.setBackground(new Color(245,246,247)); }
      });
      return card;
   }

   // --- 1. COURSES VIEW (Table with Edit Capacity) - CHANGED FROM SECTIONS TO COURSES ---
   private JPanel makeCoursesPanel() {
      JPanel card = new JPanel(new BorderLayout());
      card.setBackground(COLOR_CARD_BG);
      card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
      
      // Header with Refresh button
      JPanel topPanel = new JPanel(new BorderLayout());
      topPanel.setBackground(COLOR_CARD_BG);
      
      JLabel header = new JLabel("My Courses");
      header.setFont(FONT_HEADER);
      topPanel.add(header, BorderLayout.WEST);
      
      JButton btnRefresh = new JButton("🔄 Refresh");
      btnRefresh.setFont(FONT_BUTTON);
      btnRefresh.setBackground(new Color(0, 120, 215));
      btnRefresh.setForeground(Color.WHITE);
      
      topPanel.add(btnRefresh, BorderLayout.EAST);
      topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
      card.add(topPanel, BorderLayout.NORTH);

      String[] cols = {"Course ID", "Course", "Section", "Capacity", "Enrolled"};
      DefaultTableModel model = new DefaultTableModel(cols, 0) {
         @Override
         public boolean isCellEditable(int row, int column) {
            return false; // table is read-only (capacity edited via dialog)
         }
      };
      
      JTable table = new JTable(model);
      table.setFont(FONT_TABLE); // Apply increased font
      table.setRowHeight(35); // Increased row height
      // show vertical grid lines between columns for clearer separation
      table.setShowVerticalLines(true);
      table.setGridColor(new Color(220,220,220));
      table.setIntercellSpacing(new java.awt.Dimension(1,1));

      // Improve columns (widths, alignment, action renderer)
      setupCoursesTableColumns(table);
      
      // Define refresh action
      Runnable refreshCourses = () -> {
         model.setRowCount(0); // Clear table
         try {
            String mappedInsId = mappedInstructorId();
            String insIdToUse = (mappedInsId != null) ? mappedInsId : String.valueOf(session.getUserId());
            java.util.List<SectionRow> courses = sectionDAO.getSectionsForInstructor(insIdToUse, "Fall", 2025);
                for (SectionRow course : courses) {
                   int enrolled = getEnrolledCount(course.sectionId);
                   // The section titles in DB are stored as "Course Title - Section X"
                   // Extract the section letter from the title (e.g. "A" or "B" from "Calculus I - Section A")
                   String rawTitle = (course.courseTitle == null ? "" : course.courseTitle);
                   String sectionLetter = "A"; // default
                   java.util.regex.Pattern p = java.util.regex.Pattern.compile("Section\\s+([A-Za-z])$");
                   java.util.regex.Matcher m = p.matcher(rawTitle);
                   if (m.find()) {
                       sectionLetter = m.group(1);
                   }
                   // Strip the " - Section X" suffix to get clean course title
                   String strippedTitle = rawTitle.replaceAll("\\s*-\\s*Section\\s+[A-Za-z]$", "");
                   String courseCol = (course.courseCode == null ? "" : course.courseCode) + " - " + strippedTitle;
                   model.addRow(new Object[]{ 
                      course.getCourseId(),
                      courseCol,
                      sectionLetter,
                      String.valueOf(course.capacity),
                      String.valueOf(enrolled)
                   });
            }
            if (courses.isEmpty()) {
               statusLabel.setText("No courses found for this instructor.");
            } else {
               statusLabel.setText("Loaded " + courses.size() + " course(s).");
            }
         } catch (Exception ex) {
            statusLabel.setText("Error loading courses: " + ex.getMessage());
         }
      };
      
      // Set up Refresh button
      btnRefresh.addActionListener(e -> refreshCourses.run());
      
      // Load courses on initial panel creation
      refreshCourses.run();
      
      // Add action listener for Edit Capacity button
      table.addMouseListener(new MouseAdapter() {
         public void mouseClicked(MouseEvent e) {
            int row = table.rowAtPoint(e.getPoint());
            int col = table.columnAtPoint(e.getPoint());
                if (row >= 0) {
                   if (col == 3) { // Capacity column clicked
                      editCourseCapacity(model, row);
                   }
            }
         }
      });
      
      JScrollPane scrollPane = new JScrollPane(table);
      card.add(scrollPane, BorderLayout.CENTER);
      
      // no bottom action button for capacity (use inline actions)
      card.add(new JPanel(), BorderLayout.SOUTH);
      
      return card;
   }

   // Method to edit course capacity
   private void editCourseCapacity(DefaultTableModel model, int row) {
      try {
         int sectionId = Integer.parseInt(model.getValueAt(row, 0).toString());
         // Enforce ownership: only the mapped instructor (or the session owner) may change capacity
         if (!isOwnerOfSection(sectionId)) {
            // silently ignore edit attempts by non-owners; update status instead of showing a modal dialog
            try { statusLabel.setText("Not authorized to edit this section."); } catch (Exception ignored) {}
            return;
         }
         String courseName = model.getValueAt(row, 1).toString();
         String courseCode = courseName.split(" - ")[0].trim();
         String sectionDetails = model.getValueAt(row, 2).toString();
         int currentCapacity = Integer.parseInt(model.getValueAt(row, 3).toString());
         int enrolled = Integer.parseInt(model.getValueAt(row, 4).toString());
         
         // determine total students pool
         int totalPool = countStudentsForCourse(courseCode);
         int minAllowed = Math.max(2, enrolled); // capacity must be > 1 and at least current enrolled
         int maxAllowed;
         if (totalPool > 1) {
            // user requested "lower than the total students" => enforce strictly less than totalPool
            maxAllowed = Math.max(minAllowed, totalPool - 1);
         } else {
            maxAllowed = Math.max(minAllowed, minAllowed);
         }

         // Create edit dialog with bounds
         SpinnerNumberModel spinnerModel = new SpinnerNumberModel(currentCapacity, minAllowed, maxAllowed, 1);
         JSpinner capacitySpinner = new JSpinner(spinnerModel);
         JPanel panel = new JPanel(new GridLayout(0, 1));
         panel.add(new JLabel("Course: " + courseName));
         panel.add(new JLabel("Section: " + sectionDetails));
         panel.add(new JLabel("Currently enrolled: " + enrolled));
         panel.add(new JLabel("Total students pool: " + totalPool));
         panel.add(new JLabel("Allowed range: " + minAllowed + " - " + maxAllowed));
         panel.add(new JLabel("New Capacity:"));
         panel.add(capacitySpinner);
         
         int result = JOptionPane.showConfirmDialog(this, panel, 
             "Edit Course Capacity", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
         
         if (result == JOptionPane.OK_OPTION) {
            int newCapacity = (Integer) capacitySpinner.getValue();

            // Final validation checks
            if (newCapacity < 2) {
               JOptionPane.showMessageDialog(this, "Capacity must be greater than 1.", "Validation", JOptionPane.WARNING_MESSAGE);
               return;
            }
            if (newCapacity < enrolled) {
               JOptionPane.showMessageDialog(this, "Capacity cannot be less than current enrolled students (" + enrolled + ").", "Validation", JOptionPane.WARNING_MESSAGE);
               return;
            }
            if (totalPool > 1 && newCapacity >= totalPool) {
               JOptionPane.showMessageDialog(this, "Capacity must be lower than the total students pool (" + totalPool + ").", "Validation", JOptionPane.WARNING_MESSAGE);
               return;
            }

            // Persist the updated capacity to the database
            try {
               sectionDAO.updateCourseCapacity(sectionId, newCapacity);
               // Update the table cell on success
               model.setValueAt(String.valueOf(newCapacity), row, 3);
               statusLabel.setText("Capacity updated for " + courseName);
            } catch (Exception dbEx) {
               // fallback direct SQL
               try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                    PreparedStatement ps = conn.prepareStatement("UPDATE sections SET capacity = ? WHERE section_id = ?")) {
                   ps.setInt(1, newCapacity);
                   ps.setInt(2, sectionId);
                   ps.executeUpdate();
                   model.setValueAt(String.valueOf(newCapacity), row, 3);
                   statusLabel.setText("Capacity updated for " + courseName);
               } catch (Exception ex2) {
                   JOptionPane.showMessageDialog(this, "Failed to update capacity in DB: " + dbEx.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
               }
            }
         }
      } catch (Exception ex) {
         JOptionPane.showMessageDialog(this, "Error updating capacity: " + ex.getMessage(), 
             "Error", JOptionPane.ERROR_MESSAGE);
      }
   }

   // --- 2. SCORES VIEW (Enhanced with Grading Scheme) ---
   private JPanel makeScoresPanel() {
      JPanel card = new JPanel(new BorderLayout());
      card.setBackground(COLOR_CARD_BG);
      card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

      JLabel header = new JLabel("Enter Grades");
      header.setFont(FONT_HEADER);
      card.add(header, BorderLayout.NORTH);

      // Top filter panel with course dropdown
      JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
      filterPanel.setBackground(COLOR_CARD_BG);
      JLabel courseFilterLabel = new JLabel("Filter by Course:");
      JComboBox<String> courseFilterCombo = new JComboBox<>();
      courseFilterCombo.addItem("-- All Courses --");
      filterPanel.add(courseFilterLabel);
      filterPanel.add(courseFilterCombo);
      card.add(filterPanel, BorderLayout.NORTH);

      // Assessment types to show as columns
      java.util.List<String> assessmentTypes = new java.util.ArrayList<>();
      assessmentTypes.add("Mid Sem");
      assessmentTypes.add("End Sem");
      assessmentTypes.add("Quiz");
      assessmentTypes.add("Assignment");

      String[] cols = new String[assessmentTypes.size() + 4];
      cols[0] = "Student ID";
      cols[1] = "Student Name";
      cols[2] = "Course";
      for (int i = 0; i < assessmentTypes.size(); i++) {
         cols[i + 3] = assessmentTypes.get(i);
      }
      cols[cols.length - 1] = "Total Marks";

      DefaultTableModel gradesModel = new DefaultTableModel(cols, 0) {
         @Override public boolean isCellEditable(int row, int column) {
            return column >= 3 && column < cols.length - 1; // Assessment columns are editable, not Total Marks
         }
      };
      JTable gradesTable = new JTable(gradesModel);
      gradesTable.setFont(FONT_TABLE);
      gradesTable.setRowHeight(28);
      gradesTable.setShowVerticalLines(true);
      gradesTable.setGridColor(new Color(220, 220, 220));
      gradesTable.setIntercellSpacing(new java.awt.Dimension(1, 1));

      // Load all courses for filter dropdown
      final java.util.List<SectionRow> allCourses = new java.util.ArrayList<>();
      final java.util.Map<Integer, String> sectionIdToCourseDisplay = new java.util.HashMap<>();
      try {
         String insId = mappedInstructorId();
         if (insId == null) insId = String.valueOf(session.getUserId());
         java.util.List<SectionRow> loaded = sectionDAO.getSectionsForInstructor(insId, "Fall", 2025);
         allCourses.addAll(loaded);
         for (SectionRow course : allCourses) {
            // Prefix display with sectionId so other parts of the code which expect a numeric id can parse it
            String display = course.sectionId + " - " + course.courseCode + " - " + course.courseTitle;
            courseFilterCombo.addItem(display);
            sectionIdToCourseDisplay.put(course.sectionId, display);
         }
      } catch (Exception ex) {
         statusLabel.setText("Error loading courses: " + ex.getMessage());
      }

      // Grading scheme display area (shows scheme for selected course)
      final JTextArea schemeArea = new JTextArea();
      schemeArea.setEditable(false);
      schemeArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
      schemeArea.setBackground(new Color(250,250,250));
      schemeArea.setBorder(BorderFactory.createTitledBorder("Grading Scheme"));
      schemeArea.setRows(3);
      filterPanel.add(schemeArea);

      // Populate students table with existing marks from assessments table
      Runnable loadStudents = () -> {
         gradesModel.setRowCount(0);
         String selectedFilter = (String) courseFilterCombo.getSelectedItem();
         boolean filterAll = selectedFilter == null || selectedFilter.equals("-- All Courses --");

         try {
            for (SectionRow course : allCourses) {
               String courseDisplay = sectionIdToCourseDisplay.get(course.sectionId);
               if (!filterAll && !courseDisplay.equals(selectedFilter)) continue;

               // Updated query: Join enrollments with students and assessments to get existing marks
               String studentsSql = "SELECT DISTINCT e.student_id, s.name as student_name FROM enrollments e " +
                                   "LEFT JOIN students s ON e.student_id = s.id " +
                                   "WHERE e.section_id = ? ORDER BY s.name";
               try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                    PreparedStatement ps = c.prepareStatement(studentsSql)) {
                  ps.setInt(1, course.sectionId);
                  try (ResultSet rs = ps.executeQuery()) {
                     while (rs.next()) {
                        String studentId = rs.getString("student_id");
                        String studentName = rs.getString("student_name");
                        if (studentName == null) studentName = "Unknown"; // fallback if join fails
                        Object[] row = new Object[cols.length];
                        row[0] = studentId;
                        row[1] = studentName;
                        row[2] = courseDisplay;
                        
                        // Load existing assessment scores from database
                        double total = 0;
                        String assessmentsSql = "SELECT assessment_type, score FROM assessments WHERE section_id = ? AND student_id = ?";
                        try (PreparedStatement assessPs = c.prepareStatement(assessmentsSql)) {
                           assessPs.setInt(1, course.sectionId);
                           assessPs.setString(2, studentId);
                           try (ResultSet assessRs = assessPs.executeQuery()) {
                              java.util.Map<String, Double> assessmentScores = new java.util.HashMap<>();
                              while (assessRs.next()) {
                                 String type = assessRs.getString("assessment_type");
                                 double score = assessRs.getDouble("score");
                                 assessmentScores.put(type, score);
                                 total += score;
                              }
                              // Populate assessment columns
                              for (int i = 3; i < cols.length - 1; i++) {
                                 String colName = cols[i];
                                 Double score = assessmentScores.get(colName);
                                 row[i] = (score != null) ? String.format("%.2f", score) : "";
                              }
                           }
                        }
                        
                        row[cols.length - 1] = String.format("%.2f", total); // Set total
                        gradesModel.addRow(row);
                     }
                  }
               } catch (SQLException ex) {
                  statusLabel.setText("Error loading students: " + ex.getMessage());
               }
            }
         } catch (Exception ex) {
            statusLabel.setText("Error: " + ex.getMessage());
         }
      };
      loadStudents.run();

      courseFilterCombo.addItemListener(e -> {
         if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) loadStudents.run();
      });

      // Helper: update grading scheme display for selected course
      Runnable updateSchemeDisplay = () -> {
         String sel = (String) courseFilterCombo.getSelectedItem();
         if (sel == null || sel.equals("-- All Courses --")) {
            schemeArea.setText("Select a course to view its grading scheme.");
            return;
         }
         // Expect format: "<sectionId> - <courseCode> - <title>"
         String[] parts = sel.split(" - ");
         try {
            int sid = Integer.parseInt(parts[0].trim());
            java.util.Map<String, Integer> scheme = gradingSchemeDAO.loadGradingScheme(sid);
            if (scheme == null || scheme.isEmpty()) {
               schemeArea.setText("Using default scheme:\nMid Semester: 30%\nEnd Semester: 40%\nAssignments: 20%\nQuizzes: 10%");
            } else {
               StringBuilder sb = new StringBuilder();
               for (java.util.Map.Entry<String, Integer> e : scheme.entrySet()) {
                  sb.append(e.getKey()).append(": ").append(e.getValue()).append("%\n");
               }
               schemeArea.setText(sb.toString());
            }
         } catch (Exception ex) {
            schemeArea.setText("Could not load grading scheme: " + ex.getMessage());
         }
      };

      // Update scheme area when course selection changes
      courseFilterCombo.addItemListener(e -> {
         if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) updateSchemeDisplay.run();
      });

      // Add listener to calculate total marks when assessment scores change
      final boolean[] isUpdatingTotal = {false}; // Flag to prevent recursive updates
      gradesModel.addTableModelListener(e -> {
         if (e.getType() == javax.swing.event.TableModelEvent.UPDATE && !isUpdatingTotal[0]) {
            int row = e.getFirstRow();
            if (row >= 0 && row < gradesModel.getRowCount()) {
               double total = 0;
               for (int col = 3; col < gradesModel.getColumnCount() - 1; col++) {
                  Object val = gradesModel.getValueAt(row, col);
                  if (val != null && !val.toString().trim().isEmpty()) {
                     try {
                        total += Double.parseDouble(val.toString().trim());
                     } catch (NumberFormatException ignored) {}
                  }
               }
               isUpdatingTotal[0] = true; // Set flag before updating
               gradesModel.setValueAt(String.format("%.2f", total), row, gradesModel.getColumnCount() - 1);
               isUpdatingTotal[0] = false; // Reset flag after updating
            }
         }
      });

      JScrollPane scrollPane = new JScrollPane(gradesTable);
      card.add(scrollPane, BorderLayout.CENTER);

      // Bottom Save button
      JButton btnSaveAll = new JButton("Save All Grades");
      btnSaveAll.setFont(FONT_BUTTON);
      btnSaveAll.setBackground(new Color(0, 120, 215));
      btnSaveAll.setForeground(Color.WHITE);
      btnSaveAll.addActionListener(e -> {
         int saved = 0;
         for (int row = 0; row < gradesModel.getRowCount(); row++) {
            String studentId = gradesModel.getValueAt(row, 0).toString();
            String courseDisplay = gradesModel.getValueAt(row, 2).toString();
            for (int col = 3; col < gradesModel.getColumnCount(); col++) {
               String assessmentType = gradesModel.getColumnName(col);
               // Skip the "Total Marks" column - don't save it to database
               if (assessmentType.equals("Total Marks")) continue;
               
               Object scoreObj = gradesModel.getValueAt(row, col);
               if (scoreObj != null && !scoreObj.toString().trim().isEmpty()) {
                  String score = scoreObj.toString().trim();
                  try {
                     saveGradeToDatabase(courseDisplay, studentId, assessmentType, score);
                     saved++;
                  } catch (Exception ex) {
                     // skip on error
                  }
               }
            }
         }
         statusLabel.setText("Saved " + saved + " grade(s).");
         loadStudents.run();
      });

      JPanel bottom = new JPanel();
      bottom.setBackground(COLOR_CARD_BG);
      bottom.add(btnSaveAll);
      card.add(bottom, BorderLayout.SOUTH);
      
      return card;
   }

   // --- 4. STATS VIEW (Grade Slabs) ---
   private JPanel makeStatsPanel() {
      JPanel card = new JPanel(new BorderLayout());
      card.setBackground(COLOR_CARD_BG);
      card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
      
      JLabel header = new JLabel("Performance Statistics - Grade Slabs");
      header.setFont(FONT_HEADER);
      card.add(header, BorderLayout.NORTH);

      // Top filter panel with course dropdown
      JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
      topPanel.setBackground(COLOR_CARD_BG);
      
      JComboBox<String> courseCombo = new JComboBox<>();
      courseCombo.addItem("-- All Courses --");
      
      java.util.List<SectionRow> allCourses = new java.util.ArrayList<>();
      try {
         String insId = mappedInstructorId();
         if (insId == null) insId = String.valueOf(session.getUserId());
         allCourses = sectionDAO.getSectionsForInstructor(insId, "Fall", 2025);
         for (SectionRow course : allCourses) {
            courseCombo.addItem(course.sectionId + " - " + course.courseCode + " (" + course.courseTitle + ")");
         }
      } catch (Exception ex) {
         statusLabel.setText("Error loading courses: " + ex.getMessage());
      }
      
      JButton btnRefresh = new JButton("🔄 Refresh");
      btnRefresh.setFont(FONT_BUTTON);
      btnRefresh.setBackground(new Color(0, 120, 215));
      btnRefresh.setForeground(Color.WHITE);
      
      topPanel.add(new JLabel("Filter by Course:"));
      topPanel.add(courseCombo);
      topPanel.add(btnRefresh);

      // Main content area with grade slabs
      JPanel contentPanel = new JPanel();
      contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
      contentPanel.setBackground(COLOR_CARD_BG);

      // Define grade slabs: (min, max, label, color)
      String[][] slabs = {
          {"90", "100", "A+ (90-100)", new Color(34, 177, 76).toString()},
          {"80", "89", "A (80-89)", new Color(63, 195, 128).toString()},
          {"70", "79", "B (70-79)", new Color(252, 195, 11).toString()},
          {"60", "69", "C (60-69)", new Color(243, 156, 18).toString()},
          {"0", "59", "F (0-59)", new Color(230, 126, 34).toString()}
      };

      Runnable refreshStats = () -> {
         contentPanel.removeAll();
         String selectedCourse = (String) courseCombo.getSelectedItem();
         boolean filterAll = selectedCourse == null || selectedCourse.equals("-- All Courses --");
         
         try {
            for (String[] slab : slabs) {
               int minGrade = Integer.parseInt(slab[0]);
               int maxGrade = Integer.parseInt(slab[1]);
               String slabLabel = slab[2];
               Color slabColor = new Color(Integer.parseInt(slab[3].replaceAll("[^0-9]", "")));
               
               // Fetch students in this grade range for selected course(s)
               java.util.List<String> studentsInSlab = new java.util.ArrayList<>();
               // Calculate final grade as SUM of all scores from assessments table
               String gradeSql = "SELECT DISTINCT a.student_id, s.name as student_name, sec.course_code, " +
                   "COALESCE(SUM(a.score), 0) as final_score " +
                   "FROM assessments a " +
                   "LEFT JOIN students s ON a.student_id = s.id " +
                   "LEFT JOIN sections sec ON a.section_id = sec.section_id " +
                   "WHERE a.section_id IS NOT NULL " +
                   "GROUP BY a.student_id, a.section_id " +
                   "HAVING final_score >= ? AND final_score <= ?";
               
               if (!filterAll) {
                  // Extract section_id from selected course
                  String[] parts = selectedCourse.split(" - ");
                  String sectionIdStr = parts[0].trim();
                  try {
                     int sectionId = Integer.parseInt(sectionIdStr);
                     gradeSql += " AND a.section_id = ?";
                     
                     try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                          PreparedStatement ps = c.prepareStatement(gradeSql)) {
                        ps.setDouble(1, minGrade);
                        ps.setDouble(2, maxGrade);
                        ps.setInt(3, sectionId);
                        try (ResultSet rs = ps.executeQuery()) {
                           while (rs.next()) {
                              String id = rs.getString("student_id");
                              String name = rs.getString("student_name");
                              Double score = rs.getDouble("final_score");
                              studentsInSlab.add((name != null ? name : "N/A") + " (ID: " + id + ") - Score: " + String.format("%.2f", score));
                           }
                        }
                     }
                  } catch (NumberFormatException ex) {
                     statusLabel.setText("Invalid course selection");
                     continue;
                  }
               } else {
                  try (Connection c = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
                       PreparedStatement ps = c.prepareStatement(gradeSql)) {
                     ps.setDouble(1, minGrade);
                     ps.setDouble(2, maxGrade);
                     try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                           String id = rs.getString("student_id");
                           String name = rs.getString("student_name");
                           String courseCode = rs.getString("course_code");
                           Double score = rs.getDouble("final_score");
                           studentsInSlab.add((name != null ? name : "N/A") + " (ID: " + id + ") - " + courseCode + " - Score: " + String.format("%.2f", score));
                        }
                     }
                  }
               }
               
               // Create slab panel
               JPanel slabPanel = new JPanel(new BorderLayout());
               slabPanel.setBackground(Color.WHITE);
               slabPanel.setBorder(BorderFactory.createCompoundBorder(
                   BorderFactory.createMatteBorder(0, 5, 0, 0, slabColor),
                   BorderFactory.createEmptyBorder(10, 15, 10, 15)
               ));
               
               JLabel slabHeader = new JLabel(slabLabel + " (" + studentsInSlab.size() + " students)");
               slabHeader.setFont(new Font("SansSerif", Font.BOLD, 14));
               slabHeader.setForeground(slabColor);
               slabPanel.add(slabHeader, BorderLayout.NORTH);
               
               JPanel studentsPanel = new JPanel();
               studentsPanel.setLayout(new BoxLayout(studentsPanel, BoxLayout.Y_AXIS));
               studentsPanel.setBackground(Color.WHITE);
               
               if (studentsInSlab.isEmpty()) {
                  JLabel noStudents = new JLabel("  No students in this range");
                  noStudents.setFont(FONT_TABLE);
                  noStudents.setForeground(new Color(128, 128, 128));
                  studentsPanel.add(noStudents);
               } else {
                  for (String student : studentsInSlab) {
                     JLabel studentLabel = new JLabel("  • " + student);
                     studentLabel.setFont(FONT_TABLE);
                     studentsPanel.add(studentLabel);
                  }
               }
               
               slabPanel.add(new JScrollPane(studentsPanel), BorderLayout.CENTER);
               slabPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120 + (studentsInSlab.size() * 25)));
               contentPanel.add(slabPanel);
               contentPanel.add(Box.createVerticalStrut(10));
            }
            
            contentPanel.revalidate();
            contentPanel.repaint();
         } catch (Exception ex) {
            statusLabel.setText("Error loading statistics: " + ex.getMessage());
         }
      };

      btnRefresh.addActionListener(e -> refreshStats.run());
      courseCombo.addItemListener(ev -> {
         if (ev.getStateChange() == java.awt.event.ItemEvent.SELECTED) refreshStats.run();
      });

      // Initial load
      refreshStats.run();

      JPanel center = new JPanel(new BorderLayout());
      center.add(topPanel, BorderLayout.NORTH);
      
      JScrollPane scrollPane = new JScrollPane(contentPanel);
      scrollPane.setBackground(COLOR_CARD_BG);
      scrollPane.getVerticalScrollBar().setUnitIncrement(16);
      center.add(scrollPane, BorderLayout.CENTER);
      
      card.add(center, BorderLayout.CENTER);
      return card;
   }

   // --- 5. NEW GRADING SCHEME PANEL ---
   private JPanel makeGradingSchemePanel() {
      JPanel card = new JPanel(new BorderLayout());
      card.setBackground(COLOR_CARD_BG);
      card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
      
      JLabel header = new JLabel("Grading Scheme Management");
      header.setFont(FONT_HEADER);
      card.add(header, BorderLayout.NORTH);

      JPanel contentPanel = new JPanel(new GridBagLayout());
      contentPanel.setBackground(COLOR_CARD_BG);
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(10, 10, 10, 10);
      gbc.fill = GridBagConstraints.HORIZONTAL;

      // Course selection
      JComboBox<String> comboCourse = new JComboBox<>();
      try {
         String mappedInsId = mappedInstructorId();
         String insIdToUse = (mappedInsId != null) ? mappedInsId : String.valueOf(session.getUserId());
         java.util.List<SectionRow> courses = sectionDAO.getSectionsForInstructor(insIdToUse, "Fall", 2025);
         for (SectionRow course : courses) {
            comboCourse.addItem(course.sectionId + " - " + course.courseCode + " (" + course.courseTitle + ")");
         }
         if (comboCourse.getItemCount() == 0) {
            comboCourse.addItem("No courses available");
         }
      } catch (Exception ex) {
         comboCourse.addItem("Error loading courses");
      }

      addFormRow(contentPanel, gbc, 0, "Select Course:", comboCourse);

      // Grading components panel
      JPanel componentsPanel = new JPanel(new GridLayout(0, 3, 10, 10));
      componentsPanel.setBackground(COLOR_CARD_BG);
      componentsPanel.setBorder(BorderFactory.createTitledBorder(
          BorderFactory.createLineBorder(Color.GRAY), 
          "Grading Components", 
          TitledBorder.LEFT, 
          TitledBorder.TOP,
          FONT_NAV
      ));

      // Define all possible grading components
      String[] components = {
          "Mid Semester", "End Semester", "Assignments", 
          "Quizzes"
      };

      Map<String, JCheckBox> checkBoxes = new HashMap<>();
      Map<String, JSpinner> spinners = new HashMap<>();

      for (String component : components) {
          JCheckBox checkBox = new JCheckBox(component);
          checkBox.setFont(FONT_NAV);
          checkBox.setBackground(COLOR_CARD_BG);
          
          JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 5));
          spinner.setEnabled(false);
          
          checkBox.addActionListener(e -> {
              spinner.setEnabled(checkBox.isSelected());
          });
          
          componentsPanel.add(checkBox);
          componentsPanel.add(spinner);
          componentsPanel.add(new JLabel("%"));
          
          checkBoxes.put(component, checkBox);
          spinners.put(component, spinner);
      }

      gbc.gridx = 0;
      gbc.gridy = 1;
      gbc.gridwidth = 2;
      gbc.fill = GridBagConstraints.BOTH;
      contentPanel.add(componentsPanel, gbc);

      // Total percentage label
      JLabel totalLabel = new JLabel("Total: 0%");
      totalLabel.setFont(FONT_NAV);
      gbc.gridy = 2;
      gbc.gridwidth = 2;
      gbc.fill = GridBagConstraints.NONE;
      gbc.anchor = GridBagConstraints.CENTER;
      contentPanel.add(totalLabel, gbc);

      // Update total when spinners change
      for (JSpinner spinner : spinners.values()) {
          spinner.addChangeListener(e -> updateTotalPercentage(spinners, totalLabel));
      }

      // Buttons panel
      JPanel buttonsPanel = new JPanel(new FlowLayout());
      buttonsPanel.setBackground(COLOR_CARD_BG);
      
      JButton btnSave = new JButton("Save Grading Scheme");
      btnSave.setFont(FONT_BUTTON);
      btnSave.setBackground(new Color(0, 120, 215));
      btnSave.setForeground(Color.WHITE);
      
      JButton btnLoad = new JButton("Load Existing Scheme");
      btnLoad.setFont(FONT_BUTTON);
      btnLoad.setBackground(new Color(46, 204, 113));
      btnLoad.setForeground(Color.WHITE);

      buttonsPanel.add(btnSave);
      buttonsPanel.add(btnLoad);

      gbc.gridy = 3;
      gbc.gridwidth = 2;
      contentPanel.add(buttonsPanel, gbc);

      // Load existing scheme when course changes
      comboCourse.addItemListener(e -> {
          if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
              String selected = (String) e.getItem();
              if (selected != null && !selected.startsWith("Error") && !selected.equals("No courses available")) {
                  loadGradingScheme(selected, checkBoxes, spinners, totalLabel);
              }
          }
      });

      // Save button action
      btnSave.addActionListener(e -> {
          String selectedCourse = (String) comboCourse.getSelectedItem();
          if (selectedCourse == null || selectedCourse.startsWith("Error") || selectedCourse.equals("No courses available")) {
              JOptionPane.showMessageDialog(this, "Please select a valid course.", "Input Required", JOptionPane.WARNING_MESSAGE);
              return;
          }
          saveGradingScheme(selectedCourse, checkBoxes, spinners, totalLabel);
      });

      // Load button action
      btnLoad.addActionListener(e -> {
          String selectedCourse = (String) comboCourse.getSelectedItem();
          if (selectedCourse == null || selectedCourse.startsWith("Error") || selectedCourse.equals("No courses available")) {
              JOptionPane.showMessageDialog(this, "Please select a valid course.", "Input Required", JOptionPane.WARNING_MESSAGE);
              return;
          }
          loadGradingScheme(selectedCourse, checkBoxes, spinners, totalLabel);
      });

      card.add(contentPanel, BorderLayout.CENTER);
      return card;
   }

   private void updateTotalPercentage(Map<String, JSpinner> spinners, JLabel totalLabel) {
      int total = 0;
      for (JSpinner spinner : spinners.values()) {
          if (spinner.isEnabled()) {
              total += (Integer) spinner.getValue();
          }
      }
      totalLabel.setText("Total: " + total + "%");
      totalLabel.setForeground(total == 100 ? Color.GREEN : Color.RED);
   }

   private void addFormRow(JPanel p, GridBagConstraints gbc, int y, String lbl, Component c) {
      gbc.gridx = 0; 
      gbc.gridy = y; 
      gbc.gridwidth = 1;
      JLabel label = new JLabel(lbl);
      label.setFont(FONT_NAV);
      p.add(label, gbc);
      gbc.gridx = 1; 
      p.add(c, gbc);
   }

   // Count students whose `section` starts with given course code (e.g. "CS101" matches "CS101-A")
   private int countStudentsForCourse(String courseCode) {
      int count = 0;
      String url = DatabaseConfig.getDatabaseUrl();
      String sql = "SELECT COUNT(*) FROM students WHERE section LIKE ?";
      try (Connection conn = DriverManager.getConnection(url);
           PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, courseCode + "%");
         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) count = rs.getInt(1);
         }
      } catch (SQLException ex) {
         statusLabel.setText("DB error counting students: " + ex.getMessage());
      }
      return count;
   }

   // Count enrolled students for a given section id (from enrollments table)
   private int getEnrolledCount(int sectionId) {
      int enrolled = 0;
      String url = DatabaseConfig.getDatabaseUrl();
      String sql = "SELECT COUNT(*) FROM enrollments WHERE section_id = ?";
      try (Connection conn = DriverManager.getConnection(url);
           PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setInt(1, sectionId);
         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) enrolled = rs.getInt(1);
         }
      } catch (SQLException ex) {
         statusLabel.setText("DB error counting enrolled students: " + ex.getMessage());
      }
      return enrolled;
   }

   // Populate a JComboBox with students for a given section id
   private void populateStudentsCombo(JComboBox<String> combo, int sectionId) {
      combo.removeAllItems();
      String url = DatabaseConfig.getDatabaseUrl();
      String getCourseSql = "SELECT course_code FROM sections WHERE section_id = ?";
      String getStudentsSql = "SELECT id, name FROM students WHERE section LIKE ? OR section = ? ORDER BY name";
      try (Connection conn = DriverManager.getConnection(url);
           PreparedStatement ps1 = conn.prepareStatement(getCourseSql)) {
         ps1.setInt(1, sectionId);
         try (ResultSet rs = ps1.executeQuery()) {
            if (rs.next()) {
               String courseCode = rs.getString("course_code");
               try (PreparedStatement ps2 = conn.prepareStatement(getStudentsSql)) {
                  ps2.setString(1, courseCode + "%");
                  ps2.setString(2, courseCode);
                  try (ResultSet rs2 = ps2.executeQuery()) {
                     boolean added = false;
                     while (rs2.next()) {
                        String sid = rs2.getString("id");
                        String name = rs.getString("name");
                        combo.addItem(sid + " - " + name);
                        added = true;
                     }
                     if (!added) combo.addItem("No students enrolled");
                  }
               }
            } else {
               combo.addItem("Unknown course");
            }
         }
      } catch (SQLException ex) {
         statusLabel.setText("Error loading students: " + ex.getMessage());
         combo.removeAllItems();
         combo.addItem("Error loading students");
      }
   }

   // Populate assessment types based on grading scheme for a course
   private void populateAssessmentTypesCombo(JComboBox<String> combo, int sectionId) {
      combo.removeAllItems();
      try {
         java.util.Map<String, Integer> scheme = gradingSchemeDAO.loadGradingScheme(sectionId);
         if (scheme != null && !scheme.isEmpty()) {
            for (String comp : scheme.keySet()) combo.addItem(comp);
         } else {
            // default fallback
            String[] defaultTypes = {"Quiz", "Midterm", "End-Sem", "Assignment", "Worksheet", "Attendance"};
            for (String type : defaultTypes) combo.addItem(type);
         }
      } catch (Exception ex) {
         combo.addItem("Quiz");
         combo.addItem("Midterm");
         combo.addItem("End-Sem");
      }
   }

   /**
    * Improve table columns: preferred widths, center numeric columns and render actions nicely.
    */
   private void setupCoursesTableColumns(JTable table) {
      try {
         TableColumn col0 = table.getColumnModel().getColumn(0); col0.setPreferredWidth(60);
         TableColumn col1 = table.getColumnModel().getColumn(1); col1.setPreferredWidth(420);
         TableColumn col2 = table.getColumnModel().getColumn(2); col2.setPreferredWidth(220);
         TableColumn col3 = table.getColumnModel().getColumn(3); col3.setPreferredWidth(90);
         TableColumn col4 = table.getColumnModel().getColumn(4); col4.setPreferredWidth(90);
      } catch (Exception ignored) {}

      // center align numeric columns (id, capacity, enrolled)
      DefaultTableCellRenderer center = new DefaultTableCellRenderer();
      center.setHorizontalAlignment(DefaultTableCellRenderer.CENTER);
      try {
         table.getColumnModel().getColumn(0).setCellRenderer(center);
         table.getColumnModel().getColumn(3).setCellRenderer(center);
         table.getColumnModel().getColumn(4).setCellRenderer(center);
      } catch (Exception ignored) {}

      // render actions column as a clickable-looking label
      // Actions column removed; no renderer needed
   }

   /**
    * Verify that the current session user is the owner/instructor for the given section.
    * Tries multiple possible columns (instructor_id, instructor) and compares with mapped instructor id.
    */
   private boolean isOwnerOfSection(int sectionId) {
      String url = DatabaseConfig.getDatabaseUrl();
      String sql = "SELECT instructor_id, instructor FROM sections WHERE section_id = ? LIMIT 1";
      try (Connection conn = DriverManager.getConnection(url);
           PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setInt(1, sectionId);
         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               String insId = null;
               try { insId = rs.getString("instructor_id"); } catch (Exception ignored) {}
               if (insId == null || insId.isEmpty()) {
                  try { insId = rs.getString("instructor"); } catch (Exception ignored) {}
               }
               if (insId != null && !insId.isEmpty()) {
                  String mapped = mappedInstructorId();
                  if (mapped != null && mapped.equalsIgnoreCase(insId)) return true;
                  try { int iid = Integer.parseInt(insId); if (iid == session.getUserId()) return true; } catch (Exception ignored) {}
               }
            }
         }
      } catch (SQLException ignored) {}
      return false;
   }

   // Database integration methods
   private void saveGradeToDatabase(String course, String student, String assessmentType, String score) {
      try {
         // Parse course id (expected formats: "123 - CODE" or "123")
         String courseIdStr = course.split(" - ")[0].trim();
         int sectionId = Integer.parseInt(courseIdStr);

         // Parse student id (expected formats: "S001 - Name" or "2023001 - Name")
         String studentId = student.split(" - ")[0].trim();

         double val = Double.parseDouble(score);

         // Save assessment record
         assessmentDAO.saveAssessment(sectionId, studentId, assessmentType, val);
         JOptionPane.showMessageDialog(this, "Assessment saved successfully!\nCourse: " + course + 
             "\nStudent: " + student + "\nType: " + assessmentType + "\nScore: " + score,
             "Success", JOptionPane.INFORMATION_MESSAGE);
      } catch (NumberFormatException nfe) {
         JOptionPane.showMessageDialog(this, "Invalid numeric value: " + nfe.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
      } catch (Exception ex) {
         JOptionPane.showMessageDialog(this, "Error saving grade: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
   }

   private void computeFinalGrade(String course, String student) {
      try {
         String courseIdStr = course.split(" - ")[0].trim();
         int sectionId = Integer.parseInt(courseIdStr);
         String studentId = student.split(" - ")[0].trim();

         java.util.Map<String, Integer> scheme = gradingSchemeDAO.loadGradingScheme(sectionId);
         double finalG = 0.0;
         if (scheme == null || scheme.isEmpty()) {
            // Fallback: try to use quiz/midterm/endsem columns from grades table
            java.util.List<java.util.Map<String, Object>> rows = gradeDAO.getScoresForSection(sectionId);
            for (java.util.Map<String, Object> r : rows) {
               if (studentId.equals(String.valueOf(r.get("student_id")))) {
                  double quiz = ((Number) r.getOrDefault("quiz", 0.0)).doubleValue();
                  double mid = ((Number) r.getOrDefault("midterm", 0.0)).doubleValue();
                  double end = ((Number) r.getOrDefault("endsem", 0.0)).doubleValue();
                  finalG = (quiz * 0.2) + (mid * 0.3) + (end * 0.5);
                  gradeDAO.saveScores(sectionId, studentId, quiz, mid, end, finalG);
                  JOptionPane.showMessageDialog(this, "Final grade computed and published!\nCourse: " + course + "\nStudent: " + student + "\nFinal: " + String.format("%.2f", finalG), "Success", JOptionPane.INFORMATION_MESSAGE);
                  return;
               }
            }
            JOptionPane.showMessageDialog(this, "No scores available to compute final.", "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
         }

         // Compute using grading scheme and assessment averages
         for (java.util.Map.Entry<String, Integer> e : scheme.entrySet()) {
            String comp = e.getKey();
            int weight = e.getValue();
            double avg = assessmentDAO.getStudentAverageForType(sectionId, studentId, comp);
            finalG += avg * (weight / 100.0);
         }

         // Persist final in grades table (store final; keep quiz/mid/end as 0 unless present)
         gradeDAO.saveScores(sectionId, studentId, 0.0, 0.0, 0.0, finalG);
         JOptionPane.showMessageDialog(this, "Final grade computed and published!\nCourse: " + course + "\nStudent: " + student + "\nFinal: " + String.format("%.2f", finalG), "Success", JOptionPane.INFORMATION_MESSAGE);
      } catch (NumberFormatException nfe) {
         JOptionPane.showMessageDialog(this, "Invalid course id: " + nfe.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
      } catch (Exception ex) {
         JOptionPane.showMessageDialog(this, "Error computing final grade: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
   }

   private void generateStatisticsReport(String course) {
      String report = generateStatisticsReportString(course);
      if (report == null) {
         JOptionPane.showMessageDialog(this, "No scores available for the selected course.", "No Data", JOptionPane.INFORMATION_MESSAGE);
      } else {
         JTextArea ta = new JTextArea(report);
         ta.setEditable(false);
         ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
         JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Class Statistics", JOptionPane.INFORMATION_MESSAGE);
      }
   }

   // Return statistics report as string, or null when no data
   private String generateStatisticsReportString(String course) {
      try {
         String courseIdStr = course.split(" - ")[0].trim();
         int sectionId = Integer.parseInt(courseIdStr);
         java.util.Map<String, Integer> scheme = gradingSchemeDAO.loadGradingScheme(sectionId);

         // Gather students from assessments and grades
         java.util.Map<String, Double> finalMap = new java.util.HashMap<>();

         // First, prefer final stored in grades
         java.util.List<java.util.Map<String, Object>> gradeRows = gradeDAO.getScoresForSection(sectionId);
         for (java.util.Map<String, Object> r : gradeRows) {
            String sid = String.valueOf(r.get("student_id"));
            double f = ((Number) r.getOrDefault("final", 0.0)).doubleValue();
            if (f > 0) finalMap.put(sid, f);
         }

         // Then compute finals for students present in assessments but not in finalMap
         java.util.List<java.util.Map<String, Object>> assessRows = assessmentDAO.getAssessmentsForSection(sectionId);
         java.util.Set<String> students = new java.util.HashSet<>();
         for (java.util.Map<String, Object> a : assessRows) students.add((String)a.get("student_id"));

         for (String sid : students) {
            if (finalMap.containsKey(sid)) continue;
            double finalG = 0.0;
            if (scheme != null && !scheme.isEmpty()) {
               for (java.util.Map.Entry<String, Integer> e : scheme.entrySet()) {
                  double avg = assessmentDAO.getStudentAverageForType(sectionId, sid, e.getKey());
                  finalG += avg * (e.getValue() / 100.0);
               }
               finalMap.put(sid, finalG);
            }
         }

         if (finalMap.isEmpty()) return null;

         int total = finalMap.size();
         double sumFinal = 0.0; double minF = Double.MAX_VALUE, maxF = -Double.MAX_VALUE;
         for (double v : finalMap.values()) { sumFinal += v; if (v < minF) minF = v; if (v > maxF) maxF = v; }
         double avgFinal = sumFinal / total;

         StringBuilder sb = new StringBuilder();
         sb.append("Statistics for course: ").append(course).append("\n\n");
         sb.append("Total Students: ").append(total).append("\n");
         if (scheme != null && !scheme.isEmpty()) {
            for (String comp : scheme.keySet()) {
               // compute average for component across students
               double sumComp = 0.0; int cnt = 0;
               for (String sid : finalMap.keySet()) {
                  double v = assessmentDAO.getStudentAverageForType(sectionId, sid, comp);
                  sumComp += v; cnt++;
               }
               double avgComp = cnt == 0 ? 0.0 : (sumComp / cnt);
               sb.append(String.format("Average %s: %.2f\n", comp, avgComp));
            }
         }
         sb.append(String.format("Average Final: %.2f\n", avgFinal));
         sb.append(String.format("Final Range: %.2f - %.2f\n", minF == Double.MAX_VALUE ? 0.0 : minF, maxF == -Double.MAX_VALUE ? 0.0 : maxF));
         return sb.toString();
      } catch (Exception ex) {
         return "Error generating report: " + ex.getMessage();
      }
   }

   // Save grading scheme to database
   private void saveGradingScheme(String course, Map<String, JCheckBox> checkBoxes, Map<String, JSpinner> spinners, JLabel totalLabel) {
      try {
         String courseIdStr = course.split(" - ")[0].trim();
         int sectionId = Integer.parseInt(courseIdStr);
         
         int total = 0;
         Map<String, Integer> scheme = new HashMap<>();
         
         for (Map.Entry<String, JCheckBox> entry : checkBoxes.entrySet()) {
            String component = entry.getKey();
            JCheckBox checkBox = entry.getValue();
            JSpinner spinner = spinners.get(component);
            
            if (checkBox.isSelected()) {
               int percentage = (Integer) spinner.getValue();
               scheme.put(component, percentage);
               total += percentage;
            }
         }
         
         if (total != 100) {
            JOptionPane.showMessageDialog(this, "Total percentage must be exactly 100%. Current total: " + total + "%", 
                "Invalid Total", JOptionPane.ERROR_MESSAGE);
            return;
         }
         
         // Save grading scheme to database
         gradingSchemeDAO.saveGradingScheme(sectionId, scheme);
         
         JOptionPane.showMessageDialog(this, "Grading scheme saved successfully for " + course, 
             "Success", JOptionPane.INFORMATION_MESSAGE);
         statusLabel.setText("Grading scheme saved for " + course);
         
      } catch (Exception ex) {
         JOptionPane.showMessageDialog(this, "Error saving grading scheme: " + ex.getMessage(), 
             "Error", JOptionPane.ERROR_MESSAGE);
      }
   }

   // Load grading scheme from database
   private void loadGradingScheme(String course, Map<String, JCheckBox> checkBoxes, Map<String, JSpinner> spinners, JLabel totalLabel) {
      try {
         String courseIdStr = course.split(" - ")[0].trim();
         int sectionId = Integer.parseInt(courseIdStr);

         // Attempt to load scheme from DB
         Map<String, Integer> scheme = null;
         try {
            scheme = gradingSchemeDAO.loadGradingScheme(sectionId);
         } catch (Exception daoEx) {
            // If DAO fails, keep going and use a fallback
            statusLabel.setText("Warning: could not load grading scheme from DB: " + daoEx.getMessage());
         }

         // Fallback default scheme when DB has none
         if (scheme == null || scheme.isEmpty()) {
            scheme = new HashMap<>();
            scheme.put("Mid Semester", 30);
            scheme.put("End Semester", 40);
            scheme.put("Assignments", 20);
            scheme.put("Quizzes", 10);
            statusLabel.setText("Using default grading scheme for " + course);
         } else {
            statusLabel.setText("Grading scheme loaded from DB for " + course);
         }

         // Apply the scheme to UI safely
         for (Map.Entry<String, JCheckBox> entry : checkBoxes.entrySet()) {
            String component = entry.getKey();
            JCheckBox checkBox = entry.getValue();
            JSpinner spinner = spinners.get(component);

            if (spinner == null) continue; // defensive: ensure spinner exists

            Integer pct = scheme.get(component);
            if (pct != null) {
               checkBox.setSelected(true);
               spinner.setEnabled(true);
               // ensure correct numeric type
               spinner.setValue(pct);
            } else {
               checkBox.setSelected(false);
               spinner.setEnabled(false);
               spinner.setValue(0);
            }
         }

         // Recompute total label
         updateTotalPercentage(spinners, totalLabel);

      } catch (Exception ex) {
         JOptionPane.showMessageDialog(this, "Error loading grading scheme: " + ex.getMessage(),
             "Error", JOptionPane.ERROR_MESSAGE);
      }
   }

   private void showChangePassword() {
      // Change-password dialog: current, new, confirm
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
      if (!auth.PasswordManager.verifyPassword(this.session.getUsername(), current)) {
         JOptionPane.showMessageDialog(this, "Current password is incorrect.", "Authentication Failed", JOptionPane.ERROR_MESSAGE);
         return;
      }

      // Update password using PasswordManager (syncs across all auth providers)
      if (auth.PasswordManager.updatePassword(this.session.getUsername(), newp)) {
         JOptionPane.showMessageDialog(this, "Password changed successfully. You will be logged out.", "Success", JOptionPane.INFORMATION_MESSAGE);
         logout();
      } else {
         JOptionPane.showMessageDialog(this, "Failed to change password. Please try again.", "Failed", JOptionPane.ERROR_MESSAGE);
      }
   }

   private void showMessage(String var1) {
      JOptionPane.showMessageDialog(this, var1);
   }

   private void logout() {
      this.dispose();
      SwingUtilities.invokeLater(() -> {
         LoginWindow var0 = new LoginWindow();
         var0.setExtendedState(6);
         var0.setVisible(true);
      });
   }

   // Add helper method to derive section letter
   private String getSectionLetter(int sectionId) {
       // Example logic: derive section letter based on sectionId
       return sectionId % 2 == 0 ? "A" : "B";
   }

   // Update table initialization to include section labels and enrollment toggle
   private void initializeTable() {
         coursesModel = new DefaultTableModel(new Object[][] {}, new String[] {
            "Course ID", "Course Name", "Instructor", "Capacity", "Enrolled"
         });

       coursesTable.setModel(coursesModel);
         // Actions column removed

       loadCourses();
   }

   // Load courses into the table
   private void loadCourses() {
       try {
            String myIns = mappedInstructorId();
            if (myIns == null) return;

            List<SectionRow> sections = sectionDAO.getAllSections();
            for (SectionRow section : sections) {
               String instr = section.getInstructorName();
               if (instr == null) continue;
               if (!myIns.equalsIgnoreCase(instr)) continue; // only show sections actually assigned to this instructor

               String enrollmentStatus = section.isEnrollmentOpen() ? "Open" : "Closed";

               coursesModel.addRow(new Object[] {
                  section.getCourseId(),
                  section.getCourseName(),
                  section.getInstructorName(),
                  section.getCapacity(),
                  section.getEnrolledCount(),
                  enrollmentStatus,
                  "Toggle Enrollment"
               });
            }
       } catch (Exception ex) {
           JOptionPane.showMessageDialog(this, "Failed to load courses: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
       }
   }

   // Add event listener for table actions
   private void addTableActionListener() {
       coursesTable.getColumn("Actions").setCellEditor(new CustomCellEditor(this));
   }

   // Toggle enrollment status for a course
   public void toggleEnrollmentForCourse(int row, int column) {
       try {
           if (row < 0 || row >= coursesModel.getRowCount()) {
               JOptionPane.showMessageDialog(this, "Invalid row selected", "Error", JOptionPane.ERROR_MESSAGE);
               return;
           }
           
           int sectionId = (int) coursesModel.getValueAt(row, 0);
           String currentStatus = (String) coursesModel.getValueAt(row, 5);
           String newStatus = "Open".equals(currentStatus) ? "Closed" : "Open";
           
           // Update database
           String updateQuery = "UPDATE Section SET enrollment_open = ? WHERE id = ?";
           Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
           PreparedStatement pstmt = conn.prepareStatement(updateQuery);
           pstmt.setBoolean(1, "Open".equals(newStatus));
           pstmt.setInt(2, sectionId);
           pstmt.executeUpdate();
           pstmt.close();
           conn.close();
           
           // Update table model
           coursesModel.setValueAt(newStatus, row, 5);
           JOptionPane.showMessageDialog(this, "Enrollment status updated to: " + newStatus, "Success", JOptionPane.INFORMATION_MESSAGE);
       } catch (Exception ex) {
           JOptionPane.showMessageDialog(this, "Error toggling enrollment: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
       }
   }
}

// Placeholder for ButtonRenderer and ButtonEditor
// These need to be implemented or imported
class ButtonRenderer extends JButton implements TableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setText((value == null) ? "" : value.toString());
        return this;
    }
}

// Replace ButtonEditor with a compatible implementation
class ButtonEditor extends AbstractCellEditor implements TableCellEditor {
    private JButton button;
    private boolean currentValue;

    public ButtonEditor() {
        button = new JButton();
        button.addActionListener(e -> {
            currentValue = !currentValue;
            fireEditingStopped();
        });
    }

    @Override
    public Object getCellEditorValue() {
        return currentValue;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        currentValue = value != null && (boolean) value;
        button.setText(currentValue ? "Disable" : "Enable");
        return button;
    }
}

// Pass reference to InstructorDashboard for toggleEnrollmentForCourse
class CustomCellEditor extends AbstractCellEditor implements TableCellEditor {
    private JButton button;
    private InstructorDashboard dashboard;
    private int currentRow = -1;
    private int currentColumn = -1;

    public CustomCellEditor(InstructorDashboard dashboard) {
        this.dashboard = dashboard;
        button = new JButton("Toggle");
        button.addActionListener(e -> {
            fireEditingStopped();
        });
    }

    @Override
    public Object getCellEditorValue() {
        return "Toggle";
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        currentRow = row;
        currentColumn = column;
        button.setText("Toggle Enrollment");
        button.addActionListener(e -> {
            dashboard.toggleEnrollmentForCourse(currentRow, currentColumn);
            fireEditingStopped();
        });
        return button;
    }
}