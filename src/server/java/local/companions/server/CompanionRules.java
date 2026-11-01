package local.companions.server;

import com.google.gson.*;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

public final class CompanionRules extends JavaPlugin implements Listener {
    private final Map<UUID, String> dead = new LinkedHashMap<>();
    private boolean permanentDeath;
    private Difficulty lastDifficulty;
    private String lastState = "";
    private JsonObject appearances = new JsonObject();

    @Override public void onEnable() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            Path file = getDataFolder().toPath().resolve("config.yml");
            YamlConfiguration yaml = new YamlConfiguration();
            if (Files.exists(file)) yaml.load(file.toFile());
            if (yaml.getKeys(false).stream().anyMatch(k -> !Set.of("permanent-death", "dead-players").contains(k))) throw new IllegalArgumentException("Unknown CompanionRules configuration field");
            permanentDeath = yaml.getBoolean("permanent-death", false);
            var section = yaml.getConfigurationSection("dead-players");
            if (section != null) for (String id : section.getKeys(false)) dead.put(UUID.fromString(id), section.getString(id));
            saveRules();
        } catch (Exception e) { throw new IllegalStateException("Could not load permanent-death records", e); }
        lastDifficulty = mainWorld().getDifficulty();
        reloadSkins();
        getServer().getPluginManager().registerEvents(this, this);
        publish();
        getServer().getScheduler().runTaskTimer(this, () -> {
            Difficulty current = mainWorld().getDifficulty();
            if (current != lastDifficulty) persistDifficulty(current);
            publish();
        }, 20L, 20L);
    }
    private World mainWorld() { return getServer().getWorlds().getFirst(); }
    private boolean permanent() { return permanentDeath || getServer().isHardcore(); }
    private void reloadSkins() {
        Path file = getDataFolder().toPath().resolve("appearances.json");
        try { appearances = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject(); }
        catch (Exception e) { throw new IllegalStateException("Could not load companion skins", e); }
        for (Player player : getServer().getOnlinePlayers()) applySkin(player);
    }
    private void applySkin(Player player) {
        JsonObject agents = appearances.has("agents") ? appearances.getAsJsonObject("agents") : new JsonObject();
        String name = player.getName().toLowerCase(Locale.ROOT);
        if (!agents.has(name)) return;
        String key = agents.get(name).getAsString();
        JsonObject texture = key.isEmpty() ? null : appearances.getAsJsonObject("cache").getAsJsonObject(key);
        String value = texture == null ? null : texture.get("value").getAsString();
        var profile = player.getPlayerProfile();
        var old = profile.getProperties().stream().filter(p -> p.getName().equals("textures")).findFirst().orElse(null);
        if (Objects.equals(value, old == null ? null : old.getValue())) return;
        profile.removeProperty("textures");
        if (texture != null) profile.setProperty(new ProfileProperty("textures", value, texture.get("signature").getAsString()));
        player.setPlayerProfile(profile);
    }
    private void setDifficulty(Difficulty value) { for (World world : getServer().getWorlds()) world.setDifficulty(value); }
    private void persistDifficulty(Difficulty value) {
        // Minecraft's own properties remain the only saved difficulty setting.
        Path file = Path.of("server.properties"), temp = file.resolveSibling("server.properties.tmp");
        try {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
            properties.setProperty("difficulty", value.name().toLowerCase(Locale.ROOT));
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { properties.store(writer, "Minecraft server properties"); }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            lastDifficulty = value;
        } catch (IOException e) { throw new IllegalStateException("Could not save Minecraft difficulty", e); }
    }
    private void saveRules() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("permanent-death", permanentDeath);
        dead.forEach((id, name) -> yaml.set("dead-players." + id, name));
        try { writeAtomic("config.yml", yaml.saveToString()); }
        catch (IOException e) { throw new IllegalStateException("Could not save permanent-death records", e); }
    }
    private void writeAtomic(String name, String value) throws IOException {
        Path file = getDataFolder().toPath().resolve(name), temp = file.resolveSibling(name + ".tmp");
        Files.writeString(temp, value, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    private JsonObject state() {
        JsonObject result = new JsonObject();
        result.addProperty("difficulty", mainWorld().getDifficulty().name().toLowerCase(Locale.ROOT));
        result.addProperty("permanentDeath", permanent());
        result.addProperty("nativeHardcore", getServer().isHardcore());
        JsonArray online = new JsonArray(); for (Player p : getServer().getOnlinePlayers()) online.add(p.getName()); result.add("onlinePlayers", online);
        JsonArray choices = new JsonArray(); for (Difficulty d : Difficulty.values()) choices.add(d.name().toLowerCase(Locale.ROOT)); result.add("difficulties", choices);
        JsonArray players = new JsonArray();
        dead.forEach((id, name) -> { JsonObject p = new JsonObject(); p.addProperty("uuid", id.toString()); p.addProperty("name", name); players.add(p); });
        result.add("deadPlayers", players); return result;
    }
    private void publish() {
        String value = state().toString();
        if (value.equals(lastState)) return;
        try { writeAtomic("state.json", value); lastState = value; }
        catch (IOException e) { getLogger().severe("Could not publish server rules: " + e.getMessage()); }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender)) { sender.sendMessage("Use the local Companions host settings."); return true; }
        try {
            if (args.length == 3 && args[0].equals("set")) {
                Difficulty next = Difficulty.valueOf(args[1].toUpperCase(Locale.ROOT));
                if (!Set.of("true", "false").contains(args[2])) throw new IllegalArgumentException("Permanent death must be true or false");
                boolean nextDeath = Boolean.parseBoolean(args[2]);
                if (getServer().isHardcore() && (next != Difficulty.HARD || !nextDeath)) throw new IllegalArgumentException("Native Hardcore controls these rules");
                Difficulty oldDifficulty = mainWorld().getDifficulty(); boolean oldDeath = permanentDeath;
                try {
                    if (!getServer().isHardcore() && nextDeath != permanentDeath) { permanentDeath = nextDeath; saveRules(); }
                    if (next != oldDifficulty) { setDifficulty(next); persistDifficulty(next); }
                } catch (RuntimeException e) { permanentDeath = oldDeath; saveRules(); setDifficulty(oldDifficulty); throw e; }
            } else if (args.length == 1 && args[0].equals("skins")) reloadSkins();
            else if (!(args.length == 1 && args[0].equals("status"))) throw new IllegalArgumentException("Use status, skins or set <difficulty> <permanent-death>");
            publish(); sender.sendMessage(state().toString());
        } catch (RuntimeException e) { JsonObject error = new JsonObject(); error.addProperty("error", e.getMessage()); sender.sendMessage(error.toString()); }
        return true;
    }
    @EventHandler public void worldLoaded(WorldLoadEvent event) { event.getWorld().setDifficulty(mainWorld().getDifficulty()); }
    @EventHandler(priority = EventPriority.MONITOR) public void died(PlayerDeathEvent event) {
        if (!permanent()) return;
        Player player = event.getEntity(); dead.put(player.getUniqueId(), player.getName()); saveRules(); publish();
    }
    private void spectator(Player player) { if (dead.containsKey(player.getUniqueId())) player.setGameMode(GameMode.SPECTATOR); }
    @EventHandler public void joined(PlayerJoinEvent event) { spectator(event.getPlayer()); getServer().getScheduler().runTask(this, () -> { applySkin(event.getPlayer()); publish(); }); }
    @EventHandler public void left(PlayerQuitEvent event) { getServer().getScheduler().runTask(this, this::publish); }
    @EventHandler public void respawned(PlayerRespawnEvent event) { getServer().getScheduler().runTask(this, () -> spectator(event.getPlayer())); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST) public void modeChanged(PlayerGameModeChangeEvent event) {
        if (dead.containsKey(event.getPlayer().getUniqueId()) && event.getNewGameMode() != GameMode.SPECTATOR) event.setCancelled(true);
    }
}
