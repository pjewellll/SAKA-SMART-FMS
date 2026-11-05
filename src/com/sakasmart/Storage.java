package com.sakasmart;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Both storage implementations expose the same operations. */
public interface Storage {
    List<Map<String,String>> all(String table) throws Exception;
    Map<String,String> add(String table, Map<String,String> values) throws Exception;
    void edit(String table, String id, Map<String,String> values) throws Exception;
    void remove(String table, String id) throws Exception;
    void sell(String itemId, String buyer, double quantity, double unitPrice, String date, String actor) throws Exception;
    /** Same user-facing response even when the username does not exist. */
    void requestPasswordReset(String username) throws Exception;
    /** Only an authorized administrator can approve or reject. Returns plaintext code ONCE. */
    String approvePasswordReset(String requestId, String adminUsername) throws Exception;
    void rejectPasswordReset(String requestId, String adminUsername) throws Exception;
    /** Atomic consume-and-update operation. Never expose stored code hashes to the user. */
    boolean redeemPasswordReset(String username, String recoveryCode, char[] newPassword) throws Exception;
    /** Creates a one-time reset code for an active user with a verified channel.
     * Returns null without disclosing account existence for invalid/unverified/rate-limited requests.
     * Only call from a trusted recovery workflow, and send the code only to destination.
     */
    RecoveryDelivery issueRecoveryToVerifiedContact(String username,String channel) throws Exception;
    /** Revoke an issued code if the external delivery API rejects the request. */
    void cancelRecoveryDelivery(String requestId) throws Exception;

    /** Public farm directory exposes ID and name only (not farm data). */
    List<Map<String,String>> publicFarms() throws Exception;
    /** Register a new independent farm owner atomically with their farm. */
    Map<String,String> registerOwnFarm(Map<String,String> account,Map<String,String> farm,String provider,String subject) throws Exception;
    /** Register an inactive applicant belonging to a selected farm. */
    Map<String,String> registerJoinFarm(Map<String,String> account,String farmId,String provider,String subject) throws Exception;
    /** Farm owner approves/rejects their farm member and assigns authorized role. */
    void decideMembership(String ownerId,String memberId,String memberRole,boolean approve) throws Exception;
    /** Social identity lookup uses verified provider subject, NEVER email matching. */
    Map<String,String> bySocialIdentity(String provider,String subject) throws Exception;
    /** One-time SMS login code; requires previously verified mobile number. */
    RecoveryDelivery issuePhoneLoginCode(String phone) throws Exception;
    void cancelPhoneLoginCode(String requestId) throws Exception;
    Map<String,String> redeemPhoneLoginCode(String phone,String code) throws Exception;
    String mode();
    default Map<String,String> byId(String table, String id) throws Exception {
        for (Map<String,String> m : all(table)) if (id.equals(m.get("id"))) return m;
        return null;
    }
    default void log(String actor, String action) {
        try {
            add("logs", Map.of("actor",actor,"action",action,"date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        } catch(Exception ignored) { /* don't break a user action because of logging */ }
    }
    static String s(Map<String,String> row, String col) { return row.getOrDefault(col, ""); }
    static double num(Map<String,String> row, String col) {
        try { return Double.parseDouble(s(row,col)); } catch (Exception ignored) { return 0; }
    }
    static String money(double d) { return String.format(Locale.US, "₱%,.2f",d); }
}
