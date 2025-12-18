package jfbdev.jblockevent;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class JBlockEvent extends JavaPlugin implements Listener, TabCompleter {

    private File dataFile;
    private FileConfiguration dataConfig;

    private double holoHeight = 2.0;
    private double holoX = 0.5;
    private double holoZ = 0.5;

    private String minSingular = "минута";
    private String minPlural = "минуты";
    private String minMany = "минут";
    private String secSingular = "секунда";
    private String secPlural = "секунды";
    private String secMany = "секунд";
    private String separator = " ";
    private String onlySecondsFormat = "сек.";

    private Sound soundSpawn = Sound.ENTITY_ENDER_DRAGON_AMBIENT;
    private Sound soundBreakable = Sound.BLOCK_NOTE_BLOCK_BELL;
    private Sound soundDisappear = Sound.ENTITY_GENERIC_EXPLODE;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;

    private List<String> msgNoPermission;
    private List<String> msgReloadUsage;
    private List<String> msgReloadSuccess;
    private List<String> msgProtectedBreak;
    private List<String> msgBreakSuccessBroadcast;
    private List<String> msgEventStartBroadcast;
    private List<String> msgEventEndBroadcast;
    private List<String> msgInvalidEventNumber;
    private List<String> msgEventAlreadyActive;
    private List<String> msgEventStartedManually;
    private List<String> msgEventsDelayHeader;
    private List<String> msgEventsDelayInactive;
    private List<String> msgEventsDelayActiveProtected;
    private List<String> msgEventsDelayActiveBreakable;
    private List<String> msgEventsDelayFooter;
    private List<String> msgEventsDelayNoPermission;
    private List<String> msgEventStopped;

    private record EventLocation(int id, Location location, Material material, int spawnIntervalMinutes,
                                 int breakableDelaySeconds, int disappearSeconds) {}

    private final Map<Integer, EventLocation> eventById = new HashMap<>();
    private final List<EventLocation> eventLocations = new ArrayList<>();

    private final Map<Location, String> activeHologramNames = new HashMap<>();
    private final Set<Location> protectedBlocks = new HashSet<>();
    private final Map<Location, BukkitTask> countdownTasks = new HashMap<>();
    private final Map<Location, BukkitTask> spawnTasks = new HashMap<>();
    private final Map<Location, BukkitTask> disappearTasks = new HashMap<>();
    private final Map<Location, Integer> remainingSeconds = new HashMap<>();
    private final Map<Location, Long> spawnTimeMap = new HashMap<>();

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") == null) {
            getLogger().severe("DecentHolograms не найден! Плагин отключается.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        dataFile = new File(getDataFolder(), "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);

        restoreActiveEvents();

        startAllSpawnTasks();

        Objects.requireNonNull(getCommand("jbe")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("eventsdelay")).setTabCompleter(this);

        getLogger().info("JBlockEvent загружен! Ивентов: " + eventLocations.size());
    }

    @Override
    public void onDisable() {
        saveActiveEvents();
        cancelAllTasks();
        deleteAllHolograms();
        clearMaps();
        getLogger().info("JBlockEvent отключён.");
    }

    private void loadConfigValues() {
        reloadConfig();
        FileConfiguration config = getConfig();

        eventById.clear();
        eventLocations.clear();

        ConfigurationSection eventsSec = config.getConfigurationSection("events");
        if (eventsSec == null || eventsSec.getKeys(false).isEmpty()) {
            getLogger().warning("Нет ивентов в конфиге!");
            return;
        }

        ConfigurationSection holoOffset = config.getConfigurationSection("hologram-offset");
        if (holoOffset != null) {
            holoHeight = holoOffset.getDouble("height", 2.0);
            holoX = holoOffset.getDouble("x", 0.5);
            holoZ = holoOffset.getDouble("z", 0.5);
        }

        ConfigurationSection timerSec = config.getConfigurationSection("timer-format");
        if (timerSec != null) {
            minSingular = timerSec.getString("minutes-singular", "минута");
            minPlural = timerSec.getString("minutes-plural", "минуты");
            minMany = timerSec.getString("minutes-many", "минут");
            secSingular = timerSec.getString("seconds-singular", "секунда");
            secPlural = timerSec.getString("seconds-plural", "секунды");
            secMany = timerSec.getString("seconds-many", "секунд");
            separator = timerSec.getString("separator", " ");
            onlySecondsFormat = timerSec.getString("only-seconds", "сек.");
        }

        ConfigurationSection soundSec = config.getConfigurationSection("sounds");
        if (soundSec != null) {
            try {
                soundSpawn = Sound.valueOf(soundSec.getString("spawn", "ENTITY_ENDER_DRAGON_AMBIENT").toUpperCase());
                soundBreakable = Sound.valueOf(soundSec.getString("breakable", "BLOCK_NOTE_BLOCK_BELL").toUpperCase());
                soundDisappear = Sound.valueOf(soundSec.getString("disappear", "ENTITY_GENERIC_EXPLODE").toUpperCase());
            } catch (IllegalArgumentException e) {
                getLogger().warning("Неверный звук в конфиге, используются дефолтные.");
            }
            soundVolume = (float) soundSec.getDouble("volume", 1.0);
            soundPitch = (float) soundSec.getDouble("pitch", 1.0);
        }

        ConfigurationSection msgSec = config.getConfigurationSection("messages");
        if (msgSec != null) {
            msgNoPermission = coloredList(msgSec.getStringList("no-permission"));
            msgReloadUsage = coloredList(msgSec.getStringList("reload-usage"));
            msgReloadSuccess = coloredList(msgSec.getStringList("reload-success"));
            msgProtectedBreak = coloredList(msgSec.getStringList("protected-break"));
            msgBreakSuccessBroadcast = coloredList(msgSec.getStringList("break-success-broadcast"));
            msgEventStartBroadcast = coloredList(msgSec.getStringList("event-start-broadcast"));
            msgEventEndBroadcast = coloredList(msgSec.getStringList("event-end-broadcast"));
            msgInvalidEventNumber = coloredList(msgSec.getStringList("invalid-event-number"));
            msgEventAlreadyActive = coloredList(msgSec.getStringList("event-already-active"));
            msgEventStartedManually = coloredList(msgSec.getStringList("event-started-manually"));
            msgEventsDelayHeader = coloredList(msgSec.getStringList("eventsdelay-header"));
            msgEventsDelayInactive = coloredList(msgSec.getStringList("eventsdelay-inactive"));
            msgEventsDelayActiveProtected = coloredList(msgSec.getStringList("eventsdelay-active-protected"));
            msgEventsDelayActiveBreakable = coloredList(msgSec.getStringList("eventsdelay-active-breakable"));
            msgEventsDelayFooter = coloredList(msgSec.getStringList("eventsdelay-footer"));
            msgEventsDelayNoPermission = coloredList(msgSec.getStringList("eventsdelay-no-permission"));
            msgEventStopped = coloredList(msgSec.getStringList("event-stopped"));
        }

        // Парсинг ивентов
        for (String key : eventsSec.getKeys(false)) {
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            ConfigurationSection evSec = eventsSec.getConfigurationSection(key);
            if (evSec == null) continue;

            String locStr = evSec.getString("location");
            if (locStr == null) continue;

            String[] parts = locStr.split(":");
            if (parts.length != 4) continue;

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;

            try {
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                Location loc = new Location(world, x, y, z);

                String matStr = evSec.getString("block-material");
                Material mat;
                if (matStr != null && !matStr.isEmpty()) {
                    try {
                        mat = Material.valueOf(matStr.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("Ивент " + id + ": неверный материал '" + matStr + "', используется BEACON.");
                        mat = Material.BEACON;
                    }
                } else {
                    getLogger().warning("Ивент " + id + ": материал блока не указан! Используется BEACON.");
                    mat = Material.BEACON;
                }

                int spawnMin = Math.max(1, evSec.getInt("spawn-interval-minutes", 10));
                int delaySec = Math.max(0, evSec.getInt("breakable-delay-seconds", 300));
                int disappearSec = Math.max(delaySec + 60, evSec.getInt("disappear-seconds", 600));

                EventLocation ev = new EventLocation(id, loc, mat, spawnMin, delaySec, disappearSec);
                eventLocations.add(ev);
                eventById.put(id, ev);

            } catch (NumberFormatException ignored) {}
        }
    }

    private List<String> coloredList(List<String> list) {
        return list.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList();
    }

    private void broadcastList(List<String> messages, Map<String, String> placeholders) {
        messages.forEach(line -> {
            String msg = line;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    msg = msg.replace(entry.getKey(), entry.getValue());
                }
            }
            Bukkit.getServer().broadcastMessage(msg);
        });
    }

    private void startAllSpawnTasks() {
        spawnTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
        spawnTasks.clear();

        for (EventLocation ev : eventLocations) {
            long ticks = (long) ev.spawnIntervalMinutes() * 60L * 20L;
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    spawnBlockAt(ev);
                }
            }.runTaskTimer(this, ticks, ticks);
            spawnTasks.put(ev.location(), task);
        }
    }

    private void spawnBlockAt(EventLocation ev) {
        Location loc = ev.location();
        if (activeHologramNames.containsKey(loc)) return;

        loc.getBlock().setType(ev.material());

        String holoName = "jblock_" + UUID.randomUUID();
        Location holoLoc = loc.clone().add(holoX, holoHeight, holoZ);
        DHAPI.createHologram(holoName, holoLoc);

        remainingSeconds.put(loc, ev.breakableDelaySeconds());
        updateHologramWithTimer(holoName, ev.breakableDelaySeconds());

        activeHologramNames.put(loc, holoName);
        protectedBlocks.add(loc);

        spawnTimeMap.put(loc, System.currentTimeMillis());

        Map<String, String> ph = new HashMap<>();
        ph.put("%world%", loc.getWorld().getName());
        ph.put("%x%", String.valueOf(loc.getBlockX()));
        ph.put("%y%", String.valueOf(loc.getBlockY()));
        ph.put("%z%", String.valueOf(loc.getBlockZ()));
        broadcastList(msgEventStartBroadcast, ph);

        loc.getWorld().playSound(loc, soundSpawn, soundVolume, soundPitch);

        BukkitTask countdown = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeHologramNames.containsKey(loc)) {
                    cancel();
                    return;
                }
                int left = remainingSeconds.getOrDefault(loc, 0) - 1;
                remainingSeconds.put(loc, left);

                if (left <= 0) {
                    Hologram holo = DHAPI.getHologram(holoName);
                    if (holo != null) {
                        DHAPI.setHologramLines(holo, coloredList(getConfig().getStringList("hologram-after")));
                    }
                    protectedBlocks.remove(loc);
                    loc.getWorld().playSound(loc, soundBreakable, soundVolume, soundPitch);
                    cancel();
                    return;
                }
                updateHologramWithTimer(holoName, left);
            }
        }.runTaskTimer(this, 20L, 20L);
        countdownTasks.put(loc, countdown);

        BukkitTask disappear = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeHologramNames.containsKey(loc)) return;
                endEventNaturally(loc, holoName);
            }
        }.runTaskLater(this, ev.disappearSeconds() * 20L);
        disappearTasks.put(loc, disappear);
    }

    private void updateHologramWithTimer(String holoName, int secondsLeft) {
        String timer = formatTimeRussian(secondsLeft);
        List<String> lines = new ArrayList<>();
        for (String raw : getConfig().getStringList("hologram-before")) {
            lines.add(ChatColor.translateAlternateColorCodes('&', raw.replace("%timer_delay%", timer)));
        }
        Hologram holo = DHAPI.getHologram(holoName);
        if (holo != null) DHAPI.setHologramLines(holo, lines);
    }

    private String formatTimeRussian(int totalSeconds) {
        if (totalSeconds <= 0) return "0 " + onlySecondsFormat;
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        if (min == 0) return sec + " " + declineSeconds(sec);
        String m = min + " " + declineMinutes(min);
        return sec > 0 ? m + separator + sec + " " + declineSeconds(sec) : m;
    }

    private String declineMinutes(int n) {
        if (n % 10 == 1 && n % 100 != 11) return minSingular;
        if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) return minPlural;
        return minMany;
    }

    private String declineSeconds(int n) {
        if (n % 10 == 1 && n % 100 != 11) return secSingular;
        if (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) return secPlural;
        return secMany;
    }

    private void endEventNaturally(Location loc, String holoName) {
        Optional.ofNullable(DHAPI.getHologram(holoName)).ifPresent(Hologram::delete);
        loc.getBlock().setType(Material.AIR);
        loc.getWorld().playSound(loc, soundDisappear, soundVolume, soundPitch);
        msgEventEndBroadcast.forEach(Bukkit.getServer()::broadcastMessage);
        cleanup(loc);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        if (!activeHologramNames.containsKey(loc)) return;

        if (protectedBlocks.contains(loc)) {
            e.setCancelled(true);
            msgProtectedBreak.forEach(e.getPlayer()::sendMessage);
            return;
        }

        String holoName = activeHologramNames.remove(loc);
        Optional.ofNullable(DHAPI.getHologram(holoName)).ifPresent(Hologram::delete);

        cancelTasks(loc);

        e.setDropItems(true);
        e.getBlock().breakNaturally(e.getPlayer().getInventory().getItemInMainHand());

        loc.getWorld().playSound(loc, soundDisappear, soundVolume, soundPitch);

        List<String> success = new ArrayList<>(msgBreakSuccessBroadcast);
        success.replaceAll(line -> line.replace("%player%", e.getPlayer().getName()));
        success.forEach(Bukkit.getServer()::broadcastMessage);

        cleanup(loc);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        if (activeHologramNames.containsKey(block.getLocation())) {
            e.setCancelled(true);
        }
    }

    private void saveActiveEvents() {
        dataConfig.set("active", null);
        long current = System.currentTimeMillis();

        for (Map.Entry<Location, Integer> entry : remainingSeconds.entrySet()) {
            Location loc = entry.getKey();
            int remainingBreakable = entry.getValue();

            Long spawnTime = spawnTimeMap.get(loc);
            long disappearRemainingSeconds = 0;
            if (spawnTime != null) {
                EventLocation ev = eventLocations.stream()
                        .filter(e -> e.location().equals(loc))
                        .findFirst()
                        .orElse(null);
                if (ev != null) {
                    long elapsed = (current - spawnTime) / 1000;
                    disappearRemainingSeconds = Math.max(0, ev.disappearSeconds() - elapsed);
                }
            }

            String path = "active." + serializeLocation(loc);
            dataConfig.set(path + ".remaining-breakable", remainingBreakable);
            dataConfig.set(path + ".disappear-remaining", disappearRemainingSeconds);
            dataConfig.set(path + ".material", loc.getBlock().getType().name());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Не удалось сохранить состояние активных ивентов!");
        }
    }

    private void restoreActiveEvents() {
        ConfigurationSection activeSec = dataConfig.getConfigurationSection("active");
        if (activeSec == null) return;

        long current = System.currentTimeMillis();

        for (String key : activeSec.getKeys(false)) {
            Location loc = deserializeLocation(key);
            if (loc == null) continue;

            int remainingBreakable = activeSec.getInt(key + ".remaining-breakable", 0);
            long disappearRemainingSeconds = activeSec.getLong(key + ".disappear-remaining", 600);

            String matStr = activeSec.getString(key + ".material", "BEACON");
            Material mat;
            try {
                mat = Material.valueOf(matStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                mat = Material.BEACON;
            }

            loc.getBlock().setType(mat);

            String holoName = "jblock_restore_" + UUID.randomUUID();
            Location holoLoc = loc.clone().add(holoX, holoHeight, holoZ);
            DHAPI.createHologram(holoName, holoLoc);

            remainingSeconds.put(loc, remainingBreakable);
            updateHologramWithTimer(holoName, remainingBreakable);

            activeHologramNames.put(loc, holoName);
            if (remainingBreakable > 0) protectedBlocks.add(loc);

            EventLocation ev = eventLocations.stream()
                    .filter(e -> e.location().equals(loc))
                    .findFirst()
                    .orElse(null);
            if (ev != null) {
                long elapsed = ev.disappearSeconds() - disappearRemainingSeconds;
                spawnTimeMap.put(loc, current - elapsed * 1000);
            }

            if (remainingBreakable > 0) {
                BukkitTask countdown = new BukkitRunnable() {
                    @Override
                    public void run() {
                        int left = remainingSeconds.getOrDefault(loc, 0) - 1;
                        remainingSeconds.put(loc, left);
                        if (left <= 0) {
                            Hologram holo = DHAPI.getHologram(holoName);
                            if (holo != null) DHAPI.setHologramLines(holo, coloredList(getConfig().getStringList("hologram-after")));
                            protectedBlocks.remove(loc);
                            loc.getWorld().playSound(loc, soundBreakable, soundVolume, soundPitch);
                            cancel();
                            return;
                        }
                        updateHologramWithTimer(holoName, left);
                    }
                }.runTaskTimer(this, 20L, 20L);
                countdownTasks.put(loc, countdown);
            } else {
                Hologram holo = DHAPI.getHologram(holoName);
                if (holo != null) DHAPI.setHologramLines(holo, coloredList(getConfig().getStringList("hologram-after")));
            }

            if (disappearRemainingSeconds > 0) {
                BukkitTask disappear = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!activeHologramNames.containsKey(loc)) return;
                        endEventNaturally(loc, holoName);
                    }
                }.runTaskLater(this, disappearRemainingSeconds * 20L);
                disappearTasks.put(loc, disappear);
            }
        }
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private Location deserializeLocation(String str) {
        String[] parts = str.split(";");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x + 0.5, y, z + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void cancelTasks(Location loc) {
        Optional.ofNullable(countdownTasks.remove(loc)).ifPresent(BukkitTask::cancel);
        Optional.ofNullable(disappearTasks.remove(loc)).ifPresent(BukkitTask::cancel);
    }

    private void cleanup(Location loc) {
        activeHologramNames.remove(loc);
        protectedBlocks.remove(loc);
        remainingSeconds.remove(loc);
        spawnTimeMap.remove(loc);
    }

    private void cancelAllTasks() {
        spawnTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
        countdownTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
        disappearTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
    }

    private void deleteAllHolograms() {
        activeHologramNames.values().forEach(name -> Optional.ofNullable(DHAPI.getHologram(name)).ifPresent(Hologram::delete));
    }

    private void clearMaps() {
        activeHologramNames.clear();
        protectedBlocks.clear();
        countdownTasks.clear();
        spawnTasks.clear();
        disappearTasks.clear();
        remainingSeconds.clear();
        spawnTimeMap.clear();
        eventLocations.clear();
        eventById.clear();
    }

    private String formatRemainingTime(long seconds) {
        if (seconds <= 0) return "сейчас";
        long min = seconds / 60;
        long sec = seconds % 60;
        if (min == 0) return sec + " сек.";
        if (sec == 0) return min + " мин.";
        return min + " мин. " + sec + " сек.";
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("jbe")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase()) && sender.hasPermission("jblockevent.admin")) completions.add("reload");
            if ("start".startsWith(args[0].toLowerCase()) && sender.hasPermission("jblockevent.admin")) completions.add("start");
            if ("stop".startsWith(args[0].toLowerCase()) && sender.hasPermission("jblockevent.admin")) completions.add("stop");
            if ("eventsdelay".startsWith(args[0].toLowerCase())) completions.add("eventsdelay");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("start") && sender.hasPermission("jblockevent.admin")) {
                eventById.keySet().stream().map(String::valueOf).forEach(completions::add);
            }
            if (args[0].equalsIgnoreCase("stop") && sender.hasPermission("jblockevent.admin")) {
                eventLocations.stream()
                        .filter(ev -> activeHologramNames.containsKey(ev.location()))
                        .map(ev -> String.valueOf(ev.id()))
                        .forEach(completions::add);
            }
        }

        return completions;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        boolean isJbe = cmd.getName().equalsIgnoreCase("jbe");
        boolean isEd = cmd.getName().equalsIgnoreCase("eventsdelay");

        if (isEd || (isJbe && args.length > 0 && (args[0].equalsIgnoreCase("eventsdelay") || args[0].equalsIgnoreCase("ed")))) {
            if (!sender.hasPermission("jblockevent.player")) {
                msgEventsDelayNoPermission.forEach(sender::sendMessage);
                return true;
            }

            msgEventsDelayHeader.forEach(sender::sendMessage);

            long currentTime = System.currentTimeMillis() / 1000;

            for (EventLocation ev : eventLocations) {
                Location loc = ev.location();
                String materialName = ev.material().name().toLowerCase().replace("_", " ");

                if (activeHologramNames.containsKey(loc)) {
                    int remaining = remainingSeconds.getOrDefault(loc, 0);
                    if (remaining > 0) {
                        String time = formatRemainingTime(remaining);
                        msgEventsDelayActiveProtected.forEach(line -> sender.sendMessage(
                                line.replace("%id%", String.valueOf(ev.id()))
                                        .replace("%material%", materialName)
                                        .replace("%time%", time)
                        ));
                    } else {
                        msgEventsDelayActiveBreakable.forEach(line -> sender.sendMessage(
                                line.replace("%id%", String.valueOf(ev.id()))
                                        .replace("%material%", materialName)
                                        .replace("%x%", String.valueOf(loc.getBlockX()))
                                        .replace("%y%", String.valueOf(loc.getBlockY()))
                                        .replace("%z%", String.valueOf(loc.getBlockZ()))
                        ));
                    }
                } else {
                    long intervalSeconds = (long) ev.spawnIntervalMinutes() * 60;
                    long cycle = currentTime % intervalSeconds;
                    long remainingToSpawn = intervalSeconds - cycle;

                    String time = formatRemainingTime(remainingToSpawn);
                    msgEventsDelayInactive.forEach(line -> sender.sendMessage(
                            line.replace("%id%", String.valueOf(ev.id()))
                                    .replace("%material%", materialName)
                                    .replace("%time%", time)
                    ));
                }
            }

            msgEventsDelayFooter.forEach(line -> sender.sendMessage(
                    line.replace("%total%", String.valueOf(eventLocations.size()))
            ));
            return true;
        }

        if (!isJbe) return false;

        if (!sender.hasPermission("jblockevent.admin")) {
            msgNoPermission.forEach(sender::sendMessage);
            return true;
        }

        if (args.length == 0) {
            msgReloadUsage.forEach(sender::sendMessage);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadConfigValues();

            List<String> success = new ArrayList<>(msgReloadSuccess);
            success.replaceAll(s -> s.replace("%events%", String.valueOf(eventLocations.size())));
            success.forEach(sender::sendMessage);
            return true;
        }

        if (args[0].equalsIgnoreCase("start") && args.length == 2) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                msgInvalidEventNumber.forEach(m -> sender.sendMessage(m.replace("%number%", args[1])));
                return true;
            }

            EventLocation ev = eventById.get(id);
            if (ev == null) {
                msgInvalidEventNumber.forEach(m -> sender.sendMessage(m.replace("%number%", String.valueOf(id))));
                return true;
            }

            if (activeHologramNames.containsKey(ev.location())) {
                msgEventAlreadyActive.forEach(m -> sender.sendMessage(m.replace("%number%", String.valueOf(id))));
                return true;
            }

            spawnBlockAt(ev);
            msgEventStartedManually.forEach(m -> sender.sendMessage(m.replace("%number%", String.valueOf(id))));
            return true;
        }

        if (args[0].equalsIgnoreCase("stop") && args.length == 2) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                msgInvalidEventNumber.forEach(m -> sender.sendMessage(m.replace("%number%", args[1])));
                return true;
            }

            EventLocation ev = eventById.get(id);
            if (ev == null) {
                msgInvalidEventNumber.forEach(m -> sender.sendMessage(m.replace("%number%", String.valueOf(id))));
                return true;
            }

            if (!activeHologramNames.containsKey(ev.location())) {
                sender.sendMessage("§cИвент №" + id + " не активен!");
                return true;
            }

            Location loc = ev.location();
            String holoName = activeHologramNames.remove(loc);
            Optional.ofNullable(DHAPI.getHologram(holoName)).ifPresent(Hologram::delete);

            cancelTasks(loc);

            loc.getBlock().setType(Material.AIR);
            loc.getWorld().playSound(loc, soundDisappear, soundVolume, soundPitch);

            cleanup(loc);

            List<String> stopped = new ArrayList<>(msgEventStopped);
            stopped.replaceAll(line -> line.replace("%id%", String.valueOf(id)));
            stopped.forEach(sender::sendMessage);
            return true;
        }

        msgReloadUsage.forEach(sender::sendMessage);
        return true;
    }
}