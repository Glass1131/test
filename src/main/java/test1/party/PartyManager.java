package test1.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyManager {
    private final Map<Integer, Party> parties = new HashMap<>();
    private final Map<UUID, Integer> playerPartyMap = new HashMap<>();

    public boolean createParty(Player creator, int roomId) {
        if (parties.containsKey(roomId)) return false;
        if (playerPartyMap.containsKey(creator.getUniqueId())) return false;

        Party newParty = new Party(roomId, creator.getUniqueId());
        parties.put(roomId, newParty);
        playerPartyMap.put(creator.getUniqueId(), roomId);
        return true;
    }

    public boolean joinParty(Player player, int roomId) {
        if (playerPartyMap.containsKey(player.getUniqueId())) return false;
        Party party = parties.get(roomId);
        if (party == null) return false;

        party.addMember(player.getUniqueId());
        playerPartyMap.put(player.getUniqueId(), roomId);
        return true;
    }

    public void leaveParty(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerPartyMap.containsKey(uuid)) return;

        int roomId = playerPartyMap.remove(uuid);
        Party party = parties.get(roomId);
        if (party != null) {
            party.removeMember(uuid);
            if (party.isEmpty()) {
                parties.remove(roomId);
            }
        }
    }

    public Party getParty(Player player) {
        Integer roomId = playerPartyMap.get(player.getUniqueId());
        return roomId != null ? parties.get(roomId) : null;
    }

    public void disbandParty(int roomId) {
        Party party = parties.get(roomId);
        if (party == null) return;

        // [버그 수정] 파티를 삭제하기 전에 멤버들에게 메시지를 먼저 보냄
        for (UUID memberId : party.getMembers()) {
            playerPartyMap.remove(memberId); // 매핑 정보 삭제
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(Component.text("🚫 파티가 해체되었습니다.", NamedTextColor.RED));
            }
        }

        // 마지막에 파티 객체 삭제
        parties.remove(roomId);
    }
}