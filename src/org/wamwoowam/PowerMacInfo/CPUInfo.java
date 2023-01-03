package org.wamwoowam.PowerMacInfo;

public class CPUInfo {
    private final int idx;
    private final String name;
    private final int clockMhz;

    public CPUInfo(int idx, String name, int clockMhz) {
        this.idx = idx;
        this.name = name;
        this.clockMhz = clockMhz;
    }

    public int getIndex() {
        return idx;
    }

    public String getName() {
        return name;
    }

    public String getClock() {
        return Util.formatSpeed(clockMhz);
    }
}
