package test1.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import test1.MyPlugin;
import test1.command.CustomCommand;
import test1.item.CustomItem;

import java.util.*;

public class Weaponability implements Listener {

    private final MyPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> damageLock = new HashSet<>();
    private final Map<UUID, BukkitTask> strengthRestoreTasks = new HashMap<>();
    private final Map<UUID, Integer> strengthBonusLevel = new HashMap<>();


    private static final Component DARK_WEAPON_NAME = Component.text("비명들의 힘", NamedTextColor.DARK_AQUA);
    private static final double SPECIAL_ATTACK_PROBABILITY = 0.035;
    private static final long SPECIAL_ATTACK_COOLDOWN_MS = 1000;
    private static final int SONIC_WAVE_RANGE = 5;
    private static final double SONIC_WAVE_DAMAGE = 9;
    private static final double SPECIAL_ATTACK_DAMAGE = 20;
    private static final int MAX_EFFECT_LEVEL = 4; // 5단계 (0-4)

    public Weaponability(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDummyDamage(EntityDamageEvent event) {
        if (CustomCommand.testDummies.contains(event.getEntity())) {
            // event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity && CustomCommand.testDummies.contains((LivingEntity) event.getEntity())) {
            if (event.getDamager() instanceof Player attacker) {
                double damage = event.getFinalDamage();
                attacker.sendActionBar(Component.text(String.format("§c❤ §f피해량: §e%.2f", damage), NamedTextColor.WHITE));
            }
            return;
        }

        if (!(event.getDamager() instanceof Player player)) return;
        if (damageLock.contains(player.getUniqueId())) return;

        if (isDarkWeapon(player.getInventory().getItemInMainHand())) {
            if (!(event.getEntity() instanceof LivingEntity)) return;
            handleDarkWeapon(player);
        }

        if (event.getEntityType().toString().contains("ZOMBIE")) {
            handleImmortalOne(player);
        }
    }

    private void handleDarkWeapon(Player player) {
        damageLock.add(player.getUniqueId());
        try {
            createSonicWave(player);
            double effectiveProbability = plugin.getEffectiveProbability("dark_weapon", SPECIAL_ATTACK_PROBABILITY);
            if (random.nextDouble() < effectiveProbability && canUseSpecialAttack(player)) {
                performSpecialAttack(player);
                startCooldown(player);
            }
        } finally {
            damageLock.remove(player.getUniqueId());
        }
    }

    private void handleImmortalOne(Player player) {
        if (!plugin.getImmortalPlayers().contains(player.getUniqueId())) {
            return;
        }

        double effectiveProbability = plugin.getEffectiveProbability("immortal_one", 0.1);
        if (random.nextDouble() < effectiveProbability) {
            List<PotionEffect> effects = List.of(
                    new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1),
                    new PotionEffect(PotionEffectType.STRENGTH, 100, 0),
                    new PotionEffect(PotionEffectType.RESISTANCE, 100, 0),
                    new PotionEffect(PotionEffectType.SPEED, 100, 0)
            );
            PotionEffect randomEffect = effects.get(random.nextInt(effects.size()));

            player.getWorld().getNearbyPlayers(player.getLocation(), 3).forEach(p -> {
                int currentBonus = strengthBonusLevel.getOrDefault(p.getUniqueId(), 0);
                int newAmplifier = randomEffect.getAmplifier();

                if (!randomEffect.getType().equals(PotionEffectType.INSTANT_HEALTH)) {
                    if (currentBonus < MAX_EFFECT_LEVEL) {
                        currentBonus++;
                    }
                    newAmplifier = currentBonus;
                    strengthBonusLevel.put(p.getUniqueId(), currentBonus);
                }


                PotionEffect newEffect = new PotionEffect(randomEffect.getType(), randomEffect.getDuration(), newAmplifier);
                p.addPotionEffect(newEffect);

                if (plugin.getImmortalPlayers().contains(p.getUniqueId()) && newEffect.getType().equals(PotionEffectType.STRENGTH)) {
                    if (strengthRestoreTasks.containsKey(p.getUniqueId())) {
                        strengthRestoreTasks.get(p.getUniqueId()).cancel();
                    }
                    BukkitTask restoreTask = new BukkitRunnable() {
                        @Override
                        public void run() {
                            p.removePotionEffect(PotionEffectType.STRENGTH);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
                            strengthRestoreTasks.remove(p.getUniqueId());
                            strengthBonusLevel.remove(p.getUniqueId());
                        }
                    }.runTaskLater(plugin, newEffect.getDuration());
                    strengthRestoreTasks.put(p.getUniqueId(), restoreTask);
                }

                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 5, 0.5, 0.5, 0.5);
                p.sendMessage(Component.text(player.getName() + "님의 영생의 힘이 당신을 축복합니다!", NamedTextColor.GREEN));
            });
        }
    }

    private boolean isDarkWeapon(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && DARK_WEAPON_NAME.equals(meta.displayName());
    }

    private void createSonicWave(Player player) {
        World world = player.getWorld();
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize();

        Location particleLoc = startLoc.clone();
        Vector step = direction.clone().multiply(0.5);
        for (int i = 0; i < SONIC_WAVE_RANGE * 2; i++) {
            particleLoc.add(step);
            world.spawnParticle(Particle.SONIC_BOOM, particleLoc, 1, 0, 0, 0, 0);
        }
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);

        for (Entity entity : world.getNearbyEntities(startLoc, SONIC_WAVE_RANGE, SONIC_WAVE_RANGE, SONIC_WAVE_RANGE)) {
            if (entity instanceof LivingEntity target && !entity.equals(player) && !CustomCommand.testDummies.contains(target)) {
                Location targetLoc = target.getLocation().add(0, target.getHeight() / 2.0, 0);
                Vector toTarget = targetLoc.toVector().subtract(startLoc.toVector());

                if (toTarget.lengthSquared() > SONIC_WAVE_RANGE * SONIC_WAVE_RANGE) continue;

                double dot = toTarget.dot(direction);
                if (dot < 0) continue;

                Vector closestPoint = direction.clone().multiply(dot);
                if (toTarget.distance(closestPoint) <= 1.2) {
                    target.damage(SONIC_WAVE_DAMAGE, player);
                }
            }
        }
    }

    private void performSpecialAttack(Player player) {
        World world = player.getWorld();
        player.sendMessage(Component.text("❗ 특수 공격 발동!", NamedTextColor.AQUA));
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        double[] angles = {72, 144, 216, 288, 360};

        for (double angle : angles) {
            double rad = Math.toRadians(angle);
            Vector dir = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize();

            Location pLoc = player.getEyeLocation().add(dir.clone().multiply(2));
            Vector pStep = dir.clone();

            for(int i=0; i<5; i++) {
                pLoc.add(pStep);
                pLoc.setY(player.getLocation().getY() + 1);
                world.spawnParticle(Particle.SONIC_BOOM, pLoc, 5, 0.2, 0.2, 0.2, 0.1);
            }
        }

        double searchRadius = 7.0;
        for (Entity entity : world.getNearbyEntities(player.getLocation(), searchRadius, 3, searchRadius)) {
            if (entity instanceof LivingEntity target && !entity.equals(player) && !CustomCommand.testDummies.contains(target)) {
                Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector());
                toTarget.setY(0);
                double dist = toTarget.length();

                if (dist > 7 || dist < 2) continue;

                target.damage(SPECIAL_ATTACK_DAMAGE, player);
            }
        }
    }

    private boolean canUseSpecialAttack(Player player) {
        long now = System.currentTimeMillis();
        return !cooldowns.containsKey(player.getUniqueId()) || (now - cooldowns.get(player.getUniqueId()) >= SPECIAL_ATTACK_COOLDOWN_MS);
    }

    private void startCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
}