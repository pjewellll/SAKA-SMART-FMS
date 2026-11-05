package com.sakasmart;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import static com.sakasmart.Main.*;

/** Farm owner approves membership; separate from system-wide administrator privileges. */
final class FarmMembersPage extends PageBase {
    FarmMembersPage(Main host){super(host);}
    JComponent build(){
        if(!"FARM_OWNER".equals(role()))return blank("Access Denied","Only a farm owner can review their farm members.");
        JPanel body=stack();
        List<Map<String,String>> members=rows("users");
        List<Map<String,String>> pending=members.stream().filter(x->"false".equalsIgnoreCase(v(x,"active")) && "VIEWER".equals(v(x,"role"))).toList();
        long active=members.stream().filter(x->"true".equalsIgnoreCase(v(x,"active"))).count();
        addBlock(body,horizontal(stat("Pending Requests",Integer.toString(pending.size()),"Waiting for your approval",LIGHT,"!"),
            stat("Active Farm Members",Long.toString(active),"Approved accounts",new Color(228,245,233),"✓")),113);
        JPanel controls=pane(new BorderLayout(0,12));
        Main.TableParts table=tableView("users",pending,List.of("id","username","full_name","role","active"),"Search joining farmers...");
        JPanel toolbar=pane(new FlowLayout(FlowLayout.LEFT,8,0));
        JButton approve=btn("Approve & Assign Role",GREEN),reject=btn("Reject Request",RED),refresh=secondary("Refresh");
        approve.addActionListener(e->{
            Map<String,String> row=table.selected();if(row==null){info("Select a pending member first.");return;}
            String[] roles={"VIEWER","INVENTORY_STAFF","FARM_MANAGER","BOOKKEEPER"};
            JComboBox<String> choice=new JComboBox<>(roles);
            JPanel panel=pane(new BorderLayout(0,8));panel.add(label("Role for "+v(row,"username"),14,true,DARK),BorderLayout.NORTH);panel.add(choice,BorderLayout.CENTER);
            if(StyledDialogs.showConfirmDialog(frame,panel,"Approve Farm Member",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            try{store.decideMembership(v(user,"id"),v(row,"id"),Objects.toString(choice.getSelectedItem(),"VIEWER"),true);
                store.log(username(),"Approved membership for "+v(row,"username"));renderPage();}catch(Exception ex){error(ex);}
        });
        reject.addActionListener(e->{
            Map<String,String> row=table.selected();if(row==null){info("Select a pending member first.");return;}
            if(StyledDialogs.showConfirmDialog(frame,"Reject "+v(row,"username")+"?","Reject Membership",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            try{store.decideMembership(v(user,"id"),v(row,"id"),"VIEWER",false);
                store.log(username(),"Rejected membership for "+v(row,"username"));renderPage();}catch(Exception ex){error(ex);}
        });
        refresh.addActionListener(e->renderPage());toolbar.add(approve);toolbar.add(reject);toolbar.add(refresh);
        controls.add(toolbar,BorderLayout.NORTH);controls.add(table.scroll(),BorderLayout.CENTER);
        addBlock(body,section("Requests to Join Your Farm","Only verified farm owners can approve staff membership",controls),430);
        List<Map<String,String>> accepted=members.stream().filter(x->"true".equalsIgnoreCase(v(x,"active"))).toList();
        addBlock(body,section("Your Farm Team","Approved accounts and their roles",
            tableView("users",accepted,List.of("username","full_name","role","active"),null).scroll()),300);
        return body;
    }
}
