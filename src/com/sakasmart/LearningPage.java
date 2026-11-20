
package com.sakasmart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.List;
import java.awt.font.FontRenderContext;

import static com.sakasmart.Main.*;

final class LearningPage extends PageBase {

    private static final Color DARK = new Color(4, 65, 34);
    private static final Color DEEP = new Color(5, 90, 46);
    private static final Color GREEN = new Color(18, 146, 68);
    private static final Color LIME = new Color(93, 221, 96);
    private static final Color MINT = new Color(224, 247, 228);
    private static final Color WHITE = new Color(252, 255, 251);
    private static final Color MUTED = new Color(88, 124, 96);
    private static final Color BORDER = new Color(141, 211, 157);

    private static final Font FONT =
        new Font("Segoe UI", Font.PLAIN, 14);

    private final Main host;

    private final Set<String> read = new HashSet<>();
    private final Set<String> checked = new HashSet<>();
    private final List<Guide> guides = new ArrayList<>();

    private JLayeredPane layer;
    private JComponent chatPanel;
    private JButton fab;
    private ComponentAdapter layerResize;
    private JTextArea chatInput;
    private boolean cleanupQueued;

    // One timer for all Learning Center slides; automatically stops off-screen.
    private javax.swing.Timer slideshowTimer;
    private Runnable seasonalSlideSync;


    private record Guide(
        String title,
        String category,
        String description,
        String content,
        String date,
        String step,
        Map<String, String> record
    ) {
        boolean builtIn() {
            return record == null && !"RESOURCE".equals(step);
        }
    }

    private static final String[][] TIPS = {
        {
            "Planting Season Reminder",
            "Check local weather forecasts before planting. " +
            "Prepare drainage and monitor soil moisture."
        },
        {
            "Healthy Soil",
            "Consider soil testing before applying fertilizer. " +
            "Good soil management supports healthier crops."
        },
        {
            "Water Conservation",
            "Water according to crop needs and soil conditions. " +
            "Inspect irrigation systems for leaks."
        },
        {
            "Crop Monitoring",
            "Regularly inspect plants for pests, unusual leaf " +
            "changes, and disease symptoms. Ask local experts."
        }
    };

    private static final String[][] CHECKS = {
        {
            "Prepare fields and tools",
            "Check land preparation, sunlight, drainage, " +
            "water and working equipment."
        },
        {
            "Check seeds and livestock supplies",
            "Check seed quality, animal feed, water " +
            "and required materials."
        },
        {
            "Review farm budget and expenses",
            "Record expected costs, available funds " +
            "and emergency allowances."
        },
        {
            "Set production and harvest targets",
            "Plan realistic yields, routine activities " +
            "and harvest schedules."
        },
        {
            "Check farm safety measures",
            "Inspect protection, product labels, " +
            "storage and safe handling."
        }
    };

    LearningPage(Main host) {
        super(host);
        this.host = host;
    }

    // ======================================================
    // LEARNING CENTER DASHBOARD
    // ======================================================

    JComponent build() {

        List<Map<String, String>> articles = rows("articles");

        guides.clear();
        guides.addAll(starterGuides());
        guides.addAll(researchGuides());

        for (Map<String, String> article : articles) {
            guides.add(new Guide(
                v(article, "title"),
                v(article, "category"),
                "Community farming article",
                v(article, "content"),
                v(article, "date"),
                "",
                article
            ));
        }

        JPanel root = col();
        root.setBorder(new EmptyBorder(23, 24, 26, 24));

        root.add(label(
            "LEARN  •  PRACTICE  •  GROW",
            13, true, GREEN
        ));

        gap(root, 7);

        root.add(label(
            "Explore farming knowledge and practical resources",
            17, false, MUTED
        ));

        gap(root, 16);

        // --------------------------------------------------
        // SEARCH BAR
        // --------------------------------------------------

        JPanel searchBox = glass(
            new BorderLayout(10, 0),
            WHITE, MINT, 24
        );

        searchBox.setBorder(
            new EmptyBorder(9, 14, 9, 12)
        );

        searchBox.add(
            new Symbol("search", GREEN),
            BorderLayout.WEST
        );

        JTextField search = new JTextField() {
            @Override protected void paintComponent(Graphics original) {
                super.paintComponent(original);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g = (Graphics2D) original.create();
                    aa(g);
                    g.setFont(getFont());
                    g.setColor(new Color(111, 151, 121));
                    Insets insets = getInsets();
                    g.drawString("Search guides, crops, livestock or farm tips...",
                        insets.left + 3,
                        insets.top + (getHeight() - g.getFontMetrics().getHeight()) / 2
                            + g.getFontMetrics().getAscent());
                    g.dispose();
                }
            }
        };
        search.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { search.repaint(); }
            @Override public void focusLost(FocusEvent e) { search.repaint(); }
        });

        search.setOpaque(false);
        search.setFont(FONT.deriveFont(15f));
        search.setForeground(DARK);
        search.setCaretColor(GREEN);
        search.setBorder(new EmptyBorder(5, 0, 5, 0));

        search.setToolTipText(
            "Search farming lessons and articles"
        );

        JButton find = button("Search", true);
        find.setPreferredSize(new Dimension(102, 43));

        searchBox.add(search, BorderLayout.CENTER);
        searchBox.add(find, BorderLayout.EAST);

        addFixed(root, searchBox, 65);
        gap(root, 11);

        // --------------------------------------------------
        // CATEGORY FILTERS
        // --------------------------------------------------

        String[] categories = {
            "All Topics",
            "Getting Started",
            "Crops",
            "Livestock",
            "Farm Finance",
            "Sustainability",
            "Safety"
        };

        JPanel filterRow = clear(
            new FlowLayout(FlowLayout.LEFT, 7, 5)
        );

        ButtonGroup group = new ButtonGroup();

        Map<String, JToggleButton> filters =
            new LinkedHashMap<>();

        for (String category : categories) {

            JToggleButton b = new Pill(category);

            b.setSelected(
                category.equals("All Topics")
            );

            group.add(b);
            filters.put(category, b);
            filterRow.add(b);
        }

        addFixed(root, filterRow, 64);
        gap(root, 18);

        // --------------------------------------------------
        // TWO-COLUMN DASHBOARD
        // --------------------------------------------------

        JPanel columns = clear(
            new BorderLayout(19, 0)
        );

        JPanel left = col();
        JPanel right = col();

        right.setPreferredSize(
            new Dimension(322, 1000)
        );

        right.setMinimumSize(
            new Dimension(265, 750)
        );

        Guide featured = guides.get(0);

        addFixed(
            left,
            new Hero(
                featured,
                () -> openGuide(featured)
            ),
            248
        );

        gap(left, 19);

        JPanel resourcesHeader = clear(
            new BorderLayout()
        );

        resourcesHeader.add(
            label(
                "Explore Learning Resources",
                20, true, DARK
            ),
            BorderLayout.WEST
        );

        JLabel resultCount = label(
            "", 12, true, GREEN
        );

        resourcesHeader.add(
            resultCount,
            BorderLayout.EAST
        );

        addFixed(left, resourcesHeader, 40);

        // --------------------------------------------------
        // RESOURCE TILES
        // --------------------------------------------------

        JPanel tiles = clear(
            new GridLayout(0, 2, 12, 12)
        );

        // Keep resource cards compact even when a category has only a few items.
        JPanel resourceStack = clear(new BorderLayout());
        resourceStack.add(tiles, BorderLayout.NORTH);
        JScrollPane resourceScroll =
            new JScrollPane(resourceStack);

        resourceScroll.setBorder(null);
        resourceScroll.setOpaque(false);
        resourceScroll.getViewport().setOpaque(false);

        resourceScroll.setHorizontalScrollBarPolicy(
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );

        scroll(resourceScroll);

        JPanel resourceBorder = glass(
            new BorderLayout(),
            WHITE,
            new Color(238, 250, 239),
            22
        );

        resourceBorder.setBorder(
            new EmptyBorder(13, 13, 13, 13)
        );

        resourceBorder.add(resourceScroll);

        addFixed(left, resourceBorder, 545);

        // --------------------------------------------------
        // LIVE SEARCH / FILTERS
        // --------------------------------------------------

        Runnable refresh = () -> {

            String keyword = search.getText()
                .trim()
                .toLowerCase(Locale.ROOT);

            String category = "All Topics";

            for (var entry : filters.entrySet()) {
                if (entry.getValue().isSelected()) {
                    category = entry.getKey();
                    break;
                }
            }

            tiles.removeAll();

            List<Guide> matches = new ArrayList<>();

            for (Guide guide : guides) {

                if (!category.equals("All Topics") &&
                    !category.equals(guide.category())) {
                    continue;
                }

                String target = (
                    guide.title() + " " +
                    guide.category() + " " +
                    guide.description() + " " +
                    guide.content()
                ).toLowerCase(Locale.ROOT);

                if (keyword.isEmpty() ||
                    target.contains(keyword)) {

                    matches.add(guide);
                }
            }

            resultCount.setText(
                matches.size() + " RESOURCES"
            );

            if (matches.isEmpty()) {

                tiles.setLayout(new BorderLayout());
                tiles.setPreferredSize(new Dimension(650, 110));

                tiles.add(
                    label(
                        "No matching resources. Try another search.",
                        13, false, MUTED
                    ),
                    BorderLayout.NORTH
                );

            } else {

                tiles.setLayout(
                    new GridLayout(0, 2, 12, 12)
                );
                // Prevent GridLayout from stretching two rows into giant empty cards.
                tiles.setPreferredSize(new Dimension(650,
                    Math.max(1, (matches.size() + 1) / 2) * 154));

                for (Guide guide : matches) {

                    tiles.add(new Tile(
                        guide,
                        read.contains(guide.title()),
                        () -> { if ("RESOURCE".equals(guide.step())) openResearchGuide(guide); else openGuide(guide); }
                    ));
                }
            }

            tiles.revalidate();
            tiles.repaint();

            SwingUtilities.invokeLater(() ->
                resourceScroll
                    .getVerticalScrollBar()
                    .setValue(0)
            );
        };

        search.getDocument().addDocumentListener(
            new DocumentListener() {

                public void insertUpdate(DocumentEvent e) {
                    refresh.run();
                }

                public void removeUpdate(DocumentEvent e) {
                    refresh.run();
                }

                public void changedUpdate(DocumentEvent e) {
                    refresh.run();
                }
            }
        );

        find.addActionListener(e -> refresh.run());

        for (JToggleButton b : filters.values()) {
            b.addActionListener(e -> refresh.run());
        }

        refresh.run();

        // --------------------------------------------------
        // RIGHT SIDEBAR
        // --------------------------------------------------

        addFixed(right, aiShortcut(), 95);
        gap(right, 12);

        addFixed(
            right,
            sideCard(
                "Seasonal Tips",
                seasonalTips()
            ),
                235
        );

        gap(right, 12);

        addFixed(
            right,
            sideCard(
                "Quick Checklists",
                quickChecks()
            ),
            365
        );

        gap(right, 12);

        addFixed(
            right,
            sideCard(
                "Recently Updated",
                recent(articles)
            ),
            249
        );

        gap(right, 12);

        if (admin()) {

            addFixed(
                right,
                sideCard(
                    "Article Management",
                    articleTools(articles)
                ),
                190
            );

            gap(right, 12);
        }

        columns.add(left, BorderLayout.CENTER);
        columns.add(right, BorderLayout.EAST);

        JPanel slot = clear(new BorderLayout());
        slot.add(columns, BorderLayout.CENTER);

        setFixedSize(
            slot,
            admin() ? 1300 : 1165
        );

        root.add(slot);
        gap(root, 13);

        addFixed(
            root,
            sideCard(
                "A Note for Farmers",
                label(
                    "Educational guidance only. Confirm important " +
                    "farming decisions with local experts.",
                    12, false, MUTED
                )
            ),
            85
        );

        // --------------------------------------------------
        // RESPONSIVE LAYOUT
        // --------------------------------------------------

        final boolean[] stacked = {false};

        Runnable adapt = () -> {

            int width = root.getWidth();

            if (width <= 0) return;

            boolean narrow = width < 940;

            if (narrow == stacked[0]) return;

            stacked[0] = narrow;

            columns.removeAll();

            if (narrow) {

                columns.setLayout(
                    new BorderLayout(0, 16)
                );

                columns.add(left, BorderLayout.CENTER);
                columns.add(right, BorderLayout.SOUTH);

                right.setPreferredSize(
                    new Dimension(
                        1,
                        admin() ? 1280 : 1090
                    )
                );

                setFixedSize(
                    slot,
                    admin() ? 2520 : 2350
                );

            } else {

                columns.setLayout(
                    new BorderLayout(19, 0)
                );

                columns.add(left, BorderLayout.CENTER);
                columns.add(right, BorderLayout.EAST);

                right.setPreferredSize(
                    new Dimension(322, 1000)
                );

                setFixedSize(
                    slot,
                    admin() ? 1300 : 1165
                );
            }

            columns.revalidate();
            root.revalidate();
            root.repaint();
        };

        root.addComponentListener(
            new ComponentAdapter() {

                @Override
                public void componentResized(ComponentEvent e) {
                    adapt.run();
                }
            }
        );

        SwingUtilities.invokeLater(adapt);

        // --------------------------------------------------
        // FLOATING AI LIFECYCLE
        // --------------------------------------------------

        if (slideshowTimer != null) slideshowTimer.stop();
        slideshowTimer = new javax.swing.Timer(110, event -> {
            if (seasonalSlideSync != null) seasonalSlideSync.run();
            repaintSlides(root);
        });
        slideshowTimer.setCoalesce(true);

        root.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
            if (root.isShowing()) {
                if (slideshowTimer != null) slideshowTimer.start();
                SwingUtilities.invokeLater(() -> {
                    if (!root.isShowing()) return;
                    JScrollPane pane = (JScrollPane)
                        SwingUtilities.getAncestorOfClass(JScrollPane.class, root);
                    if (pane != null) pane.getViewport().setViewPosition(new Point(0, 0));
                    showFab();
                });
            } else {
                if (slideshowTimer != null) slideshowTimer.stop();
                scheduleCleanup();
            }
        });

        return root;
    }

    // ======================================================
    // AI SHORTCUT CARD
    // ======================================================

    private JComponent aiShortcut() {

        JButton b = new JButton(
            "SAKA-SMART AI ASSISTANT"
        );

        b.setFont(
            FONT.deriveFont(Font.BOLD, 14f)
        );

        b.setForeground(Color.WHITE);
        b.setHorizontalAlignment(SwingConstants.LEFT);

        b.setBorder(
            new EmptyBorder(13, 57, 13, 11)
        );

        b.setCursor(
            Cursor.getPredefinedCursor(
                Cursor.HAND_CURSOR
            )
        );

        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setIcon(null); // Robot is rendered by the glossy background, not a checkbox.

        b.addActionListener(e -> showAi());

        JPanel wrap = clear(new BorderLayout());
        wrap.add(b);

        return new JComponent() {

            {
                setLayout(new BorderLayout());
                add(wrap);
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics old) {

                Graphics2D g =
                    (Graphics2D) old.create();

                aa(g);

                int w = getWidth();
                int h = getHeight();

                g.setPaint(new GradientPaint(
                    0, 0,
                    new Color(24, 171, 83),
                    0, h, DARK
                ));

                g.fillRoundRect(
                    1, 1, w - 3, h - 4, 24, 24
                );

                g.setColor(
                    new Color(255, 255, 255, 110)
                );

                g.drawRoundRect(
                    2, 2, w - 5, h - 7, 24, 24
                );

                drawRobot(g, 29, h / 2, 0.8);

                g.dispose();
            }
        };
    }

    // ======================================================
    // SEASONAL TIPS
    // ======================================================

    private JPanel seasonalTips() {
        JPanel panel = clear(new BorderLayout(13, 9));
        JPanel tipBody = clear(new BorderLayout(12, 0));
        PhotoArt art = new PhotoArt(new String[]{
            "tip_planting.png", "tip_soil.png", "tip_water.png", "tip_monitoring.png"
        }, "weather");
        art.setPreferredSize(new Dimension(108, 122));
        tipBody.add(art, BorderLayout.WEST);

        JPanel words = col();
        JTextArea title = area(TIPS[0][0], 14, DARK);
        title.setFont(FONT.deriveFont(Font.BOLD, 14f));
        JTextArea desc = area(TIPS[0][1], 12, MUTED);
        words.add(title);
        gap(words, 8);
        words.add(desc);
        tipBody.add(words, BorderLayout.CENTER);
        panel.add(tipBody, BorderLayout.CENTER);

        JButton next = button("Next farming tip  >", false);
        next.setPreferredSize(new Dimension(100, 39));
        panel.add(next, BorderLayout.SOUTH);

        final int[] lastTip = {-1};
        seasonalSlideSync = () -> {
            int current = art.slideIndex();
            if (current != lastTip[0]) {
                lastTip[0] = current;
                title.setText(TIPS[current][0]);
                desc.setText(TIPS[current][1]);
                words.revalidate();
                words.repaint();
            }
        };
        seasonalSlideSync.run();
        next.addActionListener(e -> {
            art.advance();
            seasonalSlideSync.run();
        });
        return panel;
    }

    // ======================================================
    // INTERACTIVE CHECKLISTS
    // ======================================================

    private JPanel quickChecks() {
        JPanel p = col();
        JLabel progress = label(checked.size() + " / " + CHECKS.length + " completed",
            12, true, GREEN);
        p.add(progress);
        gap(p, 9);

        JProgressBar progressBar = new JProgressBar(0, CHECKS.length);
        progressBar.setValue(checked.size());
        progressBar.setForeground(new Color(25, 177, 84));
        progressBar.setBackground(new Color(204, 238, 211));
        progressBar.setOpaque(true);
        progressBar.setBorderPainted(false);
        addFixed(p, progressBar, 10);
        gap(p, 13);

        for (String[] item : CHECKS) {
            JCheckBox check = new JCheckBox(item[0]);
            check.setFont(FONT.deriveFont(12.5f));
            check.setOpaque(false);
            check.setForeground(DARK);
            check.setFocusPainted(false);
            check.setSelected(checked.contains(item[0]));
            check.setToolTipText(item[1]);
            check.setBorder(new EmptyBorder(1, 1, 1, 0));
            Icon marker = new Icon() {
                public int getIconWidth() { return 24; }
                public int getIconHeight() { return 23; }
                public void paintIcon(Component component, Graphics original, int x, int y) {
                    Graphics2D g = (Graphics2D) original.create();
                    aa(g);
                    boolean on = check.isSelected();
                    g.setColor(on ? new Color(33, 173, 80) : WHITE);
                    g.fillRoundRect(x + 2, y + 2, 18, 18, 7, 7);
                    g.setColor(on ? new Color(10, 120, 52) : BORDER);
                    g.setStroke(new BasicStroke(1.3f));
                    g.drawRoundRect(x + 2, y + 2, 18, 18, 7, 7);
                    if (on) {
                        g.setColor(Color.WHITE);
                        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                        g.drawLine(x + 6, y + 11, x + 10, y + 15);
                        g.drawLine(x + 10, y + 15, x + 17, y + 7);
                    }
                    g.dispose();
                }
            };
            check.setIcon(marker);
            check.setSelectedIcon(marker);
            check.addActionListener(e -> {
                if (check.isSelected()) checked.add(item[0]);
                else checked.remove(item[0]);
                progress.setText(checked.size() + " / " + CHECKS.length + " completed");
                progressBar.setValue(checked.size());
                check.repaint();
            });
            addFixed(p, check, 34);
            gap(p, 4);
        }

        gap(p, 6);
        JButton details = button("View checklist details  >", false);
        details.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            for (String[] item : CHECKS)
                sb.append(item[0]).append("\n").append(item[1]).append("\n\n");
            JTextArea text = area(sb.toString(), 14, DARK);
            JScrollPane scrollPane = new JScrollPane(text);
            scrollPane.setPreferredSize(new Dimension(460, 320));
            scroll(scrollPane);
            StyledDialogs.showMessageDialog(host.frame, scrollPane,
                "Farm Checklists", JOptionPane.PLAIN_MESSAGE);
        });
        addFixed(p, details, 42);
        return p;
    }

    // ======================================================
    // RECENT ARTICLES
    // ======================================================

    private JPanel recent(
        List<Map<String, String>> articles
    ) {

        JPanel p = col();

        List<Map<String, String>> sorted =
            new ArrayList<>(articles);

        sorted.sort(
            (a, b) ->
                v(b, "date").compareTo(
                    v(a, "date")
                )
        );

        if (sorted.isEmpty()) {

            p.add(label(
                "No published articles yet.",
                13, false, MUTED
            ));

            return p;
        }

        for (int i = 0;
             i < Math.min(3, sorted.size());
             i++) {

            Map<String, String> article =
                sorted.get(i);

            JButton b = button(
                shorten(
                    v(article, "title"), 29
                ),
                false
            );

            b.setToolTipText(
                v(article, "title") +
                "  •  " +
                v(article, "date")
            );

            b.addActionListener(e ->
                openGuide(new Guide(
                    v(article, "title"),
                    v(article, "category"),
                    "Community farming article",
                    v(article, "content"),
                    v(article, "date"),
                    "",
                    article
                ))
            );

            addFixed(p, b, 45);
            gap(p, 7);
        }

        return p;
    }

    // ======================================================
    // ARTICLE MANAGEMENT
    // ======================================================

    private JPanel articleTools(
        List<Map<String, String>> articles
    ) {

        JPanel p = col();

        JButton add = button("Add Article", true);
        JButton edit = button("Edit Article", false);
        JButton del = button("Delete Article", false);

        add.addActionListener(e ->
            form(
                "articles",
                null,
                Map.of(
                    "category", "Crops",
                    "date", today()
                ),
                Set.of()
            )
        );

        edit.addActionListener(e ->
            chooseArticle(articles, false)
        );

        del.addActionListener(e ->
            chooseArticle(articles, true)
        );

        addFixed(p, add, 38);
        gap(p, 6);

        addFixed(p, edit, 38);
        gap(p, 6);

        addFixed(p, del, 38);

        return p;
    }

    private void chooseArticle(
        List<Map<String, String>> articles,
        boolean delete
    ) {

        if (articles.isEmpty()) {
            info("No articles available.");
            return;
        }

        JComboBox<String> choices =
            new JComboBox<>();

        for (int i = 0; i < articles.size(); i++) {

            choices.addItem(
                (i + 1) + ". " +
                v(articles.get(i), "title")
            );
        }

        int response =
            StyledDialogs.showConfirmDialog(
                host.frame,
                choices,
                delete ? "Delete Article" : "Edit Article",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

        if (response != JOptionPane.OK_OPTION) {
            return;
        }

        int index = choices.getSelectedIndex();

        if (index < 0) return;

        Map<String, String> article =
            articles.get(index);

        if (delete) {

            int confirmation =
                StyledDialogs.showConfirmDialog(
                    host.frame,
                    "Delete this article?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );

            if (confirmation == JOptionPane.YES_OPTION) {
                deleteRow("articles", article);
            }

        } else {

            form(
                "articles",
                article,
                Map.of(),
                Set.of()
            );
        }
    }

    // ======================================================
    // LESSON READER
    // ======================================================

    // ======================================================
    // GLOSSY GREEN LEARNING READER - SIX LESSONS + ARTICLES
    // ======================================================

    // SAKA_TIMELINE_READER_V9 — aligned takeaway, richer observation guide, high-contrast readable body text.
    // This changes the reader presentation only; database, AI and dashboard stay intact.
    private void openGuide(Guide chosen) {
        List<Guide> course = new ArrayList<>();
        for (Guide guide : guides) {
            if (guide.builtIn() == chosen.builtIn()) course.add(guide);
        }
        if (course.isEmpty()) course.add(chosen);
        int first = 0;
        for (int n = 0; n < course.size(); n++) {
            if (course.get(n).title().equals(chosen.title())) { first = n; break; }
        }
        final int[] lesson = {first};
        final int[] focusStep = {0};
        final boolean beginner = chosen.builtIn();
        final int total = course.size();
        final Runnable[] refresh = new Runnable[1];
        // Keep checklist selections while exploring this reader; no database writes.
        final Map<String, boolean[]> actionChecks = new HashMap<>();

        JDialog dialog = new JDialog(host.frame, "SAKA-SMART | Learning Reader",
            Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(new Color(234, 250, 237));
        root.setBorder(new EmptyBorder(12, 15, 11, 15));

        JPanel header = readerCard(new BorderLayout(0, 7),
            new Color(12, 127, 59), new Color(2, 74, 39), 29);
        header.setBorder(new EmptyBorder(11, 19, 12, 19));
        JPanel headerTop = clear(new BorderLayout(10, 0));
        JPanel headerChips = clear(new FlowLayout(FlowLayout.LEFT, 7, 0));
        JLabel category = readerChip("", false);
        JLabel lessonChip = readerChip("", true);
        headerChips.add(category);
        headerChips.add(lessonChip);
        headerTop.add(headerChips, BorderLayout.WEST);
        JLabel counter = label("", 13, true, new Color(231, 255, 236));
        headerTop.add(counter, BorderLayout.EAST);
        header.add(headerTop, BorderLayout.NORTH);

        JPanel heading = clear(new BorderLayout(0, 5));
        JTextArea title = readerText("", 24, true, Color.WHITE);
        title.setRows(1);
        JTextArea summary = readerText("", 15, false, new Color(235, 255, 236));
        summary.setRows(1);
        heading.add(title, BorderLayout.CENTER);
        heading.add(summary, BorderLayout.SOUTH);
        header.add(heading, BorderLayout.CENTER);

        JPanel headerProgress = clear(new BorderLayout(0, 5));
        JProgressBar progress = new JProgressBar(0, Math.max(1, total));
        progress.setForeground(new Color(129, 239, 142));
        progress.setBackground(new Color(58, 141, 91));
        progress.setBorderPainted(false);
        progress.setPreferredSize(new Dimension(100, 7));
        headerProgress.add(progress, BorderLayout.NORTH);
        JPanel lessons = clear(new GridLayout(1, Math.max(1, total), 7, 0));
        lessons.setPreferredSize(new Dimension(1, 26));
        headerProgress.add(lessons, BorderLayout.CENTER);
        header.add(headerProgress, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        ReaderColumn body = new ReaderColumn();
        body.setBorder(new EmptyBorder(1, 2, 12, 8));
        JScrollPane viewport = new JScrollPane(body);
        viewport.setOpaque(false);
        viewport.getViewport().setOpaque(false);
        viewport.setBorder(null);
        viewport.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll(viewport);
        root.add(viewport, BorderLayout.CENTER);

        JPanel footer = readerCard(new BorderLayout(10, 0),
            new Color(248, 255, 246), new Color(204, 242, 211), 26);
        footer.setBorder(new EmptyBorder(7, 12, 7, 12));
        JLabel hint = label("Select a timeline step to explore your lesson", 12, false, MUTED);
        footer.add(hint, BorderLayout.WEST);
        JPanel actions = clear(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        JButton previous = button("Previous Lesson", false);
        JButton mark = button("Mark as Read", false);
        JButton next = button("Next Lesson", true);
        JButton close = button("Close", false);
        for (JButton item : new JButton[]{previous, mark, next, close}) {
            item.setFont(FONT.deriveFont(Font.BOLD, 12f));
            item.setPreferredSize(new Dimension(item == previous ? 140 :
                item == next ? 128 : item == mark ? 118 : 83, 38));
            actions.add(item);
        }
        footer.add(actions, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        refresh[0] = () -> {
            Guide guide = course.get(lesson[0]);
            GuideViewParts parts = splitGuideContent(guide);
            List<String> instructions = parts.steps();
            if (instructions.isEmpty()) instructions = List.of(
                "Read the article and identify one idea you can use safely.");
            final List<String> steps = instructions;
            focusStep[0] = Math.max(0, Math.min(focusStep[0], steps.size() - 1));
            category.setText(guide.category().isBlank() ? "Farming Resource" : guide.category());
            lessonChip.setText(beginner ? "BEGINNER COURSE" : "COMMUNITY ARTICLE");
            counter.setText((beginner ? "LESSON " : "ARTICLE ") + (lesson[0] + 1) + " / " + total);
            title.setText(guide.title());
            summary.setText(guide.description());
            progress.setMaximum(Math.max(1, total));
            progress.setValue(lesson[0] + 1);
            lessons.removeAll();
            for (int i = 0; i < total; i++) {
                final int target = i;
                JButton chip = timelineButton(String.valueOf(i + 1), i == lesson[0], i < lesson[0]);
                chip.setToolTipText(course.get(i).title());
                chip.addActionListener(e -> {
                    lesson[0] = target;
                    focusStep[0] = 0;
                    refresh[0].run();
                });
                lessons.add(chip);
            }
            lessons.revalidate();
            lessons.repaint();
            body.removeAll();
            if (beginner) {
                // This horizontal stepper represents the FIVE actions inside ONE lesson.
                JPanel journey = readerCard(new BorderLayout(0, 9),
                    new Color(253, 255, 252), new Color(215, 248, 222), 26);
                journey.setBorder(new EmptyBorder(12, 15, 12, 15));
                JPanel journeyHeader = clear(new BorderLayout());
                journeyHeader.add(label("YOUR FARMING JOURNEY", 14, true, DEEP), BorderLayout.WEST);
                JLabel stepStatus = label("ACTION " + (focusStep[0] + 1) + " OF " + steps.size(),
                    12, true, GREEN);
                journeyHeader.add(stepStatus, BorderLayout.EAST);
                journey.add(journeyHeader, BorderLayout.NORTH);
                JPanel nodeRow = clear(new GridLayout(1, steps.size(), 7, 0));
                for (int i = 0; i < steps.size(); i++) {
                    final int chosenStep = i;
                    JPanel node = clear(new BorderLayout(0, 4));
                    JButton stepButton = timelineButton(
                        timelineStepName(guide, i), i == focusStep[0], i < focusStep[0]);
                    stepButton.setFont(FONT.deriveFont(Font.BOLD, 11.5f));
                    stepButton.setIcon(journeyIcon(i, i == focusStep[0], 23));
                    stepButton.setIconTextGap(8);
                    stepButton.setToolTipText(steps.get(i));
                    stepButton.addActionListener(e -> {
                        focusStep[0] = chosenStep;
                        refresh[0].run();
                    });
                    node.add(stepButton, BorderLayout.CENTER);
                    node.add(label("STEP " + (i + 1), 10, true, MUTED), BorderLayout.SOUTH);
                    nodeRow.add(node);
                }
                journey.add(nodeRow, BorderLayout.CENTER);
                addReaderSection(body, journey, 112);
                body.add(Box.createVerticalStrut(10));

                // One active action is in focus, not five repeated giant cards.
                JPanel feature = clear(new BorderLayout(12, 0));
                JPanel main = readerCard(new BorderLayout(11, 0),
                    new Color(250, 255, 249), new Color(213, 247, 224), 28);
                main.setBorder(new EmptyBorder(12, 13, 12, 14));
                PhotoArt image = new PhotoArt(photosFor(guide), guide.title());
                image.setPreferredSize(new Dimension(235, 220));
                main.add(image, BorderLayout.WEST);
                JPanel explanation = clear(new BorderLayout(0, 10));
                JPanel introduction = clear(new BorderLayout(0, 8));
                introduction.add(readerHeader("ACTION " + (focusStep[0] + 1) + "  •  " + timelineStepName(guide, focusStep[0]).replaceFirst("^\\d+\\s+", ""), focusStep[0]), BorderLayout.NORTH);
                introduction.add(readerText(steps.get(focusStep[0]), 18, true, DARK), BorderLayout.CENTER);
                explanation.add(introduction, BorderLayout.NORTH);
                JPanel tip = readerCard(new BorderLayout(0, 6),
                    new Color(233, 254, 224), new Color(186, 239, 194), 21);
                tip.setBorder(new EmptyBorder(11, 12, 11, 12));
                tip.add(readerHeader("PRACTICAL FARMING TIP", 0), BorderLayout.NORTH);
                tip.add(readerText(stepExpanded(guide, focusStep[0]), 16, false, DARK), BorderLayout.CENTER);
                explanation.add(tip, BorderLayout.CENTER);
                JPanel focusActions = clear(new FlowLayout(FlowLayout.RIGHT, 7, 0));
                JButton backStep = button("Back a Step", false);
                JButton nextStep = button(focusStep[0] == steps.size() - 1
                    ? "Review Steps" : "Next Step  >", true);
                backStep.setEnabled(focusStep[0] > 0);
                backStep.addActionListener(e -> {
                    if (focusStep[0] > 0) { focusStep[0]--; refresh[0].run(); }
                });
                nextStep.addActionListener(e -> {
                    focusStep[0] = (focusStep[0] + 1) % steps.size();
                    refresh[0].run();
                });
                backStep.setPreferredSize(new Dimension(110, 36));
                nextStep.setPreferredSize(new Dimension(115, 36));
                focusActions.add(backStep);
                focusActions.add(nextStep);
                explanation.add(focusActions, BorderLayout.SOUTH);
                main.add(explanation, BorderLayout.CENTER);
                feature.add(main, BorderLayout.CENTER);

                JPanel sidebar = clear(new GridLayout(2, 1, 0, 9));
                JPanel goal = readerCard(new BorderLayout(0, 8),
                    new Color(242, 255, 231), new Color(204, 244, 198), 25);
                goal.setBorder(new EmptyBorder(14, 15, 14, 15));
                goal.add(readerHeader("LESSON OVERVIEW", 1), BorderLayout.NORTH);
                goal.add(readerText(parts.intro().isBlank() ? guide.description() :
                    parts.intro() + "\n" + guide.description(), 15, false, DARK),
                    BorderLayout.CENTER);
                sidebar.add(goal);
                JPanel reminder = readerCard(new BorderLayout(0, 8),
                    new Color(229, 252, 237), new Color(187, 230, 210), 25);
                reminder.setBorder(new EmptyBorder(14, 15, 14, 15));
                reminder.add(readerHeader("QUICK REMINDER", 2), BorderLayout.NORTH);
                reminder.add(readerText(lessonExtra(guide, 0), 15, false, DARK), BorderLayout.CENTER);
                sidebar.add(reminder);
                sidebar.setPreferredSize(new Dimension(275, 265));
                feature.add(sidebar, BorderLayout.EAST);
                addReaderSection(body, feature, 350);
                body.add(Box.createVerticalStrut(11));

                JPanel info = clear(new GridLayout(1, 3, 10, 0));
                info.add(readerInsightCard(guide, "WHY IT MATTERS", lessonExtra(guide, 0),
                    new Color(240, 255, 236), new Color(186, 236, 191)));
                info.add(readerInsightCard(guide, "PREPARE THESE", lessonExtra(guide, 1),
                    new Color(245, 255, 229), new Color(216, 244, 178)));
                info.add(readerInsightCard(guide, "AVOID THIS MISTAKE", lessonExtra(guide, 2),
                    new Color(246, 253, 242), new Color(208, 232, 206)));
                addReaderSection(body, info, 262);
                body.add(Box.createVerticalStrut(10));

                // V7: photo-rich field observations, unique to each of the six lessons.
                addReaderSection(body, readerVisualFieldGuide(guide), 390);
                body.add(Box.createVerticalStrut(10));
                addReaderSection(body, readerTakeawayStrip(guide), 92);
                body.add(Box.createVerticalStrut(10));

                JPanel details = clear(new GridLayout(1, 2, 10, 0));
                details.add(readerDetailCard("FARMER'S FIELD NOTES", readerDetail(guide, 0),
                    new Color(245, 255, 240), new Color(205, 242, 202), 1));
                details.add(readerDetailCard("KEY FARMING WORDS", readerDetail(guide, 1),
                    new Color(237, 255, 248), new Color(191, 231, 215), 2));
                addReaderSection(body, details, 200);
                body.add(Box.createVerticalStrut(10));

                // All five original instructions remain visible together here for review.
                JPanel recap = readerCard(new BorderLayout(0, 9),
                    new Color(252, 255, 250), new Color(224, 248, 225), 26);
                recap.setBorder(new EmptyBorder(12, 15, 12, 15));
                recap.add(readerHeader("REVIEW ALL FIVE ACTIONS", 3), BorderLayout.NORTH);
                JPanel recapRows = clear(new GridLayout(0, 1, 0, 6));
                for (int i = 0; i < steps.size(); i++) {
                    recapRows.add(buildLessonStepItem(i + 1, steps.get(i), stepExpanded(guide, i)));
                }
                recap.add(recapRows, BorderLayout.CENTER);
                addReaderSection(body, recap, 62 + steps.size() * 126);
                body.add(Box.createVerticalStrut(10));

                addReaderSection(body, readerActionPlan(guide, actionChecks), 378);
                body.add(Box.createVerticalStrut(10));
                JPanel outcome = clear(new GridLayout(1, 2, 12, 0));
                JPanel example = readerCard(new BorderLayout(0, 10),
                    new Color(255, 255, 245), new Color(230, 246, 213), 26);
                example.setBorder(new EmptyBorder(14, 17, 14, 17));
                example.add(readerHeader("REAL-LIFE EXAMPLE", 1), BorderLayout.NORTH);
                JPanel exampleContent = clear(new BorderLayout(0, 9));
                JPanel realStory = clear(new BorderLayout(0, 6));
                realStory.add(label("A FARMER'S STORY", 11, true, GREEN), BorderLayout.NORTH);
                realStory.add(readerText(parts.example(), 15, false, DARK), BorderLayout.CENTER);
                exampleContent.add(realStory, BorderLayout.CENTER);
                JPanel doThis = clear(new BorderLayout(0, 4));
                doThis.add(label("YOUR TURN  •  TRY THIS", 11, true, GREEN), BorderLayout.NORTH);
                doThis.add(readerText(readerOutcomeExtra(guide, 0), 15, false,
                    new Color(42, 101, 61)), BorderLayout.CENTER);
                exampleContent.add(doThis, BorderLayout.SOUTH);
                example.add(exampleContent, BorderLayout.CENTER);
                outcome.add(example);
                JPanel checkpoint = readerCard(new BorderLayout(0, 10),
                    new Color(237, 254, 242), new Color(190, 235, 208), 26);
                checkpoint.setBorder(new EmptyBorder(14, 17, 14, 17));
                checkpoint.add(readerHeader("REFLECT & REMEMBER", 5), BorderLayout.NORTH);
                JPanel reflection = clear(new BorderLayout(0, 9));
                JPanel think = clear(new BorderLayout(0, 6));
                think.add(label("THINK ABOUT IT", 11, true, GREEN), BorderLayout.NORTH);
                think.add(readerText(checkQuestion(guide, parts.checkpoint()), 16, true, DARK),
                    BorderLayout.CENTER);
                reflection.add(think, BorderLayout.CENTER);
                JPanel takeAway = clear(new BorderLayout(0, 4));
                takeAway.add(label("BEFORE THE NEXT LESSON", 11, true, GREEN), BorderLayout.NORTH);
                takeAway.add(readerText(readerOutcomeExtra(guide, 1), 15, false,
                    new Color(48, 109, 70)), BorderLayout.CENTER);
                reflection.add(takeAway, BorderLayout.SOUTH);
                checkpoint.add(reflection, BorderLayout.CENTER);
                outcome.add(checkpoint);
                addReaderSection(body, outcome, 225);
                body.add(Box.createVerticalStrut(10));
                addReaderSection(body, readerPracticeCard(guide), 195);
            } else {
                PhotoArt photo = new PhotoArt(photosFor(guide), guide.title());
                addReaderSection(body, photo, 210);
                body.add(Box.createVerticalStrut(12));
                JPanel article = readerCard(new BorderLayout(0, 12),
                    WHITE, new Color(239, 252, 240), 24);
                article.setBorder(new EmptyBorder(24, 26, 24, 26));
                article.add(label("FARMING ARTICLE", 14, true, GREEN), BorderLayout.NORTH);
                article.add(readerText(guide.content(), 16, false, DARK), BorderLayout.CENTER);
                addReaderSection(body, article, Math.min(2200,
                    Math.max(250, 100 + guide.content().length() / 75 * 30)));
            }
            previous.setEnabled(lesson[0] > 0);
            next.setEnabled(lesson[0] < total - 1);
            next.setText(lesson[0] == total - 1 ? "Last Lesson" : "Next Lesson");
            mark.setText(read.contains(guide.title()) ? "Read ✓" : "Mark as Read");
            body.revalidate();
            body.repaint();
            header.revalidate();
            header.repaint();
            SwingUtilities.invokeLater(() -> viewport.getViewport()
                .setViewPosition(new Point(0, 0)));
        };
        previous.addActionListener(e -> {
            if (lesson[0] > 0) { lesson[0]--; focusStep[0] = 0; refresh[0].run(); }
        });
        next.addActionListener(e -> {
            if (lesson[0] < total - 1) {
                lesson[0]++; focusStep[0] = 0; refresh[0].run();
            }
        });
        mark.addActionListener(e -> {
            read.add(course.get(lesson[0]).title());
            refresh[0].run();
        });
        close.addActionListener(e -> dialog.dispose());
        refresh[0].run();
        dialog.setContentPane(root);
        Dimension desktop = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(1260, desktop.width - 55),
            Math.min(820, desktop.height - 55));
        dialog.setMinimumSize(new Dimension(970, 570));
        dialog.setLocationRelativeTo(host.frame);
        dialog.setVisible(true);
    }


    // SAKA_READER_JOURNEY_V4 — vector-drawn icons; no extra files or icon fonts required.
    private static Icon journeyIcon(int kind, boolean selected, int side) {
        return new Icon() {
            public int getIconWidth() { return side; }
            public int getIconHeight() { return side; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                aa(g);
                g.translate(x, y);
                float scale = side / 36f;
                g.scale(scale, scale);
                Color dark = selected ? Color.WHITE : new Color(7, 110, 52);
                g.setPaint(new GradientPaint(0, 0,
                    selected ? new Color(31, 160, 72) : new Color(222, 255, 226),
                    36, 36, selected ? new Color(4, 92, 46) : new Color(184, 235, 196)));
                g.fillRoundRect(1, 1, 34, 34, 13, 13);
                g.setColor(selected ? new Color(134, 248, 147) : new Color(120, 201, 142));
                g.drawRoundRect(1, 1, 33, 33, 12, 12);
                g.setColor(dark);
                g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (Math.floorMod(kind, 5)) {
                    case 0 -> { // goal / bullseye
                        g.drawOval(8, 8, 19, 19); g.drawOval(13, 13, 9, 9);
                        g.drawLine(18, 18, 29, 6); g.drawLine(25, 6, 29, 6);
                    }
                    case 1 -> { // two sprouting leaves
                        g.drawLine(18, 29, 18, 15);
                        g.drawOval(7, 11, 13, 9); g.drawOval(18, 9, 12, 10);
                        g.drawLine(10, 30, 26, 30);
                    }
                    case 2 -> { // resources: crossed tool + droplet
                        g.drawLine(9, 27, 25, 11); g.drawLine(9, 11, 25, 27);
                        g.drawOval(22, 7, 7, 7); g.drawLine(6, 8, 13, 15);
                    }
                    case 3 -> { // budget wallet/coins
                        g.drawRoundRect(7, 12, 24, 17, 4, 4);
                        g.drawLine(10, 9, 26, 9);
                        g.drawRoundRect(20, 18, 12, 7, 3, 3);
                        g.fillOval(23, 20, 3, 3);
                    }
                    default -> { // idea/advice bulb
                        g.drawOval(11, 7, 15, 16);
                        g.drawLine(15, 23, 15, 28); g.drawLine(22, 23, 22, 28);
                        g.drawLine(15, 28, 22, 28); g.drawLine(18, 4, 18, 2);
                        g.drawLine(7, 14, 5, 14); g.drawLine(30, 14, 32, 14);
                    }
                }
                g.dispose();
            }
        };
    }

    private static JPanel readerHeader(String title, int symbol) {
        JPanel bar = readerCard(new BorderLayout(8, 0),
            new Color(38, 174, 83), new Color(2, 105, 50), 19);
        bar.setBorder(new EmptyBorder(4, 9, 5, 12));
        JLabel icon = new JLabel(journeyIcon(symbol, true, 25));
        icon.setPreferredSize(new Dimension(27, 27));
        bar.add(icon, BorderLayout.WEST);
        JLabel name = label(title, 12, true, Color.WHITE);
        name.setForeground(Color.WHITE);
        bar.add(name, BorderLayout.CENTER);
        return bar;
    }

    // Thirty distinct beginner-action explanations (five actions in each of six lessons).
    // Advice is general; consult local agriculture extension staff for crop-specific decisions.
    private static final String[][] EXPANDED_STEP_GUIDANCE = {
        {
            "Start by writing whether your farm is intended for family food, extra income, or a future business. Pick one manageable outcome for the first growing cycle, such as maintaining a small vegetable plot. A clear goal tells you what to buy, how much work you can handle, and what progress to record.",
            "List the vegetables, crops, or animals you are interested in, then compare them with your space, skills, and available time. Ask nearby growers which products are suitable for the local season. Start small so you can learn daily care before expanding.",
            "Walk around your chosen space and write down its size, sunlight, water access, tools, and available helpers. Include how much time you can give each day because farming requires consistent attention. This list helps you distinguish what you already have from what you still need.",
            "Separate one-time expenses, such as containers or tools, from recurring costs, such as seeds, water, feed, and transport. Estimate total costs before you purchase supplies and allow a modest reserve for unexpected needs. Avoid assuming that the first harvest will immediately recover every cost.",
            "Contact your city or municipal agriculture office for locally suitable crops, planting guidance, and training opportunities. Bring your proposed goal and basic resource list so the advice fits your situation. Verify fertilizer, pesticide, and animal-care questions with qualified local professionals."
        },
        {
            "Measure the usable length and width of your planting area, then make a simple sketch. Mark paths, containers, trees, buildings, and places that flood. Keep sufficient access space for watering, inspection, and harvest rather than filling every corner with crops.",
            "Observe which parts of your garden receive morning and afternoon light. Different crops have different light requirements, so match your planting spot to the crop rather than assuming every plant needs the same amount. Check for shadows from buildings and growing trees.",
            "Look for puddles after rain and signs that soil remains waterlogged. Poor drainage may damage roots, while very fast-draining soil can dry out quickly. Consult local guidance before changing soil or building raised beds; the right solution depends on the site.",
            "Identify the water source, distance from the planting area, and likely reliability during dry months. Estimate how you will water crops safely without wasting water or leaving the soil saturated. Inspect hoses and containers for leaks and keep irrigation equipment clean.",
            "Consider heavy rain, flooding, hot periods, and local weather advisories when planning planting dates. Avoid making a fixed calendar without checking current conditions. Choose a crop and planting method appropriate for the season and your area's climate."
        },
        {
            "Begin with products suitable for your climate, available land, and ability to manage daily tasks. A crop that is popular in a different region may not grow well in yours. Compare a few realistic options before choosing your first farm project.",
            "Ask reliable nurseries or agriculture officers about locally adapted seed varieties and their growing requirements. Check seed labels, quality, and recommended planting conditions. Buy a manageable quantity first so you can observe actual performance.",
            "If you plan to raise animals, first understand their space, housing, clean water, feeding, and health-care needs. Animal welfare requires regular attention and suitable facilities, not just available land. Seek local veterinary guidance about disease prevention and care.",
            "Estimate the money, labor, and daily time needed to maintain the chosen crop or animal project. Include repeat purchases such as feed, planting materials, and water. A small plan that you can maintain is better than a larger one that exceeds your resources.",
            "Identify whether the harvest is for home use, neighbors, local markets, or another buyer. Check demand, expected quality, transportation, and possible losses before increasing production. Keep your sales assumptions conservative until you have real records."
        },
        {
            "Examine the planting area for soil condition, drainage, and visible problems before applying amendments. A soil test can help identify nutrient needs when available. Avoid applying fertilizer solely by guesswork because excess inputs can waste money and harm plants.",
            "List the hand tools, watering equipment, containers, and safety items needed for your chosen activity. Check that tools are functional, clean, and stored safely. Borrowing or sharing suitable equipment may be more practical than buying everything immediately.",
            "Separate essential inputs from optional purchases. Record amounts, estimated prices, supplier details, and when each item will be needed. This prevents duplicate shopping and helps you avoid supplies expiring or deteriorating before use.",
            "Write down both startup and ongoing expenses, including water, transport, maintenance, and labor where applicable. Compare planned spending with your available capital. Review actual receipts to understand whether the budget is realistic.",
            "Reserve a reasonable portion of available funds for unexpected repairs, crop losses, or urgent supplies. Do not spend the entire budget on day one. Adjust plans when costs rise rather than assuming all future expenses will stay the same."
        },
        {
            "Inspect soil moisture and plant condition routinely instead of watering on a fixed guess. Feel the soil at an appropriate depth and consider crop needs, drainage, and weather. Overwatering and underwatering can both damage growth.",
            "Look at leaves, stems, and surrounding soil for unusual color, holes, insects, or wilting. Record the date and take a photo when possible so you can compare changes. Avoid applying pesticides without identifying the problem and following professional advice.",
            "Check that animals have clean water, appropriate feed, secure housing, and regular care. Monitor behavior and appetite for unusual changes. Ask a veterinarian or qualified local expert when you suspect illness rather than guessing at treatment.",
            "Use a notebook or digital log to record watering, planting, feeding, weather, purchases, and observations. Keep entries short enough to maintain every day. Comparing records over time can reveal patterns that memory alone may miss.",
            "When you encounter a problem you cannot identify, contact a qualified agriculture or veterinary professional. Explain the symptoms, dates, and conditions you recorded. Early, informed action is often safer than trying several unverified remedies."
        },
        {
            "Check the crop's maturity indicators and recommended harvest method before picking. Harvest carefully to prevent unnecessary bruising or damage. For livestock products, follow appropriate handling and food-safety guidance.",
            "Prepare clean containers and a suitable storage area before harvesting. Protect produce from excessive heat, moisture, and rough handling where applicable. Different crops need different storage conditions, so check local recommendations.",
            "Record harvested weight, count, quality, and losses using the same units each time. Accurate quantities help you compare expected and actual production. Keep rejected or spoiled items separate when calculating usable yield.",
            "Record selling price, quantity sold, and actual revenue, then subtract production, packaging, transport, and other costs. Sales revenue is not the same as profit. Keep simple receipts and logs so the numbers can be checked later.",
            "Review which activities, varieties, and expenses worked well during the cycle. Note weather challenges, quality problems, buyer feedback, and unused supplies. Use this evidence to make one or two manageable improvements before the next planting."
        }
    };

    private static String stepExpanded(Guide guide, int index) {
        int lesson = beginnerIndex(guide);
        if (lesson < 0 || index < 0 || index >= 5) return stepTip(guide, index);
        return EXPANDED_STEP_GUIDANCE[lesson][index];
    }

    private static String timelineStepName(Guide guide, int index) {
        String[][] names = {
            {"Goal", "Products", "Resources", "Budget", "Advice"},
            {"Measure", "Sunlight", "Drainage", "Water", "Climate"},
            {"Location", "Varieties", "Care", "Budget", "Buyers"},
            {"Soil", "Tools", "Supplies", "Expenses", "Reserve"},
            {"Moisture", "Plants", "Livestock", "Records", "Support"},
            {"Harvest", "Storage", "Quantities", "Sales", "Improve"}
        };
        int lesson = beginnerIndex(guide);
        if (lesson >= 0 && lesson < names.length && index < names[lesson].length)
            return (index + 1) + "  " + names[lesson][index];
        return "Step " + (index + 1);
    }

    private static JButton timelineButton(String text, boolean active, boolean completed) {
        JButton button = new JButton(text) {
            { setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
              setFocusPainted(false); setBorder(new EmptyBorder(6, 5, 6, 5)); }
            @Override protected void paintComponent(Graphics original) {
                Graphics2D g = (Graphics2D) original.create();
                aa(g);
                int w = getWidth(), h = getHeight();
                Color top = active ? new Color(65, 217, 109)
                    : completed ? new Color(193, 246, 195) : new Color(249, 255, 248);
                Color bottom = active ? new Color(3, 105, 48)
                    : completed ? new Color(132, 216, 151) : new Color(219, 244, 219);
                if (getModel().isRollover()) top = top.brighter();
                g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g.fillRoundRect(0, 0, w - 2, h - 2, 19, 19);
                g.setColor(active ? new Color(2, 88, 43) : new Color(118, 201, 135));
                g.setStroke(new BasicStroke(active ? 1.7f : 1.1f));
                g.drawRoundRect(0, 0, w - 3, h - 3, 19, 19);
                g.dispose();
                super.paintComponent(original);
            }
        };
        button.setFont(FONT.deriveFont(Font.BOLD, 12f));
        button.setForeground(active ? Color.WHITE : DEEP);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    // SAKA_EMERALD_FARM_ENHANCEMENT_V2
    // EMERALD_FIELD_GUIDE_V1 - practical, beginner-friendly farming reference.
    // General guidance only: weather, crops and local advisories vary by area.
    private static final String[][] READER_DETAILS = {
        {
            "Begin with one manageable crop or project. Write down its purpose, space, expected cost and a simple weekly routine. Review your goal before expanding.",
            "GOAL: the result you want. RESOURCES: land, water, time, materials and people available to help. BUDGET: your plan for spending."
        },
        {
            "Observe a proposed growing area at different times of day. After rain, inspect where water collects; during dry weather, check how easily you can water.",
            "DRAINAGE: how excess water leaves soil. SUNLIGHT: light available to crops. IRRIGATION: supplying water when needed."
        },
        {
            "Ask a local agriculture office which crops suit your season. For animals, plan feed, clean water, housing, waste management and routine care first.",
            "VARIETY: a type of crop with particular traits. STOCKING: number of animals in a space. MARKET DEMAND: what buyers want."
        },
        {
            "List startup expenses separately from recurring costs like feed, water, fuel and transport. Consider soil testing before applying large amounts of fertilizer.",
            "STARTUP COST: a one-time starting expense. OPERATING COST: repeating expense. SOIL TEST: a check of soil properties."
        },
        {
            "Write down the date, weather, watering, feeding and unusual observations. Check plants and animals regularly; seek expert advice for unclear symptoms.",
            "MONITORING: checking conditions over time. FARM LOG: a dated activity record. PEST: an organism that may damage crops."
        },
        {
            "Harvest at an appropriate maturity, handle produce gently and keep containers clean. Record quantities, damaged goods, transport costs and actual sales.",
            "YIELD: the amount harvested. REVENUE: money earned from sales. PROFIT: revenue after subtracting relevant costs."
        }
    };

    private static final String[][] READER_ACTIONS = {
        {"Write one specific farming goal", "List your available land, water and time", "Estimate a starting budget"},
        {"Sketch the available planting area", "Record sunlight and shade observations", "Locate water and drainage routes"},
        {"List two locally suitable farm options", "Estimate care and space requirements", "Choose one manageable option"},
        {"List tools, seeds and other supplies", "Separate one-time and repeat expenses", "Allow room for unexpected costs"},
        {"Create a daily farm log", "Schedule short crop or animal checks", "Review records at week's end"},
        {"Plan a clean harvest and storage area", "Record harvest weight or quantity", "Compare total revenue with total costs"}
    };

    private static String readerDetail(Guide guide, int column) {
        int i = beginnerIndex(guide);
        return i < 0 ? guide.description() : READER_DETAILS[i][column];
    }

    private static JPanel readerDetailCard(String heading, String content,
            Color top, Color bottom, int variant) {
        JPanel card = readerCard(new BorderLayout(0, 10), top, bottom, 28);
        card.setBorder(new EmptyBorder(16, 17, 15, 17));
        JPanel labelRow = clear(new BorderLayout(10, 0));
        JLabel symbol = new JLabel(variant == 1 ? "i" : "Aa", SwingConstants.CENTER);
        symbol.setFont(FONT.deriveFont(Font.BOLD, 13f));
        symbol.setForeground(Color.WHITE);
        symbol.setOpaque(true);
        symbol.setBackground(variant == 1 ? new Color(15, 134, 63) : new Color(19, 112, 74));
        symbol.setPreferredSize(new Dimension(32, 27));
        labelRow.add(symbol, BorderLayout.WEST);
        labelRow.add(readerHeader(heading, variant == 1 ? 2 : 1), BorderLayout.CENTER);
        card.add(labelRow, BorderLayout.NORTH);
        card.add(readerText(content, 15, false, DARK), BorderLayout.CENTER);
        return card;
    }

    // SAKA_READER_ACTION_PLAN_V5 — 3 meaningful interactive cards per beginner lesson.
    // Each lesson has tailored example + reason + actionable guidance.
    private static final String[][] READER_ACTION_DETAILS = {
        {
            "Example: Grow pechay for household meals and sell any surplus. State the purpose and a realistic first-cycle target.",
            "Measure your planting area, locate reliable water and decide how many minutes you can spend on regular care.",
            "List seeds, containers or tools, water and transport. Check prices locally and reserve some money for surprises."
        },
        {
            "Sketch your garden and label pathways, existing trees and any low-lying or flood-prone spots.",
            "Observe the area in the morning and afternoon. Match sun exposure to the actual needs of your chosen crop.",
            "Identify a water source and where rainwater flows. Consider dry spells and waterlogging before planting."
        },
        {
            "Compare two crops or animal projects suitable for your location rather than picking only what looks profitable.",
            "Write down how much room, time, clean water, feed or equipment each option needs every week.",
            "Select one small project you can afford and care for consistently; ask local experts about suitability."
        },
        {
            "Check which tools and supplies are essential and already available before spending on new items.",
            "Keep one-time purchases separate from repeated expenses such as water, feed, fuel and transport.",
            "Keep a modest reserve for damaged tools, higher input prices or unexpected field problems."
        },
        {
            "Record short entries for watering, feeding, weather, purchases and observations in one dated notebook.",
            "Create a manageable routine for observing crop condition, soil moisture and livestock care.",
            "Review entries weekly: compare changes, note what worked and seek help for unfamiliar symptoms."
        },
        {
            "Prepare clean containers and a suitable storage area before harvesting to reduce avoidable damage.",
            "Record weights or quantities using the same units so you can compare harvests accurately.",
            "Add up actual sales, then subtract relevant production and selling costs to understand your results."
        }
    };

    private static final String[][] READER_OUTCOME_EXTRAS = {
        {
            "Try it: Write a one-sentence goal with your chosen crop, available space and purpose. Small, clear goals are easier to review.",
            "Before continuing: Can you name your first crop or project, three resources you have, and a spending limit?"
        },
        {
            "Try it: Draw a simple map of your planting space and mark the areas with sunlight, shade and water access.",
            "Before continuing: Which part drains poorly after rain, and how will you water during a dry week?"
        },
        {
            "Try it: Compare two locally suitable products based on care time, space, supplies and possible buyers.",
            "Before continuing: Which option matches your actual resources, not just your expected selling price?"
        },
        {
            "Try it: Make two budget columns: one-time supplies and costs you expect to pay regularly.",
            "Before continuing: Which purchases can wait, and what amount can you keep for unexpected costs?"
        },
        {
            "Try it: Record today's weather, observations and completed farm tasks in one short log entry.",
            "Before continuing: What change should you watch tomorrow, and where will you record it?"
        },
        {
            "Try it: Make a simple harvest record showing quantity, selling price and transport or packaging costs.",
            "Before continuing: Can you explain why total sales are different from actual profit?"
        }
    };

    private static String readerOutcomeExtra(Guide guide, int position) {
        int n = beginnerIndex(guide);
        return n < 0 ? "Apply the main idea to a small, safe farm activity."
            : READER_OUTCOME_EXTRAS[n][position];
    }

    // SAKA_FARM_FIELD_GUIDE_V7: structured, lesson-specific observational references.
    // No fixed crop calendar or cost claim: examples must be adapted to local conditions.
    private static final String[][] FIELD_GUIDE_V7 = {
        {
            "A SMALL START CAN GROW", "A seedling or vegetable plot is a manageable place to start. Choose one measurable goal and decide who will care for it every day.",
            "PLAN BEFORE PURCHASING", "Write a simple list: intended harvest, available space, water, time, materials and a spending limit. A list helps prevent buying supplies you may not need.",
            "TRY THIS TODAY", "Sketch your first growing area and write one goal you can explain in a single sentence."
        },
        {
            "OBSERVE YOUR GROWING AREA", "Look at the site during the morning and afternoon. Buildings and trees may change shade throughout the day, so note when direct light actually reaches it.",
            "TEST WATER AND DRAINAGE", "After rainfall, identify places where water remains standing. During drier weather, check how far you must carry water and whether the supply is reliable.",
            "TRY THIS TODAY", "Draw a site sketch marking sunny spots, shaded spots, drainage problems and your nearest water source."
        },
        {
            "COMPARE POSSIBLE CROPS", "Choose plants that suit your available growing area, local season and capacity to care for them. Ask for locally adapted varieties rather than relying only on popularity.",
            "CHECK ANIMAL-CARE NEEDS", "Before raising livestock, plan safe housing, clean water, appropriate feed, time for routine care and access to qualified veterinary help.",
            "TRY THIS TODAY", "Compare two crop or livestock options using five headings: space, time, cost, care and intended use or buyers."
        },
        {
            "PREPARE THE SITE FIRST", "Check soil, containers, drainage and tool condition before planting. Keep soil and equipment clean, and seek local guidance before using fertilizers or pesticides.",
            "PUT COSTS ON PAPER", "Keep startup tools separate from recurring costs such as inputs, water and transport. Include a reserve for unexpected purchases where possible.",
            "TRY THIS TODAY", "Make two columns called One-Time Costs and Repeat Costs. Add your actual estimated prices, not assumed profits."
        },
        {
            "LOOK FOR CHANGES", "Observe leaves, soil moisture and plant growth regularly. Note unusual colors or damage, but identify the problem before attempting treatment.",
            "KEEP A SIMPLE FARM LOG", "Record the date, weather, watering, feeding where relevant, purchases and observations. Clear records make patterns easier to recognize.",
            "TRY THIS TODAY", "Write one sample farm-log entry with today's date, what you checked, what you did and what you noticed."
        },
        {
            "HANDLE THE HARVEST", "Prepare clean containers, choose appropriate maturity indicators and protect harvested produce from unnecessary damage. Handling requirements differ by crop.",
            "REVIEW SALES AND COSTS", "Record harvested quantity and selling price. Compare total sales with all production, packing and transport costs to estimate profit or loss.",
            "TRY THIS TODAY", "Create a practice harvest log: quantity, unit price, total sales, costs and what you would improve next cycle."
        }
    };

    private static JPanel readerVisualFieldGuide(Guide guide) {
        int lesson = beginnerIndex(guide);
        JPanel outer = readerCard(new BorderLayout(0, 10),
            new Color(248, 255, 244), new Color(213, 246, 221), 28);
        outer.setBorder(new EmptyBorder(12, 14, 13, 14));
        outer.add(readerHeader("FARMING IN ACTION  •  SEE, OBSERVE & TRY", 1), BorderLayout.NORTH);
        if (lesson < 0) {
            outer.add(readerText("Observe one practical farming example and record what you learned.",
                15, false, DARK), BorderLayout.CENTER);
            return outer;
        }
        String[][] photoChoices = {
            {"photo_seedling.jpg", "photo_harvest.jpg"},
            {"photo_seedling.jpg", "photo_irrigation.jpg"},
            {"photo_seedling.jpg", "photo_livestock.jpg"},
            {"photo_tools.jpg", "photo_finance.jpg"},
            {"photo_irrigation.jpg", "photo_tools.jpg"},
            {"photo_harvest.jpg", "photo_finance.jpg"}
        };
        JPanel mainRow = clear(new GridLayout(1, 3, 10, 0));
        String[] data = FIELD_GUIDE_V7[lesson];
        for (int i = 0; i < 2; i++) {
            JPanel item = readerCard(new BorderLayout(0, 7),
                new Color(255, 255, 253), new Color(235, 253, 234), 19);
            item.setBorder(new EmptyBorder(7, 9, 10, 9));
            PhotoArt photo = new PhotoArt(new String[]{photoChoices[lesson][i]}, data[i * 2]);
            photo.setToolTipText(data[i * 2]);
            item.add(photo, BorderLayout.CENTER);
            JPanel caption = clear(new BorderLayout(0, 4));
            caption.add(label(data[i * 2], 14, true, DEEP), BorderLayout.NORTH);
            caption.add(readerText(data[i * 2 + 1], 14, false, new Color(30, 75, 44)),
                BorderLayout.CENTER);
            item.add(caption, BorderLayout.SOUTH);
            mainRow.add(item);
        }
        JPanel side = readerCard(new BorderLayout(0, 8),
            new Color(231, 252, 232), new Color(188, 237, 205), 21);
        side.setBorder(new EmptyBorder(11, 12, 11, 12));
        side.add(readerHeader("TRY THIS TODAY", 3), BorderLayout.NORTH);
        JPanel notes = clear(new BorderLayout(0, 8));
        notes.add(readerText(data[5], 16, false, new Color(21, 70, 38)), BorderLayout.NORTH);
        JPanel observationStack = clear(new GridLayout(3, 1, 0, 7));
        String[] fieldNotes = FIELD_OBSERVATION_V9[lesson];
        String[] noteTitles = {"LOOK FOR", "WRITE IT DOWN", "NEXT SMALL ACTION"};
        for (int n = 0; n < 3; n++) {
            JPanel mini = readerCard(new BorderLayout(0, 4),
                n == 1 ? new Color(244, 255, 241) : new Color(252, 255, 246),
                n == 2 ? new Color(216, 247, 221) : new Color(228, 251, 226), 16);
            mini.setBorder(new EmptyBorder(7, 10, 7, 10));
            mini.add(label(noteTitles[n], 12, true, n == 2 ? GREEN : DEEP), BorderLayout.NORTH);
            mini.add(readerText(fieldNotes[n], 14, false, new Color(28, 72, 43)), BorderLayout.CENTER);
            observationStack.add(mini);
        }
        notes.add(observationStack, BorderLayout.CENTER);
        side.add(notes, BorderLayout.CENTER);
        mainRow.add(side);
        outer.add(mainRow, BorderLayout.CENTER);
        return outer;
    }

    private static final String[] FIELD_WATCH_V8 = {
        "Notice your available time, usable space, dependable water source and which supplies are truly essential.",
        "Look for morning and afternoon shade, pooled water after rainfall, and how easy it is to reach the water supply.",
        "Compare time for care, local climate, housing or planting space, input costs, and possible buyers.",
        "Check soil condition, tool safety, repeat purchases, and whether the budget includes unexpected costs.",
        "Watch for changes in leaf color, soil moisture, feeding behavior and the consistency of written farm records.",
        "Observe harvest maturity and handling damage, then compare quantities sold, revenue, and actual costs."
    };

    // SAKA_READER_V9: three distinct and actionable field observations for each lesson.
    private static final String[][] FIELD_OBSERVATION_V9 = {
        {
            "A manageable plot, dependable water and time for regular care.",
            "Your goal, planting area, available supplies and spending limit.",
            "Choose one crop or project and write who will care for it."
        },
        {
            "Morning sun, afternoon shade, water access and standing rainwater.",
            "Approximate sun hours, drainage issues and distance to water.",
            "Sketch the safest growing spot and compare it with another area."
        },
        {
            "Local suitability, animal-care needs and available growing space.",
            "Two options, their care time, basic expenses and potential use.",
            "Compare both options before choosing a beginner project."
        },
        {
            "Soil condition, essential tools, clean water and safe storage.",
            "One-time purchases, repeating costs and emergency allowance.",
            "Check existing tools first, then price only missing essentials."
        },
        {
            "Leaf changes, soil moisture, pests and animal behavior.",
            "Date, weather, tasks, unusual signs and materials used.",
            "Make a simple log today and compare observations tomorrow."
        },
        {
            "Crop maturity, handling damage and clean harvest containers.",
            "Quantity, selling price, sales, transport and other costs.",
            "Calculate your net result after recording all relevant expenses."
        }
    };

    // SAKA_READER_MISSION_V6 — clean typography hierarchy and visual motivation.
    // No database updates: temporary lesson-session checklist only.
    private static JPanel readerActionPlan(Guide guide, Map<String, boolean[]> state) {
        int lessonId = beginnerIndex(guide);
        JPanel card = readerCard(new BorderLayout(0, 11),
            new Color(252, 255, 249), new Color(218, 247, 225), 29);
        card.setBorder(new EmptyBorder(14, 16, 13, 16));

        String[] actions = lessonId < 0
            ? new String[]{"Read the lesson", "Write one takeaway", "Try one safe action"}
            : READER_ACTIONS[lessonId];
        String[] details = lessonId < 0
            ? new String[]{"Read the guidance carefully and identify the central idea.",
                "Summarize what you learned in your own words.",
                "Choose a small action appropriate for your local conditions."}
            : READER_ACTION_DETAILS[lessonId];
        boolean[] selections = state.computeIfAbsent(guide.title(), k -> new boolean[3]);

        JPanel heading = clear(new BorderLayout(10, 0));
        JPanel headingWords = clear(new BorderLayout(0, 4));
        headingWords.add(readerHeader("YOUR MINI FARM ACTION PLAN", 3), BorderLayout.NORTH);
        headingWords.add(readerText("Three small missions to turn this lesson into a real farm plan.",
            13, false, new Color(58, 109, 75)), BorderLayout.SOUTH);
        heading.add(headingWords, BorderLayout.WEST);
        JLabel count = label("0 OF 3 MISSIONS", 13, true, DEEP);
        count.setHorizontalAlignment(SwingConstants.RIGHT);
        heading.add(count, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        JPanel missionArea = clear(new BorderLayout(0, 10));
        JPanel items = clear(new GridLayout(1, 3, 12, 0));
        JProgressBar track = new JProgressBar(0, 3);
        track.setOpaque(false);
        track.setForeground(new Color(19, 167, 74));
        track.setBackground(new Color(197, 232, 204));
        track.setBorderPainted(false);
        track.setPreferredSize(new Dimension(50, 9));
        final JLabel encouragement = label("START YOUR FARM JOURNEY  •  Tick a mission after you try it.",
            12, true, new Color(42, 114, 66));
        Runnable updateCount = () -> {
            int completed = 0;
            for (boolean selected : selections) if (selected) completed++;
            track.setValue(completed);
            count.setText(completed + " OF 3 MISSIONS");
            encouragement.setText(completed == 3
                ? "ALL MISSIONS COMPLETE!  Great work — review your plan before moving on."
                : completed == 2 ? "ONE MORE TO GO!  You're building a useful first farm plan."
                : completed == 1 ? "NICE START!  Keep going, one practical action at a time."
                : "START YOUR FARM JOURNEY  •  Tick a mission after you try it.");
        };

        for (int n = 0; n < 3; n++) {
            final int index = n;
            JPanel tile = readerCard(new BorderLayout(0, 9),
                n == 0 ? new Color(250, 255, 247) : n == 1
                    ? new Color(244, 255, 237) : new Color(239, 255, 248),
                n == 0 ? new Color(219, 249, 225) : n == 1
                    ? new Color(215, 247, 206) : new Color(205, 244, 226), 25);
            tile.setBorder(new EmptyBorder(12, 13, 11, 13));

            JPanel top = clear(new BorderLayout(7, 0));
            JLabel mission = label("MISSION 0" + (n + 1), 11, true,
                n == 1 ? new Color(88, 125, 31) : GREEN);
            top.add(mission, BorderLayout.WEST);
            JLabel image = new JLabel(journeyIcon(n, false, 33));
            top.add(image, BorderLayout.EAST);
            tile.add(top, BorderLayout.NORTH);

            JPanel middle = clear(new BorderLayout(8, 6));
            JCheckBox check = new JCheckBox();
            check.setOpaque(false);
            check.setSelected(selections[n]);
            check.setFocusPainted(false);
            check.setIcon(new ReaderCheckIcon(false));
            check.setSelectedIcon(new ReaderCheckIcon(true));
            check.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            check.setToolTipText("Mark this practical farm mission complete");
            middle.add(check, BorderLayout.WEST);
            JTextArea taskTitle = readerText(actions[n], 17, true, DARK);
            taskTitle.setToolTipText("Click to mark this mission complete");
            taskTitle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            taskTitle.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { check.doClick(); }
            });
            middle.add(taskTitle, BorderLayout.CENTER);
            tile.add(middle, BorderLayout.CENTER);

            JPanel bottom = clear(new BorderLayout(0, 7));
            JTextArea note = readerText(details[n], 14, false, new Color(35, 78, 49));
            bottom.add(note, BorderLayout.CENTER);
            JLabel micro = label("TRY IT ON YOUR FARM", 10, true, new Color(20, 132, 62));
            bottom.add(micro, BorderLayout.SOUTH);
            tile.add(bottom, BorderLayout.SOUTH);
            check.addActionListener(e -> {
                selections[index] = check.isSelected();
                mission.setText(selections[index] ? "MISSION 0" + (index + 1) + "  ✓ DONE" :
                    "MISSION 0" + (index + 1));
                updateCount.run();
            });
            if (selections[n]) mission.setText("MISSION 0" + (n + 1) + "  ✓ DONE");
            items.add(tile);
        }
        missionArea.add(items, BorderLayout.CENTER);
        JPanel progressLine = clear(new BorderLayout(0, 5));
        progressLine.add(track, BorderLayout.NORTH);
        progressLine.add(encouragement, BorderLayout.CENTER);
        missionArea.add(progressLine, BorderLayout.SOUTH);
        card.add(missionArea, BorderLayout.CENTER);
        card.add(readerText("Personal practice only — your checklist isn't a graded quiz or saved to the database.",
            11, false, MUTED), BorderLayout.SOUTH);
        updateCount.run();
        return card;
    }

    private static final class ReaderCheckIcon implements Icon {
        private final boolean checked;
        ReaderCheckIcon(boolean checked) { this.checked = checked; }
        public int getIconWidth() { return 26; }
        public int getIconHeight() { return 26; }
        public void paintIcon(Component component, Graphics original, int x, int y) {
            Graphics2D g = (Graphics2D) original.create();
            aa(g);
            g.setPaint(new GradientPaint(x, y,
                checked ? new Color(56, 204, 104) : Color.WHITE,
                x, y + 23, checked ? new Color(5, 112, 53) : new Color(226, 250, 228)));
            g.fillRoundRect(x + 2, y + 2, 22, 22, 10, 10);
            g.setColor(checked ? DEEP : new Color(96, 179, 116));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x + 2, y + 2, 22, 22, 10, 10);
            if (checked) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.drawLine(x + 7, y + 13, x + 12, y + 18);
                g.drawLine(x + 12, y + 18, x + 20, y + 8);
            }
            g.dispose();
        }
    }

    // SAKA_READER_JOURNEY_V5
    // SAKA_READER_LEARNING_PLUS_V1 — additional instructional content.
    private static final String[][] LESSON_EXTRAS = {
        {
            "A specific goal helps you decide what to plant, how much land and money to use, and which daily tasks matter. Begin with a small, measurable result for your first cycle.",
            "Write your goal on paper. Add a list of available space, water, time, tools, possible crops, and an estimated amount you can safely spend.",
            "Buying seeds, tools, or animals too early can waste money. Plan first, ask local experts, and purchase only what your small first project needs."
        },
        {
            "Sunlight, water, and drainage determine whether your growing area is suitable.",
            "A measuring tape, site sketch, sunlight observations, and water-source notes.",
            "Planting first without checking flooding, shade, or dry-season water supply."
        },
        {
            "Choosing suitable crops or animals prevents costs that your farm cannot support.",
            "Local crop options, space estimates, available feed or water, and care time.",
            "Choosing a product only because it sells well elsewhere."
        },
        {
            "Planning soil needs, tools, and expenses reduces wasted supplies and spending.",
            "Soil and drainage observations, a tools checklist, and cost estimates.",
            "Ignoring recurring expenses and keeping no emergency allowance."
        },
        {
            "Consistent checks help you notice problems early and improve farm records.",
            "A daily log, crop or animal checklist, and a routine you can maintain.",
            "Relying on memory instead of recording what you did and observed."
        },
        {
            "Harvest and sales records reveal whether your farming plan is working.",
            "Clean harvest containers, weighing tools, a price list, and sales log.",
            "Counting sales as profit without subtracting production costs."
        }
    };

    private static final String[][] LESSON_PRACTICE = {
        {"Which is the best first step before buying farming supplies?",
         "Choose a realistic farming goal", "Buy the most expensive tools", "Plant everything available", "0",
         "Start with a goal, then plan the space, budget, and materials around it."},
        {"What should you check before selecting a planting area?",
         "Only the color of the fence", "Sunlight, water, and drainage", "Only the size of the gate", "1",
         "A suitable site needs appropriate light, reliable water, and workable drainage."},
        {"Which choice best suits a beginner with limited space?",
         "Start a huge livestock operation", "Ignore daily care requirements", "Choose a manageable crop or project", "2",
         "Match the activity to your actual land, budget, and care time."},
        {"What belongs in your initial farm budget?",
         "Only seed prices", "Startup and recurring costs plus a reserve", "Only expected sales", "1",
         "Include tools, supplies, maintenance, and unexpected expenses."},
        {"What is a useful daily farming habit?",
         "Record observations and completed tasks", "Check only during harvest", "Never inspect for plant changes", "0",
         "A short daily log helps you spot issues and remember important activities."},
        {"How can you estimate whether harvest sales were profitable?",
         "Count the harvested fruits only", "Ignore transportation costs", "Compare sales revenue with all costs", "2",
         "Profit depends on sales minus production, transport, and other expenses."}
    };

    private static int beginnerIndex(Guide guide) {
        if (guide == null || guide.step() == null) return -1;
        try {
            int n = Integer.parseInt(guide.step().trim()) - 1;
            return n >= 0 && n < LESSON_EXTRAS.length ? n : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String lessonExtra(Guide guide, int column) {
        int i = beginnerIndex(guide);
        return i < 0 ? guide.description() : LESSON_EXTRAS[i][column];
    }

    // SAKA_READER_V8: contextual cards have lesson-specific examples rather than repeated prompts.
    private static final String[][][] INSIGHT_V8 = {
        {
            {"EXAMPLE IN PRACTICE", "For a family vegetable garden, success may mean producing enough fresh vegetables for home meals—not necessarily selling anything."},
            {"YOUR STARTER LIST", "Write down your available growing space, reliable water source, weekly time, and maximum amount you can afford to spend."},
            {"BETTER APPROACH", "Select one affordable activity, calculate the essentials first, and only purchase materials after checking the plan."}
        },
        {
            {"EXAMPLE IN PRACTICE", "Two backyard corners may look equally useful, but one stays shaded and collects puddles. Observing both can prevent a poor site choice."},
            {"YOUR SITE CHECK", "Mark the sunny hours, measure the usable space, locate the nearest safe water supply, and inspect drainage after rain."},
            {"BETTER APPROACH", "Compare more than one location before planting and ask for crop-specific sunlight and water guidance locally."}
        },
        {
            {"EXAMPLE IN PRACTICE", "A small container garden can be easier to manage than starting with many animals that require daily housing, feeding, and health care."},
            {"YOUR COMPARISON", "For each crop or animal, compare local suitability, starting cost, maintenance time, space, and expected use or buyers."},
            {"BETTER APPROACH", "Try a manageable scale first, verify local growing conditions, and plan care requirements before committing money."}
        },
        {
            {"EXAMPLE IN PRACTICE", "A farm plan may fail even with good seeds if drainage is poor or the budget leaves no allowance for water and replacement supplies."},
            {"YOUR ESSENTIALS", "Make separate lists for reusable tools, planting materials, recurring inputs, water or transport, and a small contingency."},
            {"BETTER APPROACH", "Prioritize safe, suitable tools and good soil conditions; compare prices before expanding your purchases."}
        },
        {
            {"EXAMPLE IN PRACTICE", "A dated note about yellowing leaves or unusually wet soil helps you recognize whether the condition is improving or spreading."},
            {"YOUR DAILY KIT", "Keep a simple record book or phone note, a routine task list, and a way to observe plants or animals safely."},
            {"BETTER APPROACH", "Check conditions before taking action. Water based on soil and crop needs, and identify pest or disease problems before treatment."}
        },
        {
            {"EXAMPLE IN PRACTICE", "Selling vegetables for ₱1,000 does not mean earning ₱1,000: seeds, inputs, packaging, and transport still need to be counted."},
            {"YOUR HARVEST RECORD", "Prepare clean containers, a weighing or counting method, a product list, selling-price notes, and a record of total expenses."},
            {"BETTER APPROACH", "Record quantity and costs as you go; compare revenue with all relevant expenses before describing the result as profit."}
        }
    };

    private static JPanel readerInsightCard(Guide guide, String heading, String detail,
            Color top, Color bottom) {
        int lessonId = beginnerIndex(guide);
        int kind = heading.startsWith("WHY") ? 0 : heading.startsWith("PREPARE") ? 1 : 2;
        JPanel card = readerCard(new BorderLayout(0, 11), top, bottom, 23);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.add(readerHeader(heading, kind == 2 ? 4 : kind == 1 ? 2 : 1), BorderLayout.NORTH);
        JPanel contents = clear(new BorderLayout(0, 8));
        contents.add(readerText(detail, 16, false, DARK), BorderLayout.NORTH);
        String[] detail2 = lessonId < 0
            ? new String[]{"TRY THIS", "Write down one way you can apply this lesson."}
            : INSIGHT_V8[lessonId][kind];
        JPanel supporting = readerCard(new BorderLayout(0, 4),
            new Color(251, 255, 245), new Color(229, 252, 226), 17);
        supporting.setBorder(new EmptyBorder(8, 10, 9, 10));
        supporting.add(label(detail2[0], 11, true, DEEP), BorderLayout.NORTH);
        supporting.add(readerText(detail2[1], 15, false, new Color(31, 76, 45)), BorderLayout.CENTER);
        contents.add(supporting, BorderLayout.CENTER);
        card.add(contents, BorderLayout.CENTER);
        return card;
    }

    // Short, memorable conclusion for each of the six beginner lessons.
    private static final String[] TAKEAWAYS_V8 = {
        "Plan your purpose, resources and spending before starting. A small goal you can maintain is a strong first step.",
        "A suitable site needs the right light, workable soil, reliable water, and drainage appropriate for your crop.",
        "Choose crops or animals for your real conditions and ability to care for them—not just what is popular.",
        "Prepare the essentials, distinguish one-time costs from recurring costs, and protect your starting budget.",
        "Regular observation and simple written records make it easier to spot problems and improve daily routines.",
        "Harvest carefully, track quantities and sales, subtract costs, and use the results to plan your next cycle."
    };
    private static JPanel readerTakeawayStrip(Guide guide) {
        int i = beginnerIndex(guide);
        JPanel panel = readerCard(new GridBagLayout(),
            new Color(29, 157, 78), new Color(4, 102, 52), 24);
        panel.setBorder(new EmptyBorder(14, 20, 14, 20));
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = 0;
        left.weightx = 0;
        left.anchor = GridBagConstraints.CENTER;
        left.insets = new Insets(0, 0, 0, 25);
        JPanel tag = clear(new BorderLayout(9, 0));
        JLabel icon = new JLabel("\u2713", SwingConstants.CENTER);
        icon.setFont(FONT.deriveFont(Font.BOLD, 20f));
        icon.setForeground(Color.WHITE);
        icon.setPreferredSize(new Dimension(32, 32));
        tag.add(icon, BorderLayout.WEST);
        JLabel name = label("KEY TAKEAWAY", 16, true, Color.WHITE);
        tag.add(name, BorderLayout.CENTER);
        panel.add(tag, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = 0;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.anchor = GridBagConstraints.CENTER;
        JTextArea message = readerText(i < 0 ? guide.description() : TAKEAWAYS_V8[i],
            16, false, new Color(255, 255, 248));
        message.setBorder(new EmptyBorder(1, 0, 1, 0));
        panel.add(message, right);
        return panel;
    }

    private static JPanel readerPracticeCard(Guide guide) {
        int i = beginnerIndex(guide);
        JPanel card = readerCard(new BorderLayout(0, 7),
            new Color(235, 253, 240), new Color(184, 234, 199), 30);
        card.setBorder(new EmptyBorder(11, 15, 11, 15));
        card.add(readerHeader("QUICK PRACTICE  •  CHECK YOUR UNDERSTANDING", 5), BorderLayout.NORTH);
        if (i < 0) {
            card.add(readerText("What one idea from this article can you apply?",
                14, false, DARK), BorderLayout.CENTER);
            return card;
        }
        String[] data = LESSON_PRACTICE[i];
        JPanel content = clear(new BorderLayout(0, 7));
        content.add(readerText(data[0], 16, true, DARK), BorderLayout.NORTH);
        JPanel options = clear(new GridLayout(1, 3, 7, 0));
        JLabel feedback = label("Select an answer to get instant feedback.",
            14, true, MUTED);
        int correct = Integer.parseInt(data[4]);
        for (int choice = 0; choice < 3; choice++) {
            final int selected = choice;
            JButton answer = button(data[1 + choice], false);
            answer.setFont(FONT.deriveFont(Font.BOLD, 13.5f));
            answer.setToolTipText(data[1 + choice]);
            answer.addActionListener(e -> {
                boolean right = selected == correct;
                feedback.setForeground(right ? DEEP : new Color(137, 89, 32));
                feedback.setText((right ? "Correct! " : "Try again. ") + data[5]);
            });
            options.add(answer);
        }
        content.add(options, BorderLayout.CENTER);
        content.add(feedback, BorderLayout.SOUTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    // Reader helpers are isolated from dashboard/Gemini/database code.
    private static JPanel readerCard(LayoutManager layout, Color top, Color bottom, int r) {
        return new JPanel(layout) {
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics original) {
                Graphics2D g = (Graphics2D) original.create();
                aa(g);
                int w = getWidth(), h = getHeight();
                if (w > 5 && h > 5) {
                    g.setColor(new Color(0, 74, 30, 26));
                    g.fillRoundRect(2, 4, w - 3, h - 5, r, r);
                    g.setPaint(new GradientPaint(0, 0, top, w / 2f, h, bottom));
                    g.fillRoundRect(0, 0, w - 3, h - 5, r, r);
                    Shape clip = g.getClip();
                    g.clip(new RoundRectangle2D.Float(0, 0, w - 3, h - 5, r, r));
                    g.setPaint(new GradientPaint(0, 0,
                        new Color(255, 255, 255, 110), 0, h / 2f,
                        new Color(255, 255, 255, 0)));
                    g.fillRect(0, 0, w, h / 2);
                    g.setClip(clip);
                    g.setColor(new Color(85, 176, 117));
                    g.setStroke(new BasicStroke(1.15f));
                    g.drawRoundRect(0, 0, w - 4, h - 6, r, r);
                }
                g.dispose();
                super.paintComponent(original);
            }
        };
    }

    private static JLabel readerChip(String text, boolean emphasized) {
        JLabel chip = new JLabel(text) {
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics original) {
                Graphics2D g = (Graphics2D) original.create();
                aa(g);
                int w = getWidth(), h = getHeight();
                g.setPaint(new GradientPaint(0, 0,
                    emphasized ? new Color(62, 204, 105) : new Color(230, 252, 235),
                    0, h, emphasized ? new Color(8, 110, 48) : new Color(198, 237, 210)));
                g.fillRoundRect(0, 0, w - 1, h - 1, 17, 17);
                g.setColor(emphasized ? DEEP : BORDER);
                g.drawRoundRect(0, 0, w - 2, h - 2, 17, 17);
                g.dispose();
                super.paintComponent(original);
            }
        };
        chip.setFont(FONT.deriveFont(Font.BOLD, 12f));
        chip.setForeground(emphasized ? Color.WHITE : DEEP);
        chip.setBorder(new EmptyBorder(7, 12, 7, 12));
        return chip;
    }

    private static JTextArea readerText(String value, int size, boolean strong, Color color) {
        JTextArea text = new JTextArea(value == null ? "" : value);
        text.setFont(FONT.deriveFont(strong ? Font.BOLD : Font.PLAIN, (float)size));
        text.setForeground(color);
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setBorder(null);
        return text;
    }

    private static void addReaderSection(JPanel body, JComponent child, int height) {
        child.setAlignmentX(Component.LEFT_ALIGNMENT);
        child.setPreferredSize(new Dimension(600, height));
        child.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        body.add(child);
    }

    private static final class ReaderColumn extends JPanel implements Scrollable {
        ReaderColumn() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }
        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return Math.max(80, r.height - 60);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static GuideViewParts splitGuideContent(Guide guide) {
        String raw = guide.content() == null ? "" : guide.content().replace("\r", "");
        String intro = "", example = "", checkpoint = "";
        List<String> steps = new ArrayList<>();
        for (String block : raw.split("\\n\\n")) {
            String b = block.trim();
            if (b.isBlank()) continue;
            if (b.startsWith("Example:")) {
                example = b.substring("Example:".length()).trim();
                continue;
            }
            if (b.startsWith("CHECKPOINT:")) {
                checkpoint = b.substring("CHECKPOINT:".length()).trim();
                continue;
            }
            boolean numbered = false;
            for (String line : b.split("\\n")) {
                String item = line.trim();
                if (item.matches("\\d+\\..*")) {
                    numbered = true;
                    steps.add(item.replaceFirst("\\d+\\.\\s*", "").trim());
                }
            }
            if (!numbered) intro += (intro.isBlank() ? "" : "\n\n") + b;
        }
        return new GuideViewParts(intro, steps, example, checkpoint);
    }

    private static JPanel buildLessonStepItem(int number, String instruction, String tip) {
        JPanel row = readerCard(new BorderLayout(14, 0),
            new Color(240, 254, 244), new Color(202, 239, 213), 25);
        row.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel badge = new JLabel(String.valueOf(number), SwingConstants.CENTER) {
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics original) {
                Graphics2D g = (Graphics2D) original.create();
                aa(g);
                g.setPaint(new GradientPaint(0, 0, new Color(62, 207, 106),
                    0, getHeight(), new Color(7, 112, 50)));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                g.dispose();
                super.paintComponent(original);
            }
        };
        badge.setFont(FONT.deriveFont(Font.BOLD, 17f));
        badge.setForeground(Color.WHITE);
        badge.setPreferredSize(new Dimension(47, 48));
        JPanel markers = clear(new BorderLayout(8, 0));
        markers.add(badge, BorderLayout.WEST);
        JLabel pictogram = new JLabel(journeyIcon(number - 1, false, 37));
        pictogram.setPreferredSize(new Dimension(42, 48));
        markers.add(pictogram, BorderLayout.EAST);
        row.add(markers, BorderLayout.WEST);
        JPanel words = clear(new BorderLayout(0, 5));
        words.add(readerText(instruction, 15, true, DARK), BorderLayout.NORTH);
        words.add(readerText(tip, 14, false, new Color(51, 103, 65)), BorderLayout.CENTER);
        row.add(words, BorderLayout.CENTER);
        return row;
    }

    private static String stepTip(Guide guide, int index) {
        String[][] tips = {
            {"Know your purpose before spending on supplies.",
             "Choose products that match your skills and local conditions.",
             "List your land, time, water, equipment and helpers.",
             "Include startup costs and a little emergency allowance.",
             "Local agricultural officers can suggest suitable methods."},
            {"Start with a measured sketch of your available area.",
             "Observe sunlight at different times of the day.",
             "Look for standing water or soil that drains too quickly.",
             "Check whether watering will be practical throughout the season.",
             "Choose a plan suited to rainy and dry periods."},
            {"Focus on plants or animals suited to your location.",
             "Ask about locally adapted seeds and crop varieties.",
             "Animals need space, shelter, feed and clean water.",
             "Plan within your available money and daily time.",
             "Think about demand before increasing production."},
            {"Observe soil texture and drainage before planting.",
             "Prepare safe, clean and usable tools first.",
             "Make a checklist of everything you need.",
             "Record expected and recurring costs.",
             "Keep a reserve for unexpected needs."},
            {"Check moisture instead of watering by guesswork.",
             "Inspect plants before small issues become bigger.",
             "Keep animals healthy with routine care.",
             "Short daily notes make trends easier to spot.",
             "Seek expert support for unfamiliar symptoms."},
            {"Harvest according to the crop's maturity.",
             "Use clean containers and protect products from heat.",
             "Track your actual harvest by weight or quantity.",
             "Compare sales against all production costs.",
             "Use records to improve the next planting cycle."}
        };
        try {
            int section = Integer.parseInt(guide.step().trim()) - 1;
            if (section >= 0 && section < tips.length && index >= 0 && index < 5)
                return tips[section][index];
        } catch (NumberFormatException ignored) { }
        return "Consider how this step applies to your own farming situation.";
    }

    private static String checkQuestion(Guide guide, String original) {
        String[] questions = {
            "What is your first farming goal, and which resources do you already have?",
            "Does your chosen location have enough sunlight, suitable drainage and water?",
            "Which crop or animal best fits your space, skills and budget?",
            "Can you list the tools, materials and estimated costs before starting?",
            "What three daily tasks will you record to monitor your farm?",
            "How will you record your harvest, expenses and sales?"
        };
        try {
            int idx = Integer.parseInt(guide.step().trim()) - 1;
            if (idx >= 0 && idx < questions.length) return questions[idx];
        } catch (NumberFormatException ignored) { }
        return original.isBlank() ? "Can you explain what you learned in your own words?" : original;
    }

    private record GuideViewParts(String intro, List<String> steps,
                                  String example, String checkpoint) { }

    // ======================================================
    // FLOATING AI BUTTON
    // ======================================================

    private void showFab() {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::showFab);
            return;
        }

        if (host.frame == null) return;

        JLayeredPane current =
            host.frame.getLayeredPane();

        if (layer != null && layer != current) {
            disposeAi();
        }

        layer = current;

        if (fab == null) {

            fab = new RobotFab();

            fab.setToolTipText(
                "Open SAKA AI Assistant"
            );

            fab.addActionListener(e -> showAi());

            layer.add(
                fab,
                JLayeredPane.PALETTE_LAYER
            );
        }

        if (layerResize == null) {

            layerResize = new ComponentAdapter() {

                @Override
                public void componentResized(ComponentEvent e) {
                    positionAi();
                }
            };

            layer.addComponentListener(layerResize);
        }

        positionAi();

        fab.setVisible(
            chatPanel == null ||
            !chatPanel.isVisible()
        );
    }

    private void showAi() {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::showAi);
            return;
        }

        showFab();

        if (layer == null) return;

        if (chatPanel == null) {

            chatPanel = createAssistant();

            layer.add(
                chatPanel,
                JLayeredPane.POPUP_LAYER
            );
        }

        positionAi();

        chatPanel.setVisible(true);

        fab.setVisible(false);

        layer.moveToFront(chatPanel);

        if (chatInput != null) {

            SwingUtilities.invokeLater(() ->
                chatInput.requestFocusInWindow()
            );
        }
    }

    private void positionAi() {

        if (layer == null) return;

        int w = layer.getWidth();
        int h = layer.getHeight();

        if (fab != null) {

            // SAKA_AI_FAB_POLISH_V1
        fab.setBounds(Math.max(3, w - 85), Math.max(3, h - 95), 62, 62);
        }

        if (chatPanel != null && w > 40 && h > 40) {

            int pw = Math.min(760, w - 14);
            int ph = Math.min(650, h - 14);

            chatPanel.setBounds(
                Math.max(4, w - pw - 12),
                Math.max(4, h - ph - 12),
                pw, ph
            );

            chatPanel.revalidate();
            chatPanel.repaint();
        }
    }

    private void minimizeAi() {

        if (chatPanel != null) {
            chatPanel.setVisible(false);
        }

        if (fab != null) {
            fab.setVisible(true);
        }

        if (layer != null) {
            layer.repaint();
        }
    }

    private void scheduleCleanup() {

        if (cleanupQueued) return;

        cleanupQueued = true;

        SwingUtilities.invokeLater(() -> {
            cleanupQueued = false;
            disposeAi();
        });
    }

    private void disposeAi() {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::disposeAi);
            return;
        }

        JLayeredPane old = layer;

        if (old == null) return;

        if (layerResize != null) {
            old.removeComponentListener(layerResize);
        }

        if (chatPanel != null) {
            old.remove(chatPanel);
        }

        if (fab != null) {
            old.remove(fab);
        }

        layer = null;
        layerResize = null;
        chatPanel = null;
        fab = null;
        chatInput = null;

        old.repaint();
    }

    // ======================================================
    // GLOSSY GEMINI AI CHAT
    // ======================================================

    private JComponent createAssistant() {

        JPanel outer = clear(
            new BorderLayout()
        );

        outer.setBorder(
            new EmptyBorder(5, 5, 6, 6)
        );

        JPanel main = glass(
            new BorderLayout(0, 12),
            new Color(225, 254, 231),
            new Color(185, 237, 198),
            28
        );

        main.setBorder(
            new EmptyBorder(12, 12, 12, 12)
        );

        outer.add(main);

        // --------------------------------------------------
        // HEADER
        // --------------------------------------------------

        JPanel header = glass(
            new BorderLayout(12, 0),
            new Color(28, 185, 87),
            DARK,
            24
        );

        header.setBorder(
            new EmptyBorder(13, 15, 13, 12)
        );

        Symbol bot = new Symbol(
            "robot",
            Color.WHITE
        );

        bot.setPreferredSize(
            new Dimension(50, 55)
        );

        header.add(bot, BorderLayout.WEST);

        JPanel labels = col();

        labels.add(label(
            "SAKA-SMART AI ASSISTANT",
            16, true, Color.WHITE
        ));

        gap(labels, 7);

        labels.add(label(
            "Your smart farming companion",
            12, false,
            new Color(231, 255, 234)
        ));

        header.add(labels, BorderLayout.CENTER);

        JButton min = button("-", false);

        min.setPreferredSize(
            new Dimension(44, 42)
        );

        min.addActionListener(e -> minimizeAi());

        header.add(min, BorderLayout.EAST);

        addFixedMain(
            main, header, 93, BorderLayout.NORTH
        );

        // --------------------------------------------------
        // CHAT HISTORY
        // --------------------------------------------------

        JPanel messages = col();

        messages.setOpaque(true);

        messages.setBackground(
            new Color(251, 255, 250)
        );

        messages.setBorder(
            new EmptyBorder(12, 12, 14, 12)
        );

        JScrollPane chatScroll =
            new JScrollPane(messages);

        chatScroll.setBorder(null);

        chatScroll.setHorizontalScrollBarPolicy(
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );

        chatScroll.getViewport().setBackground(
            new Color(251, 255, 250)
        );

        scroll(chatScroll);

        JPanel conversation = glass(
            new BorderLayout(),
            WHITE,
            new Color(239, 251, 239),
            22
        );

        conversation.setBorder(
            new EmptyBorder(8, 8, 8, 8)
        );

        conversation.add(chatScroll);

        main.add(
            conversation,
            BorderLayout.CENTER
        );

        // --------------------------------------------------
        // MESSAGE INPUT
        // --------------------------------------------------

        chatInput = new JTextArea(2, 18) {

            @Override
            protected void paintComponent(Graphics original) {
                super.paintComponent(original);

                if (!getText().isEmpty()) {
                    return;
                }

                Graphics2D g =
                    (Graphics2D) original.create();

                g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                );

                g.setColor(new Color(119, 148, 126));

                FontMetrics fm = g.getFontMetrics(getFont());
                Insets insets = getInsets();

                g.drawString(
                    "Ask SAKA-SMART AI anything...",
                    insets.left + 2,
                    insets.top + fm.getAscent() + 2
                );

                g.dispose();
            }
        };

        chatInput.setFont(
            FONT.deriveFont(15f)
        );

        chatInput.setLineWrap(true);
        chatInput.setWrapStyleWord(true);

        chatInput.setForeground(DARK);
        chatInput.setCaretColor(GREEN);
        chatInput.setBackground(WHITE);

        chatInput.setBorder(
            new EmptyBorder(9, 12, 9, 12)
        );

        JScrollPane inputScroll =
            new JScrollPane(chatInput);

        inputScroll.setBorder(null);

        inputScroll.setHorizontalScrollBarPolicy(
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );

        scroll(inputScroll);

        JPanel inputFrame = glass(
            new BorderLayout(),
            WHITE, MINT, 18
        );

        inputFrame.setBorder(
            new EmptyBorder(5, 5, 5, 5)
        );

        inputFrame.add(inputScroll);

        JButton send = new IconButton();

        send.setPreferredSize(
            new Dimension(62, 65)
        );

        send.setToolTipText("Send message");

        JPanel inputRow = clear(
            new BorderLayout(10, 0)
        );

        inputRow.add(
            inputFrame,
            BorderLayout.CENTER
        );

        inputRow.add(
            send,
            BorderLayout.EAST
        );

        JLabel status = label(
            LearningAiService.configurationText(),
            11, false, MUTED
        );

        JLabel hint = label(
            "Enter to send  •  Shift+Enter for a new line",
            10, false, MUTED
        );

        JPanel footer = clear(
            new BorderLayout(0, 8)
        );

        footer.add(
            inputRow,
            BorderLayout.CENTER
        );

        JPanel footText = col();

        footText.add(status);
        gap(footText, 4);
        footText.add(hint);

        footer.add(
            footText,
            BorderLayout.SOUTH
        );

        addFixedMain(
            main,
            footer,
            125,
            BorderLayout.SOUTH
        );

        // --------------------------------------------------
        // GEMINI SERVICE
        // --------------------------------------------------

        List<LearningAiService.Turn> history =
            new ArrayList<>();

        final boolean[] busy = {false};

        Runnable scrollBottom = () ->
            SwingUtilities.invokeLater(() -> {

                JScrollBar bar =
                    chatScroll.getVerticalScrollBar();

                bar.setValue(bar.getMaximum());
            });

        java.util.function.BiConsumer<String, Boolean> add =
            (message, user) -> {

                appendMessage(
                    messages,
                    chatScroll,
                    message,
                    user
                );

                scrollBottom.run();
            };

        add.accept(
            "Kumusta, ka-farmer!\n\n" +
            "I'm your SAKA-SMART AI Assistant. " +
            "Ask about crops, livestock, farm planning, " +
            "expenses, or sustainability.",
            false
        );

        send.addActionListener(e -> {

            if (busy[0]) return;

            String question =
                chatInput.getText().trim();

            if (question.isEmpty()) return;

            if (question.length() > 1800) {

                status.setText(
                    "Maximum 1,800 characters."
                );

                return;
            }

            if (!LearningAiService.configured()) {

                status.setText(
                    "Configure GEMINI_API_KEY to activate SAKA AI."
                );

                return;
            }

            List<LearningAiService.Turn> before =
                new ArrayList<>(history);

            chatInput.setText("");

            add.accept(question, true);

            busy[0] = true;
            send.setEnabled(false);

            status.setText(
                "SAKA AI is thinking..."
            );

            JLabel thinking = label(
                "SAKA AI is typing...",
                12, false, GREEN
            );

            messages.add(thinking);
            messages.revalidate();
            messages.repaint();

            scrollBottom.run();

            new SwingWorker<String, Void>() {

                @Override
                protected String doInBackground()
                    throws Exception {

                    return LearningAiService.ask(
                        question,
                        "Auto",
                        before
                    );
                }

                @Override
                protected void done() {

                    messages.remove(thinking);

                    busy[0] = false;
                    send.setEnabled(true);

                    try {

                        String answer = get();

                        history.add(
                            new LearningAiService.Turn(
                                "user",
                                question
                            )
                        );

                        history.add(
                            new LearningAiService.Turn(
                                "assistant",
                                answer
                            )
                        );

                        add.accept(answer, false);

                        status.setText(
                            "Connected  •  Verify important advice locally"
                        );

                    } catch (Exception ex) {

                        Throwable cause =
                            ex.getCause() == null
                                ? ex
                                : ex.getCause();

                        add.accept(
                            "Sorry, I couldn't answer right now.\n" +
                            Objects.toString(
                                cause.getMessage(),
                                "Please try again."
                            ),
                            false
                        );

                        status.setText(
                            "AI unavailable  •  Please try again"
                        );
                    }

                    messages.revalidate();
                    messages.repaint();

                    scrollBottom.run();
                }
            }.execute();
        });

        chatInput.getInputMap(
            JComponent.WHEN_FOCUSED
        ).put(
            KeyStroke.getKeyStroke("ENTER"),
            "sendMessage"
        );

        chatInput.getActionMap().put(
            "sendMessage",
            new AbstractAction() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    if (!busy[0]) {
                        send.doClick();
                    }
                }
            }
        );

        chatInput.getInputMap(
            JComponent.WHEN_FOCUSED
        ).put(
            KeyStroke.getKeyStroke("shift ENTER"),
            "insert-break"
        );

        return outer;
    }

    // ======================================================
    // CHAT BUBBLES
    // ======================================================

    private static void appendMessage(
        JPanel messages,
        JScrollPane scrollPane,
        String message,
        boolean user
    ) {

        if (message == null) {
            message = "";
        }

        int viewportWidth = scrollPane.getViewport().getWidth();

        if (viewportWidth < 100) {
            viewportWidth = Math.max(
                300,
                scrollPane.getWidth() - 30
            );
        }

        int maxBubbleWidth = Math.min(
            600,
            Math.max(160, viewportWidth - 45)
        );

        JTextArea text = area(message, 14, DARK);

        FontMetrics metrics =
            text.getFontMetrics(text.getFont());

        int longestLine = 0;

        for (String line : message.split("\n", -1)) {

            longestLine = Math.max(
                longestLine,
                metrics.stringWidth(line)
            );
        }

        int textWidth = Math.max(
            95,
            Math.min(maxBubbleWidth - 32, longestLine + 12)
        );

        int bubbleWidth = Math.min(
            maxBubbleWidth,
            Math.max(135, textWidth + 32)
        );

        // Measure wrapped text at the calculated width.
        text.setSize(
            textWidth,
            Short.MAX_VALUE
        );

        int textHeight = Math.max(
            metrics.getHeight() + 10,
            text.getPreferredSize().height + 6
        );

        text.setPreferredSize(
            new Dimension(textWidth, textHeight)
        );

        JPanel bubble = glass(
            new BorderLayout(0, 9),
            user
                ? new Color(215, 248, 222)
                : WHITE,
            user
                ? new Color(191, 239, 207)
                : new Color(239, 251, 240),
            23
        );

        bubble.setBorder(
            new EmptyBorder(13, 15, 13, 15)
        );

        JLabel sender = label(
            user ? "YOU" : "SAKA AI",
            11,
            true,
            GREEN
        );

        bubble.add(
            sender,
            BorderLayout.NORTH
        );

        bubble.add(
            text,
            BorderLayout.CENTER
        );

        int bubbleHeight = textHeight + 57;

        bubble.setPreferredSize(
            new Dimension(
                bubbleWidth,
                bubbleHeight
            )
        );

        // Full-width row, but content-sized bubble.
        JPanel row = clear(
            new BorderLayout()
        );

        row.setAlignmentX(
            Component.LEFT_ALIGNMENT
        );

        row.add(
            bubble,
            user
                ? BorderLayout.EAST
                : BorderLayout.WEST
        );

        row.setPreferredSize(
            new Dimension(
                Math.max(1, viewportWidth - 20),
                bubbleHeight + 12
            )
        );

        row.setMaximumSize(
            new Dimension(
                Integer.MAX_VALUE,
                bubbleHeight + 12
            )
        );

        messages.add(row);
        messages.add(Box.createVerticalStrut(7));

        messages.revalidate();
        messages.repaint();
    }
    // ======================================================
    // GLASS PANELS
    // ======================================================

    private static JPanel sideCard(
        String title,
        JComponent content
    ) {

        JPanel card = glass(
            new BorderLayout(0, 11),
            WHITE,
            new Color(224, 249, 228),
            25
        );

        card.setBorder(
            new EmptyBorder(16, 16, 16, 16)
        );

        card.add(
            label(title, 17, true, DARK),
            BorderLayout.NORTH
        );

        card.add(
            content,
            BorderLayout.CENTER
        );

        return card;
    }

    private static JPanel glass(
        LayoutManager layout,
        Color top,
        Color bottom,
        int radius
    ) {

        return new JPanel(layout) {

            {
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics original) {

                Graphics2D g =
                    (Graphics2D) original.create();

                aa(g);

                int w = getWidth();
                int h = getHeight();

                if (w > 5 && h > 6) {

                    g.setColor(
                        new Color(1, 68, 34, 25)
                    );

                    g.fillRoundRect(
                        2, 4,
                        w - 4,
                        h - 5,
                        radius, radius
                    );

                    g.setPaint(new GradientPaint(
                        0, 0, top,
                        0, h, bottom
                    ));

                    g.fillRoundRect(
                        0, 0,
                        w - 2, h - 4,
                        radius, radius
                    );

                    Shape oldClip = g.getClip();

                    g.clip(
                        new RoundRectangle2D.Double(
                            0, 0,
                            w - 2, h - 4,
                            radius, radius
                        )
                    );

                    g.setPaint(new GradientPaint(
                        0, 0,
                        new Color(255, 255, 255, 73),
                        0, h / 2f,
                        new Color(255, 255, 255, 0)
                    ));

                    g.fillRect(
                        0, 0, w, h / 2
                    );

                    g.setClip(oldClip);

                    g.setColor(BORDER);

                    g.drawRoundRect(
                        0, 0,
                        w - 3, h - 5,
                        radius, radius
                    );
                }

                g.dispose();

                super.paintComponent(original);
            }
        };
    }

    // ======================================================
    // BUTTONS
    // ======================================================

    private static JButton button(
        String caption,
        boolean strong
    ) {

        return new JButton(caption) {

            {
                setFont(
                    FONT.deriveFont(Font.BOLD, 12f)
                );

                setForeground(
                    strong ? Color.WHITE : DARK
                );

                setBorder(
                    new EmptyBorder(9, 12, 9, 12)
                );

                setContentAreaFilled(false);
                setFocusPainted(false);

                setCursor(
                    Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                    )
                );
            }

            @Override
            protected void paintComponent(Graphics old) {

                Graphics2D g =
                    (Graphics2D) old.create();

                aa(g);

                int w = getWidth();
                int h = getHeight();

                Color top = strong
                    ? new Color(70, 199, 111)
                    : WHITE;

                Color bottom = strong
                    ? DEEP
                    : MINT;

                if (getModel().isRollover()) {
                    top = top.brighter();
                }

                if (!isEnabled()) {
                    top = new Color(199, 213, 200);
                    bottom = new Color(151, 177, 157);
                }

                g.setPaint(new GradientPaint(
                    0, 0, top,
                    0, h, bottom
                ));

                g.fillRoundRect(
                    0, 0, w - 2, h - 3,
                    17, 17
                );

                g.setColor(
                    strong ? DEEP : BORDER
                );

                g.drawRoundRect(
                    0, 0, w - 3, h - 4,
                    17, 17
                );

                g.dispose();

                super.paintComponent(old);
            }
        };
    }

    private static final class Pill
        extends JToggleButton {

        Pill(String title) {

            super(title);

            setFont(
                FONT.deriveFont(Font.BOLD, 12f)
            );

            setBorder(
                new EmptyBorder(10, 12, 10, 12)
            );

            setContentAreaFilled(false);
            setFocusPainted(false);

            setCursor(
                Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )
            );
        }

        @Override
        protected void paintComponent(Graphics old) {

            Graphics2D g =
                (Graphics2D) old.create();

            aa(g);

            int w = getWidth();
            int h = getHeight();

            boolean selected = isSelected();

            g.setPaint(new GradientPaint(
                0, 0,
                selected
                    ? new Color(84, 209, 116)
                    : WHITE,
                0, h,
                selected ? DEEP : MINT
            ));

            g.fillRoundRect(
                0, 0,
                w - 2, h - 3,
                25, 25
            );

            g.setColor(
                selected ? DEEP : BORDER
            );

            g.drawRoundRect(
                0, 0,
                w - 3, h - 4,
                25, 25
            );

            setForeground(
                selected ? Color.WHITE : DARK
            );

            g.dispose();

            super.paintComponent(old);
        }
    }

    // ======================================================
    // FEATURED HERO
    // ======================================================

    private static final class Hero
        extends JPanel {

        Hero(Guide guide, Runnable open) {

            super(new BorderLayout());

            setOpaque(false);

            setBorder(
                new EmptyBorder(24, 23, 19, 23)
            );

            JPanel center = col();

            center.add(label(
                "FEATURED BEGINNER GUIDE",
                11, true,
                new Color(218, 255, 217)
            ));

            gap(center, 19);

            center.add(label(
                guide.title(),
                23, true,
                Color.WHITE
            ));

            gap(center, 10);

            center.add(label(
                "Build your farming skills one step at a time.",
                13, false,
                new Color(224, 255, 224)
            ));

            add(center, BorderLayout.NORTH);

            JPanel footer = clear(
                new BorderLayout()
            );

            footer.add(
                label(
                    "6 LESSONS  •  YOUR FARMING JOURNEY",
                    11, true,
                    Color.WHITE
                ),
                BorderLayout.WEST
            );

            JButton go = button(
                "Start Learning  >",
                false
            );

            go.addActionListener(e -> open.run());

            footer.add(
                go,
                BorderLayout.EAST
            );

            add(footer, BorderLayout.SOUTH);

            setCursor(
                Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )
            );

            addMouseListener(
                new MouseAdapter() {

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        open.run();
                    }
                }
            );
        }

        @Override
        protected void paintComponent(Graphics old) {

            Graphics2D g =
                (Graphics2D) old.create();

            aa(g);

            int w = getWidth();
            int h = getHeight();

            g.setPaint(new GradientPaint(
                0, 0,
                new Color(8, 129, 57),
                w, h,
                new Color(0, 73, 38)
            ));

            g.fillRoundRect(
                0, 0,
                w - 2, h - 4,
                27, 27
            );

            Shape clip = g.getClip();

            g.clip(
                new RoundRectangle2D.Double(
                    0, 0,
                    w - 2, h - 4,
                    27, 27
                )
            );

            g.setPaint(
                new RadialGradientPaint(
                    new Point2D.Float(
                        w * .83f,
                        h * .39f
                    ),
                    Math.max(60, w * .32f),
                    new float[]{0f, 1f},
                    new Color[]{
                        new Color(200, 255, 116, 125),
                        new Color(200, 255, 116, 0)
                    }
                )
            );

            g.fillRect(0, 0, w, h);

            // Realistic farm photographs crossfade every five seconds.
            // Only occupy the right side so the guide title stays readable.
            int px = (int) (w * .62);
            int py = 14;
            int pw = Math.max(20, (int)(w * .35));
            int ph = Math.max(20, h - 35);
            Shape photoClip = g.getClip();
            g.clip(new RoundRectangle2D.Double(px, py, pw, ph, 20, 20));
            boolean loaded = PhotoArt.paintSlide(g, new String[]{
                "photo_seedling.jpg", "photo_irrigation.jpg", "photo_harvest.jpg"
            }, px, py, pw, ph, 0);
            g.setClip(photoClip);
            if (!loaded) drawPlant(g, (int)(w * .82), (int)(h * .64), 1.5);
            // Blend the left edge of the photograph into the green hero.
            g.setPaint(new GradientPaint(px - 30, 0, new Color(5, 108, 49, 210),
                px + pw, 0, new Color(5, 108, 49, 0)));
            g.fillRect(px - 30, py, pw + 30, ph);


            g.setColor(
                new Color(131, 247, 130, 60)
            );

            g.setStroke(
                new BasicStroke(3)
            );

            g.drawArc(
                -45,
                h - 63,
                w + 110,
                105,
                0, 160
            );

            g.setClip(clip);

            g.setColor(
                new Color(221, 255, 224, 150)
            );

            g.drawRoundRect(
                1, 1,
                w - 4, h - 7,
                27, 27
            );

            g.dispose();

            super.paintComponent(old);
        }
    }

    // ======================================================
    // RESOURCE CARDS
    // ======================================================

    private static final class Tile extends JButton {
        Tile(Guide guide, boolean done, Runnable open) {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(11, 11, 12, 11));
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            PhotoArt picture = new PhotoArt(photosFor(guide), guide.title());
            picture.setPreferredSize(new Dimension(120, 116));
            add(picture, BorderLayout.WEST);
            JPanel details = col();
            JTextArea name = area(guide.title(), 15, DARK);
            name.setFont(FONT.deriveFont(Font.BOLD, 15f));
            name.setToolTipText(guide.title());
            addFixed(details, name, 42);
            gap(details, 3);
            JTextArea preview = area(guide.description(), 13, new Color(42, 83, 57));
            addFixed(details, preview, 57);
            gap(details, 4);
            JPanel bottom = clear(new BorderLayout());
            bottom.add(label(guide.category(), 11, true, GREEN), BorderLayout.WEST);
            bottom.add(label(done ? "READ  ✓" : "READ GUIDE  >", 11, true, GREEN), BorderLayout.EAST);
            addFixed(details, bottom, 19);
            add(details, BorderLayout.CENTER);
            setPreferredSize(new Dimension(340, 145));
            addActionListener(e -> open.run());
        }

        @Override protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            aa(g);
            int w = getWidth(), h = getHeight();
            if (w < 8 || h < 8) { g.dispose(); return; }
            boolean hover = getModel().isRollover();
            g.setColor(new Color(5, 75, 35, hover ? 42 : 23));
            g.fillRoundRect(3, 5, w - 6, h - 7, 23, 23);
            g.setPaint(new GradientPaint(0, 0, WHITE, w, h,
                hover ? new Color(213, 248, 221) : new Color(233, 250, 235)));
            g.fillRoundRect(1, 0, w - 4, h - 6, 22, 22);
            g.setColor(hover ? new Color(39, 177, 90) : BORDER);
            g.setStroke(new BasicStroke(hover ? 2f : 1f));
            g.drawRoundRect(1, 0, w - 5, h - 7, 22, 22);
            if (hover) {
                g.setColor(new Color(255, 255, 255, 105));
                g.fillRoundRect(5, 4, w - 13, Math.max(1, h / 4), 20, 20);
            }
            g.dispose();
            super.paintComponent(original);
        }
    }

    // ======================================================
    // OFFLINE IMAGE SLIDESHOW — SINGLE SWING TIMER
    // ======================================================

    private static final long SLIDE_DURATION_MS = 5000L;
    private static final long FADE_DURATION_MS = 750L;
    private static final long SLIDE_START_TIME = System.currentTimeMillis();
    private static final Map<String, BufferedImage> PHOTO_CACHE = new HashMap<>();

    private static String[] photosFor(Guide guide) {
        // V10 named photo slots; if the matching file is unavailable, existing
        // offline SAKA-SMART photos continue to work without a crash.
        Map<String, String> dedicated = Map.ofEntries(
            Map.entry("palay (rice): healthy crop management", "crop_palay.jpg"),
            Map.entry("mais (corn): planning and care", "crop_mais.jpg"),
            Map.entry("mangga (mango): orchard basics", "crop_mangga.jpg"),
            Map.entry("sibuyas (onion): growing and handling", "crop_sibuyas.jpg"),
            Map.entry("singkamas (jicama): root crop basics", "crop_singkamas.jpg"),
            Map.entry("chicken: care and farm biosecurity", "livestock_chicken.jpg"),
            Map.entry("pig: care and farm biosecurity", "livestock_pig.jpg"),
            Map.entry("cow: care and farm biosecurity", "livestock_cow.jpg"),
            Map.entry("carabao: care and farm biosecurity", "livestock_carabao.jpg"),
            Map.entry("goat: care and farm biosecurity", "livestock_goat.jpg"),
            Map.entry("farm records: know your costs", "finance_records.jpg"),
            Map.entry("income, expenses and profit", "finance_income.jpg"),
            Map.entry("healthy soil and composting", "sustain_soil.jpg"),
            Map.entry("save water on your farm", "sustain_water.jpg"),
            Map.entry("crop rotation and diversification", "sustain_rotation.jpg"),
            Map.entry("integrated pest management (ipm)", "sustain_ipm.jpg"),
            Map.entry("agroforestry and farm biodiversity", "sustain_agroforestry.jpg"),
            Map.entry("safety first: machinery and equipment", "safety_first.jpg"),
            Map.entry("pesticide safety: read before spraying", "safety_pesticide.jpg"),
            Map.entry("crop protection and food safety", "safety_crop.jpg"),
            Map.entry("livestock biosecurity and animal health", "safety_animal.jpg"),
            Map.entry("safe harvest and handling", "safety_harvest.jpg")
        );
        String selected = dedicated.get(guide.title().toLowerCase(Locale.ROOT));
        if (selected != null && PhotoArt.loadImage(selected) != null) return new String[]{selected};
        String topic = (guide.title() + " " + guide.category())
            .toLowerCase(Locale.ROOT);
        if (topic.contains("livestock") || topic.contains("animal") ||
            topic.contains("chicken")) {
            return new String[]{"photo_livestock.jpg", "photo_harvest.jpg"};
        }
        if (topic.contains("finance") || topic.contains("record") ||
            topic.contains("budget") || topic.contains("expense")) {
            return new String[]{"photo_finance.jpg", "photo_tools.jpg"};
        }
        if (topic.contains("tool") || topic.contains("soil") ||
            topic.contains("pesticide") || topic.contains("safety")) {
            return new String[]{"photo_tools.jpg", "photo_seedling.jpg"};
        }
        if (topic.contains("water") || topic.contains("sunlight") ||
            topic.contains("irrigation") || topic.contains("rain")) {
            return new String[]{"photo_irrigation.jpg", "photo_seedling.jpg"};
        }
        if (topic.contains("harvest") || topic.contains("sales") ||
            topic.contains("market")) {
            return new String[]{"photo_harvest.jpg", "photo_finance.jpg"};
        }
        return new String[]{"photo_seedling.jpg", "photo_irrigation.jpg",
            "photo_harvest.jpg"};
    }

    private static void repaintSlides(Component root) {
        if (root instanceof PhotoArt || root instanceof Hero) root.repaint();
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) repaintSlides(child);
        }
    }

    private static final class PhotoArt extends JComponent {
        private final String[] filenames;
        private final FarmArt fallback;
        private int offset;
        private final int initialOffset;

        PhotoArt(String[] filenames, String topic) {
            this.filenames = filenames.clone();
            this.fallback = new FarmArt(topic);
            this.initialOffset = 0;
            setOpaque(false);
            setPreferredSize(new Dimension(100, 122));
            setToolTipText("Offline farming photo slideshow");
        }

        int slideIndex() {
            long elapsed = Math.max(0L, System.currentTimeMillis() - SLIDE_START_TIME);
            return Math.floorMod((int)(elapsed / SLIDE_DURATION_MS) + offset +
                initialOffset, filenames.length);
        }

        void advance() {
            offset = (offset + 1) % filenames.length;
            repaint();
        }

        @Override protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            aa(g);
            int w = getWidth(), h = getHeight();
            if (w < 2 || h < 2) { g.dispose(); return; }
            Shape oldClip = g.getClip();
            g.clip(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 16, 16));
            boolean loaded = paintSlide(g, filenames, 0, 0, w, h, offset + initialOffset);
            if (!loaded) {
                fallback.setSize(w, h);
                fallback.paint(g);
            }
            g.setClip(oldClip);
            g.setColor(new Color(255, 255, 255, 115));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(1, 1, w - 3, h - 3, 16, 16);
            // Small slide indicators, visible even with local photos.
            if (filenames.length > 1 && w > 90) {
                int total = filenames.length * 13;
                int first = Math.max(8, (w - total) / 2);
                for (int i = 0; i < filenames.length; i++) {
                    g.setColor(i == slideIndex()
                        ? Color.WHITE : new Color(255, 255, 255, 125));
                    g.fillOval(first + i * 13, h - 14, 7, 7);
                }
            }
            g.dispose();
        }

        static boolean paintSlide(Graphics2D g, String[] filenames,
                                  int x, int y, int w, int h, int shift) {
            if (filenames.length == 0 || w < 2 || h < 2) return false;
            long elapsed = Math.max(0L, System.currentTimeMillis() - SLIDE_START_TIME);
            int index = Math.floorMod((int)(elapsed / SLIDE_DURATION_MS) + shift,
                filenames.length);
            BufferedImage first = loadImage(filenames[index]);
            if (first == null) {
                for (String filename : filenames) {
                    first = loadImage(filename);
                    if (first != null) break;
                }
            }
            if (first == null) return false;
            paintCover(g, first, x, y, w, h, 1f);
            long phase = elapsed % SLIDE_DURATION_MS;
            if (phase >= SLIDE_DURATION_MS - FADE_DURATION_MS) {
                BufferedImage next = loadImage(filenames[(index + 1) % filenames.length]);
                if (next != null) {
                    float alpha = Math.min(1f,
                        (phase - (SLIDE_DURATION_MS - FADE_DURATION_MS)) /
                        (float) FADE_DURATION_MS);
                    paintCover(g, next, x, y, w, h, alpha);
                }
            }
            return true;
        }

        private static void paintCover(Graphics2D g, BufferedImage image,
                                       int x, int y, int width, int height,
                                       float alpha) {
            Graphics2D layer = (Graphics2D) g.create();
            layer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            double scale = Math.max(width / (double) image.getWidth(),
                height / (double) image.getHeight());
            int dw = Math.max(1, (int)Math.ceil(image.getWidth() * scale));
            int dh = Math.max(1, (int)Math.ceil(image.getHeight() * scale));
            layer.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            layer.drawImage(image, x + (width - dw) / 2, y + (height - dh) / 2,
                dw, dh, null);
            layer.dispose();
        }

        static BufferedImage loadImage(String filename) {
            synchronized (PHOTO_CACHE) {
                if (PHOTO_CACHE.containsKey(filename)) return PHOTO_CACHE.get(filename);
                BufferedImage image = null;
                try {
                    File file = new File("assets/learning", filename);
                    if (file.isFile()) image = ImageIO.read(file);
                    if (image == null) {
                        try (java.io.InputStream stream = LearningPage.class.getResourceAsStream(
                            "/assets/learning/" + filename)) {
                            if (stream != null) image = ImageIO.read(stream);
                        }
                    }
                } catch (Exception ignored) {
                    // Keep the existing Java2D illustration if an asset is absent.
                }
                PHOTO_CACHE.put(filename, image);
                return image;
            }
        }
    }

    // ======================================================
    // FARM ARTWORK
    // ======================================================

    private static final class FarmArt extends JComponent {
        private final String topic;

        FarmArt(int step) {
            this(switch (step) {
                case 2 -> "water";
                case 4 -> "tools";
                case 5 -> "records";
                case 6 -> "harvest";
                default -> "seedling";
            });
        }
        FarmArt(String topic) {
            this.topic = topic == null ? "seedling" : topic.toLowerCase(Locale.ROOT);
            setPreferredSize(new Dimension(100, 108));
        }

        @Override protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            aa(g);
            int w = getWidth(), h = getHeight();
            if (w < 5 || h < 5) { g.dispose(); return; }
            g.setPaint(new GradientPaint(0, 0, new Color(204, 242, 195),
                0, h, new Color(88, 185, 119)));
            g.fillRoundRect(0, 0, w, h, 16, 16);
            Shape clip = g.getClip();
            g.clip(new RoundRectangle2D.Double(0, 0, w, h, 16, 16));
            g.setColor(new Color(255, 226, 129));
            g.fillOval(w - 42, 10, 26, 26);
            g.setColor(new Color(129, 197, 127));
            g.fillOval(-25, h - 43, w + 50, 86);
            g.setColor(new Color(33, 137, 72));
            g.fillOval(-20, h - 18, w + 40, 76);
            int cx = w / 2, cy = h / 2;

            if (topic.contains("livestock") || topic.contains("animal") ||
                topic.contains("chicken")) {
                g.setColor(new Color(255, 248, 224));
                g.fillOval(cx - 27, cy - 13, 57, 43);
                g.fillOval(cx + 11, cy - 26, 31, 30);
                g.setColor(new Color(235, 89, 64));
                g.fillOval(cx + 16, cy - 32, 11, 11);
                g.fillOval(cx + 26, cy - 32, 10, 11);
                g.setColor(new Color(242, 168, 55));
                Path2D beak = new Path2D.Double();
                beak.moveTo(cx + 40, cy - 12);
                beak.lineTo(cx + 52, cy - 8);
                beak.lineTo(cx + 40, cy - 3);
                beak.closePath();
                g.fill(beak);
                g.setColor(DARK);
                g.fillOval(cx + 31, cy - 18, 4, 4);
                g.setStroke(new BasicStroke(3f));
                g.drawLine(cx - 10, cy + 24, cx - 10, cy + 37);
                g.drawLine(cx + 12, cy + 24, cx + 12, cy + 37);
            } else if (topic.contains("finance") || topic.contains("budget") ||
                topic.contains("record") || topic.contains("expense")) {
                g.setColor(new Color(255, 255, 243));
                g.fillRoundRect(cx - 29, cy - 35, 58, 72, 8, 8);
                g.setColor(new Color(18, 119, 69));
                for (int k = 0; k < 3; k++)
                    g.fillRoundRect(cx - 19, cy - 18 + k * 17, 38, 4, 3, 3);
                g.setColor(new Color(245, 190, 71));
                g.fillOval(cx + 9, cy + 12, 24, 24);
                g.setColor(new Color(156, 105, 25));
                g.drawOval(cx + 9, cy + 12, 24, 24);
            } else if (topic.contains("water") || topic.contains("weather") ||
                topic.contains("sunlight") || topic.contains("irrigation")) {
                g.setColor(new Color(25, 151, 189));
                for (int i = -1; i <= 1; i++) {
                    int x = cx + i * 27;
                    Path2D drop = new Path2D.Double();
                    drop.moveTo(x, cy - 30);
                    drop.curveTo(x - 22, cy - 2, x - 13, cy + 21, x, cy + 28);
                    drop.curveTo(x + 14, cy + 20, x + 20, cy - 2, x, cy - 30);
                    g.fill(drop);
                }
            } else if (topic.contains("safety") || topic.contains("pesticide")) {
                g.setColor(new Color(253, 253, 239));
                Path2D shield = new Path2D.Double();
                shield.moveTo(cx, cy - 37);
                shield.lineTo(cx + 30, cy - 25);
                shield.lineTo(cx + 24, cy + 16);
                shield.quadTo(cx, cy + 45, cx - 24, cy + 16);
                shield.lineTo(cx - 30, cy - 25);
                shield.closePath();
                g.fill(shield);
                g.setColor(new Color(20, 146, 70));
                g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.drawLine(cx - 13, cy + 2, cx - 2, cy + 13);
                g.drawLine(cx - 2, cy + 13, cx + 17, cy - 12);
            } else if (topic.contains("tools") || topic.contains("soil")) {
                g.setColor(new Color(146, 92, 52));
                g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.drawLine(cx - 18, cy - 29, cx + 12, cy + 25);
                g.setColor(new Color(246, 253, 242));
                g.fillRoundRect(cx - 1, cy + 7, 44, 28, 6, 6);
                g.setColor(new Color(40, 139, 82));
                g.drawRoundRect(cx - 1, cy + 7, 44, 28, 6, 6);
            } else if (topic.contains("rice") || topic.contains("harvest") ||
                topic.contains("sales")) {
                g.setColor(new Color(172, 112, 55));
                g.fillRoundRect(cx - 34, cy + 2, 68, 33, 6, 6);
                g.setColor(new Color(244, 196, 78));
                for (int i = 0; i < 5; i++)
                    g.fillOval(cx - 26 + i * 12, cy - 24 + (i % 2) * 6, 17, 29);
                g.setColor(new Color(120, 80, 35));
                g.drawRoundRect(cx - 34, cy + 2, 68, 33, 6, 6);
            } else if (topic.contains("livestock")) {
                drawPlant(g, cx, cy + 10, .75);
            } else {
                drawPlant(g, cx, cy + 12, .85);
                g.setColor(new Color(249, 234, 182, 165));
                g.fillOval(cx + 41, cy - 14, 17, 17);
            }
            g.setClip(clip);
            g.setColor(new Color(255, 255, 255, 105));
            g.drawRoundRect(1, 1, w - 3, h - 3, 16, 16);
            g.dispose();
        }
    }

    // ======================================================
    // JAVA2D ICONS
    // ======================================================

    private static class Symbol extends JComponent {

        private final String kind;
        private final Color color;

        Symbol(String kind, Color color) {

            this.kind = kind;
            this.color = color;

            setPreferredSize(
                new Dimension(35, 34)
            );
        }

        @Override
        protected void paintComponent(Graphics old) {

            Graphics2D g =
                (Graphics2D) old.create();

            aa(g);
            g.setColor(color);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            if (kind.equals("search")) {

                g.setStroke(
                    new BasicStroke(2.7f)
                );

                g.drawOval(
                    cx - 9,
                    cy - 10,
                    17, 17
                );

                g.drawLine(
                    cx + 5, cy + 5,
                    cx + 15, cy + 15
                );

            } else {

                drawRobot(
                    g, cx, cy, .60
                );
            }

            g.dispose();
        }
    }

    private static final class RobotFab
        extends JButton {

        RobotFab() {

            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);

            setCursor(
                Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )
            );
        }

        @Override
        protected void paintComponent(Graphics old) {

            Graphics2D g =
                (Graphics2D) old.create();

            aa(g);

            int w = getWidth();
            int h = getHeight();

            g.setColor(
                new Color(0, 51, 24, 75)
            );

            g.fillOval(
                6, 8,
                w - 11, h - 12
            );

            g.setPaint(new GradientPaint(
                0, 0,
                new Color(84, 219, 113),
                0, h, DEEP
            ));

            g.fillOval(
                2, 2,
                w - 9, h - 9
            );

            g.setColor(Color.WHITE);

            g.setStroke(
                new BasicStroke(2)
            );

            g.drawOval(
                3, 3,
                w - 11, h - 11
            );

            drawRobot(
                g,
                (w - 7) / 2,
                (h - 7) / 2,
                .94
            );

            g.dispose();
        }
    }

    private static final class IconButton
        extends JButton {

        IconButton() {

            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);

            setCursor(
                Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )
            );
        }

        @Override
        protected void paintComponent(Graphics old) {

            Graphics2D g =
                (Graphics2D) old.create();

            aa(g);

            int w = getWidth();
            int h = getHeight();

            g.setPaint(new GradientPaint(
                0, 0,
                isEnabled()
                    ? new Color(72, 218, 111)
                    : new Color(180, 207, 185),
                0, h,
                isEnabled()
                    ? DEEP
                    : new Color(140, 159, 141)
            ));

            g.fillRoundRect(
                0, 0,
                w - 2, h - 4,
                18, 18
            );

            g.setColor(
                new Color(255, 255, 255, 70)
            );

            g.fillRoundRect(
                4, 4,
                w - 11, h / 3,
                13, 13
            );

            int x = w / 2;
            int y = h / 2;

            Path2D p = new Path2D.Double();

            p.moveTo(x - 17, y - 4);
            p.lineTo(x + 17, y - 16);
            p.lineTo(x + 5, y + 17);
            p.lineTo(x - 1, y + 6);
            p.closePath();

            g.setColor(Color.WHITE);
            g.fill(p);

            g.setColor(DEEP);

            g.setStroke(
                new BasicStroke(2)
            );

            g.drawLine(
                x - 1, y + 6,
                x + 10, y - 8
            );

            g.dispose();
        }
    }

    private static void drawRobot(
        Graphics2D g,
        int x,
        int y,
        double scale
    ) {

        Graphics2D r =
            (Graphics2D) g.create();

        r.translate(x, y);
        r.scale(scale, scale);

        r.setColor(Color.WHITE);

        r.fillOval(
            -3, -31,
            6, 7
        );

        r.setStroke(
            new BasicStroke(3)
        );

        r.drawLine(0, -25, 0, -19);

        r.fillRoundRect(
            -24, -8,
            8, 18,
            6, 6
        );

        r.fillRoundRect(
            16, -8,
            8, 18,
            6, 6
        );

        r.fillRoundRect(
            -19, -18,
            38, 35,
            15, 15
        );

        r.setColor(DEEP);

        r.fillOval(-11, -4, 6, 6);
        r.fillOval(5, -4, 6, 6);

        r.setStroke(
            new BasicStroke(2)
        );

        r.drawArc(
            -7, -1,
            14, 10,
            200, 140
        );

        r.dispose();
    }

    private static void drawPlant(
        Graphics2D g,
        int x,
        int y,
        double scale
    ) {

        Graphics2D p =
            (Graphics2D) g.create();

        p.translate(x, y);
        p.scale(scale, scale);

        p.setColor(
            new Color(7, 94, 42)
        );

        p.setStroke(
            new BasicStroke(4)
        );

        p.drawLine(
            0, 24,
            0, -32
        );

        p.setColor(
            new Color(131, 226, 75)
        );

        p.fillOval(
            -41, -40,
            41, 22
        );

        p.fillOval(
            1, -55,
            46, 25
        );

        p.setColor(
            new Color(18, 133, 57)
        );

        p.setStroke(
            new BasicStroke(2)
        );

        p.drawLine(
            0, -30,
            -28, -31
        );

        p.drawLine(
            0, -34,
            26, -42
        );

        p.dispose();
    }

    // ======================================================
    // GLOSSY GREEN SCROLLBARS
    // ======================================================

    private static void scroll(JScrollPane pane) {

        JScrollBar bar =
            pane.getVerticalScrollBar();

        bar.setPreferredSize(
            new Dimension(11, 0)
        );

        bar.setUnitIncrement(23);

        bar.setUI(new GreenBar());
    }

    private static class GreenBar
        extends BasicScrollBarUI {

        private JButton zero() {

            JButton b = new JButton();

            b.setPreferredSize(
                new Dimension(0, 0)
            );

            b.setMinimumSize(
                new Dimension(0, 0)
            );

            return b;
        }

        @Override
        protected JButton createDecreaseButton(int d) {
            return zero();
        }

        @Override
        protected JButton createIncreaseButton(int d) {
            return zero();
        }

        @Override
        protected void paintTrack(
            Graphics old,
            JComponent c,
            Rectangle r
        ) {

            Graphics2D g =
                (Graphics2D) old.create();

            aa(g);

            g.setColor(
                new Color(220, 246, 225)
            );

            g.fillRoundRect(
                r.x + 2,
                r.y + 2,
                Math.max(3, r.width - 4),
                Math.max(3, r.height - 4),
                10, 10
            );

            g.dispose();
        }

        @Override
        protected void paintThumb(
            Graphics old,
            JComponent c,
            Rectangle r
        ) {

            if (r.width <= 0 || r.height <= 0) {
                return;
            }

            Graphics2D g =
                (Graphics2D) old.create();

            aa(g);

            g.setPaint(new GradientPaint(
                r.x, r.y,
                isThumbRollover() ? LIME : GREEN,
                r.x + r.width, r.y,
                DEEP
            ));

            g.fillRoundRect(
                r.x + 2,
                r.y + 2,
                Math.max(3, r.width - 4),
                Math.max(3, r.height - 4),
                10, 10
            );

            g.dispose();
        }
    }

    // ======================================================
    // UI HELPERS
    // ======================================================

    private static void aa(Graphics2D g) {

        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );
    }

    private static JPanel clear(LayoutManager layout) {

        JPanel p = new JPanel(layout);
        p.setOpaque(false);

        return p;
    }

    private static JPanel col() {

        JPanel p = new JPanel();

        p.setOpaque(false);

        p.setLayout(
            new BoxLayout(p, BoxLayout.Y_AXIS)
        );

        return p;
    }

    private static JLabel label(
        String text,
        int size,
        boolean bold,
        Color color
    ) {

        JLabel l = new JLabel(text);

        l.setFont(new Font(
            "Segoe UI",
            bold ? Font.BOLD : Font.PLAIN,
            size
        ));

        l.setForeground(color);

        l.setAlignmentX(
            Component.LEFT_ALIGNMENT
        );

        return l;
    }

    private static JTextArea area(
        String text,
        int size,
        Color color
    ) {

        JTextArea t = new JTextArea(text);

        t.setEditable(false);
        t.setFocusable(false);
        t.setOpaque(false);

        t.setLineWrap(true);
        t.setWrapStyleWord(true);

        t.setFont(new Font(
            "Segoe UI",
            Font.PLAIN,
            size
        ));

        t.setForeground(color);

        return t;
    }

    private static void gap(
        JPanel p,
        int height
    ) {

        p.add(
            Box.createVerticalStrut(height)
        );
    }

    private static void addFixed(
        JPanel parent,
        JComponent child,
        int height
    ) {

        JPanel wrapper = clear(
            new BorderLayout()
        );

        wrapper.add(child);

        setFixedSize(wrapper, height);

        wrapper.setAlignmentX(
            Component.LEFT_ALIGNMENT
        );

        parent.add(wrapper);
    }

    private static void addFixedMain(
        JPanel parent,
        JComponent child,
        int height,
        String side
    ) {

        child.setPreferredSize(
            new Dimension(1, height)
        );

        parent.add(child, side);
    }

    private static void setFixedSize(
        JComponent component,
        int height
    ) {

        component.setPreferredSize(
            new Dimension(1, height)
        );

        component.setMinimumSize(
            new Dimension(1, height)
        );

        component.setMaximumSize(
            new Dimension(
                Integer.MAX_VALUE,
                height
            )
        );
    }

    private static String shorten(
        String text,
        int limit
    ) {

        if (text == null) return "";

        return text.length() > limit
            ? text.substring(0, limit - 1) + "..."
            : text;
    }

    private static Guide guide(
        String step,
        String title,
        String category,
        String description,
        String content
    ) {

        return new Guide(
            title,
            category,
            description,
            content,
            "",
            step,
            null
        );
    }


    // SAKA_LEARNING_V10: offline educational category guides and verifiable APA references.
    // Research references are shown inside each resource; no live browsing is required.
    private static List<Guide> researchGuides() {
        List<Guide> result = new ArrayList<>();
        result.add(new Guide("Palay (Rice): Healthy Crop Management", "Crops", "Plan rice production using suitable varieties and field observations.", "INTRODUCTION:\nPlan rice production using suitable varieties and field observations.\n\n1. Begin with a variety suited to your local growing conditions. Ask a local agriculture office or PhilRice about appropriate planting material and season.\n\n2. Monitor irrigation, drainage and weeds regularly. Water needs depend on production system and weather; avoid relying on a fixed schedule for every field.\n\n3. Inspect leaves and crop growth for unusual symptoms. Use field observations and approved diagnostic tools before deciding on nutrient or pest-control measures.\n\n4. Keep planting, input and harvest records. Compare yield and costs after each season to guide your next crop plan.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nPhilippine Rice Research Institute. (n.d.). *INFOTXT & E-INFO: Pinoy Rice Knowledge Bank*. Philippine Rice Research Institute.\nhttps://www.philrice.gov.ph/services/infotxt-e-info/", "", "RESOURCE", null));
        result.add(new Guide("Mais (Corn): Planning and Care", "Crops", "Protect crop establishment and manage farm inputs wisely.", "INTRODUCTION:\nProtect crop establishment and manage farm inputs wisely.\n\n1. Choose maize varieties according to intended use, local climate and available market. Confirm appropriate seed and site conditions with local extension staff.\n\n2. Prepare planting rows with adequate drainage and check soil nutrient needs. Avoid applying fertilizer solely by guesswork.\n\n3. Walk the field routinely to monitor weeds, moisture, damaged leaves and insect activity. Integrated pest management emphasizes monitoring before intervention.\n\n4. Note seed, labor, irrigation and harvesting expenses as well as the quantity and quality of corn harvested.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *What is integrated plant nutrient management?*\nhttps://www.fao.org/agriculture/crops/thematic-sitemap/theme/spi/scpi-home/managing-ecosystems/integrated-plant-nutrient-management/ipnm-what/en/", "", "RESOURCE", null));
        result.add(new Guide("Mangga (Mango): Orchard Basics", "Crops", "Learn routine orchard checks and careful fruit handling.", "INTRODUCTION:\nLearn routine orchard checks and careful fruit handling.\n\n1. Mango trees require a suitable long-term location, adequate space and appropriate drainage. Plan access for pruning and harvest operations.\n\n2. Observe flowering and fruit development, checking for unusual pests and disease symptoms. Local conditions influence management needs.\n\n3. Use clean tools and avoid unnecessary damage during pruning or harvest. Ask a trained adviser before using crop chemicals.\n\n4. Keep records of the orchard, inputs, fruit quality and harvest quantities. Handle fruit gently to reduce bruising.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *Integrated pest management (IPM).*\nhttps://www.fao.org/pest-and-pesticide-management/ipm/integrated-pest-management/en", "", "RESOURCE", null));
        result.add(new Guide("Sibuyas (Onion): Growing and Handling", "Crops", "Understand soil moisture, crop health and postharvest care.", "INTRODUCTION:\nUnderstand soil moisture, crop health and postharvest care.\n\n1. Plan onion production for a locally suitable season and planting material. Good drainage and appropriate soil conditions are important.\n\n2. Check moisture frequently but avoid persistently waterlogged soil. Weed management and observation help reduce competition and spot problems.\n\n3. Identify signs of pests or disease before applying any treatment. Use label-approved products only when necessary and seek extension advice.\n\n4. Harvest at the appropriate maturity for your variety and handle bulbs carefully. Follow locally appropriate drying and storage guidance.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *What is integrated plant nutrient management?*\nhttps://www.fao.org/agriculture/crops/thematic-sitemap/theme/spi/scpi-home/managing-ecosystems/integrated-plant-nutrient-management/ipnm-what/en/", "", "RESOURCE", null));
        result.add(new Guide("Singkamas (Jicama): Root Crop Basics", "Crops", "Plan a manageable root crop from planting to harvest.", "INTRODUCTION:\nPlan a manageable root crop from planting to harvest.\n\n1. Choose planting material suited to your area and prepare loose, well-drained soil suitable for root development.\n\n2. Observe emergence, weed growth and soil moisture. Avoid waterlogging and protect the planting area from avoidable damage.\n\n3. Inspect crop health regularly. Ask local agriculture advisers about any plant disease symptoms and recommended management practices.\n\n4. Harvest carefully to avoid cutting or bruising roots. Record weights and sort produce for safe handling and sale.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *What is integrated plant nutrient management?*\nhttps://www.fao.org/agriculture/crops/thematic-sitemap/theme/spi/scpi-home/managing-ecosystems/integrated-plant-nutrient-management/ipnm-what/en/", "", "RESOURCE", null));
        result.add(new Guide("Chicken: Care and Farm Biosecurity", "Livestock", "Everyday welfare, hygiene and responsible poultry flock management.", "INTRODUCTION:\nEveryday welfare, hygiene and responsible poultry flock management.\n\n1. Plan suitable shelter, space, ventilation and reliable clean water for your poultry flock. Animal needs vary with age and local climate.\n\n2. Use appropriate feeds and maintain clean feeding and watering equipment. Keep records of feed use and routine care.\n\n3. Limit unnecessary visitors to the poultry area and clean tools used around housing. Separate animals showing possible illness where safe and seek veterinary guidance; do not diagnose solely from online descriptions.\n\n4. Keep visitor, cleaning and treatment records. Follow local animal health and movement requirements to reduce disease risks.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nWorld Organisation for Animal Health. (2024). *Biosecurity procedures in poultry production.*\nhttps://www.woah.org/fileadmin/Home/eng/Health_standards/tahc/2024/en_chapitre_biosecu_poul_production.htm", "", "RESOURCE", null));
        result.add(new Guide("Pig: Care and Farm Biosecurity", "Livestock", "Everyday welfare, hygiene and responsible swine herd management.", "INTRODUCTION:\nEveryday welfare, hygiene and responsible swine herd management.\n\n1. Plan suitable shelter, space, ventilation and reliable clean water for your swine herd. Animal needs vary with age and local climate.\n\n2. Use appropriate feeds and maintain clean feeding and watering equipment. Keep records of feed use and routine care.\n\n3. Watch for changes in feeding, activity and signs of illness; contact a veterinarian promptly. Separate animals showing possible illness where safe and seek veterinary guidance; do not diagnose solely from online descriptions.\n\n4. Keep visitor, cleaning and treatment records. Follow local animal health and movement requirements to reduce disease risks.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nWorld Organisation for Animal Health. (n.d.). *Animal welfare and pig production systems.*\nhttps://www.woah.org/fileadmin/Home/eng/Health_standards/tahc/current/en_chapitre_aw_pigs.htm", "", "RESOURCE", null));
        result.add(new Guide("Cow: Care and Farm Biosecurity", "Livestock", "Everyday welfare, hygiene and responsible cattle management.", "INTRODUCTION:\nEveryday welfare, hygiene and responsible cattle management.\n\n1. Plan suitable shelter, space, ventilation and reliable clean water for your cattle. Animal needs vary with age and local climate.\n\n2. Use appropriate feeds and maintain clean feeding and watering equipment. Keep records of feed use and routine care.\n\n3. Provide space for safe handling and avoid crowding around larger animals. Separate animals showing possible illness where safe and seek veterinary guidance; do not diagnose solely from online descriptions.\n\n4. Keep visitor, cleaning and treatment records. Follow local animal health and movement requirements to reduce disease risks.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nDepartment of Agriculture–Bureau of Animal Industry. (n.d.). *Animal Management Information System (AMIS).*\nhttps://amis.bai.gov.ph/", "", "RESOURCE", null));
        result.add(new Guide("Carabao: Care and Farm Biosecurity", "Livestock", "Everyday welfare, hygiene and responsible water buffalo management.", "INTRODUCTION:\nEveryday welfare, hygiene and responsible water buffalo management.\n\n1. Plan suitable shelter, space, ventilation and reliable clean water for your water buffalo. Animal needs vary with age and local climate.\n\n2. Use appropriate feeds and maintain clean feeding and watering equipment. Keep records of feed use and routine care.\n\n3. Plan shelter, safe restraint, adequate water and veterinary support for working or farm animals. Separate animals showing possible illness where safe and seek veterinary guidance; do not diagnose solely from online descriptions.\n\n4. Keep visitor, cleaning and treatment records. Follow local animal health and movement requirements to reduce disease risks.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nDepartment of Agriculture–Bureau of Animal Industry. (n.d.). *Animal Management Information System (AMIS).*\nhttps://amis.bai.gov.ph/", "", "RESOURCE", null));
        result.add(new Guide("Goat: Care and Farm Biosecurity", "Livestock", "Everyday welfare, hygiene and responsible small ruminants management.", "INTRODUCTION:\nEveryday welfare, hygiene and responsible small ruminants management.\n\n1. Plan suitable shelter, space, ventilation and reliable clean water for your small ruminants. Animal needs vary with age and local climate.\n\n2. Use appropriate feeds and maintain clean feeding and watering equipment. Keep records of feed use and routine care.\n\n3. Inspect hooves, shelter conditions and pasture hazards as part of regular care. Separate animals showing possible illness where safe and seek veterinary guidance; do not diagnose solely from online descriptions.\n\n4. Keep visitor, cleaning and treatment records. Follow local animal health and movement requirements to reduce disease risks.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nDepartment of Agriculture–Bureau of Animal Industry. (n.d.). *Animal Management Information System (AMIS).*\nhttps://amis.bai.gov.ph/", "", "RESOURCE", null));
        result.add(new Guide("Farm Records: Know Your Costs", "Farm Finance", "Use simple records to understand money entering and leaving the farm.", "INTRODUCTION:\nUse simple records to understand money entering and leaving the farm.\n\n1. Write down the date and purpose of every farm transaction. Record amounts for seeds, feeds, fertilizer, utilities, hired labor and transport.\n\n2. Separate household spending from farm spending so you can better assess the farm business.\n\n3. Record harvest quantities, sales receipts, unsold produce and spoilage. Keep receipts or photo copies where practical.\n\n4. Review the records every week and after each production cycle. Use actual local prices instead of assumed profit.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nDepartment of Agriculture–Special Area for Agricultural Development. (2026). *SAAD Sorsogon FAs enhance skills in managing records.*\nhttps://saad.da.gov.ph/saad-sorsogon-fas-enhance-skills-in-managing-records/", "", "RESOURCE", null));
        result.add(new Guide("Income, Expenses and Profit", "Farm Finance", "Understand sales, costs and what remains after farm expenses.", "INTRODUCTION:\nUnderstand sales, costs and what remains after farm expenses.\n\n1. Revenue is the money received from sales; it is not the same as profit. Keep a clear record of quantities and selling prices.\n\n2. Total costs may include planting materials, labor, packaging, fuel, feed, water, equipment wear and transport.\n\n3. A simple example: if sales are PHP 5,000 and total recorded costs are PHP 3,800, the remaining amount is PHP 1,200 before any omitted costs or obligations.\n\n4. Plan a reserve for unexpected expenses and compare actual results against the original budget before expanding.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nDepartment of Agriculture. (2024). *Enterprise assessment and business plan preparation: Facilitator’s guide.*\nhttps://ncisrd.da.gov.ph/wp-content/uploads/2024/09/AFF-Faci-Guide-2024.pdf", "", "RESOURCE", null));
        result.add(new Guide("Healthy Soil and Composting", "Sustainability", "Protect soil structure and manage organic materials responsibly.", "INTRODUCTION:\nProtect soil structure and manage organic materials responsibly.\n\n1. Soil supports roots and helps retain nutrients and moisture. Minimize avoidable soil erosion and compaction.\n\n2. Use compost made and handled properly; do not apply untreated or unsafe wastes directly to edible crops.\n\n3. Monitor soil conditions and match amendments to actual crop needs. Soil tests and local extension advice can guide nutrient management.\n\n4. Use crop residues and appropriate ground cover where suitable to reduce bare-soil exposure.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *What is conservation agriculture?*\nhttps://www.fao.org/conservation-agriculture/overview/principles-of-ca", "", "RESOURCE", null));
        result.add(new Guide("Save Water on Your Farm", "Sustainability", "Improve water use while protecting crop and animal health.", "INTRODUCTION:\nImprove water use while protecting crop and animal health.\n\n1. Observe soil moisture and crop condition before irrigation, rather than watering simply because the clock says so.\n\n2. Check hoses, pumps and pipes for leaks and use watering methods suitable for your production system.\n\n3. Provide safe drainage and avoid persistent ponding in sensitive crops; water conservation does not mean depriving plants or livestock of water.\n\n4. Record rainfall, irrigation and observations to learn which practices work in your location.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *Land, soil, water and the Sustainable Development Goals.*\nhttps://www.fao.org/land-water/overview/Land-soil-water-and-the-sustainable-development-goals/", "", "RESOURCE", null));
        result.add(new Guide("Crop Rotation and Diversification", "Sustainability", "Build resilience by planning varied production.", "INTRODUCTION:\nBuild resilience by planning varied production.\n\n1. Where appropriate, rotate crops with different characteristics rather than planting exactly the same crop in the same place repeatedly.\n\n2. Choose rotations based on locally common pests, soil conditions, available water and market needs.\n\n3. Consider ground cover and reduced unnecessary soil disturbance to protect soil structure.\n\n4. Keep a simple map of which crop occupied each plot in every season to improve planning.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *What is conservation agriculture?*\nhttps://www.fao.org/conservation-agriculture/overview/principles-of-ca", "", "RESOURCE", null));
        result.add(new Guide("Integrated Pest Management (IPM)", "Sustainability", "Prevent and monitor pests before choosing control methods.", "INTRODUCTION:\nPrevent and monitor pests before choosing control methods.\n\n1. Inspect crops regularly and correctly identify the organism and damage before acting.\n\n2. Use prevention, suitable crop management, physical methods and biological approaches where appropriate.\n\n3. Consider chemical control only when justified and permitted; follow registered product labels, restrictions and safety measures.\n\n4. Keep a record of pest observations, actions, and the outcome. Consult a trained local adviser for unfamiliar outbreaks.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *Integrated pest management (IPM).*\nhttps://www.fao.org/pest-and-pesticide-management/ipm/integrated-pest-management/en", "", "RESOURCE", null));
        result.add(new Guide("SAFETY FIRST: Machinery and Equipment", "Safety", "Prevent serious injuries when using tools, tractors and harvesters.", "INTRODUCTION:\nPrevent serious injuries when using tools, tractors and harvesters.\n\n1. Only trained and authorized operators should use powered equipment. Read and follow the operator manual and site safety instructions.\n\n2. Inspect safety guards, brakes, tires and surroundings before operating. Keep children, bystanders and animals clear of the work zone.\n\n3. Turn machinery off and isolate the power source before clearing blockages or maintenance. Never reach into moving machinery.\n\n4. Prepare a local emergency plan, first-aid supplies and suitable communication for accidents.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nInternational Labour Organization. (2011). *Safety and health in agriculture: Code of practice.*\nhttps://www.ilo.org/global/topics/safety-and-health-at-work/normative-instruments/code-of-practice/WCMS_161135/lang--en/index.htm", "", "RESOURCE", null));
        result.add(new Guide("Pesticide Safety: Read Before Spraying", "Safety", "Protect people, animals, water and crops from pesticide misuse.", "INTRODUCTION:\nProtect people, animals, water and crops from pesticide misuse.\n\n1. Use only appropriate FPA-registered products for the specific intended use. Read the complete current label before any handling or application.\n\n2. Wear the protective equipment specified by the label; avoid spray drift and keep people and animals out of the treated area as directed.\n\n3. Follow label instructions on dilution, permitted uses, re-entry interval and pre-harvest interval. Never invent or exceed rates.\n\n4. Store pesticides in original labelled containers away from food, feed and children. Follow authorized disposal guidance; seek medical help for suspected exposure.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nDepartment of Agriculture–Fertilizer and Pesticide Authority. (2022). *FPA discusses the proper safe use, handling, and storage of pesticides to 16 corn farmers.*\nhttps://fpa.da.gov.ph/fpa-discusses-the-proper-safe-use-handling-and-storage-of-pesticides-to-16-corn-farmers/", "", "RESOURCE", null));
        result.add(new Guide("Crop Protection and Food Safety", "Safety", "Reduce contamination while monitoring plant health.", "INTRODUCTION:\nReduce contamination while monitoring plant health.\n\n1. Scout for pests, damage and unusual leaf symptoms regularly. Record where and when problems appear.\n\n2. Use integrated pest management principles, focusing on correct identification and prevention first.\n\n3. Keep spraying equipment, harvest containers and storage areas appropriately separated and cleaned.\n\n4. Observe product labels and withholding periods before harvesting. Ask qualified authorities about uncertain pesticide or disease issues.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nFood and Agriculture Organization of the United Nations. (n.d.). *Integrated pest management (IPM).*\nhttps://www.fao.org/pest-and-pesticide-management/ipm/integrated-pest-management/en", "", "RESOURCE", null));
        result.add(new Guide("Livestock Biosecurity and Animal Health", "Safety", "Reduce the introduction and spread of disease on the farm.", "INTRODUCTION:\nReduce the introduction and spread of disease on the farm.\n\n1. Limit unnecessary visitors to animal housing and use suitable footwear, clothing and cleaning procedures.\n\n2. Keep feed and water safe, and clean pens and tools with appropriate methods. Restrict contact with sick or unknown animals.\n\n3. Monitor behavior, appetite and other signs of possible illness and ask a veterinarian promptly when animals seem unwell.\n\n4. Follow local reporting, vaccination and animal movement requirements. Do not self-prescribe antibiotics.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nDepartment of Agriculture–Bureau of Animal Industry. (n.d.). *Animal Management Information System (AMIS).*\nhttps://amis.bai.gov.ph/", "", "RESOURCE", null));
        result.add(new Guide("Safe Harvest and Handling", "Safety", "Prevent injuries and damage during harvest and transport.", "INTRODUCTION:\nPrevent injuries and damage during harvest and transport.\n\n1. Plan safe lifting, carrying and working distances around moving vehicles or machinery.\n\n2. Use clean, suitable harvest containers and tools, following safe procedures for the crop.\n\n3. Keep harvested products protected from chemical contamination and dirty equipment.\n\n4. Record injuries, near misses and damaged produce; use the information to improve your work area.\n\nPRACTICAL REMINDER:\nObserve the actual conditions of your farm and consult an accredited local agriculture or veterinary professional for site-specific advice.\n\nAPA 7 REFERENCE:\nInternational Labour Organization. (2011). *Safety and health in agriculture: Code of practice.*\nhttps://www.ilo.org/global/topics/safety-and-health-at-work/normative-instruments/code-of-practice/WCMS_161135/lang--en/index.htm", "", "RESOURCE", null));
        result.add(new Guide("Agroforestry and Farm Biodiversity", "Sustainability", "Plan tree and crop combinations that protect natural resources.", "INTRODUCTION:\nThoughtful tree and crop combinations may diversify production while protecting soil.\n\n1. Review whether trees, shrubs and crops can share your land without overcrowding or too much shading.\n\n2. Plan crop and tree choices around available sunlight, root space, water and farm goals.\n\n3. Keep soil covered where appropriate, preserve useful habitats and avoid clearing vegetation unnecessarily.\n\n4. Ask local extension specialists about suitable agroforestry combinations and observe the effects on your site.\n\nPRACTICAL REMINDER:\nNot every tree is appropriate for every crop. Check local conditions before changing your farm layout.\n\nAPA 7 REFERENCE:\nFarmonaut Research & Content Team. (2025, August 5). Sustainable agriculture Philippines: Top 7 practices 2025. Farmonaut. Updated October 15, 2026.\nhttps://farmonaut.com/asia/sustainable-agriculture-philippines-top-7-practices-2025", "", "RESOURCE", null));
        return result;
    }

    private void openResearchGuide(Guide guide) {
        JDialog dialog = new JDialog(host.frame, "SAKA-SMART | Research-based Farming Guide",
            Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 11));
        root.setBackground(new Color(235, 250, 237));
        root.setBorder(new EmptyBorder(15, 16, 12, 16));
        JPanel heading = readerCard(new BorderLayout(0, 9), new Color(34, 164, 84), DEEP, 25);
        heading.setBorder(new EmptyBorder(16, 20, 16, 20));
        heading.add(readerText(guide.category().toUpperCase(Locale.ROOT) + "  •  RESEARCH-BASED FARMING",
            13, true, new Color(218, 255, 230)), BorderLayout.NORTH);
        heading.add(readerText(guide.title(), 25, true, Color.WHITE), BorderLayout.CENTER);
        heading.add(readerText(guide.description(), 16, false, Color.WHITE), BorderLayout.SOUTH);
        root.add(heading, BorderLayout.NORTH);
        ReaderColumn content = new ReaderColumn();
        content.setBorder(new EmptyBorder(3, 4, 8, 10));
        JPanel photoRow = readerCard(new BorderLayout(13, 0), WHITE, MINT, 23);
        photoRow.setBorder(new EmptyBorder(11, 12, 11, 12));
        PhotoArt picture = new PhotoArt(photosFor(guide), guide.title());
        picture.setPreferredSize(new Dimension(210, 172));
        photoRow.add(picture, BorderLayout.WEST);
        JPanel intro = clear(new BorderLayout(0, 9));
        intro.add(readerHeader("WHAT YOU WILL LEARN", 0), BorderLayout.NORTH);
        intro.add(readerText(guide.description() + "\n\nUse these research-backed ideas to plan your next farm observation or activity.",
            16, false, DARK), BorderLayout.CENTER);
        photoRow.add(intro, BorderLayout.CENTER);
        addReaderSection(content, photoRow, 192);
        content.add(Box.createVerticalStrut(13));
        String[] blocks = guide.content().split("\\n\\n");
        int counter = 0;
        for (String block : blocks) {
            String text = block.trim();
            if (text.isEmpty() || text.startsWith("INTRODUCTION:")) continue;
            if (text.matches("(?s)^\\d+\\..*")) {
                counter++;
                JPanel section = readerCard(new BorderLayout(12, 0),
                    counter % 2 == 0 ? new Color(236, 253, 237) : WHITE,
                    counter % 2 == 0 ? new Color(210, 246, 220) : new Color(227, 248, 230), 24);
                section.setBorder(new EmptyBorder(15, 17, 15, 17));
                JLabel badge = readerChip("" + counter, true);
                badge.setHorizontalAlignment(SwingConstants.CENTER);
                badge.setPreferredSize(new Dimension(48, 42));
                section.add(badge, BorderLayout.WEST);
                String clean = text.replaceFirst("^\\d+\\.\\s*", "");
                section.add(readerText(clean, 16, false, DARK), BorderLayout.CENTER);
                int h = 130 + Math.max(0, (clean.length() - 150) / 90) * 22;
                addReaderSection(content, section, h);
                content.add(Box.createVerticalStrut(10));
            } else {
                JPanel note = readerCard(new BorderLayout(0, 8),
                    new Color(244, 255, 244), new Color(216, 246, 223), 22);
                note.setBorder(new EmptyBorder(13, 16, 13, 16));
                boolean reference = text.startsWith("APA 7 REFERENCE:");
                note.add(readerHeader(reference ? "APA 7 REFERENCE & SOURCE" : "PRACTICAL REMINDER",
                    reference ? 2 : 0), BorderLayout.NORTH);
                note.add(readerText(text.replaceFirst("^[^:]+:\\n", ""),
                    reference ? 14 : 15, false, DARK), BorderLayout.CENTER);
                addReaderSection(content, note, reference ? 135 : 105);
                content.add(Box.createVerticalStrut(10));
            }
        }
        JScrollPane sc = new JScrollPane(content);
        sc.setOpaque(false);
        sc.getViewport().setOpaque(false);
        sc.setBorder(null);
        sc.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll(sc);
        root.add(sc, BorderLayout.CENTER);
        JPanel actions = clear(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton source = button("Open Original Source", true);
        String raw = guide.content();
        int pos = raw.lastIndexOf("https://");
        String url = pos >= 0 ? raw.substring(pos).trim() : "";
        source.setEnabled(!url.isBlank());
        source.addActionListener(e -> {
            try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new java.net.URI(url)); }
            catch (Exception error) { JOptionPane.showMessageDialog(dialog, "Source link: " + url); }
        });
        JButton done = button("Close Guide", false);
        done.addActionListener(e -> dialog.dispose());
        actions.add(source);
        actions.add(done);
        root.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(1050, screen.width - 50), Math.min(820, screen.height - 50));
        dialog.setLocationRelativeTo(host.frame);
        dialog.setVisible(true);
    }

    // ======================================================
    // SIX BEGINNER FARMING LESSONS
    // ======================================================

    private static List<Guide> starterGuides() {

        return List.of(

            guide(
                "1",
                "Your First Farm: Choose a Goal",
                "Getting Started",
                "Learn how to plan your first farming project.",

                "Welcome to your farming journey!\n\n" +

                "1. Decide why you want to start farming.\n" +
                "2. Identify the crops or livestock you want to raise.\n" +
                "3. Determine your available space and resources.\n" +
                "4. Estimate your initial budget.\n" +
                "5. Consult your local agricultural office.\n\n" +

                "Example:\n" +
                "A beginner may choose to grow vegetables in a small backyard " +
                "before expanding to a larger farm.\n\n" +

                "CHECKPOINT:\n" +
                "You should understand your farm's purpose and available resources."
            ),

            guide(
                "2",
                "Check Your Space, Sunlight & Water",
                "Getting Started",
                "Prepare your growing area before planting.",

                "The farm location affects plant growth and management.\n\n" +

                "1. Measure the available farming area.\n" +
                "2. Identify areas that receive sunlight.\n" +
                "3. Check the soil and drainage conditions.\n" +
                "4. Identify a reliable water source.\n" +
                "5. Consider flooding and dry-season risks.\n\n" +

                "Example:\n" +
                "A vegetable garden should be located where the chosen crops " +
                "receive suitable sunlight and have access to water.\n\n" +

                "CHECKPOINT:\n" +
                "You should know whether your planned farming location is suitable."
            ),

            guide(
                "3",
                "Choose Suitable Crops or Livestock",
                "Getting Started",
                "Select farm products based on your resources.",

                "Choosing suitable crops or animals is important.\n\n" +

                "1. Consider the local climate.\n" +
                "2. Research suitable crop varieties.\n" +
                "3. Evaluate livestock housing and care requirements.\n" +
                "4. Consider available capital and labor.\n" +
                "5. Identify potential customers or buyers.\n\n" +

                "Example:\n" +
                "Farmers with limited land may begin with container gardening " +
                "rather than large-scale livestock production.\n\n" +

                "CHECKPOINT:\n" +
                "Your chosen farming activity should fit your available resources."
            ),

            guide(
                "4",
                "Prepare Soil, Tools & a Budget",
                "Getting Started",
                "Organize your materials and farming expenses.",

                "Proper preparation helps reduce avoidable problems.\n\n" +

                "1. Check soil condition and drainage.\n" +
                "2. Prepare the required tools.\n" +
                "3. List seeds, supplies, and equipment.\n" +
                "4. Estimate operating expenses.\n" +
                "5. Create an emergency allowance.\n\n" +

                "Example:\n" +
                "A farmer can record the estimated cost of seeds, watering tools, " +
                "soil preparation, and transportation before planting.\n\n" +

                "CHECKPOINT:\n" +
                "You should have a workable plan and estimated budget."
            ),

            guide(
                "5",
                "Manage Daily Farming Tasks",
                "Getting Started",
                "Develop daily routines and monitor progress.",

                "Daily farming activities require consistent attention.\n\n" +

                "1. Monitor soil moisture and crop conditions.\n" +
                "2. Observe plants for unusual changes.\n" +
                "3. Check livestock feed, water, and housing.\n" +
                "4. Record completed activities and expenses.\n" +
                "5. Address problems promptly with professional advice.\n\n" +

                "Example:\n" +
                "A farmer may record watering schedules, fertilizer applications, " +
                "and observations in a daily farm log.\n\n" +

                "CHECKPOINT:\n" +
                "You should be able to maintain basic farm records and routines."
            ),

            guide(
                "6",
                "Plan Harvest, Sales & Next Steps",
                "Getting Started",
                "Prepare your products for harvest and marketing.",

                "Harvesting and marketing are important production stages.\n\n" +

                "1. Identify suitable harvesting methods.\n" +
                "2. Prepare containers and storage areas.\n" +
                "3. Record harvest quantities.\n" +
                "4. Calculate production costs and sales.\n" +
                "5. Review the results before starting another cycle.\n\n" +

                "Example:\n" +
                "A vegetable farmer can record harvest weight, selling price, " +
                "transportation cost, and total sales.\n\n" +

                "CHECKPOINT:\n" +
                "You should understand basic harvest preparation and financial tracking."
            )
        );
    }
}
