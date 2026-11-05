package com.sakasmart;

import java.util.regex.Pattern;

/** Shared contact format validation. Contact ownership is verified separately. */
public final class ContactValidation {
    private ContactValidation() {}
    private static final Pattern EMAIL=Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE=Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    public static boolean validEmail(String value){return value!=null&&EMAIL.matcher(value).matches()&&value.length()<=254;}
    public static boolean validPhone(String value){return value!=null&&PHONE.matcher(value).matches();}
}
