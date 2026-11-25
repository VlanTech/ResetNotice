package android.content;

import java.util.Map;

public interface SharedPreferences {
    Map<String, ?> getAll();
    String getString(String key, String def);
    boolean getBoolean(String key, boolean def);
    long getLong(String key, long def);
    int getInt(String key, int def);
    boolean contains(String key);
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        Editor putBoolean(String key, boolean value);
        Editor putLong(String key, long value);
        Editor putInt(String key, int value);
        Editor remove(String key);
        boolean commit();
    }
}
