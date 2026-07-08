package test1.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class AdminPartyGUI implements Listener {

    public static void openListGUI(Player admin) {
        Component title = Component.text("👑 관리자 파티 목록").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD);
        Inventory inv = Bukkit.createInventory(null, 54, title);
        Plugin plugin = Bukkit.getPluginManager().getPlugin("test");

        Collection<Party> parties = PartyManager.getInstance().getParties();
        int slot = 0;

        for (Party party : parties) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null && plugin != null) {
                OfflinePlayer leader = Bukkit.getOfflinePlayer(party.getAdmin());
                meta.setOwningPlayer(leader);
                String leaderName = leader.getName() != null ? leader.getName() : "알 수 없음";

                meta.displayName(Component.text(leaderName + "의 파티").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

                // 💡 [핵심 해결] 파티장의 UUID를 NBT 태그에 암호화하여 저장
                NamespacedKey key = new NamespacedKey(plugin, "admin_party_leader");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, party.getAdmin().toString());

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§7인원수: " + party.getMembers().size() + "명"));
                lore.add(Component.text("§e클릭하여 파티 관리"));
                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            inv.setItem(slot, skull);
            slot++;
            if (slot >= 54) break;
        }

        admin.openInventory(inv);
    }

    public static void openManageGUI(Player admin, UUID leaderUuid) {
        Component title = Component.text("👑 파티 관리 창").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD);
        Inventory inv = Bukkit.createInventory(null, 54, title);
        Plugin plugin = Bukkit.getPluginManager().getPlugin("test");

        Party party = PartyManager.getInstance().getParty(leaderUuid);
        if (party == null) {
            admin.sendMessage("§c존재하지 않는 파티입니다.");
            return;
        }

        int slot = 0;
        for (UUID memberUuid : party.getMembers()) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null && plugin != null) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
                meta.setOwningPlayer(member);

                boolean isLeader = party.isAdmin(memberUuid);
                String role = isLeader ? "§c[방장] " : "§7[파티원] ";
                String memberName = member.getName() != null ? member.getName() : "알 수 없음";
                meta.displayName(Component.text(role + memberName).decorate(TextDecoration.BOLD));

                NamespacedKey leaderKey = new NamespacedKey(plugin, "manage_leader");
                meta.getPersistentDataContainer().set(leaderKey, PersistentDataType.STRING, leaderUuid.toString());

                NamespacedKey memberKey = new NamespacedKey(plugin, "manage_target");
                meta.getPersistentDataContainer().set(memberKey, PersistentDataType.STRING, memberUuid.toString());

                List<Component> lore = new ArrayList<>();
                if (!isLeader) {
                    lore.add(Component.text("§e좌클릭: §f파티장 위임"));
                    lore.add(Component.text("§c우클릭: §f강퇴(추방)"));
                } else {
                    lore.add(Component.text("§c우클릭: §f파티 강제 해체"));
                }
                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            inv.setItem(slot, skull);
            slot++;
            if (slot >= 54) break;
        }

        admin.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.contains("관리자 파티 목록") || title.contains("파티 관리 창")) {
            event.setCancelled(true);

            if (event.getClickedInventory() == null || event.getView().getBottomInventory().equals(event.getClickedInventory())) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Plugin plugin = Bukkit.getPluginManager().getPlugin("test");
            if (plugin == null) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                PartyManager pm = PartyManager.getInstance();

                if (title.contains("관리자 파티 목록")) {
                    if (clickedItem.getType() == Material.PLAYER_HEAD && clickedItem.hasItemMeta()) {
                        NamespacedKey key = new NamespacedKey(plugin, "admin_party_leader");
                        if (clickedItem.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                            String uuidStr = clickedItem.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                            try {
                                UUID leaderUuid = UUID.fromString(Objects.requireNonNull(uuidStr));
                                openManageGUI(player, leaderUuid);
                            } catch (Exception e) {
                                player.sendMessage("§c데이터를 읽을 수 없습니다.");
                            }
                        }
                    }
                }
                else if (title.contains("파티 관리 창")) {
                    if (clickedItem.getType() == Material.PLAYER_HEAD && clickedItem.hasItemMeta()) {
                        NamespacedKey leaderKey = new NamespacedKey(plugin, "manage_leader");
                        NamespacedKey targetKey = new NamespacedKey(plugin, "manage_target");

                        var pdc = clickedItem.getItemMeta().getPersistentDataContainer();
                        if (pdc.has(leaderKey, PersistentDataType.STRING) && pdc.has(targetKey, PersistentDataType.STRING)) {
                            try {
                                UUID leaderUuid = UUID.fromString(Objects.requireNonNull(pdc.get(leaderKey, PersistentDataType.STRING)));
                                UUID targetUuid = UUID.fromString(Objects.requireNonNull(pdc.get(targetKey, PersistentDataType.STRING)));

                                Party party = pm.getParty(leaderUuid);
                                if (party == null) {
                                    player.sendMessage("§c파티가 더 이상 존재하지 않습니다.");
                                    player.closeInventory();
                                    return;
                                }

                                boolean isTargetLeader = party.isAdmin(targetUuid);

                                // 💡 [로직 처리] 좌클릭 = 위임, 우클릭 = 강퇴/해체
                                if (event.isLeftClick()) {
                                    if (!isTargetLeader) {
                                        pm.delegateAdmin(leaderUuid, targetUuid);
                                        player.sendMessage("§a성공적으로 파티장을 위임했습니다.");
                                        openManageGUI(player, targetUuid); // 새로운 방장 UUID로 창 갱신
                                    }
                                } else if (event.isRightClick()) {
                                    if (isTargetLeader) {
                                        pm.disbandParty(leaderUuid);
                                        player.sendMessage("§c파티를 강제 해체했습니다.");
                                        player.closeInventory();
                                    } else {
                                        pm.kickMember(leaderUuid, targetUuid);
                                        player.sendMessage("§e해당 플레이어를 강퇴했습니다.");
                                        openManageGUI(player, leaderUuid); // 창 갱신
                                    }
                                }

                            } catch (Exception e) {
                                player.sendMessage("§c오류가 발생했습니다.");
                            }
                        }
                    }
                }
            });
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.contains("관리자 파티 목록") || title.contains("파티 관리 창")) {
            event.setCancelled(true);
        }
    }
}