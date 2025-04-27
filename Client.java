import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.prefs.Preferences;

public class Client {
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private JPanel loginPanel, registerPanel, chatPanel;
    private JTextField loginUserField, loginServerField;
    private JPasswordField loginPassField;
    private JTextField regUserField, regServerField;
    private JPasswordField regPassField, regConfirmPassField;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton loginButton, registerButton, sendButton;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private RSA rsa;
    private String username;
    private Preferences prefs = Preferences.userNodeForPackage(Client.class);

    public Client() {
        initializeGUI();
    }

    private void initializeGUI() {
        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);

        tabbedPane = new JTabbedPane();
        createLoginPanel();
        createRegisterPanel();
        createChatPanel();

        frame.add(tabbedPane, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Server field
        gbc.gridx = 0; gbc.gridy = 0;
        loginPanel.add(new JLabel("Server:"), gbc);
        gbc.gridx = 1;
        loginServerField = new JTextField(prefs.get("lastServer", "localhost"), 20);
        loginPanel.add(loginServerField, gbc);

        // Username field
        gbc.gridx = 0; gbc.gridy = 1;
        loginPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        loginUserField = new JTextField(20);
        loginPanel.add(loginUserField, gbc);

        // Password field
        gbc.gridx = 0; gbc.gridy = 2;
        loginPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        loginPassField = new JPasswordField(20);
        loginPanel.add(loginPassField, gbc);

        // Login button
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        loginButton = new JButton("Login");
        loginButton.addActionListener(e -> attemptLogin());
        loginPanel.add(loginButton, gbc);

        tabbedPane.addTab("Login", loginPanel);
    }

    private void createRegisterPanel() {
        registerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Server field
        gbc.gridx = 0; gbc.gridy = 0;
        registerPanel.add(new JLabel("Server:"), gbc);
        gbc.gridx = 1;
        regServerField = new JTextField(prefs.get("lastServer", "localhost"), 20);
        registerPanel.add(regServerField, gbc);

        // Username field
        gbc.gridx = 0; gbc.gridy = 1;
        registerPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        regUserField = new JTextField(20);
        registerPanel.add(regUserField, gbc);

        // Password field
        gbc.gridx = 0; gbc.gridy = 2;
        registerPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        regPassField = new JPasswordField(20);
        registerPanel.add(regPassField, gbc);

        // Confirm password field
        gbc.gridx = 0; gbc.gridy = 3;
        registerPanel.add(new JLabel("Confirm Password:"), gbc);
        gbc.gridx = 1;
        regConfirmPassField = new JPasswordField(20);
        registerPanel.add(regConfirmPassField, gbc);

        // Register button
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        registerButton = new JButton("Register");
        registerButton.addActionListener(e -> attemptRegistration());
        registerPanel.add(registerButton, gbc);

        tabbedPane.addTab("Register", registerPanel);
    }

    private void createChatPanel() {
        chatPanel = new JPanel(new BorderLayout());

        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setEnabled(false);
        messageField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
    }

    private void attemptLogin() {
        String server = loginServerField.getText().trim();
        username = loginUserField.getText().trim();
        String password = new String(loginPassField.getPassword()).trim();

        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields");
            return;
        }

        prefs.put("lastServer", server);

        try {
            connectToServer(server, "LOGIN", username + ":" + password);
        } catch (Exception e) {
            showError("Login failed: " + e.getMessage());
            resetConnection();
        }
    }

    private void attemptRegistration() {
        String server = regServerField.getText().trim();
        String username = regUserField.getText().trim();
        String password = new String(regPassField.getPassword()).trim();
        String confirm = new String(regConfirmPassField.getPassword()).trim();

        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields");
            return;
        }

        if (!password.equals(confirm)) {
            showError("Passwords do not match");
            return;
        }

        if (password.length() < 8) {
            showError("Password must be at least 8 characters");
            return;
        }

        prefs.put("lastServer", server);

        try {
            connectToServer(server, "REGISTER", username + ":" + password);
            showMessage("Registration successful! Please login.");
            tabbedPane.setSelectedIndex(0);
        } catch (Exception e) {
            showError("Registration failed: " + e.getMessage());
        } finally {
            resetConnection();
        }
    }

    private void connectToServer(String server, String action, String data) throws IOException {
        socket = new Socket(server, 12345);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        rsa = new RSA();

        // Key exchange
        out.println(rsa.getPublicKey());
        String serverKey = in.readLine();
        rsa.setOtherPublicKey(serverKey);

        // Send action and data
        out.println(action);
        out.println(rsa.encrypt(data));

        // Check response
        String response = in.readLine();
        if (action.equals("LOGIN")) {
            if (!"LOGIN_SUCCESS".equals(response)) {
                throw new IOException("Invalid username or password");
            }

            // Switch to chat view
            tabbedPane.removeAll();
            frame.getContentPane().remove(tabbedPane);
            frame.add(chatPanel, BorderLayout.CENTER);
            frame.revalidate();
            frame.repaint();

            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            chatArea.append("Connected as " + username + "\n");

            // Start message listener
            new Thread(this::listenForMessages).start();
        } else if (action.equals("REGISTER")) {
            if (!"REGISTER_SUCCESS".equals(response)) {
                throw new IOException(response);
            }
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                String decrypted = rsa.decrypt(message);
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(decrypted + "\n");
                });
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Disconnected from server: " + e.getMessage() + "\n");
                resetConnection();
            });
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        try {
            out.println(rsa.encrypt(message));
            chatArea.append("You: " + message + "\n");
            messageField.setText("");
        } catch (IOException e) {
            showError("Failed to send message: " + e.getMessage());
        }
    }

    private void resetConnection() {
        try {
            if (socket != null) socket.close();
            if (out != null) out.close();
            if (in != null) in.close();

            frame.remove(chatPanel);
            tabbedPane = new JTabbedPane();
            createLoginPanel();
            createRegisterPanel();
            createChatPanel();
            frame.add(tabbedPane, BorderLayout.CENTER);
            frame.revalidate();
            frame.repaint();

            messageField.setEnabled(false);
            sendButton.setEnabled(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(frame, message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}