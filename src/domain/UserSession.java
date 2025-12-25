package domain;

/**
 * Represents a logged-in user's session.
 * 
 * Fully compatible with:
 * - Your old LoginWindow
 * - MockAuthService login(username, password)
 * - Dashboards that check authentication
 * - DAOs that expect getUserId() → int
 */
public class UserSession {

    private final int userId;          // required by your DAOs
    private final String username;
    private final String role;
    private final String authToken;    // NEW: used to validate authenticated session

    /** OLD Constructor (legacy compatibility) */
    public UserSession(int userId, String username, String role) {
        this(userId, username, role, "");
    }

    /** NEW Constructor with token (used by MockAuthService) */
    public UserSession(int userId, String username, String role, String authToken) {
        this.userId = userId;
        this.username = username == null ? "" : username;
        this.role = role == null ? "" : role;
        this.authToken = authToken == null ? "" : authToken;
    }

    /** Required by Admin / Instructor / Student dashboards */
    public boolean isAuthenticated() {
        return authToken != null && !authToken.trim().isEmpty();
    }

    /** Getters (same as before) */
    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public boolean hasRole(String r) {
        return this.role != null && this.role.equalsIgnoreCase(r);
    }

    public boolean hasAnyRole(String... roles) {
        if (this.role == null) return false;
        for (String r : roles) {
            if (this.role.equalsIgnoreCase(r)) return true;
        }
        return false;
    }


    public boolean isRole(String targetRole) {
        return this.role.equalsIgnoreCase(targetRole);
    }

    /** NEW: allow dashboards to inspect session token */
    public String getToken() {
        return authToken;
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", token=" + (isAuthenticated() ? "[SET]" : "[EMPTY]") +
                '}';
    }
}
