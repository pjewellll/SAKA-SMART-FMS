package com.sakasmart;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import static com.sakasmart.Main.*;

/** SAKA-SMART Users page UI. Uses shared actions, storage and styling from the application context. */
final class UsersPage extends PageBase {
    UsersPage(Main host) { super(host); }
    JComponent build(){
        if(!admin())return blank("Access Denied","Only system administrators can manage user accounts.");
        boolean requestsSelected="requests".equals(host.usersTab);
        JPanel content=requestsSelected?(JPanel)passwordRequestsPage():userAccountsPage();
        JButton accounts=btn("User Accounts",requestsSelected?new Color(99,128,111):GREEN);
        JButton requests=btn("Password Requests",requestsSelected?GREEN:new Color(99,128,111));
        accounts.addActionListener(e->{host.usersTab="accounts";renderPage();});
        requests.addActionListener(e->{host.usersTab="requests";renderPage();});
        JPanel tabs=pane(new FlowLayout(FlowLayout.LEFT,9,3));
        tabs.add(accounts);tabs.add(requests);
        JComponent top=section("User Management",
            "Manage registered accounts and identity-verified password assistance requests",tabs);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setPreferredSize(new Dimension(250,151));
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE,151));
        content.add(spacer(13),0);
        content.add(top,0);
        return content;
    }

    /** Option B -- dark emerald, high-contrast premium dialogs. UI only; Storage owns recovery logic. */
    private static final Color POPUP_WHITE = new Color(248, 255, 246);
    private static final Color POPUP_PALE = new Color(211, 237, 221);
    private static final Color POPUP_MINT = new Color(175, 241, 185);
    private static final Color POPUP_AMBER = new Color(255, 213, 145);

    private static class PremiumDialogSurface extends JPanel {
        PremiumDialogSurface(LayoutManager layout) { super(layout); setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D x = (Graphics2D) g.create();
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // Offset shadow creates a floating 3D glass-card effect.
            x.setColor(new Color(0, 24, 14, 105));
            x.fillRoundRect(11, 13, w - 20, h - 23, 38, 38);
            // Dark forest green / emerald gradient: readable behind light text.
            x.setPaint(new LinearGradientPaint(7, 6, w - 17, h - 24,
                    new float[]{0f, .47f, 1f},
                    new Color[]{new Color(7, 49, 35), new Color(13, 83, 57), new Color(9, 64, 43)}));
            x.fillRoundRect(5, 4, w - 19, h - 22, 36, 36);
            // Subtle glass highlight, kept low-opacity for text contrast.
            x.setPaint(new GradientPaint(0, 5, new Color(170, 244, 194, 45),
                    0, h * .66f, new Color(255, 255, 255, 0)));
            x.fillRoundRect(7, 6, w - 23, Math.max(20, (h - 28) / 2), 33, 33);
            x.setStroke(new BasicStroke(2f));
            x.setColor(new Color(181, 241, 184, 200));
            x.drawRoundRect(6, 5, w - 21, h - 24, 35, 35);
            x.setStroke(new BasicStroke(1f));
            x.setColor(new Color(255, 255, 255, 54));
            x.drawRoundRect(10, 9, w - 29, h - 32, 30, 30);
            x.dispose();
            super.paintComponent(g);
        }
    }

    /** Semi-transparent dark inset to group important information. */
    private static class PremiumInset extends JPanel {
        PremiumInset(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(new EmptyBorder(17, 18, 17, 18));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D x = (Graphics2D) g.create();
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            x.setColor(new Color(0, 31, 20, 115));
            x.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            x.setStroke(new BasicStroke(1.2f));
            x.setColor(new Color(171, 229, 176, 105));
            x.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 21, 21);
            x.dispose();
            super.paintComponent(g);
        }
    }

    private static class PremiumCheckIcon implements Icon {
        private final boolean selected;
        PremiumCheckIcon(boolean selected) { this.selected = selected; }
        @Override public int getIconWidth() { return 23; }
        @Override public int getIconHeight() { return 21; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D a = (Graphics2D) g.create();
            a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            a.setColor(selected ? new Color(139, 237, 149) : new Color(17, 70, 49));
            a.fillRoundRect(x + 1, y + 2, 17, 17, 5, 5);
            a.setStroke(new BasicStroke(1.7f));
            a.setColor(new Color(195, 243, 199));
            a.drawRoundRect(x + 1, y + 2, 17, 17, 5, 5);
            if (selected) {
                a.setColor(new Color(8, 67, 39));
                a.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                a.drawLine(x + 5, y + 10, x + 9, y + 14);
                a.drawLine(x + 9, y + 14, x + 15, y + 6);
            }
            a.dispose();
        }
    }

    private JButton premiumButton(String text, boolean primary) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                Color top = primary ? new Color(53, 204, 105) : new Color(223, 248, 220);
                Color bottom = primary ? new Color(11, 116, 63) : new Color(157, 212, 171);
                if (getModel().isPressed()) { top = top.darker(); bottom = bottom.darker(); }
                else if (getModel().isRollover()) { top = top.brighter(); bottom = bottom.brighter(); }
                x.setColor(new Color(0, 24, 15, 110));
                x.fillRoundRect(2, 5, w - 4, h - 7, 16, 16);
                x.setPaint(new GradientPaint(0, 1, top, 0, h - 6, bottom));
                x.fillRoundRect(1, 1, w - 3, h - 8, 16, 16);
                x.setPaint(new GradientPaint(0, 2, new Color(255, 255, 255, 135),
                        0, h * .53f, new Color(255, 255, 255, 0)));
                x.fillRoundRect(3, 3, w - 7, Math.max(3, h / 2), 14, 14);
                x.setColor(new Color(255, 255, 255, 170));
                x.drawRoundRect(2, 2, w - 5, h - 10, 15, 15);
                x.dispose();
                super.paintComponent(g);
            }
        };
        b.setForeground(primary ? Color.WHITE : new Color(9, 60, 38));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setBorder(new EmptyBorder(12, 20, 15, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JDialog premiumDialog(String title, int width, int height, JPanel body) {
        JDialog dialog = new JDialog(frame, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        PremiumDialogSurface surface = new PremiumDialogSurface(new BorderLayout());
        surface.setBorder(new EmptyBorder(33, 38, 39, 45));
        surface.add(body, BorderLayout.CENTER);
        dialog.setContentPane(surface);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(frame);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return dialog;
    }

    private static JPanel premiumHeading(String eyebrow, String title, String subtitle) {
        JPanel head = pane(new GridLayout(3, 1, 0, 6));
        head.add(label(eyebrow, 12, true, POPUP_MINT));
        head.add(label(title, 25, true, POPUP_WHITE));
        head.add(label(subtitle, 13, false, POPUP_PALE));
        return head;
    }

    private boolean showPremiumIdentityDialog(String account) {
        final boolean[] approved = {false};
        JPanel body = pane(new BorderLayout(0, 17));
        body.add(premiumHeading("SAKA-SMART  /  ACCOUNT SECURITY", "Verify Account Holder",
                "Confirm identity before issuing a recovery code."), BorderLayout.NORTH);

        PremiumInset center = new PremiumInset(new BorderLayout(0, 13));
        JPanel details = pane(new GridLayout(2, 1, 0, 6));
        details.add(label("ACCOUNT:  " + account, 17, true, POPUP_WHITE));
        details.add(label("Verify in person or through a trusted contact.", 13, false, POPUP_PALE));
        center.add(details, BorderLayout.NORTH);

        JPanel verification = pane(new BorderLayout(0, 12));
        JCheckBox verified = new JCheckBox("I have personally verified the account holder's identity.");
        verified.setOpaque(false);
        verified.setFont(new Font("Segoe UI", Font.BOLD, 13));
        verified.setForeground(POPUP_WHITE);
        verified.setIcon(new PremiumCheckIcon(false));
        verified.setSelectedIcon(new PremiumCheckIcon(true));
        verified.setIconTextGap(9);
        verified.setFocusPainted(false);
        verified.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        verification.add(verified, BorderLayout.NORTH);
        JLabel warning = label("Identity verification is required to continue.", 12, true, POPUP_AMBER);
        verification.add(warning, BorderLayout.SOUTH);
        center.add(verification, BorderLayout.CENTER);
        body.add(center, BorderLayout.CENTER);

        JPanel footer = pane(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        JButton cancel = premiumButton("Cancel", false);
        JButton accept = premiumButton("Approve Request  →", true);
        footer.add(cancel);
        footer.add(accept);
        body.add(footer, BorderLayout.SOUTH);
        JDialog d = premiumDialog("Verify Identity", 670, 465, body);
        cancel.addActionListener(e -> d.dispose());
        accept.addActionListener(e -> {
            if (!verified.isSelected()) {
                warning.setText("Please verify identity before approving.");
                warning.setForeground(new Color(255, 169, 141));
                return;
            }
            approved[0] = true;
            d.dispose();
        });
        d.getRootPane().setDefaultButton(accept);
        d.setVisible(true);
        return approved[0];
    }

    private void showPremiumRecoveryCodeDialog(String code) {
        JPanel body = pane(new BorderLayout(0, 18));
        body.add(premiumHeading("SAKA-SMART  /  ACCOUNT RECOVERY", "Recovery Code Generated",
                "Share this code only with the verified account holder."), BorderLayout.NORTH);

        PremiumInset center = new PremiumInset(new BorderLayout(0, 13));
        JTextField codeField = new JTextField(code);
        codeField.setEditable(false);
        codeField.setFocusable(true);
        codeField.setHorizontalAlignment(JTextField.CENTER);
        codeField.setFont(new Font("Consolas", Font.BOLD, 27));
        codeField.setForeground(new Color(8, 64, 37));
        codeField.setBackground(new Color(237, 255, 231));
        codeField.setBorder(new CompoundBorder(new LineBorder(new Color(139, 223, 154), 2, true),
                new EmptyBorder(12, 14, 12, 14)));
        codeField.setPreferredSize(new Dimension(330, 64));
        center.add(codeField, BorderLayout.NORTH);

        JPanel details = pane(new GridLayout(3, 1, 0, 7));
        details.add(label("Expires in " + RecoveryCodes.LIFETIME_MINUTES + " minutes  •  " +
                RecoveryCodes.MAX_ATTEMPTS + " attempts maximum", 13, true, POPUP_PALE));
        details.add(label("IMPORTANT: This code is displayed only once.", 13, true, POPUP_AMBER));
        JLabel copied = label(" ", 12, false, POPUP_MINT);
        details.add(copied);
        center.add(details, BorderLayout.CENTER);
        body.add(center, BorderLayout.CENTER);

        JPanel footer = pane(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        JButton copy = premiumButton("Copy Code", false);
        JButton close = premiumButton("Done  ✓", true);
        footer.add(copy);
        footer.add(close);
        body.add(footer, BorderLayout.SOUTH);
        JDialog d = premiumDialog("Recovery Code Issued", 670, 470, body);
        copy.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(code), null);
                copied.setText("Copied! Share privately and clear clipboard afterward.");
            } catch (IllegalStateException ex) {
                copied.setText("Clipboard is busy. Select and copy the code manually.");
            }
        });
        close.addActionListener(e -> d.dispose());
        d.getRootPane().setDefaultButton(close);
        d.setVisible(true);
    }

    /** Administrator-only queue for in-person identity-verified password recovery. */
    private JComponent passwordRequestsPage(){
        if(!admin())return blank("Access Denied","Only system administrators can review recovery requests.");
        JPanel body=stack();
        List<Map<String,String>> all=rows("password_reset_requests");
        long pending=all.stream().filter(x->"PENDING".equals(v(x,"status"))).count();
        long approved=all.stream().filter(x->"APPROVED".equals(v(x,"status"))).count();
        addBlock(body,horizontal(stat("Pending Requests",Long.toString(pending),"Waiting for identity check",LIGHT,"!"),
            stat("Codes Issued",Long.toString(approved),"May expire after 20 minutes",new Color(230,244,235),"✓")),105);
        TableParts table=tableView("password_reset_requests",all,
            List.of("id","username","status","requested_at","expires_at","handled_by"),"Search recovery requests...");
        JPanel view=pane(new BorderLayout(0,11));
        JPanel bar=pane(new FlowLayout(FlowLayout.LEFT,9,0));
        JButton approve=btn("Verify Identity & Approve",GREEN);
        JButton reject=btn("Reject Request",RED);
        JButton refresh=secondary("Refresh Requests");
        approve.addActionListener(e->{
            Map<String,String> item=table.selected();
            if(item==null){info("Select a request first.");return;}
            if(!"PENDING".equals(v(item,"status"))){info("Select a PENDING request.");return;}
            if(!showPremiumIdentityDialog(v(item,"username")))return;
            try{
                String code=store.approvePasswordReset(v(item,"id"),username());
                store.log(username(),"Approved password assistance request #"+v(item,"id"));
                showPremiumRecoveryCodeDialog(code);
                renderPage();
            }catch(Exception ex){error(ex);}
        });
        reject.addActionListener(e->{
            Map<String,String> item=table.selected();
            if(item==null || !"PENDING".equals(v(item,"status"))){info("Select a PENDING request.");return;}
            if(StyledDialogs.showConfirmDialog(frame,"Reject request for "+v(item,"username")+"?",
                "Reject Password Request",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            try{store.rejectPasswordReset(v(item,"id"),username());
                store.log(username(),"Rejected password assistance request #"+v(item,"id"));renderPage();
            }catch(Exception ex){error(ex);}
        });
        refresh.addActionListener(e->renderPage());
        bar.add(approve);bar.add(reject);bar.add(refresh);
        view.add(bar,BorderLayout.NORTH);view.add(table.scroll(),BorderLayout.CENTER);
        addBlock(body,section("Password Assistance Requests",
            "Verify the requester before issuing a one-time code. Codes are NEVER stored as plaintext.",view),505);
        addBlock(body,blank("Farmer Recovery Instructions",
            "After verification, share the code privately. Farmer uses Forgot Password > Enter Recovery Code."),100);
        return body;
    }

    private JPanel userAccountsPage(){
        JPanel p=stack();List<Map<String,String>> us=rows("users");
        long pending=us.stream().filter(x->"false".equalsIgnoreCase(v(x,"active"))).count();
        addBlock(p,horizontal(stat("Total Users",String.valueOf(us.size()),"Registered accounts",LIGHT,"♟"),
            stat("Active Accounts",String.valueOf(us.size()-pending),"Sign-in allowed",new Color(233,247,235),"✓"),
            stat("Pending Accounts",String.valueOf(pending),"Awaiting activation",new Color(255,244,224),"!")),114);
        List<Map<String,String>> safe=new ArrayList<>();for(Map<String,String> u:us){Map<String,String> copy=new LinkedHashMap<>(u);copy.remove("password_hash");safe.add(copy);}
        TableParts table=tableView("users",safe,List.of("id","username","full_name","role","active","farm_id"),"Search users...");
        JPanel controls=pane(new BorderLayout(0,10));JButton add=btn("+ Add User",GREEN),edit=secondary("Edit Profile / Contacts"),toggle=secondary("Activate / Deactivate"),pw=secondary("Reset Password");
        add.addActionListener(e->userDialog(null));edit.addActionListener(e->{Map<String,String> r=table.selected();if(r==null){info("Select a user.");return;}userDialog(r);});
        toggle.addActionListener(e->{Map<String,String> r=table.selected();if(r==null){info("Select a user.");return;}
            if(v(r,"id").equals(v(user,"id"))){info("You cannot deactivate your own account.");return;}
            try{boolean active=Boolean.parseBoolean(v(r,"active"));store.edit("users",v(r,"id"),Map.of("active",Boolean.toString(!active)));
                store.log(username(),(active?"Deactivated ":"Activated ")+v(r,"username"));renderPage();}catch(Exception ex){error(ex);}});
        pw.addActionListener(e->{Map<String,String> r=table.selected();if(r==null){info("Select a user.");return;}
            JCheckBox identity=new JCheckBox("I have verified this account holder's identity.");
            JPanel verify=pane(new GridLayout(0,1,0,6));
            verify.add(label("Manual administrative reset for "+v(r,"username"),13,true,DARK));
            verify.add(identity);
            if(StyledDialogs.showConfirmDialog(frame,verify,"Identity Check Required",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            if(!identity.isSelected()){info("Verify the person's identity before resetting.");return;}
            JPasswordField f=new JPasswordField();if(StyledDialogs.showConfirmDialog(frame,f,"New Password for "+v(r,"username"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            char[] pass=f.getPassword();try{if(pass.length<8)throw new IllegalArgumentException("Password must be at least 8 characters.");
                store.edit("users",v(r,"id"),Map.of("password_hash",Security.hash(pass)));store.log(username(),"Verified manual reset for "+v(r,"username"));info("Password reset.");}
            catch(Exception ex){error(ex);}finally{Arrays.fill(pass,'\0');}});
        controls.add(actions(add,edit,toggle,pw),BorderLayout.NORTH);controls.add(table.scroll(),BorderLayout.CENTER);
        addBlock(p,section("User Management","New registrations are inactive until approved by an administrator",controls),490);
        return p;
    }
    private void userDialog(Map<String,String> orig){
        JTextField login=new JTextField(orig==null?"":v(orig,"username")),name=new JTextField(orig==null?"":v(orig,"full_name"));
        JComboBox<String> roles=new JComboBox<>(new String[]{"FARM_OWNER","FARM_MANAGER","INVENTORY_STAFF","BOOKKEEPER","VIEWER","SYSTEM_ADMIN"});
        if(orig!=null)roles.setSelectedItem(v(orig,"role"));JCheckBox active=new JCheckBox("Account is active",orig==null||Boolean.parseBoolean(v(orig,"active")));
        JPasswordField pass=new JPasswordField();
        JTextField email=new JTextField(orig==null?"":v(orig,"email"));
        JTextField phone=new JTextField(orig==null?"":v(orig,"phone"));
        JCheckBox emailVerified=new JCheckBox("I checked and confirmed ownership of this email",
            orig!=null&&Boolean.parseBoolean(v(orig,"email_verified")));
        JCheckBox phoneVerified=new JCheckBox("I checked and confirmed ownership of this phone",
            orig!=null&&Boolean.parseBoolean(v(orig,"phone_verified")));
        // Changing a contact automatically removes its previous verification.
        email.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){emailVerified.setSelected(false);}
            public void removeUpdate(javax.swing.event.DocumentEvent e){emailVerified.setSelected(false);}
            public void changedUpdate(javax.swing.event.DocumentEvent e){emailVerified.setSelected(false);}
        });
        phone.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){phoneVerified.setSelected(false);}
            public void removeUpdate(javax.swing.event.DocumentEvent e){phoneVerified.setSelected(false);}
            public void changedUpdate(javax.swing.event.DocumentEvent e){phoneVerified.setSelected(false);}
        });
        JPanel f=pane(new GridLayout(0,1,0,5));
        for(JTextField input:List.of(login,name,email,phone,pass))styleInput(input);
        f.add(label("Username",13,true,DARK));f.add(login);f.add(label("Full Name",13,true,DARK));f.add(name);
        f.add(label("Role",13,true,DARK));f.add(roles);f.add(active);
        f.add(label("Recovery Email (optional)",13,true,DARK));f.add(email);f.add(emailVerified);
        f.add(label("Recovery Phone in +63 format (optional)",13,true,DARK));f.add(phone);f.add(phoneVerified);
        f.add(label("Check verification only after independent proof of contact ownership.",11,false,MUTED));
        if(orig==null){f.add(label("Initial Password (8+ characters)",13,true,DARK));f.add(pass);}
        if(StyledDialogs.showConfirmDialog(frame,f,orig==null?"Add New User":"Edit User",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        char[] pw=pass.getPassword();try{
            if(login.getText().isBlank()||name.getText().isBlank())throw new IllegalArgumentException("Username and full name are required.");
            Map<String,String> changes=new LinkedHashMap<>();changes.put("username",login.getText().trim());changes.put("full_name",name.getText().trim());
            changes.put("role",Objects.toString(roles.getSelectedItem(),"VIEWER"));changes.put("active",Boolean.toString(active.isSelected()));
            String mail=email.getText().trim(),mobile=phone.getText().trim();
            if(!mail.isBlank()&&!ContactValidation.validEmail(mail))throw new IllegalArgumentException("Invalid recovery email address.");
            if(!mobile.isBlank()&&!ContactValidation.validPhone(mobile))throw new IllegalArgumentException("Use phone format +639123456789.");
            changes.put("email",mail);changes.put("phone",mobile);
            changes.put("email_verified",Boolean.toString(!mail.isBlank() && emailVerified.isSelected()));
            changes.put("phone_verified",Boolean.toString(!mobile.isBlank() && phoneVerified.isSelected()));
            if(orig==null){if(pw.length<8)throw new IllegalArgumentException("Password must be at least 8 characters.");
                changes.put("password_hash",Security.hash(pw));changes.put("farm_id","1");store.add("users",changes);
            }else {if(v(orig,"id").equals(v(user,"id"))&&( !active.isSelected()||!"SYSTEM_ADMIN".equals(changes.get("role"))))
                    throw new IllegalArgumentException("You cannot remove your own administrator access.");
                store.edit("users",v(orig,"id"),changes);
            }
            store.log(username(),(orig==null?"Created user ":"Updated user ")+login.getText().trim());
            if(orig!=null && v(orig,"id").equals(v(user,"id")))user.putAll(changes);
            renderPage();
        }catch(Exception ex){error(ex);}finally{Arrays.fill(pw,'\0');}
    }

}
