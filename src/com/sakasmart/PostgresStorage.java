package com.sakasmart;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** PostgreSQL implementation. Uses real SQL tables and an atomic sale transaction. */
public final class PostgresStorage implements Storage {
    private final String url, user, password;
    public PostgresStorage() throws Exception {
        url = System.getenv().getOrDefault("SAKA_DB_URL","jdbc:postgresql://localhost:5432/sakasmart");
        user = System.getenv().getOrDefault("SAKA_DB_USER","postgres");
        password = System.getenv().getOrDefault("SAKA_DB_PASSWORD","");
        Class.forName("org.postgresql.Driver");
        try(Connection c = open()) { init(c); }
    }
    private Connection open() throws SQLException {return DriverManager.getConnection(url,user,password);}
    private void init(Connection c) throws Exception {
        for(Schema.Definition d:Schema.TABLES.values()) {
            StringBuilder sql=new StringBuilder("CREATE TABLE IF NOT EXISTS "+d.table()+" (id BIGSERIAL PRIMARY KEY");
            for(Schema.Field f:d.fields()) {
                sql.append(", ").append(f.key()).append(' ').append(Schema.sqlType(f));
                if("username".equals(f.key())&&"users".equals(d.table()))sql.append(" UNIQUE");
                if("farm_id".equals(f.key()))sql.append(" REFERENCES farms(id)");
                if("item_id".equals(f.key())&&"sales".equals(d.table()))sql.append(" REFERENCES inventory(id)");
                if("user_id".equals(f.key())&&Set.of("password_reset_requests","social_identities","phone_login_codes").contains(d.table()))sql.append(" REFERENCES users(id)");
            }
            sql.append(')');
            try(Statement st=c.createStatement()) {st.execute(sql.toString());}
        }
        // Non-destructive migration for installations from V7-V10.
        // Existing accounts and database records remain untouched.
        try(Statement st=c.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone TEXT");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN DEFAULT FALSE");
            st.execute("ALTER TABLE logs ADD COLUMN IF NOT EXISTS farm_id BIGINT REFERENCES farms(id)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS saka_social_subject_uniq ON social_identities(provider,subject)");
        }
        try(Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM users")){
            rs.next();if(rs.getLong(1)>0)return;
        }
        Map<String,String> farm=doAdd(c,"farms",Map.of("name","My Farm","location","Philippines","owner","Farm Owner","area_ha","0","crops","","livestock",""));
        String id=farm.get("id");
        doAdd(c,"users",Map.of("username","admin","full_name","System Administrator","password_hash",Security.hash("Admin@123".toCharArray()),"role","SYSTEM_ADMIN","active","true","farm_id",id));
        doAdd(c,"users",Map.of("username","farmer","full_name","Farm Owner","password_hash",Security.hash("Farmer@123".toCharArray()),"role","FARM_OWNER","active","true","farm_id",id));
    }
    @Override public List<Map<String,String>> all(String table) throws Exception {
        checkTable(table);
        List<Map<String,String>> result = new ArrayList<>();
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT * FROM "+table+" ORDER BY id DESC");ResultSet rs=p.executeQuery()){
            while(rs.next()) {
                Map<String,String> row=new LinkedHashMap<>();row.put("id",rs.getString("id"));
                for(Schema.Field f:Schema.TABLES.get(table).fields()) {
                    if(f.type()==Schema.Type.BOOL){boolean value=rs.getBoolean(f.key());row.put(f.key(),rs.wasNull()?"":Boolean.toString(value));}
                    else row.put(f.key(),Objects.toString(rs.getString(f.key()),""));
                }
                result.add(row);
            }
        }
        return result;
    }
    private static void checkTable(String table) {
        if(!Schema.TABLES.containsKey(table))throw new IllegalArgumentException("Unknown table: "+table);
    }
    private static void bind(PreparedStatement p,int index,Schema.Field field,String value) throws SQLException {
        if(value==null||value.isBlank()) {p.setNull(index,switch(field.type()) {case NUMBER -> ("user_id".equals(field.key())?Types.BIGINT:Types.NUMERIC);case DATE -> Types.DATE;case BOOL -> Types.BOOLEAN;default -> Types.VARCHAR;});return;}
        if("user_id".equals(field.key())){p.setLong(index,Long.parseLong(value));return;}
        switch(field.type()) {
            case NUMBER -> p.setBigDecimal(index,new BigDecimal(value));
            case DATE -> p.setDate(index,java.sql.Date.valueOf(value));
            case BOOL -> p.setBoolean(index,Boolean.parseBoolean(value));
            default -> p.setString(index,value);
        }
    }
    private static Map<String,String> doAdd(Connection c,String table,Map<String,String> values) throws Exception {
        checkTable(table);
        List<Schema.Field> fields = Schema.TABLES.get(table).fields().stream().filter(f->values.containsKey(f.key())).toList();
        String cols=String.join(",",fields.stream().map(Schema.Field::key).toList());
        String placeholders=String.join(",",Collections.nCopies(fields.size(),"?"));
        String sql="INSERT INTO "+table+" ("+cols+") VALUES ("+placeholders+") RETURNING id";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            int i=1;for(Schema.Field f:fields)bind(p,i++,f,values.get(f.key()));
            try(ResultSet rs=p.executeQuery()) {rs.next();Map<String,String> out=new LinkedHashMap<>(values);out.put("id",rs.getString(1));return out;}
        }
    }
    @Override public Map<String,String> add(String table,Map<String,String> values) throws Exception {
        try(Connection c=open()){return doAdd(c,table,values);}
    }
    @Override public void edit(String table,String id,Map<String,String> values) throws Exception {
        checkTable(table);
        List<Schema.Field> fields=Schema.TABLES.get(table).fields().stream().filter(f->values.containsKey(f.key())).toList();
        if(fields.isEmpty())return;
        StringBuilder sql=new StringBuilder("UPDATE "+table+" SET ");
        sql.append(String.join(",",fields.stream().map(f->f.key()+"=?").toList())).append(" WHERE id=?");
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                boolean contactChanged=false;
                if("users".equals(table) && (values.containsKey("email")||values.containsKey("phone"))) {
                    try(PreparedStatement q=c.prepareStatement("SELECT email,phone FROM users WHERE id=? FOR UPDATE")) {
                        q.setLong(1,Long.parseLong(id));
                        try(ResultSet r=q.executeQuery()) {
                            if(r.next()){
                                if(values.containsKey("email") && !Objects.equals(Objects.toString(r.getString(1),""),values.get("email")))contactChanged=true;
                                if(values.containsKey("phone") && !Objects.equals(Objects.toString(r.getString(2),""),values.get("phone")))contactChanged=true;
                            }
                        }
                    }
                }
                try(PreparedStatement p=c.prepareStatement(sql.toString())) {
                    int i=1;for(Schema.Field f:fields)bind(p,i++,f,values.get(f.key()));
                    p.setLong(i,Long.parseLong(id));
                    if(p.executeUpdate()==0)throw new IllegalArgumentException("Record not found.");
                }
                if(contactChanged){
                    try(PreparedStatement q=c.prepareStatement(
                        "UPDATE password_reset_requests SET status='EXPIRED',code_hash=NULL WHERE user_id=? AND status='APPROVED'")) {
                        q.setLong(1,Long.parseLong(id));q.executeUpdate();
                    }
                }
                c.commit();
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
    @Override public void remove(String table,String id) throws Exception {
        checkTable(table);
        try(Connection c=open();PreparedStatement p=c.prepareStatement("DELETE FROM "+table+" WHERE id=?")) {
            p.setLong(1,Long.parseLong(id));if(p.executeUpdate()==0)throw new IllegalArgumentException("Record not found.");
        }
    }
    @Override public void sell(String itemId,String buyer,double qty,double unitPrice,String date,String actor) throws Exception {
        if(qty<=0||unitPrice<0)throw new IllegalArgumentException("Enter valid quantity and price.");
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                BigDecimal available;String farmId;
                try(PreparedStatement p=c.prepareStatement("SELECT quantity, kind, farm_id FROM inventory WHERE id=? FOR UPDATE")){
                    p.setLong(1,Long.parseLong(itemId));
                    try(ResultSet rs=p.executeQuery()) {
                        if(!rs.next()||!"HARVEST".equals(rs.getString("kind")))throw new IllegalArgumentException("Select a harvested product.");
                        available=rs.getBigDecimal("quantity");farmId=rs.getString("farm_id");
                    }
                }
                BigDecimal q=BigDecimal.valueOf(qty);
                if(available.compareTo(q)<0)throw new IllegalArgumentException("Insufficient stock. Available: "+available);
                try(PreparedStatement p=c.prepareStatement("UPDATE inventory SET quantity=? WHERE id=?")){
                    p.setBigDecimal(1,available.subtract(q));p.setLong(2,Long.parseLong(itemId));p.executeUpdate();
                }
                Map<String,String> sale=doAdd(c,"sales",Map.of("item_id",itemId,"buyer",buyer,"quantity",Double.toString(qty),"unit_price",Double.toString(unitPrice),"date",date,"farm_id",farmId));
                doAdd(c,"transactions",Map.of("type","Income","category","Product Sale","amount",BigDecimal.valueOf(qty).multiply(BigDecimal.valueOf(unitPrice)).toPlainString(),"date",date,"notes","Sale to "+buyer,"sale_id",sale.get("id"),"farm_id",farmId));
                doAdd(c,"logs",Map.of("actor",actor,"action","Recorded sale to "+buyer,"date",java.time.LocalDateTime.now().toString().replace('T',' '),"farm_id",farmId));
                c.commit();
            } catch(Exception ex) {c.rollback();throw ex;}
            finally {c.setAutoCommit(true);}
        }
    }
    private static void requireAdministrator(Connection c,String admin) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT role, active FROM users WHERE LOWER(username)=LOWER(?)")){
            p.setString(1,admin);
            try(ResultSet r=p.executeQuery()){
                if(!r.next() || !"SYSTEM_ADMIN".equals(r.getString(1)) || !r.getBoolean(2))
                    throw new SecurityException("Administrator access required.");
            }
        }
    }
    @Override public void requestPasswordReset(String username) throws Exception {
        if(username==null || username.isBlank())return;
        // The INSERT only succeeds for active users without an existing pending request.
        try(Connection c=open();PreparedStatement p=c.prepareStatement(
            "INSERT INTO password_reset_requests (user_id,username,status,requested_at,attempts) "+
            "SELECT u.id,u.username,'PENDING',?,0 FROM users u WHERE LOWER(u.username)=LOWER(?) "+
            "AND u.active=TRUE AND NOT EXISTS (SELECT 1 FROM password_reset_requests r WHERE r.user_id=u.id AND r.status='PENDING')")){
            p.setString(1,RecoveryCodes.utcNow());p.setString(2,username.trim());p.executeUpdate();
        }
    }
    @Override public String approvePasswordReset(String requestId,String admin) throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                requireAdministrator(c,admin);
                String status;
                try(PreparedStatement p=c.prepareStatement("SELECT status FROM password_reset_requests WHERE id=? FOR UPDATE")){
                    p.setLong(1,Long.parseLong(requestId));
                    try(ResultSet r=p.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("Request not found.");
                        status=r.getString(1);
                    }
                }
                if(!"PENDING".equals(status))throw new IllegalStateException("Only pending requests can be approved.");
                String code=RecoveryCodes.create();
                try(PreparedStatement p=c.prepareStatement(
                    "UPDATE password_reset_requests SET status='APPROVED',code_hash=?,expires_at=?,attempts=0,handled_by=?,handled_at=? WHERE id=?")){
                    p.setString(1,RecoveryCodes.digest(code));
                    p.setString(2,java.time.Instant.now().plusSeconds(RecoveryCodes.LIFETIME_MINUTES*60L).toString());
                    p.setString(3,admin);p.setString(4,RecoveryCodes.utcNow());p.setLong(5,Long.parseLong(requestId));p.executeUpdate();
                }
                c.commit();return code;
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    @Override public void rejectPasswordReset(String requestId,String admin) throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                requireAdministrator(c,admin);
                try(PreparedStatement p=c.prepareStatement(
                    "UPDATE password_reset_requests SET status='REJECTED',handled_by=?,handled_at=? WHERE id=? AND status='PENDING'")){
                    p.setString(1,admin);p.setString(2,RecoveryCodes.utcNow());p.setLong(3,Long.parseLong(requestId));
                    if(p.executeUpdate()!=1)throw new IllegalStateException("Pending request not found.");
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    @Override public boolean redeemPasswordReset(String username,String code,char[] password) throws Exception {
        if(password==null || password.length<8)throw new IllegalArgumentException("Password must have at least 8 characters.");
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                long userId;
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE LOWER(username)=LOWER(?) AND active=TRUE FOR UPDATE")){
                    p.setString(1,username.trim());
                    try(ResultSet r=p.executeQuery()){
                        if(!r.next()){c.rollback();return false;}
                        userId=r.getLong(1);
                    }
                }
                try(PreparedStatement p=c.prepareStatement(
                    "SELECT id,code_hash,expires_at,attempts FROM password_reset_requests WHERE user_id=? AND status='APPROVED' ORDER BY id DESC FOR UPDATE")){
                    p.setLong(1,userId);
                    try(ResultSet r=p.executeQuery()){
                        if(!r.next()){c.rollback();return false;}
                        long requestId=r.getLong(1);
                        String stored=r.getString(2),expiry=r.getString(3);
                        int attempts=r.getInt(4);
                        if(RecoveryCodes.expired(expiry) || attempts>=RecoveryCodes.MAX_ATTEMPTS){
                            try(PreparedStatement x=c.prepareStatement("UPDATE password_reset_requests SET status=? WHERE id=?")){
                                x.setString(1,attempts>=RecoveryCodes.MAX_ATTEMPTS?"LOCKED":"EXPIRED");x.setLong(2,requestId);x.executeUpdate();
                            }
                            c.commit();return false;
                        }
                        if(!RecoveryCodes.matches(code,stored)){
                            try(PreparedStatement x=c.prepareStatement(
                                "UPDATE password_reset_requests SET attempts=?,status=? WHERE id=?")){
                                x.setInt(1,attempts+1);x.setString(2,attempts+1>=RecoveryCodes.MAX_ATTEMPTS?"LOCKED":"APPROVED");
                                x.setLong(3,requestId);x.executeUpdate();
                            }
                            c.commit();return false;
                        }
                        try(PreparedStatement x=c.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")){
                            x.setString(1,Security.hash(password));x.setLong(2,userId);x.executeUpdate();
                        }
                        try(PreparedStatement x=c.prepareStatement(
                            "UPDATE password_reset_requests SET status='USED',code_hash=NULL WHERE id=?")){
                            x.setLong(1,requestId);x.executeUpdate();
                        }
                        try(PreparedStatement x=c.prepareStatement(
                            "UPDATE password_reset_requests SET status='EXPIRED',code_hash=NULL WHERE user_id=? AND status='APPROVED'")){
                            x.setLong(1,userId);x.executeUpdate();
                        }
                        c.commit();return true;
                    }
                }
            }catch(Exception e){c.rollback();throw e;}
        }
    }

    @Override public RecoveryDelivery issueRecoveryToVerifiedContact(String username,String channel) throws Exception {
        if(!"EMAIL".equals(channel) && !"SMS".equals(channel))throw new IllegalArgumentException("Invalid channel.");
        if(username==null || username.isBlank())return null;
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                long id;
                String contact;
                boolean verified;
                try(PreparedStatement p=c.prepareStatement(
                    "SELECT id,email,email_verified,phone,phone_verified FROM users WHERE LOWER(username)=LOWER(?) AND active=TRUE FOR UPDATE")) {
                    p.setString(1,username.trim());
                    try(ResultSet r=p.executeQuery()) {
                        if(!r.next()){c.rollback();return null;}
                        id=r.getLong("id");
                        contact=r.getString("EMAIL".equals(channel)?"email":"phone");
                        verified=r.getBoolean("EMAIL".equals(channel)?"email_verified":"phone_verified");
                    }
                }
                if(!verified || contact==null || contact.isBlank()){c.rollback();return null;}
                java.time.Instant now=java.time.Instant.now();
                String marker=channel+"_AUTOMATED";
                int dayCount=0;
                try(PreparedStatement p=c.prepareStatement(
                    "SELECT requested_at FROM password_reset_requests WHERE user_id=? AND handled_by=? ORDER BY id DESC LIMIT 8")) {
                    p.setLong(1,id);p.setString(2,marker);
                    try(ResultSet r=p.executeQuery()) {
                        while(r.next()) {
                            try {
                                java.time.Instant time=java.time.Instant.parse(r.getString(1));
                                if(time.isAfter(now.minusSeconds(120))){c.rollback();return null;}
                                if(time.isAfter(now.minusSeconds(86400)))dayCount++;
                            }catch(Exception ignore){}
                        }
                    }
                }
                if(dayCount>=5){c.rollback();return null;}
                String code=RecoveryCodes.create();
                // Only newest code should remain valid. Requests are serialized per user.
                try(PreparedStatement p=c.prepareStatement(
                    "UPDATE password_reset_requests SET status='EXPIRED',code_hash=NULL WHERE user_id=? AND status='APPROVED'")) {
                    p.setLong(1,id);p.executeUpdate();
                }
                long requestId;
                try(PreparedStatement p=c.prepareStatement(
                    "INSERT INTO password_reset_requests (user_id,username,status,requested_at,code_hash,expires_at,attempts,handled_by,handled_at) "+
                    "VALUES (?,?,'APPROVED',?,?,?,?,?,?) RETURNING id")) {
                    p.setLong(1,id);p.setString(2,username.trim());
                    p.setString(3,RecoveryCodes.utcNow());p.setString(4,RecoveryCodes.digest(code));
                    p.setString(5,now.plusSeconds(RecoveryCodes.LIFETIME_MINUTES*60L).toString());
                    p.setInt(6,0);p.setString(7,marker);p.setString(8,RecoveryCodes.utcNow());
                    try(ResultSet r=p.executeQuery()){r.next();requestId=r.getLong(1);}
                }
                c.commit();return new RecoveryDelivery(Long.toString(requestId),contact,code);
            }catch(Exception e){c.rollback();throw e;}
        }
    }

    @Override public void cancelRecoveryDelivery(String requestId) throws Exception {
        try(Connection c=open();PreparedStatement p=c.prepareStatement(
            "UPDATE password_reset_requests SET status='FAILED',code_hash=NULL WHERE id=? AND status='APPROVED' AND handled_by IN ('EMAIL_AUTOMATED','SMS_AUTOMATED')")) {
            p.setLong(1,Long.parseLong(requestId));p.executeUpdate();
        }
    }


    @Override public List<Map<String,String>> publicFarms() throws Exception {
        List<Map<String,String>> result=new ArrayList<>();
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT id,name FROM farms ORDER BY name");ResultSet r=p.executeQuery()) {
            while(r.next())result.add(Map.of("id",r.getString(1),"name",r.getString(2)));
        }
        return result;
    }
    private static void uniqueUsername(Connection c,String name) throws Exception {
        if(name==null || !name.matches("[A-Za-z0-9_.-]{3,40}"))
            throw new IllegalArgumentException("Username must be 3-40 letters, digits, dots, underscores or dashes.");
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users WHERE LOWER(username)=LOWER(?)")){
            p.setString(1,name);try(ResultSet r=p.executeQuery()){
                if(r.next())throw new IllegalArgumentException("Username already exists.");
            }
        }
    }
    private static void linkSocial(Connection c,String userId,String provider,String subject) throws Exception {
        if(provider==null || provider.isBlank())return;
        if(!Set.of("GOOGLE","FACEBOOK").contains(provider) || subject==null || subject.isBlank())
            throw new SecurityException("Invalid social identity.");
        doAdd(c,"social_identities",Map.of("user_id",userId,"provider",provider,"subject",subject));
    }
    @Override public Map<String,String> registerOwnFarm(Map<String,String> account,Map<String,String> farm,String provider,String subject) throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                uniqueUsername(c,account.get("username"));
                Map<String,String> createdFarm=doAdd(c,"farms",farm);
                Map<String,String> values=new HashMap<>(account);
                values.put("role","FARM_OWNER");values.put("active","true");values.put("farm_id",createdFarm.get("id"));
                Map<String,String> created=doAdd(c,"users",values);
                linkSocial(c,created.get("id"),provider,subject);
                c.commit();return created;
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    @Override public Map<String,String> registerJoinFarm(Map<String,String> account,String farmId,String provider,String subject) throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                uniqueUsername(c,account.get("username"));
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM farms WHERE id=?")){
                    p.setLong(1,Long.parseLong(farmId));
                    try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Farm not found.");}
                }
                Map<String,String> values=new HashMap<>(account);
                values.put("role","VIEWER");values.put("active","false");values.put("farm_id",farmId);
                Map<String,String> created=doAdd(c,"users",values);
                linkSocial(c,created.get("id"),provider,subject);
                c.commit();return created;
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    @Override public Map<String,String> bySocialIdentity(String provider,String subject) throws Exception {
        try(Connection c=open();PreparedStatement p=c.prepareStatement(
            "SELECT u.id FROM social_identities s JOIN users u ON u.id=s.user_id WHERE s.provider=? AND s.subject=?")){
            p.setString(1,provider);p.setString(2,subject);
            try(ResultSet r=p.executeQuery()){
                if(!r.next())return null;
                String id=r.getString(1);
                try(PreparedStatement u=c.prepareStatement("SELECT * FROM users WHERE id=?")){
                    u.setLong(1,Long.parseLong(id));try(ResultSet ur=u.executeQuery()){
                        if(!ur.next())return null;
                        Map<String,String> value=new HashMap<>();value.put("id",id);
                        for(Schema.Field f:Schema.TABLES.get("users").fields()){
                            if(f.type()==Schema.Type.BOOL){boolean b=ur.getBoolean(f.key());value.put(f.key(),ur.wasNull()?"":Boolean.toString(b));}
                            else value.put(f.key(),Objects.toString(ur.getString(f.key()),""));
                        }
                        return value;
                    }
                }
            }
        }
    }
    @Override public void decideMembership(String ownerId,String memberId,String role,boolean approve) throws Exception {
        if(approve && !Set.of("FARM_MANAGER","INVENTORY_STAFF","BOOKKEEPER","VIEWER").contains(role))
            throw new SecurityException("Invalid farm staff role.");
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                long farmId;
                try(PreparedStatement p=c.prepareStatement("SELECT farm_id FROM users WHERE id=? AND role='FARM_OWNER' AND active=TRUE FOR UPDATE")){
                    p.setLong(1,Long.parseLong(ownerId));try(ResultSet r=p.executeQuery()){
                        if(!r.next())throw new SecurityException("Farm owner account required.");farmId=r.getLong(1);
                    }
                }
                try(PreparedStatement p=c.prepareStatement(
                    "UPDATE users SET active=?,role=? WHERE id=? AND farm_id=? AND active=FALSE AND role='VIEWER' AND id<>?")){
                    p.setBoolean(1,approve);p.setString(2,approve?role:"REJECTED");p.setLong(3,Long.parseLong(memberId));
                    p.setLong(4,farmId);p.setLong(5,Long.parseLong(ownerId));
                    if(p.executeUpdate()!=1)throw new SecurityException("Pending applicant not found for your farm.");
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    @Override public RecoveryDelivery issuePhoneLoginCode(String phone) throws Exception {
        if(phone==null || !ContactValidation.validPhone(phone))return null;
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                long id;
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE phone=? AND phone_verified=TRUE AND active=TRUE FOR UPDATE")){
                    p.setString(1,phone);try(ResultSet r=p.executeQuery()){
                        if(!r.next()){c.rollback();return null;}id=r.getLong(1);
                        if(r.next()){c.rollback();return null;} // no ambiguous shared numbers
                    }
                }
                java.time.Instant now=java.time.Instant.now();
                try(PreparedStatement p=c.prepareStatement("SELECT created_at FROM phone_login_codes WHERE user_id=? ORDER BY id DESC LIMIT 8")){
                    p.setLong(1,id);int count=0;
                    try(ResultSet r=p.executeQuery()){
                        while(r.next())try{
                            java.time.Instant when=java.time.Instant.parse(r.getString(1));
                            if(when.isAfter(now.minusSeconds(120))){c.rollback();return null;}
                            if(when.isAfter(now.minusSeconds(86400)))count++;
                        }catch(Exception ignored){}
                    }
                    if(count>=5){c.rollback();return null;}
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE phone_login_codes SET status='EXPIRED',code_hash=NULL WHERE user_id=? AND status='APPROVED'")){
                    p.setLong(1,id);p.executeUpdate();
                }
                String code=RecoveryCodes.create();Map<String,String> added=doAdd(c,"phone_login_codes",Map.of(
                    "user_id",Long.toString(id),"code_hash",RecoveryCodes.digest(code),
                    "expires_at",now.plusSeconds(RecoveryCodes.LIFETIME_MINUTES*60L).toString(),
                    "attempts","0","created_at",now.toString(),"status","APPROVED"));
                c.commit();return new RecoveryDelivery(added.get("id"),phone,code);
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    @Override public void cancelPhoneLoginCode(String requestId) throws Exception {
        try(Connection c=open();PreparedStatement p=c.prepareStatement(
            "UPDATE phone_login_codes SET status='FAILED',code_hash=NULL WHERE id=? AND status='APPROVED'")){
            p.setLong(1,Long.parseLong(requestId));p.executeUpdate();
        }
    }
    @Override public Map<String,String> redeemPhoneLoginCode(String phone,String code) throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                long id;
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE phone=? AND phone_verified=TRUE AND active=TRUE FOR UPDATE")){
                    p.setString(1,phone);try(ResultSet r=p.executeQuery()){
                        if(!r.next()){c.rollback();return null;}id=r.getLong(1);if(r.next()){c.rollback();return null;}
                    }
                }
                try(PreparedStatement p=c.prepareStatement("SELECT id,code_hash,expires_at,attempts FROM phone_login_codes WHERE user_id=? AND status='APPROVED' ORDER BY id DESC LIMIT 1 FOR UPDATE")){
                    p.setLong(1,id);try(ResultSet r=p.executeQuery()){
                        if(!r.next()){c.rollback();return null;}
                        String rid=r.getString(1),stored=r.getString(2),expiry=r.getString(3);int attempts=r.getInt(4);
                        if(RecoveryCodes.expired(expiry)||attempts>=RecoveryCodes.MAX_ATTEMPTS){
                            try(PreparedStatement q=c.prepareStatement("UPDATE phone_login_codes SET status='EXPIRED',code_hash=NULL WHERE id=?")){
                                q.setLong(1,Long.parseLong(rid));q.executeUpdate();
                            }c.commit();return null;
                        }
                        if(!RecoveryCodes.matches(code,stored)){
                            try(PreparedStatement q=c.prepareStatement("UPDATE phone_login_codes SET attempts=?,status=? WHERE id=?")){
                                q.setInt(1,attempts+1);q.setString(2,attempts+1>=RecoveryCodes.MAX_ATTEMPTS?"LOCKED":"APPROVED");q.setLong(3,Long.parseLong(rid));q.executeUpdate();
                            }c.commit();return null;
                        }
                        try(PreparedStatement q=c.prepareStatement("UPDATE phone_login_codes SET status='USED',code_hash=NULL WHERE id=?")){
                            q.setLong(1,Long.parseLong(rid));q.executeUpdate();
                        }
                    }
                }
                c.commit();
                return all("users").stream().filter(u->u.get("id").equals(Long.toString(id))).findFirst().orElse(null);
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    @Override public String mode(){return "POSTGRESQL · "+url;}
}
