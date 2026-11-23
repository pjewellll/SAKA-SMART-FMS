package com.sakasmart;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * SAKA-SMART Manage My Profile V4 – compact glossy white-green dialog.
 * Own-account name persists in PostgreSQL; photo remains local per desktop.
 * Uses the OS-native image file picker for familiar file navigation.
 */
final class ProfileEditor {
    private static final Color DARK = new Color(5, 75, 36);
    private static final Color GREEN = new Color(7, 120, 53);
    private static final Color MID = new Color(30, 149, 76);
    private static final Color MUTED = new Color(78, 112, 88);
    private static final Color OUTLINE = new Color(144, 204, 158);
    private static final Color PALE = new Color(242, 251, 242);
    private static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 15);
    private static final int PHOTO_MAX_BYTES = 3 * 1024 * 1024;
    private ProfileEditor() {}

    static JButton avatarButton(Main host, Map<String,String> user, int size) {
        String id = user.getOrDefault("id", "");
        String username = user.getOrDefault("username", "");
        Avatar button = new Avatar(user.getOrDefault("full_name", username), readPhoto(id,username), size);
        button.setToolTipText("Manage My Profile");
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> open(host));
        return button;
    }

    static void open(Main host) {
        if (host.user == null || host.user.getOrDefault("id", "").isBlank()) {
            JOptionPane.showMessageDialog(host.frame, "Please sign in first.");
            return;
        }
        Map<String,String> account = host.user;
        String id = account.getOrDefault("id", "");
        String username = account.getOrDefault("username", "");
        String originalName = account.getOrDefault("full_name", username);
        BufferedImage existing = readPhoto(id, username);
        final BufferedImage[] selected = { existing };
        final boolean[] changed = { false }, removed = { false };

        JDialog dialog = new JDialog(host.frame, "Manage My Profile", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setUndecorated(true);
        int width = 720, height = 660;
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        width = Math.min(width, Math.max(540, screen.width - 36));
        height = Math.min(height, Math.max(560, screen.height - 36));
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(host.frame);
        try { dialog.setShape(new RoundRectangle2D.Double(0, 0, width, height, 32, 32)); }
        catch (UnsupportedOperationException | IllegalComponentStateException ignored) { }

        RoundedShell shell = new RoundedShell();
        shell.setLayout(new BorderLayout(0, 0));
        dialog.setContentPane(shell);

        JPanel top = transparent(new BorderLayout(10, 0));
        top.setBorder(new EmptyBorder(10, 22, 8, 14));
        top.add(text("Manage My Profile", 17, true, DARK), BorderLayout.WEST);
        RoundedButton close = button("×", false);
        close.setPreferredSize(new Dimension(38, 36));
        close.setFont(new Font("Segoe UI", Font.BOLD, 24));
        close.addActionListener(e -> dialog.dispose());
        top.add(close, BorderLayout.EAST);
        shell.add(top, BorderLayout.NORTH);
        final Point[] drag = { null };
        top.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { drag[0] = e.getPoint(); }
        });
        top.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (drag[0] != null) {
                    Point pt = e.getLocationOnScreen();
                    dialog.setLocation(pt.x - drag[0].x, pt.y - drag[0].y);
                }
            }
        });

        JPanel content = transparent(new BorderLayout(0, 13));
        content.setBorder(new EmptyBorder(3, 20, 16, 20));

        GlossyHero hero = new GlossyHero();
        hero.setLayout(new BorderLayout(18, 0));
        hero.setBorder(new EmptyBorder(12, 21, 12, 18));
        hero.setPreferredSize(new Dimension(0, 124));
        Avatar avatar = new Avatar(originalName, existing, 99);
        avatar.setEnabled(false);
        JPanel avatarHolder = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        avatarHolder.add(avatar);
        hero.add(avatarHolder, BorderLayout.WEST);
        JPanel headings = transparent();
        headings.setLayout(new BoxLayout(headings, BoxLayout.Y_AXIS));
        headings.add(Box.createVerticalGlue());
        JLabel heroTitle = text("Manage My Profile", 24, true, DARK);
        heroTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headings.add(heroTitle);
        headings.add(Box.createVerticalStrut(4));
        JLabel subtitle = text("Your personal SAKA-SMART account", 14, false, MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headings.add(subtitle);
        headings.add(Box.createVerticalGlue());
        hero.add(headings, BorderLayout.CENTER);
        content.add(hero, BorderLayout.NORTH);

        RoundedPanel card = new RoundedPanel(Color.WHITE, new Color(247, 253, 248), 27);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(16, 22, 12, 22));
        JTextField name = new RoundedTextField(originalName, true);
        JTextField login = new RoundedTextField(username, false);
        JTextField email = new RoundedTextField(account.getOrDefault("email", ""), false);
        addField(card, 0, "Full name", name);
        addField(card, 1, "Username (read-only)", login);
        addField(card, 2, "Email (read-only)", email);

        JPanel photoActions = transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        RoundedButton choose = button("Change Photo", true);
        RoundedButton remove = button("Remove Photo", false);
        choose.setPreferredSize(new Dimension(148, 40));
        remove.setPreferredSize(new Dimension(152, 40));
        photoActions.add(choose);
        photoActions.add(remove);
        addField(card, 3, "Profile photo (PNG or JPG, up to 3 MB)", photoActions);
        GridBagConstraints noteC = con(4);
        noteC.insets = new Insets(0, 0, 0, 0);
        JLabel note = text("Photo stays on this computer; name is saved to PostgreSQL.", 12, false, MUTED);
        card.add(note, noteC);
        content.add(card, BorderLayout.CENTER);

        JPanel actions = transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        RoundedButton cancel = button("Cancel", false);
        RoundedButton save = button("Save Changes", true);
        cancel.setPreferredSize(new Dimension(115, 42));
        save.setPreferredSize(new Dimension(162, 42));
        actions.add(cancel);
        actions.add(save);
        content.add(actions, BorderLayout.SOUTH);
        shell.add(content, BorderLayout.CENTER);

        choose.addActionListener(e -> {
            File chosen = selectPhoto(dialog);
            if (chosen == null) return;
            try {
                if (!Files.isRegularFile(chosen.toPath())) throw new IOException("Please select an image file.");
                if (Files.size(chosen.toPath()) > PHOTO_MAX_BYTES) throw new IOException("Please choose an image smaller than 3 MB.");
                BufferedImage source = ImageIO.read(chosen);
                if (source == null) throw new IOException("That file is not a valid PNG or JPEG image.");
                if (source.getWidth() > 5000 || source.getHeight() > 5000) throw new IOException("Image dimensions are too large (maximum 5000 pixels per side).");
                selected[0] = squareCrop(source, 256);
                changed[0] = true; removed[0] = false;
                avatar.setPhoto(selected[0]);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Photo Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        remove.addActionListener(e -> {
            selected[0] = null; changed[0] = true; removed[0] = true;
            avatar.setPhoto(null);
        });
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            String newName = name.getText().strip();
            if (newName.length() < 2 || newName.length() > 80 || newName.indexOf('\n') >= 0 || newName.indexOf('\r') >= 0) {
                JOptionPane.showMessageDialog(dialog, "Please enter a full name between 2 and 80 characters.", "Invalid Name", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                if (!newName.equals(originalName)) host.store.edit("users", id, Map.of("full_name", newName));
                host.user.put("full_name", newName);
                if (changed[0]) {
                    if (removed[0]) Files.deleteIfExists(photoPath(id, username));
                    else savePhoto(id, username, selected[0]);
                }
                host.store.log(username, "Updated own profile");
                dialog.dispose();
                host.showShell();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Unable to finish saving profile: " + ex.getMessage(), "Profile Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        dialog.setVisible(true);
    }

    /** Windows-native picker instead of the old Swing 1990s-style JFileChooser. */
    private static File selectPhoto(Window owner) {
        Frame frame=owner instanceof Frame?(Frame)owner: (owner instanceof Dialog ? (Frame)((Dialog)owner).getOwner():null);
        FileDialog picker=new FileDialog(frame,"Choose a Profile Photo",FileDialog.LOAD);
        picker.setFilenameFilter((dir,file)->{
            String s=file.toLowerCase(Locale.ROOT);
            return s.endsWith(".png")||s.endsWith(".jpg")||s.endsWith(".jpeg")||new File(dir,file).isDirectory();
        });
        Path pictures=Paths.get(System.getProperty("user.home"),"Pictures");
        if(Files.isDirectory(pictures))picker.setDirectory(pictures.toString());
        picker.setVisible(true);
        String file=picker.getFile();
        File result=file==null?null:new File(picker.getDirectory(),file);
        picker.dispose();
        return result;
    }

    private static JPanel transparent(){JPanel p=new JPanel();p.setOpaque(false);return p;}
    private static JPanel transparent(LayoutManager l){JPanel p=new JPanel(l);p.setOpaque(false);return p;}
    private static JLabel text(String txt,int size,boolean bold,Color c){JLabel l=new JLabel(txt);l.setForeground(c);l.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));return l;}
    private static GridBagConstraints con(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = y; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, y == 4 ? 0 : 11, 0);
        return c;
    }
    private static void addField(JPanel parent, int y, String label, JComponent input) {
        JPanel group = transparent(new BorderLayout(0, 5));
        group.add(text(label, 13, true, DARK), BorderLayout.NORTH);
        group.add(input, BorderLayout.CENTER);
        parent.add(group, con(y));
    }
    /** Paints both field fill and border in one anti-aliased rounded shape. */
    private static final class RoundedTextField extends JTextField {
        private final boolean changeable;
        RoundedTextField(String value, boolean editable) {
            super(value);
            changeable = editable;
            setEditable(editable);
            setFont(new Font("Segoe UI", Font.PLAIN, 15));
            setForeground(DARK);
            setCaretColor(GREEN);
            setOpaque(false);
            setBorder(new EmptyBorder(8, 14, 8, 14));
            setPreferredSize(new Dimension(200, 42));
            setMinimumSize(new Dimension(140, 42));
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g.setColor(changeable ? Color.WHITE : new Color(242, 249, 243));
                g.fillRoundRect(1, 1, w-3, h-3, 17, 17);
                g.setColor(hasFocus() && changeable ? GREEN : OUTLINE);
                g.setStroke(new BasicStroke(hasFocus() && changeable ? 2f : 1.25f));
                g.drawRoundRect(1, 1, w-3, h-3, 17, 17);
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }
    private static final class GlossyHero extends JPanel {
        GlossyHero() { setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g.setPaint(new GradientPaint(0, 0, new Color(237, 255, 238), w, h, new Color(166, 230, 164)));
                g.fillRoundRect(1, 1, w-3, h-3, 26, 26);
                g.setColor(new Color(146, 216, 155));
                g.drawRoundRect(1, 1, w-3, h-3, 26, 26);
                g.setColor(new Color(255, 255, 255, 115));
                g.fillRoundRect(3, 3, w-7, Math.max(1, h / 3), 24, 24);
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }
    private static RoundedButton button(String label,boolean primary){return new RoundedButton(label,primary);}

    private static final class RoundedButton extends JButton {
        private final boolean primary;
        RoundedButton(String text,boolean primary){super(text);this.primary=primary;
            setFont(new Font("Segoe UI",Font.BOLD,14));setForeground(primary?Color.WHITE:DARK);
            setPreferredSize(new Dimension(Math.max(136,getFontMetrics(getFont()).stringWidth(text)+38),44));
            setContentAreaFilled(false);setFocusPainted(false);setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));setOpaque(false);
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                GradientPaint gradient=primary?
                    new GradientPaint(0,1,new Color(67,207,119),0,h,new Color(0,103,47)):
                    new GradientPaint(0,1,Color.WHITE,0,h,new Color(212,241,219));
                g.setPaint(gradient);g.fillRoundRect(1,1,w-3,h-4,22,22);
                g.setColor(primary?new Color(0,107,47):OUTLINE);g.setStroke(new BasicStroke(1.4f));g.drawRoundRect(1,1,w-3,h-4,22,22);
                g.setColor(new Color(255,255,255,primary?110:170));g.drawRoundRect(3,3,w-7,Math.max(5,h/2-3),19,19);
            }finally{g.dispose();}
            super.paintComponent(graphics);
        }
    }
    private static final class RoundedPanel extends JPanel {
        final Color top,bottom;final int arc;
        RoundedPanel(Color top,Color bottom,int arc){this.top=top;this.bottom=bottom;this.arc=arc;setOpaque(false);}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();g.setPaint(new GradientPaint(0,0,top,0,h,bottom));
                g.fillRoundRect(1,1,w-3,h-3,arc,arc);g.setColor(new Color(191,224,198));g.drawRoundRect(1,1,w-3,h-3,arc,arc);
            }finally{g.dispose();}super.paintComponent(graphics);
        }
    }
    private static final class RoundedShell extends JPanel {
        RoundedShell(){setOpaque(false);}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();g.setPaint(new GradientPaint(0,0,Color.WHITE,0,h,new Color(221,246,225)));
                g.fillRoundRect(0,0,w-1,h-1,34,34);
                g.setColor(new Color(77,168,93));g.setStroke(new BasicStroke(2));g.drawRoundRect(1,1,w-3,h-3,34,34);
            }finally{g.dispose();}super.paintComponent(graphics);
        }
    }
    private static Path photoPath(String id,String username) throws Exception {
        String home=System.getenv("APPDATA");
        if(home==null || home.isBlank())home=System.getProperty("user.home")+File.separator+".saka-smart";
        Path folder=Paths.get(home,"SAKA-SMART","profile-photos");
        MessageDigest dig=MessageDigest.getInstance("SHA-256");
        byte[] hash=dig.digest((id+"\u0000"+username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder key=new StringBuilder();for(byte b:hash)key.append(String.format("%02x",b & 255));
        return folder.resolve(key+".png");
    }
    private static BufferedImage readPhoto(String id,String username){
        try{Path p=photoPath(id,username);if(!Files.isRegularFile(p)||Files.size(p)>PHOTO_MAX_BYTES)return null;return ImageIO.read(p.toFile());}
        catch(Exception ignored){return null;}
    }
    private static void savePhoto(String id,String username,BufferedImage image)throws Exception{
        if(image==null)throw new IOException("No image chosen.");
        Path path=photoPath(id,username);Files.createDirectories(path.getParent());
        Path temp=Files.createTempFile(path.getParent(),"profile-",".png");
        try{if(!ImageIO.write(image,"PNG",temp.toFile()))throw new IOException("Could not encode PNG.");
            try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static BufferedImage squareCrop(BufferedImage source,int size){
        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int side=Math.min(source.getWidth(),source.getHeight());int sx=(source.getWidth()-side)/2,sy=(source.getHeight()-side)/2;
            g.drawImage(source,0,0,size,size,sx,sy,sx+side,sy+side,null);
        }finally{g.dispose();}return out;
    }
    private static final class Avatar extends JButton {
        private BufferedImage photo;private final String initials;
        Avatar(String fullName,BufferedImage photo,int size){
            this.photo=photo;String[] names=fullName.strip().split("\\s+");
            String s=names.length==0||names[0].isBlank()?"?":names[0].substring(0,1).toUpperCase(Locale.ROOT);
            if(names.length>1)s+=names[names.length-1].substring(0,1).toUpperCase(Locale.ROOT);
            initials=s;setPreferredSize(new Dimension(size,size));setMinimumSize(new Dimension(size,size));
            setMaximumSize(new Dimension(size,size));setOpaque(false);setFocusPainted(false);setContentAreaFilled(false);setBorderPainted(false);
        }
        void setPhoto(BufferedImage image){photo=image;repaint();}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int d=Math.min(getWidth(),getHeight())-9;int x=(getWidth()-d)/2,y=(getHeight()-d)/2;
                g.setPaint(new GradientPaint(x,y,new Color(85,201,117),x+d,y+d,new Color(0,98,47)));
                g.fillOval(x,y,d,d);
                if(photo!=null){Shape clip=g.getClip();g.setClip(new Ellipse2D.Float(x+4,y+4,d-8,d-8));
                    g.drawImage(photo,x+4,y+4,d-8,d-8,null);g.setClip(clip);
                }else{g.setColor(Color.WHITE);g.setFont(new Font("Segoe UI",Font.BOLD,Math.max(18,d/3)));
                    FontMetrics fm=g.getFontMetrics();g.drawString(initials,(getWidth()-fm.stringWidth(initials))/2,(getHeight()-fm.getHeight())/2+fm.getAscent());}
                g.setStroke(new BasicStroke(3.5f));g.setColor(Color.WHITE);g.drawOval(x+2,y+2,d-4,d-4);
                g.setStroke(new BasicStroke(2f));g.setColor(new Color(23,128,61));g.drawOval(x,y,d,d);
            }finally{g.dispose();}
        }
    }
}
