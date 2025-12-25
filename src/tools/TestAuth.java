package tools;

import auth.DBAuthAdapter;
import auth.MockAuthService;
import domain.UserSession;

public class TestAuth {
    public static void main(String[] args) throws Exception {
        DBAuthAdapter dba = new DBAuthAdapter();
        try {
            UserSession s = dba.login("inst", "123");
            System.out.println("DBAuthAdapter login OK: " + s.getUsername() + " role=" + s.getRole());
        } catch (Exception ex) {
            System.out.println("DBAuthAdapter login failed: " + ex.getMessage());
            // try mock directly
            MockAuthService mock = new MockAuthService();
            UserSession m = mock.login("inst", "123");
            System.out.println("Mock login OK: " + m.getUsername() + " role=" + m.getRole());
        }
    }
}
