package auth;

import domain.UserSession;
import login.DatabaseConfig;

import java.sql.*;
import java.io.*;
import java.util.*;

/**
 * Authentication service that reads from users table in erp.db.
 * Works with your OLD login window and updated UserSession.
 */
public class DBAuthService {

    private static final String CREDENTIALS_FILE = "user_credentials.csv";

    public DBAuthService() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC not present: " + e.getMessage());
        }
        // Ensure per-student and per-instructor user accounts exist in `users` table.
        try {
            syncUsersFromPeople();
        } catch (Exception ex) {
            System.err.println("Warning: failed to sync users from people: " + ex.getMessage());
        }
    }

    /**
     * Create or update user accounts for every student and instructor.
     * Username = firstName + id (first token of the name + their id), password = 'pass'.
     */
    private void syncUsersFromPeople() {
        try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl())) {
            // Ensure users table exists (already used elsewhere, but double-check)
            try (Statement s = conn.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT, role TEXT NOT NULL, email TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            }

            // Prepare upsert statement using SQLite ON CONFLICT
                // Do not overwrite existing password_hash on conflict — preserve user's password if already set
                String upsert = "INSERT INTO users (username, password_hash, role, email) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(username) DO UPDATE SET role=excluded.role, email=excluded.email";

            try (PreparedStatement up = conn.prepareStatement(upsert)) {
                // Students
                admin.dao.StudentDAO sdao = new admin.dao.StudentDAO();
                for (domain.Student st : sdao.listAll()) {
                    String first = (st.getName() == null || st.getName().isEmpty()) ? "user" : st.getName().split(" ")[0];
                    String username = (first + st.getId()).toLowerCase();
                    String pwd = "pass";
                    String role = "Student";
                    String email = st.getEmail() == null ? (username + "@example.com") : st.getEmail();
                    up.setString(1, username);
                    up.setString(2, pwd);
                    up.setString(3, role);
                    up.setString(4, email);
                    up.executeUpdate();
                }

                // Instructors
                admin.dao.InstructorDAO idao = new admin.dao.InstructorDAO();
                for (domain.Instructor ins : idao.listAll()) {
                    String first = (ins.getName() == null || ins.getName().isEmpty()) ? "instr" : ins.getName().split(" ")[0];
                    String username = (first + ins.getId()).toLowerCase();
                    String pwd = "pass";
                    String role = "Instructor";
                    String email = ins.getEmail() == null ? (username + "@example.com") : ins.getEmail();
                    up.setString(1, username);
                    up.setString(2, pwd);
                    up.setString(3, role);
                    up.setString(4, email);
                    up.executeUpdate();
                }
            }
            // Also create a user->person mapping table to deterministically map auth usernames
            try (Statement s = conn.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS user_person_map (username TEXT PRIMARY KEY, person_type TEXT, person_id TEXT)");
            }

            String upmap = "INSERT INTO user_person_map (username, person_type, person_id) VALUES (?, ?, ?) " +
                    "ON CONFLICT(username) DO UPDATE SET person_type=excluded.person_type, person_id=excluded.person_id";

            try (PreparedStatement mup = conn.prepareStatement(upmap)) {
                // Students
                admin.dao.StudentDAO sdao2 = new admin.dao.StudentDAO();
                for (domain.Student st : sdao2.listAll()) {
                    String first = (st.getName() == null || st.getName().isEmpty()) ? "user" : st.getName().split(" ")[0];
                    String username = (first + st.getId()).toLowerCase();
                    mup.setString(1, username);
                    mup.setString(2, "student");
                    mup.setString(3, st.getId());
                    mup.executeUpdate();
                }

                // Instructors
                admin.dao.InstructorDAO idao2 = new admin.dao.InstructorDAO();
                for (domain.Instructor ins2 : idao2.listAll()) {
                    String first = (ins2.getName() == null || ins2.getName().isEmpty()) ? "instr" : ins2.getName().split(" ")[0];
                    String username = (first + ins2.getId()).toLowerCase();
                    mup.setString(1, username);
                    mup.setString(2, "instructor");
                    mup.setString(3, ins2.getId());
                    mup.executeUpdate();
                }
            }
            // After syncing DB, also sync the CSV file so all credentials are in one place
            syncCredentialsToCSV();
        } catch (SQLException ex) {
            System.err.println("syncUsersFromPeople failed: " + ex.getMessage());
        }
    }

    /**
     * Write all students and instructors (plus admin) to user_credentials.csv.
     * This keeps the CSV file in sync with the DB whenever users are added/updated.
     */
    private void syncCredentialsToCSV() {
        try {
            List<String[]> records = new ArrayList<>();

            // Add admin
            records.add(new String[]{"admin", "100", "Admin", "pass", "ADMIN"});

            // Add students
            admin.dao.StudentDAO sdao = new admin.dao.StudentDAO();
            for (domain.Student st : sdao.listAll()) {
                String first = (st.getName() == null || st.getName().isEmpty()) ? "user" : st.getName().split(" ")[0];
                String username = (first + st.getId()).toLowerCase();
                records.add(new String[]{username, st.getId(), "Student", "pass", st.getId()});
            }

            // Add instructors
            admin.dao.InstructorDAO idao = new admin.dao.InstructorDAO();
            for (domain.Instructor ins : idao.listAll()) {
                String first = (ins.getName() == null || ins.getName().isEmpty()) ? "instr" : ins.getName().split(" ")[0];
                String username = (first + ins.getId()).toLowerCase();
                records.add(new String[]{username, ins.getId(), "Instructor", "pass", ins.getId()});
            }

            // Write to CSV using actual password_hash from users table when available
            try {
                try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl())) {
                    try (BufferedWriter bw = new BufferedWriter(new FileWriter(CREDENTIALS_FILE))) {
                        // write header explicitly
                        bw.write("username,id,role,password,security_identity\n");

                        for (String[] rec : records) {
                            String username = rec[0];
                            String pwd = "pass"; // default fallback
                            try (PreparedStatement p = conn.prepareStatement("SELECT password_hash FROM users WHERE username = ? LIMIT 1")) {
                                p.setString(1, username);
                                try (ResultSet rs = p.executeQuery()) {
                                    if (rs.next()) {
                                        String ph = rs.getString(1);
                                        if (ph != null && !ph.isEmpty()) pwd = ph;
                                    }
                                }
                            } catch (SQLException ignored) {}

                            // replace password field (index 3) with actual password
                            String[] outRec = new String[]{rec[0], rec[1], rec[2], pwd, rec[4]};
                            bw.write(String.join(",", outRec));
                            bw.newLine();
                        }
                    }
                }
            } catch (SQLException sqlEx) {
                System.err.println("Warning: failed to read password hashes from DB: " + sqlEx.getMessage());
                // fallback to writing records with default passwords
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(CREDENTIALS_FILE))) {
                    // write header first
                    bw.write("username,id,role,password,security_identity\n");
                    for (String[] rec : records) {
                        bw.write(String.join(",", rec));
                        bw.newLine();
                    }
                }
            }
            System.out.println("Synced " + (records.size() - 1) + " credentials to " + CREDENTIALS_FILE);
        } catch (IOException ex) {
            System.err.println("Warning: failed to sync credentials to CSV: " + ex.getMessage());
        }
    }

    /**
     * Change password for a user. Returns true if changed successfully.
     */
    public boolean changePassword(String username, String currentPassword, String newPassword) {
        final String selectSql = "SELECT password_hash FROM users WHERE username = ? LIMIT 1";
        final String updateSql = "UPDATE users SET password_hash = ? WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
             PreparedStatement sel = conn.prepareStatement(selectSql)) {

            sel.setString(1, username);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) return false; // user not found
                String stored = rs.getString("password_hash");
                if (!currentPassword.equals(stored)) return false; // current password mismatch
            }

            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setString(1, newPassword);
                upd.setString(2, username);
                int updated = upd.executeUpdate();
                boolean ok = updated > 0;
                if (ok) {
                    // ensure CSV is in sync with DB after password change
                    try {
                        syncCredentialsToCSV();
                    } catch (Exception ignored) {}
                }
                return ok;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Authenticate user from DB.
     * Returns UserSession on success, null on failure.
     */
    public UserSession authenticate(String username, String password) {

        final String sql = "SELECT id, username, password_hash, role FROM users WHERE username = ? LIMIT 1";

        try (Connection conn = DriverManager.getConnection(DatabaseConfig.getDatabaseUrl());
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    return null;  // user not found
                }

                int userId = rs.getInt("id");
                String storedPassword = rs.getString("password_hash");
                String role = rs.getString("role");

                // Since your DB stores password in plain text, compare directly
                if (!password.equals(storedPassword)) {
                    return null; // wrong password
                }

                // Normalize role
                if (role == null) role = "Student";
                role = role.trim();
                if (role.equalsIgnoreCase("superadmin") || role.equalsIgnoreCase("super admin")) {
                    role = "SUPERADMIN";
                } else if (role.equalsIgnoreCase("admin")) {
                    role = "Admin";
                } else if (role.equalsIgnoreCase("instructor") || role.equalsIgnoreCase("prof")) {
                    role = "Instructor";
                } else {
                    role = "Student";
                }

                // Provide a token so session.isAuthenticated() works
                String token = "dbtok-" + System.currentTimeMillis();

                // FIXED: pass userId as int
                return new UserSession(userId, username, role, token);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
