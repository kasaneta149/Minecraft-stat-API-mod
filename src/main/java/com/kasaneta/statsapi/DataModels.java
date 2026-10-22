package com.kasaneta.statsapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataModels {
    public static class RootData {
        public WorldData world = new WorldData();
        public List<PlayerData> players = new ArrayList<>();
        public List<Map<String, Object>> entities = new ArrayList<>();
        public List<Map<String, Object>> nbt_blocks = new ArrayList<>();
        public List<StorageData> storage = new ArrayList<>();
        public SettingsData settings = new SettingsData();
    }

    public static class WorldData {
        public long ticks;
        public int days;
        public List<Map<String, Object>> variables = new ArrayList<>();
        public List<Map<String, Object>> scoreboards = new ArrayList<>();
        public Map<String, Object> level_data = new HashMap<>();
    }

    public static class PlayerData {
        public String uuid;
        public String name;
        public boolean isOnline;
        public Map<String, Object> attributes = new HashMap<>();
        public StatsData stats = new StatsData();
        public DetailedStatsData detailed_stats = new DetailedStatsData();
        public SocialStatsData social_stats = new SocialStatsData();
        public Map<String, Object> variables = new HashMap<>();
        public Map<String, Object> player_data = new HashMap<>();
    }

    public static class SocialStatsData {
        public long comments_count = 0;
        public long like_count = 0;
        public long views_count = 0;
        public long followers_count = 0;
        public long following_count = 0;
        public long posts_count = 0;
        public double trend = 0.0;
    }

    public static class StatsData {
        public long blocks_mined;
        public long mobs_killed;
        public long current_xp_point;
        public long total_xp_point;
        public long current_level;
        public long items_picked_up;
        public long items_crafted;
        public long items_broken;
        public long items_used;
        public long items_dropped;
        public long total_distance;
        public long death_count;
        public long damage_dealt;
        public long damage_taken;
        public long play_time;
        public long time_since_death;
    }

    public static class DetailedStatsData {
        public List<StatItem> mined_blocks = new ArrayList<>();
        public List<StatItem> killed_mobs = new ArrayList<>();
        public List<StatItem> pickedup_items = new ArrayList<>();
    }

    public static class StatItem {
        public String name;
        public String mod;
        public long count;

        public StatItem(String name, String mod, long count) {
            this.name = name;
            this.mod = mod;
            this.count = count;
        }
    }

    public static class StorageData {
        public String name;
        public String mod;
        public long count;

        public StorageData(String name, String mod, long count) {
            this.name = name;
            this.mod = mod;
            this.count = count;
        }
    }

    public static class SettingsData {
        public boolean mined_list;
        public boolean killed_list;
        public boolean pickedup_list;
        public boolean ae2_storage_enabled;
        public int update_rate;
    }
}