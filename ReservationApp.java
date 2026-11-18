package com.example.reservation;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

public class ReservationApp {
    private static final String DB_URL = "jdbc:sqlite:reservation.db";
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 14);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Database.initialize();
                new LoginFrame().setVisible(true);
            } catch (Exception ex) {
                showError(null, "Could not start application: " + ex.getMessage());
            }
        });
    }

    static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    static class Database {
        static Connection connect() throws SQLException {
            return DriverManager.getConnection(DB_URL);
        }

        static void initialize() throws SQLException {
            try (Connection c = connect(); Statement s = c.createStatement()) {
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users(
                      username TEXT PRIMARY KEY, password_hash TEXT NOT NULL
                    )
                    """);
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS trains(
                      train_number INTEGER PRIMARY KEY, train_name TEXT NOT NULL
                    )
                    """);
                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS reservations(
                      pnr TEXT PRIMARY KEY, passenger_name TEXT NOT NULL,
                      train_number INTEGER NOT NULL, train_name TEXT NOT NULL,
                      class_type TEXT NOT NULL, journey_date TEXT NOT NULL,
                      source_station TEXT NOT NULL, destination_station TEXT NOT NULL,
                      booked_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            }
            seedData();
        }

        private static void seedData() throws SQLException {
            try (Connection c = connect()) {
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT OR IGNORE INTO users(username,password_hash) VALUES(?,?)")) {
                    p.setString(1, "admin");
                    p.setString(2, hashPassword("admin123"));
                    p.executeUpdate();
                }
                String[][] trains = {
                    {"12727", "Godavari Express"},
                    {"12760", "Charminar Express"},
                    {"12603", "Chennai Express"},
                    {"12723", "Telangana Express"},
                    {"17016", "Visakha Express"}
                };
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT OR IGNORE INTO trains(train_number,train_name) VALUES(?,?)")) {
                    for (String[] t : trains) {
                        p.setInt(1, Integer.parseInt(t[0]));
                        p.setString(2, t[1]);
                        p.addBatch();
                    }
                    p.executeBatch();
                }
            }
        }

        static String hashPassword(String password) {
            try {
                // PBKDF2-like iterative SHA-256 demo hash. For production, use a salted
                // password-hashing library such as BCrypt or Argon2.
                byte[] bytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                for (int i = 0; i < 120_000; i++) bytes = md.digest(bytes);
                return HexFormat.of().formatHex(bytes);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        static boolean authenticate(String username, String password) throws SQLException {
            String sql = "SELECT password_hash FROM users WHERE username=?";
            try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, username.trim());
                try (ResultSet rs = p.executeQuery()) {
                    return rs.next() && MessageDigest.isEqual(
                            rs.getString(1).getBytes(), hashPassword(password).getBytes());
                }
            }
        }

        static String trainName(String trainNumber) throws SQLException {
            String sql = "SELECT train_name FROM trains WHERE train_number=?";
            try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setInt(1, Integer.parseInt(trainNumber.trim()));
                try (ResultSet rs = p.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        }

        static String book(String passenger, int trainNo, String trainName, String classType,
                           String date, String source, String destination) throws SQLException {
            String pnr;
            SecureRandom random = new SecureRandom();
            String sql = """
                INSERT INTO reservations(pnr,passenger_name,train_number,train_name,class_type,
                  journey_date,source_station,destination_station)
                VALUES(?,?,?,?,?,?,?,?)
                """;
            try (Connection c = connect()) {
                do {
                    pnr = String.format("%010d", random.nextLong(1_000_000_000L, 10_000_000_000L));
                    try (PreparedStatement check = c.prepareStatement(
                            "SELECT 1 FROM reservations WHERE pnr=?")) {
                        check.setString(1, pnr);
                        try (ResultSet rs = check.executeQuery()) {
                            if (!rs.next()) break;
                        }
                    }
                } while (true);
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setString(1, pnr); p.setString(2, passenger); p.setInt(3, trainNo);
                    p.setString(4, trainName); p.setString(5, classType); p.setString(6, date);
                    p.setString(7, source); p.setString(8, destination);
                    p.executeUpdate();
                }
            }
            return pnr;
        }

        static Booking findBooking(String pnr) throws SQLException {
            String sql = "SELECT * FROM reservations WHERE pnr=?";
            try (Connection c = connect(); PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, pnr.trim());
                try (ResultSet rs = p.executeQuery()) {
                    if (!rs.next()) return null;
                    return new Booking(rs.getString("pnr"), rs.getString("passenger_name"),
                            rs.getInt("train_number"), rs.getString("train_name"),
                            rs.getString("class_type"), rs.getString("journey_date"),
                            rs.getString("source_station"), rs.getString("destination_station"));
                }
            }
        }

        static void cancel(String pnr) throws SQLException {
            try (Connection c = connect();
                 PreparedStatement p = c.prepareStatement("DELETE FROM reservations WHERE pnr=?")) {
                p.setString(1, pnr.trim());
                p.executeUpdate();
            }
        }
    }

    record Booking(String pnr, String passenger, int trainNumber, String trainName,
                   String classType, String date, String source, String destination) {
        String details() {
            return "PNR: " + pnr + "\nPassenger: " + passenger + "\nTrain: " +
                    trainNumber + " - " + trainName + "\nClass: " + classType +
                    "\nJourney date: " + date + "\nFrom: " + source + "\nTo: " + destination;
        }
    }

    static class LoginFrame extends JFrame {
        private final JTextField username = new JTextField(18);
        private final JPasswordField password = new JPasswordField(18);

        LoginFrame() {
            setTitle("Online Reservation System - Login");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(420, 270);
            setLocationRelativeTo(null);
            JPanel panel = formPanel("ONLINE RESERVATION SYSTEM");
            addRow(panel, "Username", username);
            addRow(panel, "Password", password);
            JButton login = new JButton("Login");
            login.addActionListener(e -> login());
            JPanel bottom = new JPanel();
            bottom.add(login);
            panel.add(bottom);
            add(panel);
            getRootPane().setDefaultButton(login);
        }

        private void login() {
            String user = username.getText().trim();
            String pass = new String(password.getPassword());
            if (user.isEmpty() || pass.isEmpty()) {
                showError(this, "Enter both username and password.");
                return;
            }
            try {
                if (Database.authenticate(user, pass)) {
                    dispose();
                    new MainFrame(user).setVisible(true);
                } else {
                    showError(this, "Access denied. Invalid username or password.");
                }
            } catch (SQLException ex) {
                showError(this, "Database error: " + ex.getMessage());
            }
        }
    }

    static class MainFrame extends JFrame {
        MainFrame(String username) {
            setTitle("Online Reservation System");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(760, 590);
            setLocationRelativeTo(null);
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Book Ticket", new ReservationPanel());
            tabs.addTab("Cancel Ticket", new CancellationPanel());
            add(tabs, BorderLayout.CENTER);
            JLabel status = new JLabel("Logged in as: " + username);
            status.setBorder(new EmptyBorder(8, 12, 8, 12));
            add(status, BorderLayout.SOUTH);
        }
    }

    static class ReservationPanel extends JPanel {
        private final JTextField passenger = new JTextField(24);
        private final JTextField trainNumber = new JTextField(24);
        private final JTextField trainName = new JTextField(24);
        private final JComboBox<String> classType =
                new JComboBox<>(new String[]{"Sleeper", "AC 3 Tier", "AC 2 Tier", "AC First Class", "General"});
        private final JTextField journeyDate = new JTextField(24);
        private final JTextField source = new JTextField(24);
        private final JTextField destination = new JTextField(24);

        ReservationPanel() {
            setLayout(new BorderLayout());
            JPanel form = formPanel("RESERVATION FORM");
            trainName.setEditable(false);
            addRow(form, "Passenger name", passenger);
            addRow(form, "Train number", trainNumber);
            addRow(form, "Train name (auto)", trainName);
            addRow(form, "Class type", classType);
            addRow(form, "Journey date (YYYY-MM-DD)", journeyDate);
            addRow(form, "Source station", source);
            addRow(form, "Destination station", destination);

            trainNumber.addActionListener(e -> lookupTrain());
            trainNumber.addFocusListener(new java.awt.event.FocusAdapter() {
                public void focusLost(java.awt.event.FocusEvent e) { lookupTrain(); }
            });

            JButton book = new JButton("Book / Insert");
            book.addActionListener(e -> bookTicket());
            JPanel buttons = new JPanel(); buttons.add(book);
            addActionRow(form, buttons);
            add(new JScrollPane(form), BorderLayout.CENTER);
        }

        private void lookupTrain() {
            String number = trainNumber.getText().trim();
            if (number.isEmpty()) { trainName.setText(""); return; }
            if (!number.matches("\\d+")) {
                trainName.setText("");
                showError(this, "Train number must contain digits only.");
                return;
            }
            try {
                String name = Database.trainName(number);
                trainName.setText(name == null ? "" : name);
                if (name == null) showError(this, "Train number not found. Try 12727, 12760, 12603, 12723 or 17016.");
            } catch (SQLException ex) {
                showError(this, "Database error: " + ex.getMessage());
            }
        }

        private void bookTicket() {
            String name = passenger.getText().trim();
            String number = trainNumber.getText().trim();
            String train = trainName.getText().trim();
            String date = journeyDate.getText().trim();
            String from = source.getText().trim();
            String to = destination.getText().trim();

            if (name.isEmpty() || number.isEmpty() || train.isEmpty() || date.isEmpty()
                    || from.isEmpty() || to.isEmpty()) {
                showError(this, "Please fill in all required fields.");
                return;
            }
            if (!number.matches("\\d+")) {
                showError(this, "Train number must be numeric."); return;
            }
            if (from.equalsIgnoreCase(to)) {
                showError(this, "Source and destination must be different."); return;
            }
            try {
                LocalDate parsed = LocalDate.parse(date);
                if (parsed.isBefore(LocalDate.now())) {
                    showError(this, "Journey date cannot be in the past."); return;
                }
                int trainNo = Integer.parseInt(number);
                String pnr = Database.book(name, trainNo, train, (String) classType.getSelectedItem(),
                        parsed.toString(), from, to);
                Booking booking = Database.findBooking(pnr);
                JOptionPane.showMessageDialog(this,
                        "Booking successful!\n\n" + booking.details(),
                        "Ticket Confirmation", JOptionPane.INFORMATION_MESSAGE);
                passenger.setText(""); trainNumber.setText(""); trainName.setText("");
                journeyDate.setText(""); source.setText(""); destination.setText("");
            } catch (DateTimeParseException ex) {
                showError(this, "Invalid date. Use YYYY-MM-DD (example: 2026-12-25).");
            } catch (NumberFormatException ex) {
                showError(this, "Train number is too large or invalid.");
            } catch (SQLException ex) {
                showError(this, "Could not save booking: " + ex.getMessage());
            }
        }
    }

    static class CancellationPanel extends JPanel {
        private final JTextField pnrField = new JTextField(22);
        private final JTextArea details = new JTextArea(10, 42);
        private Booking currentBooking;

        CancellationPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(new EmptyBorder(15, 15, 15, 15));
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(new JLabel("Enter PNR number:"));
            top.add(pnrField);
            JButton fetch = new JButton("Fetch");
            fetch.addActionListener(e -> fetchBooking());
            top.add(fetch);
            add(top, BorderLayout.NORTH);

            details.setEditable(false);
            details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            details.setBorder(BorderFactory.createTitledBorder("Booking details"));
            add(new JScrollPane(details), BorderLayout.CENTER);

            JButton cancel = new JButton("Confirm Cancellation");
            cancel.addActionListener(e -> cancelBooking());
            JPanel bottom = new JPanel(); bottom.add(cancel);
            add(bottom, BorderLayout.SOUTH);
        }

        private void fetchBooking() {
            String pnr = pnrField.getText().trim();
            if (pnr.isEmpty()) { showError(this, "Enter a PNR number."); return; }
            try {
                currentBooking = Database.findBooking(pnr);
                if (currentBooking == null) {
                    details.setText("No active booking found for this PNR.");
                } else {
                    details.setText(currentBooking.details());
                }
            } catch (SQLException ex) {
                showError(this, "Database error: " + ex.getMessage());
            }
        }

        private void cancelBooking() {
            if (currentBooking == null) {
                showError(this, "Fetch a valid booking before cancelling."); return;
            }
            int answer = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to cancel this ticket?\n\n" + currentBooking.details(),
                    "Confirm Cancellation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            try {
                Database.cancel(currentBooking.pnr());
                JOptionPane.showMessageDialog(this, "Booking cancelled successfully.");
                currentBooking = null; details.setText(""); pnrField.setText("");
            } catch (SQLException ex) {
                showError(this, "Cancellation failed: " + ex.getMessage());
            }
        }
    }


    private static void addActionRow(JPanel panel, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = (panel.getComponentCount() + 1) / 2;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(12, 7, 7, 7);
        panel.add(component, c);
    }

    private static JPanel formPanel(String heading) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(18, 30, 18, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 7, 7, 7);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        JLabel title = new JLabel(heading);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        panel.add(title, c);
        return panel;
    }

    private static void addRow(JPanel panel, String label, JComponent field) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0; left.gridy = (panel.getComponentCount() + 1) / 2;
        left.anchor = GridBagConstraints.LINE_END;
        left.insets = new Insets(7, 7, 7, 7);
        JLabel l = new JLabel(label);
        l.setFont(LABEL_FONT);
        panel.add(l, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1; right.gridy = left.gridy;
        right.anchor = GridBagConstraints.LINE_START;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.weightx = 1;
        right.insets = new Insets(7, 7, 7, 7);
        panel.add(field, right);
    }
}
