package test1.party;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    private UUID admin;
    private final Set<UUID> members;

    public Party(UUID admin) {
        this.admin = admin;
        this.members = new HashSet<>();
        this.members.add(admin); // 방장도 기본적으로 파티원에 포함
    }

    public UUID getAdmin() {
        return admin;
    }

    public void setAdmin(UUID admin) {
        this.admin = admin;
    }

    public boolean isAdmin(UUID playerUuid) {
        return this.admin.equals(playerUuid);
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }
}