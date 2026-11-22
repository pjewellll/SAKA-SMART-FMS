package com.sakasmart;

/** V13 rules for NEW passwords only. Existing password hashes remain valid on login. */
final class PasswordPolicy {
    static void requireValid(char[] password) {
        if(password==null || password.length<8 || password.length>128)
            throw new IllegalArgumentException("Use a password with 8 to 128 characters.");
        boolean letter=false,number=false;
        for(char c:password){
            if(Character.isWhitespace(c) || Character.isISOControl(c))
                throw new IllegalArgumentException("Password cannot contain spaces or control characters.");
            if(Character.isLetter(c))letter=true;
            if(c>='0' && c<='9')number=true;
        }
        if(!letter||!number)
            throw new IllegalArgumentException("Password needs at least one letter and one number.");
        String simple=new String(password).toLowerCase(java.util.Locale.ROOT);
        if(java.util.Set.of("password123","password1234","admin1234","12345678a","123456789a","qwerty123","welcome123").contains(simple))
            throw new IllegalArgumentException("This password is too common. Choose a different one.");
    }
    private PasswordPolicy(){}
}
