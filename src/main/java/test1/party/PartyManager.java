package test1.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

public class PartyManager {
    private static PartyManager instance;

    private final Map<UUID, Party> parties;
    private final Map<UUID, UUID> playerPartyMap;

    public PartyManager() {
        parties = new HashMap<>();
        playerPartyMap = new HashMap<>();
    }

    public static PartyManager getInstance() {
        if (instance == null) {
            instance = new PartyManager();
        }
        return instance;
    }

    public Collection<Party> getParties() {
        return parties.values();
    }

    public Party getParty(UUID leaderUuid) {
        return parties.get(leaderUuid);
    }

    public Party getPartyByPlayer(UUID playerUuid) {
        UUID leaderUuid = playerPartyMap.get(playerUuid);
        if (leaderUuid != null) {
            return parties.get(leaderUuid);
        }
        return null;
    }

    public void createParty(Player player) {
        UUID uuid = player.getUniqueId();
        if (getPartyByPlayer(uuid) != null) return;

        Party party = new Party(uuid);
        parties.put(uuid, party);
        playerPartyMap.put(uuid, uuid);
        refreshPartyListGUI(); // 파티가 생성되었으므로 목록 새로고침
    }

    public void joinParty(Player player, UUID leaderUuid) {
        Party party = parties.get(leaderUuid);
        if (party != null) {
            party.addMember(player.getUniqueId());
            playerPartyMap.put(player.getUniqueId(), leaderUuid);

            Component message = Component.text("§a" + player.getName() + "님이 파티에 참가했습니다.");
            broadcastToParty(party, message);

            refreshPartyGUI(party, player.getUniqueId());
            refreshPartyListGUI(); // 파티 인원이 변경되었으므로 목록 새로고침
        }
    }

    public void leaveParty(Player player) {
        UUID uuid = player.getUniqueId();
        Party party = getPartyByPlayer(uuid);

        if (party != null) {
            UUID oldAdminUuid = party.getAdmin();
            
            party.removeMember(uuid);
            playerPartyMap.remove(uuid);

            Component leaveMessage = Component.text("§e" + player.getName() + "님이 파티에서 나갔습니다.");
            broadcastToParty(party, leaveMessage);
            
            if (party.getMembers().isEmpty()) {
                parties.remove(oldAdminUuid);
                refreshPartyListGUI(); // 파티가 사라졌으므로 목록 새로고침
                return;
            }

            if (oldAdminUuid.equals(uuid)) {
                Optional<UUID> newAdminOptional = party.getMembers().stream().findFirst();
                newAdminOptional.ifPresent(newAdminUuid -> delegateAdmin(oldAdminUuid, newAdminUuid, party));
            } else {
                refreshPartyGUI(party);
                refreshPartyListGUI(); // 파티 인원이 변경되었으므로 목록 새로고침
            }
        }
    }

    public void disbandParty(UUID leaderUuid) {
        Party party = parties.get(leaderUuid);
        if (party != null) {
            Component message = Component.text("§c파티가 해체되었습니다.");
            broadcastToParty(party, message, true);

            for (UUID memberId : party.getMembers()) {
                playerPartyMap.remove(memberId);
            }
            parties.remove(leaderUuid);
            refreshPartyListGUI(); // 파티가 해체되었으므로 목록 새로고침
        }
    }

    public void kickMember(UUID leaderUuid, UUID targetUuid) {
        Party party = parties.get(leaderUuid);
        Player target = Bukkit.getPlayer(targetUuid);
        if (party != null && target != null) {
            party.removeMember(targetUuid);
            playerPartyMap.remove(targetUuid);

            Component message = Component.text("§e" + target.getName() + "님이 파티에서 추방당했습니다.");
            broadcastToParty(party, message);
            target.sendMessage("§c파티에서 추방당했습니다.");
            target.closeInventory();

            refreshPartyGUI(party);
            refreshPartyListGUI(); // 파티 인원이 변경되었으므로 목록 새로고침
        }
    }

    public void delegateAdmin(UUID oldAdminUuid, UUID newAdminUuid, Party party) {
        parties.remove(oldAdminUuid);
        party.setAdmin(newAdminUuid);
        parties.put(newAdminUuid, party);

        for (UUID memberId : party.getMembers()) {
            playerPartyMap.put(memberId, newAdminUuid);
        }

        Player newAdmin = Bukkit.getPlayer(newAdminUuid);
        if (newAdmin != null) {
            Component message = Component.text("§b" + newAdmin.getName() + "님이 새로운 파티장이 되었습니다.");
            broadcastToParty(party, message);
        }
        refreshPartyGUI(party);
        refreshPartyListGUI(); // 방장이 변경되었으므로 목록 새로고침
    }

    public void delegateAdmin(UUID oldAdminUuid, UUID newAdminUuid) {
        Party party = getParty(oldAdminUuid);
        if (party != null && party.getMembers().contains(newAdminUuid)) {
            // 기존 방장 정보를 유지한 채로 party 객체를 전달
            delegateAdmin(oldAdminUuid, newAdminUuid, party);
        }
    }

    private void broadcastToParty(Party party, Component message) {
        broadcastToParty(party, message, false);
    }

    private void broadcastToParty(Party party, Component message, boolean closeInventory) {
        for (UUID memberUuid : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
                if (closeInventory) {
                    member.closeInventory();
                }
            }
        }
    }
    
    private void refreshPartyGUI(Party party) {
        refreshPartyGUI(party, null);
    }

    private void refreshPartyGUI(Party party, UUID excludedPlayer) {
        for (UUID memberUuid : party.getMembers()) {
            if (memberUuid.equals(excludedPlayer)) continue;
            
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                String title = PlainTextComponentSerializer.plainText().serialize(member.getOpenInventory().title());
                if (title.contains("파티 관리 창")) {
                    PlayerPartyGUI.openManageGUI(member);
                }
            }
        }
    }

    public void refreshPartyListGUI() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String title = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
            if (title.contains("파티 목록")) {
                PlayerPartyGUI.openListGUI(player);
            }
        }
    }
}