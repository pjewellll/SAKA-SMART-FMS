package com.sakasmart;

import java.util.*;

/** Central field definitions shared by the Java UI, demo storage, and PostgreSQL SQL. */
public final class Schema {
    public enum Type { TEXT, NUMBER, DATE, BOOL, LONG_TEXT, CHOICE, SECRET }
    public record Field(String key, String label, Type type, boolean required, List<String> choices) {
        public Field(String key, String label, Type type, boolean required) {
            this(key, label, type, required, List.of());
        }
        public Field(String key, String label, String... choices) {
            this(key, label, Type.CHOICE, true, List.of(choices));
        }
    }
    public record Definition(String table, String title, List<Field> fields) {
        public Field find(String key) {
            return fields.stream().filter(f -> f.key.equals(key)).findFirst().orElse(null);
        }
    }
    public static final Map<String,Definition> TABLES = new LinkedHashMap<>();
    private static void def(String table, String title, Field... fields) {
        TABLES.put(table, new Definition(table, title, List.of(fields)));
    }
    static {
        def("farms", "Farm Profiles",
            new Field("name", "Farm Name", Type.TEXT, true),
            new Field("location", "Location", Type.TEXT, true),
            new Field("owner", "Owner", Type.TEXT, true),
            new Field("area_ha", "Area (hectares)", Type.NUMBER, true),
            new Field("crops", "Crop Categories", Type.TEXT, false),
            new Field("livestock", "Livestock Categories", Type.TEXT, false));
        def("users", "Users",
            new Field("username", "Username", Type.TEXT, true),
            new Field("full_name", "Full Name", Type.TEXT, true),
            new Field("password_hash", "Password Hash", Type.SECRET, true),
            new Field("role", "Role", "SYSTEM_ADMIN", "FARM_OWNER", "FARM_MANAGER", "INVENTORY_STAFF", "BOOKKEEPER", "VIEWER"),
            new Field("active", "Active", Type.BOOL, true),
            new Field("farm_id", "Farm ID", Type.NUMBER, true),
            new Field("email", "Recovery Email", Type.TEXT, false),
            new Field("email_verified", "Email Verified", Type.BOOL, false),
            new Field("phone", "Recovery Phone (E.164)", Type.TEXT, false),
            new Field("phone_verified", "Phone Verified", Type.BOOL, false));
        // Added in Phase 1. Existing tables are NOT dropped or recreated.
        def("password_reset_requests", "Password Reset Requests",
            new Field("user_id", "User ID", Type.NUMBER, true),
            new Field("username", "Username", Type.TEXT, true),
            new Field("status", "Status", "PENDING", "APPROVED", "USED", "REJECTED", "EXPIRED", "LOCKED", "FAILED"),
            new Field("requested_at", "Requested At (UTC)", Type.TEXT, true),
            new Field("code_hash", "Recovery Code Hash", Type.SECRET, false),
            new Field("expires_at", "Expires At (UTC)", Type.TEXT, false),
            new Field("attempts", "Attempts", Type.NUMBER, true),
            new Field("handled_by", "Handled By", Type.TEXT, false),
            new Field("handled_at", "Handled At (UTC)", Type.TEXT, false));
        def("inventory", "Inventory",
            new Field("name", "Item Name", Type.TEXT, true),
            new Field("category", "Category", "Seeds", "Fertilizer", "Pesticide", "Feed", "Equipment", "Medicine", "Fuel", "Packaging", "Crops", "Livestock Product", "Other"),
            new Field("kind", "Stock Type", "SUPPLY", "HARVEST"),
            new Field("quantity", "Quantity", Type.NUMBER, true),
            new Field("unit", "Unit", "kg", "bags", "pcs", "liters", "bottles", "sets", "heads", "tons"),
            new Field("supplier", "Supplier / Source", Type.TEXT, false),
            new Field("expiry_date", "Expiry Date (YYYY-MM-DD)", Type.DATE, false),
            new Field("min_stock", "Low Stock Limit", Type.NUMBER, true),
            new Field("farm_id", "Farm ID", Type.NUMBER, true));
        def("events", "Calendar Events",
            new Field("title", "Task Name", Type.TEXT, true),
            new Field("kind", "Schedule", "CROP", "LIVESTOCK"),
            new Field("category", "Task Type", "Planting", "Watering", "Fertilizing", "Harvest", "Vaccination", "Breeding", "Feeding", "Collection", "Maintenance", "Other"),
            new Field("target", "Field / Animal Group", Type.TEXT, true),
            new Field("date", "Date (YYYY-MM-DD)", Type.DATE, true),
            new Field("time", "Time (HH:MM)", Type.TEXT, true),
            new Field("status", "Status", "Upcoming", "Completed", "Cancelled"),
            new Field("notes", "Notes", Type.LONG_TEXT, false),
            new Field("farm_id", "Farm ID", Type.NUMBER, true));
        def("production", "Production Logs",
            new Field("item", "Crop / Livestock Product", Type.TEXT, true),
            new Field("kind", "Production Type", "CROP", "LIVESTOCK"),
            new Field("target", "Target Output", Type.NUMBER, true),
            new Field("actual", "Actual Output", Type.NUMBER, true),
            new Field("unit", "Unit", "kg", "tons", "liters", "pcs", "heads"),
            new Field("date", "Log Date (YYYY-MM-DD)", Type.DATE, true),
            new Field("notes", "Notes", Type.LONG_TEXT, false),
            new Field("farm_id", "Farm ID", Type.NUMBER, true));
        def("transactions", "Accounting Transactions",
            new Field("type", "Type", "Income", "Expense"),
            new Field("category", "Category", Type.TEXT, true),
            new Field("amount", "Amount (PHP)", Type.NUMBER, true),
            new Field("date", "Date (YYYY-MM-DD)", Type.DATE, true),
            new Field("notes", "Reference / Notes", Type.TEXT, false),
            new Field("sale_id", "Related Sale ID", Type.NUMBER, false),
            new Field("farm_id", "Farm ID", Type.NUMBER, true));
        def("sales", "Sales Records",
            new Field("item_id", "Harvested Item ID", Type.NUMBER, true),
            new Field("buyer", "Buyer", Type.TEXT, true),
            new Field("quantity", "Quantity", Type.NUMBER, true),
            new Field("unit_price", "Unit Price (PHP)", Type.NUMBER, true),
            new Field("date", "Sale Date (YYYY-MM-DD)", Type.DATE, true),
            new Field("farm_id", "Farm ID", Type.NUMBER, true));
        def("articles", "Learning Articles",
            new Field("title", "Article Title", Type.TEXT, true),
            new Field("category", "Category", "Crops", "Livestock", "Farm Finance", "Sustainability", "Safety"),
            new Field("content", "Article Content", Type.LONG_TEXT, true),
            new Field("date", "Published Date (YYYY-MM-DD)", Type.DATE, true));
        def("logs", "System Activity Logs",
            new Field("actor", "Actor", Type.TEXT, true),
            new Field("action", "Action", Type.TEXT, true),
            new Field("date", "Date and Time", Type.TEXT, true),
            new Field("farm_id", "Farm ID", Type.NUMBER, false));
        def("social_identities", "Linked Social Accounts",
            new Field("user_id", "User ID", Type.NUMBER, true),
            new Field("provider", "Provider", Type.TEXT, true),
            new Field("subject", "Provider User ID", Type.TEXT, true));
        def("phone_login_codes", "Phone Login Codes",
            new Field("user_id", "User ID", Type.NUMBER, true),
            new Field("code_hash", "One-time Code Hash", Type.SECRET, false),
            new Field("expires_at", "Expiry (UTC)", Type.TEXT, false),
            new Field("attempts", "Attempts", Type.NUMBER, true),
            new Field("created_at", "Created At (UTC)", Type.TEXT, true),
            new Field("status", "Status", Type.TEXT, true));
    }
    public static String sqlType(Field f) {
    if (f.key().equals("farm_id") ||
        f.key().equals("item_id") ||
        f.key().equals("sale_id") ||
        f.key().equals("user_id")) {
        return "BIGINT";
    }

    return switch (f.type()) {
        case NUMBER -> "NUMERIC(16,2)";
        case BOOL -> "BOOLEAN";
        case DATE -> "DATE";
        default -> "TEXT";
    };
}
    private Schema() {}
}
