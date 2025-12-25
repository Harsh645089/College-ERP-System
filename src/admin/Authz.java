package admin;

import domain.UserSession;

/** Small authorization helper */
public class Authz {
    public static void requireAnyRole(UserSession s, String... roles) {
        if (s == null || !s.isAuthenticated()) {
            throw new SecurityException("Unauthenticated");
        }
        for (String r : roles) {
            if (s.hasRole(r)) return;
        }
        throw new SecurityException("Requires role: " + String.join(",", roles));
    }

    public static boolean hasAnyRole(UserSession s, String... roles) {
        if (s == null) return false;
        for (String r : roles) if (s.hasRole(r)) return true;
        return false;
    }
}
