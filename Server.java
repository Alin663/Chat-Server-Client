import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private JFrame frame;
    private JTextArea logArea;
    private JButton startButton, stopButton;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private Map<String, String> userDatabase = new ConcurrentHashMap<>();
    private BufferedWriter chatLogger;
    private SimpleDateFormat timestampFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss] ");

    public Server() {
        initializeGUI();
        loadUserDatabase();
        setupLogger();
    }

    private void initializeGUI() {
        frame = new JFrame("Chat Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        JPanel buttonPanel = new JPanel();
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    private void setupLogger() {
        try {
            new File("logs").mkdirs();
            String logFileName = "logs/server_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".log";
            chatLogger = new BufferedWriter(new FileWriter(logFileName));
            log("Chat logging started in " + logFileName);
        } catch (IOException e) {
            log("Failed to initialize chat logger: " + e.getMessage());
        }
    }

    private void loadUserDatabase() {
        File dbFile = new File("userdb.dat");
        if (dbFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dbFile))) {
                userDatabase = (ConcurrentHashMap<String, String>) ois.readObject();
                log("Loaded " + userDatabase.size() + " users from database");
            } catch (Exception e) {
                log("Error loading user database: " + e.getMessage());
            }
        } else {
            userDatabase.put("admin", "admin123");
            log("Created new user database with default admin account");
        }
    }

    private void saveUserDatabase() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("userdb.dat"))) {
            oos.writeObject(userDatabase);
            log("User database saved with " + userDatabase.size() + " users");
        } catch (IOException e) {
            log("Error saving user database: " + e.getMessage());
        }
    }

    private void log(String message) {
        String timestamped = timestampFormat.format(new Date()) + message;
        SwingUtilities.invokeLater(() -> {
            logArea.append(timestamped + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });

        try {
            if (chatLogger != null) {
                chatLogger.write(timestamped + "\n");
                chatLogger.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(12345);
            threadPool = Executors.newCachedThreadPool();

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            log("Server started on port 12345");

            threadPool.execute(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(socket);
                        threadPool.execute(handler);
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            log("Error accepting connection: " + e.getMessage());
                        }
                    }
                }
            });
        } catch (IOException e) {
            log("Failed to start server: " + e.getMessage());
        }
    }

    private void stopServer() {
        try {
            for (ClientHandler client : clients.values()) {
                client.disconnect();
            }
            clients.clear();

            serverSocket.close();
            threadPool.shutdown();
            saveUserDatabase();

            if (chatLogger != null) {
                chatLogger.close();
            }

            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            log("Server stopped");
        } catch (IOException e) {
            log("Error stopping server: " + e.getMessage());
        }
    }

    private void broadcast(String message, ClientHandler sender) {
        log("Broadcasting: " + message);
        for (ClientHandler client : clients.values()) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private final RSA rsa = new RSA();
        private String username;
        private boolean authenticated = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Key exchange
                out.println(rsa.getPublicKey());
                String clientKey = in.readLine();
                rsa.setOtherPublicKey(clientKey);

                // Authentication loop
                while (!authenticated) {
                    String action = in.readLine();
                    if (action == null) break;

                    switch (action) {
                        case "LOGIN":
                            handleLogin();
                            break;
                        case "REGISTER":
                            handleRegistration();
                            break;
                        default:
                            out.println("INVALID_ACTION");
                    }
                }

                // Main message loop
                while (authenticated) {
                    String encrypted = in.readLine();
                    if (encrypted == null) break;

                    String message = rsa.decrypt(encrypted);
                    log(username + ": " + message);
                    broadcast(username + ": " + message, this);
                }

            } catch (Exception e) {
                log("Client error: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void handleLogin() throws IOException {
            String encrypted = in.readLine();
            String credentials = rsa.decrypt(encrypted);
            String[] parts = credentials.split(":", 2);

            if (parts.length == 2 && userDatabase.containsKey(parts[0]) &&
                    userDatabase.get(parts[0]).equals(parts[1])) {

                username = parts[0];
                authenticated = true;
                clients.put(username, this);
                out.println("LOGIN_SUCCESS");
                log(username + " logged in successfully");
                broadcast(username + " joined the chat", this);
            } else {
                out.println("LOGIN_FAILED");
                throw new IOException("Authentication failed");
            }
        }

        private void handleRegistration() throws IOException {
            String encrypted = in.readLine();
            String credentials = rsa.decrypt(encrypted);
            String[] parts = credentials.split(":", 2);

            if (parts.length != 2) {
                out.println("REGISTER_INVALID_FORMAT");
                return;
            }

            synchronized (userDatabase) {
                if (userDatabase.containsKey(parts[0])) {
                    out.println("REGISTER_USER_EXISTS");
                    return;
                }

                if (parts[1].length() < 8) {
                    out.println("REGISTER_PASSWORD_TOO_SHORT");
                    return;
                }

                userDatabase.put(parts[0], parts[1]);
                out.println("REGISTER_SUCCESS");
                log("New user registered: " + parts[0]);
            }
        }

        public void sendMessage(String message) {
            try {
                out.println(rsa.encrypt(message));
            } catch (IOException e) {
                log("Failed to send message to " + username + ": " + e.getMessage());
            }
        }

        public void disconnect() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }

                if (authenticated && username != null) {
                    clients.remove(username);
                    broadcast(username + " left the chat", this);
                    log(username + " disconnected");
                }
            } catch (IOException e) {
                log("Error disconnecting client: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Server::new);
    }
}