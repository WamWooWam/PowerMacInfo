package org.wamwoowam.PowerMacInfo;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class InfoCollector {
    private static final String LOOKUP_API_FORMAT
            = "https://di-api.reincubate.com/v1/apple-serials/%s/";
    private static final Path DEVICE_TREE
            = Path.of("/proc/device-tree");
    ;
    private static final Path WINDFARM
            = Path.of("/sys/devices/platform/windfarm.0");
    ;
    private final List<CPUInfo> cpus;
    private final List<MemoryBankInfo> memBanks;
    private final List<String> gpus;
    private final List<SensorInfo> sensors;
    private String displayModel;
    private String diskCache;

    private boolean hasWindfarm;

    private int addressCells;
    private int sizeCells;

    public InfoCollector() {
        this.cpus = new ArrayList<>();
        this.memBanks = new ArrayList<>();
        this.gpus = new ArrayList<>();
        this.sensors = new ArrayList<>();
    }

    public boolean init(Plugin plugin) {
        if (!Files.exists(DEVICE_TREE))
            return false;

        hasWindfarm = Files.exists(WINDFARM);

        try {
            addressCells = ByteBuffer.wrap(Files.readAllBytes(DEVICE_TREE.resolve("#address-cells"))).getInt();
            sizeCells = ByteBuffer.wrap(Files.readAllBytes(DEVICE_TREE.resolve("#size-cells"))).getInt();

            readCPUInfo();
            readMemoryInfo();
            readDiskInfo();
            readGPUInfo();

            var scheduler = Bukkit.getScheduler();
            // updates disk stats every 15 seconds on a separate thread
            scheduler.runTaskTimerAsynchronously(plugin, this::readDiskInfo, 0L, 20L * 15L);
            // updates sensors every second on a separate thread
            scheduler.runTaskTimerAsynchronously(plugin, this::readSensors, 0L, 20L);
        } catch (IOException e) {
            return false;
        }

        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder(URI.create(String.format(LOOKUP_API_FORMAT, getSerialNumber())))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if(response.statusCode() != 200)
                            throw new CompletionException(new Exception("Invalid response code!"));
                        return response;
                    })
                    .thenApply(HttpResponse::body)
                    .thenAccept(body -> {
                        var json = new JSONObject(body);
                        displayModel = json.getJSONObject("configurationCode").getString("skuHint");
                    });
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    public String getModel() {
        try {
            return clean(Files.readString(DEVICE_TREE.resolve("model")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getDisplayModel() {
        return displayModel;
    }

    public String getSerialNumber() {
        try {
            var rawSerial = Files.readAllBytes(DEVICE_TREE.resolve("serial-number"));
            int x = 0;
            while (rawSerial[x] != 0)
                x++;

            while (rawSerial[x] == 0)
                x++;

            int y = x;
            while (rawSerial[y] != 0)
                y++;

            return new String(rawSerial, x, y - x);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getCPU() {
        return String.format("%dx %s @ %s", cpus.size(), cpus.get(0).getName(), cpus.get(0).getClock());
    }

    public String[] getCPUs() {
        var array = new String[cpus.size()];
        for (int i = 0; i < cpus.size(); i++) {
            var cpu = cpus.get(i);
            array[i] = cpu.getIndex() + ": " + cpu.getName() + " @ " + cpu.getClock();
        }

        return array;
    }

    public String getMemory() {
        var groups = memBanks.stream()
                .collect(Collectors.groupingBy((MemoryBankInfo p) -> p.getSize() + " " + p.getDIMMSpeed()));

        var groupStrings = new ArrayList<String>();
        for (var group : groups.values()) {
            groupStrings.add(String.format("%dx%s %s", group.size(), group.get(0).getSizeString(), group.get(0).getDIMMSpeed()));
        }

        long totalSize = 0;
        for (var bank : memBanks)
            totalSize += bank.getSize();

        return String.format("%s %s (%s)",
                Util.formatSize(totalSize),
                memBanks.get(0).getDIMMType(),
                String.join(", ", groupStrings));
    }

    public String[] getMemoryBanks() {
        var array = new String[memBanks.size()];
        for (int i = 0; i < memBanks.size(); i++) {
            var mem = memBanks.get(i);

            array[i] = String.format("%d: %s %s %s", i, mem.getSizeString(), mem.getDIMMType(), mem.getDIMMSpeed());
        }

        return array;
    }

    public String getDisk() {
        return diskCache;
    }

    public String[] getGPUs() {
        String[] array = new String[gpus.size()];
        gpus.toArray(array);

        return array;
    }

    public String getTemps() {
        if (sensors.size() == 0) return "unavailable";

        // average temperature, total power
        double cpuTemp = 0;
        double cpuPower = 0;

        for (var sensor : sensors) {
            if(sensor.getLocation() == SensorLocation.CPU) {
                if (sensor.getType() == SensorType.TEMPERATURE)
                    cpuTemp += Double.parseDouble(sensor.getValue());
                if (sensor.getType() == SensorType.POWER)
                    cpuPower += Double.parseDouble(sensor.getValue());
            }
        }

        cpuTemp /= cpus.size();

        return String.format("%.2f°C (%.2fW)", cpuTemp, cpuPower);
    }

    private void readCPUInfo() throws IOException {
        cpus.clear();

        var lines = Files.readAllLines(Path.of("/proc/cpuinfo"));

        int idx = -1;
        int cpuMhz = 0;
        String cpuName = null;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);

            if (line.isBlank() && idx != -1) {
                cpus.add(new CPUInfo(idx, cpuName, cpuMhz));
                idx = -1;
                continue;
            }

            String value = line.substring(line.indexOf(':') + 2);
            if (line.startsWith("processor")) {
                idx = Integer.parseInt(value);
            }

            if (line.startsWith("cpu")) {
                cpuName = value;

                var x = cpuName.indexOf(',');
                if (x != -1) {
                    cpuName = cpuName.substring(0, x);
                }

                if (cpuName.matches("740/750"))
                    cpuName = String.format("PowerPC G3 (%s)", cpuName);
                else if (cpuName.matches("^74.+"))
                    cpuName = String.format("PowerPC G4 (%s)", cpuName);
                else if (cpuName.matches("^(PPC)*9.+"))
                    cpuName = String.format("PowerPC G5 (%s)", cpuName);
            }

            if (line.startsWith("clock")) {
                cpuMhz = Integer.parseInt(value.substring(0, value.indexOf('.')));
            }

            if (line.startsWith("detected as")) {
                if (value.indexOf('(') != -1)
                    displayModel = value.substring(value.indexOf('(') + 1, value.indexOf(')'));
                else
                    displayModel = value;
            }
        }
    }


    private void readMemoryInfo() throws IOException {
        memBanks.clear();

        var addressBytes = addressCells * 4;
        var sizeBytes = sizeCells * 4;

        // TODO: is it always memory@0,0?
        var mem = Files.readAllBytes(DEVICE_TREE.resolve("memory@0,0/reg"));
        var dimmSpeeds = Files.readString(DEVICE_TREE.resolve("memory@0,0/dimm-speeds")).split("\0");
        var dimmTypes = Files.readString(DEVICE_TREE.resolve("memory@0,0/dimm-types")).split("\0");

        var stride = (addressCells + sizeCells) * 4;
        var count = mem.length / stride;

        assert dimmSpeeds.length == count;
        assert dimmTypes.length == count;

        for (int i = 0; i < count; i += 1) {
            var idx = i * stride;
            var addressBuf = ByteBuffer.wrap(mem, idx, addressBytes);
            var sizeBuff = ByteBuffer.wrap(mem, idx + addressBytes, sizeBytes);

            long address = getAddress(addressBuf);
            long size = getSize(sizeBuff);

            memBanks.add(new MemoryBankInfo(address, size, dimmTypes[i], dimmSpeeds[i]));
        }
    }

    private void readDiskInfo() {
        try {
            var p = Runtime.getRuntime().exec("df -h -x aufs -x tmpfs -x overlay -x drvfs -x devtmpfs --total");
            var is = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line;
            while ((line = is.readLine()) != null) {
                if (!line.startsWith("total"))
                    continue;

                String[] split = line.replaceAll("\\s+", "_").split("_");
                diskCache = String.format("%sB/%sB", split[2], split[1]);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readGPUInfo() {
        try {
            gpus.clear();
            var p = Runtime.getRuntime().exec("lspci");
            var is = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line;
            while ((line = is.readLine()) != null) {
                if (!line.contains("VGA compatible controller"))
                    continue;

                line = line.substring(10);

                var idx = line.indexOf(':') + 2;
                var endIdx = line.indexOf('(');
                if (endIdx == -1)
                    endIdx = line.length();

                gpus.add(line.substring(idx, endIdx));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readSensors() {
        // TODO: we only support windfarm currently
        try {
            if (sensors.size() == 0 && hasWindfarm) {
                for (int i = 0; i < cpus.size(); i++) {
                    addSensorIfExists(SensorType.TEMPERATURE, SensorLocation.CPU, "cpu-temp-" + i);
                    addSensorIfExists(SensorType.POWER, SensorLocation.CPU,"cpu-power-" + i);
                    addSensorIfExists(SensorType.CURRENT, SensorLocation.CPU, "cpu-current-" + i);
                    addSensorIfExists(SensorType.VOLTAGE, SensorLocation.CPU,"cpu-voltage-" + i);
                    addSensorIfExists(SensorType.FAN_SPEED, SensorLocation.CPU,"cpu-rear-fan-" + i);
                    addSensorIfExists(SensorType.FAN_SPEED, SensorLocation.CPU,"cpu-front-fan-" + i);
                }

                addSensorIfExists(SensorType.FAN_SPEED, SensorLocation.DISK,"drive-bay-fan");
                addSensorIfExists(SensorType.TEMPERATURE, SensorLocation.DISK,"hd-temp");
                addSensorIfExists(SensorType.FAN_SPEED, SensorLocation.GENERIC,"backside-fan");
                addSensorIfExists(SensorType.TEMPERATURE, SensorLocation.GENERIC,"backside-temp");
                addSensorIfExists(SensorType.FAN_SPEED, SensorLocation.SLOTS,"slots-fan");
                addSensorIfExists(SensorType.POWER, SensorLocation.SLOTS,"slots-power");
            }

            for (var sensor : sensors)
                sensor.update();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addSensorIfExists(SensorType type, SensorLocation location, String sensor) {
        var path = WINDFARM.resolve(sensor);

        if (Files.exists(path))
            sensors.add(new SensorInfo(type, location, path));
    }

    // cleans a string retrieved from the Device Tree
    private String clean(String src) {
        return src.substring(0, src.length() - 1);
    }

    private long getSize(ByteBuffer sizeBuff) {
        long size;
        if (sizeCells == 2)
            size = sizeBuff.getLong();
        else
            size = (long) sizeBuff.getInt() & 0xffffffffL;
        return size;
    }

    private long getAddress(ByteBuffer addressBuf) {
        long address;
        if (addressCells == 2)
            address = addressBuf.getLong();
        else
            address = addressBuf.getInt() & 0xffffffffL;
        return address;
    }
}
