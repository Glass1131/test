package test1.listener;

import org.bukkit.scheduler.BukkitRunnable;
import test1.MyPlugin;
import test1.item.CustomItem;
import test1.party.Party;
import test1.party.PartyManager;
import test1.party.PlayerPartyGUI;
import test1.command.CustomCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;
import java.util.Random;

public class ItemEventListener implements Listener {

    private final MyPlugin plugin;
    private final Random random = new Random();

    public ItemEventListener(MyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) return;

        if (event.getAction().isRightClick()) {
            if (item.getType() == Material.BEACON && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String name = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(item.getItemMeta().displayName()));
                if (name.contains("파티")) {
                    event.setCancelled(true);
                    Party party = PartyManager.getInstance().getPartyByPlayer(player.getUniqueId());
                    if (party == null) {
                        PlayerPartyGUI.openListGUI(player);
                    } else {
                        PlayerPartyGUI.openManageGUI(player);
                    }
                    return;
                }
            }

            if (item.isSimilar(CustomItem.ZOMBIE_GOLD_NUGGET)) {
                event.setCancelled(true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
                player.sendMessage(Component.text("재생 효과를 받았습니다.", NamedTextColor.GOLD));
                item.setAmount(item.getAmount() - 1);
            } else if (item.isSimilar(CustomItem.ZOMBIE_TRACE)) {
                event.setCancelled(true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1));
                player.sendMessage(Component.text("체력을 회복했습니다.", NamedTextColor.LIGHT_PURPLE));
                item.setAmount(item.getAmount() - 1);
            } else if (item.isSimilar(CustomItem.IMMORTAL_ONE)) {
                event.setCancelled(true);
                item.setAmount(item.getAmount() - 1);
                plugin.getImmortalPlayers().add(player.getUniqueId());
                player.sendMessage(Component.text("영생의 힘이 당신을 감쌉니다. 이제 당신은 영원히 죽지 않습니다.", NamedTextColor.AQUA));

                // 기존 효과 제거 후 무한 효과 적용
                player.removePotionEffect(PotionEffectType.REGENERATION);
                player.removePotionEffect(PotionEffectType.SATURATION);
                player.removePotionEffect(PotionEffectType.STRENGTH);

                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
            }
        }
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item.isSimilar(CustomItem.ZOMBIE_APPLE)) {
            double effectiveProbability = plugin.getEffectiveProbability("zombie_apple", 0.01);
            if (random.nextDouble() < effectiveProbability) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 0));
                player.sendMessage(Component.text("으윽... 몸 상태가 좋지 않다.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        if (CustomCommand.lockedPlayers.contains(player.getUniqueId())) {
            if (item.getType() == Material.BEACON && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String name = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(item.getItemMeta().displayName()));
                if (name.contains("파티")) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("관리자에 의해 파티 아이템이 잠겨있어 버릴 수 없습니다.", NamedTextColor.RED));
                }
            }
        }
    }
}