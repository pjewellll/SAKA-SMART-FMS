package com.sakasmart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

import static com.sakasmart.Main.*;

/**
 * Inventory workspace — Dashboard-matched design.
 * Compact, no-horizontal-scroll table and prominent stock alerts.
 * Presentation and client-side table filtering only. Inventory CRUD and farm
 * permission checks remain delegated to Main and Storage.
 */
final class InventoryPage extends PageBase {
    private static final Color INK = new Color(10, 77, 45);
    private static final Color SUBTEXT = new Color(87, 117, 96);
    private static final Color PALE_GREEN = new Color(232, 247, 231);
    private static final Color RED_TINT = new Color(255, 226, 230);
    private static final Color RED_LINE = new Color(216, 85, 104);
    private static final Color AMBER_TINT = new Color(255, 241, 208);
    private static final Color AMBER_LINE = new Color(211, 151, 49);

    InventoryPage(Main host) {
        super(host);
    }

    JComponent build() {
        JPanel page = stack();
        List<Map<String, String>> data = rows("inventory");

        long low = data.stream().filter(InventoryPage::lowStock).count();
        long expiring = data.stream().filter(Main::isExpiring).count();
        long harvests = data.stream().filter(r -> "HARVEST".equalsIgnoreCase(v(r, "kind"))).count();
        long supplies = data.stream().filter(r -> "SUPPLY".equalsIgnoreCase(v(r, "kind"))).count();

        addBlock(page, horizontal(
            stat("Inventory Items", Long.toString(data.size()), "All farm inventory", LIGHT, "▣"),
            stat("Low Stock Items", Long.toString(low), "At or below minimum", new Color(255, 230, 228), "!"),
            stat("Expiring Within 30 Days", Long.toString(expiring), "Includes expired stock", new Color(255, 243, 216), "◷"),
            stat("Harvested Products", Long.toString(harvests), "Recorded product types", new Color(222, 245, 232), "❧")
        ), 137);

        JButton suppliesButton = btn("Farm Inputs / Supplies  (" + supplies + ")",
            host.inventoryHarvest ? new Color(98, 125, 107) : GREEN);
        JButton harvestButton = btn("Harvested Products  (" + harvests + ")",
            host.inventoryHarvest ? GREEN : new Color(98, 125, 107));

        suppliesButton.addActionListener(e -> {
            if (host.inventoryHarvest) {
                host.inventoryHarvest = false;
                renderPage();
            }
        });
        harvestButton.addActionListener(e -> {
            if (!host.inventoryHarvest) {
                host.inventoryHarvest = true;
                renderPage();
            }
        });

        JPanel categories = pane(new BorderLayout(12, 0));
        JPanel tabs = pane(new FlowLayout(FlowLayout.LEFT, 9, 0));
        tabs.add(suppliesButton);
        tabs.add(harvestButton);
        categories.add(tabs, BorderLayout.WEST);

        if (editable("inventory")) {
            JButton scanner = secondary("Phone Scanner");
            scanner.setToolTipText(
                "Use your cellphone camera as a barcode scanner for stock in / new items"
            );
            scanner.addActionListener(e -> InventoryScanner.show(host));
            categories.add(scanner, BorderLayout.EAST);
        } else {
            categories.add(
                centeredHint("Select a category to manage its records"),
                BorderLayout.EAST
            );
        }
        addBlock(page, section("Inventory Categories",
            "Organize your supplies and harvested products in one place", categories), 168);

        String activeKind = host.inventoryHarvest ? "HARVEST" : "SUPPLY";
        List<Map<String, String>> selected = filter(data,
            r -> activeKind.equalsIgnoreCase(v(r, "kind")));

        String title = host.inventoryHarvest ? "Harvested Products" : "Farm Inputs / Supplies";
        JComponent table = inventoryWorkspace(selected, activeKind);
        addBlock(page, section(title,
            "Search inventory, review stock alerts, and manage saved records", table), 610);

        return page;
    }

    private JComponent inventoryWorkspace(List<Map<String, String>> items, String kind) {
        JPanel workspace = pane(new BorderLayout(0, 10));

        // Copies contain one DISPLAY-ONLY field, never written to Storage.
        List<Map<String, String>> display = new ArrayList<>(items.size());
        for (Map<String, String> item : items) {
            Map<String, String> row = new LinkedHashMap<>(item);
            row.put("inventory_display_status", status(item));
            display.add(row);
        }

        // Seven focused columns fit the existing app shell at its minimum window size.
        // ID and minimum stock are available in the selection details + tooltips.
        // The model always contains the COMPLETE source maps for edit/delete/stock moves.
        List<String> columns = List.of("name", "category", "quantity", "unit",
            "supplier", "expiry_date", "inventory_display_status");
        TableParts parts = tableView("inventory", display, columns, null);
        JTable table = parts.table();
        table.setRowHeight(41);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setToolTipText(
            "Columns fit the window. Hover a cell or select a row to view complete details.");

        TableColumnModel columnModel = table.getColumnModel();
        int[] widths = {225, 112, 82, 59, 160, 112, 145};
        int[] minimums = {100, 60, 45, 37, 70, 71, 91};
        String[] headings = {"Item / Product", "Category", "Qty", "Unit",
            "Supplier", "Expiry", "Stock Status"};
        for (int i = 0; i < widths.length; i++) {
            columnModel.getColumn(i).setHeaderValue(headings[i]);
            columnModel.getColumn(i).setMinWidth(minimums[i]);
            columnModel.getColumn(i).setPreferredWidth(widths[i]);
        }

        for (int i = 0; i < columnModel.getColumnCount() - 1; i++) {
            final int column = i;
            DefaultTableCellRenderer textRenderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value,
                        boolean selected, boolean focused, int row, int col) {
                    super.getTableCellRendererComponent(t, value, selected, focused, row, col);
                    int modelIndex = t.convertRowIndexToModel(row);
                    Map<String, String> item = display.get(modelIndex);
                    String state = status(item);
                    boolean critical = isCritical(state);
                    boolean approaching = "EXPIRING SOON".equals(state);
                    Color rose = "OUT OF STOCK".equals(state)
                        ? new Color(255, 224, 228) : new Color(255, 239, 242);
                    Color tint = selected ? new Color(197, 229, 202)
                        : critical ? rose
                        : approaching ? new Color(255, 250, 226)
                        : row % 2 == 0 ? new Color(251, 255, 251)
                        : new Color(241, 249, 242);
                    setBackground(tint);
                    setForeground((column == 0 || column == 2 || column == 5) && critical
                        ? new Color(171, 32, 50)
                        : (column == 0 || column == 5) && approaching
                            ? new Color(145, 86, 11) : INK);
                    setFont(new Font("Segoe UI",
                        (column == 0 || (column == 2 && critical)) ? Font.BOLD : Font.PLAIN, 12));
                    setBorder(new EmptyBorder(0, column == 0 ? 11 : 7, 0, 5));
                    setHorizontalAlignment(column == 2 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                    setToolTipText("Item #" + v(item, "id") + " — " + v(item, "name")
                        + " | Qty: " + v(item, "quantity") + " " + v(item, "unit")
                        + " | Minimum: " + v(item, "min_stock")
                        + " | Supplier: " + v(item, "supplier")
                        + " | Expiry: " + (v(item, "expiry_date").isBlank() ? "Not set" : v(item, "expiry_date"))
                        + " | " + issueSummary(item));
                    return this;
                }
            };
            columnModel.getColumn(i).setCellRenderer(textRenderer);
        }
        columnModel.getColumn(6).setCellRenderer(new StockStatusRenderer());

        DefaultTableModel model = (DefaultTableModel) table.getModel();
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        Comparator<String> numericOrder = (a, b) -> Double.compare(parseNumber(a), parseNumber(b));
        sorter.setComparator(2, numericOrder);
        sorter.setComparator(6, Comparator.comparingInt(InventoryPage::statusRank));
        table.setRowSorter(sorter);
        // Critical rows appear first, then expiring stock; harmless to stored records.
        sorter.setSortKeys(List.of(new RowSorter.SortKey(6, SortOrder.ASCENDING),
            new RowSorter.SortKey(0, SortOrder.ASCENDING)));

        GlossySearchField search = new GlossySearchField(
            "Type an item, supplier, category or stock status…");
        search.setToolTipText("Search inventory records instantly");
        search.getAccessibleContext().setAccessibleName("Search inventory");

        JLabel count = label("", 12, true, SUBTEXT);
        count.setHorizontalAlignment(SwingConstants.RIGHT);
        count.setBorder(new EmptyBorder(0, 10, 0, 3));

        JPanel searchLine = pane(new BorderLayout(11, 0));
        searchLine.add(new GlossySearchBadge(), BorderLayout.WEST);
        searchLine.add(search, BorderLayout.CENTER);
        searchLine.add(count, BorderLayout.EAST);
        searchLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        searchLine.setPreferredSize(new Dimension(280, 46));

        int lowCount = (int) items.stream().filter(InventoryPage::lowStock).count();
        int expiredCount = (int) items.stream().filter(InventoryPage::isExpired).count();
        int expiringCount = (int) items.stream().filter(InventoryPage::isExpiringSoon).count();
        int depletedCount = (int) items.stream()
            .filter(item -> Storage.num(item, "quantity") <= 0).count();
        int issueCount = (int) items.stream()
            .filter(item -> lowStock(item) || Main.isExpiring(item)).count();

        JComponent alert = attentionRibbon(issueCount, depletedCount,
            lowCount, expiredCount, expiringCount);
        alert.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        alert.setPreferredSize(new Dimension(280, 42));

        FilterChip all = new FilterChip("All Items  " + items.size(), true);
        FilterChip urgent = new FilterChip("Needs Attention  " + issueCount, false);
        FilterChip low = new FilterChip("Low Stock  " + lowCount, false);
        FilterChip exp = new FilterChip("Expiring / Expired  "
            + items.stream().filter(Main::isExpiring).count(), false);
        ButtonGroup group = new ButtonGroup();
        for (FilterChip chip : List.of(all, urgent, low, exp)) group.add(chip);
        JPanel chips = pane(new FlowLayout(FlowLayout.LEFT, 7, 0));
        for (FilterChip chip : List.of(all, urgent, low, exp)) chips.add(chip);
        chips.setMaximumSize(new Dimension(Integer.MAX_VALUE, 39));
        chips.setPreferredSize(new Dimension(280, 39));

        Runnable updateFilter = () -> {
            final String keyword = search.getText().trim().toLowerCase(Locale.ROOT);
            sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                    int i = entry.getIdentifier();
                    if (i < 0 || i >= display.size()) return false;
                    Map<String, String> record = display.get(i);
                    if (urgent.isSelected() && !(lowStock(record) || Main.isExpiring(record))) return false;
                    if (low.isSelected() && !lowStock(record)) return false;
                    if (exp.isSelected() && !Main.isExpiring(record)) return false;
                    if (keyword.isEmpty()) return true;
                    for (String field : List.of("id", "name", "category", "quantity", "unit",
                            "supplier", "expiry_date", "min_stock", "inventory_display_status")) {
                        if (Objects.toString(record.get(field), "")
                                .toLowerCase(Locale.ROOT).contains(keyword)) return true;
                    }
                    return false;
                }
            });
            count.setText("Showing " + table.getRowCount() + " of " + items.size());
        };
        watch(search, updateFilter);
        all.addActionListener(e -> updateFilter.run());
        urgent.addActionListener(e -> updateFilter.run());
        low.addActionListener(e -> updateFilter.run());
        exp.addActionListener(e -> updateFilter.run());
        updateFilter.run();

        JPanel actions = pane(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (editable("inventory")) {
            JButton add = btn("+ Add Item", GREEN);
            JButton edit = secondary("Edit Selected");
            JButton move = secondary("Stock In / Out");
            JButton delete = btn("Delete Selected", RED);

            add.addActionListener(e -> form("inventory", null,
                Map.of("kind", kind, "quantity", "0", "min_stock", "0", "farm_id", "1"),
                Set.of("kind")));
            edit.addActionListener(e -> selectedEdit("inventory", parts));
            move.addActionListener(e -> stockMove(parts));
            delete.addActionListener(e -> selectedDelete("inventory", parts));

            // Keep action buttons readable. Existing Main handlers already display a
            // helpful message when the user clicks an action without selecting a row.

            actions.add(add);
            actions.add(edit);
            actions.add(move);
            actions.add(delete);
        }
        JButton export = secondary("Export CSV");
        export.addActionListener(e -> exportTable("inventory", items));
        actions.add(export);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        actions.setPreferredSize(new Dimension(280, 48));

        JPanel toolbar = pane(null);
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
        for (JComponent row : List.of(searchLine, alert, chips, actions)) {
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            toolbar.add(row);
            toolbar.add(Box.createVerticalStrut(6));
        }
        workspace.add(toolbar, BorderLayout.NORTH);

        JScrollPane scroll = parts.scroll();
        scroll.setBorder(BorderFactory.createLineBorder(new Color(191, 218, 196), 1, true));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // The table also resizes its columns to the viewport, so there is no
        // clipped off-screen table to navigate horizontally.
        scroll.getViewport().setBackground(Color.WHITE);
        GlossyTheme.styleScrollPane(scroll);
        workspace.add(scroll, BorderLayout.CENTER);

        JLabel legend = label("Red = depleted / expired / low stock     •     Amber = expiring soon     •     Green = healthy",
            11, false, SUBTEXT);
        legend.setBorder(new EmptyBorder(1, 4, 0, 0));
        JLabel selectionDetail = label("Select an item to see its full details and reorder limit.",
            11, true, INK);
        selectionDetail.setToolTipText("Select any row to view its complete item details.");
        selectionDetail.setBorder(new EmptyBorder(0, 4, 0, 0));
        table.getSelectionModel().addListSelectionListener((ListSelectionEvent event) -> {
            if (event.getValueIsAdjusting()) return;
            Map<String, String> selected = parts.selected();
            if (selected == null) {
                selectionDetail.setText("Select an item to see its full details and reorder limit.");
                return;
            }
            String full = "#" + v(selected, "id") + "  •  " + v(selected, "name")
                + "  •  Qty " + v(selected, "quantity") + " " + v(selected, "unit")
                + "  •  Min " + v(selected, "min_stock")
                + "  •  " + issueSummary(selected);
            selectionDetail.setText(shorten(full, 133));
            selectionDetail.setToolTipText(full + "  •  Supplier: " + v(selected, "supplier"));
        });
        JPanel footer = pane(new GridLayout(2, 1, 0, 1));
        footer.add(selectionDetail);
        footer.add(legend);
        workspace.add(footer, BorderLayout.SOUTH);
        return workspace;
    }

    private void stockMove(TableParts part) {
        Map<String, String> selected = part.selected();
        if (selected == null) {
            info("Select an inventory row first.");
            return;
        }
        String value = StyledDialogs.showInputDialog(frame,
            "Enter stock change for " + v(selected, "name")
                + "\nExamples: 20 = Stock In, -5 = Stock Out", "0");
        if (value == null) return;
        try {
            double delta = Double.parseDouble(value.trim());
            double quantity = Storage.num(selected, "quantity") + delta;
            if (!Double.isFinite(delta) || !Double.isFinite(quantity))
                throw new IllegalArgumentException("Enter a valid finite stock quantity.");
            if (quantity < 0) throw new IllegalArgumentException("Stock cannot be negative.");
            store.edit("inventory", v(selected, "id"), Map.of("quantity", Double.toString(quantity)));
            store.log(username(), "Stock change " + delta + " for " + v(selected, "name"));
            renderPage();
        } catch (Exception ex) {
            error(ex);
        }
    }

    private static boolean lowStock(Map<String, String> r) {
        return Storage.num(r, "quantity") <= Storage.num(r, "min_stock");
    }

    private static boolean isExpired(Map<String, String> r) {
        try {
            String date = v(r, "expiry_date").trim();
            return !date.isBlank() && LocalDate.parse(date).isBefore(LocalDate.now());
        } catch (Exception ignored) { return false; }
    }

    private static boolean isExpiringSoon(Map<String, String> r) {
        try {
            String date = v(r, "expiry_date").trim();
            if (date.isBlank()) return false;
            LocalDate expiry = LocalDate.parse(date);
            LocalDate today = LocalDate.now();
            return !expiry.isBefore(today) && !expiry.isAfter(today.plusDays(30));
        } catch (Exception ignored) { return false; }
    }

    private static String status(Map<String, String> r) {
        if (Storage.num(r, "quantity") <= 0) return "OUT OF STOCK";
        if (isExpired(r)) return "EXPIRED";
        if (lowStock(r)) return "LOW STOCK";
        if (isExpiringSoon(r)) return "EXPIRING SOON";
        return "IN STOCK";
    }

    private static boolean isCritical(String state) {
        return "OUT OF STOCK".equals(state) || "EXPIRED".equals(state)
            || "LOW STOCK".equals(state);
    }

    private static int statusRank(String value) {
        return switch (value) {
            case "OUT OF STOCK" -> 0;
            case "EXPIRED" -> 1;
            case "LOW STOCK" -> 2;
            case "EXPIRING SOON" -> 3;
            default -> 4;
        };
    }

    private static String issueSummary(Map<String, String> r) {
        List<String> flags = new ArrayList<>();
        if (Storage.num(r, "quantity") <= 0) flags.add("Out of stock");
        if (isExpired(r)) flags.add("Expired");
        if (lowStock(r)) flags.add("At/below minimum");
        if (isExpiringSoon(r)) flags.add("Expires within 30 days");
        return flags.isEmpty() ? "In stock" : String.join(", ", flags);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private static JComponent attentionRibbon(int issueCount, int depleted, int low,
            int expired, int expiring) {
        boolean urgent = issueCount > 0;
        JPanel panel = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics original) {
                Graphics2D g = (Graphics2D) original.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    if (w < 4 || h < 4) return;
                    g.setPaint(new GradientPaint(0, 1,
                        urgent ? new Color(255, 240, 239) : new Color(232, 250, 234),
                        0, h - 2, urgent ? new Color(255, 225, 224) : new Color(213, 241, 218)));
                    g.fillRoundRect(1, 1, w - 3, h - 3, 17, 17);
                    g.setColor(urgent ? new Color(215, 95, 101) : new Color(107, 175, 126));
                    g.setStroke(new BasicStroke(1.3f));
                    g.drawRoundRect(2, 2, w - 5, h - 5, 16, 16);
                } finally { g.dispose(); }
                super.paintComponent(original);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(5, 15, 5, 13));
        JLabel heading = label(urgent ? "!   STOCK NEEDS ATTENTION" : "✓   ALL STOCK LOOKS HEALTHY",
            12, true, urgent ? new Color(160, 30, 50) : INK);
        JLabel metrics = label(urgent
            ? depleted + " empty  •  " + low + " low  •  " + expired
                + " expired  •  " + expiring + " expiring soon"
            : "No low-stock or expiry issues in this category",
            11, true, urgent ? new Color(135, 48, 46) : SUBTEXT);
        metrics.setHorizontalAlignment(SwingConstants.RIGHT);
        metrics.setToolTipText("Counts can overlap when one item has multiple problems.");
        panel.add(heading, BorderLayout.WEST);
        panel.add(metrics, BorderLayout.CENTER);
        return panel;
    }

    private static double parseNumber(String text) {
        try { return Double.parseDouble(text.trim()); }
        catch (Exception ignored) { return 0; }
    }

    private static JLabel centeredHint(String value) {
        JLabel hint = label(value, 11, false, SUBTEXT);
        hint.setHorizontalAlignment(SwingConstants.RIGHT);
        return hint;
    }

    /** Matching dark-green glossy SEARCH label, styled independently of action buttons. */
    private static final class GlossySearchBadge extends JLabel {
        GlossySearchBadge() {
            super("SEARCH", SwingConstants.CENTER);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setForeground(Color.WHITE);
            setBorder(new EmptyBorder(11, 22, 13, 22));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics source) {
            Graphics2D g = (Graphics2D) source.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                if (w < 7 || h < 9) return;
                g.setColor(new Color(12, 71, 42, 80));
                g.fillRoundRect(1, 5, w - 2, h - 6, 18, 18);
                g.setPaint(new GradientPaint(0, 1, new Color(65, 184, 107),
                    0, h - 5, new Color(9, 94, 48)));
                g.fillRoundRect(1, 1, w - 2, h - 6, 18, 18);
                g.setPaint(new GradientPaint(0, 2, new Color(255, 255, 255, 118),
                    0, h / 2f, new Color(255, 255, 255, 0)));
                g.fillRoundRect(3, 2, w - 6, h / 2, 15, 15);
                g.setColor(new Color(246, 255, 247, 160));
                g.drawRoundRect(2, 2, w - 5, h - 8, 17, 17);
            } finally { g.dispose(); }
            super.paintComponent(source);
        }
    }

    /** A genuinely rounded, green-outlined search input (not a square text box). */
    private static final class GlossySearchField extends JTextField {
        private final String placeholder;
        GlossySearchField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setFont(new Font("Segoe UI", Font.PLAIN, 13));
            setForeground(INK);
            setCaretColor(INK);
            setBorder(new EmptyBorder(10, 16, 11, 14));
            setPreferredSize(new Dimension(240, 44));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
            getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                if (w < 6 || h < 6) return;
                g.setColor(new Color(16, 90, 54, 20));
                g.fillRoundRect(2, 4, w - 4, h - 5, 17, 17);
                g.setPaint(new GradientPaint(0, 1, Color.WHITE, 0, h,
                    new Color(240, 250, 241)));
                g.fillRoundRect(1, 1, w - 3, h - 4, 16, 16);
                g.setStroke(new BasicStroke(isFocusOwner() ? 2.0f : 1.3f));
                g.setColor(isFocusOwner() ? new Color(16, 136, 69)
                    : new Color(92, 162, 110));
                g.drawRoundRect(2, 2, w - 5, h - 6, 16, 16);
            } finally { g.dispose(); }
            super.paintComponent(original);
            if (!getText().isEmpty() || isFocusOwner()) return;
            Graphics2D hint = (Graphics2D) original.create();
            try {
                hint.setColor(new Color(110, 139, 118));
                hint.setFont(getFont());
                FontMetrics fm = hint.getFontMetrics();
                hint.drawString(placeholder, getInsets().left,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            } finally { hint.dispose(); }
        }
    }

    private static final class FilterChip extends JToggleButton {
        FilterChip(String name, boolean selected) {
            super(name);
            setSelected(selected);
            setFont(new Font("Segoe UI", Font.BOLD, 11));
            setForeground(INK);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 14, 8, 14));
            setToolTipText("Filter visible inventory records: " + name);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                Color upper = isSelected() ? new Color(205, 238, 211) :
                    getModel().isRollover() ? new Color(241, 250, 239) : new Color(248, 252, 248);
                Color lower = isSelected() ? new Color(166, 221, 181) : new Color(232, 243, 232);
                g.setPaint(new GradientPaint(0, 0, upper, 0, h, lower));
                g.fillRoundRect(1, 1, w - 3, h - 4, 20, 20);
                g.setColor(isSelected() ? new Color(52, 145, 81) : new Color(185, 214, 190));
                g.setStroke(new BasicStroke(isSelected() ? 1.6f : 1f));
                g.drawRoundRect(2, 2, w - 5, h - 6, 20, 20);
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }

    private static final class StockStatusRenderer extends DefaultTableCellRenderer {
        private String displayed = "";
        private boolean selected;
        private boolean alternating;

        StockStatusRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(new Font("Segoe UI", Font.BOLD, 11));
        }

        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean focused, int row, int column) {
            displayed = Objects.toString(value, "");
            selected = isSelected;
            alternating = row % 2 != 0;
            setToolTipText("Stock status: " + displayed);
            return this;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g.setColor(selected ? new Color(200, 237, 207)
                    : alternating ? new Color(241, 249, 242) : new Color(251, 255, 251));
                g.fillRect(0, 0, w, h);

                Color fill, border, text;
                switch (displayed) {
                    case "OUT OF STOCK", "EXPIRED", "LOW STOCK" -> {
                        fill = RED_TINT;
                        border = RED_LINE;
                        text = new Color(160, 29, 48);
                    }
                    case "EXPIRING SOON" -> {
                        fill = AMBER_TINT;
                        border = AMBER_LINE;
                        text = new Color(142, 85, 9);
                    }
                    default -> {
                        fill = PALE_GREEN;
                        border = new Color(131, 191, 143);
                        text = INK;
                    }
                }
                int x = 4, y = 7, bw = Math.max(1, w - 8), bh = Math.max(1, h - 14);
                g.setColor(fill);
                g.fillRoundRect(x, y, bw, bh, 18, 18);
                g.setColor(border);
                g.drawRoundRect(x, y, bw, bh, 18, 18);
                g.setFont(getFont());
                g.setColor(text);
                FontMetrics fm = g.getFontMetrics();
                int tx = Math.max(5, (w - fm.stringWidth(displayed)) / 2);
                g.drawString(displayed, tx, (h - fm.getHeight()) / 2 + fm.getAscent());
            } finally { g.dispose(); }
        }
    }
}
