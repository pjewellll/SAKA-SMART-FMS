package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import static com.sakasmart.Main.*;

/**
 * SAKA-SMART Farm Members V2.
 * Visual-only redesign; uses existing farm-scoped Storage and membership decisions.
 * No schema changes, credentials, or additional dependencies.
 */
final class FarmMembersPage extends PageBase {
    private static final Color INK = new Color(9, 76, 47);
    private static final Color MUTED_TEXT = new Color(78, 119, 91);
    private static final Color BORDER = new Color(126, 192, 139);
    private static final Color MINT = new Color(231, 250, 229);
    private static final Color RED_TEXT = new Color(176, 42, 59);
    private static final Color AMBER_TEXT = new Color(135, 85, 16);

    FarmMembersPage(Main host) { super(host); }

    JComponent build() {
        if (!"FARM_OWNER".equals(role())) {
            return blank("Access Denied", "Only the farm owner can manage farm membership.");
        }

        List<Map<String,String>> members = rows("users");
        List<Map<String,String>> pending = new ArrayList<>();
        List<Map<String,String>> approvedStaff = new ArrayList<>();
        List<Map<String,String>> owners = new ArrayList<>();
        for (Map<String,String> member : members) {
            String status = v(member,"active");
            String memberRole = v(member,"role");
            if ("false".equalsIgnoreCase(status) && "VIEWER".equals(memberRole)) {
                pending.add(member);
            } else if ("true".equalsIgnoreCase(status)) {
                if ("FARM_OWNER".equals(memberRole)) owners.add(member);
                else if (!"SYSTEM_ADMIN".equals(memberRole)) approvedStaff.add(member);
            }
        }
        Comparator<Map<String,String>> byName = Comparator.comparing(
            r -> v(r,"full_name").toLowerCase(Locale.ROOT));
        pending.sort(byName); approvedStaff.sort(byName); owners.sort(byName);

        JPanel page = stack();
        JPanel metrics = pane(new GridLayout(1,3,12,0));
        metrics.add(stat("Pending Requests", Integer.toString(pending.size()),
            "Awaiting your approval", LIGHT, "!"));
        metrics.add(stat("Active Staff", Integer.toString(approvedStaff.size()),
            "Approved farm workers", new Color(227,247,235), "✓"));
        metrics.add(stat("Farm Owners", Integer.toString(owners.size()),
            "Active owner accounts", new Color(241,249,223), "♙"));
        addBlock(page,metrics,116);

        // Pending and active sections are independent scrollable card lists.
        addBlock(page, section("Requests to Join Your Farm",
            "Review each applicant, assign an appropriate role, or reject the request",
            pendingContent(pending)), Math.max(260, Math.min(460, pending.size()*139 + 136)));
        addBlock(page, section("Your Farm Team",
            "Approved members and their current access levels",
            teamContent(owners, approvedStaff)),
            Math.max(230, Math.min(440,(owners.size()+approvedStaff.size())*108 + 126)));
        return page;
    }

    private JComponent pendingContent(List<Map<String,String>> pending) {
        JPanel body = pane(new BorderLayout(0,10));
        JPanel top = pane(new BorderLayout(10,0));
        top.add(badge("PENDING APPLICATIONS  " + pending.size(),
            new Color(255,243,216), AMBER_TEXT, new Color(238,194,118)), BorderLayout.WEST);
        JButton refresh = secondary("Refresh");
        refresh.addActionListener(e -> renderPage());
        JPanel right = pane(new FlowLayout(FlowLayout.RIGHT,0,0));
        right.add(refresh);
        top.add(right,BorderLayout.EAST);
        body.add(top,BorderLayout.NORTH);
        if(pending.isEmpty()) {
            body.add(emptyState("No pending requests",
                "New applications to join your farm will appear here."),BorderLayout.CENTER);
        } else {
            JPanel cards = verticalCards();
            for (Map<String,String> member : pending) {
                addCard(cards, pendingCard(member));
            }
            body.add(scroller(cards),BorderLayout.CENTER);
        }
        return body;
    }

    private JComponent teamContent(List<Map<String,String>> owners,
                                    List<Map<String,String>> staff) {
        JPanel content = pane(new BorderLayout(0,10));
        JPanel top = pane(new BorderLayout());
        top.add(badge("ACTIVE MEMBERS  " + (owners.size()+staff.size()),
            new Color(225,248,232), INK, BORDER),BorderLayout.WEST);
        content.add(top,BorderLayout.NORTH);
        if(owners.isEmpty() && staff.isEmpty()) {
            content.add(emptyState("No active farm members",
                "Approved members will appear here."),BorderLayout.CENTER);
        } else {
            JPanel cards = verticalCards();
            if(!owners.isEmpty()) {
                addCard(cards, dividerHeader("FARM OWNER"));
                for(Map<String,String> member : owners) addCard(cards,activeCard(member,true));
            }
            if(!staff.isEmpty()) {
                addCard(cards, dividerHeader("APPROVED STAFF"));
                for(Map<String,String> member : staff) addCard(cards,activeCard(member,false));
            }
            content.add(scroller(cards),BorderLayout.CENTER);
        }
        return content;
    }

    private JComponent pendingCard(Map<String,String> member) {
        JPanel inner = pane(new BorderLayout(10,7));
        inner.setBorder(new EmptyBorder(12,15,13,15));
        inner.add(identity(member),BorderLayout.CENTER);
        JPanel lower = pane(new BorderLayout(8,0));
        lower.add(badge("AWAITING APPROVAL", new Color(255,242,211),
            AMBER_TEXT,new Color(234,194,116)),BorderLayout.WEST);
        JPanel actions = pane(new FlowLayout(FlowLayout.RIGHT,7,0));
        JButton approve = btn("Approve & Assign Role",GREEN);
        JButton reject = btn("Reject",RED);
        approve.setToolTipText("Approve this applicant and choose their farm access role");
        reject.setToolTipText("Reject this applicant's request to join the farm");
        approve.addActionListener(e -> approveMember(member));
        reject.addActionListener(e -> rejectMember(member));
        actions.add(approve); actions.add(reject);
        lower.add(actions,BorderLayout.EAST);
        inner.add(divider(),BorderLayout.SOUTH);
        JPanel wrapper = pane(new BorderLayout(0,7));
        wrapper.add(inner,BorderLayout.CENTER);
        wrapper.add(lower,BorderLayout.SOUTH);
        wrapper.setBorder(new EmptyBorder(0,0,4,0));
        return glossyCard(wrapper, new Color(244,251,242), BORDER);
    }

    private JComponent activeCard(Map<String,String> member, boolean owner) {
        JPanel content = pane(new BorderLayout(12,0));
        content.setBorder(new EmptyBorder(11,14,12,14));
        content.add(identity(member),BorderLayout.CENTER);
        JPanel right = pane(new GridLayout(2,1,0,4));
        JLabel roleBadge = badge(roleLabel(v(member,"role")),
            owner?new Color(228,244,211):new Color(227,247,232),
            INK, owner?new Color(181,206,134):BORDER);
        JLabel activeBadge = badge("ACTIVE",new Color(232,249,236),INK,BORDER);
        right.add(roleBadge);right.add(activeBadge);
        content.add(right,BorderLayout.EAST);
        return glossyCard(content,new Color(246,253,246),BORDER);
    }

    private JComponent identity(Map<String,String> member) {
        JPanel identity = pane(new BorderLayout(12,0));
        String fullName = v(member,"full_name").trim();
        if(fullName.isEmpty()) fullName = "Unnamed Member";
        String username = v(member,"username").trim();
        String initials = initials(fullName);
        JLabel avatar = new JLabel(initials,SwingConstants.CENTER);
        avatar.setFont(new Font("Segoe UI",Font.BOLD,16));
        avatar.setForeground(new Color(9,94,53));
        avatar.setOpaque(true);
        avatar.setBackground(new Color(215,241,214));
        avatar.setBorder(BorderFactory.createLineBorder(new Color(132,192,146),1));
        avatar.setPreferredSize(new Dimension(46,46));
        identity.add(avatar,BorderLayout.WEST);
        JPanel text = pane(new GridLayout(2,1,0,2));
        JLabel name = label(fullName,15,true,INK);
        JLabel secondary = label("@"+username,12,false,MUTED_TEXT);
        name.setToolTipText(fullName);
        secondary.setToolTipText(username);
        text.add(name);text.add(secondary);
        identity.add(text,BorderLayout.CENTER);
        return identity;
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

    private static String initials(String s){
        String[] parts=s.trim().split("\\s+");
        if(parts.length==0 || parts[0].isBlank())return "?";
        String a=parts[0].substring(0,1).toUpperCase(Locale.ROOT);
        return parts.length>1?a+parts[parts.length-1].substring(0,1).toUpperCase(Locale.ROOT):a;
    }

    private static JPanel verticalCards(){
        JPanel cards = new JPanel();
        cards.setOpaque(false);
        cards.setLayout(new BoxLayout(cards,BoxLayout.Y_AXIS));
        cards.setBorder(new EmptyBorder(4,3,8,7));
        return cards;
    }
    private static void addCard(JPanel parent,JComponent child){
        child.setAlignmentX(Component.LEFT_ALIGNMENT);
        child.setMaximumSize(new Dimension(Integer.MAX_VALUE,child.getPreferredSize().height));
        parent.add(child);parent.add(Box.createVerticalStrut(10));
    }
    private static JScrollPane scroller(JComponent view){
        JScrollPane sp=new JScrollPane(view,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setOpaque(false);sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(20);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(12,0));
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI(){
            @Override protected void configureScrollBarColors(){thumbColor=new Color(25,135,73);trackColor=new Color(221,245,224);}
            @Override protected JButton createDecreaseButton(int orientation){return zeroButton();}
            @Override protected JButton createIncreaseButton(int orientation){return zeroButton();}
            private JButton zeroButton(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));b.setMaximumSize(new Dimension(0,0));return b;}
            @Override protected void paintThumb(Graphics g,JComponent c,Rectangle b){
                if(b.isEmpty() || !scrollbar.isEnabled())return;
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(b.x,b.y,new Color(89,195,111),b.x+b.width,b.y,new Color(11,106,61)));
                g2.fillRoundRect(b.x+2,b.y+2,Math.max(1,b.width-4),Math.max(1,b.height-4),12,12);
                g2.dispose();
            }
        });
        return sp;
    }

    private static JComponent emptyState(String title,String detail){
        JPanel content = pane(new GridBagLayout());
        JPanel lines=pane(new GridLayout(2,1,0,5));
        JLabel t=label(title,16,true,INK);t.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel d=label(detail,12,false,MUTED_TEXT);d.setHorizontalAlignment(SwingConstants.CENTER);
        lines.add(t);lines.add(d);content.add(lines);
        return glossyCard(content, new Color(241,251,241),BORDER);
    }
    private static JComponent dividerHeader(String title){
        JPanel p=pane(new BorderLayout(8,0));
        JLabel l=label(title,11,true,MUTED_TEXT);
        p.add(l,BorderLayout.WEST);p.add(divider(),BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(3,2,2,2));
        return p;
    }
    private static JComponent divider(){
        JComponent line=new JComponent(){
            @Override protected void paintComponent(Graphics g){
                Graphics2D x=(Graphics2D)g.create();
                x.setPaint(new GradientPaint(0,0,new Color(81,173,100),getWidth(),0,new Color(207,238,207)));
                x.fillRoundRect(0,Math.max(0,getHeight()/2),getWidth(),2,2,2);
                x.dispose();
            }
        };
        line.setPreferredSize(new Dimension(1,9));return line;
    }
    private static JLabel badge(String text,Color bg,Color fg,Color outline){
        JLabel l=new JLabel("  "+text+"  ",SwingConstants.CENTER);
        l.setFont(new Font("Segoe UI",Font.BOLD,11));l.setOpaque(true);
        l.setBackground(bg);l.setForeground(fg);
        l.setBorder(new CompoundBorder(new LineBorder(outline,1,true),new EmptyBorder(5,7,5,7)));
        return l;
    }
    private static JComponent glossyCard(JComponent child,Color tint,Color line){
        JPanel surface=new JPanel(new BorderLayout()){
            @Override protected void paintComponent(Graphics g){
                Graphics2D x=(Graphics2D)g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                x.setColor(new Color(18,85,46,30));x.fillRoundRect(3,5,w-6,h-7,18,18);
                x.setPaint(new GradientPaint(0,1,new Color(255,255,255),0,Math.max(1,h),tint));
                x.fillRoundRect(1,1,w-4,h-6,18,18);
                x.setStroke(new BasicStroke(1.15f));x.setColor(line);
                x.drawRoundRect(1,1,w-4,h-6,18,18);
                x.setColor(new Color(255,255,255,200));x.drawRoundRect(3,3,w-8,h-10,15,15);
                x.dispose();super.paintComponent(g);
            }
        };
        surface.setOpaque(false);
        surface.setBorder(new EmptyBorder(4,4,7,4));
        surface.add(child,BorderLayout.CENTER);
        return surface;
    }
}
