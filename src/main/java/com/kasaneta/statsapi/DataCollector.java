package com.kasaneta.statsapi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataCollector {
    private static volatile DataModels.RootData currentData = new DataModels.RootData();
    private static MinecraftServer serverInstance;
    private static int tickCounter = 0;
    private static boolean ae2Loaded = false;
    private static boolean fakeSocialLoaded = false;
    private static final AtomicBoolean isProcessing = new AtomicBoolean(false);

    private static final Map<UUID, DataModels.PlayerData> offlinePlayerCache = new ConcurrentHashMap<>();

    private record CachedStat<T>(Stat<T> stat, String path, String namespace) {}
    private static final List<CachedStat<Block>> CACHED_BLOCKS = new ArrayList<>();
    private static final List<CachedStat<EntityType<?>>> CACHED_ENTITIES = new ArrayList<>();
    private static final List<CachedStat<Item>> CACHED_ITEMS = new ArrayList<>();
    private static final List<Stat<Item>> CACHED_CRAFTED = new ArrayList<>();
    private static final List<Stat<Item>> CACHED_BROKEN = new ArrayList<>();
    private static final List<Stat<Item>> CACHED_USED = new ArrayList<>();
    private static final List<Stat<Item>> CACHED_DROPPED = new ArrayList<>();
    private static final List<Stat<ResourceLocation>> CACHED_DISTANCE_STATS = new ArrayList<>();

    private static volatile byte[] cachedJsonBytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    public static byte[] getCachedJsonBytes() {
        return cachedJsonBytes;
    }

    private static class OnlinePlayerSnapshot {
        UUID uuid;
        String name;
        int level;
        int totalXp;
        float xpProgress;
        int xpNeeded;
        Map<String, Double> attributes = new HashMap<>();
        DataModels.PlayerData extractedData;
        CompoundTag nbtTag;
    }

    private static class ServerSnapshot {
        long ticks;
        int days;
        String levelName;
        String difficultyKey;
        boolean hardcore;
        boolean raining;
        boolean thundering;
        long clearWeatherTime;
        long rainTime;
        long thunderTime;
        Map<String, String> gameRules = new HashMap<>();
        Map<String, CompoundTag> commandStorage = new HashMap<>();
        CompoundTag levelAttachments;
        List<Map<String, Object>> scoreboards = new ArrayList<>();
        List<OnlinePlayerSnapshot> onlinePlayers = new ArrayList<>();
        List<CompoundTag> nbtBlocks = new ArrayList<>();
        List<CompoundTag> entities = new ArrayList<>();
        List<DataModels.StorageData> storageData = new ArrayList<>();
        
        Set<String> ae2TrackedItems = new HashSet<>();
        
        boolean minedList;
        boolean killedList;
        boolean pickedupList;
        boolean ae2Enabled;
        int updateRate;
    }

    private static long calculateTotalXp(long level, float xpProgress) {
        long baseTotal;
        if (level <= 16) {
            baseTotal = level * level + 6 * level;
        } else if (level <= 31) {
            baseTotal = (5 * level * level - 81 * level) / 2 + 360;
        } else {
            baseTotal = (9 * level * level - 325 * level) / 2 + 2220;
        }

        long xpNeeded = level >= 31 ? 112 + (level - 31) * 9 : (level >= 16 ? 37 + (level - 16) * 5 : 7 + level * 2);
        return baseTotal + (long)(xpProgress * xpNeeded);
    }

    public static void init(MinecraftServer server) {
        serverInstance = server;
        ae2Loaded = ModList.get().isLoaded("ae2");
        fakeSocialLoaded = ModList.get().isLoaded("fakesocialsimulator");
        initRegistryCache();
        CompletableFuture.runAsync(() -> buildOfflineCache(server));
    }

    private static void initRegistryCache() {
        CACHED_BLOCKS.clear();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(block);
            CACHED_BLOCKS.add(new CachedStat<>(Stats.BLOCK_MINED.get(block), loc.getPath(), loc.getNamespace()));
        }

        CACHED_ENTITIES.clear();
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation loc = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            CACHED_ENTITIES.add(new CachedStat<>(Stats.ENTITY_KILLED.get(entityType), loc.getPath(), loc.getNamespace()));
        }

        CACHED_ITEMS.clear();
        CACHED_CRAFTED.clear();
        CACHED_BROKEN.clear();
        CACHED_USED.clear();
        CACHED_DROPPED.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation loc = BuiltInRegistries.ITEM.getKey(item);
            CACHED_ITEMS.add(new CachedStat<>(Stats.ITEM_PICKED_UP.get(item), loc.getPath(), loc.getNamespace()));
            CACHED_CRAFTED.add(Stats.ITEM_CRAFTED.get(item));
            CACHED_BROKEN.add(Stats.ITEM_BROKEN.get(item));
            CACHED_USED.add(Stats.ITEM_USED.get(item));
            CACHED_DROPPED.add(Stats.ITEM_DROPPED.get(item));
        }

        CACHED_DISTANCE_STATS.clear();
        for (ResourceLocation key : BuiltInRegistries.CUSTOM_STAT.keySet()) {
            if (key.getPath().endsWith("_one_cm")) {
                ResourceLocation statValue = BuiltInRegistries.CUSTOM_STAT.get(key);
                if (statValue != null) {
                    CACHED_DISTANCE_STATS.add(Stats.CUSTOM.get(statValue));
                }
            }
        }
    }

    public static DataModels.RootData getCurrentData() {
        return currentData;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        offlinePlayerCache.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompletableFuture.runAsync(() -> {
                OnlinePlayerSnapshot snap = snapshotPlayer(player);
                DataModels.PlayerData pData = processOnlinePlayer(snap, Config.MINED_LIST.get(), Config.KILLED_LIST.get(), Config.PICKEDUP_LIST.get());
                pData.isOnline = false;
                offlinePlayerCache.put(player.getUUID(), pData);
            });
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (serverInstance == null) return;

        tickCounter++;
        if (tickCounter >= Config.UPDATE_RATE.get()) {
            tickCounter = 0;

            if (isProcessing.compareAndSet(false, true)) {
                try {
                    ServerSnapshot snapshot = captureMainThreadSnapshot();

                    CompletableFuture.runAsync(() -> {
                        try {
                            processSnapshotAsync(snapshot);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            isProcessing.set(false);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    isProcessing.set(false);
                }
            }
        }
    }

    private static ServerSnapshot captureMainThreadSnapshot() {
        ServerSnapshot snap = new ServerSnapshot();
        ServerLevel overworld = serverInstance.overworld();

        snap.minedList = Config.MINED_LIST.get();
        snap.killedList = Config.KILLED_LIST.get();
        snap.pickedupList = Config.PICKEDUP_LIST.get();
        snap.ae2Enabled = Config.AE2_STORAGE_ENABLED.get();
        snap.updateRate = Config.UPDATE_RATE.get();

        for (Object itemObj : Config.AE2_TRACKED_ITEMS.get()) {
            if (itemObj instanceof String str) {
                snap.ae2TrackedItems.add(str);
            }
        }

        snap.ticks = overworld.getGameTime();
        snap.days = (int) (overworld.getDayTime() / 24000L);

        var worldData = serverInstance.getWorldData();
        snap.levelName = worldData.getLevelName();
        snap.difficultyKey = worldData.getDifficulty().getKey();
        snap.hardcore = worldData.isHardcore();

        snap.raining = overworld.isRaining();
        snap.thundering = overworld.isThundering();

        if (overworld.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData sld) {
            snap.clearWeatherTime = sld.getClearWeatherTime();
            snap.rainTime = sld.getRainTime();
            snap.thunderTime = sld.getThunderTime();
        }

        overworld.getGameRules().visitGameRuleTypes(new net.minecraft.world.level.GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends net.minecraft.world.level.GameRules.Value<T>> void visit(net.minecraft.world.level.GameRules.Key<T> key, net.minecraft.world.level.GameRules.Type<T> type) {
                snap.gameRules.put(key.getId(), overworld.getGameRules().getRule(key).toString());
            }
        });

        try {
            serverInstance.getCommandStorage().keys().forEach(key -> {
                snap.commandStorage.put(key.toString(), serverInstance.getCommandStorage().get(key));
            });
        } catch (Exception ignored) {}

        try {
            snap.levelAttachments = overworld.serializeAttachments(serverInstance.registryAccess());
        } catch (Exception ignored) {}

        try {
            var scoreboard = serverInstance.getScoreboard();
            for (var objective : scoreboard.getObjectives()) {
                Map<String, Object> objMap = new HashMap<>();
                objMap.put("name", objective.getName());
                objMap.put("display_name", objective.getDisplayName().getString());
                objMap.put("criteria", objective.getCriteria().getName());

                List<Map<String, Object>> scoresList = new ArrayList<>();
                for (ScoreHolder holder : scoreboard.getTrackedPlayers()) {
                    ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, objective);
                    if (info != null) {
                        Map<String, Object> scoreMap = new HashMap<>();
                        scoreMap.put("holder", holder.getScoreboardName());
                        scoreMap.put("value", info.value());
                        scoresList.add(scoreMap);
                    }
                }
                objMap.put("scores", scoresList);
                snap.scoreboards.add(objMap);
            }
        } catch (Exception ignored) {}

        for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
            snap.onlinePlayers.add(snapshotPlayer(player));
        }

        if (fakeSocialLoaded) {
            for (Map.Entry<UUID, DataModels.PlayerData> entry : offlinePlayerCache.entrySet()) {
                FakeSocialHelper.fetchSocialStats(entry.getKey(), serverInstance, entry.getValue().social_stats);
            }
        }

        try {
            for (String configLine : Config.NBT_BLOCKS.get()) {
                String[] parts = configLine.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(parts[3]));

                ServerLevel level = serverInstance.getLevel(dim);
                if (level != null) {
                    BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
                    if (be != null) {
                        snap.nbtBlocks.add(be.saveWithFullMetadata(level.registryAccess()));
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            for (String uuidStr : Config.ENTITIES.get()) {
                UUID uuid = UUID.fromString(uuidStr);
                for (ServerLevel level : serverInstance.getAllLevels()) {
                    Entity e = level.getEntity(uuid);
                    if (e != null) {
                        CompoundTag tag = new CompoundTag();
                        e.saveWithoutId(tag);
                        snap.entities.add(tag);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            if (snap.ae2Enabled && ae2Loaded) {
                for (String configLine : Config.AE2_COORDS.get()) {
                    AE2Helper.fetchStorage(snap.storageData, configLine, serverInstance, snap.ae2TrackedItems);
                }
            }
        } catch (Exception ignored) {}

        return snap;
    }

    private static OnlinePlayerSnapshot snapshotPlayer(ServerPlayer player) {
        OnlinePlayerSnapshot p = new OnlinePlayerSnapshot();
        p.uuid = player.getUUID();
        p.name = player.getName().getString();
        p.level = player.experienceLevel;
        p.totalXp = player.totalExperience;
        p.xpProgress = player.experienceProgress;
        p.xpNeeded = player.getXpNeededForNextLevel();

        for (AttributeInstance attr : player.getAttributes().getSyncableAttributes()) {
            p.attributes.put(BuiltInRegistries.ATTRIBUTE.getKey(attr.getAttribute().value()).toString(), attr.getValue());
        }

        p.nbtTag = new CompoundTag();
        player.saveWithoutId(p.nbtTag);

        p.extractedData = new DataModels.PlayerData();
        extractStatsOptimized(player.getStats(), p.extractedData, Config.MINED_LIST.get(), Config.KILLED_LIST.get(), Config.PICKEDUP_LIST.get());

        if (fakeSocialLoaded) {
            FakeSocialHelper.fetchSocialStats(p.uuid, serverInstance, p.extractedData.social_stats);
        }

        return p;
    }

    private static void processSnapshotAsync(ServerSnapshot snap) {
        DataModels.RootData newData = new DataModels.RootData();

        newData.settings.mined_list = snap.minedList;
        newData.settings.killed_list = snap.killedList;
        newData.settings.pickedup_list = snap.pickedupList;
        newData.settings.ae2_storage_enabled = snap.ae2Enabled;
        newData.settings.update_rate = snap.updateRate;

        newData.world.ticks = snap.ticks;
        newData.world.days = snap.days;

        newData.world.level_data.put("name", snap.levelName);
        newData.world.level_data.put("difficulty", snap.difficultyKey);
        newData.world.level_data.put("hardcore", snap.hardcore);
        newData.world.level_data.put("raining", snap.raining);
        newData.world.level_data.put("thundering", snap.thundering);
        newData.world.level_data.put("clear_weather_time", snap.clearWeatherTime);
        newData.world.level_data.put("rain_time", snap.rainTime);
        newData.world.level_data.put("thunder_time", snap.thunderTime);
        newData.world.level_data.put("gamerules", snap.gameRules);

        Map<String, Object> modVariables = new HashMap<>();
        Map<String, Object> commandStorageData = new HashMap<>();
        snap.commandStorage.forEach((k, v) -> commandStorageData.put(k, NbtToMap.convert(v)));
        modVariables.put("command_storage", commandStorageData);

        if (snap.levelAttachments != null && !snap.levelAttachments.isEmpty()) {
            modVariables.put("world_attachments", NbtToMap.convert(snap.levelAttachments));
        }
        newData.world.level_data.put("mod_variables", modVariables);
        newData.world.scoreboards = snap.scoreboards;

        for (OnlinePlayerSnapshot playerSnap : snap.onlinePlayers) {
            newData.players.add(processOnlinePlayer(playerSnap, snap.minedList, snap.killedList, snap.pickedupList));
        }

        newData.players.addAll(offlinePlayerCache.values());

        for (CompoundTag tag : snap.nbtBlocks) {
            newData.nbt_blocks.add(NbtToMap.convert(tag));
        }

        for (CompoundTag tag : snap.entities) {
            newData.entities.add(NbtToMap.convert(tag));
        }

        newData.storage = snap.storageData;

        currentData = newData;

        String jsonString = com.kasaneta.statsapi.WebServer.GSON.toJson(newData);
        cachedJsonBytes = jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static DataModels.PlayerData processOnlinePlayer(OnlinePlayerSnapshot snap, boolean minedList, boolean killedList, boolean pickedupList) {
        DataModels.PlayerData pData = snap.extractedData != null ? snap.extractedData : new DataModels.PlayerData();
        pData.uuid = snap.uuid.toString();
        pData.name = snap.name;
        pData.isOnline = true;

        pData.stats.current_level = snap.level;
        pData.stats.total_xp_point = calculateTotalXp(snap.level, snap.xpProgress);
        pData.stats.current_xp_point = (long) (snap.xpProgress * snap.xpNeeded);
        pData.attributes.putAll(snap.attributes);

        pData.player_data = NbtToMap.convert(snap.nbtTag);

        if (snap.nbtTag.contains("neoforge:attachments")) {
            pData.variables = NbtToMap.convert(snap.nbtTag.getCompound("neoforge:attachments"));
        } else if (snap.nbtTag.contains("ForgeData")) {
            pData.variables = NbtToMap.convert(snap.nbtTag.getCompound("ForgeData"));
        }

        return pData;
    }

    private static void extractStatsOptimized(ServerStatsCounter stats, DataModels.PlayerData pData, boolean minedList, boolean killedList, boolean pickedupList) {
        long totalMined = 0;
        long totalKilled = 0;
        long totalPickedUp = 0;
        long totalCrafted = 0;
        long totalBroken = 0;
        long totalUsed = 0;
        long totalDropped = 0;

        for (int i = 0; i < CACHED_BLOCKS.size(); i++) {
            CachedStat<Block> cs = CACHED_BLOCKS.get(i);
            int count = stats.getValue(cs.stat);
            if (count > 0) {
                totalMined += count;
                if (minedList) {
                    pData.detailed_stats.mined_blocks.add(new DataModels.StatItem(cs.path, cs.namespace, count));
                }
            }
        }

        for (int i = 0; i < CACHED_ENTITIES.size(); i++) {
            CachedStat<EntityType<?>> cs = CACHED_ENTITIES.get(i);
            int count = stats.getValue(cs.stat);
            if (count > 0) {
                totalKilled += count;
                if (killedList) {
                    pData.detailed_stats.killed_mobs.add(new DataModels.StatItem(cs.path, cs.namespace, count));
                }
            }
        }

        for (int i = 0; i < CACHED_ITEMS.size(); i++) {
            CachedStat<Item> cs = CACHED_ITEMS.get(i);
            int count = stats.getValue(cs.stat);
            if (count > 0) {
                totalPickedUp += count;
                if (pickedupList) {
                    pData.detailed_stats.pickedup_items.add(new DataModels.StatItem(cs.path, cs.namespace, count));
                }
            }

            totalCrafted += stats.getValue(CACHED_CRAFTED.get(i));
            totalBroken += stats.getValue(CACHED_BROKEN.get(i));
            totalUsed += stats.getValue(CACHED_USED.get(i));
            totalDropped += stats.getValue(CACHED_DROPPED.get(i));
        }

        pData.stats.blocks_mined = totalMined;
        pData.stats.mobs_killed = totalKilled;
        pData.stats.items_picked_up = totalPickedUp;
        pData.stats.items_crafted = totalCrafted;
        pData.stats.items_broken = totalBroken;
        pData.stats.items_used = totalUsed;
        pData.stats.items_dropped = totalDropped;

        pData.stats.death_count = stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
        pData.stats.damage_dealt = stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_DEALT));
        pData.stats.damage_taken = stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_TAKEN));
        pData.stats.play_time = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        pData.stats.time_since_death = stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));

        long totalDistance = 0;
        for (int i = 0; i < CACHED_DISTANCE_STATS.size(); i++) {
            totalDistance += stats.getValue(CACHED_DISTANCE_STATS.get(i));
        }
        pData.stats.total_distance = totalDistance;
    }

    private static void buildOfflineCache(MinecraftServer server) {
        try {
            offlinePlayerCache.clear();

            File playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
            File worldDir = playerDataDir.getParentFile();
            File statsDir = new File(worldDir, "stats");

            if (!playerDataDir.exists() || !playerDataDir.isDirectory()) return;

            File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
            if (files == null) return;

            boolean minedList = Config.MINED_LIST.get();
            boolean killedList = Config.KILLED_LIST.get();
            boolean pickedupList = Config.PICKEDUP_LIST.get();

            for (File file : files) {
                String uuidStr = file.getName().replace(".dat", "");
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (server.getPlayerList().getPlayer(uuid) != null) continue;

                    DataModels.PlayerData pData = new DataModels.PlayerData();
                    pData.uuid = uuid.toString();
                    pData.isOnline = false;

                    var profileCache = server.getProfileCache();
                    if (profileCache != null) {
                        var profileOpt = profileCache.get(uuid);
                        pData.name = profileOpt.map(com.mojang.authlib.GameProfile::getName).orElse(uuid.toString());
                    } else {
                        pData.name = uuid.toString();
                    }

                    if (file.exists()) {
                        CompoundTag pTag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
                        pData.player_data = NbtToMap.convert(pTag);

                        int level = pTag.getInt("XpLevel");
                        float xpP = pTag.getFloat("XpP");
                        int xpNeeded = level >= 31 ? 112 + (level - 31) * 9 : (level >= 16 ? 37 + (level - 16) * 5 : 7 + level * 2);

                        pData.stats.current_level = level;
                        pData.stats.total_xp_point = calculateTotalXp(level, xpP);
                        pData.stats.current_xp_point = (long) (xpP * xpNeeded);

                        if (pTag.contains("attributes", 9)) {
                            ListTag attrList = pTag.getList("attributes", 10);
                            for (int i = 0; i < attrList.size(); i++) {
                                CompoundTag attrTag = attrList.getCompound(i);
                                String key = attrTag.contains("id") ? attrTag.getString("id") : attrTag.getString("Name");
                                double val = attrTag.contains("base") ? attrTag.getDouble("base") : attrTag.getDouble("Base");
                                if (key != null && !key.isEmpty()) {
                                    pData.attributes.put(key, val);
                                }
                            }
                        }

                        if (pTag.contains("neoforge:attachments")) {
                            pData.variables = NbtToMap.convert(pTag.getCompound("neoforge:attachments"));
                        } else if (pTag.contains("ForgeData")) {
                            pData.variables = NbtToMap.convert(pTag.getCompound("ForgeData"));
                        }
                    }

                    File statsFile = new File(statsDir, uuid.toString() + ".json");
                    if (statsFile.exists()) {
                        ServerStatsCounter stats = new ServerStatsCounter(server, statsFile);
                        extractStatsOptimized(stats, pData, minedList, killedList, pickedupList);
                    }

                    if (fakeSocialLoaded) {
                        FakeSocialHelper.fetchSocialStats(uuid, server, pData.social_stats);
                    }

                    offlinePlayerCache.put(uuid, pData);

                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}