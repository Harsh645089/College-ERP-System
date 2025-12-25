package student;

import types.SectionRow;
import domain.UserSession;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Vector;
import java.util.List;

public class CourseCatalogPanel extends JPanel {

    private final UserSession session;
    private final JLabel statusLabel;

    private DefaultTableModel tableModel;
    private JTable catalogTable;

    // Use the API interface
    private final StudentApi studentApi = new MockStudentApi();

    public CourseCatalogPanel(UserSession session, JLabel statusLabel) {
        this.session = session;
        this.statusLabel = statusLabel;
        setLayout(new BorderLayout(10, 10));

        setupCatalogTable();
        add(createControlPanel(), BorderLayout.SOUTH);

        loadCourseCatalog();
    }

    private void setupCatalogTable() {
        Vector<String> columns = new Vector<>();
        columns.add("Code");
        columns.add("Title");
        columns.add("Credits");
        columns.add("Seats Avail.");
        columns.add("Day/Time");
        columns.add("Instructor");
        columns.add("Section ID"); // Hidden

        tableModel = new DefaultTableModel(columns, 0);
        catalogTable = new JTable(tableModel);
        catalogTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Hide the Section ID column (index 6)
        catalogTable.getColumnModel().getColumn(6).setMaxWidth(0);
        catalogTable.getColumnModel().getColumn(6).setMinWidth(0);
        catalogTable.getColumnModel().getColumn(6).setPreferredWidth(0);

        add(new JScrollPane(catalogTable), BorderLayout.CENTER);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JButton registerButton = new JButton("Register for Selected Section");

        registerButton.addActionListener(e -> attemptRegistration());

        panel.add(registerButton);
        return panel;
    }

    // --- Business Logic ---

    private void loadCourseCatalog() {
        tableModel.setRowCount(0);
        try {
            // Placeholder semester/year (real app would check system settings)
            List<SectionRow> sections = studentApi.getCourseCatalog("Fall", 2025);

            for (SectionRow row : sections) {
                Vector<Object> data = new Vector<>();
                data.add(row.courseCode);
                data.add(row.courseTitle);
                data.add(row.credits);
                data.add(row.capacity);
                data.add(row.dayTime);
                data.add(row.instructorName);
                data.add(row.sectionId);
                tableModel.addRow(data);
            }
            statusLabel.setText("Catalog loaded. Showing " + sections.size() + " available sections.");
        } catch (Exception ex) {
            statusLabel.setText("Error loading catalog: " + ex.getMessage());
        }
    }

    private void attemptRegistration() {
        int selectedRow = catalogTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a section to register.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int sectionId = (Integer) catalogTable.getValueAt(selectedRow, 6);
        String courseCode = (String) catalogTable.getValueAt(selectedRow, 0);

        try {
            studentApi.registerSection(session.getUserId(), sectionId);

            JOptionPane.showMessageDialog(this, "Successfully registered for " + courseCode + "!", "Success", JOptionPane.INFORMATION_MESSAGE);
            loadCourseCatalog(); // Refresh the list

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Registration Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}