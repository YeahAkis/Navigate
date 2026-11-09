package org.notionsmp.navigate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class DoorOpener {

    private static final double REACH_SQ = 2.6 * 2.6;
    private static final double CLOSE_SQ = 3.6 * 3.6;

    private final Navigate plugin;
    private final boolean closeBehind;
    private final List<Block> opened = new ArrayList<>();

    DoorOpener(Navigate plugin, boolean closeBehind) {
        this.plugin = plugin;
        this.closeBehind = closeBehind;
    }

    void openAround(Location position, Location node) {
        openCell(position, node.getBlockX(), node.getBlockY(), node.getBlockZ());
        openCell(position, node.getBlockX(), node.getBlockY() + 1, node.getBlockZ());
    }

    void closePassed(Location position) {
        if (!closeBehind || opened.isEmpty()) {
            return;
        }
        Iterator<Block> iterator = opened.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (distanceSquared(position, block) > CLOSE_SQ) {
                set(block, false);
                iterator.remove();
            }
        }
    }

    void release() {
        if (opened.isEmpty()) {
            return;
        }
        List<Block> pending = new ArrayList<>(opened);
        opened.clear();
        for (Block block : pending) {
            if (!closeBehind) {
                continue;
            }
            if (plugin.isEnabled()) {
                plugin.getServer().getRegionScheduler().run(plugin, block.getLocation(), task -> set(block, false));
            } else {
                set(block, false);
            }
        }
    }

    private void openCell(Location position, int x, int y, int z) {
        Block block = position.getWorld().getBlockAt(x, y, z);
        if (!Openables.isOpenable(block.getType()) || distanceSquared(position, block) > REACH_SQ) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Openable openable) || openable.isOpen()) {
            return;
        }
        set(block, true);
        if (closeBehind) {
            opened.add(block);
        }
    }

    private static void set(Block block, boolean open) {
        Material type = block.getType();
        if (!Openables.isOpenable(type)) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Openable openable) || openable.isOpen() == open) {
            return;
        }
        openable.setOpen(open);
        block.setBlockData(data);
        if (Openables.isDoor(type) && data instanceof Bisected bisected) {
            Block other = block.getRelative(bisected.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
            if (other.getType() == type && other.getBlockData() instanceof Openable otherOpenable) {
                otherOpenable.setOpen(open);
                other.setBlockData((BlockData) otherOpenable);
            }
        }
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Openables.sound(type, open), SoundCategory.BLOCKS, 1.0f, 0.9f + (float) Math.random() * 0.2f);
    }

    private static double distanceSquared(Location position, Block block) {
        double dx = block.getX() + 0.5 - position.getX();
        double dy = block.getY() + 0.5 - position.getY();
        double dz = block.getZ() + 0.5 - position.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
