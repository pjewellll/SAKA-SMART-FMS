package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * SAKA-SMART Notification Drawer V5 — readable text and circular notification count.
 * Visual-only upgrade of V1; existing PostgreSQL-backed alert sources remain in Main.
 * Read status is saved per local user/device via Java Preferences.
 */
final class NotificationDrawer extends JDialog {
    enum Category { TASKS, INVENTORY, MEMBERS, MARKETPLACE }
    static final class Alert {
        final String id, title, description, dateText, destination;
        final Category category;
        final LocalDate relevantDate;
        Alert(String id, Category category, String title, String description,
              LocalDate relevantDate, String destination) {
            this.id = Objects.toString(id, "");
            this.category = category;
            this.title = Objects.toString(title, "");
            this.description = Objects.toString(description, "");
            this.relevantDate = relevantDate;
            this.dateText = relevantDate == null ? "Active" : relevantDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
            this.destination = destination;
        }
    }
    private static final Color FOREST = new Color(5, 105, 55);
    private static final Color FOREST_DARK = new Color(3, 78, 42);
    private static final Color TEXT = new Color(14, 60, 36);
    private static final Color MUTED = new Color(86, 115, 94);
    private static final Color PALE = new Color(246, 252, 242);
    private static final Color LINE = new Color(186, 219, 186);
    private static final Color RED = new Color(202, 68, 61);
    private static final Color AMBER = new Color(206, 137, 29);
    private static final Color BLUE = new Color(49, 133, 185);
    private static final Color PURPLE = new Color(119, 95, 179);
    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font BOLD = new Font("Segoe UI", Font.BOLD, 14);

    private final List<Alert> alerts;
    private final Preferences preferences;
    private final Consumer<String> navigate;
    private final JPanel rows = new JPanel();
    private final JPanel filterBar = new JPanel(new GridLayout(1,5,5,0));
    private Category selectedCategory = null;
    private JScrollPane scroll;

    static void show(JFrame owner, List<Alert> alerts, String userId, Consumer<String> navigate) {
        new NotificationDrawer(owner, alerts, userId, navigate).setVisible(true);
    }
    private NotificationDrawer(JFrame owner, List<Alert> alerts, String userId, Consumer<String> navigate) {
        super(owner, "SAKA-SMART Notifications", false);
        this.alerts = new ArrayList<>(alerts);
        this.navigate = navigate;
        this.preferences = Preferences.userRoot().node("com/sakasmart/notifications/" +
            Integer.toHexString(Objects.toString(userId,"unknown").hashCode()));
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(Math.min(565, Math.max(450, owner.getWidth()-35)), Math.min(810, Math.max(420, owner.getHeight()-102)));
        try { setShape(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(),24,24)); } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {}
        setLocation(place(owner));
        setContentPane(build());
        getRootPane().registerKeyboardAction(e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    private Point place(JFrame owner) {
        Point p = owner.getLocationOnScreen();
        GraphicsConfiguration gc = owner.getGraphicsConfiguration();
        Rectangle bounds = gc == null ? new Rectangle(0,0,1920,1080) : gc.getBounds();
        int x = Math.max(bounds.x+6,Math.min(p.x+owner.getWidth()-getWidth()-8,bounds.x+bounds.width-getWidth()-8));
        int y = Math.max(bounds.y+7,Math.min(p.y+92,bounds.y+bounds.height-getHeight()-8));
        return new Point(x,y);
    }
    private JComponent build() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics original) {
                Graphics2D g=(Graphics2D) original.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0,0,new Color(254,255,251),0,getHeight(),new Color(227,247,222)));
                g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,24,24);
                g.setColor(new Color(38,140,76));g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,23,23);g.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2,2,2,2));

        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics original) {
                Graphics2D g=(Graphics2D)original.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0,0,new Color(48,173,99),0,getHeight(),FOREST_DARK));
                g.fillRoundRect(0,0,getWidth(),getHeight()+18,22,22);
                g.setColor(new Color(208,255,220,110));g.drawLine(13,2,getWidth()-13,2);
                g.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(9,13,9,8));
        JLabel heading = new JLabel("Notifications");
        heading.setForeground(Color.WHITE);
        heading.setFont(new Font("Segoe UI",Font.BOLD,21));
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));
        title.setOpaque(false);
        IconBadge bell = new IconBadge("bell",new Color(255,255,255,30),Color.WHITE,30);
        title.add(bell);title.add(heading);
        // Exact circular badge regardless of alert count (1 / 9 / 10+).
        title.add(new CountCircle(alerts.size()));
        header.add(title,BorderLayout.WEST);
        JButton close = new JButton("×");
        close.setFont(new Font("Segoe UI",Font.BOLD,23));
        close.setForeground(Color.WHITE);close.setOpaque(false);close.setContentAreaFilled(false);
        close.setBorder(new EmptyBorder(0,8,0,5));
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(e->dispose());
        header.add(close,BorderLayout.EAST);
        root.add(header,BorderLayout.NORTH);

        JPanel upper = new JPanel();
        upper.setOpaque(false);
        upper.setLayout(new BoxLayout(upper,BoxLayout.Y_AXIS));
        upper.setBorder(new EmptyBorder(8,10,4,10));
        filterBar.setOpaque(false);
        filterBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        upper.add(filterBar);
        upper.add(Box.createVerticalStrut(4));
        JButton mark = new JButton("✓  Mark all as read");
        mark.setFont(BOLD);mark.setForeground(FOREST);
        mark.setOpaque(false);mark.setContentAreaFilled(false);
        mark.setBorder(new EmptyBorder(2,3,4,3));
        mark.setAlignmentX(Component.LEFT_ALIGNMENT);
        mark.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        mark.addActionListener(e->{ for(Alert a:alerts) preferences.putBoolean(readKey(a),true);render(); });
        upper.add(mark);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(upper,BorderLayout.NORTH);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        rows.setOpaque(false);
        rows.setBorder(new EmptyBorder(1,10,10,10));
        scroll = new JScrollPane(rows,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(PALE);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(11,0));
        scroll.getVerticalScrollBar().setUI(new GlossyScrollUI());
        center.add(scroll,BorderLayout.CENTER);
        root.add(center,BorderLayout.CENTER);
        rebuildFilters();render();
        return root;
    }
    private int count(Category c) {
        int n=0;for(Alert a:alerts)if(a.category==c)n++;return n;
    }
    private void rebuildFilters() {
        filterBar.removeAll();
        filter("All",alerts.size(),null);
        filter("Tasks",count(Category.TASKS),Category.TASKS);
        filter("Inventory",count(Category.INVENTORY),Category.INVENTORY);
        filter("Members",count(Category.MEMBERS),Category.MEMBERS);
        filter("Orders",count(Category.MARKETPLACE),Category.MARKETPLACE);
        filterBar.revalidate();filterBar.repaint();
    }
    private void filter(String name,int num,Category cat) {
        boolean active = Objects.equals(cat, selectedCategory);
        PillButton b = new PillButton(name+" ("+num+")",active);
        b.addActionListener(e->{selectedCategory=cat;rebuildFilters();render();});
        filterBar.add(b);
    }

    /** Antialiased true circle: unlike a JLabel with padding, it never becomes square. */
    private static final class CountCircle extends JComponent {
        private final String count;
        CountCircle(int number) {
            count = Integer.toString(number);
            setOpaque(false);
            setPreferredSize(new Dimension(35,35));
            setMinimumSize(new Dimension(35,35));
            setMaximumSize(new Dimension(35,35));
            setToolTipText(number + " active notifications");
        }
        @Override protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int d = Math.min(getWidth(),getHeight())-3;
                int x = (getWidth()-d)/2, y = (getHeight()-d)/2;
                g.setPaint(new GradientPaint(x,y,new Color(248,95,96),
                    x+d,y+d,new Color(181,27,43)));
                g.fillOval(x,y,d,d);
                g.setColor(new Color(255,255,255,145));
                g.drawOval(x,y,d,d);
                g.setFont(new Font("Segoe UI",Font.BOLD,count.length()>2?12:16));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(Color.WHITE);
                g.drawString(count,(getWidth()-fm.stringWidth(count))/2,
                    (getHeight()-fm.getHeight())/2+fm.getAscent());
            } finally {g.dispose();}
        }
    }

    private static final class PillButton extends JButton {
        private final boolean active;
        PillButton(String label,boolean active){super(label);this.active=active;
            setFont(new Font("Segoe UI",Font.BOLD,12));setForeground(active?Color.WHITE:TEXT);
            setOpaque(false);setContentAreaFilled(false);setBorderPainted(false);
            setFocusPainted(false);setMargin(new Insets(8,3,8,3));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(92,38));
        }
        @Override protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            if(active)g.setPaint(new GradientPaint(0,1,new Color(83,205,112),0,h,FOREST_DARK));
            else g.setPaint(new GradientPaint(0,1,Color.WHITE,0,h,new Color(211,241,213)));
            g.fillRoundRect(1,1,w-3,h-4,24,24);
            g.setColor(active?new Color(17,125,69):LINE);g.drawRoundRect(1,1,w-3,h-4,24,24);
            if(active){g.setColor(new Color(255,255,255,90));g.drawLine(13,3,w-14,3);}
            g.dispose();super.paintComponent(original);
        }
    }

    private static final class GlossyScrollUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int orientation){return zeroButton();}
        @Override protected JButton createIncreaseButton(int orientation){return zeroButton();}
        private JButton zeroButton(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));b.setMaximumSize(new Dimension(0,0));return b;}
        @Override protected void paintTrack(Graphics original,JComponent c,Rectangle r){
            Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(227,244,225));g.fillRoundRect(r.x+2,r.y+1,Math.max(2,r.width-4),r.height-2,10,10);g.dispose();
        }
        @Override protected void paintThumb(Graphics original,JComponent c,Rectangle r){
            if(r.isEmpty())return;Graphics2D g=(Graphics2D)original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(r.x,r.y,new Color(84,191,113),r.x+r.width,r.y+r.height,new Color(11,107,60)));
            g.fillRoundRect(r.x+1,r.y,r.width-2,r.height,10,10);g.dispose();
        }
    }

    private static String readKey(Alert a){return "a"+Integer.toHexString(a.id.hashCode());}
    private void render() {
        rows.removeAll();
        List<Alert> filtered = new ArrayList<>();
        for (Alert a : alerts) {
            if (selectedCategory == null || a.category == selectedCategory) filtered.add(a);
        }
        if (filtered.isEmpty()) {
            JLabel empty = new JLabel("No notifications in this category.", SwingConstants.CENTER);
            empty.setForeground(MUTED);
            empty.setFont(FONT);
            empty.setBorder(new EmptyBorder(30,0,30,0));
            addRow(empty);
        } else {
            List<Alert> today = new ArrayList<>();
            List<Alert> week = new ArrayList<>();
            List<Alert> earlier = new ArrayList<>();
            LocalDate current = LocalDate.now();
            for (Alert a : filtered) {
                // Current low stock and overdue tasks need attention now.
                // Upcoming expiry alerts follow their REAL calendar dates.
                boolean ongoing = a.relevantDate == null ||
                    (a.category == Category.TASKS && !a.relevantDate.isAfter(current)) ||
                    a.relevantDate.equals(current);
                if (ongoing) today.add(a);
                else if (a.relevantDate.isAfter(current) && !a.relevantDate.isAfter(current.plusDays(7))) week.add(a);
                else earlier.add(a);
            }
            group("Today", today);
            group("This Week", week);
            group("Earlier / Other Dates", earlier);
        }
        rows.revalidate();
        rows.repaint();
        if (scroll != null) SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    private void group(String name, List<Alert> items) {
        if (items.isEmpty()) return;
        JPanel heading = new JPanel(new BorderLayout(12,0));
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(12,1,7,1));
        JLabel text = new JLabel(name + " (" + items.size() + ")");
        text.setForeground(FOREST);
        text.setFont(new Font("Segoe UI",Font.BOLD,16));
        heading.add(text, BorderLayout.WEST);
        JSeparator line = new JSeparator();
        line.setForeground(LINE);
        heading.add(line, BorderLayout.CENTER);
        addRow(heading);
        for (Alert a : items) {
            addRow(card(a));
            rows.add(Box.createVerticalStrut(5));
        }
    }

    private void addRow(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        rows.add(c);
    }

    private JComponent card(Alert a) {
        boolean read = preferences.getBoolean(readKey(a), false);
        LocalDate now = LocalDate.now();
        boolean overdue = a.relevantDate != null && a.relevantDate.isBefore(now);
        Color accent = a.category == Category.TASKS ? BLUE :
            (a.category == Category.MEMBERS ? PURPLE :
            (a.category == Category.MARKETPLACE ? new Color(23,148,110) :
            (overdue ? RED : a.title.toLowerCase(Locale.ROOT).contains("stock") ? RED : AMBER)));

        JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(false);
        shell.setBorder(new LineBorder(LINE,1,true));
        JPanel content = new JPanel(new BorderLayout(8,0));
        content.setBackground(read ? new Color(245,249,243) : Color.WHITE);
        content.setBorder(new EmptyBorder(10,9,10,9));

        JPanel stripe = new JPanel();
        stripe.setBackground(accent);
        stripe.setPreferredSize(new Dimension(3,1));
        content.add(stripe, BorderLayout.WEST);

        JPanel contentLine = new JPanel(new BorderLayout(12,0));
        contentLine.setOpaque(false);
        contentLine.add(new IconBadge(symbol(a),tint(accent),accent,42),BorderLayout.WEST);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy,BoxLayout.Y_AXIS));
        JLabel title = new JLabel(escapeHtml(trim(a.title,43)));
        title.setForeground(TEXT);
        title.setFont(new Font("Segoe UI",Font.BOLD,15));
        JLabel desc = new JLabel(escapeHtml(trim(a.description,47)));
        desc.setForeground(new Color(75,91,77));
        desc.setFont(new Font("Segoe UI",Font.PLAIN,14));
        JLabel tag = new JLabel("● " + sourceLabel(a.category));
        tag.setForeground(FOREST);
        tag.setFont(new Font("Segoe UI",Font.BOLD,12));
        copy.add(title);
        copy.add(Box.createVerticalStrut(2));
        copy.add(desc);
        copy.add(Box.createVerticalStrut(2));
        copy.add(tag);
        contentLine.add(copy,BorderLayout.CENTER);

        JPanel meta = new JPanel();
        meta.setOpaque(false);
        meta.setLayout(new BoxLayout(meta,BoxLayout.Y_AXIS));
        JLabel date = new JLabel(a.dateText);
        date.setForeground(MUTED);
        date.setFont(new Font("Segoe UI",Font.PLAIN,12));
        date.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JLabel dot = new JLabel(read ? "○" : "●");
        dot.setForeground(read ? new Color(166,183,170) : FOREST);
        dot.setFont(new Font("Segoe UI",Font.BOLD,12));
        dot.setAlignmentX(Component.RIGHT_ALIGNMENT);
        meta.add(date);
        meta.add(Box.createVerticalStrut(6));
        meta.add(dot);
        contentLine.add(meta,BorderLayout.EAST);
        content.add(contentLine,BorderLayout.CENTER);
        shell.add(content,BorderLayout.CENTER);
        // Slightly taller, so 15/14/12pt text is never compressed.
        shell.setPreferredSize(new Dimension(510,88));

        // Entire row is clickable, with a tooltip for clarity.
        MouseAdapter opener = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                preferences.putBoolean(readKey(a), true);
                dispose();
                navigate.accept(a.destination);
            }
        };
        setOpenAction(shell, opener);
        shell.setToolTipText("Open " + sourceLabel(a.category) + " • " + a.title);
        return shell;
    }

    private void setOpenAction(Component component, MouseAdapter action) {
        component.addMouseListener(action);
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) setOpenAction(child, action);
        }
    }
    private static String trim(String s,int limit){return s.length()>limit?s.substring(0,limit-1)+"…":s;}
    private static String escapeHtml(String s){return "<html>"+s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</html>";}
    private static String sourceLabel(Category c){return switch(c){case TASKS->"Calendar";case INVENTORY->"Inventory";case MEMBERS->"Members";case MARKETPLACE->"Marketplace";};}
    private static String symbol(Alert a){return switch(a.category){case TASKS->"calendar";case MEMBERS->"members";case MARKETPLACE->"box";case INVENTORY->a.title.toLowerCase(Locale.ROOT).contains("stock")?"box":"hourglass";};}
    private static Color tint(Color c){return new Color((c.getRed()+255*3)/4,(c.getGreen()+255*3)/4,(c.getBlue()+255*3)/4);}

    /** Geometric icons painted in Swing; avoids missing-glyph squares on Windows fonts. */
    private static final class IconBadge extends JComponent {
        private final String kind;private final Color bg,fg;private final int size;
        IconBadge(String kind,Color bg,Color fg,int size){this.kind=kind;this.bg=bg;this.fg=fg;this.size=size;
            setPreferredSize(new Dimension(size,size));setMinimumSize(new Dimension(size,size));setOpaque(false);}
        @Override protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight(),s=Math.min(w,h),x=(w-s)/2,y=(h-s)/2;
                g.setColor(bg);g.fillOval(x+1,y+1,s-2,s-2);
                g.translate(w/2.0,h/2.0);g.setColor(fg);g.setStroke(new BasicStroke(2.0f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                switch(kind){
                    case "box" -> {g.drawRect(-10,-8,20,17);g.drawLine(-10,-8,0,-13);g.drawLine(10,-8,0,-13);g.drawLine(0,-13,0,9);}
                    case "calendar" -> {g.drawRoundRect(-11,-10,22,21,3,3);g.drawLine(-11,-3,11,-3);g.drawLine(-5,-13,-5,-7);g.drawLine(5,-13,5,-7);g.drawLine(-4,3,-1,6);g.drawLine(-1,6,6,0);}
                    case "hourglass" -> {g.drawLine(-8,-12,8,-12);g.drawLine(-8,12,8,12);g.drawLine(-6,-11,-6,-5);g.drawLine(6,-11,6,-5);g.drawLine(-6,-5,6,6);g.drawLine(6,-5,-6,6);g.drawLine(-6,6,-6,11);g.drawLine(6,6,6,11);}
                    case "members" -> {g.drawOval(-4,-10,8,8);g.drawArc(-10,-1,20,15,0,180);g.drawOval(-13,-4,5,5);g.drawOval(8,-4,5,5);}
                    case "bell" -> {g.drawArc(-8,-9,16,16,0,180);g.drawLine(-8,-1,-8,8);g.drawLine(8,-1,8,8);g.drawLine(-11,9,11,9);g.fillOval(-2,11,4,3);}
                    default -> g.drawOval(-8,-8,16,16);
                }
            } finally {g.dispose();}
        }
    }
}
