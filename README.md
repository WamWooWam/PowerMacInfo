# PowerMacInfo
 Spigot plugin to show info about the PowerMac the server is running on. 

 This code is not very good. I don't really like writing Java, and it's hacked together to work specifically with my PowerMac G5 Dual Core (late 2005) running Debian sid. The code will require work to run on non-G5 systems (specifically with sensors) but the rest should work.

 The methods of pulling some info are taken from [screenfetch](https://github.com/KittyKatt/screenFetch) and ported to Java, and while screenfetch is GPLv3 I'm releasing this as MIT anyway because you can't really claim ownership over reading `/proc/cpuinfo`.

## Requirements
 - A PowerMac running Linux
 - Java 11+
 - Spigot compatible server (I used PaperMC)
 - Minecraft 1.12+
