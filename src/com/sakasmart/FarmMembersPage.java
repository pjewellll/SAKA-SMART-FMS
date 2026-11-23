package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import static com.sakasmart.Main.*;

/** SAKA-SMART Farm Members V7 — full-page scrolling, rounded glossy layout. */
final class FarmMembersPage extends PageBase {
    private static final Color INK = new Color(8, 74, 42);
    private static final Color MUTED_TEXT = new Color(75, 117, 91);
    private static final Color BORDER = new Color(135, 198, 145);
    private static final Color MINT = new Color(235, 251, 230);
    private static final Color AMBER_TEXT = new Color(137, 84, 18);
    private static final Color DEEP = new Color(7, 107, 55);
    private static final Color RED_TEXT = new Color(171, 42, 58);

    FarmMembersPage(Main host) { super(host); }

    JComponent build() {
        if (!"FARM_OWNER".equals(role()))
            return blank("Access Denied", "Only the farm owner can manage farm membership.");
        List<Map<String,String>> pending = new ArrayList<>();
        List<Map<String,String>> staff = new ArrayList<>();
        List<Map<String,String>> owners = new ArrayList<>();
        for (Map<String,String> m : rows("users")) {
            String active=v(m,"active"), r=v(m,"role");
            if ("false".equalsIgnoreCase(active) && "VIEWER".equals(r)) pending.add(m);
            else if ("true".equalsIgnoreCase(active)) {
                if ("FARM_OWNER".equals(r)) owners.add(m);
                else if (!"SYSTEM_ADMIN".equals(r)) staff.add(m);
            }
        }
        Comparator<Map<String,String>> comparator=Comparator.comparing(m -> v(m,"full_name").toLowerCase(Locale.ROOT));
        pending.sort(comparator);staff.sort(comparator);owners.sort(comparator);

        JPanel page=new FarmScrollPage();page.setBorder(new EmptyBorder(14,18,24,18));
        JPanel metrics=transparent(new GridLayout(1,3,16,0));
        metrics.add(stat("Pending Requests",String.valueOf(pending.size()),"Awaiting your approval",new Color(251,237,228),"!"));
        metrics.add(stat("Active Staff",String.valueOf(staff.size()),"Approved farm workers",new Color(226,247,234),"+"));
        metrics.add(stat("Farm Owners",String.valueOf(owners.size()),"Active owner accounts",new Color(238,251,221),"✓"));
        addRow(page,metrics,124,16);
        addRow(page,sectionPanel("Requests to Join Your Farm", "Review each applicant, assign an appropriate role, or reject the request.", pendingBody(pending)), -1,16);
        addRow(page,sectionPanel("Your Farm Team", "Approved members and their current access levels.", teamBody(owners,staff)), -1,10);

        JScrollPane scroll=new JScrollPane(page,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        styleScroll(scroll.getVerticalScrollBar());
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
        return scroll;
    }

    private JComponent pendingBody(List<Map<String,String>> pending){
        JPanel body=vStackLocal();
        JPanel bar=transparent(new BorderLayout(8,0));
        bar.add(pill("Pending Applications   "+pending.size(),new Color(255,239,207),AMBER_TEXT),BorderLayout.WEST);
        JButton refresh=btn("Refresh",GREEN);refresh.addActionListener(e -> renderPage());
        JPanel right=transparent(new FlowLayout(FlowLayout.RIGHT,0,0));right.add(refresh);
        bar.add(right,BorderLayout.EAST);
        addRow(body,bar,44,12);
        if(pending.isEmpty())addRow(body,emptyState("No pending requests", "New applications to join your farm will appear here."),104,0);
        else for(Map<String,String> m:pending)addRow(body,pendingCard(m),144,12);
        return body;
    }

    private JComponent teamBody(List<Map<String,String>> owners,List<Map<String,String>> staff){
        JPanel body=vStackLocal();
        addRow(body,pill("Active Members   "+(owners.size()+staff.size()),new Color(225,247,218),INK),39,12);
        if(owners.isEmpty() && staff.isEmpty()){
            addRow(body,emptyState("No approved members yet", "Approved farm members will appear here."),104,0);
        }else{
            if(!owners.isEmpty()){
                addRow(body,dividerHeader("♛   Farm Owner"),32,8);
                for(Map<String,String> m:owners)addRow(body,activeCard(m,true),90,10);
            }
            if(!staff.isEmpty()){
                addRow(body,dividerHeader("♧   Approved Staff"),32,8);
                for(Map<String,String> m:staff)addRow(body,activeCard(m,false),90,10);
            }
        }
        return body;
    }

    private JComponent pendingCard(Map<String,String> m){
        JPanel content=transparent(new BorderLayout(0,13));content.setBorder(new EmptyBorder(15,18,16,18));
        content.add(identity(m),BorderLayout.CENTER);
        JPanel bottom=transparent(new BorderLayout(8,0));
        bottom.add(pill("Awaiting Approval",new Color(255,239,207),AMBER_TEXT),BorderLayout.WEST);
        JPanel buttons=transparent(new FlowLayout(FlowLayout.RIGHT,9,0));
        JButton approve=btn("Approve & Assign Role",GREEN),reject=btn("Reject",RED);
        approve.addActionListener(e -> approveMember(m));reject.addActionListener(e -> rejectMember(m));
        buttons.add(approve);buttons.add(reject);bottom.add(buttons,BorderLayout.EAST);
        content.add(bottom,BorderLayout.SOUTH);
        return glossyCard(content,new Color(246,252,243),BORDER);
    }

    private JComponent activeCard(Map<String,String> m,boolean owner){
        JPanel p=transparent(new BorderLayout(12,0));p.setBorder(new EmptyBorder(13,18,13,18));
        p.add(identity(m),BorderLayout.CENTER);
        JPanel badges=transparent(new FlowLayout(FlowLayout.RIGHT,9,10));
        badges.add(pill(owner?"♛  Owner":roleLabel(v(m,"role")),new Color(211,242,203),INK));
        badges.add(pill("✓  Active",new Color(219,251,222),INK));
        p.add(badges,BorderLayout.EAST);
        return glossyCard(p,new Color(244,252,240),BORDER);
    }

    private JComponent identity(Map<String,String> m){
        JPanel p=transparent(new BorderLayout(15,0));
        String name=v(m,"full_name").trim();if(name.isEmpty())name="Unnamed Member";
        String username=v(m,"username").trim();
        p.add(new CircularAvatar(initials(name)),BorderLayout.WEST);
        JPanel t=transparent(new GridLayout(2,1,0,3));
        JLabel n=label(name,16,true,INK),u=label("@"+username,12,false,MUTED_TEXT);
        n.setToolTipText(name);u.setToolTipText(username);t.add(n);t.add(u);
        p.add(t,BorderLayout.CENTER);return p;
    }

    private JComponent sectionPanel(String title,String subtitle,JComponent content){
        JPanel inner=transparent(new BorderLayout(0,12));inner.setBorder(new EmptyBorder(20,20,20,20));
        JPanel top=transparent(new GridLayout(2,1,0,3));top.add(label(title,21,true,INK));
        top.add(label(subtitle,13,false,MUTED_TEXT));
        inner.add(top,BorderLayout.NORTH);inner.add(content,BorderLayout.CENTER);
        return glossyCard(inner,new Color(241,252,241),BORDER);
    }

    private static final class FarmScrollPage extends JPanel implements Scrollable {
        FarmScrollPage(){super(new GridBagLayout());setOpaque(false);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(120,r.height-70);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static JPanel vStackLocal(){JPanel p=transparent(new GridBagLayout());return p;}
    private static JPanel transparent(LayoutManager manager){JPanel p=new JPanel(manager);p.setOpaque(false);return p;}
    private static void addRow(JPanel p,JComponent child,int height,int gap){
        if(height>0)child.setPreferredSize(new Dimension(Math.max(200,child.getPreferredSize().width),height));
        GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=p.getComponentCount();
        c.weightx=1;c.weighty=0;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.NORTHWEST;
        c.insets=new Insets(0,0,gap,0);p.add(child,c);
    }
    private static JLabel pill(String text,Color bg,Color fg){
        JLabel label=new JLabel("  "+text+"  ",SwingConstants.CENTER){
            protected void paintComponent(Graphics g){
                Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                q.setPaint(new GradientPaint(0,0,Color.WHITE,0,getHeight(),bg));
                q.fillRoundRect(1,1,getWidth()-3,getHeight()-3,getHeight()-3,getHeight()-3);
                q.setColor(new Color(137,202,141));q.drawRoundRect(1,1,getWidth()-3,getHeight()-3,getHeight()-3,getHeight()-3);
                q.dispose();super.paintComponent(g);
            }
        };label.setOpaque(false);label.setFont(new Font("Segoe UI",Font.BOLD,12));label.setForeground(fg);
        label.setBorder(new EmptyBorder(8,12,8,12));return label;
    }
    private static JComponent emptyState(String heading,String description){
        JPanel p=transparent(new GridBagLayout());JPanel text=transparent(new GridLayout(2,1,0,6));
        JLabel h=label(heading,17,true,INK),d=label(description,13,false,MUTED_TEXT);
        h.setHorizontalAlignment(SwingConstants.CENTER);d.setHorizontalAlignment(SwingConstants.CENTER);
        text.add(h);text.add(d);p.add(text);return glossyCard(p,new Color(250,255,249),BORDER);
    }
    private static JComponent dividerHeader(String s){
        JPanel p=transparent(new BorderLayout(12,0));p.add(pill(s,new Color(226,250,223),INK),BorderLayout.WEST);
        JSeparator line=new JSeparator();line.setForeground(new Color(164,215,168));p.add(line,BorderLayout.CENTER);return p;
    }
    private static final class CircularAvatar extends JComponent {
        private final String initials; CircularAvatar(String s){initials=s;setPreferredSize(new Dimension(58,58));}
        protected void paintComponent(Graphics g){Graphics2D q=(Graphics2D)g.create();
            q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int size=Math.min(getWidth(),getHeight())-4,x=(getWidth()-size)/2,y=(getHeight()-size)/2;
            q.setPaint(new GradientPaint(x,y,new Color(111,198,112),x,y+size,new Color(0,101,55)));
            q.fillOval(x,y,size,size);q.setStroke(new BasicStroke(2));q.setColor(Color.WHITE);q.drawOval(x+3,y+3,size-6,size-6);
            q.setFont(new Font("Segoe UI",Font.BOLD,17));q.setColor(Color.WHITE);
            FontMetrics fm=q.getFontMetrics();q.drawString(initials,(getWidth()-fm.stringWidth(initials))/2,(getHeight()-fm.getHeight())/2+fm.getAscent());q.dispose();
        }
    }
    private static JComponent glossyCard(JComponent child,Color tint,Color line){
        JPanel surface=new JPanel(new BorderLayout()){
            protected void paintComponent(Graphics g){Graphics2D q=(Graphics2D)g.create();
                q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();q.setColor(new Color(13,84,43,30));q.fillRoundRect(3,5,w-6,h-7,24,24);
                q.setPaint(new GradientPaint(0,0,Color.WHITE,0,Math.max(1,h),tint));q.fillRoundRect(1,1,w-4,h-6,24,24);
                q.setColor(line);q.setStroke(new BasicStroke(1.2f));q.drawRoundRect(1,1,w-4,h-6,24,24);
                q.setColor(new Color(255,255,255,200));q.drawRoundRect(4,4,w-10,h-12,20,20);q.dispose();super.paintComponent(g);
            }
        };surface.setOpaque(false);surface.setBorder(new EmptyBorder(5,6,8,6));surface.add(child,BorderLayout.CENTER);return surface;
    }
    private static void styleScroll(JScrollBar sb){sb.setPreferredSize(new Dimension(13,0));
        sb.setUI(new BasicScrollBarUI(){
            protected void configureScrollBarColors(){thumbColor=new Color(30,144,72);trackColor=new Color(226,246,222);}
            protected JButton createDecreaseButton(int o){return zero();}protected JButton createIncreaseButton(int o){return zero();}
            private JButton zero(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));return b;}
            protected void paintThumb(Graphics g,JComponent c,Rectangle r){if(r.isEmpty())return;Graphics2D q=(Graphics2D)g.create();
                q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                q.setPaint(new GradientPaint(r.x,r.y,new Color(100,209,119),r.x+r.width,r.y,new Color(7,112,61)));
                q.fillRoundRect(r.x+2,r.y+2,Math.max(1,r.width-4),Math.max(1,r.height-4),12,12);q.dispose();}
        });
    }

    private void approveMember(Map<String,String> member) {
        String name=v(member,"full_name").isBlank()?v(member,"username"):v(member,"full_name");
        String[] roles={"VIEWER","INVENTORY_STAFF","FARM_MANAGER","BOOKKEEPER"};
        JComboBox<String> choice=new JComboBox<>(roles);
        choice.setFont(new Font("Segoe UI",Font.BOLD,14));
        choice.setPreferredSize(new Dimension(310,38));
        choice.setSelectedItem("VIEWER");
        JTextArea explanation=new JTextArea(3,30);
        explanation.setEditable(false);explanation.setFocusable(false);
        explanation.setOpaque(false);explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setFont(new Font("Segoe UI",Font.PLAIN,12));
        explanation.setForeground(MUTED_TEXT);
        choice.addActionListener(e -> explanation.setText(roleDescription(Objects.toString(choice.getSelectedItem(),"VIEWER"))));
        explanation.setText(roleDescription("VIEWER"));
        JPanel panel=pane(new BorderLayout(0,9));
        panel.add(label("Assign a role to " + name,15,true,INK),BorderLayout.NORTH);
        JPanel center=pane(new BorderLayout(0,8));
        center.add(choice,BorderLayout.NORTH);center.add(explanation,BorderLayout.CENTER);
        panel.add(center,BorderLayout.CENTER);
        if(StyledDialogs.showConfirmDialog(frame,panel,"Approve Farm Member",
            JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String newRole=Objects.toString(choice.getSelectedItem(),"VIEWER");
        try {
            // ScopedStorage and PostgreSQL both verify the owner's own farm membership.
            store.decideMembership(v(user,"id"),v(member,"id"),newRole,true);
            store.log(username(),"Approved membership for "+v(member,"username"));
            renderPage();
        } catch(Exception ex){error(ex);}
    }

    private void rejectMember(Map<String,String> member) {
        String name=v(member,"full_name").isBlank()?v(member,"username"):v(member,"full_name");
        String question="Are you sure you want to reject the membership request from " + name + "?";
        if(StyledDialogs.showConfirmDialog(frame,question,"Reject Membership",
            JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try {
            store.decideMembership(v(user,"id"),v(member,"id"),"VIEWER",false);
            store.log(username(),"Rejected membership for "+v(member,"username"));
            renderPage();
        } catch(Exception ex){error(ex);}
    }

    private static String roleDescription(String role) {
        return switch(role){
            case "FARM_MANAGER" -> "Farm Manager can manage inventory, calendar, production, accounting, and sales.";
            case "INVENTORY_STAFF" -> "Inventory Staff can manage farm inventory and view permitted farm records.";
            case "BOOKKEEPER" -> "Bookkeeper can manage accounting and sales records.";
            default -> "Viewer can view permitted farm records but cannot modify them.";
        };
    }

    private static String roleLabel(String role) {
        return switch(role){
            case "FARM_OWNER" -> "OWNER";
            case "FARM_MANAGER" -> "FARM MANAGER";
            case "INVENTORY_STAFF" -> "INVENTORY STAFF";
            case "BOOKKEEPER" -> "BOOKKEEPER";
            default -> "VIEWER";
        };
    }

    private static String initials(String s){String[] a=s.trim().split("\\s+");if(a.length==0||a[0].isBlank())return "?";
        String f=a[0].substring(0,1).toUpperCase(Locale.ROOT);return a.length>1?f+a[a.length-1].substring(0,1).toUpperCase(Locale.ROOT):f;}
}
