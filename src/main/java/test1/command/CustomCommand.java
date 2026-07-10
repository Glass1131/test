package test1.command;

import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;
import test1.manager.GameManager;
import test1.MyPlugin;
import test1.system.HeatSystem;
import test1.system.OxygenSystem;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
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
    private final Map<String, ItemStack> itemMap = new LinkedHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    public static final Set<LivingEntity> testDummies = new HashSet<>();
    private final Set<UUID> godModePlayers = new HashSet<>();
    private static final List<String> ZOMBIE_SUBCOMMANDS = Arrays.asList(
            "game", "guide", "biome", "reload", "info", "system", "item",
            "setaltar", "chance", "mob", "region", "summon", "party", "lock", "dummy", "god", "cleareffects"
    );
    private static final List<String> DUMMY_SUBCOMMANDS = Arrays.asList("spawn", "remove");
    private static final List<String> GAME_SUBCOMMANDS = Arrays.asList("start", "stop", "skip", "round");
    private static final List<String> ROUND_SUBCOMMANDS = Arrays.asList("add", "remove", "reset", "set");
    private static final List<String> SYSTEM_SUBCOMMANDS = Arrays.asList("oxygen", "thirst", "heat");
    private static final List<String> ITEM_SUBCOMMANDS = Collections.singletonList("list");
    private static final List<String> REGION_SUBCOMMANDS = Arrays.asList("p1", "p2", "save", "remove", "list");
    private static final List<String> WITCH_POTION_TYPES = Arrays.asList("HARMING", "POISON", "SLOWNESS", "WEAKNESS");
    private static final List<String> CHANCE_ITEM_NAMES = Arrays.asList("immortal_one", "dark_weapon", "zombie_apple", "spear_zombie");
    public static final Set<UUID> lockedPlayers = new HashSet<>();

    public CustomCommand(MyPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
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
        ItemStack partyBeacon = new ItemStack(Material.BEACON);
        ItemMeta beaconMeta = partyBeacon.getItemMeta();
        if (beaconMeta != null) {
            beaconMeta.displayName(Component.text("파티", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            partyBeacon.setItemMeta(beaconMeta);
        }
        itemMap.put("party_beacon", partyBeacon);
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
            player.sendMessage(Component.text("사용법: /zombie <subcommand>", NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "item":
                if (args.length < 2 || !args[1].equalsIgnoreCase("list")) {
                    player.sendMessage(Component.text("사용법: /zombie item list", NamedTextColor.RED));
                    return true;
                }
                handleItemSubCommand(player);
                return true;
            case "guide":
                openGuideGui(player);
                return true;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("이 명령어를 사용할 권한(OP)이 없습니다.", NamedTextColor.RED));
            return true;
        }
        switch (sub) {
            case "reload":
                plugin.loadConfigValues();
                player.sendMessage(Component.text("✅ 설정(config.yml)이 리로드되었습니다!", NamedTextColor.GREEN));
                return true;
            case "start":
                handleStartGameCommand(player);
                return true;
            case "stop":
                handleStopGameCommand(player);
                return true;
            case "game":
                handleGameCommand(player, args);
                return true;
            case "system":
                handleSystemCommand(player, args);
                return true;
            case "round":
                handleRoundCommand(player, Arrays.copyOfRange(args, 1, args.length));
                return true;
            case "skip":
                player.sendMessage(Component.text("⏩ 준비 시간을 건너뜁니다!", NamedTextColor.GREEN));
                gameManager.skipPreparation();
                return true;
            case "biome":
                handleBiomeCommand(player);
                return true;
            case "chance":
                handleChanceCommand(player, args);
                return true;
            case "setaltar":
                handleSetAltarCommand(player);
                return true;
            case "mob":
                handleMobCommand(player, args);
                return true;
            case "region":
                handleRegionCommand(player, args);
                return true;
            case "summon":
                handleSummonCommand(player, args);
                return true;
            case "info":
                handleInfoCommand(player, args);
                return true;
            case "party":
                handlePartyCommand(player, args);
                return true;
            case "lock":
                handleLockCommand(player, args);
                return true;
            case "dummy":
                handleDummyCommand(player, args);
                return true;
            case "god":
                handleGodCommand(player);
                return true;
            case "cleareffects":
                handleClearEffectsCommand(player, args);
                return true;
            default:
                player.sendMessage(Component.text("알 수 없는 명령어입니다.", NamedTextColor.RED));
                return false;
        }
    }

    private void openGuideGui(Player player) {
        Inventory guide = Bukkit.createInventory(null, 27, Component.text("§c[관리자 명령어 가이드]"));
        guide.setItem(10, createGuideItem(Material.CLOCK, "§6게임 관리", "§e/zombie game <start|stop|skip>", "§7게임을 시작, 중지, 스킵합니다."));
        guide.setItem(11, createGuideItem(Material.CHEST, "§a아이템 지급", "§e/zombie item list", "§7커스텀 아이템 목록을 엽니다."));
        guide.setItem(12, createGuideItem(Material.REPEATER, "§b시스템 제어", "§e/zombie system <heat|thirst|oxygen>", "§7체온, 갈증, 산소 시스템을 개별적으로 켜고 끕니다."));
        guide.setItem(13, createGuideItem(Material.ARMOR_STAND, "§c테스트 허수아비", "§e/zombie dummy <spawn|remove>", "§7피해량 측정을 위한 허수아비를 소환/제거합니다."));
        guide.setItem(14, createGuideItem(Material.TOTEM_OF_UNDYING, "§d갓 모드", "§e/zombie god", "§7자신을 무적 상태로 만듭니다."));
        guide.setItem(15, createGuideItem(Material.EXPERIENCE_BOTTLE, "§2확률 테스트", "§e/zombie chance <item> <0-100|reset>", "§7아이템 능력의 발동 확률을 조절합니다."));
        guide.setItem(16, createGuideItem(Material.BEACON, "§5파티 관리", "§e/zombie party gui", "§7모든 파티를 관리하는 GUI를 엽니다."));
        guide.setItem(17, createGuideItem(Material.MILK_BUCKET, "§f효과 제거", "§e/zombie cleareffects [player]", "§7커스텀 아이템 효과를 제거합니다."));
        player.openInventory(guide);
    }

    private ItemStack createGuideItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line));
            }
            meta.lore(loreComponents);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleItemSubCommand(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("커스텀 아이템 목록", NamedTextColor.DARK_PURPLE));
        for (ItemStack item : itemMap.values()) {
            if (item != null) gui.addItem(item);
        }
        player.openInventory(gui);
    }

    private void handleDummyCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /zombie dummy <spawn|remove> [mob_type]", NamedTextColor.RED));
            return;
        }
        String dummySub = args[1].toLowerCase();
        if (dummySub.equals("spawn")) {
            EntityType type = EntityType.ZOMBIE;
            if (args.length > 2) {
                try {
                    type = EntityType.valueOf(args[2].toUpperCase());
                    if (!type.isAlive()) {
                        player.sendMessage(Component.text("살아있는 몹 종류만 소환할 수 있습니다.", NamedTextColor.RED));
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Component.text("알 수 없는 몹 종류입니다.", NamedTextColor.RED));
                    return;
                }
            }
            LivingEntity dummy = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), type);
            dummy.setAI(false);
            dummy.setInvulnerable(false);
            dummy.setCustomNameVisible(true);
            dummy.customName(Component.text("§c[테스트 허수아비]§r", NamedTextColor.WHITE));
            testDummies.add(dummy);
            player.sendMessage(Component.text("테스트용 허수아비를 소환했습니다.", NamedTextColor.GREEN));
        } else if (dummySub.equals("remove")) {
            int count = 0;
            for (LivingEntity dummy : testDummies) {
                if (dummy != null && !dummy.isDead()) {
                    dummy.remove();
                    count++;
                }
            }
            testDummies.clear();
            player.sendMessage(Component.text(count + "개의 허수아비를 제거했습니다.", NamedTextColor.YELLOW));
        }
    }

    private void handleGodCommand(Player player) {
        UUID uuid = player.getUniqueId();
        if (godModePlayers.contains(uuid)) {
            godModePlayers.remove(uuid);
            player.setInvulnerable(false);
            player.sendMessage(Component.text("갓 모드가 비활성화되었습니다.", NamedTextColor.YELLOW));
        } else {
            godModePlayers.add(uuid);
            player.setInvulnerable(true);
            player.sendMessage(Component.text("갓 모드가 활성화되었습니다. 이제 당신은 무적입니다.", NamedTextColor.GOLD));
        }
    }

    private void handleSystemCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /zombie system <oxygen|thirst|heat>", NamedTextColor.RED));
            return;
        }
        String sysSub = args[1].toLowerCase();
        String[] sysArgs = Arrays.copyOfRange(args, 2, args.length);
        switch (sysSub) {
            case "oxygen" -> handleOxygenCommand(player, sysArgs);
            case "thirst" -> handleThirstCommand(player, sysArgs);
            case "heat" -> handleHeatCommand(player, sysArgs);
            default -> player.sendMessage(Component.text("알 수 없는 시스템 명령어입니다.", NamedTextColor.RED));
        }
    }

    private void handleBiomeCommand(Player player) {
        org.bukkit.block.Biome b = player.getWorld().getBiome(player.getLocation());
        String key = b.getKey().getKey();
        String formatted = plugin.formatBiomeName(b);
        player.sendMessage(Component.text("--- 바이옴 디버그 ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("현재 위치 바이옴: ", NamedTextColor.WHITE)
                .append(Component.text(formatted + " (" + key + ")", NamedTextColor.YELLOW)));
    }

    private void handleChanceCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("사용법: /zombie chance <item_name> <0-100|reset>", NamedTextColor.RED));
            return;
        }
        String itemName = args[1].toLowerCase();
        if (!CHANCE_ITEM_NAMES.contains(itemName)) {
            player.sendMessage(Component.text("알 수 없는 아이템 이름입니다.", NamedTextColor.RED));
            return;
        }

        String value = args[2].toLowerCase();
        if (value.equals("reset")) {
            plugin.resetCustomProbability(itemName);
            player.sendMessage(Component.text(itemName + "의 확률이 기본값으로 초기화되었습니다.", NamedTextColor.GREEN));
        } else {
            try {
                int probability = Integer.parseInt(value);
                if (probability < 0 || probability > 100) {
                    player.sendMessage(Component.text("확률은 0에서 100 사이의 숫자여야 합니다.", NamedTextColor.RED));
                    return;
                }
                plugin.setCustomProbability(itemName, probability);
                player.sendMessage(Component.text(itemName + "의 발동 확률이 " + probability + "%로 설정되었습니다.", NamedTextColor.GREEN));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("잘못된 값입니다. 0-100 사이의 숫자나 'reset'을 입력해주세요.", NamedTextColor.RED));
            }
        }
    }

    private void handleSetAltarCommand(Player player) {
        if (plugin.getDeepDark() != null) {
            Location currentLoc = player.getLocation();
            Location target = new Location(currentLoc.getWorld(), currentLoc.getBlockX() + 0.5, currentLoc.getBlockY(), currentLoc.getBlockZ() + 0.5);
            plugin.getDeepDark().setTargetLocation(target);
            player.sendMessage(Component.text("🏛 [Deep Dark] 제단 위치가 현재 위치로 설정되었습니다.", NamedTextColor.DARK_PURPLE));
        }
    }

    private void handleMobCommand(Player player, String[] args) {
        if (args.length < 2) return;
        String mobName = args[1].toLowerCase();
        if (mobName.equals("witch") && plugin.getBossListener() != null) {
            if (args.length >= 3) {
                String witchSetting = args[2].toLowerCase();
                if (witchSetting.equals("prevent-vanilla")) {
                    plugin.getBossListener().setWitchPreventVanillaThrow(!plugin.getBossListener().isWitchPreventVanillaThrow());
                }
            }
        }
    }

    private void handleSummonCommand(Player player, String[] args) {
        if (args.length < 2) return;
        String mobName = args[1].toLowerCase();
        Location loc = player.getLocation();
        if (mobName.equals("skeleton") && args.length >= 3 && args[2].equalsIgnoreCase("swamp")) {
            gameManager.spawnBoss(loc, "swamp", 10);
            return;
        }
        if (mobName.equals("spear_zombie") && plugin.getSpearZombie() != null) {
            plugin.getSpearZombie().spawn(loc);
            player.sendMessage(Component.text("⚔ 창 좀비 소환", NamedTextColor.GREEN));
        } else if (mobName.equals("boss_witch")) {
            org.bukkit.entity.Witch w = (org.bukkit.entity.Witch) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.WITCH);
            w.customName(Component.text("§5§l[BOSS] 늪지대의 마녀"));
            w.setCustomNameVisible(true);
            gameManager.addGameMonster(w);
        }
    }

    private void handlePartyCommand(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("gui")) {
            test1.party.AdminPartyGUI.openListGUI(player);
        } else {
            player.sendMessage(Component.text("사용법: /zombie party gui", NamedTextColor.RED));
        }
    }

    private void handleLockCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /zombie lock [플레이어이름]", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("해당 플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
            return;
        }
        UUID tid = target.getUniqueId();
        if (lockedPlayers.contains(tid)) {
            lockedPlayers.remove(tid);
            player.sendMessage(Component.text(target.getName() + "의 파티 아이템 잠금을 해제했습니다.", NamedTextColor.GREEN));
        } else {
            lockedPlayers.add(tid);
            player.sendMessage(Component.text(target.getName() + "의 파티 아이템을 잠갔습니다.", NamedTextColor.RED));
        }
    }

    private void handleClearEffectsCommand(Player player, String[] args) {
        Player target = player;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
                return;
            }
        }

        List<PotionEffectType> effectsToClear = List.of(
                PotionEffectType.REGENERATION,
                PotionEffectType.SATURATION,
                PotionEffectType.STRENGTH,
                PotionEffectType.RESISTANCE,
                PotionEffectType.SPEED,
                PotionEffectType.INSTANT_HEALTH
        );

        int clearedCount = 0;
        for (PotionEffectType effectType : effectsToClear) {
            if (target.hasPotionEffect(effectType)) {
                target.removePotionEffect(effectType);
                clearedCount++;
            }
        }

        if (clearedCount > 0) {
            player.sendMessage(Component.text(target.getName() + "님의 커스텀 아이템 효과를 제거했습니다.", NamedTextColor.GREEN));
            if (!target.equals(player)) {
                target.sendMessage(Component.text("관리자에 의해 커스텀 아이템 효과가 제거되었습니다.", NamedTextColor.YELLOW));
            }
        } else {
            player.sendMessage(Component.text(target.getName() + "님에게 제거할 효과가 없습니다.", NamedTextColor.GRAY));
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("커스텀 아이템 목록") || title.contains("관리자 명령어 가이드")) {
            event.setCancelled(true);
            if (title.contains("커스텀 아이템 목록")) {
                if (!(event.getWhoClicked() instanceof Player player)) return;
                if (event.getClickedInventory() == event.getView().getTopInventory()) {
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
                    if (player.isOp()) {
                        player.getInventory().addItem(clickedItem.clone());
                        player.sendMessage(Component.text("아이템 지급 완료", NamedTextColor.GREEN));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains("커스텀 아이템 목록") || title.contains("관리자 명령어 가이드")) {
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

    private void handleHeatCommand(Player player, String[] args) {
        HeatSystem heatSystem = plugin.getHeatSystem();
        if (heatSystem == null) return;
        if (args.length == 0) {
            boolean enabled = !heatSystem.isEnabled();
            heatSystem.setEnabled(enabled);
            Component statusText = enabled ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED);
            player.sendMessage(Component.text("🌡️ 체온 시스템: ", NamedTextColor.AQUA).append(statusText));
            return;
        }
        String temp = heatSystem.getTemperatureState(player);
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
                if (!player.hasPermission("plugin.round")) {
                    player.sendMessage(Component.text("권한 없음", NamedTextColor.RED));
                    return;
                }
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
            switch (subCommand) {
                case "set" -> targetRound = val;
                case "add" -> targetRound += val;
                case "remove" -> targetRound -= val;
                case "reset" -> targetRound = 1;
            }
        } catch (NumberFormatException e) {
            return;
        }
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("zombie")) {
            if (args.length == 1) {
                return ZOMBIE_SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                switch (sub) {
                    case "item": return ITEM_SUBCOMMANDS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    case "system": return SYSTEM_SUBCOMMANDS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    case "game": return GAME_SUBCOMMANDS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    case "mob": return Collections.singletonList("witch");
                    case "info": return Arrays.stream(EntityType.values()).map(e -> e.name().toLowerCase()).filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    case "region": return REGION_SUBCOMMANDS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    case "summon": return Arrays.asList("spear_zombie", "boss_witch", "skeleton");
                    case "party": return Collections.singletonList("gui");
                    case "lock", "cleareffects": return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    case "dummy": return DUMMY_SUBCOMMANDS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    case "chance": return CHANCE_ITEM_NAMES.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
            }
            if (args.length == 3) {
                String sub = args[0].toLowerCase();
                String sub2 = args[1].toLowerCase();
                if (sub.equals("system") && sub2.equals("oxygen")) return Arrays.asList("list", "add", "delete", "0", "100");
                if (sub.equals("game") && sub2.equals("round")) return ROUND_SUBCOMMANDS;
                if (sub.equals("mob") && sub2.equalsIgnoreCase("witch")) return Arrays.asList("type", "level", "cooldown", "playeronly", "prevent-vanilla");
                if (sub.equals("region") && (sub2.equalsIgnoreCase("save") || sub2.equalsIgnoreCase("remove"))) return new ArrayList<>(plugin.getConfigBiomeSpawnCoords().keySet());
                if (sub.equals("summon") && sub2.equalsIgnoreCase("skeleton")) return List.of("swamp");
                if (sub.equals("dummy") && sub2.equals("spawn")) return Arrays.stream(EntityType.values()).filter(EntityType::isAlive).map(e -> e.name().toLowerCase()).filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                if (sub.equals("chance")) return Arrays.asList("0", "50", "100", "reset");
            }
            if (args.length == 4) {
                String sub = args[0].toLowerCase();
                String sub2 = args[1].toLowerCase();
                String sub3 = args[2].toLowerCase();
                if (sub.equals("mob") && sub2.equals("witch") && sub3.equals("type")) return WITCH_POTION_TYPES;
            }
        }
        return Collections.emptyList();
    }
}