package com.sakasmart;

import static com.sakasmart.Main.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * SAKA-SMART Farm Profile page.
 *
 * Uses only the currently signed-in farm's saved data.
 * Does not change data or location until an authorized user chooses an action.
 */
final class FarmProfilePage extends PageBase {

    private static final Color DEEP_GREEN = new Color(12, 82, 49);
    private static final Color SUBTEXT = new Color(88, 115, 94);
    private static final Color LIGHT_MINT = new Color(236, 247, 237);
    private static final Color TILE_BORDER = new Color(212, 232, 215);

    FarmProfilePage(Main host) {
        super(host);
    }

    JComponent build() {
        JPanel page = stack();
        List<Map<String, String>> farms = rows("farms");
        Map<String, String> farm = farms.isEmpty() ? Map.of() : farms.get(0);

        boolean canManage = admin() || "FARM_OWNER".equals(role());
        boolean hasFarm = !farm.isEmpty();
        boolean hasMap = hasFarm && FarmLocationMap.hasCoordinates(farm);

        // 1. Compact management toolbar; never place the buttons inside
        // a panel that is shorter than their preferred height.
        SoftCard administration = new SoftCard(new BorderLayout(0, 7));
        JPanel actionHeading = transparent(new BorderLayout(12, 0));
        actionHeading.add(twoLineHeading(
            "Farm Administration",
            "Manage your farm profile and saved map location"
        ), BorderLayout.CENTER);
        actionHeading.add(badgeContainer(
            hasMap ? "MAP PIN SAVED" : "MAP PIN NOT SET",
            hasMap
        ), BorderLayout.EAST);
        administration.add(actionHeading, BorderLayout.NORTH);

        JPanel buttons = transparent(new FlowLayout(FlowLayout.LEFT, 9, 0));
        // Reserve the whole glossy-button height; the former CENTER slot
        // could shrink below the button height and clip the bottom.
        buttons.setPreferredSize(new Dimension(1, 49));
        if (canManage && hasFarm) {
            JButton edit = Main.dashboardButton("Edit Farm Profile");
            edit.setPreferredSize(new Dimension(162, 43));
            edit.addActionListener(e -> editFarm());
            buttons.add(edit);

            JButton setPin = Main.dashboardButton(
                hasMap ? "Update Map Pin" : "Set Map Pin"
            );
            setPin.setPreferredSize(new Dimension(164, 43));
            setPin.addActionListener(e -> FarmLocationMap.chooseAndSave(host));
            buttons.add(setPin);
        } else if (!hasFarm) {
            buttons.add(note("No farm profile is available for this account."));
        } else {
            buttons.add(note("Only the farm owner can edit farm details."));
        }
        administration.add(buttons, BorderLayout.SOUTH);
        addBlock(page, administration, 151);

        // 2. Readable details instead of a one-row JTable.
        SoftCard summary = new SoftCard(new BorderLayout(0, 12));
        summary.add(twoLineHeading(
            "Farm Summary",
            "Information saved for your farm"
        ), BorderLayout.NORTH);

        if (!hasFarm) {
            JPanel empty = transparent(new GridBagLayout());
            empty.add(note("Your farm information will appear here once a profile is available."));
            summary.add(empty, BorderLayout.CENTER);
        } else {
            JPanel facts = transparent(new GridLayout(2, 3, 10, 10));
            facts.add(factTile("FARM NAME", display(farm, "name")));
            facts.add(factTile("LOCATION", display(farm, "location")));
            facts.add(factTile("FARM OWNER", display(farm, "owner")));

            String area = display(farm, "area_ha");
            if (!"Not provided".equals(area)) {
                area += " hectares";
            }
            facts.add(factTile("LAND AREA", area));
            facts.add(factTile("MAIN CROPS", display(farm, "crops")));
            facts.add(factTile("LIVESTOCK", display(farm, "livestock")));
            summary.add(facts, BorderLayout.CENTER);
        }
        addBlock(page, summary, 244);

        // 3. Farm map with its own footer and action.
        SoftCard mapCard = new SoftCard(new BorderLayout(0, 10));
        JPanel mapHeading = transparent(new BorderLayout(12, 0));
        mapHeading.add(twoLineHeading(
            "Farm Location Map",
            hasMap
                ? "Saved farm pin - this is not live device tracking"
                : "Choose a location to display your farm on the map"
        ), BorderLayout.CENTER);
        if (hasMap) {
            mapHeading.add(badgeContainer("LOCATION SAVED", true), BorderLayout.EAST);
        }
        mapCard.add(mapHeading, BorderLayout.NORTH);

        JPanel mapArea = transparent(new BorderLayout());
        mapArea.setBorder(
            javax.swing.BorderFactory.createLineBorder(TILE_BORDER, 1, true)
        );

        if (hasMap) {
            FarmLocationMap savedMap = new FarmLocationMap(
                FarmLocationMap.lat(farm),
                FarmLocationMap.lon(farm),
                false,
                null
            );
            savedMap.setPreferredSize(new Dimension(860, 240));
            savedMap.setToolTipText(
                "Saved farm map position. Use Open Full Map for more detail."
            );
            mapArea.add(savedMap, BorderLayout.CENTER);
        } else {
            JPanel emptyMap = new JPanel(new GridBagLayout());
            emptyMap.setBackground(LIGHT_MINT);
            JPanel message = transparent(new GridLayout(2, 1, 0, 6));
            JLabel emptyTitle = text(
                "No farm location pin saved yet", 15, true, DEEP_GREEN
            );
            emptyTitle.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel emptyBody = text(
                canManage ? "Use Set Map Pin above to choose the farm location."
                          : "Ask the farm owner to set the location.",
                12, false, SUBTEXT
            );
            emptyBody.setHorizontalAlignment(SwingConstants.CENTER);
            message.add(emptyTitle);
            message.add(emptyBody);
            emptyMap.add(message);
            mapArea.add(emptyMap, BorderLayout.CENTER);
        }
        mapCard.add(mapArea, BorderLayout.CENTER);

        JPanel mapFooter = transparent(new BorderLayout(12, 0));
        mapFooter.add(
            note("Map data: OpenStreetMap contributors | Location is saved, not tracked."),
            BorderLayout.CENTER
        );
        if (hasMap) {
            JButton fullMap = Main.dashboardButton("Open Full Map");
            fullMap.addActionListener(e -> FarmLocationMap.openBrowser(
                frame,
                FarmLocationMap.lat(farm),
                FarmLocationMap.lon(farm)
            ));
            mapFooter.add(fullMap, BorderLayout.EAST);
        }
        mapCard.add(mapFooter, BorderLayout.SOUTH);
        addBlock(page, mapCard, 354);

        return page;
    }

    @Override
    protected void editFarm() {
        if (!(admin() || "FARM_OWNER".equals(role()))) {
            info("Only farm owners and system administrators may edit farm information.");
            return;
        }
        Map<String, String> farm =
            rows("farms").stream().findFirst().orElse(null);
        if (farm != null) {
            form("farms", farm, Map.of(), java.util.Set.of());
        } else {
            info("No farm profile is available for editing.");
        }
    }

    private static JComponent twoLineHeading(String title, String subtitle) {
        JPanel head = transparent(new GridLayout(2, 1, 0, 3));
        head.add(text(title, 18, true, DEEP_GREEN));
        head.add(text(subtitle, 12, false, SUBTEXT));
        return head;
    }

    /** Keep the pill at its natural height, centered beside the heading. */
    private static JComponent badgeContainer(String value, boolean positive) {
        JPanel wrapper = transparent(new GridBagLayout());
        wrapper.add(new RoundedStatusBadge(value, positive));
        return wrapper;
    }

    /** Draw a capsule with genuinely rounded ends, not an opaque rectangle. */
    private static final class RoundedStatusBadge extends JLabel {
        private final Color fill;
        private final Color outline;

        RoundedStatusBadge(String text, boolean positive) {
            super(text, SwingConstants.CENTER);
            setFont(new Font("Segoe UI", Font.BOLD, 10));
            setForeground(positive ? new Color(23, 110, 61)
                                   : new Color(125, 102, 40));
            fill = positive ? new Color(220, 244, 229)
                            : new Color(252, 240, 210);
            outline = positive ? new Color(154, 209, 170)
                               : new Color(226, 196, 132);
            setBorder(new EmptyBorder(9, 15, 9, 15));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics source) {
            Graphics2D g = (Graphics2D) source.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            int w = getWidth();
            if (h > 2 && w > 2) {
                int arc = h - 2;
                g.setColor(fill);
                g.fillRoundRect(1, 1, w - 3, h - 3, arc, arc);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            }
            g.dispose();
            super.paintComponent(source);
        }
    }

    private static JComponent factTile(String title, String value) {
        JPanel tile = new JPanel(new BorderLayout(0, 6));
        tile.setOpaque(true);
        tile.setBackground(LIGHT_MINT);
        tile.setBorder(new EmptyBorder(11, 13, 10, 12));

        JLabel heading = text(title, 10, true, SUBTEXT);
        JLabel detail = text(value, 14, true, DEEP_GREEN);
        detail.setToolTipText(value);
        tile.setToolTipText(title + ": " + value);

        tile.add(heading, BorderLayout.NORTH);
        tile.add(detail, BorderLayout.CENTER);
        return tile;
    }

    private static String display(Map<String, String> farm, String key) {
        String value = farm.get(key);
        return value == null || value.isBlank()
            ? "Not provided"
            : value.trim();
    }

    private static JLabel note(String value) {
        return text(value, 11, false, SUBTEXT);
    }

    private static JLabel text(
        String value, int size, boolean bold, Color color
    ) {
        JLabel label = new JLabel(value);
        label.setFont(new Font(
            "Segoe UI", bold ? Font.BOLD : Font.PLAIN, size
        ));
        label.setForeground(color);
        return label;
    }

    private static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Soft single-outline rounded card (not the double heavy outline).
     * All content uses Swing components, so existing buttons still work.
     */
    private static final class SoftCard extends JPanel {
        SoftCard(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(new EmptyBorder(16, 19, 16, 19));
        }

        @Override
        protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );

            int width = getWidth();
            int height = getHeight();

            if (width > 8 && height > 8) {
                g.setColor(new Color(27, 79, 43, 18));
                g.fill(new RoundRectangle2D.Float(
                    3, 5, width - 7, height - 7, 23, 23
                ));

                g.setPaint(new GradientPaint(
                    0, 1, new Color(255, 255, 250),
                    0, height, new Color(247, 253, 247)
                ));
                g.fill(new RoundRectangle2D.Float(
                    1, 1, width - 5, height - 6, 23, 23
                ));

                g.setStroke(new BasicStroke(1.1f));
                g.setColor(new Color(188, 216, 192));
                g.draw(new RoundRectangle2D.Float(
                    1, 1, width - 5, height - 6, 23, 23
                ));
            }
            g.dispose();
            super.paintComponent(original);
        }
    }
}
