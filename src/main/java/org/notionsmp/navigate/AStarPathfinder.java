package org.notionsmp.navigate;

import com.destroystokyo.paper.MaterialSetTag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public class AStarPathfinder {

    private static final boolean[] PASSABLE;
    private static final boolean[] SOLID;
    private static final boolean[] CLIMBABLE;

    static {
        Set<Material> passable = EnumSet.of(
                Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
                Material.WATER, Material.LAVA,
                Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
                Material.COBWEB, Material.TRIPWIRE,
                Material.TORCH, Material.WALL_TORCH, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH,
                Material.LEVER,
                Material.SUGAR_CANE, Material.KELP, Material.SEAGRASS, Material.TALL_SEAGRASS,
                Material.REDSTONE_WIRE, Material.REPEATER, Material.COMPARATOR,
                Material.SNOW, Material.BAMBOO_SAPLING,
                Material.SWEET_BERRY_BUSH, Material.GLOW_LICHEN, Material.SCULK_VEIN,
                Material.SMALL_DRIPLEAF
        );
        Material leafLitter = Material.matchMaterial("LEAF_LITTER");
        if (leafLitter != null) {
            passable.add(leafLitter);
        }
        passable.addAll(MaterialSetTag.BUTTONS.getValues());
        passable.addAll(MaterialSetTag.FLOWERS.getValues());
        passable.addAll(MaterialSetTag.FLOWER_POTS.getValues());
        passable.addAll(MaterialSetTag.PRESSURE_PLATES.getValues());
        passable.addAll(MaterialSetTag.WOOL_CARPETS.getValues());
        passable.addAll(MaterialSetTag.RAILS.getValues());
        passable.addAll(MaterialSetTag.CLIMBABLE.getValues());
        for (Material material : Material.values()) {
            if (!material.isLegacy() && Openables.isOpenable(material)) {
                passable.add(material);
            }
        }

        Material[] materials = Material.values();
        PASSABLE = new boolean[materials.length];
        SOLID = new boolean[materials.length];
        CLIMBABLE = new boolean[materials.length];
        Set<Material> climbable = MaterialSetTag.CLIMBABLE.getValues();
        for (Material material : materials) {
            if (material.isLegacy()) {
                continue;
            }
            PASSABLE[material.ordinal()] = passable.contains(material);
            SOLID[material.ordinal()] = material.isSolid() && !(Openables.isOpenable(material) && !Openables.isTrapdoor(material));
            CLIMBABLE[material.ordinal()] = climbable.contains(material);
        }
    }

    private static final int MAX_ITERATIONS = 40000;
    private static final int MAX_PRE_PATHFINDING_RADIUS = 32;
    private static final double HEURISTIC_WEIGHT = 1.5;
    private static final double SQRT2 = Math.sqrt(2);
    private static final double SQRT3 = Math.sqrt(3);

    private final boolean safeMode;
    private final boolean[] allowed;

    public AStarPathfinder(boolean safeMode, Set<Material> allowedMaterials) {
        this.safeMode = safeMode;
        if (allowedMaterials != null && !allowedMaterials.isEmpty()) {
            this.allowed = new boolean[Material.values().length];
            for (Material material : allowedMaterials) {
                this.allowed[material.ordinal()] = true;
            }
        } else {
            this.allowed = null;
        }
    }

    List<Location> findPath(WorldSnapshot snapshot, Location start, Location end) {
        World world = snapshot.getWorld();
        if (!world.equals(start.getWorld()) || !world.equals(end.getWorld())) {
            return Collections.emptyList();
        }

        int sx = start.getBlockX();
        int sy = start.getBlockY();
        int sz = start.getBlockZ();
        int ex = end.getBlockX();
        int ey = end.getBlockY();
        int ez = end.getBlockZ();

        List<Location> prePath = Collections.emptyList();
        if (allowed != null && !allowed[snapshot.get(sx, sy - 1, sz).ordinal()]) {
            Node reached = findClosestAllowed(snapshot, sx, sy, sz);
            if (reached == null) {
                return Collections.emptyList();
            }
            prePath = toLocations(world, reached);
            sx = reached.x;
            sy = reached.y;
            sz = reached.z;
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<Long, Node> nodes = new HashMap<>();

        Node startNode = new Node(sx, sy, sz, null, 0, heuristic(sx, sy, sz, ex, ey, ez));
        openSet.add(startNode);
        nodes.put(startNode.key(), startNode);

        Node best = startNode;
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            Node current = openSet.poll();
            if (current.closed || nodes.get(current.key()) != current) {
                continue;
            }
            current.closed = true;
            iterations++;

            if (current.x == ex && current.y == ey && current.z == ez) {
                return join(prePath, toLocations(world, current));
            }

            if (current.h < best.h) {
                best = current;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int nx = current.x + dx;
                        int ny = current.y + dy;
                        int nz = current.z + dz;
                        if (!snapshot.contains(nx, nz)) {
                            continue;
                        }
                        long key = Node.pack(nx, ny, nz);
                        Node existing = nodes.get(key);
                        if (existing != null && existing.closed) {
                            continue;
                        }
                        if (!isWalkable(snapshot, nx, ny, nz, current.x, current.y, current.z, true)) {
                            continue;
                        }
                        double g = current.g + stepCost(dx, dy, dz);
                        if (existing == null || g < existing.g) {
                            Node node = new Node(nx, ny, nz, current, g, heuristic(nx, ny, nz, ex, ey, ez));
                            nodes.put(key, node);
                            openSet.add(node);
                        }
                    }
                }
            }
        }

        if (best != startNode && !snapshot.contains(ex, ez)) {
            return join(prePath, toLocations(world, best));
        }
        return Collections.emptyList();
    }

    private Node findClosestAllowed(WorldSnapshot snapshot, int sx, int sy, int sz) {
        Queue<Node> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Node startNode = new Node(sx, sy, sz, null, 0, 0);
        queue.add(startNode);
        visited.add(startNode.key());

        double maxSq = (double) MAX_PRE_PATHFINDING_RADIUS * MAX_PRE_PATHFINDING_RADIUS;
        int iterations = 0;
        while (!queue.isEmpty() && iterations < MAX_ITERATIONS) {
            Node current = queue.poll();
            iterations++;

            if (allowed[snapshot.get(current.x, current.y - 1, current.z).ordinal()]) {
                return current;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int nx = current.x + dx;
                        int ny = current.y + dy;
                        int nz = current.z + dz;
                        int ox = nx - sx;
                        int oy = ny - sy;
                        int oz = nz - sz;
                        if (ox * ox + oy * oy + oz * oz >= maxSq) {
                            continue;
                        }
                        long key = Node.pack(nx, ny, nz);
                        if (visited.contains(key)) {
                            continue;
                        }
                        if (isWalkable(snapshot, nx, ny, nz, current.x, current.y, current.z, false)) {
                            visited.add(key);
                            queue.add(new Node(nx, ny, nz, current, 0, 0));
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isWalkable(WorldSnapshot snapshot, int x, int y, int z, int px, int py, int pz, boolean strict) {
        Material block = snapshot.get(x, y, z);

        if (strict && CLIMBABLE[block.ordinal()]) {
            return true;
        }

        if (!PASSABLE[block.ordinal()] || !PASSABLE[snapshot.get(x, y + 1, z).ordinal()]) {
            return false;
        }

        Material below = snapshot.get(x, y - 1, z);

        if (strict && allowed != null && !allowed[below.ordinal()]) {
            return false;
        }

        if (x != px && z != pz && !isDiagonalPassable(snapshot, px, py, pz, x, z)) {
            return false;
        }

        if (y == py) {
            return SOLID[below.ordinal()] || below == Material.WATER || below == Material.LAVA;
        }

        if (y > py) {
            return y - py == 1 && SOLID[below.ordinal()];
        }

        if (safeMode) {
            if (py - y > 3) {
                return false;
            }
            return SOLID[below.ordinal()] || SOLID[snapshot.get(x, y - 2, z).ordinal()];
        }

        int minY = snapshot.getMinY();
        for (int cy = y - 1; cy >= minY; cy--) {
            Material check = snapshot.get(x, cy, z);
            if (SOLID[check.ordinal()]) {
                return true;
            }
            if (!PASSABLE[check.ordinal()]) {
                return false;
            }
        }
        return false;
    }

    private boolean isDiagonalPassable(WorldSnapshot snapshot, int px, int py, int pz, int x, int z) {
        int cx = px + Integer.compare(x, px);
        int cz = pz + Integer.compare(z, pz);
        return PASSABLE[snapshot.get(cx, py, pz).ordinal()]
                && PASSABLE[snapshot.get(cx, py + 1, pz).ordinal()]
                && PASSABLE[snapshot.get(px, py, cz).ordinal()]
                && PASSABLE[snapshot.get(px, py + 1, cz).ordinal()];
    }

    private static double stepCost(int dx, int dy, int dz) {
        int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        return axes == 1 ? 1 : axes == 2 ? SQRT2 : SQRT3;
    }

    private static double heuristic(int x, int y, int z, int ex, int ey, int ez) {
        double dx = ex - x;
        double dy = ey - y;
        double dz = ez - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<Location> toLocations(World world, Node end) {
        List<Location> path = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) {
            path.add(new Location(world, node.x, node.y, node.z));
        }
        Collections.reverse(path);
        return path;
    }

    private static List<Location> join(List<Location> prePath, List<Location> mainPath) {
        if (prePath.isEmpty()) {
            return mainPath;
        }
        List<Location> full = new ArrayList<>(prePath);
        full.addAll(mainPath);
        return full;
    }

    public static String getDirection(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) {
            return Navigate.getInstance().getConfig().getString("strings.direction.unknown", "N/A");
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        if (angle < 0) angle += 360;

        String path = "strings.direction.";
        if (angle >= 337.5 || angle < 22.5) return Navigate.getInstance().getConfig().getString(path + "south", "South");
        if (angle >= 22.5 && angle < 67.5) return Navigate.getInstance().getConfig().getString(path + "south-west", "South-West");
        if (angle >= 67.5 && angle < 112.5) return Navigate.getInstance().getConfig().getString(path + "west", "West");
        if (angle >= 112.5 && angle < 157.5) return Navigate.getInstance().getConfig().getString(path + "north-west", "North-West");
        if (angle >= 157.5 && angle < 202.5) return Navigate.getInstance().getConfig().getString(path + "north", "North");
        if (angle >= 202.5 && angle < 247.5) return Navigate.getInstance().getConfig().getString(path + "north-east", "North-East");
        if (angle >= 247.5 && angle < 292.5) return Navigate.getInstance().getConfig().getString(path + "east", "East");
        if (angle >= 292.5 && angle < 337.5) return Navigate.getInstance().getConfig().getString(path + "south-east", "South-East");

        return Navigate.getInstance().getConfig().getString(path + "unknown", "N/A");
    }

    public static float getYaw(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return 0;
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        return (float) yaw;
    }

    public static float getPitch(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return 0;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        double pitch = -Math.toDegrees(Math.atan2(dy, distanceXZ));
        return (float) pitch;
    }

    public static int getDistance2D(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return -1;
        return (int) Math.sqrt(Math.pow(to.getX() - from.getX(), 2) + Math.pow(to.getZ() - from.getZ(), 2));
    }

    public static int getDistance3D(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return -1;
        return (int) from.distance(to);
    }

    private static final class Node {
        final int x;
        final int y;
        final int z;
        final Node parent;
        final double g;
        final double h;
        final double f;
        boolean closed;

        Node(int x, int y, int z, Node parent, double g, double h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.f = g + h * HEURISTIC_WEIGHT;
        }

        long key() {
            return pack(x, y, z);
        }

        static long pack(int x, int y, int z) {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
        }
    }
}
