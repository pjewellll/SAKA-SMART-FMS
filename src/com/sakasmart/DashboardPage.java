package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

import com.sakasmart.Main.ChartPanel;

import java.awt.*;
import java.awt.geom.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.function.*;
import static com.sakasmart.Main.*;

final class DashboardPage extends PageBase {

    private static final Color INVENTORY_TINT =
        new Color(231, 247, 224);
    private static final Color INVENTORY_SHELL =
        new Color(221, 241, 212);
    private static final Color INVENTORY_BORDER =
        new Color(74, 160, 93);

    private static final Color TASK_TINT =
        new Color(255, 243, 204);
    private static final Color TASK_SHELL =
        new Color(255, 236, 184);
    private static final Color TASK_BORDER =
        new Color(214, 149, 28);

    private static final Color INCOME_TINT =
        new Color(220, 247, 236);
    private static final Color INCOME_SHELL =
        new Color(204, 239, 224);
    private static final Color INCOME_BORDER =
        new Color(39, 150, 110);

    private static final Color ALERT_TINT =
        new Color(255, 221, 221);
    private static final Color ALERT_SHELL =
        new Color(255, 208, 208);
    private static final Color ALERT_BORDER =
        new Color(219, 58, 71);
    private static final Color ALERT_TEXT =
        new Color(170, 28, 42);

    private static final Color NEUTRAL_TINT =
        new Color(245, 246, 240);
    private static final Color NEUTRAL_SHELL =
        new Color(239, 241, 235);
    private static final Color NEUTRAL_BORDER =
        new Color(182, 191, 177);

    DashboardPage(Main host) {
        super(host);
    }

    JComponent build() {
        JPanel body = stack();

        List<Map<String, String>> items = rows("inventory");
        List<Map<String, String>> events = rows("events");
        List<Map<String, String>> tx = rows("transactions");
        List<Map<String, String>> logs = rows("logs");

        long low = items.stream()
            .filter(x ->
                Storage.num(x, "quantity")
                    <= Storage.num(x, "min_stock"))
            .count();

        long upcoming = DashboardPriorityLists.upcomingCount(events, LocalDate.now());
        long overdue = DashboardPriorityLists.overdueCount(events, LocalDate.now());

        double incomes = tx.stream()
            .filter(x ->
                "Income".equals(v(x, "type"))
                    && v(x, "date").startsWith(month()))
            .mapToDouble(x -> Storage.num(x, "amount"))
            .sum();

        if (admin()) {
            long active = rows("users").stream()
                .filter(x ->
                    "true".equalsIgnoreCase(v(x, "active")))
                .count();

            long pending = rows("users").stream()
                .filter(x ->
                    "false".equalsIgnoreCase(v(x, "active")))
                .count();

            addBlock(
                body,
                horizontal(
                    roundedPriorityCard(
                        "Registered Users",
                        Integer.toString(rows("users").size()),
                        "All system accounts",
                        LIGHT,
                        INVENTORY_SHELL,
                        INVENTORY_BORDER,
                        "♟",
                        false
                    ),
                    roundedPriorityCard(
                        "Active Users",
                        Long.toString(active),
                        "Can sign in",
                        new Color(234, 244, 253),
                        INCOME_SHELL,
                        INCOME_BORDER,
                        "✓",
                        false
                    ),
                    roundedPriorityCard(
                        "Pending Approvals",
                        Long.toString(pending),
                        "Inactive registrations",
                        TASK_TINT,
                        TASK_SHELL,
                        TASK_BORDER,
                        "!",
                        false
                    ),
                    roundedPriorityCard(
                        "Activity Records",
                        Integer.toString(logs.size()),
                        "Logged system actions",
                        new Color(240, 233, 253),
                        new Color(231, 226, 249),
                        new Color(130, 103, 195),
                        "≡",
                        false
                    )
                ),
                118
            );

            JButton bu = dashboardButton("Manage Users");
            JButton bc = dashboardButton("Learning Content");
            JButton bf = dashboardButton("Farm Profile");

            bu.addActionListener(e -> go("Users"));
            bc.addActionListener(e -> go("Learning Center"));
            bf.addActionListener(e -> go("Farm Profile"));

            addBlock(
                body,
                roundedSection(
                    "Quick Actions",
                    "Administrative tools",
                    actions(bu, bc, bf),
                    INVENTORY_SHELL,
                    INVENTORY_BORDER,
                    false
                ),
                150
            );

            addBlock(
                body,
                horizontal(
                    roundedSection(
                        "Recent Users",
                        "Latest registered accounts",
                        dashboardTable(
                            "users",
                            rows("users"),
                            List.of(
                                "username",
                                "full_name",
                                "role",
                                "active"
                            )
                        ),
                        INVENTORY_SHELL,
                        INVENTORY_BORDER,
                        false
                    ),
                    roundedSection(
                        "System Activity",
                        "Recent changes",
                        dashboardTable(
                            "logs",
                            logs.stream().limit(9).toList(),
                            List.of(
                                "actor",
                                "action",
                                "date"
                            )
                        ),
                        TASK_SHELL,
                        TASK_BORDER,
                        false
                    )
                ),
                360
            );

        } else {

            JComponent inventoryCard = roundedPriorityCard(
                "Total Inventory Items",
                Integer.toString(items.size()),
                "Registered stock entries",
                INVENTORY_TINT,
                INVENTORY_SHELL,
                INVENTORY_BORDER,
                "▣",
                false
            );

            JComponent taskCard = roundedPriorityCard(
                "Upcoming Tasks",
                Long.toString(upcoming),
                overdue > 0 ? overdue + (overdue == 1 ? " task overdue" : " tasks overdue") : "Scheduled and pending",
                upcoming > 0 ? TASK_TINT : NEUTRAL_TINT,
                upcoming > 0 ? TASK_SHELL : NEUTRAL_SHELL,
                upcoming > 0 ? TASK_BORDER : NEUTRAL_BORDER,
                "▦",
                false
            );

            JComponent incomeCard = roundedPriorityCard(
                "This Month Income",
                Storage.money(incomes),
                "Recorded income",
                INCOME_TINT,
                INCOME_SHELL,
                INCOME_BORDER,
                "₱",
                false
            );

            JComponent lowStockCard = roundedPriorityCard(
                "Low Stock Alerts",
                Long.toString(low),
                low > 0
                    ? "Items need attention"
                    : "Stock levels are healthy",
                low > 0 ? ALERT_TINT : NEUTRAL_TINT,
                low > 0 ? ALERT_SHELL : NEUTRAL_SHELL,
                low > 0 ? ALERT_BORDER : NEUTRAL_BORDER,
                "!",
                low > 0
            );

            addBlock(
                body,
                horizontal(
                    inventoryCard,
                    taskCard,
                    incomeCard,
                    lowStockCard
                ),
                126
            );

            List<Map<String, String>> urgent = filter(
                items,
                x ->
                    Storage.num(x, "quantity")
                        <= Storage.num(x, "min_stock")
                    || isExpiring(x)
            );


            List<Map<String, String>> soon = events.stream()
    .filter(event ->
        "Upcoming".equalsIgnoreCase(v(event, "status"))
    )
    .toList();


            Map<String, String> farm = rows("farms")
                .stream()
                .findFirst()
                .orElse(Map.of());

            JComponent farmOverview = new FarmOverviewWidget(host, farm);



            JScrollPane alertTable =
                dashboardTable(
                    "inventory",
                    urgent,
                    List.of(
                        "name",
                        "quantity",
                        "unit",
                        "expiry_date"
                    )
                );

            applyAlertRowColors(alertTable, urgent);

            addBlock(
                body,
                horizontal(
                    roundedSection(
                        "Farm Overview",
                        "Your farm at a glance",
                        farmOverview,
                        INVENTORY_SHELL,
                        INVENTORY_BORDER,
                        false
                    ),
                    roundedSection(
                        "Pending Calendar Tasks",
                        "Scheduled farming tasks",
                        DashboardPriorityLists.upcomingEvents(host, soon),
                        TASK_SHELL,
                        TASK_BORDER,
                        false
                    ),
                    roundedSection(
                        "Low Stock & Expiry Alerts",
                        urgent.isEmpty()
                            ? "No active inventory alerts"
                            : "Monitor supplies",
                        DashboardPriorityLists.inventoryAlerts(host, urgent),
                        low > 0 ? ALERT_SHELL : TASK_SHELL,
                        low > 0 ? ALERT_BORDER : TASK_BORDER,
                        low > 0
                    )
                ),
                332
            );

            List<Map<String, String>> production =
                rows("production");

            Map<String, Double> productionMap =
                new LinkedHashMap<>();

            for (Map<String, String> row : production) {
                productionMap.put(
                    v(row, "item"),
                    Storage.num(row, "target") <= 0
                        ? 0
                        : Math.min(
                            120,
                            100
                                * Storage.num(row, "actual")
                                / Storage.num(row, "target")
                        )
                );
            }

            Map<String, Double> financial =
                new LinkedHashMap<>();

            financial.put(
                "Income",
                tx.stream()
                    .filter(x ->
                        "Income".equals(v(x, "type")))
                    .mapToDouble(x ->
                        Storage.num(x, "amount"))
                    .sum()
            );

            financial.put(
                "Expenses",
                tx.stream()
                    .filter(x ->
                        "Expense".equals(v(x, "type")))
                    .mapToDouble(x ->
                        Storage.num(x, "amount"))
                    .sum()
            );

            addBlock(
                body,
                horizontal(
                    roundedSection(
                        "Financial Snapshot",
                        "Recorded transactions",
                        new ChartPanel(financial, "₱"),
                        INCOME_SHELL,
                        INCOME_BORDER,
                        false
                    ),
                    roundedSection(
                        "Production Summary",
                        "Target achievement (%)",
                        new ChartPanel(
                            productionMap,
                            "%"
                        ),
                        INVENTORY_SHELL,
                        INVENTORY_BORDER,
                        false
                    )
                ),
                305
            );

            addBlock(
                body,
                roundedSection(
                    "🌱  SAKA-SMART TIP",
                    "Helpful reminder",
                    dashboardTip(
                        "🌱  SAKA-SMART TIP",
                        "Your charts and totals are calculated "
                            + "from saved records, not from fixed "
                            + "mockup numbers."
                    ),
                    new Color(238, 247, 233),
                    new Color(98, 166, 94),
                    false
                ),
                110
            );
        }

        return body;
    }

    private static JComponent roundedPriorityCard(
        String title,
        String value,
        String detail,
        Color tint,
        Color shell,
        Color outline,
        String glyph,
        boolean critical
    ) {
        JComponent card = dashboardStat(
            title,
            value,
            detail,
            tint,
            glyph
        );

        if (critical) {
            applyCriticalTextColor(card);
        }

        return roundedWrap(
            card,
            shell,
            outline,
            critical ? 2 : 1,
            30
        );
    }

    private static JComponent roundedSection(
        String title,
        String subtitle,
        JComponent content,
        Color shell,
        Color outline,
        boolean critical
    ) {
        JComponent section = dashboardSection(
            title,
            subtitle,
            content
        );

        if (critical) {
            applyCriticalTextColor(section);
        }

        return roundedWrap(
            section,
            shell,
            outline,
            critical ? 2 : 1,
            30
        );
    }

    private static JComponent roundedWrap(
        JComponent inner,
        Color fill,
        Color stroke,
        int thickness,
        int arc
    ) {
        return new RoundedOutlinePanel(
            inner,
            fill,
            stroke,
            thickness,
            arc
        );
    }

    private static void applyCriticalTextColor(
        Component component
    ) {
        if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            label.setForeground(ALERT_TEXT);
        }

        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                applyCriticalTextColor(child);
            }
        }
    }

    private static void applyAlertRowColors(
        JScrollPane scroll,
        List<Map<String, String>> urgent
    ) {
        if (!(scroll.getViewport().getView()
                instanceof JTable)) {
            return;
        }

        JTable table =
            (JTable) scroll.getViewport().getView();

        DefaultTableCellRenderer renderer =
            new DefaultTableCellRenderer() {

                @Override
                public Component getTableCellRendererComponent(
                    JTable table,
                    Object value,
                    boolean selected,
                    boolean focused,
                    int row,
                    int column
                ) {
                    Component component =
                        super.getTableCellRendererComponent(
                            table,
                            value,
                            selected,
                            focused,
                            row,
                            column
                        );

                    int modelRow =
                        table.convertRowIndexToModel(row);

                    if (modelRow < 0
                            || modelRow >= urgent.size()) {
                        return component;
                    }

                    Map<String, String> item =
                        urgent.get(modelRow);

                    boolean lowStock =
                        Storage.num(item, "quantity")
                            <= Storage.num(item, "min_stock");

                    boolean expiring =
                        isExpiring(item);

                    if (selected) {
                        component.setBackground(
                            new Color(222, 238, 225)
                        );
                        component.setForeground(DARK);

                    } else if (lowStock) {
                        component.setBackground(
                            new Color(255, 219, 219)
                        );
                        component.setForeground(
                            new Color(160, 28, 38)
                        );

                    } else if (expiring) {
                        component.setBackground(
                            new Color(255, 239, 201)
                        );
                        component.setForeground(
                            new Color(136, 86, 14)
                        );

                    } else {
                        component.setBackground(
                            new Color(251, 254, 250)
                        );
                        component.setForeground(DARK);
                    }

                    if (component instanceof JComponent jc) {
                        jc.setBorder(
                            BorderFactory.createEmptyBorder(
                                0, 7, 0, 7
                            )
                        );
                    }

                    return component;
                }
            };

        for (int c = 0; c < table.getColumnCount(); c++) {
            table.getColumnModel()
                .getColumn(c)
                .setCellRenderer(renderer);
        }

        table.repaint();
    }

    /**
     * Rounded glossy outline wrapper.
     * This avoids the rectangular outer outline and keeps the
     * highlight on a curved container only.
     */
    private static final class RoundedOutlinePanel
        extends JPanel {

        private final Color fill;
        private final Color stroke;
        private final int thickness;
        private final int arc;

        RoundedOutlinePanel(
            JComponent inner,
            Color fill,
            Color stroke,
            int thickness,
            int arc
        ) {
            super(new BorderLayout());
            this.fill = fill;
            this.stroke = stroke;
            this.thickness = thickness;
            this.arc = arc;

            setOpaque(false);
            setBorder(
                BorderFactory.createEmptyBorder(
                    6, 6, 6, 6
                )
            );

            add(inner, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );

            int w = getWidth();
            int h = getHeight();

            // soft shadow
            g2.setColor(
                new Color(0, 0, 0, 26)
            );
            g2.fillRoundRect(
                2,
                4,
                w - 6,
                h - 6,
                arc,
                arc
            );

            // base fill
            g2.setColor(fill);
            g2.fillRoundRect(
                0,
                0,
                w - 4,
                h - 4,
                arc,
                arc
            );

            // glossy highlight
            GradientPaint gloss =
                new GradientPaint(
                    0,
                    0,
                    new Color(255, 255, 255, 120),
                    0,
                    h / 2f,
                    new Color(255, 255, 255, 18)
                );

            g2.setPaint(gloss);
            g2.fillRoundRect(
                1,
                1,
                w - 6,
                h / 2,
                arc,
                arc
            );

            // rounded outline
            g2.setStroke(
                new BasicStroke(thickness)
            );
            g2.setColor(new Color(stroke.getRed(), stroke.getGreen(), stroke.getBlue(), thickness > 1 ? 190 : 115));
            g2.drawRoundRect(
                thickness / 2,
                thickness / 2,
                w - thickness - 4,
                h - thickness - 4,
                arc,
                arc
            );

            g2.dispose();
            super.paintComponent(g);
        }
    }
}