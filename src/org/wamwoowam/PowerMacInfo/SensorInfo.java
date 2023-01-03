package org.wamwoowam.PowerMacInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SensorInfo {
    private SensorType type;
    private SensorLocation location;
    private Path path;
    private String value;

    public SensorInfo(SensorType type, SensorLocation location, Path path) {
        this.type = type;
        this.location = location;
        this.path = path;
    }

    void update() throws IOException {
        value = Files.readString(path);
    }

    public SensorType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public SensorLocation getLocation() {
        return location;
    }
}
