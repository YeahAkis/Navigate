package org.notionsmp.navigate;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;

import java.util.EnumSet;
import java.util.Set;

final class Openables {

    private static final boolean[] OPENABLE;
    private static final boolean[] DOOR;
    private static final boolean[] TRAPDOOR;

    static {
        Material[] materials = Material.values();
        OPENABLE = new boolean[materials.length];
        DOOR = new boolean[materials.length];
        TRAPDOOR = new boolean[materials.length];

        Set<Material> doors = EnumSet.noneOf(Material.class);
        doors.addAll(Tag.DOORS.getValues());
        doors.remove(Material.IRON_DOOR);
        Set<Material> trapdoors = EnumSet.noneOf(Material.class);
        trapdoors.addAll(Tag.TRAPDOORS.getValues());
        trapdoors.remove(Material.IRON_TRAPDOOR);
        Set<Material> gates = Tag.FENCE_GATES.getValues();

        for (Material material : doors) {
            DOOR[material.ordinal()] = true;
            OPENABLE[material.ordinal()] = true;
        }
        for (Material material : trapdoors) {
            TRAPDOOR[material.ordinal()] = true;
            OPENABLE[material.ordinal()] = true;
        }
        for (Material material : gates) {
            OPENABLE[material.ordinal()] = true;
        }
    }

    private Openables() {
    }

    static boolean isOpenable(Material material) {
        return OPENABLE[material.ordinal()];
    }

    static boolean isDoor(Material material) {
        return DOOR[material.ordinal()];
    }

    static boolean isTrapdoor(Material material) {
        return TRAPDOOR[material.ordinal()];
    }

    static Sound sound(Material material, boolean open) {
        boolean copper = material.name().contains("COPPER");
        if (DOOR[material.ordinal()]) {
            if (copper) {
                return open ? Sound.BLOCK_COPPER_DOOR_OPEN : Sound.BLOCK_COPPER_DOOR_CLOSE;
            }
            return open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
        }
        if (TRAPDOOR[material.ordinal()]) {
            if (copper) {
                return open ? Sound.BLOCK_COPPER_TRAPDOOR_OPEN : Sound.BLOCK_COPPER_TRAPDOOR_CLOSE;
            }
            return open ? Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE;
        }
        return open ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE;
    }
}
