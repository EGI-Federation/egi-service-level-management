package egi.eu;

/***
 * Utility methods
 */
public class Utils {

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
}
