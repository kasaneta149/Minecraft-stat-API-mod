package com.kasaneta.statsapi;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.UUID;

public class FakeSocialHelper {
    private static Class<?> managerClass;
    private static Class<?> statsClass;
    private static Method getManagerMethod;
    private static Method getStatsMethod;
    private static Method getFollowersMethod;
    private static Method getFollowingMethod;
    private static Method getViewsMethod;
    private static Method getLikesMethod;
    private static Method getPostsMethod;
    private static Method getCommentMethod;
    private static Method getTrendMethod;
    private static boolean initialized = false;

    private static void init() {
        if (initialized) return;
        try {
            managerClass = Class.forName("com.kimiga149.fakesocialsimulator.SocialManager");
            statsClass = Class.forName("com.kimiga149.fakesocialsimulator.PlayerStats");
            getManagerMethod = managerClass.getMethod("get", MinecraftServer.class);
            getStatsMethod = managerClass.getMethod("getStats", UUID.class);
            getFollowersMethod = statsClass.getMethod("getFollowersCount");
            getFollowingMethod = statsClass.getMethod("getFollowingCount");
            getPostsMethod = statsClass.getMethod("getPostsCount");
            getViewsMethod = statsClass.getMethod("getViewCount");
            getLikesMethod = statsClass.getMethod("getLikeCount");
            getCommentMethod = statsClass.getMethod("getCommentCount");

            getTrendMethod = statsClass.getMethod("getTrend");
        } catch (Exception ignored) {}
        initialized = true;
    }

    public static void fetchSocialStats(UUID uuid, MinecraftServer server, DataModels.SocialStatsData target) {
        try {
            init();
            if (getManagerMethod == null || server == null) return;

            Object manager = getManagerMethod.invoke(null, server);
            if (manager == null) return;

            Object stats = getStatsMethod.invoke(manager, uuid);
            if (stats == null) return;

            target.followers_count = (long) getFollowersMethod.invoke(stats);
            target.following_count = (long) getFollowingMethod.invoke(stats);
            target.posts_count = (long) getPostsMethod.invoke(stats);
            target.views_count = (long) getViewsMethod.invoke(stats);
            target.like_count = (long) getLikesMethod.invoke(stats);
            target.comments_count = (long) getCommentMethod.invoke(stats);
            target.trend = (double) getTrendMethod.invoke(stats);
        } catch (Exception ignored) {}
    }
}