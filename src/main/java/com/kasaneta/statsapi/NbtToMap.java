package com.kasaneta.statsapi;

import net.minecraft.nbt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NbtToMap {
    public static Map<String, Object> convert(CompoundTag tag) {
        Map<String, Object> map = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            map.put(key, convertElement(tag.get(key)));
        }
        return map;
    }

    private static Object convertElement(Tag tag) {
        if (tag instanceof CompoundTag ct) return convert(ct);
        if (tag instanceof ListTag lt) {
            List<Object> list = new ArrayList<>();
            for (Tag t : lt) list.add(convertElement(t));
            return list;
        }
        if (tag instanceof NumericTag nt) return nt.getAsLong();
        if (tag instanceof StringTag st) return st.getAsString();
        if (tag instanceof ByteArrayTag bat) return bat.getAsByteArray();
        if (tag instanceof IntArrayTag iat) return iat.getAsIntArray();
        if (tag instanceof LongArrayTag lat) return lat.getAsLongArray();
        return tag.toString();
    }
}