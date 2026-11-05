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

/** SAKA-SMART Settings page UI. Uses shared actions, storage and styling from the application context. */
final class SettingsPage extends PageBase {
    SettingsPage(Main host) { super(host); }
    JComponent build(){
        JPanel p=stack();
        addBlock(p,horizontal(stat("Current User",user.getOrDefault("full_name",username()),role().replace('_',' '),LIGHT,"♟"),
            stat("Storage Mode",store.mode().startsWith("POSTGRESQL")?"PostgreSQL":"Local Demo","Current storage connection",new Color(236,244,254),"▣")),115);

        // Personal security controls belong in Settings, not administrator request management.
        JPanel accountSecurity=pane(new BorderLayout(0,9));
        JButton pw=btn("Change My Password",GREEN),contacts=secondary("Update Recovery Contacts");
        pw.addActionListener(e->changePassword());
        contacts.addActionListener(e->updateRecoveryContacts());
        accountSecurity.add(actions(pw,contacts),BorderLayout.NORTH);
        JPanel securityDetails=pane(new GridLayout(3,1,0,4));
        securityDetails.add(label("Email: "+displayContact(v(user,"email")) +
            (Boolean.parseBoolean(v(user,"email_verified"))?"  (verified)":"  (not verified)"),12,false,TEXT));
        securityDetails.add(label("Phone: "+displayContact(v(user,"phone"))+
            (Boolean.parseBoolean(v(user,"phone_verified"))?"  (verified)":"  (not verified)"),12,false,TEXT));
        securityDetails.add(label("New contacts must be verified by an administrator before recovery is enabled.",11,false,MUTED));
        accountSecurity.add(securityDetails,BorderLayout.CENTER);
        addBlock(p,section("Account & Security",
            "Manage your password and verified recovery contacts",accountSecurity),230);

        JButton farm=btn("Edit Farm Profile",GREEN),backup=secondary("Export Data Archive");
        farm.addActionListener(e->editFarm());backup.addActionListener(e->backupArchive());
        addBlock(p,section("Farm Management & Data",
            "Maintain farm details and export a copy of records",actions(farm,backup)),151);
        Map<String,String> f=rows("farms").stream().findFirst().orElse(Map.of());
        JPanel details=pane(new GridLayout(0,2,12,12));
        for(String col:List.of("name","location","owner","area_ha","crops","livestock")){
            details.add(label(Schema.TABLES.get("farms").find(col).label(),14,true,MUTED));details.add(label(v(f,col),14,false,DARK));
        }
        addBlock(p,section("Farm Profile","Current farm information",details),245);
        addBlock(p,blank("About Backup & Sync",
            "CSV archives are exports only, not a full PostgreSQL restore backup or cloud sync."),110);
        return p;
    }

    @Override protected void editFarm(){
        if(!(admin()||"FARM_OWNER".equals(role()))){info("Only farm owners and system administrators may edit farm information.");return;}
        Map<String,String> f=rows("farms").stream().findFirst().orElse(null);
        if(f!=null)form("farms",f,Map.of(),Set.of());
    }
    private static String displayContact(String contact){return contact.isBlank()?"Not provided":contact;}

    private void updateRecoveryContacts(){
        JTextField email=new JTextField(v(user,"email")),phone=new JTextField(v(user,"phone"));
        JPasswordField password=new JPasswordField();
        styleInput(email);styleInput(phone);styleInput(password);
        JPanel fields=pane(new GridLayout(0,1,0,7));
        fields.add(label("Recovery email (optional)",13,true,DARK));fields.add(email);
        fields.add(label("Recovery mobile number (+639XXXXXXXXX, optional)",13,true,DARK));fields.add(phone);
        fields.add(label("Confirm current password",13,true,DARK));fields.add(password);
        fields.add(label("Changed contacts remain unverified until reviewed by an administrator.",11,false,MUTED));
        if(StyledDialogs.showConfirmDialog(frame,fields,"Update Recovery Contacts",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        char[] entered=password.getPassword();
        try {
            if(!Security.verify(entered,v(user,"password_hash")))
                throw new IllegalArgumentException("Current password incorrect.");
            String mail=email.getText().trim(),mobile=phone.getText().trim();
            if(!mail.isBlank() && !ContactValidation.validEmail(mail))
                throw new IllegalArgumentException("Enter a valid email address.");
            if(!mobile.isBlank() && !ContactValidation.validPhone(mobile))
                throw new IllegalArgumentException("Use international phone format, such as +639123456789.");
            boolean keepMail=mail.equalsIgnoreCase(v(user,"email")) && Boolean.parseBoolean(v(user,"email_verified"));
            boolean keepPhone=mobile.equals(v(user,"phone")) && Boolean.parseBoolean(v(user,"phone_verified"));
            Map<String,String> changes=Map.of("email",mail,"email_verified",Boolean.toString(keepMail),
                "phone",mobile,"phone_verified",Boolean.toString(keepPhone));
            store.edit("users",v(user,"id"),changes);user.putAll(changes);
            store.log(username(),"Updated personal recovery contacts (values not logged)");
            info("Recovery contact details saved. Newly added or changed contacts require administrator verification.");
            renderPage();
        }catch(Exception ex){error(ex);}finally{Arrays.fill(entered,'\0');}
    }

    private void changePassword(){
        JPasswordField old=new JPasswordField(),first=new JPasswordField(),again=new JPasswordField();
        JPanel p=pane(new GridLayout(0,1,0,5));p.add(label("Current Password",13,true,DARK));p.add(old);
        p.add(label("New Password",13,true,DARK));p.add(first);p.add(label("Repeat New Password",13,true,DARK));p.add(again);
        if(StyledDialogs.showConfirmDialog(frame,p,"Change Password",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        char[] current=old.getPassword(),pw=first.getPassword(),verify=again.getPassword();
        try{
            if(!Security.verify(current,v(user,"password_hash")))throw new IllegalArgumentException("Current password incorrect.");
            if(pw.length<8||!Arrays.equals(pw,verify))throw new IllegalArgumentException("New passwords must match and contain at least 8 characters.");
            String hashed=Security.hash(pw);store.edit("users",v(user,"id"),Map.of("password_hash",hashed));user.put("password_hash",hashed);
            store.log(username(),"Changed account password");info("Password changed successfully.");
        }catch(Exception e){error(e);}finally{Arrays.fill(current,'\0');Arrays.fill(pw,'\0');Arrays.fill(verify,'\0');}
    }

}
