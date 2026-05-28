package aiv.ashivered.safebrowser;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public class PasswordUtils {

    private static final String PREFS_SETTINGS_LOCK_ENABLED = "prefs_settings_lock_enabled";
    private static final String PREFS_SETTINGS_PASSWORD_HASH = "prefs_settings_password_hash";

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^[\\w!@#$%^&*()_+\\-=\\[\\]{};':\",./\\<>?]{4,8}$");

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isSettingsLockEnabled(Context context) {
        return getPrefs(context).getBoolean(PREFS_SETTINGS_LOCK_ENABLED, false);
    }

    public static void setSettingsLockEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(PREFS_SETTINGS_LOCK_ENABLED, enabled).apply();
    }

    private static String getPasswordHash(Context context) {
        return getPrefs(context).getString(PREFS_SETTINGS_PASSWORD_HASH, null);
    }

    public static void setPasswordHash(Context context, String hash) {
        getPrefs(context).edit().putString(PREFS_SETTINGS_PASSWORD_HASH, hash).apply();
    }

    public static void clearPassword(Context context) {
        getPrefs(context).edit().remove(PREFS_SETTINGS_PASSWORD_HASH).apply();
    }

    public static boolean hasPasswordSet(Context context) {
        return getPasswordHash(context) != null;
    }

    public static boolean isValidPasswordFormat(String password) {
        if (password == null) return false;
        return PASSWORD_PATTERN.matcher(password).matches();
    }

    public static String hashPassword(String password) {
        if (password == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean checkPassword(Context context, String inputPassword) {
        String storedHash = getPasswordHash(context);
        if (storedHash == null || inputPassword == null) return false;
        String inputHash = hashPassword(inputPassword);
        return storedHash.equals(inputHash);
    }
}
