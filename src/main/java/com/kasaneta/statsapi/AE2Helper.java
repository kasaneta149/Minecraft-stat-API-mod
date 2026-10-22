package com.kasaneta.statsapi;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Set;

public class AE2Helper {
    public static void fetchStorage(List<DataModels.StorageData> storageList, String configLine, MinecraftServer serverInstance, Set<String> trackedItems) {
        try {
            String[] parts = configLine.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(parts[3]));

            ServerLevel level = serverInstance.getLevel(dim);
            if (level == null) return;

            BlockPos pos = new BlockPos(x, y, z);
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof IActionHost host)) return;

            IGridNode node = host.getActionableNode();
            if (node == null || node.getGrid() == null) return;

            IStorageService storageService = node.getGrid().getService(IStorageService.class);
            if (storageService == null) return;

            MEStorage inventory = storageService.getInventory();
            KeyCounter counter = inventory.getAvailableStacks();

            boolean hasFilter = !trackedItems.isEmpty();

            for (var entry : counter) {
                AEKey key = entry.getKey();
                ResourceLocation id = key.getId();
                
                if (hasFilter && !trackedItems.contains(id.toString())) {
                    continue;
                }

                long amount = entry.getLongValue();
                storageList.add(new DataModels.StorageData(
                        id.getPath(),
                        id.getNamespace(),
                        amount
                ));
            }
        } catch (Exception ignored) {}
    }
}