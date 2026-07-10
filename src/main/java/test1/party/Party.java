package test1.party;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Party {
    private UUID admin;
    private final Set<UUID> members;

    public Party(UUID admin) {
        Objects.requireNonNull(admin, "방장(admin)의 UUID는 null일 수 없습니다.");
        this.admin = admin;
        this.members = new HashSet<>();
        this.members.add(admin); // 방장도 기본적으로 파티원에 포함
    }

    public UUID getAdmin() {
        return admin;
    }

    public void setAdmin(UUID admin) {
        Objects.requireNonNull(admin, "방장(admin)의 UUID는 null일 수 없습니다.");
        this.admin = admin;
        this.members.add(admin); // 새로운 방장도 파티원 목록에 확실히 포함되도록 보장
    }

    public boolean isAdmin(UUID playerUuid) {
        return this.admin.equals(playerUuid);
    }

    public Set<UUID> getMembers() {
        // 외부에서 컬렉션을 직접 수정하지 못하게 읽기 전용(Unmodifiable) 뷰 반환
        return Collections.unmodifiableSet(members);
    }

    public boolean hasMember(UUID uuid) {
        return members.contains(uuid);
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean addMember(UUID uuid) {
        Objects.requireNonNull(uuid, "추가할 멤버의 UUID는 null일 수 없습니다.");
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        // if (isAdmin(uuid)) {
        //     throw new IllegalStateException("방장을 일반적인 방법으로 제거할 수 없습니다. 먼저 방장을 위임하세요.");
        // }
        return members.remove(uuid);
    }
}