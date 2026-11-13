package ctbrec.io;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public class ByteUnitFormatter {

    private ByteUnitFormatter() {
    }

    public static final long KiB = 1024;
    public static final long MiB = KiB * 1024;
    public static final long GiB = MiB * 1024;
    public static final long TiB = GiB * 1024;

    private static NumberFormat nf = new DecimalFormat(".00");

    public static String format(long bytes) {
        double decimal = bytes;
        return format(decimal);
    }

    public static String format(double bytes) {
        double decimal = bytes;
        String unit = "Bytes";
        if (bytes >= TiB) {
            decimal = bytes / (1024 * 1024 * 1024 * 1024);
            unit = "TiB";
        } else if (bytes >= GiB) {
            decimal = bytes / (1024 * 1024 * 1024);
            unit = "GiB";
        } else if (bytes >= MiB) {
            decimal = bytes / (1024 * 1024);
            unit = "MiB";
        } else if (bytes >= KiB) {
            decimal = bytes / 1024;
            unit = "KiB";
        }

        return nf.format(decimal) + " " + unit;
    }
}
