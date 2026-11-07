package org.notionsmp.navigate;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class FollowTask extends BukkitRunnable {

    private static final double JUMP_VELOCITY = 0.42;
    private static final double CLIMB_VELOCITY = 0.2;
    private static final double DESCEND_VELOCITY = -0.15;
    private static final double SWIM_VELOCITY = 0.12;
    private static final double JUMP_RANGE = 1.6;
    private static final double OFF_PATH_SQ = 9;
    private static final int LOOKAHEAD = 8;
    private static final int STUCK_TICKS = 40;

    private final Navigate plugin;
    private final Navigation navigation;

    private List<Location> path;
    private int index;
    private Location lastPosition;
    private int stuckTicks;

    public FollowTask(Navigate plugin, Navigation navigation) {
        this.plugin = plugin;
        this.navigation = navigation;
    }

    @Override
    public void run() {
        Player player = navigation.getPlayer();
        if (!player.isOnline() || player.isSneaking()) {
            return;
        }

        List<Location> current = navigation.getCurrentPathInternal();
        if (current == null || current.size() < 2) {
            return;
        }

        Location position = player.getLocation();
        if (!position.getWorld().equals(current.get(0).getWorld())) {
            return;
        }

        int last = current.size() - 1;
        if (current != path) {
            path = current;
            index = nearest(position, 0, last);
        } else {
            index = nearest(position, index, Math.min(index + LOOKAHEAD, last));
        }

        if (distanceSquared(position, path.get(index)) > OFF_PATH_SQ) {
            navigation.requestReplan();
            return;
        }

        if (trackStuck(position)) {
            navigation.requestReplan();
            return;
        }

        if (index >= last) {
            return;
        }

        Location next = path.get(index + 1);
        double dx = next.getBlockX() + 0.5 - position.getX();
        double dz = next.getBlockZ() + 0.5 - position.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double speed = Math.min(plugin.getConfig().getDouble("follow.speed", 0.22), horizontal);
        double vx = horizontal > 1.0E-4 ? dx / horizontal * speed : 0;
        double vz = horizontal > 1.0E-4 ? dz / horizontal * speed : 0;
        double vy = player.getVelocity().getY();

        int rise = next.getBlockY() - position.getBlockY();
        Block block = position.getBlock();

        if (Tag.CLIMBABLE.isTagged(block.getType())) {
            if (rise > 0) {
                vy = CLIMB_VELOCITY;
            } else if (rise < 0) {
                vy = DESCEND_VELOCITY;
            } else {
                vy = Math.max(vy, 0);
            }
        } else if (block.isLiquid()) {
            if (rise >= 0) {
                vy = Math.max(vy, SWIM_VELOCITY);
            }
        } else if (rise > 0 && player.isOnGround() && horizontal < JUMP_RANGE) {
            vy = JUMP_VELOCITY;
        }

        player.setVelocity(new Vector(vx, vy, vz));
    }

    private boolean trackStuck(Location position) {
        if (lastPosition != null && lastPosition.distanceSquared(position) < 0.0025) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPosition = position;
        if (stuckTicks > STUCK_TICKS) {
            stuckTicks = 0;
            return true;
        }
        return false;
    }

    private int nearest(Location position, int from, int to) {
        int best = from;
        double bestDistance = Double.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            double distance = distanceSquared(position, path.get(i));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static double distanceSquared(Location position, Location node) {
        double dx = node.getBlockX() + 0.5 - position.getX();
        double dy = node.getBlockY() - position.getY();
        double dz = node.getBlockZ() + 0.5 - position.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
