package instructor;

import domain.UserSession;
import types.SectionRow;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Vector;
import instructor.dao.SectionDAO;
import instructor.dao.GradeDAO;
import java.util.List;
import java.util.Map;

public class MySectionsPanel extends JPanel {

    private final UserSession session;
    private final JLabel statusLabel;

    private DefaultTableModel tableModel;
    private JTable sectionsTable;

    private final SectionDAO sectionDAO = new SectionDAO();
    private final GradeDAO gradeDAO = new GradeDAO();

    public MySectionsPanel(UserSession session, JLabel statusLabel) {
        this.session = session;
        this.statusLabel = statusLabel;
        setLayout(new BorderLayout(10, 10));

        // 1. Table Setup
        setupSectionsTable();

        // 2. Control Panel
        add(createControlPanel(), BorderLayout.SOUTH);

        // Load data on initialization
        loadMySections();
    }

    private void setupSectionsTable() {
        // Column names: course code, title, day/time, room, enrolled count
        Vector<String> columns = new Vector<>();
        columns.add("Section ID");
        columns.add("Course Code");
        columns.add("Title");
        columns.add("Day/Time");
        columns.add("Room");
        columns.add("Enrollment");

        tableModel = new DefaultTableModel(columns, 0);
        sectionsTable = new JTable(tableModel);
        sectionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Hide Section ID column (index 0)
        sectionsTable.getColumnModel().getColumn(0).setMaxWidth(0);
        sectionsTable.getColumnModel().getColumn(0).setMinWidth(0);
        sectionsTable.getColumnModel().getColumn(0).setPreferredWidth(0);


        add(new JScrollPane(sectionsTable), BorderLayout.CENTER);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JButton gradeEntryButton = new JButton("Enter Grades for Selected Section");

        gradeEntryButton.addActionListener(e -> openGradeEntry());

        panel.add(gradeEntryButton);
        return panel;
    }

    // --- Business Logic ---

    private void loadMySections() {
        tableModel.setRowCount(0);
        try {
            // Use current term/year for demo; you can make this dynamic
            String term = "Fall";
            int year = 2025;
            List<SectionRow> sections = sectionDAO.getSectionsForInstructor(String.valueOf(session.getUserId()), term, year);
            for (SectionRow row : sections) {
                Vector<Object> data = new Vector<>();
                data.add(row.sectionId);
                data.add(row.courseCode);
                data.add(row.courseTitle);
                data.add(row.dayTime);
                data.add(row.room);
                data.add(row.capacity + " Students");
                tableModel.addRow(data);
            }
            statusLabel.setText("Loaded " + sections.size() + " sections for " + session.getUsername());
        } catch (Exception ex) {
            statusLabel.setText("Error loading sections: " + ex.getMessage());
        }
    }

    private void openGradeEntry() {
        int selectedRow = sectionsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a section to view grades.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int sectionId = (Integer) sectionsTable.getValueAt(selectedRow, 0);
        // Check if this section belongs to the instructor
        String term = "Fall";
        int year = 2025;
        List<SectionRow> mySections = sectionDAO.getSectionsForInstructor(String.valueOf(session.getUserId()), term, year);
        boolean found = false;
        for (SectionRow s : mySections) {
            if (s.sectionId == sectionId) found = true;
        }
        if (!found) {
            JOptionPane.showMessageDialog(this, "Not your section.", "Access Denied", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Grade entry dialog
        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Enter Grades", true);
        dlg.setLayout(new BorderLayout(10,10));
        dlg.setSize(600, 400);
        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        // Table for grades
        String[] columns = {"Student ID", "Quiz", "Midterm", "End-sem", "Final"};
        DefaultTableModel gradeModel = new DefaultTableModel(columns, 0);
        List<Map<String, Object>> grades = gradeDAO.getScoresForSection(sectionId);
        for (Map<String, Object> g : grades) {
            gradeModel.addRow(new Object[]{g.get("student_id"), g.get("quiz"), g.get("midterm"), g.get("endsem"), g.get("final")});
        }
        JTable gradeTable = new JTable(gradeModel);
        panel.add(new JScrollPane(gradeTable), BorderLayout.CENTER);
        // Weighting rule
        JPanel weightsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        weightsPanel.add(new JLabel("Weighting Rule (Quiz/Midterm/End-sem): "));
        JTextField quizW = new JTextField("20", 3);
        JTextField midW = new JTextField("30", 3);
        JTextField endW = new JTextField("50", 3);
        weightsPanel.add(quizW); weightsPanel.add(new JLabel("%  "));
        weightsPanel.add(midW); weightsPanel.add(new JLabel("%  "));
        weightsPanel.add(endW); weightsPanel.add(new JLabel("%"));
        panel.add(weightsPanel, BorderLayout.NORTH);
        // Save button
        JButton saveBtn = new JButton("Save All Grades");
        saveBtn.addActionListener(e -> {
            double qw = Double.parseDouble(quizW.getText());
            double mw = Double.parseDouble(midW.getText());
            double ew = Double.parseDouble(endW.getText());
            for (int i = 0; i < gradeModel.getRowCount(); i++) {
                String studentId = String.valueOf(gradeModel.getValueAt(i, 0));
                double quiz = Double.parseDouble(String.valueOf(gradeModel.getValueAt(i, 1)));
                double midterm = Double.parseDouble(String.valueOf(gradeModel.getValueAt(i, 2)));
                double endsem = Double.parseDouble(String.valueOf(gradeModel.getValueAt(i, 3)));
                double finalGrade = (quiz * qw + midterm * mw + endsem * ew) / 100.0;
                gradeModel.setValueAt(finalGrade, i, 4);
                gradeDAO.saveScores(sectionId, studentId, quiz, midterm, endsem, finalGrade);
            }
            JOptionPane.showMessageDialog(dlg, "Grades saved and final grades computed.");
        });
        // Stats button
        JButton statsBtn = new JButton("Show Class Stats");
        statsBtn.addActionListener(e -> {
            int n = gradeModel.getRowCount();
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += Double.parseDouble(String.valueOf(gradeModel.getValueAt(i, 4)));
            }
            double avg = n > 0 ? sum / n : 0;
            JOptionPane.showMessageDialog(dlg, "Class Average (Final): " + String.format("%.2f", avg));
        });
        // CSV export button
        JButton exportBtn = new JButton("Export Grades to CSV");
        exportBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                java.io.File file = fc.getSelectedFile();
                try (java.io.PrintWriter pw = new java.io.PrintWriter(file)) {
                    pw.println("StudentID,Quiz,Midterm,Endsem,Final");
                    for (int i = 0; i < gradeModel.getRowCount(); i++) {
                        pw.println(gradeModel.getValueAt(i,0) + "," + gradeModel.getValueAt(i,1) + "," + gradeModel.getValueAt(i,2) + "," + gradeModel.getValueAt(i,3) + "," + gradeModel.getValueAt(i,4));
                    }
                } catch (Exception ex) { JOptionPane.showMessageDialog(dlg, "Export failed: " + ex.getMessage()); }
            }
        });
        // CSV import button
        JButton importBtn = new JButton("Import Grades from CSV");
        importBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                java.io.File file = fc.getSelectedFile();
                try (java.util.Scanner sc = new java.util.Scanner(file)) {
                    sc.nextLine(); // skip header
                    int row = 0;
                    while (sc.hasNextLine() && row < gradeModel.getRowCount()) {
                        String[] vals = sc.nextLine().split(",");
                        gradeModel.setValueAt(vals[1], row, 1);
                        gradeModel.setValueAt(vals[2], row, 2);
                        gradeModel.setValueAt(vals[3], row, 3);
                        gradeModel.setValueAt(vals[4], row, 4);
                        row++;
                    }
                } catch (Exception ex) { JOptionPane.showMessageDialog(dlg, "Import failed: " + ex.getMessage()); }
            }
        });
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(saveBtn); btnPanel.add(statsBtn); btnPanel.add(exportBtn); btnPanel.add(importBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        dlg.add(panel, BorderLayout.CENTER);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }
}