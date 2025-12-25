package admin;

import javax.swing.*;
import java.awt.*;

/**
 * Panel to display key statistics and recent activity for the Admin Dashboard.
 */
public class DashboardOverviewPanel extends JPanel {

    public DashboardOverviewPanel() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 1. Statistics Cards (North)
        add(createStatsPanel(), BorderLayout.NORTH);

        // 2. Recent Activity (Center)
        add(createRecentActivityPanel(), BorderLayout.CENTER);
    }

    private JPanel createStatsPanel() {
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 15, 0)); // 1 row, 4 columns

        // Mock Data for the cards (matching the image)
        statsPanel.add(createStatCard("Total Students", "2", new Color(40, 100, 200)));
        statsPanel.add(createStatCard("Faculty Members", "85", new Color(100, 40, 200)));
        statsPanel.add(createStatCard("Active Courses", "46", new Color(200, 100, 40)));
        // Note: Attendance Rate uses a different color/format in the image
        statsPanel.add(createStatCard("Attendance Rate", "93.6%", new Color(34, 139, 34)));

        return statsPanel;
    }

    private JPanel createStatCard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        card.add(titleLabel, BorderLayout.NORTH);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        valueLabel.setForeground(color); // Match the color highlight
        card.add(valueLabel, BorderLayout.CENTER);

        // Add icons or other visual elements here if desired (using ImageIcon)

        return card;
    }

    private JPanel createRecentActivityPanel() {
        JPanel activityPanel = new JPanel(new BorderLayout());
        JLabel header = new JLabel("Recent Activity");
        header.setFont(new Font("SansSerif", Font.BOLD, 16));
        header.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Mock data from the image
        JTextArea activityLog = new JTextArea();
        activityLog.setEditable(false);
        activityLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        activityLog.setText(
                "[Nov 18, 01:25] ADMIN (ADMIN) - User Login: Admin accessed dashboard\n" +
                        "[Nov 18, 01:23] STUDENT (STUDENT) - User Login: Accessed student Dashboard\n" +
                        "[Nov 17, 10:00] ADMIN (SYSTEM) - Maintenance Mode Toggled OFF\n" +
                        "[Nov 17, 09:55] STUDENT (12345) - Registered for CS400 (Advanced Algorithms)"
        );

        activityPanel.add(header, BorderLayout.NORTH);
        activityPanel.add(new JScrollPane(activityLog), BorderLayout.CENTER);

        return activityPanel;
    }
}