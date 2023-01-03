package org.wamwoowam.PowerMacInfo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class Main extends JavaPlugin {
    private InfoCollector info;

    @Override
    public void onEnable() {
        info = new InfoCollector();
        if (!info.init(this)) {
            this.getLogger().severe("This plugin only works on Power Macintosh platforms under Linux.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.getLogger().info("Hello! This is a " + info.getDisplayModel());

        var cpus = info.getCPUs();
        for (String cpu : cpus)
            this.getLogger().info("CPU" + cpu);

        var memoryBanks = info.getMemoryBanks();
        for (String mem : memoryBanks)
            this.getLogger().info("RAM" + mem);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("system")) {

            var list = new ArrayList<String>();
            list.add(String.format("--- %s%s%s ---", ChatColor.BOLD, info.getDisplayModel(),ChatColor.RESET));
            list.add(String.format("%sCPU:%s %s", ChatColor.BOLD, ChatColor.RESET, info.getCPU()));
            list.add(String.format("%sRAM:%s %s", ChatColor.BOLD, ChatColor.RESET, info.getMemory()));

            var gpus = info.getGPUs();
            if(gpus.length > 0) {
                if(gpus.length == 1) {
                    list.add(String.format("%sGPU:%s %s", ChatColor.BOLD, ChatColor.RESET, gpus[0]));
                }
                else {
                    for (int i = 0; i < gpus.length; i++) {
                        list.add(String.format("%sGPU%d:%s %s", ChatColor.BOLD, i, ChatColor.RESET, gpus[i]));
                    }
                }
            }

            list.add(String.format("%sDisk:%s %s", ChatColor.BOLD, ChatColor.RESET, info.getDisk()));
            list.add(String.format("%sSerial:%s %s", ChatColor.BOLD, ChatColor.RESET, info.getSerialNumber()));
            list.add(String.format("%sTemp:%s %s", ChatColor.BOLD, ChatColor.RESET, info.getTemps()));

            String[] array = new String[list.size()];
            list.toArray(array);

            sender.sendMessage(array);
        }

        return true;
    }
}
