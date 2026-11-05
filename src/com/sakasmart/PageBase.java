package com.sakasmart;

import javax.swing.*;
import java.util.*;

/** Shared UI operations for navigation pages. This keeps the storage connection
 * and login/session state owned by Main instead of duplicating them in pages. */
abstract class PageBase {
    protected final Main host;
    protected final Storage store;
    protected final JFrame frame;
    protected final Map<String,String> user;

    protected PageBase(Main host) {
        this.host=host;
        this.store=host.store;
        this.frame=host.frame;
        this.user=host.user;
    }
    protected List<Map<String,String>> rows(String table) {return host.rows(table);}
    protected void info(String text) {host.info(text);}
    protected void error(Exception e) {host.error(e);}
    protected String username() {return host.username();}
    protected String role() {return host.role();}
    protected boolean admin() {return host.admin();}
    protected boolean editable(String table) {return host.editable(table);}
    protected void renderPage() {host.renderPage();}
    protected void go(String page) {host.go(page);}
    protected JPanel stack() {return host.stack();}
    protected JPanel vertical() {return host.vertical();}
    protected JComponent tableAndActions(String table,Main.TableParts parts,Map<String,String> defaults) {return host.tableAndActions(table,parts,defaults);}
    protected void form(String table,Map<String,String> original,Map<String,String> defaults,Set<String> locked) {host.form(table,original,defaults,locked);}
    protected void selectedEdit(String table,Main.TableParts parts) {host.selectedEdit(table,parts);}
    protected void selectedDelete(String table,Main.TableParts parts) {host.selectedDelete(table,parts);}
    protected void deleteRow(String table,Map<String,String> original) {host.deleteRow(table,original);}
    protected void exportTable(String table,List<Map<String,String>> rows) {host.exportTable(table,rows);}
    protected void backupArchive() {host.backupArchive();}
    protected void editFarm(){new SettingsPage(host).editFarm();}
}
