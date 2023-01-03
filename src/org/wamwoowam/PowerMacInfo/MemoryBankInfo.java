package org.wamwoowam.PowerMacInfo;

public class MemoryBankInfo {
    private final long address;
    private final long size;
    private final String dimmSpeed;
    private final String dimmType;

    public MemoryBankInfo(long address, long size, String type, String speed) {
        this.size = size;
        this.address = address;
        this.dimmType = type;
        this.dimmSpeed = speed;
    }

    public long getAddress() {
        return address;
    }

    public long getSize() {
        return size;
    }

    public String getDIMMSpeed() {
        return dimmSpeed;
    }

    public String getDIMMType() {
        return dimmType;
    }

    public String getSizeString() {
        return Util.formatSize(size);
    }
}

