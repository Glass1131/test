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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerPartyGUI implements Listener {

    // 사용되지 않으므로 제거
    // private static final Material[] CONCRETE_COLORS = { ... };

    public static void openListGUI(Player player) {
        Component title = Component.text("👥 파티 목록").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD);
        Inventory inv = Bukkit.createInventory(null, 54, title);
        Plugin plugin = Bukkit.getPluginManager().getPlugin("test");

        ItemStack createBtn = new ItemStack(Material.BLUE_CONCRETE);
        ItemMeta createMeta = createBtn.getItemMeta();
        if (createMeta != null) {
            createMeta.displayName(Component.text("파티 생성 버튼").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            createBtn.setItemMeta(createMeta);
        }
        inv.setItem(45, createBtn);

        int slot = 10;
        Collection<Party> parties = PartyManager.getInstance().getParties();

        for (Party party : parties) {
            ItemStack partyIcon = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) partyIcon.getItemMeta();

            if (skullMeta != null && plugin != null) {
                OfflinePlayer adminPlayer = Bukkit.getOfflinePlayer(party.getAdmin());
                skullMeta.setOwningPlayer(adminPlayer);
                String adminName = adminPlayer.getName() != null ? adminPlayer.getName() : "알 수 없음";

                skullMeta.displayName(Component.text(adminName + "의 파티").color(NamedTextColor.GOLD));

                NamespacedKey key = new NamespacedKey(plugin, "party_leader");
                skullMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, party.getAdmin().toString());

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§7방장: " + adminName));
                lore.add(Component.text("§7현재 인원: " + party.getMembers().size() + "명"));
                lore.add(Component.text("§a클릭하여 참가!"));
                skullMeta.lore(lore);
                partyIcon.setItemMeta(skullMeta);
            }

            inv.setItem(slot, partyIcon);

            slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot > 44) break;
        }

        player.openInventory(inv);
    }

    public static void openManageGUI(Player player) {
        Component title = Component.text("⚙ 파티 관리 창").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD);
        Inventory inv = Bukkit.createInventory(null, 54, title);
        Plugin plugin = Bukkit.getPluginManager().getPlugin("test");
        Party party = PartyManager.getInstance().getPartyByPlayer(player.getUniqueId());

        if (party == null) return;

        boolean isPlayerAdmin = party.isAdmin(player.getUniqueId());

        List<UUID> sortedMembers = new ArrayList<>();
        UUID adminUuid = party.getAdmin();
        sortedMembers.add(adminUuid);
        for (UUID memberUuid : party.getMembers()) {
            if (!memberUuid.equals(adminUuid)) {
                sortedMembers.add(memberUuid);
            }
        }

        int slot = 10;
        for (UUID memberUuid : sortedMembers) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null && plugin != null) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
                meta.setOwningPlayer(member);

                boolean isThisMemberAdmin = party.isAdmin(memberUuid);
                String role = isThisMemberAdmin ? "§c[방장] " : "§7[파티원] ";
                String memberName = member.getName() != null ? member.getName() : "알 수 없음";
                meta.displayName(Component.text(role + memberName));

                NamespacedKey key = new NamespacedKey(plugin, "party_member_uuid");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, memberUuid.toString());

                List<Component> lore = new ArrayList<>();
                if (isPlayerAdmin && !isThisMemberAdmin) {
                    lore.add(Component.text("§e좌클릭: 방장 위임"));
                    lore.add(Component.text("§c우클릭: 추방"));
                }
                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            inv.setItem(slot, skull);

            slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot > 44) break;
        }

        ItemStack leaveBtn = new ItemStack(Material.GRAY_CONCRETE);
        ItemMeta leaveMeta = leaveBtn.getItemMeta();
        if (leaveMeta != null) {
            leaveMeta.displayName(Component.text("Leave").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            leaveBtn.setItemMeta(leaveMeta);
        }
        inv.setItem(46, leaveBtn);

        ItemStack disbandBtn = new ItemStack(Material.GRAY_CONCRETE);
        ItemMeta disbandMeta = disbandBtn.getItemMeta();
        if (disbandMeta != null) {
            disbandMeta.displayName(Component.text("Disband").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
            disbandBtn.setItemMeta(disbandMeta);
        }
        inv.setItem(49, disbandBtn);

        ItemStack startBtn = new ItemStack(Material.GRAY_CONCRETE);
        ItemMeta startMeta = startBtn.getItemMeta();
        if (startMeta != null) {
            startMeta.displayName(Component.text("Start").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            startBtn.setItemMeta(startMeta);
        }
        inv.setItem(52, startBtn);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.contains("파티 목록") || title.contains("파티 관리 창")) {
            event.setCancelled(true);

            if (event.getClickedInventory() == null || event.getView().getBottomInventory().equals(event.getClickedInventory())) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getRawSlot();

            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Plugin plugin = Bukkit.getPluginManager().getPlugin("test");
            if (plugin == null) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                PartyManager pm = PartyManager.getInstance();

                if (title.contains("파티 목록")) {
                    if (slot == 45) {
                        if (pm.getPartyByPlayer(player.getUniqueId()) != null) {
                            player.sendMessage("§c이미 파티에 속해 있습니다.");
                            return;
                        }
                        pm.createParty(player);
                        player.sendMessage("§b성공적으로 파티를 생성했습니다!");
                        openManageGUI(player);
                    } else if (clickedItem.getType() == Material.PLAYER_HEAD) {
                        if (pm.getPartyByPlayer(player.getUniqueId()) != null) {
                            player.sendMessage("§c이미 다른 파티에 속해 있습니다.");
                            return;
                        }
                        if (clickedItem.hasItemMeta()) {
                            NamespacedKey key = new NamespacedKey(plugin, "party_leader");
                            if (clickedItem.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                                String leaderUuidStr = clickedItem.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                                try {
                                    UUID leaderUuid = UUID.fromString(Objects.requireNonNull(leaderUuidStr));
                                    pm.joinParty(player, leaderUuid);
                                    openManageGUI(player);
                                } catch (Exception e) {
                                    player.sendMessage("§c파티 정보를 불러올 수 없습니다.");
                                }
                            }
                        }
                    }
                } else if (title.contains("파티 관리 창")) {
                    Party party = pm.getPartyByPlayer(player.getUniqueId());
                    if (party == null) {
                        player.closeInventory();
                        return;
                    }

                    boolean isAdmin = party.isAdmin(player.getUniqueId());

                    if (clickedItem.getType() == Material.PLAYER_HEAD) {
                        if (isAdmin && clickedItem.hasItemMeta()) {
                            NamespacedKey key = new NamespacedKey(plugin, "party_member_uuid");
                            var container = clickedItem.getItemMeta().getPersistentDataContainer();
                            if (container.has(key, PersistentDataType.STRING)) {
                                String targetUuidStr = container.get(key, PersistentDataType.STRING);
                                UUID targetUuid = UUID.fromString(Objects.requireNonNull(targetUuidStr));

                                if (!player.getUniqueId().equals(targetUuid)) {
                                    if (event.isLeftClick()) {
                                        pm.delegateAdmin(player.getUniqueId(), targetUuid);
                                    } else if (event.isRightClick()) {
                                        pm.kickMember(player.getUniqueId(), targetUuid);
                                    }
                                }
                            }
                        }
                        return;
                    }

                    switch (slot) {
                        case 46:
                            pm.leaveParty(player);
                            openListGUI(player);
                            break;
                        case 49:
                            if (isAdmin) {
                                pm.disbandParty(party.getAdmin());
                            } else {
                                player.sendMessage("§c권한이 없습니다. 방장만 파티를 해체할 수 있습니다.");
                            }
                            break;
                        case 52:
                            if (isAdmin) {
                                player.sendMessage("§a게임을 시작합니다!");
                                player.closeInventory();
                                player.performCommand("zombie start");
                            } else {
                                player.sendMessage("§c권한이 없습니다. 방장만 게임을 시작할 수 있습니다.");
                            }
                            break;
                    }
                }
            });
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("파티 목록") || title.contains("파티 관리 창")) {
            event.setCancelled(true);
        }
    }
}