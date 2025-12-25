package auth;

import domain.UserSession;

public interface AuthService {

    /**
     * Attempts to log a user in with the provided credentials.
     * * @param username The username provided by the user.
     * @param password The raw password provided by the user.
     * @return A UserSession object containing user details (ID, username, role) if authentication succeeds.
     * @throws Exception if authentication fails (e.g., bad credentials, account inactive).
     */
    UserSession login(String username, String password) throws Exception;
}