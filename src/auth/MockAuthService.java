package auth;

import domain.UserSession;
import java.util.HashMap;
import java.util.Map;
import java.io.*;

/**
 * Mock implementation of AuthService using file I/O for persistent storage of credentials.
 */
public class MockAuthService implements AuthService {

    // File used for persistent storage.
    private static final String DATA_FILE = "user_credentials.csv";
    
    // In-memory storage of records, loaded from file.
    private static final Map<String, UserRecord> userDatabase = new HashMap<>();

    static {
        loadCredentialsFromFile();
    }
    
    public static class UserRecord {
        public final String userId;  // Changed to String to support both numeric and alphanumeric IDs
        public final String username;
        public final String role;
        public String currentPassword;
        public final String securityIdentity; 
        
        UserRecord(String userId, String username, String role, String currentPassword, String securityIdentity) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.currentPassword = currentPassword;
            this.securityIdentity = securityIdentity;
        }
        
        public String toCsvLine() {
            return String.join(",", 
                this.username, 
                String.valueOf(this.userId), 
                this.role, 
                this.currentPassword, 
                this.securityIdentity
            );
        }
    }

    /**
     * Load credentials from file - ALWAYS reload to ensure latest passwords are used
     */
    private static synchronized void loadCredentialsFromFile() {
        userDatabase.clear();
        
        try (BufferedReader br = new BufferedReader(new FileReader(DATA_FILE))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length == 5) {
                    UserRecord r = new UserRecord(
                        p[1].trim(),  // ID as String (handles both "100" and "INS001")
                        p[0].trim(),
                        p[2].trim(),
                        p[3].trim(),
                        p[4].trim()
                    );
                    userDatabase.put(r.username, r);
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("CRITICAL: Credentials file not found. Creating default data.");
            createDefaultDataAndSave();
        } catch (Exception e) {
            System.err.println("Error reading credentials file: " + e.getMessage());
        }
    }
    
    private static void createDefaultDataAndSave() {
        // Create default users matching seeded students/instructors using the pattern: firstName + id
        // IDs can now be String (both numeric and alphanumeric)
        userDatabase.put("admin", new UserRecord("100", "admin", "Admin", "pass", "ADMIN"));
        userDatabase.put("johns001", new UserRecord("301", "johns001", "Student", "pass", "S001"));
        userDatabase.put("sarahs002", new UserRecord("302", "sarahs002", "Student", "pass", "S002"));
        userDatabase.put("michaelS003", new UserRecord("303", "michaels003", "Student", "pass", "S003"));
        userDatabase.put("emilys004", new UserRecord("304", "emilys004", "Student", "pass", "S004"));
        userDatabase.put("avins001", new UserRecord("INS001", "avins001", "Instructor", "pass", "INS001"));
        saveCredentialsToFile();
    }
    
    private static void saveCredentialsToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(DATA_FILE))) {
            bw.write("username,id,role,password,security_identity\n");
            for (UserRecord r : userDatabase.values()) {
                bw.write(r.toCsvLine() + "\n");
            }
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to save credentials: " + e.getMessage());
        }
    }

    @Override
    public UserSession login(String username, String password) throws Exception {
        UserRecord record = userDatabase.get(username);

        if (record != null && record.currentPassword.equals(password)) {

            // Normalize role
            String role = record.role == null ? "Student" : record.role.trim();
            if (role.equalsIgnoreCase("superadmin") || role.equalsIgnoreCase("super admin")) {
                role = "SUPERADMIN";
            } else if (role.equalsIgnoreCase("admin")) {
                role = "Admin";
            } else if (role.equalsIgnoreCase("instructor") || role.equalsIgnoreCase("prof")) {
                role = "Instructor";
            } else {
                role = "Student";
            }

            // A simple token to make dashboards accept authentication
            String token = "tok-" + System.currentTimeMillis();

            // Convert userId String to int (parse numeric part if alphanumeric)
            int userIdInt = 0;
            try {
                // Try direct parse first
                userIdInt = Integer.parseInt(record.userId);
            } catch (NumberFormatException e) {
                // For alphanumeric IDs like "INS001", extract digits or use a hash
                for (char c : record.userId.toCharArray()) {
                    if (Character.isDigit(c)) {
                        userIdInt = userIdInt * 10 + (c - '0');
                    }
                }
                // If still 0, compute a stable hash
                if (userIdInt == 0) {
                    userIdInt = Math.abs(record.userId.hashCode()) % 100000;
                }
            }

            return new UserSession(userIdInt, record.username, role, token);
        }

        throw new Exception("Invalid username or password.");
    }

    // RETURNS the user record (used in forgot password flow)
    public UserRecord getUserRecord(String username) {
        return userDatabase.get(username);
    }

    // New method required by LoginWindow
    public boolean checkSecurityAnswer(String username, String answer) {
        UserRecord r = userDatabase.get(username);
        if (r == null) return false;

        String expected = "IIITD@" + r.securityIdentity;
        return expected.equalsIgnoreCase(answer);
    }

    // Update password
    public boolean updatePassword(String username, String newPass) {
        UserRecord r = userDatabase.get(username);
        if (r == null) return false;

        r.currentPassword = newPass;
        saveCredentialsToFile();
        return true;
    }
}
