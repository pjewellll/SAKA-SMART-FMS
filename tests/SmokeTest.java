import com.sakasmart.*;
import java.nio.file.*;
import java.util.*;
import java.time.*;

/** Basic no-dependency functional checks for local demo storage. Run in a fresh temp directory. */
public class SmokeTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        DemoStorage db=new DemoStorage();
        check(db.all("users").size()==2,"two bootstrap users exist");
        check(db.all("inventory").size()>=8,"seeded inventory exists");
        check(Security.verify("Farmer@123".toCharArray(),db.all("users").get(1).get("password_hash"))||
            Security.verify("Farmer@123".toCharArray(),db.all("users").get(0).get("password_hash")),"password verified");
        Map<String,String> newItem=db.add("inventory",Map.of("name","Test Product","category","Crops","kind","HARVEST","quantity","12","unit","kg","supplier","Test Field","expiry_date",LocalDate.now().plusDays(10).toString(),"min_stock","2","farm_id","1"));
        String id=newItem.get("id");check(db.byId("inventory",id)!=null,"item added");
        db.edit("inventory",id,Map.of("quantity","10"));check(Storage.num(db.byId("inventory",id),"quantity")==10,"item updated");
        int beforeSale=db.all("sales").size(),beforeIncome=db.all("transactions").size();
        db.sell(id,"Test Customer",3,20,today(),"test");
        check(db.all("sales").size()==beforeSale+1,"sale recorded");
        check(db.all("transactions").size()==beforeIncome+1,"income automatically recorded");
        check(Storage.num(db.byId("inventory",id),"quantity")==7,"stock automatically deducted");
        boolean deleteRejected=false;
        try{db.remove("inventory",id);}catch(IllegalArgumentException ex){deleteRejected=true;}
        check(deleteRejected,"sale-referenced item deletion blocked");
        DemoStorage reopened=new DemoStorage();
        check(Storage.num(reopened.byId("inventory",id),"quantity")==7,"data persisted across reopening");
        System.out.println("ALL 10 LOCAL DEMO SMOKE TESTS PASSED");
    }
    private static String today(){return LocalDate.now().toString();}
}
