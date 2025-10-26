package org.nyet.util;

import java.util.Collection;
import java.util.ArrayList;

public class Strings {
    public static String join(String sep, Collection<?> c) {
        return join(sep, c.toArray(), c.size());
    }

    public static String join(String sep, ArrayList<Object> a) {
        return join(sep, a.toArray(), a.size());
    }

    public static String join(String sep, Object [] a) {
        return join(sep, a, a.length);
    }

    public static String join(String sep, Object [] a, int count) {
        String out = "";
        for (int i=0; i<count; i++) {
            if(a[i].toString().length()>0)
                out += ((out.length()==0)?"":sep) + a[i].toString();
        }
        return out;
    }

    /**
     * Trims all non-null elements in a String array in-place.
     * @param array the String array to trim (modified in-place)
     */
    public static void trimArray(String[] array) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] != null) {
                array[i] = array[i].trim();
            }
        }
    }

    /**
     * Smart elision that preserves both start and end of a string.
     * Automatically chooses optimal allocation based on maxLength:
     * - Short (<=15): More weight to end (timestamp), less to start (version)
     * - Long (>15): Balanced allocation for more context
     * @param text The string to elide
     * @param maxLength Maximum total length (must be at least 7 to show start + "..." + end)
     * @return Elided string with both prefix and suffix preserved, "..." in the middle
     */
    public static String elide(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        // Need at least 7 chars: 3 for start + 3 for "..." + 1 for end
        if (maxLength < 7) {
            return text.substring(0, maxLength - 3) + "...";
        }

        int ellipsisLength = 3;
        int remainingLength = maxLength - ellipsisLength;
        int targetEndLength;
        int startLength;

        if (remainingLength >= 40) {
            // For 60+ chars (FilterWindow): ~40% end, ~60% start for more context
            targetEndLength = (int)(remainingLength * 0.4);
            startLength = remainingLength - targetEndLength;
            // Ensure minimums
            if (targetEndLength < 12) targetEndLength = 12;  // Need enough for timestamp
            if (startLength < 15) startLength = 15;  // Need enough for version + context
            startLength = remainingLength - targetEndLength;  // Recalculate after minimums
        } else if (remainingLength >= 12) {
            // For 15+ chars (legend labels): 8 chars end (timestamp), 4 chars start (version)
            targetEndLength = 8;
            startLength = remainingLength - targetEndLength;
        } else if (remainingLength >= 10) {
            // For 13-14 chars: 7 chars end, 3 chars start
            targetEndLength = 7;
            startLength = remainingLength - targetEndLength;
        } else {
            // For smaller: try to keep at least 4 chars end
            targetEndLength = Math.max(4, remainingLength / 2);
            startLength = remainingLength - targetEndLength;
        }

        String start = text.substring(0, Math.min(startLength, text.length()));
        String end = text.substring(Math.max(0, text.length() - targetEndLength));

        return start + "..." + end;
    }

}

// vim: set sw=4 ts=8 expandtab:
