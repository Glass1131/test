package test1.command;

import org.bukkit.attribute.Attribute;
import test1.manager.GameManager;
import test1.MyPlugin;
import test1.system.OxygenSystem;
import test1.mob.BossListener;
import test1.party.Party;
import test1.party.PartyManager;
import test1.item.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.*;
import java.util.stream.Collectors;

public class CustomCommand implements CommandExecutor, TabCompleter, Listener {

    private final MyPlugin plugin;
    private final GameManager gameManager;
    private final PartyManager partyManager;
    private final Map<String, ItemStack> itemMap = new LinkedHashMap<>();

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    private static final List<String> GAME_SUBCOMMANDS = Arrays.asList("start", "stop", "skip", "round");
    private static final List<String> ROUND_SUBCOMMANDS = Arrays.asList("add", "remove", "reset", "set");
    private static final List<String> ZOMBIE_SUBCOMMANDS = Arrays.asList(
            "game", "biome", "reload", "guide", "info",
            "system", "item",
            "setaltar", "testchance", "mob", "region", "summon", "party"
    );

    private static final List<String> SYSTEM_SUBCOMMANDS = Arrays.asList("oxygen", "thirst", "heat");
    private static final List<String> ITEM_SUBCOMMANDS = Collections.singletonList("list");
    private static final List<String> REGION_SUBCOMMANDS = Arrays.asList("p1", "p2", "save", "remove", "list");

    private static final List<String> WITCH_POTION_TYPES = Arrays.asList("HARMING", "POISON", "SLOWNESS", "WEAKNESS");

    public CustomCommand(MyPlugin plugin, GameManager gameManager, PartyManager partyManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.partyManager = partyManager;
        initializeItemMap();
    }

    private void initializeItemMap() {
        itemMap.put("zombie_power", CustomItem.ZOMBIE_POWER);
        itemMap.put("d_sword", CustomItem.D_SWORD);
        itemMap.put("d_helmet", CustomItem.D_HELMET);
        itemMap.put("d_chestplate", CustomItem.D_CHESTPLATE);
        itemMap.put("d_leggings", CustomItem.D_LEGGINGS);
        itemMap.put("d_boots", CustomItem.D_BOOTS);
        itemMap.put("dark_shrieker", CustomItem.DARK_CORE);
        itemMap.put("dark_weapon", CustomItem.DARK_WEAPON);
        itemMap.put("sculk", CustomItem.SCULK);
        itemMap.put("sculk_vein", CustomItem.SCULK_VEIN);
        itemMap.put("sculk_sensor", CustomItem.SCULK_SENSOR);
        itemMap.put("silence_template", CustomItem.SILENCE_TEMPLATE);
        itemMap.put("amethyst_shard", CustomItem.AMETHYST_SHARD);
        itemMap.put("calibrated_sculk_sensor", CustomItem.CALIBRATED_SCULK_SENSOR);

        itemMap.put("immortal_one", CustomItem.IMMORTAL_ONE);
        itemMap.put("witch_eye", CustomItem.WITCH_EYE);
        itemMap.put("suspicious_potion", CustomItem.SUSPICIOUS_POTION);
        itemMap.put("zombie_apple", CustomItem.ZOMBIE_APPLE);
        itemMap.put("zombie_gold_nugget", CustomItem.ZOMBIE_GOLD_NUGGET);
        itemMap.put("zombie_trace", CustomItem.ZOMBIE_TRACE);
        itemMap.put("golden_apple_custom", CustomItem.GOLDEN_APPLE_CUSTOM);

        itemMap.put("sticky_slime", CustomItem.STICKY_SLIME);
        itemMap.put("oxygen_filter", CustomItem.OXYGEN_FILTER);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("[!] 플레이어만 사용할 수 있는 명령어입니다.", NamedTextColor.RED));
            return true;
        }

        if (command.getName().equalsIgnoreCase("zombie")) {
            return handleZombieCommand(player, args);
        }
        return false;
    }

    private boolean handleZombieCommand(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Component.text("사용법: /zombie <game|item|party|guide|system|...>", NamedTextColor.YELLOW));
            return true;
        }

        String sub = args[0].toLowerCase();

        // [1] 권한 없이(누구나) 사용 가능한 명령어들 (순서 중요)

        // 아이템 확인
        switch (sub) {
            case "item" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("list")) {
                    player.sendMessage(Component.text("사용법: /zombie item list", NamedTextColor.RED));
                    return true;
                }
                handleItemSubCommand(player);
                return true;
            }


            // 가이드 확인
            case "guide" -> {
                openGuideGui(player);
                return true;
            }


            // [버그 수정] 파티 관리 명령어는 OP 체크 전에 처리 (누구나 사용 가능)
            case "party" -> {
                handlePartyCommand(player, args);
                return true;
            }
        }

        // [2] 관리자(OP) 권한이 필요한 명령어들
        // 위에서 처리되지 않은 모든 명령어는 OP 권한이 필요함
        if (!player.isOp()) {
            player.sendMessage(Component.text("이 명령어를 사용할 권한(OP)이 없습니다.", NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "reload" -> {
                plugin.loadConfigValues();
                player.sendMessage(Component.text("✅ 설정(config.yml)이 리로드되었습니다!", NamedTextColor.GREEN));
                return true;
            }
            case "start" -> { // legacy support
                handleStartGameCommand(player);
                return true;
            }
            case "stop" -> { // legacy support
                handleStopGameCommand(player);
                return true;
            }
            case "game" -> {
                handleGameCommand(player, args);
                return true;
            }
            case "system" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("사용법: /zombie system <oxygen|thirst|heat>", NamedTextColor.RED));
                    return true;
                }
                String sysSub = args[1].toLowerCase();
                String[] sysArgs = Arrays.copyOfRange(args, 2, args.length);

                switch (sysSub) {
                    case "oxygen" -> handleOxygenCommand(player, sysArgs);
                    case "thirst" -> handleThirstCommand(player, sysArgs);
                    case "heat" -> handleHeatCommand(player);
                    default -> player.sendMessage(Component.text("알 수 없는 시스템 명령어입니다.", NamedTextColor.RED));
                }
                return true;
            }
            // party 케이스는 위에서 미리 처리했으므로 switch 문에서는 제거됨
            case "round" -> { // legacy support
                if (args.length > 1) {
                    String[] roundArgs = Arrays.copyOfRange(args, 1, args.length);
                    handleRoundCommand(player, roundArgs);
                } else {
                    player.sendMessage(Component.text("사용법: /zombie round <add|remove|set|reset> [값]", NamedTextColor.RED));
                }
                return true;
            }
            case "skip" -> { // legacy support
                player.sendMessage(Component.text("⏩ 준비 시간을 건너뜁니다!", NamedTextColor.GREEN));
                gameManager.skipPreparation();
                return true;
            }
            case "biome" -> {
                org.bukkit.block.Biome b = player.getWorld().getBiome(player.getLocation());
                String key = b.getKey().getKey();
                String formatted = plugin.formatBiomeName(b);
                player.sendMessage(Component.text("--- 바이옴 디버그 ---", NamedTextColor.GOLD));
                player.sendMessage(Component.text("현재 위치 바이옴: ", NamedTextColor.WHITE)
                        .append(Component.text(formatted + " (" + key + ")", NamedTextColor.YELLOW)));
                return true;
            }
            case "testchance" -> {
                boolean newState = !plugin.isProbabilityTestMode();
                plugin.setProbabilityTestMode(newState);
                Component statusText = newState ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED);
                player.sendMessage(Component.text("🔥 [TEST MODE] 확률 테스트 모드: ", NamedTextColor.GOLD).append(statusText));
                return true;
            }
            case "setaltar" -> {
                if (plugin.getDeepDark() != null) {
                    Location currentLoc = player.getLocation();
                    Location target = new Location(currentLoc.getWorld(),
                            currentLoc.getBlockX() + 0.5,
                            currentLoc.getBlockY(),
                            currentLoc.getBlockZ() + 0.5);
                    plugin.getDeepDark().setTargetLocation(target);
                    player.sendMessage(Component.text("🏛 [Deep Dark] 제단 위치가 현재 위치로 설정되었습니다.", NamedTextColor.DARK_PURPLE));
                    player.sendMessage(Component.text("좌표: " + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ(), NamedTextColor.GRAY));
                }
                return true;
            }
            case "mob" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("사용법: /zombie mob <witch|...>", NamedTextColor.RED));
                    return true;
                }
                String mobName = args[1].toLowerCase();

                if (mobName.equals("witch")) {
                    BossListener bossListener = plugin.getBossListener();
                    if (bossListener == null) return true;

                    if (args.length < 3) {
                        player.sendMessage(Component.text("Witch settings...", NamedTextColor.GRAY));
                        return true;
                    }
                    String witchSetting = args[2].toLowerCase();
                    switch (witchSetting) {
                        case "type" -> {
                            if (args.length < 4) return true;
                            try {
                                PotionType type = PotionType.valueOf(args[3].toUpperCase());
                                bossListener.setWitchPotionType(type);
                                player.sendMessage(Component.text("🧙 마녀 포션: " + type.name(), NamedTextColor.GREEN));
                            } catch (IllegalArgumentException e) {
                                player.sendMessage(Component.text("잘못된 타입입니다.", NamedTextColor.RED));
                            }
                            return true;
                        }
                        case "level" -> {
                            if (args.length < 4) return true;
                            try {
                                int level = Math.max(1, Integer.parseInt(args[3]));
                                bossListener.setWitchAmplifier(level);
                                player.sendMessage(Component.text("🧙 마녀 강도: 레벨 " + level, NamedTextColor.GREEN));
                            } catch (NumberFormatException e) {
                                throw new RuntimeException(e);
                            }
                            return true;
                        }
                        case "cooldown" -> {
                            if (args.length < 4) return true;
                            try {
                                double cd = Math.max(0.0, Double.parseDouble(args[3]));
                                bossListener.setWitchCooldownSeconds(cd);
                                player.sendMessage(Component.text("🧙 마녀 쿨타임: " + cd + "초", NamedTextColor.GREEN));
                            } catch (NumberFormatException e) {
                                throw new RuntimeException(e);
                            }
                            return true;
                        }
                        case "playeronly" -> {
                            bossListener.setWitchPlayerOnlyMode(!bossListener.isWitchPlayerOnlyMode());
                            player.sendMessage(Component.text("PlayerOnly: " + bossListener.isWitchPlayerOnlyMode()));
                            return true;
                        }
                        case "prevent-vanilla" -> {
                            bossListener.setWitchPreventVanillaThrow(!bossListener.isWitchPreventVanillaThrow());
                            player.sendMessage(Component.text("PreventVanilla: " + bossListener.isWitchPreventVanillaThrow()));
                            return true;
                        }
                    }
                }
                return true;
            }
            case "region" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("사용법: /zombie region <p1|p2|save|remove|list> [biome]", NamedTextColor.RED));
                    return true;
                }
                handleRegionCommand(player, args);
                return true;
            }
            case "summon" -> {
                if (args.length < 2) return true;
                String mobName = args[1].toLowerCase();
                Location loc = player.getLocation();

                if (mobName.equals("skeleton")) {
                    if (args.length >= 3 && args[2].equalsIgnoreCase("swamp")) {
                        gameManager.spawnBoss(loc, "swamp", 10);
                        return true;
                    }
                }

                if (mobName.equals("spear_zombie") && plugin.getSpearZombie() != null) {
                    plugin.getSpearZombie().spawn(loc);
                    player.sendMessage(Component.text("⚔ 창 좀비 소환", NamedTextColor.GREEN));
                } else if (mobName.equals("boss_witch")) {
                    int level = 10;
                    if (args.length >= 3) { try { level = Integer.parseInt(args[2]); } catch(Exception e) {
                        throw new RuntimeException(e);
                    }
                    }
                    org.bukkit.entity.Witch w = (org.bukkit.entity.Witch) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.WITCH);
                    w.customName(Component.text("§5§l[BOSS] 늪지대의 마녀 (Lv." + level + ")"));
                    w.setCustomNameVisible(true);
                    gameManager.addGameMonster(w);

                    if (plugin.getBossListener() != null) {
                        plugin.getBossListener().addWitch(w.getUniqueId());
                    }

                    player.sendMessage(Component.text("🧙 보스 마녀 소환", NamedTextColor.DARK_PURPLE));
                }
                return true;
            }
            case "info" -> {
                handleInfoCommand(player, args);
                return true;
            }
            default -> {
                player.sendMessage(Component.text("알 수 없는 명령어입니다.", NamedTextColor.RED));
                return false;
            }
        }
    }

    private void openGuideGui(Player player) {
        Inventory guide = Bukkit.createInventory(null, 27, Component.text("관리자 명령어 가이드", NamedTextColor.DARK_RED));

        guide.setItem(10, createGuideItem(Material.DIAMOND_SWORD, "§a게임 진행", Arrays.asList(
                "§f/zombie game start §7- 게임 시작",
                "§f/zombie game stop §7- 게임 강제 종료",
                "§f/zombie game skip §7- 준비 시간 건너뛰기"
        )));

        guide.setItem(12, createGuideItem(Material.CHEST, "§6아이템 & 시스템", Arrays.asList(
                "§f/zombie item list §7- 커스텀 아이템 목록",
                "§f/zombie system oxygen add §7- 산소 구역 추가",
                "§f/zombie system heat §7- 현재 온도 확인"
        )));

        guide.setItem(14, createGuideItem(Material.ZOMBIE_HEAD, "§c몹 & 스폰", Arrays.asList(
                "§f/zombie summon spear_zombie §7- 창 좀비 소환",
                "§f/zombie summon boss_witch [Lv] §7- 보스 마녀 소환",
                "§f/zombie summon skeleton swamp §7- 늪지대 보스 스켈레톤 소환",
                "§f/zombie mob witch ... §7- 마녀 설정"
        )));

        guide.setItem(16, createGuideItem(Material.PAPER, "§b기타 & 설정", Arrays.asList(
                "§f/zombie reload §7- 설정파일 리로드",
                "§f/zombie region p1/p2/save/remove §7- 스폰 영역 설정",
                "§f/zombie info <몹종류> §7- 몹 상태 확인"
        )));

        player.openInventory(guide);
    }

    private ItemStack createGuideItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.lore(lore.stream().map(l -> Component.text(l, NamedTextColor.GRAY)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleItemSubCommand(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("커스텀 아이템 목록", NamedTextColor.DARK_PURPLE));

        for (ItemStack item : itemMap.values()) {
            if (item != null) {
                gui.addItem(item);
            }
        }

        ItemStack getAllButton = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta meta = getAllButton.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("모두 받기", NamedTextColor.GREEN, TextDecoration.BOLD));
            meta.lore(Collections.singletonList(Component.text("§7클릭 시 모든 커스텀 아이템을 지급받습니다. (OP 전용)")));
            getAllButton.setItemMeta(meta);
        }
        gui.setItem(53, getAllButton);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.contains("관리자 명령어 가이드")) {
            event.setCancelled(true);
            return;
        }

        if (title.contains("커스텀 아이템 목록")) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) return;

            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

                if (clickedItem.getType() == Material.GREEN_CONCRETE && event.getSlot() == 53) {
                    if (player.isOp()) {
                        for (ItemStack item : itemMap.values()) {
                            if (item != null) {
                                player.getInventory().addItem(item.clone());
                            }
                        }
                        player.sendMessage(Component.text("모든 커스텀 아이템을 지급받았습니다!", NamedTextColor.GREEN));
                        player.closeInventory();
                    } else {
                        player.sendMessage(Component.text("관리자(OP)만 이 기능을 사용할 수 있습니다.", NamedTextColor.RED));
                    }
                    return;
                }

                if (player.isOp()) {
                    player.getInventory().addItem(clickedItem.clone());
                    player.sendMessage(Component.text("아이템 지급: ", NamedTextColor.GREEN).append(clickedItem.displayName()));
                } else {
                    player.sendMessage(Component.text("관리자(OP)만 아이템을 꺼낼 수 있습니다.", NamedTextColor.RED));
                    player.closeInventory();
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("관리자 명령어 가이드") || title.contains("커스텀 아이템 목록")) {
            event.setCancelled(true);
        }
    }

    private void handleRegionCommand(Player player, String[] args) {
        String regionSub = args[1].toLowerCase();
        switch (regionSub) {
            case "p1" -> {
                pos1.put(player.getUniqueId(), player.getLocation());
                player.sendMessage(Component.text("📍 위치 1이 설정되었습니다.", NamedTextColor.GREEN));
            }
            case "p2" -> {
                pos2.put(player.getUniqueId(), player.getLocation());
                player.sendMessage(Component.text("📍 위치 2가 설정되었습니다.", NamedTextColor.GREEN));
            }
            case "save" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("사용법: /zombie region save <biome>", NamedTextColor.RED));
                    return;
                }
                String biomeName = args[2].toLowerCase();
                Location l1 = pos1.get(player.getUniqueId());
                Location l2 = pos2.get(player.getUniqueId());
                if (l1 != null && l2 != null && l1.getWorld().equals(l2.getWorld())) {
                    plugin.saveBiomeCoordinates(biomeName, l1.getBlockX(), l1.getBlockZ(), l2.getBlockX(), l2.getBlockZ());
                    player.sendMessage(Component.text("💾 저장 완료: " + biomeName, NamedTextColor.AQUA));
                } else {
                    player.sendMessage(Component.text("위치 1, 2를 먼저 설정해주세요 (같은 월드여야 함).", NamedTextColor.RED));
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("사용법: /zombie region remove <biome>", NamedTextColor.RED));
                    return;
                }
                String biomeName = args[2].toLowerCase();
                plugin.getConfig().set("biomes." + biomeName, null);
                plugin.saveConfig();
                plugin.loadConfigValues();
                player.sendMessage(Component.text("🗑 삭제 완료: " + biomeName, NamedTextColor.RED));
            }
            case "list" -> {
                Map<String, List<Integer>> coordsMap = plugin.getConfigBiomeSpawnCoords();
                if (coordsMap.isEmpty()) {
                    player.sendMessage(Component.text("등록된 스폰 지역이 없습니다.", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("=== 등록된 스폰 지역 목록 ===", NamedTextColor.GOLD));
                    for (String key : coordsMap.keySet()) {
                        List<Integer> coords = coordsMap.get(key);
                        String coordStr = String.format("[%d, %d, %d, %d]", coords.get(0), coords.get(1), coords.get(2), coords.get(3));
                        player.sendMessage(Component.text("- " + key + ": ", NamedTextColor.YELLOW)
                                .append(Component.text(coordStr, NamedTextColor.WHITE)));
                    }
                }
            }
            default -> player.sendMessage(Component.text("알 수 없는 region 명령어입니다.", NamedTextColor.RED));
        }
    }

    private void handleOxygenCommand(Player player, String[] args) {
        OxygenSystem oxygenSystem = plugin.getOxygenSystem();
        if (oxygenSystem == null) return;
        if (args.length == 0) {
            boolean enabled = !oxygenSystem.isEnabled();
            oxygenSystem.setEnabled(enabled);
            Component statusText = enabled ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED);
            player.sendMessage(Component.text("💨 산소 시스템: ", NamedTextColor.AQUA).append(statusText));
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> {
                Map<Integer, Location> locs = oxygenSystem.getSupplyLocations();
                if (locs.isEmpty()) {
                    player.sendMessage(Component.text("❌ 등록된 산소 공급 구역이 없습니다.", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("💨 산소 공급 구역 목록:", NamedTextColor.AQUA, TextDecoration.BOLD));
                    List<Integer> sortedIds = new ArrayList<>(locs.keySet());
                    Collections.sort(sortedIds);
                    for (int id : sortedIds) {
                        Location loc = locs.get(id);
                        String coords = String.format("%d %d %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                        Component listEntry = Component.text(id + ". ", NamedTextColor.GRAY)
                                .append(Component.text("[ " + coords + " ]", NamedTextColor.YELLOW))
                                .hoverEvent(HoverEvent.showText(Component.text("클릭하여 이동", NamedTextColor.GREEN)))
                                .clickEvent(ClickEvent.runCommand("/tp " + player.getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
                        player.sendMessage(listEntry);
                    }
                }
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("사용법: /zombie system oxygen delete <번호|all>", NamedTextColor.RED));
                    return;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    oxygenSystem.clearSupplyLocations();
                    player.sendMessage(Component.text("✅ 모든 구역 삭제 완료.", NamedTextColor.GREEN));
                } else {
                    try {
                        int id = Integer.parseInt(args[1]);
                        if (oxygenSystem.removeSupplyLocation(id)) {
                            player.sendMessage(Component.text("✅ " + id + "번 구역 삭제 완료.", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("❌ " + id + "번 구역을 찾을 수 없습니다.", NamedTextColor.RED));
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("숫자를 입력해주세요.", NamedTextColor.RED));
                    }
                }
            }
            case "add" -> {
                Location targetLoc;
                if (args.length == 4) {
                    try {
                        double x = Double.parseDouble(args[1]);
                        double y = Double.parseDouble(args[2]);
                        double z = Double.parseDouble(args[3]);
                        targetLoc = new Location(player.getWorld(), x, y, z);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("좌표는 숫자여야 합니다.", NamedTextColor.RED));
                        return;
                    }
                } else {
                    targetLoc = player.getLocation();
                }
                int newId = oxygenSystem.addSupplyLocation(targetLoc);
                player.sendMessage(Component.text("💨 산소 공급 구역 추가됨 (ID: " + newId + ")", NamedTextColor.AQUA));
            }
            default -> {
                try {
                    int val = Integer.parseInt(sub);
                    oxygenSystem.setOxygenLevel(player, val);
                    player.sendMessage(Component.text("산소 수치를 " + val + "% 로 설정했습니다.", NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("사용법: /zombie system oxygen <add|list|delete|숫자>", NamedTextColor.RED));
                }
            }
        }
    }

    private void handleThirstCommand(Player player, String[] args) {
        if (plugin.getThirstSystem() == null) return;
        if (args.length == 0) {
            boolean enabled = !plugin.getThirstSystem().isEnabled();
            plugin.getThirstSystem().setEnabled(enabled);
            Component statusText = enabled ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED);
            player.sendMessage(Component.text("💧 갈증 시스템: ", NamedTextColor.AQUA).append(statusText));
            return;
        }
        try {
            int val = Integer.parseInt(args[0]);
            plugin.getThirstSystem().setThirstLevel(player, val);
            player.sendMessage(Component.text("갈증 레벨을 " + val + "% 로 설정했습니다.", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("숫자를 입력해주세요.", NamedTextColor.RED));
        }
    }

    private void handleHeatCommand(Player player) {
        String temp = (plugin.getHeatSystem() != null) ? plugin.getHeatSystem().getTemperatureState(player) : "N/A";
        player.sendMessage(Component.text("현재 온도: " + temp, NamedTextColor.GOLD));
    }

    private void handleStartGameCommand(Player player) {
        if (gameManager.isGameInProgress()) {
            player.sendMessage(Component.text(GameManager.GAME_ALREADY_IN_PROGRESS, NamedTextColor.RED));
        } else {
            gameManager.startGame();
        }
    }

    private void handleStopGameCommand(Player player) {
        if (gameManager.isGameInProgress()) {
            gameManager.stopGame();
            Bukkit.broadcast(Component.text(MyPlugin.GAME_FORCED_STOPPED, NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text(GameManager.NO_GAME_IN_PROGRESS, NamedTextColor.RED));
        }
    }

    private void handleGameCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /zombie game <start|stop|skip|round>", NamedTextColor.RED));
            return;
        }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "start" -> handleStartGameCommand(player);
            case "stop" -> handleStopGameCommand(player);
            case "skip" -> {
                player.sendMessage(Component.text("⏩ 준비 시간을 건너뜁니다!", NamedTextColor.GREEN));
                gameManager.skipPreparation();
            }
            case "round" -> {
                if (!player.hasPermission("plugin.round")) { player.sendMessage(Component.text("권한 없음", NamedTextColor.RED)); return; }
                if (args.length > 2) {
                    String[] roundArgs = Arrays.copyOfRange(args, 2, args.length);
                    handleRoundCommand(player, roundArgs);
                } else {
                    player.sendMessage(Component.text("사용법: /zombie game round <add|remove|set|reset> [값]", NamedTextColor.RED));
                }
            }
        }
    }

    private void handleRoundCommand(Player player, String[] args) {
        if (!gameManager.isGameInProgress()) {
            player.sendMessage(Component.text("⚠ 게임이 진행 중이지 않습니다. 먼저 /zombie start 를 해주세요.", NamedTextColor.RED));
            return;
        }
        String subCommand = args[0].toLowerCase();
        int targetRound = gameManager.getCurrentRound();
        try {
            if (args.length < 2 && !subCommand.equals("reset")) return;
            int val = (args.length >= 2) ? Integer.parseInt(args[1]) : 0;
            switch(subCommand) {
                case "set" -> targetRound = val;
                case "add" -> targetRound += val;
                case "remove" -> targetRound -= val;
                case "reset" -> targetRound = 1;
            }
        } catch (NumberFormatException e) { return; }

        int finalRound = Math.max(1, targetRound);
        Bukkit.getScheduler().runTask(plugin, () -> {
            gameManager.stopGame();
            gameManager.startGame(finalRound);
            gameManager.skipPreparation();
        });
    }

    private void handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /zombie info <몹 종류>", NamedTextColor.RED));
            return;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("존재하지 않는 몹 종류입니다.", NamedTextColor.RED));
            return;
        }

        List<LivingEntity> foundEntities = player.getWorld().getEntitiesByClass(LivingEntity.class).stream()
                .filter(e -> e.getType() == type)
                .toList();

        if (foundEntities.isEmpty()) {
            player.sendMessage(Component.text("해당 종류의 몹을 찾을 수 없습니다.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== [ " + type.name() + " 정보 ] ===", NamedTextColor.GOLD));
        for (LivingEntity entity : foundEntities) {
            double hp = Math.round(entity.getHealth() * 10.0) / 10.0;
            double maxHp = 0;
            if (entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                maxHp = Objects.requireNonNull(entity.getAttribute(Attribute.MAX_HEALTH)).getValue();
            }

            String locStr = String.format("(%d, %d, %d)",
                    entity.getLocation().getBlockX(), entity.getLocation().getBlockY(), entity.getLocation().getBlockZ());

            StringBuilder effects = new StringBuilder();
            for (PotionEffect effect : entity.getActivePotionEffects()) {
                effects.append(effect.getType().key().value()).append(" ").append(effect.getAmplifier() + 1).append(", ");
            }
            String effectStr = !effects.isEmpty() ? effects.substring(0, effects.length() - 2) : "없음";

            Component msg = Component.text("ID:" + entity.getEntityId() + " " + locStr, NamedTextColor.YELLOW)
                    .append(Component.text(" | HP: " + hp + "/" + maxHp, NamedTextColor.RED))
                    .append(Component.text(" | 효과: " + effectStr, NamedTextColor.AQUA));

            player.sendMessage(msg);
        }
    }

    private void handlePartyCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /zombie party <create|join|leave|start|disband|kick>", NamedTextColor.RED));
            return;
        }
        String partySub = args[1].toLowerCase();
        switch (partySub) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("방 번호를 입력해주세요.", NamedTextColor.RED));
                    return;
                }
                try {
                    int roomId = Integer.parseInt(args[2]);
                    if (partyManager.createParty(player, roomId)) {
                        player.sendMessage(Component.text("✅ 방 " + roomId + "번이 생성되었습니다!", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("❌ 이미 존재하는 방이거나 파티에 속해있습니다.", NamedTextColor.RED));
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("숫자를 입력해주세요.", NamedTextColor.RED));
                }
            }
            case "join" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("방 번호를 입력해주세요.", NamedTextColor.RED));
                    return;
                }
                try {
                    int roomId = Integer.parseInt(args[2]);
                    if (partyManager.joinParty(player, roomId)) {
                        player.sendMessage(Component.text("✅ 방 " + roomId + "번에 참여했습니다!", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("❌ 존재하지 않는 방이거나 이미 파티에 속해있습니다.", NamedTextColor.RED));
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("숫자를 입력해주세요.", NamedTextColor.RED));
                }
            }
            case "leave" -> {
                partyManager.leaveParty(player);
                player.sendMessage(Component.text("파티를 떠났습니다.", NamedTextColor.YELLOW));
            }
            case "start" -> {
                Party party = partyManager.getParty(player);
                if (party == null) {
                    player.sendMessage(Component.text("파티에 속해있지 않습니다.", NamedTextColor.RED));
                    return;
                }
                if (!party.isAdmin(player.getUniqueId())) {
                    player.sendMessage(Component.text("방장만 게임을 시작할 수 있습니다.", NamedTextColor.RED));
                    return;
                }
                if (gameManager.isGameInProgress()) {
                    player.sendMessage(Component.text("이미 게임이 진행 중입니다.", NamedTextColor.RED));
                    return;
                }
                gameManager.startGame(party, 1);
            }
            case "disband" -> {
                Party party = partyManager.getParty(player);
                if (party == null) {
                    player.sendMessage(Component.text("파티에 속해있지 않습니다.", NamedTextColor.RED));
                    return;
                }
                if (!party.isAdmin(player.getUniqueId())) {
                    player.sendMessage(Component.text("방장만 파티를 해체할 수 있습니다.", NamedTextColor.RED));
                    return;
                }
                int roomId = party.getId();
                partyManager.disbandParty(roomId);
            }
            case "kick" -> {
                Party party = partyManager.getParty(player);
                if (party == null) {
                    player.sendMessage(Component.text("파티에 속해있지 않습니다.", NamedTextColor.RED));
                    return;
                }
                if (!party.isAdmin(player.getUniqueId())) {
                    player.sendMessage(Component.text("방장만 파티원을 추방할 수 있습니다.", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("추방할 플레이어 이름을 입력해주세요.", NamedTextColor.RED));
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage(Component.text("플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
                    return;
                }
                if (target.equals(player)) {
                    player.sendMessage(Component.text("자기 자신은 추방할 수 없습니다.", NamedTextColor.RED));
                    return;
                }
                if (party.getMembers().contains(target.getUniqueId())) {
                    partyManager.leaveParty(target);
                    target.sendMessage(Component.text("파티에서 추방되었습니다.", NamedTextColor.RED));
                    player.sendMessage(Component.text(target.getName() + "님을 추방했습니다.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("해당 플레이어는 파티원이 아닙니다.", NamedTextColor.RED));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("zombie")) {
            if (args.length == 1) {
                return new ArrayList<>(ZOMBIE_SUBCOMMANDS);
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                return switch (sub) {
                    case "item" -> ITEM_SUBCOMMANDS;
                    case "system" -> SYSTEM_SUBCOMMANDS;
                    case "game" -> GAME_SUBCOMMANDS;
                    case "mob" -> Collections.singletonList("witch");
                    case "info" -> Arrays.stream(EntityType.values()).map(Enum::name).collect(Collectors.toList());
                    case "region" -> REGION_SUBCOMMANDS;
                    case "summon" -> Arrays.asList("spear_zombie", "boss_witch", "skeleton");
                    case "party" -> Arrays.asList("create", "join", "leave", "start", "disband", "kick");
                    default -> Collections.emptyList();
                };
            }
            if (args.length == 3) {
                String sub = args[0].toLowerCase();
                String sub2 = args[1].toLowerCase();

                if (sub.equals("system")) {
                    if (sub2.equals("oxygen")) return Arrays.asList("list", "add", "delete", "0", "100");
                    if (sub2.equals("thirst")) return Arrays.asList("0", "50", "100");
                }

                if (sub.equals("game")) {
                    if (sub2.equals("round")) return ROUND_SUBCOMMANDS;
                }

                if (sub.equals("mob") && sub2.equalsIgnoreCase("witch")) {
                    return Arrays.asList("type", "level", "cooldown", "playeronly", "prevent-vanilla");
                }
                if (sub.equals("party") && sub2.equalsIgnoreCase("kick")) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> !name.equals(sender.getName()))
                            .toList();
                }
                if (sub.equals("region") && (sub2.equalsIgnoreCase("save") || sub2.equalsIgnoreCase("remove"))) {
                    return new ArrayList<>(plugin.getConfigBiomeSpawnCoords().keySet());
                }
                if (sub.equals("summon") && sub2.equalsIgnoreCase("skeleton")) {
                    return List.of("swamp");
                }
            }
            if (args.length == 4) {
                String sub = args[0].toLowerCase(); // mob
                String sub2 = args[1].toLowerCase(); // witch
                String sub3 = args[2].toLowerCase(); // type

                if (sub.equals("mob") && sub2.equals("witch") && sub3.equals("type")) {
                    return WITCH_POTION_TYPES;
                }
            }
        }
        return Collections.emptyList();
    }
}