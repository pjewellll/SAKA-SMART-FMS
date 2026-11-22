package com.sakasmart;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Farm-specific OpenStreetMap viewer and pin picker. Does not use device GPS. */
final class FarmLocationMap extends JPanel {
    private static final int SIZE = 256;
    private static final double MAX_LAT = 85.05112878;

    private static final ExecutorService TILE_WORKERS = Executors.newFixedThreadPool(2, job -> {
        Thread thread = new Thread(job, "saka-farm-map-tiles");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, BufferedImage> TILES = new ConcurrentHashMap<>();
    private static final Set<String> FETCHING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private double centerLat, centerLon;
    private double pinLat, pinLon;
    private boolean hasPin;
    private int zoom;
    private final boolean canPick;
    private final BiConsumer<Double, Double> selection;
    private Point pressed;
    private Point previous;
    private boolean moved;

    FarmLocationMap(Double latitude, Double longitude, boolean canPick,
                    BiConsumer<Double, Double> selection) {
        this.canPick = canPick;
        this.selection = selection;

        zoom = latitude != null && longitude != null ? 14 : 6;
        centerLat = latitude != null ? latitude : 12.8797;
        centerLon = longitude != null ? longitude : 121.7740;
        hasPin = latitude != null && longitude != null;
        if (hasPin) {
            pinLat = latitude;
            pinLon = longitude;
        }

        setOpaque(true);
        setBackground(new Color(232, 243, 233));
        setPreferredSize(new Dimension(460, 205));
        setCursor(Cursor.getPredefinedCursor(canPick ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
        setToolTipText(canPick
            ? "Click to place the farm pin. Drag to pan, scroll to zoom."
            : "Saved farm position (not live GPS)");

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed = e.getPoint();
                previous = e.getPoint();
                moved = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!canPick || previous == null) return;

                int dx = e.getX() - previous.x;
                int dy = e.getY() - previous.y;

                if (pressed.distance(e.getPoint()) > 4) moved = true;

                centerLon = lonAtPixel(worldX(centerLon, zoom) - dx, zoom);
                centerLat = latAtPixel(worldY(centerLat, zoom) - dy, zoom);

                previous = e.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!canPick || pressed == null) return;

                if (!moved && SwingUtilities.isLeftMouseButton(e)) {
                    pinLon = lonAtPixel(worldX(centerLon, zoom) + e.getX() - getWidth() / 2.0, zoom);
                    pinLat = latAtPixel(worldY(centerLat, zoom) + e.getY() - getHeight() / 2.0, zoom);
                    hasPin = true;
                    if (selection != null) selection.accept(pinLat, pinLon);
                    repaint();
                }

                pressed = null;
                previous = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (!canPick) return;
                setZoom(zoom - e.getWheelRotation());
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void setPin(double latitude, double longitude) {
        centerLat = pinLat = Math.max(-MAX_LAT, Math.min(MAX_LAT, latitude));
        centerLon = pinLon = normalizeLongitude(longitude);
        hasPin = true;
        if (zoom < 12) zoom = 14;
        repaint();
    }

    void setView(double latitude, double longitude, int newZoom) {
        centerLat = Math.max(-MAX_LAT, Math.min(MAX_LAT, latitude));
        centerLon = normalizeLongitude(longitude);
        zoom = Math.max(3, Math.min(17, newZoom));
        repaint();
    }

    void setZoom(int next) {
        zoom = Math.max(3, Math.min(17, next));
        repaint();
    }

    void zoomBy(int delta) {
        setZoom(zoom + delta);
    }

    static Double coordinate(String s, double lower, double upper) {
        try {
            if (s == null || s.trim().isEmpty()) return null;
            double number = Double.parseDouble(s.trim());
            return Double.isFinite(number) && number >= lower && number <= upper ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double lat(Map<String, String> farm) {
        return coordinate(farm.get("latitude"), -MAX_LAT, MAX_LAT);
    }

    static Double lon(Map<String, String> farm) {
        return coordinate(farm.get("longitude"), -180, 180);
    }

    static boolean hasCoordinates(Map<String, String> farm) {
        return lat(farm) != null && lon(farm) != null;
    }

    static String mapUrl(double lat, double lon) {
        return String.format(Locale.US,
            "https://www.openstreetmap.org/?mlat=%.7f&mlon=%.7f#map=15/%.7f/%.7f",
            lat, lon, lat, lon);
    }

    static void openBrowser(Component owner, double lat, double lon) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("Desktop browser is unavailable.");
            }
            Desktop.getDesktop().browse(URI.create(mapUrl(lat, lon)));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(owner,
                "Could not open the map: " + e.getMessage(),
                "Map unavailable",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    static void chooseAndSave(Main host) {
        if (!(host.admin() || "FARM_OWNER".equals(host.role()))) {
            host.info("Only the farm owner or system administrator can update the farm's map pin.");
            return;
        }

        List<Map<String, String>> farms = host.rows("farms");
        if (farms.isEmpty()) {
            host.info("No farm profile is available for this account.");
            return;
        }

        Map<String, String> farm = farms.get(0);
        Double originalLat = lat(farm);
        Double originalLon = lon(farm);

        final JTextField latitude = new JTextField(originalLat == null ? "" : format(originalLat), 13);
        final JTextField longitude = new JTextField(originalLon == null ? "" : format(originalLon), 13);

        FarmLocationMap map = new FarmLocationMap(originalLat, originalLon, true,
            (a, b) -> {
                latitude.setText(format(a));
                longitude.setText(format(b));
            }
        );
        map.setPreferredSize(new Dimension(650, 350));

        JTextField searchField = new JTextField(24);
        JButton searchButton = new JButton("Search Place");
        JComboBox<SearchResult> resultsBox = new JComboBox<>();
        resultsBox.setEnabled(false);
        resultsBox.setPrototypeDisplayValue(new SearchResult(0, 0,
            "San Rafael, Tarlac, Philippines"));
        JLabel searchStatus = new JLabel("Search barangay, town, city, or full address.");
        searchStatus.setForeground(new Color(76, 102, 82));

        Runnable triggerSearch = () -> {
            String query = searchField.getText().trim();
            if (query.isEmpty()) {
                JOptionPane.showMessageDialog(map,
                    "Enter a place name or address first.",
                    "Search required",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            searchButton.setEnabled(false);
            resultsBox.setEnabled(false);
            searchStatus.setText("Searching...");
            resultsBox.removeAllItems();

            new SwingWorker<List<SearchResult>, Void>() {
                @Override
                protected List<SearchResult> doInBackground() throws Exception {
                    return searchPlaces(query);
                }

                @Override
                protected void done() {
                    searchButton.setEnabled(true);
                    try {
                        List<SearchResult> found = get();
                        resultsBox.removeAllItems();

                        if (found.isEmpty()) {
                            resultsBox.setEnabled(false);
                            searchStatus.setText("No results found. Try a more specific place name.");
                            return;
                        }

                        for (SearchResult result : found) {
                            resultsBox.addItem(result);
                        }

                        resultsBox.setEnabled(true);
                        resultsBox.setSelectedIndex(0);
                        SearchResult first = (SearchResult) resultsBox.getSelectedItem();
                        if (first != null) {
                            map.setPin(first.latitude, first.longitude);
                            map.setView(first.latitude, first.longitude, 14);
                            latitude.setText(format(first.latitude));
                            longitude.setText(format(first.longitude));
                        }
                        searchStatus.setText("Found " + found.size() + " result(s).");
                    } catch (Exception ex) {
                        resultsBox.setEnabled(false);
                        searchStatus.setText("Search failed. You can still place the pin manually.");
                        JOptionPane.showMessageDialog(map,
                            "Search failed.\n\n" +
                            "Reason: " + ex.getMessage() + "\n\n" +
                            "You can still drag, zoom, and click the map manually.",
                            "Search unavailable",
                            JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        };

        searchButton.addActionListener(e -> triggerSearch.run());
        searchField.addActionListener(e -> triggerSearch.run());

        resultsBox.addActionListener(e -> {
            Object selected = resultsBox.getSelectedItem();
            if (!(selected instanceof SearchResult result)) return;
            map.setPin(result.latitude, result.longitude);
            map.setView(result.latitude, result.longitude, 14);
            latitude.setText(format(result.latitude));
            longitude.setText(format(result.longitude));
            searchStatus.setText(result.displayName);
        });

        JPanel searchTop = new JPanel(new BorderLayout(8, 4));
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);
        searchTop.add(searchRow, BorderLayout.NORTH);
        searchTop.add(resultsBox, BorderLayout.CENTER);
        searchTop.add(searchStatus, BorderLayout.SOUTH);

        JPanel fields = new JPanel(new GridLayout(2, 2, 9, 4));
        fields.add(new JLabel("Latitude (-85 to 85)"));
        fields.add(new JLabel("Longitude (-180 to 180)"));
        fields.add(latitude);
        fields.add(longitude);

        JButton preview = new JButton("Show Coordinates on Map");
        preview.addActionListener(e -> {
            Double a = coordinate(latitude.getText(), -MAX_LAT, MAX_LAT);
            Double b = coordinate(longitude.getText(), -180, 180);
            if (a == null || b == null) {
                JOptionPane.showMessageDialog(map, "Enter valid latitude and longitude values.");
                return;
            }
            map.setPin(a, b);
            map.setView(a, b, 14);
        });

        JButton minus = new JButton("−");
        minus.addActionListener(e -> map.zoomBy(-1));

        JButton plus = new JButton("+");
        plus.addActionListener(e -> map.zoomBy(1));

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        tools.add(preview);
        tools.add(minus);
        tools.add(plus);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(12, 12, 10, 12));

        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(new JLabel("Click the farm position on the map. Drag to pan; scroll to zoom."),
            BorderLayout.NORTH);
        north.add(searchTop, BorderLayout.SOUTH);

        root.add(north, BorderLayout.NORTH);
        root.add(map, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(fields, BorderLayout.NORTH);
        bottom.add(tools, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(host.frame, root, "Set Farm Location Pin",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        Double a = coordinate(latitude.getText(), -MAX_LAT, MAX_LAT);
        Double b = coordinate(longitude.getText(), -180, 180);

        if (a == null || b == null) {
            JOptionPane.showMessageDialog(host.frame,
                "Select a point on the map or enter valid coordinates.",
                "Invalid farm location",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            host.store.edit("farms", farm.get("id"), Map.of(
                "latitude", format(a),
                "longitude", format(b)
            ));
            host.store.log(host.username(), "Updated farm map pin");
            host.renderPage();
        } catch (Exception ex) {
            host.error(ex);
        }
    }

    private static List<SearchResult> searchPlaces(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=" + encoded;

        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "SAKA-SMART-FarmMap/1.0 (educational desktop application)");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "en");
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code);
        }

        String json;
        try (InputStream input = connection.getInputStream()) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        return parseSearchResults(json);
    }

    private static List<SearchResult> parseSearchResults(String json) {
        List<SearchResult> results = new ArrayList<>();
        if (json == null || json.isBlank()) return results;

        Matcher objectMatcher = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL).matcher(json);
        while (objectMatcher.find()) {
            String object = objectMatcher.group();
            String latText = extractJsonValue(object, "lat");
            String lonText = extractJsonValue(object, "lon");
            String display = extractJsonValue(object, "display_name");

            if (latText == null || lonText == null || display == null) continue;

            try {
                double lat = Double.parseDouble(latText);
                double lon = Double.parseDouble(lonText);
                results.add(new SearchResult(lat, lon, display));
            } catch (NumberFormatException ignored) {
            }
        }

        return results;
    }

    private static String extractJsonValue(String jsonObject, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL)
            .matcher(jsonObject);
        if (!matcher.find()) return null;
        return unescapeJson(matcher.group(1));
    }

    private static String unescapeJson(String text) {
        String value = text;
        value = value.replace("\\\"", "\"");
        value = value.replace("\\\\", "\\");
        value = value.replace("\\/", "/");
        value = value.replace("\\n", " ");
        value = value.replace("\\r", " ");
        value = value.replace("\\t", " ");
        return value.trim();
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.7f", value);
    }

    private static double normalizeLongitude(double lon) {
        return ((lon + 180.0) % 360 + 360) % 360 - 180;
    }

    private static double worldX(double lon, int z) {
        return (lon + 180) / 360 * SIZE * (1 << z);
    }

    private static double worldY(double lat, int z) {
        double radians = Math.toRadians(Math.max(-MAX_LAT, Math.min(MAX_LAT, lat)));
        return (1 - Math.log(Math.tan(radians) + 1 / Math.cos(radians)) / Math.PI) / 2 * SIZE * (1 << z);
    }

    private static double lonAtPixel(double x, int z) {
        return normalizeLongitude(x / (SIZE * (1 << z)) * 360 - 180);
    }

    private static double latAtPixel(double y, int z) {
        double n = Math.PI - 2 * Math.PI * y / (SIZE * (1 << z));
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    @Override
    protected void paintComponent(Graphics original) {
        super.paintComponent(original);

        Graphics2D g = (Graphics2D) original.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        double cx = worldX(centerLon, zoom);
        double cy = worldY(centerLat, zoom);

        int firstX = (int) Math.floor((cx - w / 2.0) / SIZE);
        int lastX = (int) Math.floor((cx + w / 2.0) / SIZE);
        int firstY = (int) Math.floor((cy - h / 2.0) / SIZE);
        int lastY = (int) Math.floor((cy + h / 2.0) / SIZE);

        int max = (1 << zoom);

        for (int x = firstX; x <= lastX; x++) {
            for (int y = firstY; y <= lastY; y++) {
                if (y < 0 || y >= max) continue;

                int drawX = (int) Math.round(x * SIZE - cx + w / 2.0);
                int drawY = (int) Math.round(y * SIZE - cy + h / 2.0);

                int wrapped = Math.floorMod(x, max);
                String key = zoom + "/" + wrapped + "/" + y;

                BufferedImage tile = TILES.get(key);
                if (tile != null) {
                    g.drawImage(tile, drawX, drawY, SIZE, SIZE, null);
                } else {
                    g.setColor(new Color(235, 244, 237));
                    g.fillRect(drawX, drawY, SIZE, SIZE);
                    g.setColor(new Color(211, 227, 216));
                    g.drawRect(drawX, drawY, SIZE, SIZE);
                    if (!FAILED.contains(key)) requestTile(key);
                }
            }
        }

        if (hasPin) {
            double dx = worldX(pinLon, zoom) - cx;
            double worldWidth = SIZE * (1 << zoom);
            if (dx > worldWidth / 2) dx -= worldWidth;
            if (dx < -worldWidth / 2) dx += worldWidth;

            int mx = (int) Math.round(w / 2.0 + dx);
            int my = (int) Math.round(h / 2.0 + worldY(pinLat, zoom) - cy);

            g.setColor(new Color(255, 255, 255, 215));
            g.fillOval(mx - 12, my - 28, 24, 24);

            g.setColor(new Color(216, 45, 61));
            g.fillOval(mx - 9, my - 25, 18, 18);

            g.setStroke(new BasicStroke(3));
            g.drawLine(mx, my - 7, mx, my + 3);

            g.setColor(Color.WHITE);
            g.fillOval(mx - 3, my - 19, 6, 6);
        }

        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        String attribution = "© OpenStreetMap contributors";
        FontMetrics fm = g.getFontMetrics();
        int aw = fm.stringWidth(attribution) + 10;

        g.setColor(new Color(255, 255, 255, 215));
        g.fillRoundRect(Math.max(0, w - aw - 4), h - 21, aw, 18, 6, 6);

        g.setColor(new Color(32, 64, 42));
        g.drawString(attribution, Math.max(4, w - aw + 1), h - 8);

        g.dispose();
    }

    private void requestTile(String key) {
        if (!FETCHING.add(key)) return;

        TILE_WORKERS.execute(() -> {
            try {
                URL u = URI.create("https://tile.openstreetmap.org/" + key + ".png").toURL();
                URLConnection c = u.openConnection();
                c.setRequestProperty("User-Agent", "SAKA-SMART-FarmMap/1.0 (educational desktop application)");
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);

                BufferedImage tile;
                try (InputStream input = c.getInputStream()) {
                    tile = ImageIO.read(input);
                }

                if (tile != null) {
                    if (TILES.size() > 160) TILES.clear();
                    TILES.put(key, tile);
                } else {
                    FAILED.add(key);
                }
            } catch (Exception ex) {
                FAILED.add(key);
            } finally {
                FETCHING.remove(key);
                SwingUtilities.invokeLater(this::repaint);
            }
        });
    }

    private static final class SearchResult {
        final double latitude;
        final double longitude;
        final String displayName;

        SearchResult(double latitude, double longitude, String displayName) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}