package com.sakasmart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.sakasmart.Main.*;

/**
 * "Phone Scanner" for Inventory.
 *
 * The farmer opens this window, scans the QR code with a cellphone, and the phone camera becomes
 * a barcode scanner. Every scanned barcode arrives here and is handled like this:
 *   - barcode already saved on an inventory item  -> ask how much to ADD to the stock (Stock In)
 *   - barcode not in inventory                    -> add it as a new item, or link it to an
 *                                                    existing item that has no barcode yet
 * All reads/writes go through the normal Storage (farm scoping + role checks still apply) and
 * all Swing work happens on the event-dispatch thread.
 */
final class InventoryScanner extends PageBase {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static InventoryScanner open;

    private final JDialog dialog;
    private final JLabel status = label("Starting the scanner...", 13, true, DARK);
    private final JTextField linkField = new JTextField();
    private final JComboBox<String> addressChoice = new JComboBox<>();
    private final QrPanel qr = new QrPanel();
    private final DefaultListModel<String> history = new DefaultListModel<>();
    private final Deque<String> pending = new ArrayDeque<>();
    private PhoneScannerServer server;
    private List<String> urls = List.of();
    private boolean busy;

    /** Opens the scanner window (or brings the existing one to the front). */
    static void show(Main host) {
        if (open != null) {
            open.dialog.toFront();
            open.dialog.requestFocus();
            return;
        }
        open = new InventoryScanner(host);
    }

    private InventoryScanner(Main host) {
        super(host);
        dialog = new JDialog(frame, "Phone Barcode Scanner", false);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { close(); }
        });
        dialog.setContentPane(buildContent());
        dialog.setSize(720, 600);
        dialog.setMinimumSize(new Dimension(640, 540));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
        startServer();
    }

    // ------------------------------------------------------------------ UI
    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(16, 18, 14, 18));

        JLabel steps = new JLabel("<html><div style='width:560px'>"
            + "<b style='font-size:15px'>Use your cellphone as the barcode scanner</b><br><br>"
            + "<b>1.</b> Connect the phone to the <b>same Wi-Fi</b> as this computer.<br>"
            + "<b>2.</b> Scan the QR code below with the phone camera (or type the link in the phone browser).<br>"
            + "<b>3.</b> If the browser warns the page is not secure, tap <b>Advanced &rarr; Continue</b> "
            + "(this is your own computer), then allow the camera.<br>"
            + "<b>4.</b> Scan a product barcode. The result appears here."
            + "</div></html>");
        steps.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        steps.setForeground(TEXT);
        root.add(steps, BorderLayout.NORTH);

        qr.setPreferredSize(new Dimension(210, 210));
        JPanel qrHolder = pane(new BorderLayout());
        qrHolder.add(qr, BorderLayout.NORTH);

        JPanel side = pane(null);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(status);
        side.add(Box.createVerticalStrut(8));
        JLabel linkTitle = label("Phone link", 12, true, MUTED);
        linkTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(linkTitle);
        linkField.setEditable(false);
        linkField.setFont(new Font("Consolas", Font.PLAIN, 12));
        linkField.setAlignmentX(Component.LEFT_ALIGNMENT);
        linkField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        side.add(linkField);
        side.add(Box.createVerticalStrut(6));
        addressChoice.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        addressChoice.setVisible(false);
        addressChoice.addActionListener(e -> showUrl(addressChoice.getSelectedIndex()));
        side.add(addressChoice);
        JButton copy = secondary("Copy Link");
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(linkField.getText()), null));
        side.add(Box.createVerticalStrut(4));
        side.add(copy);
        side.add(Box.createVerticalStrut(10));
        JLabel note = new JLabel("<html><div style='width:330px'>The first time, Windows may ask to allow "
            + "SAKA-SMART on the network. Choose <b>Allow access</b> (private network). "
            + "If the phone cannot connect, check the Wi-Fi is the same, or use the other address in the list."
            + "</div></html>");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        note.setForeground(MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(note);

        JPanel middle = pane(new BorderLayout(16, 0));
        middle.add(qrHolder, BorderLayout.WEST);
        middle.add(side, BorderLayout.CENTER);

        JList<String> list = new JList<>(history);
        list.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder("Scan history (this session)"));
        scroll.setPreferredSize(new Dimension(100, 150));
        scroll.setOpaque(false);

        JPanel center = pane(new BorderLayout(0, 12));
        center.add(middle, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        JButton stop = btn("Stop Scanner & Close", RED);
        stop.addActionListener(e -> close());
        JPanel south = pane(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.add(stop);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private void showUrl(int index) {
        if (index < 0 || index >= urls.size()) return;
        linkField.setText(urls.get(index));
        linkField.setCaretPosition(0);
        try {
            qr.set(QrCode.encode(urls.get(index)).toImage(1, 0));
        } catch (IllegalArgumentException ex) {
            qr.set(null);
        }
    }

    private void startServer() {
        new SwingWorker<PhoneScannerServer, Void>() {
            @Override protected PhoneScannerServer doInBackground() throws Exception {
                PhoneScannerServer s = new PhoneScannerServer(InventoryScanner.this::receive);
                s.start();
                return s;
            }
            @Override protected void done() {
                try {
                    PhoneScannerServer s = get();
                    if (open != InventoryScanner.this) { s.stop(); return; } // closed while starting
                    server = s;
                    urls = s.urls();
                    if (urls.isEmpty()) {
                        status.setText("No network found. Connect this computer to Wi-Fi, then reopen.");
                        status.setForeground(RED);
                        return;
                    }
                    addressChoice.removeAllItems();
                    for (String u : urls) addressChoice.addItem(u);
                    addressChoice.setVisible(urls.size() > 1);
                    showUrl(0);
                    String mode = s.isSecure() ? "Scanner is ON (secure)" : "Scanner is ON (not secure: camera may be blocked)";
                    status.setText(mode + " - waiting for a phone");
                    status.setForeground(s.isSecure() ? GREEN : ORANGE);
                    if (!s.warning().isBlank()) note("Warning: " + s.warning());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText("Could not start the scanner: " + cause.getMessage());
                    status.setForeground(RED);
                }
            }
        }.execute();
    }

    private void close() {
        if (server != null) server.stop();
        pending.clear();
        open = null;
        dialog.dispose();
    }

    private void note(String text) {
        history.add(0, LocalTime.now().format(CLOCK) + "   " + text);
        if (history.size() > 200) history.removeElementAt(history.size() - 1);
    }

    // ------------------------------------------------------------------ scan handling
    /** Called on a server thread. Hops to the EDT so storage and Swing are only used there. */
    private PhoneScannerServer.ScanResult receive(String code) {
        AtomicReference<PhoneScannerServer.ScanResult> out = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> out.set(accept(code)));
        } catch (Exception ex) {
            return new PhoneScannerServer.ScanResult("ERROR", "The computer could not process this scan.");
        }
        return out.get();
    }

    private PhoneScannerServer.ScanResult accept(String code) {
        if (open != this) return new PhoneScannerServer.ScanResult("ERROR", "The scanner window was closed.");
        Map<String, String> item = find(code);
        note("Scanned " + code + (item != null ? "  ->  " + v(item, "name") : "  ->  new barcode"));
        if (busy) {
            pending.add(code);
            return new PhoneScannerServer.ScanResult("QUEUED",
                "Saved. Finish the current item on the computer first.");
        }
        busy = true;
        SwingUtilities.invokeLater(() -> process(code));
        if (item != null) {
            return new PhoneScannerServer.ScanResult("FOUND", v(item, "name") + " is in inventory ("
                + fmt(Storage.num(item, "quantity")) + " " + v(item, "unit") + "). Enter the quantity to add on the computer.");
        }
        return new PhoneScannerServer.ScanResult("NEW", "New barcode " + code + ". Continue on the computer.");
    }

    private void process(String code) {
        try {
            if (open != this) return;
            bringToFront();
            Map<String, String> item = find(code);
            if (item != null) stockIn(item, code);
            else newBarcode(code);
        } catch (Exception ex) {
            error(ex);
        } finally {
            String next = pending.poll();
            if (next == null || open != this) {
                busy = false;
            } else {
                SwingUtilities.invokeLater(() -> process(next));
            }
        }
    }

    private void bringToFront() {
        if (frame.getState() == Frame.ICONIFIED) frame.setState(Frame.NORMAL);
        frame.toFront();
    }

    private Map<String, String> find(String code) {
        String key = norm(code);
        for (Map<String, String> row : rows("inventory"))
            if (!v(row, "barcode").isBlank() && norm(v(row, "barcode")).equals(key)) return row;
        return null;
    }

    /** Same barcode, tolerant of case, spaces and the leading zero that turns UPC-A into EAN-13. */
    static String norm(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!t.isEmpty() && t.chars().allMatch(Character::isDigit)) t = t.replaceFirst("^0+(?!$)", "");
        return t;
    }

    // -- existing item: Stock In -------------------------------------------------------------
    private void stockIn(Map<String, String> item, String code) throws Exception {
        double current = Storage.num(item, "quantity");
        String unit = v(item, "unit"), name = v(item, "name");
        String answer = StyledDialogs.showInputDialog(frame,
            "<html>Barcode " + html(code) + " is in your inventory:<br><b>" + html(name) + "</b> &mdash; in stock: "
                + fmt(current) + " " + html(unit) + "<br><br>How many " + html(unit) + " do you want to ADD?</html>", "1");
        if (answer == null) { note("Skipped " + name); return; }
        double add;
        try {
            add = Double.parseDouble(answer.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Enter a number such as 5 or 2.5.");
        }
        if (!Double.isFinite(add) || add <= 0) throw new IllegalArgumentException("Enter a quantity greater than 0.");
        double total = current + add;
        store.edit("inventory", v(item, "id"), Map.of("quantity", plain(total)));
        store.log(username(), "Barcode stock-in +" + fmt(add) + " for " + name + " [" + code + "]");
        note("Stock In: +" + fmt(add) + " " + unit + " " + name + " (now " + fmt(total) + ")");
        renderPage();
    }

    // -- unknown barcode: add or link ----------------------------------------------------------
    private void newBarcode(String code) throws Exception {
        Object[] options = {"Add as a New Item", "Link to an Existing Item", "Cancel"};
        int choice = StyledDialogs.showOptionDialog(frame,
            "Barcode " + code + " is not in your inventory yet.\nWhat do you want to do with it?",
            "New Barcode Scanned", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);
        if (choice == 0) addNew(code);
        else if (choice == 1) linkExisting(code);
        else note("Skipped barcode " + code);
    }

    private void addNew(String code) {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("barcode", code);
        defaults.put("kind", host.inventoryHarvest ? "HARVEST" : "SUPPLY");
        defaults.put("quantity", "1");
        defaults.put("min_stock", "0");
        defaults.put("farm_id", "1");
        form("inventory", null, defaults, Set.of("barcode"));
        Map<String, String> saved = find(code);
        note(saved != null ? "Added new item: " + v(saved, "name") + " [" + code + "]"
            : "New item was not saved for " + code);
    }

    private void linkExisting(String code) throws Exception {
        List<Map<String, String>> candidates = new ArrayList<>();
        for (Map<String, String> row : rows("inventory")) if (v(row, "barcode").isBlank()) candidates.add(row);
        candidates.sort(Comparator.comparing(r -> v(r, "name").toLowerCase(Locale.ROOT)));
        if (candidates.isEmpty()) {
            info("Every inventory item already has a barcode. Choose \"Add as a New Item\" instead.");
            return;
        }
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < labels.length; i++) {
            Map<String, String> r = candidates.get(i);
            labels[i] = v(r, "name") + "   (" + ("HARVEST".equalsIgnoreCase(v(r, "kind")) ? "Harvest" : "Supply")
                + ", " + fmt(Storage.num(r, "quantity")) + " " + v(r, "unit") + ")";
        }
        JComboBox<String> combo = new JComboBox<>(labels);
        JPanel box = pane(new BorderLayout(0, 8));
        box.add(label("Save barcode " + code + " on this item:", 13, true, DARK), BorderLayout.NORTH);
        box.add(combo, BorderLayout.CENTER);
        int ok = StyledDialogs.showConfirmDialog(frame, box, "Link Barcode to Existing Item",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) { note("Skipped barcode " + code); return; }
        Map<String, String> chosen = candidates.get(combo.getSelectedIndex());
        store.edit("inventory", v(chosen, "id"), Map.of("barcode", code));
        store.log(username(), "Linked barcode " + code + " to " + v(chosen, "name"));
        note("Linked barcode " + code + " to " + v(chosen, "name"));
        renderPage();
        Map<String, String> updated = new HashMap<>(chosen);
        updated.put("barcode", code);
        stockIn(updated, code);
    }

    // ------------------------------------------------------------------ helpers
    private static String fmt(double d) {
        BigDecimal b = BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return b.scale() < 0 ? b.setScale(0).toPlainString() : b.toPlainString();
    }
    private static String plain(double d) {
        return BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Paints the QR code scaled to the panel, on a white card so phones can read it. */
    private static final class QrPanel extends JPanel {
        private BufferedImage image;
        QrPanel() { setOpaque(false); }
        void set(BufferedImage img) { image = img; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(new Color(180, 210, 185));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                if (image == null) return;
                int pad = 12, side = Math.min(getWidth(), getHeight()) - pad * 2;
                int quiet = 4; // quiet zone in modules
                int modules = image.getWidth();
                int cell = Math.max(1, side / (modules + quiet * 2));
                int drawn = cell * modules;
                int x0 = (getWidth() - drawn) / 2, y0 = (getHeight() - drawn) / 2;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(image, x0, y0, drawn, drawn, null);
            } finally { g2.dispose(); }
        }
    }
}
