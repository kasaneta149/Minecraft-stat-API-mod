package com.kasaneta.statsapi;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue PORT;
    public static final ModConfigSpec.IntValue UPDATE_RATE;
    
    public static final ModConfigSpec.BooleanValue MINED_LIST;
    public static final ModConfigSpec.BooleanValue KILLED_LIST;
    public static final ModConfigSpec.BooleanValue PICKEDUP_LIST;
    public static final ModConfigSpec.BooleanValue AE2_STORAGE_ENABLED;
    
    public static final ModConfigSpec.ConfigValue<List<? extends String>> AE2_COORDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> AE2_TRACKED_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NBT_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITIES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("Server");
        PORT = builder.defineInRange("port", 11700, 1024, 65535);
        UPDATE_RATE = builder.defineInRange("update_rate_ticks", 80, 1, 1200);
        builder.pop();

        builder.push("Modules");
        MINED_LIST = builder.define("mined_list", false);
        KILLED_LIST = builder.define("killed_list", false);
        PICKEDUP_LIST = builder.define("pickedup_list", false);
        AE2_STORAGE_ENABLED = builder.define("ae2_storage_enabled", true);
        builder.pop();

        builder.push("Targets");
        AE2_COORDS = builder.defineList("ae2_coords", List.of("0,64,0,minecraft:overworld"), o -> o instanceof String);
        
        // NOUVEAU : Filtre des items AE2
        AE2_TRACKED_ITEMS = builder.defineList("ae2_tracked_items", List.of("minecraft:iron_ingot", "minecraft:diamond"), o -> o instanceof String);
        
        NBT_BLOCKS = builder.defineList("nbt_blocks", List.of(), o -> o instanceof String);
        ENTITIES = builder.defineList("entities", List.of(), o -> o instanceof String);
        builder.pop();

        SPEC = builder.build();
    }
}