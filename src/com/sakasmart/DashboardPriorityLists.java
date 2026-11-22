package com.sakasmart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dashboard-only, read-only visual lists. Uses existing scoped farm records.
 * No data migration, network calls, or login changes.
 */
final class DashboardPriorityLists {
    private static final Color INK = new Color(10, 61, 38);
    private static final Color MUTED = new Color(66, 88, 73);
    private static final Color GREEN = new Color(10, 100, 51);
    private static final Color AMBER = new Color(139, 82, 7);
    private static final Color RED = new Color(159, 24, 43);
    private static final Color DIVIDER = new Color(155, 179, 158);
    private static final Color GREEN_OUTLINE = new Color(69, 151, 98);
    private static final Color AMBER_OUTLINE = new Color(193, 132, 43);
    private static final Color RED_OUTLINE = new Color(193, 77, 92);

    private DashboardPriorityLists() {}

    /**
     * Display states are computed from the calendar date; the saved events.status
     * remains Upcoming, Completed, or Cancelled for existing edits and reports.
     */
    private enum EventWindow {
        OVERDUE(0, "OVERDUE", new Color(159, 24, 43),
            new Color(243, 192, 202), new Color(193, 77, 92),
            new Color(255, 234, 237), new Color(217, 139, 150)),
        DUE_TODAY(1, "DUE TODAY", new Color(137, 62, 4),
            new Color(255, 222, 170), new Color(198, 119, 37),
            new Color(255, 244, 225), new Color(226, 186, 130)),
        DUE_SOON(2, "DUE SOON", AMBER,
            new Color(251, 226, 174), AMBER_OUTLINE,
            new Color(255, 249, 229), new Color(227, 205, 153)),
        UPCOMING(3, "UPCOMING", GREEN,
            new Color(205, 231, 211), GREEN_OUTLINE,
            new Color(247, 251, 246), new Color(202, 220, 204)),
        DATE_TBD(4, "DATE TBD", new Color(80, 97, 85),
            new Color(228, 234, 229), new Color(157, 174, 162),
            new Color(245, 248, 245), new Color(211, 223, 213));

        final int order;
        final String caption;
        final Color foreground, background, outline, rowFill, rowOutline;

        EventWindow(int order, String caption, Color foreground, Color background,
                    Color outline, Color rowFill, Color rowOutline) {
            this.order = order;
            this.caption = caption;
            this.foreground = foreground;
            this.background = background;
            this.outline = outline;
            this.rowFill = rowFill;
            this.rowOutline = rowOutline;
        }
    }

    private static boolean isPending(Map<String, String> event) {
        return "Upcoming".equalsIgnoreCase(safe(event, "status"));
    }

    private static EventWindow window(Map<String, String> event, LocalDate today) {
        LocalDate scheduled = parseDate(safe(event, "date"));
        if (scheduled == null) return EventWindow.DATE_TBD;
        if (scheduled.isBefore(today)) return EventWindow.OVERDUE;
        if (scheduled.equals(today)) return EventWindow.DUE_TODAY;
        if (!scheduled.isAfter(today.plusDays(3))) return EventWindow.DUE_SOON;
        return EventWindow.UPCOMING;
    }

    /** Read-only display label for Dashboard and Calendar task tables. */
    static String displayEventStatus(Map<String, String> event, LocalDate today) {
        String stored = safe(event, "status");
        return isPending(event) ? window(event, today).caption :
            stored.isBlank() ? "UNKNOWN" : stored.toUpperCase(Locale.ENGLISH);
    }

    /** Future tasks include today's tasks; excludes incomplete past-due tasks. */
    static long upcomingCount(List<Map<String, String>> events, LocalDate today) {
        return events.stream().filter(DashboardPriorityLists::isPending)
            .map(e -> parseDate(safe(e, "date")))
            .filter(d -> d != null && !d.isBefore(today))
            .count();
    }

    /** Only incomplete/pending events with dates in the past are overdue. */
    static long overdueCount(List<Map<String, String>> events, LocalDate today) {
        return events.stream().filter(DashboardPriorityLists::isPending)
            .map(e -> parseDate(safe(e, "date")))
            .filter(d -> d != null && d.isBefore(today))
            .count();
    }

    static JComponent upcomingEvents(Main host, List<Map<String, String>> source) {
        JPanel content = transparent(new BorderLayout(0, 8));
        JPanel list = verticalList();
        LocalDate today = LocalDate.now();
        List<Map<String, String>> pending = new ArrayList<>();
        for (Map<String, String> event : source) {
            if (isPending(event)) pending.add(event);
        }
        pending.sort(Comparator
            .comparingInt((Map<String, String> e) -> window(e, today).order)
            .thenComparing(e -> {
                LocalDate date = parseDate(safe(e, "date"));
                return date == null ? LocalDate.MAX : date;
            }));

        if (pending.isEmpty()) {
            list.add(emptyState("No pending calendar tasks", "Your unfinished farm tasks will appear here."));
        } else {
            int shown = Math.min(5, pending.size());
            for (int i = 0; i < shown; i++) {
                Map<String, String> event = pending.get(i);
                String fullTitle = safe(event, "title");
                String target = safe(event, "target");
                String rawDate = safe(event, "date");
                LocalDate date = parseDate(rawDate);
                EventWindow state = window(event, today);

                JPanel row = new SoftTile(new BorderLayout(8, 0),
                    state.rowFill, state.rowOutline);
                row.setToolTipText("Task: " + fullTitle + " | Date: " + readableDate(rawDate)
                    + " | Status: " + state.caption);
                row.setBorder(new EmptyBorder(4, 6, 4, 6));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 49));
                row.setPreferredSize(new Dimension(250, 49));

                JPanel dateBox = new SoftTile(new GridLayout(2, 1, 0, 0),
                    new Color(221, 237, 217), new Color(134, 176, 145));
                dateBox.setPreferredSize(new Dimension(51, 40));
                dateBox.add(centerText(date == null ? "DATE" : date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH), 9, MUTED, true));
                dateBox.add(centerText(date == null ? "--" : String.valueOf(date.getDayOfMonth()), 15, INK, true));
                row.add(dateBox, BorderLayout.WEST);

                JPanel details = transparent(new BorderLayout(0, 2));
                JLabel title = label(ellipsize(fullTitle, 31), 12, true, INK);
                title.setToolTipText(fullTitle.isBlank() ? "No event title" : fullTitle);
                details.add(title, BorderLayout.NORTH);
                JLabel subtitle = label(target.isBlank() ? readableDate(rawDate) : ellipsize(target, 33), 10, false, MUTED);
                subtitle.setToolTipText(target.isBlank() ? rawDate : "Target: " + target + " | Date: " + rawDate);
                details.add(subtitle, BorderLayout.SOUTH);
                row.add(details, BorderLayout.CENTER);

                row.add(pill(state.caption, state.foreground, state.background,
                    state.outline), BorderLayout.EAST);
                list.add(row);
                if (i + 1 < shown) list.add(separator());
            }
            if (pending.size() > shown) list.add(label("+" + (pending.size() - shown) + " more events in Calendar", 10, false, MUTED));
        }

        content.add(scrollable(list), BorderLayout.CENTER);
        content.add(linkButton("View Calendar", () -> host.go("Calendar")), BorderLayout.SOUTH);
        return content;
    }

    static JComponent inventoryAlerts(Main host, List<Map<String, String>> source) {
        JPanel content = transparent(new BorderLayout(0, 8));
        JPanel list = verticalList();
        List<Map<String, String>> ordered = new ArrayList<>(source);
        ordered.sort(Comparator.comparingInt(DashboardPriorityLists::priority));

        if (ordered.isEmpty()) {
            list.add(emptyState("No inventory alerts", "No low-stock or expiring items currently flagged."));
        } else {
            for (int i = 0; i < ordered.size(); i++) {
                Map<String, String> item = ordered.get(i);
                boolean low = Storage.num(item, "quantity") <= Storage.num(item, "min_stock");
                LocalDate expiry = parseDate(safe(item, "expiry_date"));
                boolean expired = expiry != null && expiry.isBefore(LocalDate.now());
                boolean expiring = Main.isExpiring(item);
                Color accent = expired || low ? RED : AMBER;
                Color tint = expired || low ? new Color(251, 224, 230) : new Color(255, 238, 201);
                Color rowOutline = expired || low ? new Color(218, 139, 151) : new Color(217, 173, 97);
                String status = expired ? "EXPIRED" : low ? "LOW STOCK" : expiring ? "EXPIRING" : "CHECK";

                JPanel row = new SoftTile(new BorderLayout(8, 0), tint, rowOutline);
                row.setBorder(new EmptyBorder(7, 9, 7, 9));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 51));
                row.setPreferredSize(new Dimension(240, 51));

                JPanel copy = transparent(new BorderLayout(0, 2));
                String fullName = safe(item, "name");
                JLabel name = label(ellipsize(fullName, 31), 12, true, accent);
                name.setToolTipText(fullName);
                copy.add(name, BorderLayout.NORTH);

                String qtyText = "Qty " + quantity(item) + " " + safe(item, "unit");
                String expText = expiry == null ? "" : "  |  " + readableDate(safe(item, "expiry_date"));
                JLabel info = label(ellipsize(qtyText + expText, 42), 10, false, MUTED);
                info.setToolTipText(qtyText + (expiry == null ? "" : " | Expiry: " + safe(item, "expiry_date")));
                copy.add(info, BorderLayout.SOUTH);
                row.add(copy, BorderLayout.CENTER);
                row.add(pill(status, accent,
                    expired || low ? new Color(243, 192, 202) : new Color(249, 218, 156),
                    expired || low ? RED_OUTLINE : AMBER_OUTLINE), BorderLayout.EAST);
                list.add(row);
                if (i + 1 < ordered.size()) {
                    list.add(Box.createVerticalStrut(4));
                    list.add(separator());
                    list.add(Box.createVerticalStrut(4));
                }
            }
        }

        content.add(scrollable(list), BorderLayout.CENTER);
        content.add(linkButton("View Inventory", () -> host.go("Inventory")), BorderLayout.SOUTH);
        return content;
    }

    private static int priority(Map<String, String> row) {
        LocalDate expiry = parseDate(safe(row, "expiry_date"));
        if (expiry != null && expiry.isBefore(LocalDate.now())) return 0;
        if (Storage.num(row, "quantity") <= Storage.num(row, "min_stock")) return 1;
        if (Main.isExpiring(row)) return 2;
        return 3;
    }

    private static String quantity(Map<String, String> row) {
        String raw = safe(row, "quantity");
        return raw.isBlank() ? "—" : raw;
    }

    private static LocalDate parseDate(String input) {
        try { return LocalDate.parse(input); }
        catch (Exception ignored) { return null; }
    }

    private static String readableDate(String raw) {
        LocalDate date = parseDate(raw);
        return date == null ? (raw.isBlank() ? "Date not set" : raw)
            : date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
    }

    private static String safe(Map<String, String> row, String key) {
        return row.getOrDefault(key, "").trim();
    }

    private static String ellipsize(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
    }

    private static JPanel transparent(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    private static JPanel verticalList() {
        JPanel list = transparent(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(0, 0, 0, 4));
        return list;
    }

    // Real 2-pixel separators: JSeparator's default Look & Feel can make lines faint.
    private static JComponent separator() {
        JComponent line = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(DIVIDER);
                g.fillRect(5, 0, Math.max(0, getWidth() - 10), getHeight());
            }
        };
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setPreferredSize(new Dimension(200, 2));
        line.setMinimumSize(new Dimension(0, 2));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        return line;
    }

    private static JScrollPane scrollable(JPanel list) {
        JScrollPane s = new JScrollPane(list,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        JScrollBar bar = s.getVerticalScrollBar();
        bar.setUnitIncrement(18);
        bar.setPreferredSize(new Dimension(14, 0));
        bar.setUI(new FarmGlossyScrollBarUI());
        bar.setOpaque(false);
        s.setWheelScrollingEnabled(true);
        return s;
    }

    private static JLabel label(String value, int fontSize, boolean bold, Color color) {
        JLabel l = new JLabel(value);
        l.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, fontSize));
        l.setForeground(color);
        return l;
    }

    private static JLabel centerText(String value, int fontSize, Color color, boolean bold) {
        JLabel l = label(value, fontSize, bold, color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    private static JComponent emptyState(String title, String description) {
        JPanel p = transparent(new GridLayout(2, 1, 0, 4));
        p.setBorder(new EmptyBorder(25, 10, 25, 10));
        JLabel a = label(title, 13, true, GREEN);
        JLabel b = label(description, 11, false, MUTED);
        a.setHorizontalAlignment(SwingConstants.CENTER);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(a);
        p.add(b);
        return p;
    }

    private static JButton linkButton(String name, Runnable action) {
        JButton b = new JButton(name + "  >");
        b.setForeground(GREEN);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setBorder(new EmptyBorder(5, 4, 3, 4));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setHorizontalAlignment(SwingConstants.RIGHT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JComponent pill(String value, Color color, Color background, Color outline) {
        return new StatusPill(value, color, background, outline);
    }

    // Darker foreground with a visible 1.5-pixel capsule outline.
    private static final class StatusPill extends JLabel {
        private final Color fill;
        private final Color outline;

        StatusPill(String value, Color color, Color fill, Color outline) {
            super(value);
            this.fill = fill;
            this.outline = outline;
            setFont(new Font("Segoe UI", Font.BOLD, 9));
            setForeground(color);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(new EmptyBorder(5, 9, 5, 9));
        }

        @Override protected void paintComponent(Graphics base) {
            Graphics2D g = (Graphics2D) base.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w > 4 && h > 4) {
                int arc = Math.max(14, h - 4);
                g.setColor(fill);
                g.fillRoundRect(1, 1, w - 3, h - 3, arc, arc);
                g.setColor(outline);
                g.setStroke(new BasicStroke(0.8f));
                g.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            }
            g.dispose();
            super.paintComponent(base);
        }
    }

    // Soft row background with a single, visible rounded outline.
    private static final class SoftTile extends JPanel {
        private final Color fill;
        private final Color outline;

        SoftTile(LayoutManager layout, Color fill, Color outline) {
            super(layout);
            this.fill = fill;
            this.outline = outline;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics base) {
            Graphics2D g = (Graphics2D) base.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w > 4 && h > 4) {
                g.setColor(fill);
                g.fill(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 12, 12));
                g.setColor(outline);
                g.setStroke(new BasicStroke(0.7f));
                g.draw(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 12, 12));
            }
            g.dispose();
            super.paintComponent(base);
        }
    }

    // Application-matched scrollbar, replacing the native Windows arrows and thumb.
    private static final class FarmGlossyScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            trackColor = new Color(221, 237, 222);
            thumbColor = new Color(37, 121, 69);
        }

        @Override protected JButton createDecreaseButton(int orientation) {
            return hiddenArrow();
        }

        @Override protected JButton createIncreaseButton(int orientation) {
            return hiddenArrow();
        }

        private JButton hiddenArrow() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setBorderPainted(false);
            button.setFocusable(false);
            return button;
        }

        @Override protected Dimension getMinimumThumbSize() {
            return new Dimension(10, 34);
        }

        @Override protected void paintTrack(Graphics base, JComponent component,
                                             Rectangle bounds) {
            Graphics2D g = (Graphics2D) base.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            int x = bounds.x + 2, y = bounds.y + 1;
            int w = Math.max(0, bounds.width - 4);
            int h = Math.max(0, bounds.height - 2);
            if (w > 0 && h > 0) {
                g.setColor(trackColor);
                g.fillRoundRect(x, y, w, h, 12, 12);
                g.setColor(new Color(182, 210, 188));
                g.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
            }
            g.dispose();
        }

        @Override protected void paintThumb(Graphics base, JComponent component,
                                             Rectangle bounds) {
            if (!scrollbar.isEnabled() || bounds.isEmpty()) return;
            Graphics2D g = (Graphics2D) base.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            int x = bounds.x + 2, y = bounds.y + 1;
            int w = Math.max(0, bounds.width - 4);
            int h = Math.max(0, bounds.height - 2);
            if (w > 2 && h > 2) {
                g.setPaint(new GradientPaint(x, y, new Color(100, 170, 111),
                          x, y + h, new Color(24, 100, 58)));
                g.fillRoundRect(x, y, w, h, 12, 12);
                g.setColor(new Color(15, 82, 43));
                g.setStroke(new BasicStroke(1.1f));
                g.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
            }
            g.dispose();
        }
    }
}
