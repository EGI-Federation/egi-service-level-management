package egi.eu;

import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;


/***
 * Utility methods
 */
public class Utils {

    private static final Logger log = Logger.getLogger(Utils.class);

    /***
     * Compare two strings
     * @param s1 The first string to compare, can be null
     * @param s2 The second string to compare, can be null
     * @return True if the strings are equal
     */
    public static boolean equalStrings(String s1, String s2) {
        if ((null == s1) != (null == s2))
            return false;

        if (null != s1)
            return s1.equals(s2);

        return true;
    }

    /***
     * Get default value for a type
     * @param clazz The type to get a default value for
     */
    @SuppressWarnings("unchecked")
    public static <K> K defaultValueFor(Class<K> clazz) {

        K k = null;

        try {
            k = (K)DefaultValues.defaultValues.get(clazz);
        }
        catch(ClassCastException ignored) {
            log.error("Cannot cast default value of " + clazz.getTypeName());
        }

        return k;
    }

    /***
     * Helper class to store default value for basic types
     */
    static class DefaultValues {
        static final Map<Class<?>,Object> defaultValues = new HashMap<Class<?>, Object>();

        static {
            defaultValues.put(boolean.class, Boolean.FALSE);
            defaultValues.put(byte.class, Byte.valueOf((byte)0));
            defaultValues.put(short.class, Short.valueOf((short)0));
            defaultValues.put(int.class, Integer.valueOf(0));
            defaultValues.put(long.class, Long.valueOf(0L));
            defaultValues.put(float.class, Float.valueOf(0.0F));
            defaultValues.put(double.class, Double.valueOf(0.0));
            defaultValues.put(char.class, Character.valueOf('\0'));
        }
    }
}
