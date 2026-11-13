package ctbrec;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {
    private StringUtil() {
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    public static String formatSize(Number sizeInByte) {
        if (sizeInByte.longValue() < 0) {
            return "n/a";
        }

        DecimalFormat df = new DecimalFormat("0.00");
        String unit = "Bytes";
        double size = sizeInByte.doubleValue();
        if (size > 1024.0 * 1024 * 1024) {
            size = size / 1024.0 / 1024 / 1024;
            unit = "GiB";
        } else if (size > 1024.0 * 1024) {
            size = size / 1024.0 / 1024;
            unit = "MiB";
        } else if (size > 1024.0) {
            size = size / 1024.0;
            unit = "KiB";
        }
        return df.format(size) + ' ' + unit;
    }

    public static String toHexString(byte[] bytes, int bytesPerRow) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i += bytesPerRow) {
            int length = bytes.length - i >= bytesPerRow ? bytesPerRow : bytes.length % bytesPerRow;
            byte[] row = new byte[bytesPerRow];
            System.arraycopy(bytes, i, row, 0, length);
            for (int j = 0; j < length; j++) {
                sb.append(toHexString(row[j]));
            }
        }
        return sb.toString();
    }

    /**
     * Converts one byte to its hex representation with leading zeros. E.g. 255 -> FF, 12 -> 0C
     *
     * @param b the byte value to represent in hex
     * @return the hexadecimal representation as string
     */
    public static String toHexString(int b) {
        String hex = Integer.toHexString(b & 0xFF);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        return hex;
    }

    // @formatter:off
    public static String sanitize(String input) {
        return input
                .replace(' ', '_')
                .replace('\\', '_')
                .replace('/', '_')
                .replace('\'', '_')
                .replace('"', '_');
    } // @formatter:on

    public static String grep(String input, String regex) {
        StringTokenizer st = new StringTokenizer(input, "\n");
        StringBuilder result = new StringBuilder();
        Pattern p = Pattern.compile(regex);
        while (st.hasMoreTokens()) {
            String line = st.nextToken();
            if (p.matcher(line).find()) {
                result.append(line).append('\n');
            }
        }
        if (result.length() >= 2) {
            result = result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    public static int percentageOfEquality(String s, String t) {
        // check if strings are empty
        if (s == null || t == null || s.length() == 0 || t.length() == 0) {
            return 0;
        }

        // check if the strings are equal
        if (s.equals(t)) {
            return 100;
        }

        // check if one string is a substring of the other
        String shorter;
        String longer;
        if (s.length() > t.length()) {
            shorter = t;
            longer = s;
        } else {
            shorter = s;
            longer = t;
        }
        if (longer.startsWith(shorter) && longer.length() > shorter.length()) {
            if (longer.charAt(shorter.length()) == ' ') {
                return 99;
            } else {
                return 98;
            }
        }

        s = s.toLowerCase();
        s = s.replace("-", " ");
        s = s.replace(":", " ");
        s = s.replace(";", " ");
        s = s.replace("\\|", " ");
        s = s.replace("_", " ");
        s = s.replace("\\.", "\\. ");
        s = s.trim();
        t = t.toLowerCase();
        t = t.replace("-", " ");
        t = t.replace(":", " ");
        t = t.replace(";", " ");
        t = t.replace("\\|", " ");
        t = t.replace("_", " ");
        t = t.replace("\\.", "\\. ");
        t = t.trim();

        // calculate levenshteinDistance
        int levenshteinDistance = StringUtil.getLevenshteinDistance(s, t);
        int length = Math.max(s.length(), t.length());

        // calculate the percentage of equality
        int percentage = 100 - (int) ((double) levenshteinDistance * 100 / length);
        return percentage;
    }

    public static int getLevenshteinDistance(String s, String t) {
        int n = s.length();
        int m = t.length();
        int[][] d = new int[n + 1][m + 1];
        int i;
        int j;
        int cost;

        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        for (i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (j = 0; j <= m; j++) {
            d[0][j] = j;
        }

        for (i = 1; i <= n; i++) {
            for (j = 1; j <= m; j++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    cost = 0;
                } else {
                    cost = 1;
                }

                d[i][j] = min(d[i - 1][j] + 1, // insertion
                        d[i][j - 1] + 1, // deletion
                        d[i - 1][j - 1] + cost); // substitution
            }
        }
        return d[n][m];
    }

    private static int min(int a, int b, int c) {
        if (b < a) {
            a = b;
        }
        if (c < a) {
            a = c;
        }
        return a;
    }

    public static String[] splitParams(String params) {
        Pattern p = Pattern.compile("(\"[^\"]+\"|[^\\s\"]+)");
        Matcher m = p.matcher(params);
        List<String> result = new LinkedList<>();
        while (m.find()) {
            String group = m.group();
            if (group.startsWith("\"") && group.endsWith("\"")) {
                group = group.substring(1, group.length() - 1);
            }
            result.add(group);
        }
        return result.toArray(new String[0]);
    }

    public static String capitalize(String string) {
        if (string.length() > 0) {
            StringTokenizer st = new StringTokenizer(string, " _-.", true);
            var sb = new StringBuilder();
            while (st.hasMoreTokens()) {
                replaceBlacklistedCharacters(sb, st.nextToken());
            }
            string = sb.toString();
        }
        return string;
    }

    private static void replaceBlacklistedCharacters(StringBuilder sb, String token) {
        StringBuilder temp = new StringBuilder(token);
        char first = temp.charAt(0);
        if (first >= 'a' && first <= 'z') { // if first is a letter
            first -= 32;
            temp.setCharAt(0, first);
        } else {
            if (temp.length() > 1) {
                char second = temp.charAt(1);
                if (second >= 'a' && second <= 'z') {
                    second -= 32;
                    temp.setCharAt(1, second);
                }
            }
        }
        sb.append(temp);
    }
}
