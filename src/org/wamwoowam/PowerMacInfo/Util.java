package org.wamwoowam.PowerMacInfo;

import java.text.DecimalFormat;

public class Util {
    public static String formatSize(long size) {
        var sizeMB = size / 0x100000;

        if (sizeMB < 1024)
            return String.format("%dMiB", sizeMB);

        final DecimalFormat f = new DecimalFormat("0.##");
        return String.format("%sGiB", f.format((double) sizeMB / 1024));
    }

    public static String formatSpeed(long speed) {
        if (speed < 1000)
            return speed + " MHz";

        return String.format("%.2fGHz", speed / 1000.0);
    }
}
