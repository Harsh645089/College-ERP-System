package login;

import javax.swing.*;
import javax.swing.SwingUtilities;

public class UniversityERPApp {

    public static void main(String[] args) {
        
        // Run all database migrations before any DAO/service queries execute
        System.out.println("Application starting...");
        DBMigration.ensureSchemaUpToDate();

        // Set Look and Feel (e.g., FlatLaf for a modern look)
        try {
            // com.formdev.flatlaf.FlatLightLaf.setup();
            // Uncomment the line above if using FlatLaf
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF.");
        }

        SwingUtilities.invokeLater(() -> {
            LoginWindow login = new LoginWindow(); // Launch the login screen

            // --- CHANGE MADE HERE ---
            // Force the JFrame to open in the maximized state (full screen)
            login.setExtendedState(JFrame.MAXIMIZED_BOTH);
            // ------------------------

            login.setVisible(true);
        });
    }
}