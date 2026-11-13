
package com.sakasmart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;

/**
 * Compact farm overview.
 * Displays saved farm data, not hard-coded demo details.
 */
final class FarmOverviewWidget extends JPanel {

    private static final Color GREEN =
        new Color(12, 100, 60);

    private static final Color TEXT =
        new Color(18, 70, 45);

    private static final Color MUTED =
        new Color(90, 119, 100);

    private static final Color TILE =
        new Color(236, 247, 239);

    FarmOverviewWidget(Main host, Map<String, String> farm) {
        super(new BorderLayout(0, 7));
        setOpaque(false);

        if (farm == null || farm.isEmpty()) {
            showEmptyProfile(host);
            return;
        }

        String name = value(farm, "name");
        String location = value(farm, "location");

        boolean hasMap =
            FarmLocationMap.hasCoordinates(farm);

        boolean canEdit =
            host.admin() || "FARM_OWNER".equals(host.role());

        // Farm name and location.
        JPanel heading = transparent(
            new BorderLayout(8, 0)
        );

        JPanel identity = transparent(
            new GridLayout(2, 1, 0, 1)
        );

        JLabel nameLabel = new JLabel(
            name.isBlank()
                ? "Farm profile incomplete"
                : shorten(name, 29)
        );

        nameLabel.setFont(
            new Font("Segoe UI", Font.BOLD, 15)
        );

        nameLabel.setForeground(TEXT);

        nameLabel.setToolTipText(
            name.isBlank()
                ? "Add the farm's name in Farm Profile"
                : name
        );

        identity.add(nameLabel);

        String address = location.isBlank()
            ? "Location not provided"
            : "Location: " + location;

        JLabel addressLabel = new JLabel(
            shorten(address, 43)
        );

        addressLabel.setFont(
            new Font("Segoe UI", Font.PLAIN, 11)
        );

        addressLabel.setForeground(MUTED);
        addressLabel.setToolTipText(address);

        identity.add(addressLabel);
        heading.add(identity, BorderLayout.CENTER);

        // Open the saved map location.
        if (hasMap) {
            JButton openMap = new JButton("Open Map");

            openMap.setFont(
                new Font("Segoe UI", Font.BOLD, 10)
            );

            openMap.setForeground(GREEN);

            openMap.setBorder(
                new EmptyBorder(3, 5, 3, 5)
            );

            openMap.setContentAreaFilled(false);
            openMap.setFocusPainted(false);

            openMap.setCursor(
                Cursor.getPredefinedCursor(
                    Cursor.HAND_CURSOR
                )
            );

            openMap.setToolTipText(
                "View saved farm coordinates in OpenStreetMap"
            );

            openMap.addActionListener(e ->
                FarmLocationMap.openBrowser(
                    host.frame,
                    FarmLocationMap.lat(farm),
                    FarmLocationMap.lon(farm)
                )
            );

            heading.add(openMap, BorderLayout.EAST);
        }

        add(heading, BorderLayout.NORTH);

        // Farm map preview.
        JPanel center = transparent(
            new BorderLayout(0, 6)
        );

        JComponent mapView;

        if (hasMap) {
            FarmLocationMap preview =
                new FarmLocationMap(
                    FarmLocationMap.lat(farm),
                    FarmLocationMap.lon(farm),
                    false,
                    null
                );

            preview.setPreferredSize(
                new Dimension(260, 114)
            );

            preview.setToolTipText(
                "Saved farm map pin (not live GPS)"
            );

            mapView = preview;

        } else {
            JPanel placeholder =
                new JPanel(new GridBagLayout());

            placeholder.setBackground(
                new Color(239, 247, 239)
            );

            JLabel message = new JLabel(
                "Map pin not set - select your farm location"
            );

            message.setFont(
                new Font("Segoe UI", Font.PLAIN, 11)
            );

            message.setForeground(MUTED);
            placeholder.add(message);

            mapView = placeholder;
        }

        JPanel mapHolder = transparent(
            new BorderLayout()
        );

        mapHolder.setBorder(
            BorderFactory.createLineBorder(
                new Color(208, 230, 212),
                1
            )
        );

        mapHolder.add(mapView, BorderLayout.CENTER);

        center.add(mapHolder, BorderLayout.CENTER);

        // Three separate information tiles.
        JPanel facts = transparent(
            new GridLayout(1, 3, 6, 0)
        );

        String area = value(farm, "area_ha");

        facts.add(
            fact(
                "LAND AREA",
                area.isBlank()
                    ? "Not set"
                    : area + " ha"
            )
        );

        facts.add(
            fact(
                "CROPS",
                value(farm, "crops")
            )
        );

        facts.add(
            fact(
                "LIVESTOCK",
                value(farm, "livestock")
            )
        );

        center.add(facts, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        // Existing navigation and location editing.
        JPanel actions = transparent(
            new FlowLayout(FlowLayout.LEFT, 7, 0)
        );

        JButton profile =
            Main.dashboardButton("View Farm Profile");

        profile.addActionListener(
            e -> host.go("Farm Profile")
        );

        actions.add(profile);

        if (canEdit) {
            JButton pin = Main.dashboardButton(
                hasMap
                    ? "Update Map Pin"
                    : "Set Map Pin"
            );

            pin.addActionListener(
                e -> FarmLocationMap.chooseAndSave(host)
            );

            actions.add(pin);
        }

        add(actions, BorderLayout.SOUTH);
    }

    private void showEmptyProfile(Main host) {
        JPanel empty = transparent(
            new GridLayout(3, 1, 0, 3)
        );

        JLabel title = new JLabel(
            "No farm profile available"
        );

        title.setFont(
            new Font("Segoe UI", Font.BOLD, 15)
        );

        title.setForeground(TEXT);
        empty.add(title);

        JLabel description = new JLabel(
            "Set up your farm details to personalize this dashboard."
        );

        description.setFont(
            new Font("Segoe UI", Font.PLAIN, 11)
        );

        description.setForeground(MUTED);
        empty.add(description);

        JLabel note = new JLabel(
            "No location or farm details will be guessed."
        );

        note.setFont(
            new Font("Segoe UI", Font.PLAIN, 11)
        );

        note.setForeground(MUTED);
        empty.add(note);

        add(empty, BorderLayout.CENTER);

        JButton profile =
            Main.dashboardButton("View Farm Profile");

        profile.addActionListener(
            e -> host.go("Farm Profile")
        );

        JPanel actions = transparent(
            new FlowLayout(FlowLayout.LEFT, 0, 0)
        );

        actions.add(profile);
        add(actions, BorderLayout.SOUTH);
    }

    private static JComponent fact(
        String title,
        String fullValue
    ) {
        String shown = fullValue.isBlank()
            ? "Not set"
            : fullValue;

        JPanel panel = new JPanel(
            new GridLayout(2, 1, 0, 1)
        );

        panel.setBackground(TILE);

        panel.setBorder(
            new EmptyBorder(3, 7, 3, 5)
        );

        JLabel heading = new JLabel(title);

        heading.setFont(
            new Font("Segoe UI", Font.BOLD, 9)
        );

        heading.setForeground(MUTED);

        JLabel text = new JLabel(
            shorten(shown, 16)
        );

        text.setFont(
            new Font("Segoe UI", Font.BOLD, 11)
        );

        text.setForeground(TEXT);
        text.setToolTipText(shown);

        panel.add(heading);
        panel.add(text);

        panel.setToolTipText(
            title + ": " + shown
        );

        return panel;
    }

    private static String value(
        Map<String, String> farm,
        String key
    ) {
        String value = farm.get(key);

        return value == null
            ? ""
            : value.trim();
    }

    private static String shorten(
        String value,
        int max
    ) {
        if (value.length() <= max) {
            return value;
        }

        return value.substring(
            0,
            Math.max(0, max - 3)
        ) + "...";
    }

    private static JPanel transparent(
        LayoutManager manager
    ) {
        JPanel panel = new JPanel(manager);
        panel.setOpaque(false);
        return panel;
    }
}
