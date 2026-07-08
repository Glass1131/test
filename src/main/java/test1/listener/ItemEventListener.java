package test1.listener;

import test1.MyPlugin;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public class ItemEventListener implements Listener {

    public ItemEventListener(MyPlugin plugin) {
    }

    // [신규 로직] 파티 신호기 우클릭 감지
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) return;

        if (event.getAction().isRightClick() && item.getType() == Material.BEACON) {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String name = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(item.getItemMeta().displayName()));

                if (name.contains("파티")) {
                    event.setCancelled(true); // 블록 설치 방어

                    Party party = PartyManager.getInstance().getPartyByPlayer(player.getUniqueId());
                    if (party == null) {
                        PlayerPartyGUI.openListGUI(player);
                    } else {
                        PlayerPartyGUI.openManageGUI(player);
                    }
                }
            }
        }
    }

    // [신규 로직] 잠긴 유저가 파티 아이템을 버리는 행위(Q키) 완벽 차단
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        // CustomCommand에 정의해둔 lockedPlayers에 속해있는지 검사
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