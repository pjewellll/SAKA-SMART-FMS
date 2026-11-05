package com.sakasmart;

import java.util.*;

/** Session-bound farm authorization facade. Never trust farm_id from a UI form. */
final class ScopedStorage implements Storage {
    private final Storage delegate;
    private final String userId, username, farmId, role;
    private final boolean systemAdmin;
    private static final Set<String> FARM_DATA=Set.of("inventory","events","production","transactions","sales","farms","logs");
    private static final Set<String> STAFF_ROLES=Set.of("FARM_OWNER","FARM_MANAGER","INVENTORY_STAFF","BOOKKEEPER","VIEWER");
    ScopedStorage(Storage delegate,Map<String,String> user){
        this.delegate=delegate;
        userId=user.getOrDefault("id","");username=user.getOrDefault("username","");
        farmId=user.getOrDefault("farm_id","");role=user.getOrDefault("role","");
        systemAdmin="SYSTEM_ADMIN".equals(role);
        if(!"true".equalsIgnoreCase(user.getOrDefault("active","false")))throw new SecurityException("Inactive account.");
        if(!systemAdmin && (!STAFF_ROLES.contains(role)||farmId.isBlank()))throw new SecurityException("No authorized farm membership.");
    }
    private void require(boolean allowed){if(!allowed)throw new SecurityException("You do not have permission for this farm or operation.");}
    private boolean mayEdit(String table){
        if(systemAdmin||"FARM_OWNER".equals(role))return true;
        return switch(table){
            case "inventory" -> "FARM_MANAGER".equals(role)||"INVENTORY_STAFF".equals(role);
            case "events","production" -> "FARM_MANAGER".equals(role);
            case "transactions","sales" -> "FARM_MANAGER".equals(role)||"BOOKKEEPER".equals(role);
            default -> false;
        };
    }
    private boolean ownFarm(Map<String,String> r){return farmId.equals(r.getOrDefault("farm_id",""));}
    private Map<String,String> checkedRecord(String table,String id) throws Exception {
        Map<String,String> r=delegate.byId(table,id);
        require(r!=null && (systemAdmin||ownFarm(r)));
        return r;
    }
    @Override public List<Map<String,String>> all(String table) throws Exception {
        if("password_reset_requests".equals(table)||"social_identities".equals(table)||"phone_login_codes".equals(table)){
            require(systemAdmin && "password_reset_requests".equals(table));
        }
        List<Map<String,String>> all=delegate.all(table);
        if(systemAdmin || "articles".equals(table))return all;
        if("users".equals(table)){
            List<Map<String,String>> visible=new ArrayList<>();
            for(Map<String,String> row:all){
                if(!(("FARM_OWNER".equals(role) && ownFarm(row))||userId.equals(row.get("id"))))continue;
                Map<String,String> safe=new HashMap<>(row);
                if(!userId.equals(row.get("id"))){
                    safe.remove("password_hash");safe.remove("email");safe.remove("phone");
                }
                visible.add(safe);
            }
            return visible;
        }
        if(FARM_DATA.contains(table))return all.stream().filter(this::ownFarm).toList();
        throw new SecurityException("Unauthorized data category.");
    }
    @Override public Map<String,String> add(String table,Map<String,String> values) throws Exception {
        require(!Set.of("password_reset_requests","social_identities","phone_login_codes","users").contains(table));
        if("articles".equals(table)){require(systemAdmin);return delegate.add(table,values);}
        require("logs".equals(table)|| (mayEdit(table) && !"farms".equals(table)));
        Map<String,String> scoped=new HashMap<>(values);
        if(!systemAdmin){
            scoped.put("farm_id",farmId);
            if("logs".equals(table))scoped.put("actor",username);
            if("sales".equals(table))require(ownFarm(delegate.byId("inventory",values.get("item_id"))));
        }
        return delegate.add(table,scoped);
    }
    @Override public void edit(String table,String id,Map<String,String> values) throws Exception {
        if("users".equals(table)){
            if(systemAdmin){delegate.edit(table,id,values);return;}
            require(userId.equals(id));
            require(Set.of("password_hash","email","email_verified","phone","phone_verified").containsAll(values.keySet()));
            Map<String,String> safe=new HashMap<>(values);
            // Users can change contacts but CANNOT self-verify them.
            Map<String,String> existing=delegate.byId("users",id);
            if(safe.containsKey("email") && !Objects.equals(safe.get("email"),existing.getOrDefault("email","")))safe.put("email_verified","false");
            if(safe.containsKey("phone") && !Objects.equals(safe.get("phone"),existing.getOrDefault("phone","")))safe.put("phone_verified","false");
            if("true".equalsIgnoreCase(safe.getOrDefault("email_verified","false")))require("true".equalsIgnoreCase(existing.getOrDefault("email_verified","false")));
            if("true".equalsIgnoreCase(safe.getOrDefault("phone_verified","false")))require("true".equalsIgnoreCase(existing.getOrDefault("phone_verified","false")));
            delegate.edit(table,id,safe);return;
        }
        if("articles".equals(table)){require(systemAdmin);delegate.edit(table,id,values);return;}
        require(mayEdit(table));checkedRecord(table,id);
        Map<String,String> scoped=new HashMap<>(values);
        if(!systemAdmin){scoped.remove("farm_id");if("sales".equals(table))scoped.remove("item_id");}
        delegate.edit(table,id,scoped);
    }
    @Override public void remove(String table,String id) throws Exception {
        if("articles".equals(table)){require(systemAdmin);delegate.remove(table,id);return;}
        require(mayEdit(table) && !Set.of("users","farms","logs","password_reset_requests").contains(table));
        checkedRecord(table,id);delegate.remove(table,id);
    }
    @Override public void sell(String itemId,String buyer,double qty,double price,String date,String actor) throws Exception {
        require(mayEdit("sales"));checkedRecord("inventory",itemId);
        delegate.sell(itemId,buyer,qty,price,date,systemAdmin?actor:username);
    }
    @Override public List<Map<String,String>> publicFarms() throws Exception{return delegate.publicFarms();}
    @Override public void decideMembership(String owner,String member,String targetRole,boolean approve) throws Exception {
        require("FARM_OWNER".equals(role)&&userId.equals(owner));
        delegate.decideMembership(owner,member,targetRole,approve);
    }
    @Override public Map<String,String> registerOwnFarm(Map<String,String> a,Map<String,String> f,String p,String s) {throw new SecurityException("Log out to register.");}
    @Override public Map<String,String> registerJoinFarm(Map<String,String> a,String f,String p,String s){throw new SecurityException("Log out to register.");}
    @Override public Map<String,String> bySocialIdentity(String p,String s){throw new SecurityException("Authentication operation requires login screen.");}
    @Override public void requestPasswordReset(String username) throws Exception {throw new SecurityException("Use the recovery form.");}
    @Override public String approvePasswordReset(String id,String administrator) throws Exception {require(systemAdmin);return delegate.approvePasswordReset(id,administrator);}
    @Override public void rejectPasswordReset(String id,String administrator) throws Exception {require(systemAdmin);delegate.rejectPasswordReset(id,administrator);}
    @Override public boolean redeemPasswordReset(String u,String c,char[] pw) {throw new SecurityException("Use the recovery form.");}
    @Override public RecoveryDelivery issueRecoveryToVerifiedContact(String u,String channel) {throw new SecurityException("Use the recovery form.");}
    @Override public void cancelRecoveryDelivery(String id) {throw new SecurityException("Use the recovery form.");}
    @Override public RecoveryDelivery issuePhoneLoginCode(String number){throw new SecurityException("Use the login form.");}
    @Override public void cancelPhoneLoginCode(String id){throw new SecurityException("Use the login form.");}
    @Override public Map<String,String> redeemPhoneLoginCode(String number,String code){throw new SecurityException("Use the login form.");}
    @Override public String mode(){return delegate.mode();}
}
