package org.notionsmp.navigate;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class FollowTask implements Runnable {

    private static final double JUMP_VELOCITY = 0.42;
    private static final double CLIMB_VELOCITY = 0.2;
    private static final double DESCEND_VELOCITY = -0.15;
    private static final double SWIM_VELOCITY = 0.12;
    private static final double JUMP_RANGE = 1.6;
    private static final double OFF_PATH_SQ = 9;
    private static final float MAX_TURN = 30f;
    private static final float MAX_PITCH_TURN = 12f;
    private static final float MAX_LOOK_PITCH = 25f;
    private static final int LOOKAHEAD = 8;
    private static final int LOOK_AHEAD = 3;
    private static final int STUCK_TICKS = 40;

    private final Navigate plugin;
    private final Navigation navigation;
    private final DoorOpener doors;
    private final double walkSpeed;

    private List<Location> path;
    private int index;
    private Location lastPosition;
    private int stuckTicks;

    public FollowTask(Navigate plugin, Navigation navigation) {
        this.plugin = plugin;
        this.navigation = navigation;
        this.walkSpeed = plugin.getConfig().getDouble("follow.speed", 0.22);
        this.doors = plugin.getConfig().getBoolean("follow.open-doors", true)
                ? new DoorOpener(plugin, plugin.getConfig().getBoolean("follow.close-doors", true))
                : null;
    }

    void release() {
        if (doors != null) {
            doors.release();
        }
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
            if (Math.abs(position.getPitch()) > 0.5f) {
                player.setRotation(position.getYaw(), position.getPitch() + clamp(-position.getPitch(), MAX_PITCH_TURN));
            }
            return;
        }

        if (doors != null) {
            doors.closePassed(position);
            doors.openAround(position, path.get(index));
            doors.openAround(position, path.get(index + 1));
            if (index + 2 <= last) {
                doors.openAround(position, path.get(index + 2));
            }
        }

        Location next = path.get(index + 1);
        double dx = next.getBlockX() + 0.5 - position.getX();
        double dz = next.getBlockZ() + 0.5 - position.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double speed = Math.min(walkSpeed, horizontal);
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

        face(player, position, horizontal > 0.3 ? dx : Double.NaN, dz, last);

        player.setVelocity(new Vector(vx, vy, vz));
    }

    private void face(Player player, Location position, double dx, double dz, int last) {
        float yaw = position.getYaw();
        if (!Double.isNaN(dx)) {
            float target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            yaw += clamp(wrap(target - yaw), MAX_TURN);
        }

        Location ahead = path.get(Math.min(index + LOOK_AHEAD, last));
        double lookX = ahead.getBlockX() + 0.5 - position.getX();
        double lookZ = ahead.getBlockZ() + 0.5 - position.getZ();
        double lookDistance = Math.sqrt(lookX * lookX + lookZ * lookZ);
        double rise = ahead.getBlockY() - path.get(index).getBlockY();
        float wanted = lookDistance > 1.0 ? (float) -Math.toDegrees(Math.atan2(rise, lookDistance)) : 0f;
        wanted = clamp(wanted, MAX_LOOK_PITCH);
        float pitch = position.getPitch() + clamp(wanted - position.getPitch(), MAX_PITCH_TURN);

        player.setRotation(yaw, pitch);
    }

    private static float wrap(float angle) {
        return ((angle % 360f) + 540f) % 360f - 180f;
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
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
