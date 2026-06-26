package test1.manager;

import test1.item.CustomItem;
import test1.party.Party;
import test1.MyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Bogged;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.*;

public class GameManager {
    public static final String GAME_ALREADY_IN_PROGRESS = "게임이 이미 진행 중입니다.";
    public static final String NO_GAME_IN_PROGRESS = "진행 중인 게임이 없습니다.";

    private final MyPlugin plugin;
    private final GameScoreboard gameScoreboard;
    private final MobSpawner mobSpawner;

    private boolean gameInProgress = false;
    private int currentRound = 1;

    private final Set<LivingEntity> gameMonsters = new HashSet<>();
    private final Set<UUID> survivors = new HashSet<>();
    private final Set<UUID> zombiePlayers = new HashSet<>();

    private Party currentParty = null;

    private int preparationTimeSeconds;
    private long roundEndDelayTicks;
    private long zombieCountUpdateIntervalTicks;
    private int healthIncreaseAmount;
    private int healthIncreasePerXRounds;

    private final Random random = new Random();

    private BukkitTask preparationTask = null;
    private BukkitTask roundEndTask = null;
    private BukkitTask entityCountUpdateTask = null;

    public GameManager(MyPlugin plugin, GameScoreboard gameScoreboard) {
        this.plugin = plugin;
        this.gameScoreboard = gameScoreboard;
        this.mobSpawner = new MobSpawner(plugin, this);
        reloadConfig();
    }

    public void reloadConfig() {
        this.preparationTimeSeconds = plugin.getConfig().getInt("round.preparation-time-seconds", 30);
        this.roundEndDelayTicks = plugin.getConfig().getLong("round.end-delay-ticks", 40L);
        this.zombieCountUpdateIntervalTicks = plugin.getConfig().getLong("intervals.zombie-count-update-ticks", 20L);
        this.healthIncreaseAmount = plugin.getConfig().getInt("spawn.health-increase.per-10-rounds", 3);
        this.healthIncreasePerXRounds = plugin.getConfig().getInt("spawn.health-increase.every-x-rounds", 10);

        if (this.mobSpawner != null) {
            this.mobSpawner.reloadConfig();
        }
    }

    //외부에서 특정 보스 소환을 요청할 때 사용
    public void spawnBoss(Location location, String type, int round) {
        if (mobSpawner != null) {
            mobSpawner.spawnSpecificBoss(location, type, round);
        }
    }

    public void startGame(Party party, int startRound) {
        if (gameInProgress) {
            plugin.getLogger().warning(GAME_ALREADY_IN_PROGRESS);
            return;
        }

        survivors.clear();
        zombiePlayers.clear();

        if (party != null) {
            this.currentParty = party;
            for (UUID uuid : party.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    survivors.add(uuid);
                    double maxHealth = 20.0;
                    AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
                    if (attr != null) {
                        maxHealth = attr.getValue();
                    }
                    p.setHealth(maxHealth);
                    p.setFoodLevel(20);
                }
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                survivors.add(p.getUniqueId());
            }
        }

        if (survivors.isEmpty()) {
            plugin.getLogger().warning("참가자가 없어 게임을 시작할 수 없습니다.");
            return;
        }

        gameInProgress = true;
        currentRound = startRound;
        gameMonsters.clear();

        if (plugin.getOxygenSystem() != null) {
            plugin.getOxygenSystem().setEnabled(false);
        }

        if (gameScoreboard != null) {
            gameScoreboard.applyToAllPlayers();
            gameScoreboard.updateScore("라운드", currentRound);
        }

        String partyInfo = (party != null) ? "Party " + party.getId() : "All Players";
        plugin.getLogger().info("Game started at round " + currentRound + " for " + partyInfo);

        startPreparationPhase();
    }

    public void startGame(int startRound) {
        startGame(null, startRound);
    }

    public void startGame() {
        startGame(null, 1);
    }

    public void stopGame() {
        if (!gameInProgress) return;

        gameInProgress = false;

        cancelAllGameTasks();
        removeGameEntities();
        cleanupGameDrops();
        resetPlayerStates();

        survivors.clear();
        zombiePlayers.clear();
        currentParty = null;

        if (plugin.getOxygenSystem() != null) {
            plugin.getOxygenSystem().setEnabled(false);
        }

        World world = Bukkit.getWorld(MyPlugin.GAME_WORLD_NAME);
        if (world != null) {
            world.setStorm(false);
            world.setThundering(false);
        }

        plugin.getLogger().info("Game stopped!");
    }

    public void handlePlayerDeath(Player player) {
        if (!gameInProgress || !survivors.contains(player.getUniqueId())) return;

        survivors.remove(player.getUniqueId());

        if (!survivors.isEmpty()) {
            zombiePlayers.add(player.getUniqueId());

            Title title = Title.title(
                    Component.text("감염되었습니다!", NamedTextColor.DARK_GREEN),
                    Component.text("생존자를 공격하세요.", NamedTextColor.GREEN),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))
            );
            player.showTitle(title);

            player.playerListName(Component.text(player.getName(), NamedTextColor.GREEN));

            Bukkit.broadcast(Component.text("🧟 " + player.getName() + "님이 감염되었습니다! 생존자들을 공격합니다!", NamedTextColor.RED));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.spigot().respawn();
                    player.getInventory().setHelmet(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ZOMBIE_HEAD));
                    player.sendMessage(Component.text("당신은 좀비가 되었습니다! 생존자를 공격하세요.", NamedTextColor.DARK_RED));
                }
            }, 10L);
        }
        else {
            Bukkit.broadcast(Component.text("❌ 모든 생존자가 전멸했습니다! 게임 종료.", NamedTextColor.RED));
            stopGame();
        }
    }

    public boolean isZombiePlayer(Player player) {
        return zombiePlayers.contains(player.getUniqueId());
    }

    private void cleanupGameDrops() {
        World world = Bukkit.getWorld(MyPlugin.GAME_WORLD_NAME);
        if (world == null) return;

        List<org.bukkit.inventory.ItemStack> customItems = Arrays.asList(
                CustomItem.ZOMBIE_POWER, CustomItem.D_SWORD, CustomItem.D_HELMET,
                CustomItem.D_CHESTPLATE, CustomItem.D_LEGGINGS, CustomItem.D_BOOTS,
                CustomItem.DARK_CORE, CustomItem.DARK_WEAPON, CustomItem.SCULK,
                CustomItem.SCULK_VEIN, CustomItem.SCULK_SENSOR, CustomItem.SILENCE_TEMPLATE,
                CustomItem.AMETHYST_SHARD, CustomItem.CALIBRATED_SCULK_SENSOR,
                CustomItem.STICKY_SLIME, CustomItem.OXYGEN_FILTER
        );

        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity instanceof org.bukkit.entity.AbstractArrow) {
                entity.remove();
            }
            else if (entity instanceof Item itemEntity) {
                org.bukkit.inventory.ItemStack stack = itemEntity.getItemStack();
                for (org.bukkit.inventory.ItemStack custom : customItems) {
                    if (custom != null && stack.isSimilar(custom)) {
                        itemEntity.remove();
                        break;
                    }
                }
            }
        }
    }

    private void cancelAllGameTasks() {
        if (preparationTask != null && !preparationTask.isCancelled()) {
            preparationTask.cancel();
            preparationTask = null;
        }
        if (roundEndTask != null && !roundEndTask.isCancelled()) {
            roundEndTask.cancel();
            roundEndTask = null;
        }
        if (mobSpawner != null) {
            mobSpawner.stopSpawning();
        }
        if (entityCountUpdateTask != null && !entityCountUpdateTask.isCancelled()) {
            entityCountUpdateTask.cancel();
            entityCountUpdateTask = null;
        }
    }

    private void resetPlayerStates() {
        Scoreboard emptyScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (gameScoreboard != null && p.getScoreboard().equals(gameScoreboard.getBoard())) {
                p.setScoreboard(emptyScoreboard);
                p.getInventory().clear();
                p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
                p.playerListName(null);
            }
        }
    }

    private void startPreparationPhase() {
        if (preparationTask != null && !preparationTask.isCancelled()) {
            preparationTask.cancel();
        }
        preparationTask = new BukkitRunnable() {
            int timeLeft = preparationTimeSeconds;
            @Override
            public void run() {
                if (!gameInProgress) {
                    cancel();
                    preparationTask = null;
                    return;
                }
                if (timeLeft >= 1) {
                    if (gameScoreboard != null) {
                        gameScoreboard.updateScore("준비 시간", timeLeft);
                    }
                    timeLeft--;
                } else {
                    cancel();
                    preparationTask = null;
                    startGameRound();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void skipPreparation() {
        if (preparationTask != null && !preparationTask.isCancelled()) {
            preparationTask.cancel();
            preparationTask = null;
            plugin.getLogger().info("Skipping preparation phase...");

            if (gameInProgress) {
                if (gameScoreboard != null) {
                    gameScoreboard.updateScore("준비 시간", 0);
                }
                startGameRound();
            }
        }
    }

    private void startGameRound() {
        if (!gameInProgress) return;

        if (gameScoreboard != null) {
            gameScoreboard.updateScore("라운드", currentRound);
            gameScoreboard.updateScore("준비 시간", 0);
        }

        if (currentRound % 3 == 0) {
            World world = Bukkit.getWorld(MyPlugin.GAME_WORLD_NAME);
            if (world != null) {
                int weatherType = random.nextInt(3);
                switch (weatherType) {
                    case 0:
                        world.setStorm(false);
                        world.setThundering(false);
                        Bukkit.broadcast(Component.text("☀ 날씨가 맑아집니다.", NamedTextColor.YELLOW));
                        break;
                    case 1:
                        world.setStorm(true);
                        world.setThundering(false);
                        Bukkit.broadcast(Component.text("🌧 비(또는 눈)가 내리기 시작합니다.", NamedTextColor.BLUE));
                        break;
                    case 2:
                        world.setStorm(true);
                        world.setThundering(true);
                        Bukkit.broadcast(Component.text("⚡ 폭풍이 몰아칩니다!", NamedTextColor.DARK_PURPLE));
                        break;
                }
            }
        }

        Bukkit.broadcast(Component.text("🧟 게임 시작! 라운드 " + currentRound).color(NamedTextColor.GREEN));

        int oxygenStartRound = plugin.getConfig().getInt("biomes.swamp.oxygen-start-round", 5);
        if (currentRound == oxygenStartRound && plugin.getOxygenSystem() != null && !plugin.getOxygenSystem().isEnabled()) {
            plugin.getOxygenSystem().setEnabled(true);
            Bukkit.broadcast(Component.text("☣ 경고: 늪지대에 오염된 가스가 퍼지기 시작했습니다!", NamedTextColor.RED));
            Bukkit.broadcast(Component.text("   산소 공급 구역으로 대피하세요!", NamedTextColor.YELLOW));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
            }
        }

        int extraHealth = 0;
        if (healthIncreasePerXRounds > 0) {
            extraHealth = (currentRound / healthIncreasePerXRounds) * healthIncreaseAmount;
        }

        gameMonsters.clear();

        int bossInterval = plugin.getConfig().getInt("biomes.swamp.boss-round-interval", 10);
        boolean isBossRound = (currentRound % bossInterval == 0);

        if (mobSpawner != null) {
            mobSpawner.startSpawning(currentRound, extraHealth, isBossRound);
        }

        startEntityCountUpdateTask();
    }

    private void endRound() {
        if (roundEndTask != null && !roundEndTask.isCancelled()) {
            roundEndTask.cancel();
        }
        roundEndTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!gameInProgress) {
                roundEndTask = null;
                return;
            }
            Bukkit.broadcast(Component.text("라운드 " + currentRound + " 종료! 다음 라운드를 준비하세요.").color(NamedTextColor.YELLOW));
            currentRound++;
            roundEndTask = null;
            startPreparationPhase();
        }, this.roundEndDelayTicks);
    }

    private void startEntityCountUpdateTask() {
        if (entityCountUpdateTask != null && !entityCountUpdateTask.isCancelled()) {
            entityCountUpdateTask.cancel();
        }
        entityCountUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameInProgress) {
                    cancel();
                    entityCountUpdateTask = null;
                    return;
                }

                World world = Bukkit.getWorld(MyPlugin.GAME_WORLD_NAME);
                boolean isStorming = world != null && world.hasStorm();

                Iterator<LivingEntity> iterator = gameMonsters.iterator();
                int aliveMonsterCount = 0;
                while (iterator.hasNext()) {
                    LivingEntity monster = iterator.next();
                    if (monster.isDead() || !monster.isValid()) {
                        iterator.remove();
                    } else {
                        aliveMonsterCount++;

                        if (monster instanceof Bogged && isStorming) {
                            double temp = monster.getLocation().getBlock().getTemperature();
                            if (temp < 0.15) {
                                monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                            } else {
                                monster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
                            }
                        }

                        if (monster instanceof Creature creature) {
                            updateMonsterTarget(creature);
                        }
                    }
                }

                if (gameScoreboard != null) {
                    gameScoreboard.updateScore("남은 몹", aliveMonsterCount);
                }

                if (aliveMonsterCount == 0 && gameInProgress) {
                    cancel();
                    entityCountUpdateTask = null;
                    endRound();
                }
            }
        }.runTaskTimer(plugin, 0L, this.zombieCountUpdateIntervalTicks);
    }

    private void updateMonsterTarget(Creature creature) {
        LivingEntity currentTarget = creature.getTarget();

        if (currentTarget instanceof Player playerTarget) {
            boolean isInvalidTarget = zombiePlayers.contains(playerTarget.getUniqueId())
                    || playerTarget.isDead()
                    || playerTarget.getGameMode() == GameMode.SPECTATOR;

            if (!isInvalidTarget) {
                return;
            }
        }

        Player nearestPlayer = getNearestPlayer(creature);
        creature.setTarget(nearestPlayer);
    }

    private Player getNearestPlayer(LivingEntity entity) {
        Player closestPlayer = null;
        double closestDistanceSq = Double.MAX_VALUE;
        Location entityLocation = entity.getLocation();

        for (UUID uuid : survivors) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !player.isDead() && player.getWorld().equals(entityLocation.getWorld())
                    && player.getGameMode() != GameMode.SPECTATOR) {
                double distanceSq = player.getLocation().distanceSquared(entityLocation);
                if (distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closestPlayer = player;
                }
            }
        }
        return closestPlayer;
    }

    public void addGameMonster(LivingEntity monster) {
        if (monster == null || !monster.isValid()) return;
        gameMonsters.add(monster);
        monster.setRemoveWhenFarAway(false);
        monster.setPersistent(true);
    }

    public void removeGameMonster(LivingEntity monster) {
        if (monster == null) return;
        gameMonsters.remove(monster);
    }

    public void removeGameEntities() {
        for (LivingEntity monster : new HashSet<>(gameMonsters)) {
            if (monster != null && monster.isValid()) {
                monster.remove();
            }
        }
        gameMonsters.clear();
        plugin.getLogger().info("All game monsters removed by GameManager.");
    }

    public boolean isGameInProgress() {
        return gameInProgress;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public Set<LivingEntity> getGameMonsters() {
        return Collections.unmodifiableSet(gameMonsters);
    }
}