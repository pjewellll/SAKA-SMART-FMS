package com.sakasmart;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** PostgreSQL-backed shopping cart, stock reservation and farmer order workflow.
 * Payment method intentionally starts with CASH_ON_PICKUP (no payment gateway).
 * Each checkout creates a separate order per farm. */
final class CommerceService {
    record CartLine(long itemId,long farmId,String product,String unit,BigDecimal price,
                    BigDecimal available,int quantity) {}
    record Order(long id,long farmId,String farm,String buyer,String status,
                 String method,String payment,String paymentStatus,String address,String contact,BigDecimal total,String placed) {}
    record OrderEvent(long id,String status,String actor,String changedAt,String note) {}
    record PickupLocation(long farmId,String farmName,String location) {}
    /** Get the actual farm location for purchased items, scoped to this buyer's cart. */
    List<PickupLocation> pickupLocations(String buyer,Collection<Long> itemIds,boolean buyNow) throws Exception {
        if(itemIds==null||itemIds.isEmpty())throw new IllegalArgumentException("No products selected.");
        try(Connection c=db()){
            long buyerId=account(c,buyer,"CUSTOMER");
            Map<Long,PickupLocation> farms=new LinkedHashMap<>();
            String sql=buyNow
                ?"SELECT f.id,f.name,f.location FROM inventory i JOIN farms f ON f.id=i.farm_id WHERE i.id=? AND i.kind='HARVEST' AND i.market_published=TRUE"
                :"SELECT f.id,f.name,f.location FROM inventory i JOIN farms f ON f.id=i.farm_id JOIN saka_cart ca ON ca.inventory_id=i.id WHERE i.id=? AND ca.buyer_id=?";
            for(Long itemId:new LinkedHashSet<>(itemIds)){
                if(itemId==null)throw new IllegalArgumentException("Invalid product selected.");
                try(PreparedStatement p=c.prepareStatement(sql)){
                    p.setLong(1,itemId);if(!buyNow)p.setLong(2,buyerId);
                    try(ResultSet r=p.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("The selected product is not available in your cart or marketplace.");
                        long farmId=r.getLong(1);
                        farms.putIfAbsent(farmId,new PickupLocation(farmId,r.getString(2),r.getString(3)));
                    }
                }
            }
            return new ArrayList<>(farms.values());
        }
    }
    /** Backend validation is required even when the UI restricts typing. */
    private static String checkedMobile(String phone){
        String number=phone==null?"":phone.trim();
        if(!number.matches("09[0-9]{9}"))
            throw new IllegalArgumentException("Enter a valid 11-digit Philippine mobile number starting with 09 (e.g. 09123456789).");
        return number;
    }

    private final String url=System.getenv().getOrDefault("SAKA_DB_URL","jdbc:postgresql://localhost:5432/sakasmart");
    private final String user=System.getenv().getOrDefault("SAKA_DB_USER","postgres");
    private final String password=System.getenv().getOrDefault("SAKA_DB_PASSWORD","");
    private Connection db() throws SQLException {return DriverManager.getConnection(url,user,password);}
    CommerceService() throws SQLException {
        if(!"postgres".equalsIgnoreCase(System.getenv().getOrDefault("SAKA_MODE","")))
            throw new SQLException("Shopping requires PostgreSQL mode.");
        try(Connection c=db();Statement s=c.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS saka_cart (buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, inventory_id BIGINT NOT NULL REFERENCES inventory(id), quantity INTEGER NOT NULL CHECK(quantity>0), PRIMARY KEY(buyer_id,inventory_id))");
            s.execute("CREATE TABLE IF NOT EXISTS saka_orders (id BIGSERIAL PRIMARY KEY, buyer_id BIGINT NOT NULL REFERENCES users(id), farm_id BIGINT NOT NULL REFERENCES farms(id), full_name TEXT NOT NULL, phone TEXT NOT NULL, delivery_mode TEXT NOT NULL CHECK(delivery_mode IN ('PICKUP','DELIVERY')), address TEXT NOT NULL DEFAULT '', payment_method TEXT NOT NULL DEFAULT 'CASH_ON_PICKUP', notes TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'PENDING', total NUMERIC(16,2) NOT NULL CHECK(total>=0), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), completed_at TIMESTAMPTZ)");
            s.execute("CREATE TABLE IF NOT EXISTS saka_order_items (id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES saka_orders(id), inventory_id BIGINT NOT NULL REFERENCES inventory(id), product_name TEXT NOT NULL, unit TEXT NOT NULL, quantity NUMERIC(16,2) NOT NULL CHECK(quantity>0), unit_price NUMERIC(16,2) NOT NULL CHECK(unit_price>=0))");
            // V16 additive transaction fields. Existing orders remain intact.
            s.execute("ALTER TABLE saka_orders ADD COLUMN IF NOT EXISTS payment_status TEXT NOT NULL DEFAULT 'UNPAID'");
            s.execute("ALTER TABLE saka_orders ADD COLUMN IF NOT EXISTS payment_confirmed_at TIMESTAMPTZ");
            s.execute("ALTER TABLE saka_orders ADD COLUMN IF NOT EXISTS payment_confirmed_by TEXT NOT NULL DEFAULT ''");
            s.execute("ALTER TABLE saka_orders ADD COLUMN IF NOT EXISTS barangay TEXT NOT NULL DEFAULT ''");
            s.execute("ALTER TABLE saka_orders ADD COLUMN IF NOT EXISTS municipality TEXT NOT NULL DEFAULT ''");
            s.execute("ALTER TABLE saka_orders ADD COLUMN IF NOT EXISTS province TEXT NOT NULL DEFAULT ''");
            s.execute("ALTER TABLE saka_orders ADD COLUMN IF NOT EXISTS landmark TEXT NOT NULL DEFAULT ''");
            s.execute("ALTER TABLE saka_orders ADD COLUMN IF NOT EXISTS delivery_fee NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK(delivery_fee>=0)");
            s.execute("UPDATE saka_orders SET payment_method=CASE WHEN delivery_mode='PICKUP' THEN 'CASH_ON_PICKUP' ELSE 'CASH_ON_DELIVERY' END WHERE payment_method='CASH'");
            s.execute("ALTER TABLE saka_orders DROP CONSTRAINT IF EXISTS saka_orders_status_check");
            s.execute("ALTER TABLE saka_orders ADD CONSTRAINT saka_orders_status_check CHECK(status IN ('PENDING','ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY','DELIVERED','PICKED_UP','COMPLETED','CANCELLED','REJECTED'))");
            s.execute("CREATE TABLE IF NOT EXISTS saka_order_notifications (id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES saka_orders(id) ON DELETE CASCADE, recipient_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, title TEXT NOT NULL, message TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), read_at TIMESTAMPTZ)");
            s.execute("CREATE INDEX IF NOT EXISTS saka_order_notifications_user_idx ON saka_order_notifications(recipient_user_id,created_at DESC)");
            s.execute("CREATE INDEX IF NOT EXISTS saka_orders_buyer_idx ON saka_orders(buyer_id,created_at DESC)");
            s.execute("CREATE INDEX IF NOT EXISTS saka_orders_farm_idx ON saka_orders(farm_id,created_at DESC)");
            // Additive migration. No existing orders, farm records or sale rows are changed.
            // Create the history table before altering its status constraint. This also
            // makes first-run installation safe when no history table exists yet.
            s.execute("CREATE TABLE IF NOT EXISTS saka_order_status_history (id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES saka_orders(id) ON DELETE CASCADE, status TEXT NOT NULL, actor TEXT NOT NULL, changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), note TEXT NOT NULL DEFAULT '')");
            s.execute("ALTER TABLE saka_order_status_history DROP CONSTRAINT IF EXISTS saka_order_status_history_status_check");
            s.execute("ALTER TABLE saka_order_status_history ADD CONSTRAINT saka_order_status_history_status_check CHECK(status IN ('PENDING','ACCEPTED','PREPARING','READY','OUT_FOR_DELIVERY','DELIVERED','PICKED_UP','COMPLETED','CANCELLED','REJECTED'))");
            s.execute("CREATE INDEX IF NOT EXISTS saka_order_history_idx ON saka_order_status_history(order_id,changed_at,id)");
            // Existing V15 orders had no historical events. Record their current state as
            // an imported snapshot now, never pretend it was changed on an earlier date.
            s.execute("INSERT INTO saka_order_status_history(order_id,status,actor,note) SELECT o.id,o.status,'SYSTEM','Existing order status imported when tracking history was enabled' FROM saka_orders o WHERE NOT EXISTS (SELECT 1 FROM saka_order_status_history h WHERE h.order_id=o.id)");
        }
    }
    private static long account(Connection c,String username,String neededRole) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT id,role,active FROM users WHERE LOWER(username)=LOWER(?)")){
            p.setString(1,username);try(ResultSet r=p.executeQuery()){
                if(!r.next()||!r.getBoolean("active"))throw new SecurityException("Active account required.");
                String role=r.getString("role");
                if(neededRole!=null&&!neededRole.equals(role))throw new SecurityException(neededRole+" account required.");
                return r.getLong("id");
            }}
    }
    private static long ownerFarm(Connection c,String username) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT farm_id FROM users WHERE LOWER(username)=LOWER(?) AND role='FARM_OWNER' AND active=TRUE")){
            p.setString(1,username);try(ResultSet r=p.executeQuery()){
                if(!r.next()||r.getObject(1)==null)throw new SecurityException("Farm owner account required.");
                return r.getLong(1);
            }}
    }
    private static void event(Connection c,long orderId,String status,String actor,String note) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO saka_order_status_history(order_id,status,actor,note) VALUES (?,?,?,?)")) {
            p.setLong(1,orderId);p.setString(2,status);p.setString(3,actor);p.setString(4,note);
            if(p.executeUpdate()!=1)throw new SQLException("Unable to save order tracking event.");
        }
    }
    /** Returns only the authenticated buyer's orders or this owner's farm orders. */
    List<OrderEvent> history(String username,long orderId,boolean owner) throws Exception {
        try(Connection c=db()) {
            long id=owner?ownerFarm(c,username):account(c,username,"CUSTOMER");
            String column=owner?"farm_id":"buyer_id";
            String sql="SELECT h.id,h.status,h.actor,to_char(h.changed_at AT TIME ZONE 'Asia/Manila','YYYY-MM-DD HH24:MI:SS'),h.note FROM saka_order_status_history h JOIN saka_orders o ON o.id=h.order_id WHERE o.id=? AND o."+column+"=? ORDER BY h.changed_at,h.id";
            List<OrderEvent> result=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement(sql)) {
                p.setLong(1,orderId);p.setLong(2,id);
                try(ResultSet r=p.executeQuery()) {while(r.next()) result.add(new OrderEvent(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)));}
            }
            if(result.isEmpty())throw new SecurityException("Order not found or inaccessible.");
            return result;
        }
    }
    private static BigDecimal money(BigDecimal v){return v.setScale(2,java.math.RoundingMode.HALF_UP);}
    /** Adding to cart does not reserve inventory. Validate the combined cart quantity
     * under the inventory row lock; checkout revalidates availability atomically. */
    void add(String buyer,long itemId,int quantity) throws Exception {
        if(quantity<=0||quantity>100000)throw new IllegalArgumentException("Enter a valid quantity.");
        try(Connection c=db()){
            c.setAutoCommit(false);
            try{
                long buyerId=account(c,buyer,"CUSTOMER");
                BigDecimal stock;
                try(PreparedStatement p=c.prepareStatement("SELECT quantity,market_price FROM inventory WHERE id=? AND kind='HARVEST' AND market_published=TRUE AND (expiry_date IS NULL OR expiry_date>=CURRENT_DATE) FOR UPDATE")){
                    p.setLong(1,itemId);
                    try(ResultSet r=p.executeQuery()){
                        if(!r.next()||r.getBigDecimal(2)==null||r.getBigDecimal(2).signum()<=0)
                            throw new IllegalArgumentException("Product is not available for purchase.");
                        stock=r.getBigDecimal(1);
                    }
                }
                int existing=0;
                try(PreparedStatement p=c.prepareStatement("SELECT quantity FROM saka_cart WHERE buyer_id=? AND inventory_id=?")){
                    p.setLong(1,buyerId);p.setLong(2,itemId);
                    try(ResultSet r=p.executeQuery()){if(r.next())existing=r.getInt(1);}
                }
                if((long)existing+quantity>100000||stock.compareTo(BigDecimal.valueOf((long)existing+quantity))<0)
                    throw new IllegalArgumentException("Not enough stock for this quantity (including items already in your cart).");
                try(PreparedStatement p=c.prepareStatement("INSERT INTO saka_cart(buyer_id,inventory_id,quantity) VALUES (?,?,?) ON CONFLICT (buyer_id,inventory_id) DO UPDATE SET quantity=saka_cart.quantity+EXCLUDED.quantity")){
                    p.setLong(1,buyerId);p.setLong(2,itemId);p.setInt(3,quantity);p.executeUpdate();
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    List<CartLine> cart(String buyer) throws Exception {
        try(Connection c=db()){
            long id=account(c,buyer,"CUSTOMER");List<CartLine> lines=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("SELECT i.id,i.farm_id,i.name,i.unit,i.market_price,i.quantity,c.quantity FROM saka_cart c JOIN inventory i ON i.id=c.inventory_id WHERE c.buyer_id=? ORDER BY i.farm_id,i.id")){
                p.setLong(1,id);try(ResultSet r=p.executeQuery()){while(r.next())lines.add(new CartLine(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getBigDecimal(6),r.getInt(7)));}}
            return lines;
        }
    }
    void change(String buyer,long productId,int qty) throws Exception {
        try(Connection c=db()){
            long id=account(c,buyer,"CUSTOMER");
            if(qty<=0){try(PreparedStatement p=c.prepareStatement("DELETE FROM saka_cart WHERE buyer_id=? AND inventory_id=?")){p.setLong(1,id);p.setLong(2,productId);p.executeUpdate();}}
            else{if(qty>100000)throw new IllegalArgumentException("Quantity too large.");
                try(PreparedStatement p=c.prepareStatement("UPDATE saka_cart SET quantity=? WHERE buyer_id=? AND inventory_id=?")){p.setInt(1,qty);p.setLong(2,id);p.setLong(3,productId);p.executeUpdate();}}
        }
    }
    /** Reserves selected products atomically and creates farm-specific orders. */
    List<Long> checkout(String buyer,Collection<Long> selected,String fullName,String phone,String method,String address,String notes) throws Exception {
        return checkout(buyer,selected,fullName,phone,method,address,notes,"PICKUP".equals(method)?"CASH_ON_PICKUP":"CASH_ON_DELIVERY");
    }
    List<Long> checkout(String buyer,Collection<Long> selected,String fullName,String phone,String method,String address,String notes,String paymentMethod) throws Exception {
        if(!Set.of("CASH_ON_PICKUP","CASH_ON_DELIVERY").contains(paymentMethod))throw new IllegalArgumentException("Choose a supported cash payment method.");
        if(selected==null||selected.isEmpty())throw new IllegalArgumentException("Select at least one cart product.");
        if(fullName==null||fullName.isBlank())throw new IllegalArgumentException("Enter the buyer's full name.");
        String mobile=checkedMobile(phone);
        if(!"PICKUP".equals(method)&&!"DELIVERY".equals(method))throw new IllegalArgumentException("Invalid delivery option.");
        if("DELIVERY".equals(method)&&(address==null||address.isBlank()))throw new IllegalArgumentException("Delivery address is required.");
        if(notes!=null&&notes.length()>500)throw new IllegalArgumentException("Order notes are too long.");
        try(Connection c=db()){
            c.setAutoCommit(false);
            try{
                long buyerId=account(c,buyer,"CUSTOMER");
                List<Long> itemIds=selected.stream().distinct().sorted().toList();
                Map<Long,List<CartLine>> farms=new LinkedHashMap<>();
                // Lock inventory consistently to avoid overselling/deadlocks.
                for(long itemId:itemIds){
                    String sql="SELECT i.id,i.farm_id,i.name,i.unit,i.market_price,i.quantity,ca.quantity FROM inventory i JOIN saka_cart ca ON ca.inventory_id=i.id AND ca.buyer_id=? WHERE i.id=? AND i.kind='HARVEST' AND i.market_published=TRUE AND (i.expiry_date IS NULL OR i.expiry_date>=CURRENT_DATE) FOR UPDATE OF i,ca";
                    try(PreparedStatement p=c.prepareStatement(sql)){
                        p.setLong(1,buyerId);p.setLong(2,itemId);
                        try(ResultSet r=p.executeQuery()){
                            if(!r.next())throw new IllegalArgumentException("A selected item is no longer listed in your cart.");
                            CartLine line=new CartLine(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getBigDecimal(6),r.getInt(7));
                            if(line.price().signum()<=0||line.quantity()<=0||line.available().compareTo(BigDecimal.valueOf(line.quantity()))<0)
                                throw new IllegalArgumentException("Not enough stock for "+line.product());
                            farms.computeIfAbsent(line.farmId(),k->new ArrayList<>()).add(line);
                        }
                    }
                }
                List<Long> ids=new ArrayList<>();
                for(var entry:farms.entrySet()){
                    BigDecimal total=BigDecimal.ZERO;
                    for(CartLine line:entry.getValue())total=total.add(line.price().multiply(BigDecimal.valueOf(line.quantity())));
                    long orderId;
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO saka_orders(buyer_id,farm_id,full_name,phone,delivery_mode,address,payment_method,notes,total) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id")){
                        p.setLong(1,buyerId);p.setLong(2,entry.getKey());p.setString(3,fullName.trim());p.setString(4,mobile);p.setString(5,method);p.setString(6,address==null?"":address.trim());p.setString(7,paymentMethod);p.setString(8,notes==null?"":notes.trim());p.setBigDecimal(9,money(total));
                        try(ResultSet r=p.executeQuery()){r.next();orderId=r.getLong(1);}
                    }
                    event(c,orderId,"PENDING",buyer,"Customer placed an order; stock reserved");
                    for(CartLine line:entry.getValue()){
                        try(PreparedStatement p=c.prepareStatement("UPDATE inventory SET quantity=quantity-? WHERE id=? AND quantity>=?")){
                            p.setBigDecimal(1,BigDecimal.valueOf(line.quantity()));p.setLong(2,line.itemId());p.setBigDecimal(3,BigDecimal.valueOf(line.quantity()));
                            if(p.executeUpdate()!=1)throw new IllegalArgumentException("Insufficient stock for "+line.product());
                        }
                        try(PreparedStatement p=c.prepareStatement("INSERT INTO saka_order_items(order_id,inventory_id,product_name,unit,quantity,unit_price) VALUES (?,?,?,?,?,?)")){
                            p.setLong(1,orderId);p.setLong(2,line.itemId());p.setString(3,line.product());p.setString(4,line.unit());p.setBigDecimal(5,BigDecimal.valueOf(line.quantity()));p.setBigDecimal(6,line.price());p.executeUpdate();
                        }
                        try(PreparedStatement p=c.prepareStatement("DELETE FROM saka_cart WHERE buyer_id=? AND inventory_id=?")){
                            p.setLong(1,buyerId);p.setLong(2,line.itemId());p.executeUpdate();
                        }
                    }
                    ids.add(orderId);
                }
                c.commit();return ids;
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
    /** Buy Now creates a single order without changing the buyer's saved cart. */
    long buyNow(String buyer,long itemId,int quantity,String fullName,String phone,
                String method,String address,String notes) throws Exception {
        return buyNow(buyer,itemId,quantity,fullName,phone,method,address,notes,"PICKUP".equals(method)?"CASH_ON_PICKUP":"CASH_ON_DELIVERY");
    }
    long buyNow(String buyer,long itemId,int quantity,String fullName,String phone,
                String method,String address,String notes,String paymentMethod) throws Exception {
        if(!Set.of("CASH_ON_PICKUP","CASH_ON_DELIVERY").contains(paymentMethod))throw new IllegalArgumentException("Choose a supported cash payment method.");
        if(quantity<=0||quantity>100000)throw new IllegalArgumentException("Enter a valid quantity.");
        if(fullName==null||fullName.isBlank())throw new IllegalArgumentException("Enter your full name.");
        String mobile=checkedMobile(phone);
        if(!"PICKUP".equals(method)&&!"DELIVERY".equals(method))throw new IllegalArgumentException("Invalid fulfillment method.");
        if("DELIVERY".equals(method)&&(address==null||address.isBlank()))throw new IllegalArgumentException("Complete delivery address is required.");
        if(notes!=null&&notes.length()>500)throw new IllegalArgumentException("Order notes are too long.");
        try(Connection c=db()){
            c.setAutoCommit(false);
            try{
                long buyerId=account(c,buyer,"CUSTOMER");
                long farmId;String product,unit;BigDecimal price,available;
                try(PreparedStatement p=c.prepareStatement("SELECT farm_id,name,unit,market_price,quantity FROM inventory WHERE id=? AND kind='HARVEST' AND market_published=TRUE AND (expiry_date IS NULL OR expiry_date>=CURRENT_DATE) FOR UPDATE")){
                    p.setLong(1,itemId);
                    try(ResultSet r=p.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("This product is no longer available.");
                        farmId=r.getLong(1);product=r.getString(2);unit=r.getString(3);price=r.getBigDecimal(4);available=r.getBigDecimal(5);
                    }
                }
                if(price==null||price.signum()<=0||available.compareTo(BigDecimal.valueOf(quantity))<0)
                    throw new IllegalArgumentException("Insufficient stock or invalid selling price.");
                BigDecimal qty=BigDecimal.valueOf(quantity),total=money(price.multiply(qty));
                long orderId;
                try(PreparedStatement p=c.prepareStatement("INSERT INTO saka_orders(buyer_id,farm_id,full_name,phone,delivery_mode,address,payment_method,notes,total) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id")){
                    p.setLong(1,buyerId);p.setLong(2,farmId);p.setString(3,fullName.trim());p.setString(4,mobile);
                    p.setString(5,method);p.setString(6,address==null?"":address.trim());p.setString(7,paymentMethod);p.setString(8,notes==null?"":notes.trim());p.setBigDecimal(9,total);
                    try(ResultSet r=p.executeQuery()){r.next();orderId=r.getLong(1);}
                }
                event(c,orderId,"PENDING",buyer,"Customer placed an order; stock reserved");
                try(PreparedStatement p=c.prepareStatement("UPDATE inventory SET quantity=quantity-? WHERE id=? AND farm_id=? AND quantity>=?")){
                    p.setBigDecimal(1,qty);p.setLong(2,itemId);p.setLong(3,farmId);p.setBigDecimal(4,qty);
                    if(p.executeUpdate()!=1)throw new IllegalArgumentException("Product stock changed. Try again.");
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO saka_order_items(order_id,inventory_id,product_name,unit,quantity,unit_price) VALUES (?,?,?,?,?,?)")){
                    p.setLong(1,orderId);p.setLong(2,itemId);p.setString(3,product);p.setString(4,unit);p.setBigDecimal(5,qty);p.setBigDecimal(6,price);p.executeUpdate();
                }
                c.commit();return orderId;
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    /** Confirms cash received. Fulfillment status is deliberately independent. */
    void confirmPayment(String owner,long orderId) throws Exception {
        try(Connection c=db()) { c.setAutoCommit(false); try {
            long farm=ownerFarm(c,owner);
            long buyerId; String status; String payment;
            try(PreparedStatement p=c.prepareStatement("SELECT buyer_id,status,payment_status FROM saka_orders WHERE id=? AND farm_id=? FOR UPDATE")) {
                p.setLong(1,orderId);p.setLong(2,farm);try(ResultSet r=p.executeQuery()) {
                    if(!r.next())throw new SecurityException("Order not found for this farm.");
                    buyerId=r.getLong(1);status=r.getString(2);payment=r.getString(3);
                }
            }
            if(!Set.of("DELIVERED","PICKED_UP","COMPLETED").contains(status)) throw new IllegalStateException("Confirm payment only after delivery or pickup is completed.");
            if("PAID".equals(payment)) throw new IllegalStateException("Payment is already confirmed.");
            try(PreparedStatement p=c.prepareStatement("UPDATE saka_orders SET payment_status='PAID',payment_confirmed_at=NOW(),payment_confirmed_by=? WHERE id=? AND farm_id=? AND payment_status='UNPAID'")) {
                p.setString(1,owner);p.setLong(2,orderId);p.setLong(3,farm);if(p.executeUpdate()!=1)throw new SQLException("Payment confirmation failed.");
            }
            notification(c,orderId,buyerId,"Payment confirmed","The farm owner confirmed payment for order #"+orderId+"."); c.commit();
        } catch(Exception e){c.rollback();throw e;} }
    }
    private static void notification(Connection c,long orderId,long recipient,String title,String message) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO saka_order_notifications(order_id,recipient_user_id,title,message) VALUES (?,?,?,?)")) {
            p.setLong(1,orderId);p.setLong(2,recipient);p.setString(3,title);p.setString(4,message);p.executeUpdate();
        }
    }
    List<String> notifications(String username) throws Exception {
        try(Connection c=db()) { long id=account(c,username,null);List<String> out=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("SELECT to_char(created_at AT TIME ZONE 'Asia/Manila','YYYY-MM-DD HH24:MI'),title,message FROM saka_order_notifications WHERE recipient_user_id=? ORDER BY created_at DESC LIMIT 100")) {
                p.setLong(1,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getString(1)+" • "+r.getString(2)+"\n"+r.getString(3));}
            } return out;
        }
    }
    List<Order> orders(String username,boolean owner) throws Exception {
        try(Connection c=db()){
            long id=owner?ownerFarm(c,username):account(c,username,"CUSTOMER");
            String sql="SELECT o.id,o.farm_id,f.name,o.full_name,o.status,o.delivery_mode,o.payment_method,o.payment_status,o.address,o.phone,o.total,o.created_at::text FROM saka_orders o JOIN farms f ON f.id=o.farm_id WHERE o."+(owner?"farm_id":"buyer_id")+"=? ORDER BY o.created_at DESC";
            List<Order> out=new ArrayList<>();try(PreparedStatement p=c.prepareStatement(sql)){
                p.setLong(1,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new Order(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getBigDecimal(11),r.getString(12)));}}
            return out;
        }
    }
    String details(String username,long orderId,boolean owner) throws Exception {
        try(Connection c=db()){
            long id=owner?ownerFarm(c,username):account(c,username,"CUSTOMER");
            String column=owner?"farm_id":"buyer_id";
            try(PreparedStatement p=c.prepareStatement("SELECT i.product_name,i.quantity,i.unit,i.unit_price FROM saka_order_items i JOIN saka_orders o ON o.id=i.order_id WHERE o.id=? AND o."+column+"=? ORDER BY i.id")){
                p.setLong(1,orderId);p.setLong(2,id);StringBuilder b=new StringBuilder();try(ResultSet r=p.executeQuery()){
                    while(r.next())b.append(r.getString(1)).append("  x ").append(r.getBigDecimal(2)).append(" ").append(r.getString(3)).append("  @ ₱").append(r.getBigDecimal(4)).append("\n");
                    if(b.length()==0)throw new SecurityException("Order not found or inaccessible.");return b.toString();
                }
            }
        }
    }
    /** Cancels an order and restores reserved stock, or advances owner-side status. */
    void status(String actor,long orderId,String next,boolean owner) throws Exception {
        try(Connection c=db()){
            c.setAutoCommit(false);
            try{
                long actorId=owner?ownerFarm(c,actor):account(c,actor,"CUSTOMER");
                String old;long farm;String buyerName;long buyerUserId;
                String column=owner?"farm_id":"buyer_id";
                try(PreparedStatement p=c.prepareStatement("SELECT status,farm_id,full_name,buyer_id FROM saka_orders WHERE id=? AND "+column+"=? FOR UPDATE")){
                    p.setLong(1,orderId);p.setLong(2,actorId);try(ResultSet r=p.executeQuery()){
                        if(!r.next())throw new SecurityException("Order not found.");old=r.getString(1);farm=r.getLong(2);buyerName=r.getString(3);buyerUserId=r.getLong(4);
                    }}
                boolean cancellation="CANCELLED".equals(next)||"REJECTED".equals(next);
                boolean allowed=owner?switch(old){
                    case "PENDING" -> next.equals("ACCEPTED")||next.equals("REJECTED");
                    case "ACCEPTED" -> next.equals("PREPARING")||next.equals("REJECTED");
                    case "PREPARING" -> next.equals("READY");
                    case "READY" -> next.equals("PICKED_UP") || next.equals("OUT_FOR_DELIVERY");
                    case "OUT_FOR_DELIVERY" -> next.equals("DELIVERED");
                    case "DELIVERED", "PICKED_UP" -> next.equals("COMPLETED");
                    default -> false;
                }:old.equals("PENDING")&&next.equals("CANCELLED");
                if(!allowed)throw new IllegalStateException("Cannot change order from "+old+" to "+next);
                if(cancellation){try(PreparedStatement p=c.prepareStatement("SELECT inventory_id,quantity FROM saka_order_items WHERE order_id=? ORDER BY inventory_id FOR UPDATE")){
                    p.setLong(1,orderId);try(ResultSet r=p.executeQuery()){
                        while(r.next())try(PreparedStatement up=c.prepareStatement("UPDATE inventory SET quantity=quantity+? WHERE id=? AND farm_id=?")){
                            up.setBigDecimal(1,r.getBigDecimal(2));up.setLong(2,r.getLong(1));up.setLong(3,farm);
                            if(up.executeUpdate()!=1)throw new SQLException("Inventory record missing during restock.");
                        }
                    }}
                }
                if("COMPLETED".equals(next) && old.equals("COMPLETED")) throw new IllegalStateException("Order has already been completed.");
                if("COMPLETED".equals(next)){
                    try(PreparedStatement paymentCheck=c.prepareStatement("SELECT payment_status FROM saka_orders WHERE id=? FOR UPDATE")){
                        paymentCheck.setLong(1,orderId);
                        try(ResultSet pr=paymentCheck.executeQuery()){
                            if(!pr.next() || !"PAID".equals(pr.getString(1)))
                                throw new IllegalStateException("Confirm the cash payment first before completing this order.");
                        }
                    }
                    // Stock was reserved at checkout. Do NOT call store.sell(), which would deduct it twice.
                    String sql="SELECT inventory_id,quantity,unit_price FROM saka_order_items WHERE order_id=? ORDER BY id";
                    try(PreparedStatement p=c.prepareStatement(sql)){
                        p.setLong(1,orderId);try(ResultSet r=p.executeQuery()){
                            while(r.next()){
                                BigDecimal qty=r.getBigDecimal(2),price=r.getBigDecimal(3);long itemId=r.getLong(1);long saleId;
                                try(PreparedStatement s=c.prepareStatement("INSERT INTO sales(item_id,buyer,quantity,unit_price,date,farm_id) VALUES (?,?,?,?,?,?) RETURNING id")){
                                    s.setLong(1,itemId);s.setString(2,buyerName);s.setBigDecimal(3,qty);s.setBigDecimal(4,price);s.setDate(5,java.sql.Date.valueOf(LocalDate.now()));s.setLong(6,farm);try(ResultSet sr=s.executeQuery()){sr.next();saleId=sr.getLong(1);}
                                }
                                try(PreparedStatement t=c.prepareStatement("INSERT INTO transactions(type,category,amount,date,notes,sale_id,farm_id) VALUES ('Income','Product Sale',?,?,?,?,?)")){
                                    t.setBigDecimal(1,money(qty.multiply(price)));t.setDate(2,java.sql.Date.valueOf(LocalDate.now()));t.setString(3,"Marketplace order #"+orderId);t.setLong(4,saleId);t.setLong(5,farm);t.executeUpdate();
                                }
                            }
                        }}
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE saka_orders SET status=?,completed_at=CASE WHEN ?='COMPLETED' THEN NOW() ELSE completed_at END WHERE id=?")){
                    p.setString(1,next);p.setString(2,next);p.setLong(3,orderId);p.executeUpdate();
                }
                event(c,orderId,next,actor,owner?"Farm owner changed order status":"Customer cancelled a pending order");
                notification(c,orderId,buyerUserId,"Order update: "+next,"Your marketplace order #"+orderId+" is now "+next.replace('_',' ')+".");
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
}
