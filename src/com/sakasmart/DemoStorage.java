package com.sakasmart;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Local, persistent demonstration mode with no external dependencies. */
public final class DemoStorage implements Storage {
    private static final Path PATH = Path.of("data", "saka-smart-demo.dat");
    private static final class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        Map<String,List<Map<String,String>>> rows = new LinkedHashMap<>();
        long nextId = 1;
    }
    private Snapshot state;
    public DemoStorage() throws Exception {
        if (Files.exists(PATH)) {
            try(ObjectInputStream in = new ObjectInputStream(Files.newInputStream(PATH))) {
                state = (Snapshot)in.readObject();
            }
        } else {
            state = new Snapshot();
            for (String table : Schema.TABLES.keySet()) state.rows.put(table,new ArrayList<>());
            seed();
            persist();
        }
        // Older local demo snapshots need newly introduced tables added safely.
        for(String table:Schema.TABLES.keySet())state.rows.computeIfAbsent(table,k->new ArrayList<>());
    }
    private void persist() throws IOException {
        Files.createDirectories(PATH.getParent());
        Path temp = PATH.resolveSibling("saka-smart-demo.tmp");
        try(ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temp))) {out.writeObject(state);}
        try { Files.move(temp,PATH,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) {Files.move(temp,PATH,StandardCopyOption.REPLACE_EXISTING);}
    }
    private Map<String,String> internalAdd(String table, Map<String,String> values) {
        Map<String,String> row = new LinkedHashMap<>(values);
        row.put("id",Long.toString(state.nextId++));
        state.rows.get(table).add(row);
        return row;
    }
    private static Map<String,String> row(String... args) {
        Map<String,String> m = new LinkedHashMap<>();
        for(int i=0;i<args.length;i+=2)m.put(args[i],args[i+1]);
        return m;
    }
    private void seed() {
        LocalDate now = LocalDate.now();
        internalAdd("farms",row("name","San Rafael Farm","location","Tarlac, Philippines","owner","Juan Dela Cruz","area_ha","12.5","crops","Rice, Corn, Vegetables","livestock","Cattle, Chickens, Goats"));
        internalAdd("users",row("username","admin","full_name","System Administrator","password_hash",Security.hash("Admin@123".toCharArray()),"role","SYSTEM_ADMIN","active","true","farm_id","1"));
        internalAdd("users",row("username","farmer","full_name","Juan Dela Cruz","password_hash",Security.hash("Farmer@123".toCharArray()),"role","FARM_OWNER","active","true","farm_id","1"));
        internalAdd("inventory",row("name","Rice Seeds (NSIC Rc 222)","category","Seeds","kind","SUPPLY","quantity","200","unit","kg","supplier","SeedWorks PH","expiry_date",now.plusMonths(7).toString(),"min_stock","20","farm_id","1"));
        internalAdd("inventory",row("name","Complete Fertilizer (14-14-14)","category","Fertilizer","kind","SUPPLY","quantity","5","unit","bags","supplier","Yara Philippines","expiry_date",now.plusMonths(9).toString(),"min_stock","10","farm_id","1"));
        internalAdd("inventory",row("name","Organic Pesticide (Neem Oil)","category","Pesticide","kind","SUPPLY","quantity","12","unit","bottles","supplier","BioAgri Co.","expiry_date",now.plusDays(12).toString(),"min_stock","4","farm_id","1"));
        internalAdd("inventory",row("name","Layer Feed","category","Feed","kind","SUPPLY","quantity","8","unit","bags","supplier","B-MEG","expiry_date",now.plusMonths(2).toString(),"min_stock","12","farm_id","1"));
        internalAdd("inventory",row("name","Rice (Palay)","category","Crops","kind","HARVEST","quantity","1200","unit","kg","supplier","Field 1","expiry_date",now.plusMonths(6).toString(),"min_stock","100","farm_id","1"));
        internalAdd("inventory",row("name","Corn","category","Crops","kind","HARVEST","quantity","980","unit","kg","supplier","Field 2","expiry_date",now.plusMonths(4).toString(),"min_stock","100","farm_id","1"));
        internalAdd("inventory",row("name","Fresh Milk","category","Livestock Product","kind","HARVEST","quantity","320","unit","liters","supplier","Dairy Cattle","expiry_date",now.plusDays(7).toString(),"min_stock","50","farm_id","1"));
        internalAdd("inventory",row("name","Eggs","category","Livestock Product","kind","HARVEST","quantity","2500","unit","pcs","supplier","Chicken House","expiry_date",now.plusDays(20).toString(),"min_stock","200","farm_id","1"));
        internalAdd("events",row("title","Rice Planting","kind","CROP","category","Planting","target","Field 1 - East","date",now.plusDays(1).toString(),"time","08:00","status","Upcoming","notes","Prepare seedbeds","farm_id","1"));
        internalAdd("events",row("title","Cow Vaccination","kind","LIVESTOCK","category","Vaccination","target","Dairy Cattle","date",now.plusDays(3).toString(),"time","09:00","status","Upcoming","notes","Check vaccine stock","farm_id","1"));
        internalAdd("events",row("title","Corn Fertilizer Application","kind","CROP","category","Fertilizing","target","Field 2 - West","date",now.plusDays(5).toString(),"time","08:00","status","Upcoming","notes","","farm_id","1"));
        internalAdd("events",row("title","Egg Collection","kind","LIVESTOCK","category","Collection","target","Chicken House","date",now.toString(),"time","07:00","status","Completed","notes","","farm_id","1"));
        internalAdd("production",row("item","Rice","kind","CROP","target","8000","actual","7200","unit","kg","date",now.minusDays(1).toString(),"notes","Field 1","farm_id","1"));
        internalAdd("production",row("item","Corn","kind","CROP","target","5000","actual","4100","unit","kg","date",now.minusDays(1).toString(),"notes","Field 2","farm_id","1"));
        internalAdd("production",row("item","Milk","kind","LIVESTOCK","target","1200","actual","980","unit","liters","date",now.minusDays(1).toString(),"notes","Dairy Cattle","farm_id","1"));
        internalAdd("production",row("item","Eggs","kind","LIVESTOCK","target","3600","actual","3200","unit","pcs","date",now.minusDays(2).toString(),"notes","Layer Chickens","farm_id","1"));
        internalAdd("transactions",row("type","Expense","category","Seeds","amount","4800","date",now.minusDays(4).toString(),"notes","PUR-001","sale_id","","farm_id","1"));
        internalAdd("transactions",row("type","Expense","category","Fertilizer","amount","7200","date",now.minusDays(3).toString(),"notes","PUR-002","sale_id","","farm_id","1"));
        internalAdd("transactions",row("type","Income","category","Farm Produce","amount","18000","date",now.minusDays(2).toString(),"notes","Previous sale","sale_id","","farm_id","1"));
        internalAdd("articles",row("title","Soil Preparation Basics","category","Crops","content","Healthy soil is the foundation of a productive farm. Begin by clearing weeds, inspecting drainage, loosening compacted soil, and applying suitable organic matter. Use soil testing to guide fertilizer decisions.","date",now.minusDays(2).toString()));
        internalAdd("articles",row("title","Rice Planting Techniques","category","Crops","content","Choose appropriate seed varieties, prepare your nursery, and schedule planting around water availability. Inspect seedlings regularly and maintain written records of each field's progress.","date",now.minusDays(3).toString()));
        internalAdd("articles",row("title","Proper Pesticide Use","category","Safety","content","Read the product label, use the recommended protective equipment, observe pre-harvest intervals, and store pesticides securely away from people, animals, food, and water.","date",now.minusDays(5).toString()));
        internalAdd("articles",row("title","Animal Handling Do's and Don'ts","category","Livestock","content","Handle animals calmly, use clean facilities, provide sufficient water, and contact a qualified veterinarian for health concerns. Keep vaccination and treatment records up to date.","date",now.minusDays(6).toString()));
        internalAdd("articles",row("title","Farm Record-Keeping","category","Farm Finance","content","Record each purchase, harvest, and sale on its actual date. Separate farm transactions from personal expenses so financial summaries remain reliable.","date",now.minusDays(9).toString()));
        internalAdd("logs",row("actor","System","action","Sample project data created (demo mode)","date",LocalDateTime.now().toString().replace('T',' '),"farm_id","1"));
    }
    @Override public synchronized List<Map<String,String>> all(String table) {
        List<Map<String,String>> result=new ArrayList<>();
        for(Map<String,String> row:state.rows.getOrDefault(table,List.of())) result.add(new LinkedHashMap<>(row));
        return result;
    }
    @Override public synchronized Map<String,String> add(String table,Map<String,String> values) throws Exception {
        Map<String,String> row=internalAdd(table,values); persist();return new LinkedHashMap<>(row);
    }
    @Override public synchronized void edit(String table,String id,Map<String,String> values) throws Exception {
        for(Map<String,String> row:state.rows.getOrDefault(table,List.of())) if(id.equals(row.get("id"))){
            boolean contactChanged="users".equals(table) && (
                (values.containsKey("email") && !Objects.equals(row.getOrDefault("email",""),values.get("email"))) ||
                (values.containsKey("phone") && !Objects.equals(row.getOrDefault("phone",""),values.get("phone"))));
            row.putAll(values);
            if(contactChanged)for(Map<String,String> request:state.rows.getOrDefault("password_reset_requests",List.of()))
                if(id.equals(request.get("user_id")) && "APPROVED".equals(request.get("status"))){
                    request.put("status","EXPIRED");request.remove("code_hash");
                }
            persist();return;
        }
        throw new IllegalArgumentException("Record no longer exists.");
    }
    @Override public synchronized void remove(String table,String id) throws Exception {
        if ("inventory".equals(table)) for (Map<String,String> sale:all("sales"))
            if(id.equals(sale.get("item_id")))throw new IllegalArgumentException("This item has sales history. Keep it for accurate records.");
        boolean did=state.rows.getOrDefault(table,List.of()).removeIf(r->id.equals(r.get("id")));
        if(!did)throw new IllegalArgumentException("Record no longer exists.");
        persist();
    }
    @Override public synchronized void sell(String itemId,String buyer,double qty,double unitPrice,String date,String actor) throws Exception {
        if(qty<=0||unitPrice<0)throw new IllegalArgumentException("Enter a positive quantity and nonnegative price.");
        Map<String,String> item=null;
        for(Map<String,String> row:state.rows.get("inventory")) if(itemId.equals(row.get("id"))) {item=row;break;}
        if(item==null||!"HARVEST".equals(item.get("kind")))throw new IllegalArgumentException("Select an available harvested product.");
        double current=Storage.num(item,"quantity");
        if(current<qty)throw new IllegalArgumentException("Not enough stock. Available: "+current);
        item.put("quantity",Double.toString(current-qty));
        Map<String,String> sale=internalAdd("sales",row("item_id",itemId,"buyer",buyer,"quantity",Double.toString(qty),"unit_price",Double.toString(unitPrice),"date",date,"farm_id",item.get("farm_id")));
        internalAdd("transactions",row("type","Income","category","Product Sale","amount",Double.toString(qty*unitPrice),"date",date,"notes","Sale to "+buyer,"sale_id",sale.get("id"),"farm_id",item.get("farm_id")));
        internalAdd("logs",row("actor",actor,"action","Recorded sale to "+buyer,"date",LocalDateTime.now().toString().replace('T',' '),"farm_id",item.get("farm_id")));
        persist();
    }
    private Map<String,String> userByName(String name){
        for(Map<String,String> r:state.rows.getOrDefault("users",List.of()))
            if(name.equalsIgnoreCase(r.getOrDefault("username","")))return r;
        return null;
    }
    private void requireAdmin(String name){
        Map<String,String> u=userByName(name);
        if(u==null || !"SYSTEM_ADMIN".equals(u.get("role")) || !"true".equalsIgnoreCase(u.get("active")))
            throw new SecurityException("Administrator access required.");
    }
    @Override public synchronized void requestPasswordReset(String username) throws Exception {
        Map<String,String> u=userByName(username);
        if(u==null || !"true".equalsIgnoreCase(u.get("active")))return;
        for(Map<String,String> r:state.rows.get("password_reset_requests"))
            if(u.get("id").equals(r.get("user_id")) && "PENDING".equals(r.get("status")))return;
        internalAdd("password_reset_requests",row("user_id",u.get("id"),"username",u.get("username"),
            "status","PENDING","requested_at",RecoveryCodes.utcNow(),"attempts","0"));
        persist();
    }
    @Override public synchronized String approvePasswordReset(String id,String admin) throws Exception {
        requireAdmin(admin);
        for(Map<String,String> r:state.rows.get("password_reset_requests"))if(id.equals(r.get("id"))){
            if(!"PENDING".equals(r.get("status")))throw new IllegalStateException("Only pending requests can be approved.");
            String code=RecoveryCodes.create();
            r.put("code_hash",RecoveryCodes.digest(code));r.put("status","APPROVED");
            r.put("expires_at",java.time.Instant.now().plusSeconds(RecoveryCodes.LIFETIME_MINUTES*60L).toString());
            r.put("handled_by",admin);r.put("handled_at",RecoveryCodes.utcNow());r.put("attempts","0");
            persist();return code;
        }
        throw new IllegalArgumentException("Request not found.");
    }
    @Override public synchronized void rejectPasswordReset(String id,String admin) throws Exception {
        requireAdmin(admin);
        for(Map<String,String> r:state.rows.get("password_reset_requests"))if(id.equals(r.get("id"))){
            if(!"PENDING".equals(r.get("status")))throw new IllegalStateException("Only pending requests can be rejected.");
            r.put("status","REJECTED");r.put("handled_by",admin);r.put("handled_at",RecoveryCodes.utcNow());
            persist();return;
        }
        throw new IllegalArgumentException("Request not found.");
    }
    @Override public synchronized boolean redeemPasswordReset(String username,String code,char[] password) throws Exception {
        if(password==null || password.length<8)throw new IllegalArgumentException("Password must have at least 8 characters.");
        Map<String,String> u=userByName(username);
        if(u==null || !"true".equalsIgnoreCase(u.get("active")))return false;
        List<Map<String,String>> all=state.rows.get("password_reset_requests");
        for(int i=all.size()-1;i>=0;i--){
            Map<String,String> r=all.get(i);
            if(!u.get("id").equals(r.get("user_id")) || !"APPROVED".equals(r.get("status")))continue;
            if(RecoveryCodes.expired(r.get("expires_at"))){r.put("status","EXPIRED");persist();continue;}
            int attempts=Integer.parseInt(r.getOrDefault("attempts","0"));
            if(attempts>=RecoveryCodes.MAX_ATTEMPTS){r.put("status","LOCKED");persist();continue;}
            if(!RecoveryCodes.matches(code,r.get("code_hash"))){
                attempts++;r.put("attempts",Integer.toString(attempts));
                if(attempts>=RecoveryCodes.MAX_ATTEMPTS)r.put("status","LOCKED");
                persist();return false;
            }
            u.put("password_hash",Security.hash(password));r.put("status","USED");
            r.remove("code_hash");persist();return true;
        }
        return false;
    }

    @Override public synchronized RecoveryDelivery issueRecoveryToVerifiedContact(String username,String channel) throws Exception {
        if(!"EMAIL".equals(channel) && !"SMS".equals(channel))throw new IllegalArgumentException("Invalid channel.");
        Map<String,String> u=userByName(username==null?"":username.trim());
        if(u==null || !"true".equalsIgnoreCase(u.get("active")))return null;
        boolean verified=Boolean.parseBoolean(u.getOrDefault("EMAIL".equals(channel)?"email_verified":"phone_verified","false"));
        String contact=u.getOrDefault("EMAIL".equals(channel)?"email":"phone","");
        if(!verified || contact.isBlank())return null;
        java.time.Instant now=java.time.Instant.now();int count=0;
        String marker=channel+"_AUTOMATED";
        for(Map<String,String> r:state.rows.getOrDefault("password_reset_requests",List.of())) {
            if(!u.get("id").equals(r.get("user_id")) || !marker.equals(r.get("handled_by")))continue;
            try {
                java.time.Instant when=java.time.Instant.parse(r.get("requested_at"));
                if(when.isAfter(now.minusSeconds(120)))return null;
                if(when.isAfter(now.minusSeconds(86400)))count++;
            }catch(Exception ignored){}
        }
        if(count>=5)return null;
        String code=RecoveryCodes.create();
        for(Map<String,String> r:state.rows.get("password_reset_requests"))
            if(u.get("id").equals(r.get("user_id")) && "APPROVED".equals(r.get("status"))){
                r.put("status","EXPIRED");r.remove("code_hash");
            }
        Map<String,String> r=internalAdd("password_reset_requests",row(
            "user_id",u.get("id"),"username",u.get("username"),"status","APPROVED",
            "requested_at",RecoveryCodes.utcNow(),"code_hash",RecoveryCodes.digest(code),
            "expires_at",now.plusSeconds(RecoveryCodes.LIFETIME_MINUTES*60L).toString(),
            "attempts","0","handled_by",marker,"handled_at",RecoveryCodes.utcNow()));
        persist();return new RecoveryDelivery(r.get("id"),contact,code);
    }

    @Override public synchronized void cancelRecoveryDelivery(String requestId) throws Exception {
        for(Map<String,String> r:state.rows.getOrDefault("password_reset_requests",List.of()))
            if(requestId.equals(r.get("id")) && "APPROVED".equals(r.get("status")) &&
                ("EMAIL_AUTOMATED".equals(r.get("handled_by")) || "SMS_AUTOMATED".equals(r.get("handled_by")))) {
                r.put("status","FAILED");r.remove("code_hash");persist();return;
            }
    }


    @Override public synchronized List<Map<String,String>> publicFarms() {
        return all("farms").stream().map(r->Map.of("id",r.get("id"),"name",r.getOrDefault("name","Farm"))).toList();
    }
    private void validateNewAccount(Map<String,String> account,String provider,String subject){
        String name=account.getOrDefault("username","");
        if(!name.matches("[A-Za-z0-9_.-]{3,40}"))throw new IllegalArgumentException("Username must be 3-40 letters, digits, dots, underscores or dashes.");
        if(userByName(name)!=null)throw new IllegalArgumentException("Username already exists.");
        if(provider!=null && !provider.isBlank()){
            if(!Set.of("GOOGLE","FACEBOOK").contains(provider) || subject==null || subject.isBlank())throw new SecurityException("Invalid social identity.");
            if(bySocialIdentity(provider,subject)!=null)throw new IllegalArgumentException("Social account is already linked.");
        }
    }
    private void attachSocial(String userId,String provider,String subject){
        if(provider!=null && !provider.isBlank())internalAdd("social_identities",row("user_id",userId,"provider",provider,"subject",subject));
    }
    @Override public synchronized Map<String,String> registerOwnFarm(Map<String,String> account,Map<String,String> farm,String provider,String subject) throws Exception {
        validateNewAccount(account,provider,subject);
        Map<String,String> createdFarm=internalAdd("farms",farm);
        Map<String,String> values=new HashMap<>(account);values.put("role","FARM_OWNER");values.put("active","true");values.put("farm_id",createdFarm.get("id"));
        Map<String,String> newUser=internalAdd("users",values);attachSocial(newUser.get("id"),provider,subject);
        persist();return new HashMap<>(newUser);
    }
    @Override public synchronized Map<String,String> registerJoinFarm(Map<String,String> account,String farmId,String provider,String subject) throws Exception {
        validateNewAccount(account,provider,subject);
        boolean found=state.rows.get("farms").stream().anyMatch(f->farmId.equals(f.get("id")));
        if(!found)throw new IllegalArgumentException("Farm not found.");
        Map<String,String> values=new HashMap<>(account);values.put("role","VIEWER");values.put("active","false");values.put("farm_id",farmId);
        Map<String,String> newUser=internalAdd("users",values);attachSocial(newUser.get("id"),provider,subject);
        persist();return new HashMap<>(newUser);
    }
    @Override public synchronized void decideMembership(String ownerId,String memberId,String role,boolean approve) throws Exception {
        if(approve && !Set.of("FARM_MANAGER","INVENTORY_STAFF","BOOKKEEPER","VIEWER").contains(role))throw new SecurityException("Invalid staff role.");
        Map<String,String> owner=null,member=null;
        for(Map<String,String> u:state.rows.get("users")){
            if(ownerId.equals(u.get("id")))owner=u;
            if(memberId.equals(u.get("id")))member=u;
        }
        if(owner==null || !"FARM_OWNER".equals(owner.get("role")) || !"true".equals(owner.get("active")))throw new SecurityException("Farm owner required.");
        if(member==null || !owner.get("farm_id").equals(member.get("farm_id")) || !"false".equals(member.get("active")) || !"VIEWER".equals(member.get("role")))throw new SecurityException("Pending farm member not found.");
        member.put("active",Boolean.toString(approve));member.put("role",approve?role:"REJECTED");persist();
    }
    @Override public synchronized Map<String,String> bySocialIdentity(String provider,String subject) {
        for(Map<String,String> link:state.rows.getOrDefault("social_identities",List.of())){
            if(provider.equals(link.get("provider")) && subject.equals(link.get("subject"))){
                for(Map<String,String> user:state.rows.get("users"))if(user.get("id").equals(link.get("user_id")))return new HashMap<>(user);
            }
        }
        return null;
    }
    @Override public synchronized RecoveryDelivery issuePhoneLoginCode(String phone) throws Exception {
        if(phone==null || !ContactValidation.validPhone(phone))return null;
        List<Map<String,String>> matches=state.rows.get("users").stream().filter(u->phone.equals(u.get("phone")) &&
            "true".equalsIgnoreCase(u.get("active")) && "true".equalsIgnoreCase(u.get("phone_verified"))).toList();
        if(matches.size()!=1)return null;
        Map<String,String> user=matches.get(0);java.time.Instant now=java.time.Instant.now();int count=0;
        for(Map<String,String> r:state.rows.get("phone_login_codes"))if(user.get("id").equals(r.get("user_id")))try{
            java.time.Instant at=java.time.Instant.parse(r.get("created_at"));
            if(at.isAfter(now.minusSeconds(120)))return null;
            if(at.isAfter(now.minusSeconds(86400)))count++;
        }catch(Exception ignored){}
        if(count>=5)return null;
        for(Map<String,String> r:state.rows.get("phone_login_codes"))if(user.get("id").equals(r.get("user_id")) && "APPROVED".equals(r.get("status"))){r.put("status","EXPIRED");r.remove("code_hash");}
        String code=RecoveryCodes.create();Map<String,String> request=internalAdd("phone_login_codes",row(
            "user_id",user.get("id"),"code_hash",RecoveryCodes.digest(code),"expires_at",now.plusSeconds(RecoveryCodes.LIFETIME_MINUTES*60L).toString(),
            "attempts","0","created_at",now.toString(),"status","APPROVED"));
        persist();return new RecoveryDelivery(request.get("id"),phone,code);
    }
    @Override public synchronized void cancelPhoneLoginCode(String requestId) throws Exception {
        for(Map<String,String> r:state.rows.get("phone_login_codes"))if(requestId.equals(r.get("id")) && "APPROVED".equals(r.get("status"))){r.put("status","FAILED");r.remove("code_hash");persist();}
    }
    @Override public synchronized Map<String,String> redeemPhoneLoginCode(String phone,String code) throws Exception {
        if(phone==null)return null;
        List<Map<String,String>> matches=state.rows.get("users").stream().filter(u->phone.equals(u.get("phone")) && "true".equalsIgnoreCase(u.get("active")) && "true".equalsIgnoreCase(u.get("phone_verified"))).toList();
        if(matches.size()!=1)return null;
        Map<String,String> user=matches.get(0);
        List<Map<String,String>> requests=state.rows.get("phone_login_codes");
        for(int i=requests.size()-1;i>=0;i--){
            Map<String,String> r=requests.get(i);
            if(!user.get("id").equals(r.get("user_id")) || !"APPROVED".equals(r.get("status")))continue;
            if(RecoveryCodes.expired(r.get("expires_at"))){r.put("status","EXPIRED");r.remove("code_hash");persist();return null;}
            int attempts=Integer.parseInt(r.getOrDefault("attempts","0"));
            if(attempts>=RecoveryCodes.MAX_ATTEMPTS){r.put("status","LOCKED");persist();return null;}
            if(!RecoveryCodes.matches(code,r.get("code_hash"))){r.put("attempts",Integer.toString(attempts+1));if(attempts+1>=RecoveryCodes.MAX_ATTEMPTS)r.put("status","LOCKED");persist();return null;}
            r.put("status","USED");r.remove("code_hash");persist();return new HashMap<>(user);
        }
        return null;
    }
    @Override public String mode(){return "LOCAL DEMO · saved on this computer";}
}
