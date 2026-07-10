package test1.system;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import test1.item.CustomItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class OxygenSystem {

    private final JavaPlugin plugin;
    private boolean isEnabled = false;

    private final Map<UUID, Integer> oxygenLevels = new HashMap<>();
    private final Map<Integer, Location> oxygenSupplyLocations = new HashMap<>();
    private int nextId = 1;

    private static final int MAX_OXYGEN = 100;
    private static final int SUPPLY_RADIUS = 5;

    private final Random random = new Random();

    public OxygenSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        startOxygenTask();
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            oxygenLevels.clear();
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public int addSupplyLocation(Location loc) {
        Location centered = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5);
        int id = nextId++;
        oxygenSupplyLocations.put(id, centered);
        return id;
    }

    public boolean removeSupplyLocation(int id) {
        return oxygenSupplyLocations.remove(id) != null;
    }

    public void clearSupplyLocations() {
        oxygenSupplyLocations.clear();
    }

    public Map<Integer, Location> getSupplyLocations() {
        return new HashMap<>(oxygenSupplyLocations);
    }

    public void setOxygenLevel(Player player, int level) {
        if (!isEnabled) return; // 시스템이 비활성화된 경우 설정하지 않음
        oxygenLevels.put(player.getUniqueId(), Math.clamp(level, 0, MAX_OXYGEN));
    }

    public int getOxygenLevel(Player player) {
        if (!isEnabled) return MAX_OXYGEN; // 시스템이 비활성화된 경우 항상 최대 산소량 반환
        return oxygenLevels.getOrDefault(player.getUniqueId(), MAX_OXYGEN);
    }

    private void startOxygenTask() {
        new BukkitRunnable() {
            int tickCounter = 0;

            @Override
            public void run() {
                if (!isEnabled) return;

                if (tickCounter % 20 == 0) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) continue;
                        processOxygenLogic(player);
                    }
                }

                if (tickCounter % 10 == 0) {
                    showSupplyAreaParticles();
                }

                tickCounter++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void processOxygenLogic(Player player) {
        boolean isSafe = isNearOxygenSupply(player);
        UUID uid = player.getUniqueId();
        int currentOxygen = getOxygenLevel(player);

        if (isSafe) {
            if (currentOxygen < MAX_OXYGEN) {
                currentOxygen = Math.min(MAX_OXYGEN, currentOxygen + 5);
                player.sendActionBar(Component.text("💨 산소가 공급되고 있습니다.", NamedTextColor.AQUA));
            }
        } else {
            if (currentOxygen > 0) {
                // 산소 필터 효과: 50% 확률로 산소 감소 방지 (0.5배율)
                boolean hasFilter = player.getInventory().containsAtLeast(CustomItem.OXYGEN_FILTER, 1);
                if (!hasFilter || random.nextBoolean()) {
                    currentOxygen--;
                }
            } else {
                player.damage(1.0);
                player.sendMessage(Component.text("질식할 것 같습니다! 산소가 필요합니다!", NamedTextColor.RED));
            }
        }
        oxygenLevels.put(uid, currentOxygen);
    }

    private boolean isNearOxygenSupply(Player player) {
        if (oxygenSupplyLocations.isEmpty()) return false;
        Location pLoc = player.getLocation();

        for (Location supplyLoc : oxygenSupplyLocations.values()) {
            if (supplyLoc.getWorld().equals(pLoc.getWorld())) {
                if (supplyLoc.distanceSquared(pLoc) <= SUPPLY_RADIUS * SUPPLY_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showSupplyAreaParticles() {
        for (Location loc : oxygenSupplyLocations.values()) {
            if (loc.getWorld() == null) continue;
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 5, 0.5, 0.5, 0.5, 0.05);
        }
    }
}