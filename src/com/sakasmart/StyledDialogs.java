package com.sakasmart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;

/** Shared accessible, high-contrast dark emerald Swing dialog system. */
public final class StyledDialogs {
    private static final Color WHITE = new Color(244, 255, 247);
    private static final Color MINT = new Color(191, 239, 207);
    private static final Color INK = new Color(14, 69, 42);
    private static final Font TEXT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font BOLD = new Font("Segoe UI", Font.BOLD, 14);
    private StyledDialogs() {}

    private static class Surface extends JPanel {
        Surface(LayoutManager layout) { super(layout); setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(), h=getHeight();
            g.setColor(new Color(0, 24, 13, 115));
            g.fillRoundRect(8, 11, Math.max(0,w-15), Math.max(0,h-20), 30, 30);
            g.setPaint(new GradientPaint(0,0,new Color(8,49,35),w,h,new Color(11,106,66)));
            g.fillRoundRect(4, 4, Math.max(0,w-15), Math.max(0,h-21), 29, 29);
            g.setPaint(new GradientPaint(0,3,new Color(189,255,211,37),0,h/2f,new Color(255,255,255,0)));
            g.fillRoundRect(7, 7, Math.max(0,w-21), Math.max(0,(h-26)/2), 27, 27);
            g.setColor(new Color(174, 242, 186, 195));
            g.setStroke(new BasicStroke(1.8f));
            g.drawRoundRect(5,5,Math.max(0,w-17),Math.max(0,h-23),28,28);
            g.dispose(); super.paintComponent(graphics);
        }
    }
    private static class GlossButton extends JButton {
        private final boolean primary, danger;
        GlossButton(String title, boolean primary, boolean danger) {
            super(title); this.primary=primary;this.danger=danger;
            setFont(BOLD);setForeground(primary?Color.WHITE:INK);
            setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false);
            setBorder(new EmptyBorder(11,16,14,16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g=(Graphics2D)gr.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();
            Color top=primary?(danger?new Color(235,98,90):new Color(61,207,113)):new Color(233,249,232);
            Color bot=primary?(danger?new Color(151,37,42):new Color(12,109,59)):new Color(156,213,169);
            if(getModel().isPressed()){top=top.darker();bot=bot.darker();}
            else if(getModel().isRollover()){top=top.brighter();bot=bot.brighter();}
            g.setColor(new Color(0,20,10,92));g.fillRoundRect(1,4,w-2,h-5,16,16);
            g.setPaint(new GradientPaint(0,0,top,0,h-5,bot));g.fillRoundRect(1,1,w-2,h-6,16,16);
            g.setPaint(new GradientPaint(0,1,new Color(255,255,255,135),0,h*.56f,new Color(255,255,255,0)));
            g.fillRoundRect(3,2,w-6,Math.max(5,h/2),14,14);
            g.setColor(new Color(255,255,255,145));g.drawRoundRect(2,2,w-4,h-9,14,14);
            g.dispose();super.paintComponent(gr);
        }
    }
    private static JLabel heading(String title) {
        JLabel l=new JLabel(title);l.setForeground(WHITE);l.setFont(new Font("Segoe UI",Font.BOLD,23));return l;
    }
    private static JLabel kicker(int type) {
        String s= switch(type) {
            case JOptionPane.ERROR_MESSAGE -> "ERROR  /  PLEASE CHECK";
            case JOptionPane.WARNING_MESSAGE -> "ATTENTION  /  CONFIRM ACTION";
            case JOptionPane.QUESTION_MESSAGE -> "SAKA-SMART  /  YOUR CHOICE";
            default -> "SAKA-SMART  /  INFORMATION";
        };
        JLabel l=new JLabel(s);l.setForeground(type==JOptionPane.ERROR_MESSAGE?new Color(255,186,173):new Color(168,241,184));l.setFont(new Font("Segoe UI",Font.BOLD,12));return l;
    }
    private static JComponent content(Object message) {
        if(message instanceof Component comp){
            JPanel surround=new JPanel(new BorderLayout());surround.setBackground(new Color(231,247,232));
            surround.setBorder(new EmptyBorder(10,12,10,12));surround.add(comp,BorderLayout.CENTER);
            return surround;
        }
        String raw=Objects.toString(message,"");
        JTextArea text=new JTextArea(raw);
        text.setColumns(44);text.setFont(TEXT);text.setForeground(WHITE);text.setBackground(new Color(9,74,49));
        text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);
        text.setBorder(new EmptyBorder(12,15,12,15));
        int rows=Math.max(1,Math.min(10,(raw.length()+49)/50 + Math.max(0,raw.split("\n",-1).length-1)));
        text.setRows(rows);
        text.setPreferredSize(new Dimension(525,Math.min(260,rows*27+25)));
        return text;
    }
    private static JDialog dialog(Component parent,String title,int type,JComponent body,JPanel buttons,int width) {
        Window owner=parent==null?null:SwingUtilities.getWindowAncestor(parent);
        JDialog d=owner instanceof Frame f?new JDialog(f,title,Dialog.ModalityType.APPLICATION_MODAL):
                owner instanceof Dialog q?new JDialog(q,title,Dialog.ModalityType.APPLICATION_MODAL):
                new JDialog((Frame)null,title,Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setUndecorated(true);
        boolean translucent=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        if(translucent)d.setBackground(new Color(0,0,0,0));
        Surface surface=new Surface(new BorderLayout(0,14));surface.setBorder(new EmptyBorder(27,30,32,37));
        JPanel head=new JPanel(new GridLayout(2,1,0,4));head.setOpaque(false);
        head.add(kicker(type));head.add(heading(title));
        surface.add(head,BorderLayout.NORTH);
        Dimension desired=body.getPreferredSize();
        int maxBodyHeight=owner==null?420:Math.max(260,owner.getHeight()-235);
        if(desired.height>maxBodyHeight){
            JScrollPane scroll=new JScrollPane(body);
            scroll.setBorder(null);scroll.getViewport().setOpaque(false);scroll.setOpaque(false);
            scroll.getVerticalScrollBar().setUnitIncrement(18);
            surface.add(scroll,BorderLayout.CENTER);
        }else{
            surface.add(body,BorderLayout.CENTER);
        }
        buttons.setOpaque(false);surface.add(buttons,BorderLayout.SOUTH);
        d.setContentPane(surface);
        int h=Math.max(210,Math.min(770,desired.height+148));
        if(owner!=null)h=Math.min(h,Math.max(290,owner.getHeight()-55));
        d.setSize(Math.max(width, Math.min(750,desired.width+80)),h);
        d.setLocationRelativeTo(parent);
        d.getRootPane().registerKeyboardAction(e->d.dispose(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }
    private static JPanel footer() {JPanel p=new JPanel(new FlowLayout(FlowLayout.RIGHT,9,0));p.setOpaque(false);return p;}
    public static void showMessageDialog(Component parent,Object message,String title,int type) {
        JPanel buttons=footer();GlossButton close=new GlossButton("Okay",true,false);buttons.add(close);
        JDialog d=dialog(parent,title,type,content(message),buttons,540);
        close.addActionListener(e->d.dispose());d.getRootPane().setDefaultButton(close);d.setVisible(true);
    }
    public static int showConfirmDialog(Component parent,Object message,String title,int optionType) {
        return showConfirmDialog(parent,message,title,optionType,JOptionPane.QUESTION_MESSAGE);
    }
    public static int showConfirmDialog(Component parent,Object message,String title,int optionType,int messageType) {
        if(title.toLowerCase(java.util.Locale.ROOT).contains("delete") ||
           title.toLowerCase(java.util.Locale.ROOT).contains("reject"))messageType=JOptionPane.WARNING_MESSAGE;
        int[] result={JOptionPane.CLOSED_OPTION};JPanel buttons=footer();
        GlossButton cancel=new GlossButton(optionType==JOptionPane.YES_NO_OPTION?"No":"Cancel",false,false);
        GlossButton accept=new GlossButton(optionType==JOptionPane.YES_NO_OPTION?"Yes":"Continue",true,messageType==JOptionPane.WARNING_MESSAGE);
        if(optionType!=JOptionPane.DEFAULT_OPTION){buttons.add(cancel);}buttons.add(accept);
        JDialog d=dialog(parent,title,messageType,content(message),buttons,590);
        cancel.addActionListener(e->{result[0]=optionType==JOptionPane.YES_NO_OPTION?JOptionPane.NO_OPTION:JOptionPane.CANCEL_OPTION;d.dispose();});
        accept.addActionListener(e->{result[0]=optionType==JOptionPane.YES_NO_OPTION?JOptionPane.YES_OPTION:JOptionPane.OK_OPTION;d.dispose();});
        d.getRootPane().setDefaultButton(accept);d.setVisible(true);return result[0];
    }
    public static String showInputDialog(Component parent,Object message,Object defaultValue) {
        JPanel box=new JPanel(new BorderLayout(0,12));box.setOpaque(false);
        JLabel title=new JLabel(Objects.toString(message,""));title.setFont(TEXT);title.setForeground(WHITE);
        JTextField field=new JTextField(Objects.toString(defaultValue,""));field.setFont(TEXT);field.setForeground(INK);field.setBackground(new Color(244,255,245));
        field.setBorder(new EmptyBorder(10,12,10,12));field.setPreferredSize(new Dimension(380,42));
        box.add(title,BorderLayout.NORTH);box.add(field,BorderLayout.CENTER);
        final String[] result={null};JPanel buttons=footer();GlossButton cancel=new GlossButton("Cancel",false,false),ok=new GlossButton("Continue",true,false);
        buttons.add(cancel);buttons.add(ok);
        JDialog d=dialog(parent,"Enter Value",JOptionPane.QUESTION_MESSAGE,box,buttons,550);
        cancel.addActionListener(e->d.dispose());ok.addActionListener(e->{result[0]=field.getText();d.dispose();});
        d.getRootPane().setDefaultButton(ok);d.setVisible(true);return result[0];
    }
    public static int showOptionDialog(Component parent,Object message,String title,int optionType,int messageType,Icon icon,Object[] options,Object initialValue) {
        int[] result={JOptionPane.CLOSED_OPTION};
        JPanel items=new JPanel();items.setLayout(new BoxLayout(items,BoxLayout.Y_AXIS));items.setOpaque(false);
        JComponent description=content(message);description.setAlignmentX(Component.LEFT_ALIGNMENT);
        description.setPreferredSize(new Dimension(480,78));
        description.setMaximumSize(new Dimension(Integer.MAX_VALUE,78));
        items.add(description);items.add(Box.createVerticalStrut(16));
        JDialog[] holder={null};
        for(int i=0;i<options.length;i++) {
            final int index=i;
            String titleText=Objects.toString(options[i],"");
            GlossButton b=new GlossButton(titleText,i==0,false);
            b.setHorizontalAlignment(SwingConstants.CENTER);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE,47));
            b.setPreferredSize(new Dimension(470,47));
            items.add(b);items.add(Box.createVerticalStrut(7));
            b.addActionListener(e->{result[0]=index;holder[0].dispose();});
        }
        JDialog d=dialog(parent,title,messageType,items,footer(),600);holder[0]=d;
        d.setSize(Math.max(d.getWidth(),600),Math.max(d.getHeight(),Math.min(710,190+options.length*53+130)));
        d.setLocationRelativeTo(parent);d.setVisible(true);return result[0];
    }
}
