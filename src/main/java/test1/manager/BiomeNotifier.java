package test1.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import test1.MyPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BiomeNotifier extends BukkitRunnable {

    private final Map<UUID, String> playerInBiome = new HashMap<>();
    private final Map<UUID, Map<String, Long>> suppressionCooldowns = new HashMap<>();

    private static final long COOLDOWN_DURATION_MILLIS = TimeUnit.SECONDS.toMillis(3);

    public BiomeNotifier(MyPlugin plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerBiome(player);
        }
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerBiome(player);
        }
    }

    private void checkPlayerBiome(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        UUID playerId = player.getUniqueId();
        Location loc = player.getLocation();

        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        Biome currentBiome = loc.getWorld().getBiome(loc);
        String currentBiomeKey = currentBiome.getKey().getKey().toLowerCase();
        String previousBiomeKey = playerInBiome.getOrDefault(playerId, "default");

        if (!currentBiomeKey.equals(previousBiomeKey)) {
            long currentTime = System.currentTimeMillis();

            if (!previousBiomeKey.equals("default")) {
                handleBiomeExit(player, previousBiomeKey, currentTime);
            }

            handleBiomeEntry(player, currentBiomeKey, currentTime);

            playerInBiome.put(playerId, currentBiomeKey);
        }
    }

    public void removePlayer(UUID uuid) {
        playerInBiome.remove(uuid);
        suppressionCooldowns.remove(uuid);
    }

    private void handleBiomeEntry(Player player, String biomeKey, long currentTime) {
        String eventType = biomeKey + "_enter";
        if (isSuppressed(player.getUniqueId(), eventType, currentTime)) return;

        Runnable entryAction = switch (biomeKey) {
            case "deep_dark" -> () -> {
                player.sendMessage(Component.text("Deep Dark 바이옴 진입! 어둠이 찾아옵니다.", NamedTextColor.DARK_GRAY));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));
            };
            case "desert" -> () -> player.sendMessage(Component.text("사막에 진입했습니다. 갈증이 빨라집니다.", NamedTextColor.YELLOW));
            case "swamp" -> () -> player.sendMessage(Component.text("늪지대에 진입했습니다.", NamedTextColor.DARK_GREEN));
            default -> null;
        };

        if (entryAction != null) {
            entryAction.run();
            setSuppression(player.getUniqueId(), eventType, currentTime + COOLDOWN_DURATION_MILLIS);
        }
    }

    private void handleBiomeExit(Player player, String biomeKey, long currentTime) {
        String eventType = biomeKey + "_exit";
        if (isSuppressed(player.getUniqueId(), eventType, currentTime)) return;

        Runnable exitAction = switch (biomeKey) {
            case "deep_dark" -> () -> {
                player.removePotionEffect(PotionEffectType.DARKNESS);
                player.sendMessage(Component.text("Deep Dark를 벗어났습니다.", NamedTextColor.GRAY));
            };
            case "swamp" -> () -> player.sendMessage(Component.text("늪지대를 벗어났습니다.", NamedTextColor.GREEN));
            default -> null;
        };

        if (exitAction != null) {
            exitAction.run();
            setSuppression(player.getUniqueId(), eventType, currentTime + COOLDOWN_DURATION_MILLIS);
        }
    }

    private boolean isSuppressed(UUID playerId, String eventType, long currentTime) {
        Map<String, Long> cooldowns = suppressionCooldowns.get(playerId);
        return cooldowns != null && cooldowns.getOrDefault(eventType, 0L) > currentTime;
    }

    private void setSuppression(UUID playerId, String eventType, long endTime) {
        suppressionCooldowns.computeIfAbsent(playerId, k -> new HashMap<>()).put(eventType, endTime);
    }
}