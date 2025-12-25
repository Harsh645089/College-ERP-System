package auth;

import domain.UserSession;

/** Adapter so DBAuthService can be used where AuthService is expected. Falls back to MockAuthService if DB is unavailable. */
public class DBAuthAdapter implements AuthService {
    private final DBAuthService db = new DBAuthService();

    @Override
    public UserSession login(String username, String password) throws Exception {
        try {
            UserSession s = db.authenticate(username, password);
            if (s == null) throw new Exception("Invalid username or password.");
            return s;
        } catch (Exception dbEx) {
            // If DB auth fails (e.g., missing JDBC driver), fallback to mock file-based auth
            try {
                MockAuthService mock = new MockAuthService();
                return mock.login(username, password);
            } catch (Exception mockEx) {
                // propagate the original DB exception if mock also fails
                throw dbEx;
            }
        }
    }
}
