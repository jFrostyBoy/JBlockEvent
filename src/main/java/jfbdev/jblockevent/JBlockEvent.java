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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
import org.bukkit.event.player.PlayerJoinEvent;
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

    private String minutesAbbr = "мин.";
    private String secondsAbbr = "сек.";
    private String timerFormat = "%m %s";

    private Sound soundSpawn = Sound.ENTITY_ENDER_DRAGON_AMBIENT;
    private Sound soundBreakable = Sound.BLOCK_NOTE_BLOCK_BELL;
    private Sound soundUnbreakable = Sound.ENTITY_VILLAGER_NO;
    private Sound soundDisappear = Sound.ENTITY_GENERIC_EXPLODE;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;

    private boolean bossbarEnabled = true;
    private String bossbarColorStr = "PURPLE";
    private String bossbarStyleStr = "SEGMENTED_10";
    private String bossbarProtectedText = "";
    private boolean bossbarProtectedProgress = true;
    private String bossbarBreakableText = "";
    private boolean bossbarBreakableProgress = true;

    private final Map<String, String> worldColors = new HashMap<>();
    private final Map<String, String> worldNames = new HashMap<>();

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
                                 int breakableDelaySeconds, int disappearSeconds, String displayName) {}

    private final Map<Integer, EventLocation> eventById = new HashMap<>();
    private final List<EventLocation> eventLocations = new ArrayList<>();

    private final Map<Location, String> activeHologramNames = new HashMap<>();
    private final Set<Location> protectedBlocks = new HashSet<>();
    private final Map<Location, BukkitTask> countdownTasks = new HashMap<>();
    private final Map<Location, BukkitTask> breakableUpdateTasks = new HashMap<>();
    private final Map<Location, BukkitTask> spawnTasks = new HashMap<>();
    private final Map<Location, BukkitTask> disappearTasks = new HashMap<>();
    private final Map<Location, Integer> remainingSeconds = new HashMap<>();
    private final Map<Location, Long> spawnTimeMap = new HashMap<>();
    private final Map<Location, BossBar> bossBars = new HashMap<>();

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
        removeAllBossBars();
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
            minutesAbbr = timerSec.getString("minutes", "мин.");
            secondsAbbr = timerSec.getString("seconds", "сек.");
            timerFormat = timerSec.getString("format", "%m %s");
        }

        ConfigurationSection soundSec = config.getConfigurationSection("sounds");
        if (soundSec != null) {
            try {
                soundSpawn = Sound.valueOf(soundSec.getString("spawn", "ENTITY_ENDER_DRAGON_AMBIENT").toUpperCase());
                soundBreakable = Sound.valueOf(soundSec.getString("breakable", "BLOCK_NOTE_BLOCK_BELL").toUpperCase());
                soundUnbreakable = Sound.valueOf(soundSec.getString("unbrekable", "ENTITY_VILLAGER_NO").toUpperCase());
                soundDisappear = Sound.valueOf(soundSec.getString("disappear", "ENTITY_GENERIC_EXPLODE").toUpperCase());
            } catch (IllegalArgumentException e) {
                getLogger().warning("Неверный звук в конфиге, используются дефолтные.");
            }
            soundVolume = (float) soundSec.getDouble("volume", 1.0);
            soundPitch = (float) soundSec.getDouble("pitch", 1.0);
        }

        ConfigurationSection bossbarSec = config.getConfigurationSection("bossbar");
        if (bossbarSec != null) {
            bossbarEnabled = bossbarSec.getBoolean("enabled", true);
            bossbarColorStr = bossbarSec.getString("color", "PURPLE").toUpperCase();
            bossbarStyleStr = bossbarSec.getString("style", "SEGMENTED_10").toUpperCase();
            ConfigurationSection protectedSec = bossbarSec.getConfigurationSection("protected");
            if (protectedSec != null) {
                bossbarProtectedText = protectedSec.getString("text", "");
                bossbarProtectedProgress = protectedSec.getBoolean("progress", true);
            }
            ConfigurationSection breakableSec = bossbarSec.getConfigurationSection("breakable");
            if (breakableSec != null) {
                bossbarBreakableText = breakableSec.getString("text", "");
                bossbarBreakableProgress = breakableSec.getBoolean("progress", true);
            }
        }

        ConfigurationSection worldColorsSec = config.getConfigurationSection("world_colors");
        if (worldColorsSec != null) {
            for (String key : worldColorsSec.getKeys(false)) {
                worldColors.put(key, worldColorsSec.getString(key));
            }
        }

        ConfigurationSection worldNamesSec = config.getConfigurationSection("world_names");
        if (worldNamesSec != null) {
            for (String key : worldNamesSec.getKeys(false)) {
                worldNames.put(key, worldNamesSec.getString(key));
            }
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
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
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

                String displayName = evSec.getString("display_name", "&eEvent #" + id);

                int spawnMin = Math.max(1, evSec.getInt("spawn-interval-minutes", 10));
                int delaySec = Math.max(0, evSec.getInt("breakable-delay-seconds", 300));
                int disappearSec = Math.max(delaySec + 60, evSec.getInt("disappear-seconds", 600));

                EventLocation ev = new EventLocation(id, loc, mat, spawnMin, delaySec, disappearSec, displayName);
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
            Bukkit.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
        });
    }

    private void sendList(CommandSender sender, List<String> messages, Map<String, String> placeholders) {
        messages.forEach(line -> {
            String msg = line;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    msg = msg.replace(entry.getKey(), entry.getValue());
                }
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        });
    }

    private String getColoredWorldName(World world) {
        String name = world.getName();
        String color = worldColors.getOrDefault(name, "&f");
        String display = worldNames.getOrDefault(name, name);
        return color + display;
    }

    private void startAllSpawnTasks() {
        spawnTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
        spawnTasks.clear();

        long currentTime = System.currentTimeMillis() / 1000;

        for (EventLocation ev : eventLocations) {
            long intervalSeconds = (long) ev.spawnIntervalMinutes() * 60;
            long cycle = currentTime % intervalSeconds;
            long remainingSeconds = intervalSeconds - cycle;
            long initialTicks = remainingSeconds * 20;
            if (remainingSeconds == 0) {
                initialTicks = intervalSeconds * 20;
            }
            long periodTicks = intervalSeconds * 20;

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    spawnBlockAt(ev);
                }
            }.runTaskTimer(this, initialTicks, periodTicks);
            spawnTasks.put(ev.location(), task);

            if (remainingSeconds == 0 && !activeHologramNames.containsKey(ev.location())) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        spawnBlockAt(ev);
                    }
                }.runTaskLater(this, 20L); // Spawn immediately next tick
            }
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
        updateHologramWithTimer(holoName, ev, ev.breakableDelaySeconds());

        activeHologramNames.put(loc, holoName);
        protectedBlocks.add(loc);

        spawnTimeMap.put(loc, System.currentTimeMillis());

        Map<String, String> ph = new HashMap<>();
        ph.put("%display_name%", ev.displayName());
        ph.put("%world%", getColoredWorldName(loc.getWorld()));
        ph.put("%x%", String.valueOf(loc.getBlockX()));
        ph.put("%y%", String.valueOf(loc.getBlockY()));
        ph.put("%z%", String.valueOf(loc.getBlockZ()));
        broadcastList(msgEventStartBroadcast, ph);

        loc.getWorld().playSound(loc, soundSpawn, soundVolume, soundPitch);

        if (bossbarEnabled) {
            BarColor color;
            try {
                color = BarColor.valueOf(bossbarColorStr);
            } catch (IllegalArgumentException e) {
                color = BarColor.PURPLE;
            }
            BarStyle style;
            try {
                style = BarStyle.valueOf(bossbarStyleStr);
            } catch (IllegalArgumentException e) {
                style = BarStyle.SEGMENTED_10;
            }
            BossBar bar = Bukkit.createBossBar("", color, style);
            bossBars.put(loc, bar);
            Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
            updateBossBar(loc, ev);
        }

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
                    updateHologramAfter(holoName, ev);
                    protectedBlocks.remove(loc);
                    loc.getWorld().playSound(loc, soundBreakable, soundVolume, soundPitch);
                    if (bossbarEnabled) {
                        startBreakableUpdateTask(loc, ev);
                    }
                    cancel();
                    return;
                }
                updateHologramWithTimer(holoName, ev, left);
                if (bossbarEnabled) {
                    updateBossBar(loc, ev);
                }
            }
        }.runTaskTimer(this, 20L, 20L);
        countdownTasks.put(loc, countdown);

        BukkitTask disappear = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeHologramNames.containsKey(loc)) return;
                endEventNaturally(loc, holoName, ev);
            }
        }.runTaskLater(this, (long) ev.disappearSeconds() * 20L);
        disappearTasks.put(loc, disappear);
    }

    private void updateHologramWithTimer(String holoName, EventLocation ev, int secondsLeft) {
        String timer = formatTime(secondsLeft);
        List<String> lines = new ArrayList<>();
        for (String raw : getConfig().getStringList("hologram-before")) {
            String replaced = raw.replace("%display_name%", ev.displayName()).replace("%timer_delay%", timer);
            lines.add(ChatColor.translateAlternateColorCodes('&', replaced));
        }
        Hologram holo = DHAPI.getHologram(holoName);
        if (holo != null) DHAPI.setHologramLines(holo, lines);
    }

    private void updateHologramAfter(String holoName, EventLocation ev) {
        List<String> lines = new ArrayList<>();
        for (String raw : getConfig().getStringList("hologram-after")) {
            String replaced = raw.replace("%display_name%", ev.displayName());
            lines.add(ChatColor.translateAlternateColorCodes('&', replaced));
        }
        Hologram holo = DHAPI.getHologram(holoName);
        if (holo != null) DHAPI.setHologramLines(holo, lines);
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "0 " + secondsAbbr;
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        String m = min > 0 ? min + " " + minutesAbbr : "";
        String s = sec > 0 ? sec + " " + secondsAbbr : "";
        return timerFormat.replace("%m", m).replace("%s", s).trim();
    }

    private void updateBossBar(Location loc, EventLocation ev) {
        BossBar bar = bossBars.get(loc);
        if (bar == null) return;

        String worldName = getColoredWorldName(loc.getWorld());
        String displayName = ev.displayName();

        int remainingBreakable = remainingSeconds.getOrDefault(loc, 0);
        String text;
        double progress;
        if (remainingBreakable > 0) {
            // Protected phase
            String timer = formatTime(remainingBreakable);
            text = bossbarProtectedText.replace("%display_name%", displayName)
                    .replace("%world%", worldName)
                    .replace("%timer_delay%", timer);
            if (bossbarProtectedProgress) {
                progress = (double) remainingBreakable / ev.breakableDelaySeconds();
            } else {
                progress = 1.0;
            }
        } else {
            // Breakable phase
            int remainingToDisappear = calculateRemainingToDisappear(loc, ev);
            String timer = formatTime(remainingToDisappear);
            text = bossbarBreakableText.replace("%display_name%", displayName)
                    .replace("%world%", worldName)
                    .replace("%timer_delay%", timer);
            if (bossbarBreakableProgress) {
                int breakableDuration = ev.disappearSeconds() - ev.breakableDelaySeconds();
                progress = (double) remainingToDisappear / breakableDuration;
            } else {
                progress = 1.0;
            }
        }
        bar.setTitle(ChatColor.translateAlternateColorCodes('&', text));
        bar.setProgress(progress);
    }

    private int calculateRemainingToDisappear(Location loc, EventLocation ev) {
        Long spawnTime = spawnTimeMap.get(loc);
        if (spawnTime == null) return 0;
        long elapsedMillis = System.currentTimeMillis() - spawnTime;
        int elapsedSec = (int) (elapsedMillis / 1000);
        return Math.max(0, ev.disappearSeconds() - elapsedSec);
    }

    private void startBreakableUpdateTask(Location loc, EventLocation ev) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeHologramNames.containsKey(loc)) {
                    cancel();
                    return;
                }
                updateBossBar(loc, ev);
            }
        }.runTaskTimer(this, 20L, 20L);
        breakableUpdateTasks.put(loc, task);
    }

    private void endEventNaturally(Location loc, String holoName, EventLocation ev) {
        Optional.ofNullable(DHAPI.getHologram(holoName)).ifPresent(Hologram::delete);
        loc.getBlock().setType(Material.AIR);
        loc.getWorld().playSound(loc, soundDisappear, soundVolume, soundPitch);

        Map<String, String> ph = new HashMap<>();
        ph.put("%display_name%", ev.displayName());
        broadcastList(msgEventEndBroadcast, ph);

        cleanup(loc);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        if (!activeHologramNames.containsKey(loc)) return;

        EventLocation ev = eventLocations.stream()
                .filter(eventLoc -> eventLoc.location().equals(loc))
                .findFirst()
                .orElse(null);
        if (ev == null) return;

        if (protectedBlocks.contains(loc)) {
            e.setCancelled(true);
            String timer = formatTime(remainingSeconds.get(loc));
            Map<String, String> ph = new HashMap<>();
            ph.put("%timer_delay%", timer);
            sendList(e.getPlayer(), msgProtectedBreak, ph);
            loc.getWorld().playSound(loc, soundUnbreakable, soundVolume, soundPitch);
            return;
        }

        String holoName = activeHologramNames.remove(loc);
        Optional.ofNullable(DHAPI.getHologram(holoName)).ifPresent(Hologram::delete);

        cancelTasks(loc);

        e.setDropItems(true);
        e.getBlock().breakNaturally(e.getPlayer().getInventory().getItemInMainHand());

        loc.getWorld().playSound(loc, soundDisappear, soundVolume, soundPitch);

        Map<String, String> ph = new HashMap<>();
        ph.put("%display_name%", ev.displayName());
        ph.put("%player%", e.getPlayer().getName());
        broadcastList(msgBreakSuccessBroadcast, ph);

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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        for (BossBar bar : bossBars.values()) {
            bar.addPlayer(e.getPlayer());
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

            EventLocation ev = eventLocations.stream()
                    .filter(e -> e.location().equals(loc))
                    .findFirst()
                    .orElse(null);
            if (ev == null) continue;

            remainingSeconds.put(loc, remainingBreakable);
            activeHologramNames.put(loc, holoName);
            if (remainingBreakable > 0) {
                protectedBlocks.add(loc);
                updateHologramWithTimer(holoName, ev, remainingBreakable);
            } else {
                updateHologramAfter(holoName, ev);
            }

            long elapsed = ev.disappearSeconds() - disappearRemainingSeconds;
            spawnTimeMap.put(loc, current - elapsed * 1000);

            if (bossbarEnabled) {
                BarColor color;
                try {
                    color = BarColor.valueOf(bossbarColorStr);
                } catch (IllegalArgumentException e) {
                    color = BarColor.PURPLE;
                }
                BarStyle style;
                try {
                    style = BarStyle.valueOf(bossbarStyleStr);
                } catch (IllegalArgumentException e) {
                    style = BarStyle.SEGMENTED_10;
                }
                BossBar bar = Bukkit.createBossBar("", color, style);
                bossBars.put(loc, bar);
                Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
                updateBossBar(loc, ev);
            }

            if (remainingBreakable > 0) {
                BukkitTask countdown = new BukkitRunnable() {
                    @Override
                    public void run() {
                        int left = remainingSeconds.getOrDefault(loc, 0) - 1;
                        remainingSeconds.put(loc, left);
                        if (left <= 0) {
                            updateHologramAfter(holoName, ev);
                            protectedBlocks.remove(loc);
                            loc.getWorld().playSound(loc, soundBreakable, soundVolume, soundPitch);
                            if (bossbarEnabled) {
                                startBreakableUpdateTask(loc, ev);
                            }
                            cancel();
                            return;
                        }
                        updateHologramWithTimer(holoName, ev, left);
                        if (bossbarEnabled) {
                            updateBossBar(loc, ev);
                        }
                    }
                }.runTaskTimer(this, 20L, 20L);
                countdownTasks.put(loc, countdown);
            }

            if (disappearRemainingSeconds > 0) {
                BukkitTask disappear = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!activeHologramNames.containsKey(loc)) return;
                        endEventNaturally(loc, holoName, ev);
                    }
                }.runTaskLater(this, disappearRemainingSeconds * 20L);
                disappearTasks.put(loc, disappear);
            }

            if (remainingBreakable <= 0 && bossbarEnabled) {
                startBreakableUpdateTask(loc, ev);
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
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void cancelTasks(Location loc) {
        Optional.ofNullable(countdownTasks.remove(loc)).ifPresent(BukkitTask::cancel);
        Optional.ofNullable(breakableUpdateTasks.remove(loc)).ifPresent(BukkitTask::cancel);
        Optional.ofNullable(disappearTasks.remove(loc)).ifPresent(BukkitTask::cancel);
    }

    private void cleanup(Location loc) {
        activeHologramNames.remove(loc);
        protectedBlocks.remove(loc);
        remainingSeconds.remove(loc);
        spawnTimeMap.remove(loc);
        Optional.ofNullable(bossBars.remove(loc)).ifPresent(BossBar::removeAll);
    }

    private void cancelAllTasks() {
        spawnTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
        countdownTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
        breakableUpdateTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
        disappearTasks.values().forEach(task -> Optional.ofNullable(task).ifPresent(BukkitTask::cancel));
    }

    private void deleteAllHolograms() {
        activeHologramNames.values().forEach(name -> Optional.ofNullable(DHAPI.getHologram(name)).ifPresent(Hologram::delete));
    }

    private void removeAllBossBars() {
        bossBars.values().forEach(BossBar::removeAll);
    }

    private void clearMaps() {
        activeHologramNames.clear();
        protectedBlocks.clear();
        countdownTasks.clear();
        breakableUpdateTasks.clear();
        spawnTasks.clear();
        disappearTasks.clear();
        remainingSeconds.clear();
        spawnTimeMap.clear();
        bossBars.clear();
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
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("jbe")) {
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
        }

        return completions;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        boolean isJbe = cmd.getName().equalsIgnoreCase("jbe");
        boolean isEd = cmd.getName().equalsIgnoreCase("eventsdelay");

        if (isEd || (isJbe && args.length > 0 && (args[0].equalsIgnoreCase("eventsdelay") || args[0].equalsIgnoreCase("ed")))) {
            if (!sender.hasPermission("jblockevent.player")) {
                sendList(sender, msgEventsDelayNoPermission, null);
                return true;
            }

            Map<String, String> headerPh = new HashMap<>();
            String total = String.valueOf(eventLocations.size());
            headerPh.put("%total%", total);
            headerPh.put("%total&", total + "&");
            sendList(sender, msgEventsDelayHeader, headerPh);

            long currentTime = System.currentTimeMillis() / 1000;

            for (EventLocation ev : eventLocations) {
                Location loc = ev.location();
                String idStr = String.valueOf(ev.id());
                Map<String, String> ph = new HashMap<>();
                ph.put("%id%", idStr);
                ph.put("%id ", idStr + " ");
                ph.put("%display_name%", ev.displayName());
                ph.put("%x%", String.valueOf(loc.getBlockX()));
                ph.put("%y%", String.valueOf(loc.getBlockY()));
                ph.put("%z%", String.valueOf(loc.getBlockZ()));
                ph.put("%world%", getColoredWorldName(loc.getWorld()));

                if (activeHologramNames.containsKey(loc)) {
                    int remaining = remainingSeconds.getOrDefault(loc, 0);
                    String time = formatRemainingTime(remaining);
                    ph.put("%time%", time);
                    if (remaining > 0) {
                        sendList(sender, msgEventsDelayActiveProtected, ph);
                    } else {
                        sendList(sender, msgEventsDelayActiveBreakable, ph);
                    }
                } else {
                    long intervalSeconds = (long) ev.spawnIntervalMinutes() * 60;
                    long cycle = currentTime % intervalSeconds;
                    long remainingToSpawn = intervalSeconds - cycle;

                    String time = formatRemainingTime(remainingToSpawn);
                    ph.put("%time%", time);
                    sendList(sender, msgEventsDelayInactive, ph);
                }
            }

            Map<String, String> footerPh = new HashMap<>();
            footerPh.put("%total%", total);
            footerPh.put("%total&", total + "&");
            sendList(sender, msgEventsDelayFooter, footerPh);
            return true;
        }

        if (!isJbe) return false;

        if (!sender.hasPermission("jblockevent.admin")) {
            sendList(sender, msgNoPermission, null);
            return true;
        }

        if (args.length == 0) {
            sendList(sender, msgReloadUsage, null);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadConfigValues();

            Map<String, String> ph = new HashMap<>();
            ph.put("%events%", String.valueOf(eventLocations.size()));
            sendList(sender, msgReloadSuccess, ph);
            return true;
        }

        if (args[0].equalsIgnoreCase("start") && args.length == 2) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Map<String, String> ph = new HashMap<>();
                ph.put("%number%", args[1]);
                sendList(sender, msgInvalidEventNumber, ph);
                return true;
            }

            EventLocation ev = eventById.get(id);
            if (ev == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("%number%", String.valueOf(id));
                sendList(sender, msgInvalidEventNumber, ph);
                return true;
            }

            if (activeHologramNames.containsKey(ev.location())) {
                Map<String, String> ph = new HashMap<>();
                ph.put("%number%", String.valueOf(id));
                sendList(sender, msgEventAlreadyActive, ph);
                return true;
            }

            spawnBlockAt(ev);
            Map<String, String> ph = new HashMap<>();
            ph.put("%number%", String.valueOf(id));
            sendList(sender, msgEventStartedManually, ph);
            return true;
        }

        if (args[0].equalsIgnoreCase("stop") && args.length == 2) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Map<String, String> ph = new HashMap<>();
                ph.put("%number%", args[1]);
                sendList(sender, msgInvalidEventNumber, ph);
                return true;
            }

            EventLocation ev = eventById.get(id);
            if (ev == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("%number%", String.valueOf(id));
                sendList(sender, msgInvalidEventNumber, ph);
                return true;
            }

            if (!activeHologramNames.containsKey(ev.location())) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "§cИвент №" + id + " не активен!"));
                return true;
            }

            Location loc = ev.location();
            String holoName = activeHologramNames.remove(loc);
            Optional.ofNullable(DHAPI.getHologram(holoName)).ifPresent(Hologram::delete);

            cancelTasks(loc);

            loc.getBlock().setType(Material.AIR);
            loc.getWorld().playSound(loc, soundDisappear, soundVolume, soundPitch);

            cleanup(loc);

            Map<String, String> ph = new HashMap<>();
            ph.put("%id%", String.valueOf(id));
            ph.put("%display_name%", ev.displayName());
            sendList(sender, msgEventStopped, ph);
            return true;
        }

        sendList(sender, msgReloadUsage, null);
        return true;
    }
}
