package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.*;

/** Marketplace-only visual utilities. No database access, no new dependencies. */
final class MarketplaceVisuals {
    static final Color DARK=new Color(7,72,38), GREEN=new Color(0,113,49),
        LIGHT=new Color(240,252,239), OUTLINE=new Color(132,199,142);
    private MarketplaceVisuals(){}

    static JPanel roundedPanel(LayoutManager layout,Color background,int radius){
        return new JPanel(layout){
            {setOpaque(false);}
            @Override protected void paintComponent(Graphics raw){
                Graphics2D g=(Graphics2D)raw.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth()-1,h=getHeight()-2;
                g.setPaint(new GradientPaint(0,0,Color.WHITE,0,Math.max(h,1),background));
                g.fill(new RoundRectangle2D.Float(1,1,w-2,h-2,radius,radius));
                g.setColor(OUTLINE);g.setStroke(new BasicStroke(1.4f));
                g.draw(new RoundRectangle2D.Float(1,1,w-2,h-2,radius,radius));
                g.setColor(new Color(255,255,255,190));g.draw(new RoundRectangle2D.Float(4,4,w-8,h-8,radius-4,radius-4));
                g.dispose();super.paintComponent(raw);
            }
        };
    }
    static void scrollbar(JScrollPane scroll){
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(250,254,249));
        scroll.getVerticalScrollBar().setUnitIncrement(25);
        scroll.getHorizontalScrollBar().setUnitIncrement(25);
        scroll.getVerticalScrollBar().setUI(new GreenScrollbar());
        scroll.getHorizontalScrollBar().setUI(new GreenScrollbar());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(13,0));
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0,13));
    }
    private static final class GreenScrollbar extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors(){thumbColor=GREEN;trackColor=new Color(224,243,226);}
        @Override protected JButton createDecreaseButton(int direction){return zeroButton();}
        @Override protected JButton createIncreaseButton(int direction){return zeroButton();}
        private JButton zeroButton(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));return b;}
        @Override protected void paintTrack(Graphics g,JComponent c,Rectangle r){
            Graphics2D a=(Graphics2D)g.create();a.setColor(trackColor);
            a.fillRoundRect(r.x+2,r.y+2,Math.max(0,r.width-4),Math.max(0,r.height-4),11,11);a.dispose();
        }
        @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r){
            if(r.isEmpty()||!scrollbar.isEnabled())return;
            Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            a.setPaint(new GradientPaint(r.x,r.y,new Color(50,176,82),r.x+r.width,r.y+r.height,DARK));
            a.fillRoundRect(r.x+2,r.y+1,Math.max(0,r.width-4),Math.max(0,r.height-2),11,11);a.dispose();
        }
    }
    static JButton greenButton(String title){
        JButton b=new JButton(title){
            @Override protected void paintComponent(Graphics raw){
                Graphics2D g=(Graphics2D)raw.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth()-1,h=getHeight()-1;
                g.setPaint(new GradientPaint(0,1,new Color(72,203,91),0,h,new Color(0,96,40)));
                g.fillRoundRect(1,1,w-2,h-3,17,17);
                g.setColor(new Color(184,243,174));g.setStroke(new BasicStroke(1.6f));g.drawRoundRect(1,1,w-2,h-3,17,17);
                g.setColor(new Color(255,255,255,95));g.drawLine(11,5,w-11,5);
                g.dispose();super.paintComponent(raw);
            }
        };
        b.setContentAreaFilled(false);b.setOpaque(false);b.setFocusPainted(false);
        b.setForeground(Color.WHITE);b.setFont(new Font("Segoe UI",Font.BOLD,15));
        b.setBorder(new EmptyBorder(10,18,10,18));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    static int confirm(Component parent,String title,String intro,JComponent content){
        JPanel body=roundedPanel(new BorderLayout(0,12),new Color(239,251,237),24);
        body.setBorder(new EmptyBorder(17,19,17,19));
        JLabel explanation=new JLabel(intro);explanation.setFont(new Font("Segoe UI",Font.PLAIN,14));
        explanation.setForeground(DARK);body.add(explanation,BorderLayout.NORTH);
        body.add(content,BorderLayout.CENTER);
        return StyledDialogs.showConfirmDialog(parent,body,title,JOptionPane.OK_CANCEL_OPTION);
    }
    /** Compact vector icon; avoids missing-glyph squares on Windows fonts. */
    private static JComponent statusIcon(int type){
        final boolean error=type==JOptionPane.ERROR_MESSAGE;
        final boolean warn=type==JOptionPane.WARNING_MESSAGE;
        final Color tone=error?new Color(189,48,55):warn?new Color(211,135,28):new Color(12,147,62);
        return new JComponent(){
            {setPreferredSize(new Dimension(58,58));setMinimumSize(new Dimension(58,58));}
            @Override protected void paintComponent(Graphics raw){
                Graphics2D g=(Graphics2D)raw.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int x=(getWidth()-48)/2,y=(getHeight()-48)/2;
                g.setPaint(new GradientPaint(x,y,tone.brighter(),x,y+48,tone.darker()));
                g.fillOval(x,y,48,48);
                g.setColor(new Color(255,255,255,225));g.setStroke(new BasicStroke(2f));
                g.drawOval(x+2,y+2,44,44);
                g.setColor(Color.WHITE);g.setStroke(new BasicStroke(5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                if(error){g.drawLine(x+16,y+16,x+32,y+32);g.drawLine(x+32,y+16,x+16,y+32);}
                else if(warn){g.drawLine(x+24,y+13,x+24,y+27);g.fillOval(x+21,y+34,6,6);}
                else {g.drawLine(x+12,y+24,x+21,y+33);g.drawLine(x+21,y+33,x+36,y+16);}
                g.dispose();
            }
        };
    }
    static void notice(Component parent,String title,String message,int type){
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(parent),title,Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel shell=new JPanel(new BorderLayout());shell.setOpaque(false);
        shell.setBorder(new EmptyBorder(9,9,13,13));
        JPanel root=roundedPanel(new BorderLayout(0,15),new Color(226,247,224),30);
        root.setBorder(new EmptyBorder(24,26,20,26));
        Color tone=type==JOptionPane.ERROR_MESSAGE?new Color(158,43,50):
                   type==JOptionPane.WARNING_MESSAGE?new Color(150,96,20):DARK;
        JPanel heading=new JPanel(new BorderLayout(16,0));heading.setOpaque(false);
        heading.add(statusIcon(type),BorderLayout.WEST);
        JLabel titleLabel=new JLabel("<html><div style='width:330px'>"+escapeHtml(title)+"</div></html>");
        titleLabel.setForeground(tone);titleLabel.setFont(new Font("Segoe UI",Font.BOLD,21));
        heading.add(titleLabel,BorderLayout.CENTER);root.add(heading,BorderLayout.NORTH);
        JTextArea msg=new JTextArea(message);msg.setEditable(false);msg.setOpaque(false);
        msg.setLineWrap(true);msg.setWrapStyleWord(true);msg.setFocusable(false);
        msg.setFont(new Font("Segoe UI",Font.BOLD,15));msg.setForeground(new Color(17,79,43));
        msg.setColumns(39);msg.setRows(Math.min(5,Math.max(2,(message.length()+48)/49)));
        root.add(msg,BorderLayout.CENTER);
        JButton okay=greenButton("Okay");okay.addActionListener(e->dialog.dispose());
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));actions.setOpaque(false);actions.add(okay);
        root.add(actions,BorderLayout.SOUTH);shell.add(root,BorderLayout.CENTER);
        dialog.setContentPane(shell);dialog.pack();
        dialog.setMinimumSize(new Dimension(510,225));dialog.setLocationRelativeTo(parent);
        dialog.getRootPane().setDefaultButton(okay);
        // Escape closes this notice, matching ordinary native dialogs.
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),
            KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        try { dialog.setBackground(new Color(0,0,0,0)); }
        catch(UnsupportedOperationException ignored){ dialog.setBackground(new Color(240,252,239)); }
        dialog.setVisible(true);
    }
    private static String escapeHtml(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
    /** Rounded external table border; preserves viewport and all live JTable behavior. */
    static void roundedTable(JScrollPane scroll){
        scroll.setBorder(new AbstractBorder(){
            @Override public Insets getBorderInsets(Component c){return new Insets(2,2,2,2);}
            @Override public Insets getBorderInsets(Component c,Insets i){i.set(2,2,2,2);return i;}
            @Override public void paintBorder(Component c,Graphics raw,int x,int y,int w,int h){
                Graphics2D g=(Graphics2D)raw.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(95,170,111));g.setStroke(new BasicStroke(1.7f));
                g.drawRoundRect(x+1,y+1,w-3,h-3,19,19);g.dispose();
            }
        });
        scroll.setBackground(new Color(248,254,248));
        scroll.getViewport().setBackground(new Color(248,254,248));
        scroll.setViewportBorder(new EmptyBorder(0,2,0,2));
    }
    static void emptySales(JScrollPane scroll,JTable table){
        if(table.getRowCount()!=0)return;
        JPanel empty=new JPanel(new GridBagLayout());empty.setBackground(new Color(248,254,247));
        JPanel content=new JPanel();content.setOpaque(false);content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("No Sales Recorded Yet");title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setForeground(DARK);title.setFont(new Font("Segoe UI",Font.BOLD,20));
        JLabel detail=new JLabel("Your completed sales transactions will appear here.");
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);detail.setForeground(new Color(69,111,77));
        detail.setFont(new Font("Segoe UI",Font.BOLD,14));
        JLabel hint=new JLabel("Use Record Sale above to add your first transaction.");
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);hint.setForeground(GREEN);
        hint.setFont(new Font("Segoe UI",Font.BOLD,13));
        content.add(title);content.add(Box.createVerticalStrut(12));content.add(detail);
        content.add(Box.createVerticalStrut(8));content.add(hint);empty.add(content);
        scroll.setViewportView(empty);
        scroll.setColumnHeaderView(table.getTableHeader());
    }
}
