package auth;

import login.DatabaseConfig;
import java.sql.*;
import java.io.*;
import java.util.*;

/**
 * Centralized password management service that syncs changes across MockAuthService and DB.
 * Handles password updates, validation, and prevents old password reuse.
 */
public class PasswordManager {
    private static final String CREDENTIALS_FILE = "user_credentials.csv";
    private static final MockAuthService mockAuth = new MockAuthService();

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {}
    }

    /**
     * Update password for a user in both DB and CSV.
     * Returns true if successful, false otherwise.
     */
    public static boolean updatePassword(String username, String newPassword) {
        try {
            // Update in MockAuthService (CSV)
            boolean csvUpdated = mockAuth.updatePassword(username, newPassword);
            if (!csvUpdated) {
                System.err.println("Failed to update password in CSV for user: " + username);
                return false;
            }

            // Update in Database
            boolean dbUpdated = updatePasswordInDB(username, newPassword);
            if (!dbUpdated) {
                System.err.println("Failed to update password in DB for user: " + username);
                return false;
            }

            System.out.println("Password updated successfully for user: " + username);
            return true;

        } catch (Exception ex) {
            System.err.println("Error updating password: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Update password in the database.
     */
    private static boolean updatePasswordInDB(String username, String newPassword) {
        final String updateSql = "UPDATE users SET password_hash = ? WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {

            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            int updated = stmt.executeUpdate();
            return updated > 0;

        } catch (SQLException ex) {
            System.err.println("DB error updating password: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Verify that a password is valid for a user (used by login to prevent old passwords).
     * Returns true if password matches current stored password.
     */
    public static boolean verifyPassword(String username, String password) {
        // Check in MockAuthService first (CSV)
        MockAuthService.UserRecord record = mockAuth.getUserRecord(username);
        if (record != null && record.currentPassword.equals(password)) {
            return true;
        }

        // Check in Database
        return verifyPasswordInDB(username, password);
    }

    /**
     * Verify password against database.
     */
    private static boolean verifyPasswordInDB(String username, String password) {
        final String sql = "SELECT password_hash FROM users WHERE username = ? LIMIT 1";

        try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password_hash");
                    return password.equals(stored);
                }
            }
        } catch (SQLException ex) {
            System.err.println("DB error verifying password: " + ex.getMessage());
        }

        return false;
    }

    /**
     * Get current password for a user (used in change password flows).
     */
    public static String getCurrentPassword(String username) {
        MockAuthService.UserRecord record = mockAuth.getUserRecord(username);
        if (record != null) {
            return record.currentPassword;
        }

        // Check DB
        final String sql = "SELECT password_hash FROM users WHERE username = ? LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password_hash");
                }
            }
        } catch (SQLException ignored) {}

        return null;
    }
}
