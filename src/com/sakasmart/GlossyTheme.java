package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Shared visual-only controls for SAKA-SMART navigation pages.
 * No storage, authentication, validation, or navigation code belongs here.
 */
final class GlossyTheme {
    static final Color INK = new Color(11, 72, 43);
    static final Color BORDER = new Color(109, 161, 120);
    static final Color FIELD_BORDER = new Color(133, 177, 142);
    static final Color FOCUS_BORDER = new Color(27, 132, 72);
    static final Color TABLE_LINE = new Color(195, 214, 197);

    private GlossyTheme() {}

    static JPanel card(LayoutManager layout) {
        return new GlossCard(layout);
    }

    static JButton button(String text, Color base) {
        return new GlossButton(text, base == null ? new Color(24, 128, 67) : base);
    }

    static JButton secondary(String text) {
        return new GlossButton(text, new Color(92, 117, 100));
    }

    static void styleInput(JTextField input) {
        input.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        input.setForeground(INK);
        input.setBackground(new Color(250, 254, 249));
        input.setCaretColor(INK);
        input.setPreferredSize(new Dimension(200, 38));
        applyInputBorder(input, false);
        // Avoid adding duplicate focus handlers if a field is restyled.
        if (!Boolean.TRUE.equals(input.getClientProperty("saka.glossy.focusListener"))) {
            input.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { applyInputBorder(input, true); }
                @Override public void focusLost(FocusEvent e) { applyInputBorder(input, false); }
            });
            input.putClientProperty("saka.glossy.focusListener", Boolean.TRUE);
        }
    }

    private static void applyInputBorder(JTextField input, boolean focused) {
        input.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(focused ? FOCUS_BORDER : FIELD_BORDER, focused ? 2 : 1, true),
            new EmptyBorder(focused ? 7 : 8, focused ? 11 : 12,
                focused ? 7 : 8, focused ? 11 : 12)
        ));
    }

    static void styleTable(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(36);
        table.setBackground(new Color(252, 255, 252));
        table.setForeground(INK);
        table.setSelectionBackground(new Color(202, 235, 207));
        table.setSelectionForeground(INK);
        table.setGridColor(TABLE_LINE);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setForeground(INK);
        table.getTableHeader().setBackground(new Color(217, 237, 219));
        table.getTableHeader().setPreferredSize(new Dimension(1, 37));
        table.getTableHeader().setReorderingAllowed(false);
    }

    static void styleScrollPane(JScrollPane pane) {
        if (pane == null) return;
        JScrollBar vertical = pane.getVerticalScrollBar();
        if (vertical != null) {
            vertical.setUI(new GlossScrollBarUI());
            vertical.setPreferredSize(new Dimension(13, 0));
            vertical.setUnitIncrement(20);
        }
        JScrollBar horizontal = pane.getHorizontalScrollBar();
        if (horizontal != null) {
            horizontal.setUI(new GlossScrollBarUI());
            horizontal.setPreferredSize(new Dimension(0, 13));
            horizontal.setUnitIncrement(20);
        }
    }

    // Mirrors the Dashboard's layered outer shell + curved border.
    // Unlike the earlier shared card, it wraps the existing DashboardGlass inner component.
    static JComponent outlined(JComponent content, Color fill, Color stroke, int thickness, int arc) {
        return new DashboardOutline(content, fill, stroke, thickness, arc);
    }

    static boolean isCriticalStat(String title) {
        String value = title.toLowerCase(java.util.Locale.ROOT);
        return value.contains("low stock") || value.contains("overdue") ||
            value.contains("below target") || value.contains("pending request") ||
            value.contains("pending account");
    }

    static Color statShell(String title, Color tint) {
        String value = title.toLowerCase(java.util.Locale.ROOT);
        if (isCriticalStat(title)) return new Color(255, 218, 223);
        if (value.contains("expir") || value.contains("upcoming") ||
            value.contains("scheduled") || value.contains("orders today"))
            return new Color(255, 238, 195);
        if (value.contains("income") || value.contains("balance") || value.contains("sales"))
            return new Color(206, 240, 224);
        return blend(tint == null ? new Color(224, 245, 224) : tint,
            new Color(212, 243, 216), .32f);
    }

    static Color statOutline(String title) {
        String value = title.toLowerCase(java.util.Locale.ROOT);
        if (isCriticalStat(title)) return new Color(218, 75, 96);
        if (value.contains("expir") || value.contains("upcoming") ||
            value.contains("scheduled") || value.contains("orders today"))
            return new Color(222, 162, 53);
        if (value.contains("income") || value.contains("balance") || value.contains("sales"))
            return new Color(74, 174, 132);
        return new Color(116, 181, 127);
    }

    private static final class DashboardOutline extends JPanel {
        private final Color fill, stroke;
        private final int thickness, arc;

        DashboardOutline(JComponent inner, Color fill, Color stroke, int thickness, int arc) {
            super(new BorderLayout());
            this.fill = fill;
            this.stroke = stroke;
            this.thickness = thickness;
            this.arc = arc;
            setOpaque(false);
            setBorder(new EmptyBorder(6, 6, 6, 6));
            add(inner, BorderLayout.CENTER);
        }

        @Override protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                if (w < 10 || h < 10) return;
                g.setColor(new Color(0, 0, 0, 26));
                g.fillRoundRect(2, 4, Math.max(0, w - 6), Math.max(0, h - 6), arc, arc);
                g.setColor(fill);
                g.fillRoundRect(0, 0, Math.max(0, w - 4), Math.max(0, h - 4), arc, arc);
                g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 120),
                    0, h / 2f, new Color(255, 255, 255, 18)));
                g.fillRoundRect(1, 1, Math.max(0, w - 6), Math.max(0, h / 2), arc, arc);
                g.setStroke(new BasicStroke(thickness));
                g.setColor(new Color(stroke.getRed(), stroke.getGreen(), stroke.getBlue(),
                    thickness > 1 ? 190 : 115));
                g.drawRoundRect(thickness / 2, thickness / 2,
                    Math.max(0, w - thickness - 4), Math.max(0, h - thickness - 4), arc, arc);
            } finally {
                g.dispose();
            }
            super.paintComponent(original);
        }
    }

    private static final class GlossCard extends JPanel {
        GlossCard(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                if (w < 12 || h < 12) return;
                g.setColor(new Color(11, 72, 42, 19));
                g.fillRoundRect(5, 6, w - 9, h - 9, 25, 25);
                g.setPaint(new GradientPaint(1, 1, new Color(255, 255, 253),
                    1, h - 8, new Color(232, 246, 230)));
                g.fillRoundRect(1, 1, w - 7, h - 9, 24, 24);
                Shape oldClip = g.getClip();
                g.clip(new RoundRectangle2D.Float(1, 1, w - 7, h - 9, 24, 24));
                g.setPaint(new GradientPaint(0, 2, new Color(255, 255, 255, 160),
                    0, Math.max(22, h / 2f), new Color(255, 255, 255, 0)));
                g.fillRoundRect(3, 2, w - 11, Math.max(20, h / 2), 21, 21);
                g.setClip(oldClip);
                g.setStroke(new BasicStroke(1.0f));
                g.setColor(BORDER);
                g.drawRoundRect(1, 1, w - 8, h - 10, 24, 24);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class GlossButton extends JButton {
        private final Color base;
        GlossButton(String text, Color base) {
            super(text);
            this.base = base;
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setBorder(new EmptyBorder(9, 16, 12, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                if (w >= 9 && h >= 10) {
                    Color top = blend(base, Color.WHITE, getModel().isRollover() ? .28f : .18f);
                    Color bottom = blend(base, Color.BLACK, getModel().isPressed() ? .47f : .30f);
                    if (!isEnabled()) {
                        top = new Color(150, 165, 154);
                        bottom = new Color(104, 124, 112);
                    }
                    g.setColor(new Color(8, 45, 26, 50));
                    g.fillRoundRect(1, 4, w - 2, h - 5, 16, 16);
                    g.setPaint(new GradientPaint(1, 1, top, 1, h - 5, bottom));
                    g.fillRoundRect(1, 1, w - 2, h - 6, 16, 16);
                    g.setPaint(new GradientPaint(0, 1, new Color(255, 255, 255, 115),
                        0, Math.max(10, h * .54f), new Color(255, 255, 255, 0)));
                    g.fillRoundRect(3, 2, w - 6, Math.max(4, h / 2), 13, 13);
                    g.setColor(new Color(248, 255, 247, 150));
                    g.setStroke(new BasicStroke(1f));
                    g.drawRoundRect(2, 2, w - 5, h - 8, 15, 15);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static Color blend(Color a, Color b, float amount) {
        amount = Math.min(1f, Math.max(0f, amount));
        return new Color(
            (int) (a.getRed() * (1 - amount) + b.getRed() * amount),
            (int) (a.getGreen() * (1 - amount) + b.getGreen() * amount),
            (int) (a.getBlue() * (1 - amount) + b.getBlue() * amount)
        );
    }

    private static final class GlossScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            trackColor = new Color(223, 239, 225);
            thumbColor = new Color(31, 119, 66);
        }

        private JButton hiddenArrow() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setFocusable(false);
            return b;
        }

        @Override protected JButton createDecreaseButton(int orientation) { return hiddenArrow(); }
        @Override protected JButton createIncreaseButton(int orientation) { return hiddenArrow(); }
        @Override protected Dimension getMinimumThumbSize() { return new Dimension(32, 32); }

        @Override protected void paintTrack(Graphics graphics, JComponent c, Rectangle bounds) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(trackColor);
                g.fillRoundRect(bounds.x + 2, bounds.y + 2,
                    Math.max(0, bounds.width - 4), Math.max(0, bounds.height - 4), 10, 10);
            } finally { g.dispose(); }
        }

        @Override protected void paintThumb(Graphics graphics, JComponent c, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int x = bounds.x + 2, y = bounds.y + 2;
                int w = Math.max(0, bounds.width - 4), h = Math.max(0, bounds.height - 4);
                if (w < 2 || h < 2) return;
                g.setPaint(new GradientPaint(x, y, new Color(103, 176, 117),
                    x + (w < h ? 0 : w), y + (w < h ? h : 0), new Color(20, 100, 56)));
                g.fillRoundRect(x, y, w, h, 11, 11);
                g.setColor(new Color(15, 86, 45));
                g.drawRoundRect(x, y, w - 1, h - 1, 11, 11);
            } finally { g.dispose(); }
        }
    }
}
