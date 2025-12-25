package login;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.Border;

import domain.UserSession;
import auth.MockAuthService;
import auth.MockAuthService.UserRecord;
import auth.DBAuthAdapter;
import auth.AuthService;

import admin.AdminDashboard;
import instructor.InstructorDashboard;
import student.StudentDashboard;
import student.services.StudentService;

public class LoginWindow extends JFrame {

    // --- Enhanced Styling Constants ---
    private static final Color HEADER_COLOR = new Color(23, 23, 23); // Pure black
    private static final Color DARK_GREY = new Color(45, 45, 45); // Dark grey
    private static final Color MEDIUM_GREY = new Color(80, 80, 80); // Medium grey
    private static final Color LIGHT_GREY = new Color(240, 240, 240); // Light grey background
    private static final Color ACCENT_GREY = new Color(100, 100, 100); // Accent grey
    private static final Color PLACEHOLDER_COLOR = new Color(150, 150, 150); // Placeholder text color
    private static final Color DIALOG_BG_COLOR = new Color(250, 250, 250); // Dialog background
    
    // --- Uniform Dialog Sizes ---
    private static final Dimension DIALOG_SIZE = new Dimension(600, 400); // Reduced and uniform size
    private static final Dimension FIELD_SIZE = new Dimension(500, 50); // Consistent field size
    
    // --- UI Components ---
    private final JTextField usernameField = new JTextField(25);
    private final JPasswordField passwordField = new JPasswordField(25);
    private final JButton loginButton = new JButton("Log In");
    private final JLabel messageLabel = new JLabel("");
    private final Font loginButtonFont = new Font("SansSerif", Font.BOLD, 18);
    
    private final JLabel forgotPassLabel = new JLabel(
        "<html><a href=\"#\" style=\"color: rgb(120, 120, 120); text-decoration: none; font-size: 14pt;\">Forgotten password?</a></html>"
    );

    // Use DB-backed auth for login; keep MockAuthService around for forgot-password utilities
    private final AuthService authService = new DBAuthAdapter();
    private final MockAuthService mockAuthService = new MockAuthService();

    public LoginWindow() {
        setTitle("University ERP System Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Set background color for the main frame
        getContentPane().setBackground(LIGHT_GREY);
        
        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createContentPanel(), BorderLayout.CENTER);

        setSize(1000, 700);
        setLocationRelativeTo(null);
        
        SwingUtilities.invokeLater(() -> this.getRootPane().requestFocusInWindow());
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setPreferredSize(new Dimension(getWidth(), 80));

        JLabel titleLabel = new JLabel("University ERP");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 36));

        headerPanel.add(titleLabel);
        return headerPanel;
    }

    private JPanel createContentPanel() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(LIGHT_GREY);

        JPanel loginBox = new JPanel();
        loginBox.setLayout(new BoxLayout(loginBox, BoxLayout.Y_AXIS));
        
        loginBox.setPreferredSize(new Dimension(580, 480)); 
        loginBox.setMaximumSize(new Dimension(580, 480));
        
        // Rounded border with shadow effect
        loginBox.setBorder(BorderFactory.createCompoundBorder(
            createRoundedBorder(20, MEDIUM_GREY, 2),
            BorderFactory.createEmptyBorder(50, 50, 50, 50) 
        )); 
        loginBox.setBackground(Color.WHITE);

        Dimension fieldSize = new Dimension(480, 55); 

        setupInputField(usernameField, "Username", fieldSize);
        setupInputField(passwordField, "Password", fieldSize);
        passwordField.setEchoChar((char) 0); 

        // Enhanced login button with rounded corners
        loginButton.setBackground(DARK_GREY);
        loginButton.setForeground(Color.WHITE);
        loginButton.setFont(loginButtonFont);
        loginButton.setMaximumSize(fieldSize);
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setBorder(createRoundedBorder(12, DARK_GREY, 0));
        loginButton.setFocusPainted(false);
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Add hover effect for login button
        loginButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                loginButton.setBackground(MEDIUM_GREY);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                loginButton.setBackground(DARK_GREY);
            }
        });

        messageLabel.setForeground(Color.RED);
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // --- Enhanced Forgot Password Panel ---
        forgotPassLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        forgotPassLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        JPanel forgotPassPanel = new JPanel();
        forgotPassPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5)); 
        forgotPassPanel.setBackground(Color.WHITE); 
        forgotPassPanel.setBorder(createRoundedBorder(8, new Color(200, 200, 200), 1)); 
        forgotPassPanel.setMaximumSize(new Dimension(300, 40)); 
        forgotPassPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        forgotPassPanel.add(forgotPassLabel);
        
        // Add hover effect for forgot password
        forgotPassPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                forgotPassPanel.setBackground(new Color(250, 250, 250));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                forgotPassPanel.setBackground(Color.WHITE);
            }
        });
        
        // Assemble content
        loginBox.add(Box.createVerticalStrut(20));
        loginBox.add(usernameField);
        loginBox.add(Box.createRigidArea(new Dimension(0, 15)));
        loginBox.add(passwordField);
        loginBox.add(Box.createRigidArea(new Dimension(0, 35))); 
        loginBox.add(loginButton);
        loginBox.add(Box.createRigidArea(new Dimension(0, 20))); 
        
        loginBox.add(forgotPassPanel); 
        loginBox.add(Box.createRigidArea(new Dimension(0, 30))); 
        loginBox.add(messageLabel);

        contentPanel.add(loginBox);

        loginButton.addActionListener(this::actionPerformed);
        passwordField.addActionListener(this::actionPerformed);
        
        forgotPassLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (forgotPassLabel.isEnabled()) {
                    forgotPasswordFlow();
                }
            }
        });

        return contentPanel;
    }

    private void setupInputField(JComponent field, String hint, Dimension size) {
        field.setMaximumSize(size);
        field.setFont(new Font("SansSerif", Font.PLAIN, 16));
        field.setAlignmentX(Component.CENTER_ALIGNMENT);
        field.setBorder(createRoundedBorder(10, ACCENT_GREY, 1));
        field.setOpaque(false);
        field.setBackground(new Color(250, 250, 250));

        if (field instanceof JTextField) {
            JTextField tf = (JTextField) field;
            tf.setText(hint);
            tf.setForeground(PLACEHOLDER_COLOR);
            
            tf.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    if (tf.getText().equals(hint) || tf.getText().isEmpty()) {
                        tf.setText("");
                        tf.setForeground(Color.BLACK);
                        if (tf instanceof JPasswordField) {
                            ((JPasswordField) tf).setEchoChar('•'); // Using bullet instead of asterisk
                        }
                    }
                    // Change border color on focus
                    field.setBorder(createRoundedBorder(10, DARK_GREY, 2));
                }

                @Override
                public void focusLost(FocusEvent e) {
                    if (tf.getText().isEmpty()) {
                        tf.setText(hint);
                        tf.setForeground(PLACEHOLDER_COLOR);
                        if (tf instanceof JPasswordField) {
                            ((JPasswordField) tf).setEchoChar((char) 0);
                        }
                    }
                    // Revert border color when focus lost
                    field.setBorder(createRoundedBorder(10, ACCENT_GREY, 1));
                }
            });
        }
    }
    
    /**
     * Creates a rounded border with specified radius, color and thickness
     */
    private Border createRoundedBorder(int radius, Color color, int thickness) {
        return new Border() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(thickness));
                g2.drawRoundRect(x + thickness/2, y + thickness/2, width - thickness, height - thickness, radius, radius);
                g2.dispose();
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(thickness + 2, thickness + 2, thickness + 2, thickness + 2);
            }

            @Override
            public boolean isBorderOpaque() {
                return true;
            }
        };
    }
    
    /** Enables or disables the login UI components. */
    private void setLoginUIEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        forgotPassLabel.setEnabled(enabled);
        // Dim the forgotten password text if disabled for visual consistency
        forgotPassLabel.setForeground(enabled ? new Color(120, 120, 120) : Color.LIGHT_GRAY);
    }
    
    private void forgotPasswordFlow() {
        // Create a custom username input dialog with consistent styling
        JDialog usernameDialog = createStyledDialog("Forgot Password", DIALOG_SIZE);
        
        JPanel contentPanel = new JPanel(new BorderLayout(20, 20));
        contentPanel.setBackground(DIALOG_BG_COLOR);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40)); // Reduced padding for smaller dialog
        
        JLabel instructionLabel = new JLabel("<html><center style='font-size: 16pt; color: #2D2D2D;'>Enter your username to reset your password</center></html>");
        instructionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JTextField usernameInput = new JTextField();
        setupDialogField(usernameInput, "Username", FIELD_SIZE);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setBackground(DIALOG_BG_COLOR);
        JButton nextButton = new JButton("Next");
        JButton cancelButton = new JButton("Cancel");
        
        styleDialogButton(nextButton);
        styleDialogButton(cancelButton);
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(nextButton);
        
        final String[] usernameResult = {null};
        
        nextButton.addActionListener(e -> {
            String username = usernameInput.getText().trim();
            if (username.isEmpty() || username.equals("Username")) {
                JOptionPane.showMessageDialog(usernameDialog, "Please enter a valid username.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            UserRecord record = mockAuthService.getUserRecord(username);
            if (record == null) {
                JOptionPane.showMessageDialog(usernameDialog, "User not found.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            usernameResult[0] = username;
            usernameDialog.dispose();
        });
        
        cancelButton.addActionListener(e -> usernameDialog.dispose());
        
        contentPanel.add(instructionLabel, BorderLayout.NORTH);
        contentPanel.add(usernameInput, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        usernameDialog.add(contentPanel);
        usernameDialog.setVisible(true);
        
        if (usernameResult[0] != null) {
            String securityAnswer = showSecurityQuestionDialog(this, usernameResult[0]);
            if (securityAnswer != null && !securityAnswer.trim().isEmpty()) {
                if (mockAuthService.checkSecurityAnswer(usernameResult[0], securityAnswer)) {
                        showNewPasswordDialog(this, usernameResult[0]);
                    } else {
                        JOptionPane.showMessageDialog(this, "Security answer incorrect. Password reset failed.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
            }
        }
    }
    
    /** Creates a consistently styled dialog */
    private JDialog createStyledDialog(String title, Dimension size) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setSize(size);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(DIALOG_BG_COLOR);
        return dialog;
    }
    
    /** Sets up dialog fields with consistent styling */
    private void setupDialogField(JTextField field, String hint, Dimension size) {
        field.setPreferredSize(size);
        field.setMaximumSize(size);
        field.setFont(new Font("SansSerif", Font.PLAIN, 16));
        field.setBorder(createRoundedBorder(12, ACCENT_GREY, 1));
        field.setBackground(Color.WHITE);
        field.setForeground(PLACEHOLDER_COLOR);
        field.setText(hint);
        
        field.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(hint)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
                field.setBorder(createRoundedBorder(12, DARK_GREY, 2));
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(hint);
                    field.setForeground(PLACEHOLDER_COLOR);
                }
                field.setBorder(createRoundedBorder(12, ACCENT_GREY, 1));
            }
        });
    }
    
    /** Shows a custom dialog for the security question. */
    private String showSecurityQuestionDialog(JFrame parent, String username) {
        JDialog dialog = createStyledDialog("Security Check", DIALOG_SIZE); // Same uniform size
        
        JPanel contentPanel = new JPanel(new BorderLayout(20, 20));
        contentPanel.setBackground(DIALOG_BG_COLOR);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40)); // Reduced padding

        JLabel titleLabel = new JLabel("<html><center style='font-size: 16pt; color: #2D2D2D; font-weight: bold;'>Security Verification</center></html>");
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Using the new security question
        JLabel questionLabel = new JLabel("What is your verification code?");
        questionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        questionLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        questionLabel.setForeground(new Color(80, 80, 80));

        JTextField answerField = new JTextField();
        setupDialogField(answerField, "Enter verification code", FIELD_SIZE);
        
        JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
        centerPanel.setBackground(DIALOG_BG_COLOR);
        centerPanel.add(questionLabel, BorderLayout.NORTH);
        centerPanel.add(answerField, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setBackground(DIALOG_BG_COLOR);
        JButton submitButton = new JButton("Verify Code");
        JButton cancelButton = new JButton("Cancel");
        
        styleDialogButton(submitButton);
        styleDialogButton(cancelButton);
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(submitButton);
        
        final String[] result = {null};
        
        submitButton.addActionListener(e -> {
            result[0] = answerField.getText().trim();
            if (result[0].isEmpty() || result[0].equals("Enter verification code")) {
                JOptionPane.showMessageDialog(dialog, "Please enter the verification code.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            dialog.dispose();
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        contentPanel.add(titleLabel, BorderLayout.NORTH);
        contentPanel.add(centerPanel, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(contentPanel);
        dialog.setVisible(true);
        
        return result[0];
    }
    
    /**
     * Styles dialog buttons with rounded corners and grey theme
     */
    private void styleDialogButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.BOLD, 14)); // Slightly smaller font for buttons
        button.setBackground(DARK_GREY);
        button.setForeground(Color.WHITE);
        button.setBorder(createRoundedBorder(10, DARK_GREY, 0));
        button.setPreferredSize(new Dimension(120, 40)); // Slightly smaller buttons
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(MEDIUM_GREY);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(DARK_GREY);
            }
        });
    }
    
    private void showNewPasswordDialog(JFrame parent, String username) {
        JDialog dialog = createStyledDialog("Reset Password", DIALOG_SIZE); // Same uniform size
        
        JPanel contentPanel = new JPanel(new BorderLayout(20, 20));
        contentPanel.setBackground(DIALOG_BG_COLOR);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40)); // Reduced padding

        JLabel titleLabel = new JLabel("<html><center style='font-size: 16pt; color: #2D2D2D; font-weight: bold;'>Create New Password</center></html>");
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JPanel passwordPanel = new JPanel(new GridLayout(3, 1, 12, 12)); // Reduced spacing
        passwordPanel.setBackground(DIALOG_BG_COLOR);
        
        // New Password Field
        JPanel newPassPanel = new JPanel(new BorderLayout(8, 5));
        newPassPanel.setBackground(DIALOG_BG_COLOR);
        JLabel newPassLabel = new JLabel("New Password:");
        newPassLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        newPassLabel.setForeground(DARK_GREY);
        JPasswordField newPass = new JPasswordField();
        setupPasswordField(newPass, "Enter new password");
        newPassPanel.add(newPassLabel, BorderLayout.NORTH);
        newPassPanel.add(newPass, BorderLayout.CENTER);
        
        // Confirm Password Field
        JPanel confirmPassPanel = new JPanel(new BorderLayout(8, 5));
        confirmPassPanel.setBackground(DIALOG_BG_COLOR);
        JLabel confirmPassLabel = new JLabel("Confirm Password:");
        confirmPassLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        confirmPassLabel.setForeground(DARK_GREY);
        JPasswordField confirmPass = new JPasswordField();
        setupPasswordField(confirmPass, "Confirm new password");
        confirmPassPanel.add(confirmPassLabel, BorderLayout.NORTH);
        confirmPassPanel.add(confirmPass, BorderLayout.CENTER);
        
        JLabel errorLabel = new JLabel(" ");
        errorLabel.setForeground(Color.RED);
        errorLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        passwordPanel.add(newPassPanel);
        passwordPanel.add(confirmPassPanel);
        passwordPanel.add(errorLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setBackground(DIALOG_BG_COLOR);
        JButton saveButton = new JButton("Save Password");
        JButton cancelButton = new JButton("Cancel");
        
        styleDialogButton(saveButton);
        styleDialogButton(cancelButton);
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        
        cancelButton.addActionListener(e -> dialog.dispose());

        saveButton.addActionListener(e -> {
            String newPassword = new String(newPass.getPassword());
            String confirmPassword = new String(confirmPass.getPassword());

            if (newPassword.isEmpty() || newPassword.equals("Enter new password") || 
                confirmPassword.isEmpty() || confirmPassword.equals("Confirm new password")) {
                errorLabel.setText("Password fields cannot be empty.");
            } else if (!newPassword.equals(confirmPassword)) {
                errorLabel.setText("Passwords do not match.");
            } else if (newPassword.length() < 4) {
                errorLabel.setText("Password must be at least 4 characters.");
            } else {
                // Use PasswordManager to sync updates across all auth providers
                if (auth.PasswordManager.updatePassword(username, newPassword)) { 
                    JOptionPane.showMessageDialog(parent, 
                        "Password successfully reset and saved! You can now log in using the new password.", 
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(parent, "Failed to update password for user.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        contentPanel.add(titleLabel, BorderLayout.NORTH);
        contentPanel.add(passwordPanel, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(contentPanel);
        dialog.setVisible(true);
    }
    
    /** Sets up password fields with consistent styling */
    private void setupPasswordField(JPasswordField field, String hint) {
        field.setPreferredSize(FIELD_SIZE);
        field.setMaximumSize(FIELD_SIZE);
        field.setFont(new Font("SansSerif", Font.PLAIN, 16));
        field.setBorder(createRoundedBorder(12, ACCENT_GREY, 1));
        field.setBackground(Color.WHITE);
        field.setForeground(PLACEHOLDER_COLOR);
        field.setEchoChar((char) 0);
        field.setText(hint);
        
        field.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (new String(field.getPassword()).equals(hint)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                    field.setEchoChar('•');
                }
                field.setBorder(createRoundedBorder(12, DARK_GREY, 2));
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (new String(field.getPassword()).isEmpty()) {
                    field.setText(hint);
                    field.setForeground(PLACEHOLDER_COLOR);
                    field.setEchoChar((char) 0);
                }
                field.setBorder(createRoundedBorder(12, ACCENT_GREY, 1));
            }
        });
    }
    
    private void attemptLogin() {
        String username = usernameField.getText().trim();
        char[] passwordChars = passwordField.getPassword();
        String password = new String(passwordChars);
        java.util.Arrays.fill(passwordChars, '\0');

        if (username.equals("Username") || password.equals("Password")) {
            messageLabel.setText("Please enter a valid username and password.");
            messageLabel.setForeground(Color.RED);
            return;
        }

        // --- START: Provide UI Feedback (Logging in...) ---
        setLoginUIEnabled(false);
        loginButton.setText("Logging in...");
        messageLabel.setText("Authenticating..."); // Show status message
        messageLabel.setForeground(DARK_GREY);
        // --- END: Provide UI Feedback ---

        // Run authentication off the EDT so UI remains responsive
        new Thread(() -> {
            try {
                UserSession session = authService.login(username, password);
                SwingUtilities.invokeLater(() -> {
                    if (session != null) {
                            try {
                                boolean opened = openDashboard(session);
                                if (!opened) {
                                    // dashboard failed to open (e.g., missing person record) -> re-enable UI
                                    setLoginUIEnabled(true);
                                    loginButton.setText("Log In");
                                }
                                // if opened == true, openDashboard disposed this login window
                            } catch (Throwable t) {
                                t.printStackTrace();
                                JOptionPane.showMessageDialog(this, "Failed to open dashboard: " + t.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                setLoginUIEnabled(true);
                                loginButton.setText("Log In");
                            }
                    } else {
                        setLoginUIEnabled(true);
                        loginButton.setText("Log In");
                        messageLabel.setText("Invalid username or password.");
                        messageLabel.setForeground(Color.RED);
                        passwordField.setText("");
                        setupInputField(passwordField, "Password", new Dimension(480, 55));
                    }
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() -> {
                    ex.printStackTrace();
                    setLoginUIEnabled(true);
                    loginButton.setText("Log In");
                    messageLabel.setText(ex.getMessage() == null ? "Login failed" : ex.getMessage());
                    messageLabel.setForeground(Color.RED);
                    passwordField.setText("");
                    setupInputField(passwordField, "Password", new Dimension(480, 55));
                });
            }
        }).start();
    }

    private boolean openDashboard(UserSession userSession) {
        JFrame dashboardFrame = null;
        String role = userSession.getRole();
        try {
            switch (role) {
                case "Admin":
                    dashboardFrame = new AdminDashboard(userSession);
                    break;
                case "Instructor":
                    // For instructors we attempt construction; if it fails we'll show an error and keep login window
                    dashboardFrame = new InstructorDashboard(userSession);
                    break;
                case "Student":
                    // Ensure student profile exists before closing login window
                    StudentService ss = new StudentService(userSession);
                    domain.Student prof = ss.getStudentProfile(userSession.getUsername());
                    if (prof == null) {
                        JOptionPane.showMessageDialog(this, "Invalid credentials or no student record found for this account.", "Login Error", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    dashboardFrame = new StudentDashboard(userSession);
                    break;
                default:
                    JOptionPane.showMessageDialog(this, "Error: Unknown user role.", "Login Error", JOptionPane.ERROR_MESSAGE);
                    return false;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to open dashboard: " + t.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // Only dispose login window after dashboard was created successfully
        this.dispose();
        dashboardFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        dashboardFrame.setVisible(true);
        return true;
    }

    private void actionPerformed(ActionEvent e) {
        attemptLogin();
    }
}
