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
    private static final boolean[] HAZARD_FEET;
    private static final boolean[] HAZARD_FLOOR;

    static {
        Set<Material> passable = EnumSet.of(
                Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
                Material.WATER,
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
        HAZARD_FEET = new boolean[materials.length];
        HAZARD_FLOOR = new boolean[materials.length];
        for (Material material : EnumSet.of(Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.COBWEB, Material.POWDER_SNOW)) {
            HAZARD_FEET[material.ordinal()] = true;
        }
        for (Material material : EnumSet.of(Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CACTUS, Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.POWDER_SNOW)) {
            HAZARD_FLOOR[material.ordinal()] = true;
        }
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
    private static final int MAX_SAFE_DROP = 3;
    private static final int MAX_DROP = 128;
    private static final double HEURISTIC_WEIGHT = 1.5;
    private static final double SQRT2 = Math.sqrt(2);
    private static final double STEP_UP_COST = 1.6;
    private static final double OPENABLE_COST = 0.6;
    private static final double WATER_COST = 0.5;
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    private final boolean safeMode;
    private final boolean[] allowed;
    private final boolean[] passable;

    public AStarPathfinder(boolean safeMode, Set<Material> allowedMaterials) {
        this.safeMode = safeMode;
        this.passable = PASSABLE.clone();
        if (safeMode) {
            for (int i = 0; i < passable.length; i++) {
                if (HAZARD_FEET[i]) {
                    passable[i] = false;
                }
            }
        }
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

        if (!passable[snapshot.get(sx, sy, sz).ordinal()] && passable[snapshot.get(sx, sy + 1, sz).ordinal()] && passable[snapshot.get(sx, sy + 2, sz).ordinal()]) {
            sy++;
        }

        Moves moves = new Moves();
        List<Location> prePath = Collections.emptyList();
        if (allowed != null && !allowed[snapshot.get(sx, sy - 1, sz).ordinal()]) {
            Node reached = findClosestAllowed(snapshot, moves, sx, sy, sz);
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

            int gx = current.x - ex;
            int gy = current.y - ey;
            int gz = current.z - ez;
            if (gx * gx + gy * gy + gz * gz <= 2) {
                return join(prePath, toLocations(world, current));
            }

            if (current.h < best.h || current.h == best.h && current.g < best.g) {
                best = current;
            }

            int count = generate(snapshot, moves, current.x, current.y, current.z, true);
            for (int i = 0; i < count; i++) {
                int nx = moves.x[i];
                int ny = moves.y[i];
                int nz = moves.z[i];
                long key = Node.pack(nx, ny, nz);
                Node existing = nodes.get(key);
                if (existing != null && existing.closed) {
                    continue;
                }
                double g = current.g + moves.cost[i];
                if (existing == null || g < existing.g) {
                    Node node = new Node(nx, ny, nz, current, g, heuristic(nx, ny, nz, ex, ey, ez));
                    nodes.put(key, node);
                    openSet.add(node);
                }
            }
        }

        if (best != startNode) {
            return join(prePath, toLocations(world, best));
        }
        return Collections.emptyList();
    }

    private Node findClosestAllowed(WorldSnapshot snapshot, Moves moves, int sx, int sy, int sz) {
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

            int count = generate(snapshot, moves, current.x, current.y, current.z, false);
            for (int i = 0; i < count; i++) {
                int nx = moves.x[i];
                int ny = moves.y[i];
                int nz = moves.z[i];
                int ox = nx - sx;
                int oy = ny - sy;
                int oz = nz - sz;
                if (ox * ox + oy * oy + oz * oz >= maxSq) {
                    continue;
                }
                if (visited.add(Node.pack(nx, ny, nz))) {
                    queue.add(new Node(nx, ny, nz, current, 0, 0));
                }
            }
        }
        return null;
    }

    private int generate(WorldSnapshot snapshot, Moves moves, int cx, int cy, int cz, boolean strict) {
        moves.count = 0;
        boolean headroom = passable[snapshot.get(cx, cy + 2, cz).ordinal()];
        Material here = snapshot.get(cx, cy, cz);
        boolean swimming = here == Material.WATER;

        for (int d = 0; d < 4; d++) {
            int nx = cx + DX[d];
            int nz = cz + DZ[d];
            if (!snapshot.contains(nx, nz)) {
                continue;
            }
            Material feet = snapshot.get(nx, cy, nz);
            if (standable(snapshot, nx, cy, nz, strict)) {
                moves.add(nx, cy, nz, 1 + extra(snapshot, nx, cy, nz));
            } else if (SOLID[feet.ordinal()]) {
                if (headroom && standable(snapshot, nx, cy + 1, nz, strict)) {
                    moves.add(nx, cy + 1, nz, STEP_UP_COST + extra(snapshot, nx, cy + 1, nz));
                }
            } else if (passable[feet.ordinal()] && passable[snapshot.get(nx, cy + 1, nz).ordinal()]) {
                drop(snapshot, moves, nx, cy, nz, strict);
            }
        }

        for (int d = 4; d < 8; d++) {
            int dx = DX[d];
            int dz = DZ[d];
            int nx = cx + dx;
            int nz = cz + dz;
            if (!snapshot.contains(nx, nz)) {
                continue;
            }
            if (!standable(snapshot, nx, cy, nz, strict)) {
                continue;
            }
            if (passable[snapshot.get(cx + dx, cy, cz).ordinal()]
                    && passable[snapshot.get(cx + dx, cy + 1, cz).ordinal()]
                    && passable[snapshot.get(cx, cy, cz + dz).ordinal()]
                    && passable[snapshot.get(cx, cy + 1, cz + dz).ordinal()]) {
                moves.add(nx, cy, nz, SQRT2 + extra(snapshot, nx, cy, nz));
            }
        }

        if (headroom) {
            Material above = snapshot.get(cx, cy + 1, cz);
            if (CLIMBABLE[above.ordinal()] || swimming && passable[above.ordinal()]) {
                moves.add(cx, cy + 1, cz, 1 + (swimming ? WATER_COST : 0));
            }
        }
        Material below = snapshot.get(cx, cy - 1, cz);
        if (CLIMBABLE[below.ordinal()] || swimming && passable[below.ordinal()]) {
            moves.add(cx, cy - 1, cz, 1 + (swimming ? WATER_COST : 0));
        }
        return moves.count;
    }

    private void drop(WorldSnapshot snapshot, Moves moves, int x, int y, int z, boolean strict) {
        int limit = safeMode ? MAX_SAFE_DROP : MAX_DROP;
        int minY = snapshot.getMinY();
        for (int depth = 1; depth <= limit; depth++) {
            int ly = y - depth;
            if (ly < minY) {
                return;
            }
            Material feet = snapshot.get(x, ly, z);
            if (!passable[feet.ordinal()]) {
                return;
            }
            if (standable(snapshot, x, ly, z, strict)) {
                moves.add(x, ly, z, 1 + (depth - 1) + extra(snapshot, x, ly, z));
                return;
            }
            Material below = snapshot.get(x, ly - 1, z);
            if (HAZARD_FLOOR[below.ordinal()] && (safeMode || below == Material.LAVA)) {
                return;
            }
        }
    }

    private boolean standable(WorldSnapshot snapshot, int x, int y, int z, boolean strict) {
        Material feet = snapshot.get(x, y, z);
        if (!passable[feet.ordinal()] || !passable[snapshot.get(x, y + 1, z).ordinal()]) {
            return false;
        }

        Material below = snapshot.get(x, y - 1, z);
        if (HAZARD_FLOOR[below.ordinal()] && (safeMode || below == Material.LAVA)) {
            return false;
        }

        boolean climbable = CLIMBABLE[feet.ordinal()];
        if (strict && allowed != null && !climbable && feet != Material.WATER && !allowed[below.ordinal()]) {
            return false;
        }

        return SOLID[below.ordinal()] || below == Material.WATER || climbable || feet == Material.WATER;
    }

    private double extra(WorldSnapshot snapshot, int x, int y, int z) {
        double extra = 0;
        if (Openables.isOpenable(snapshot.get(x, y, z)) || Openables.isOpenable(snapshot.get(x, y + 1, z))) {
            extra += OPENABLE_COST;
        }
        if (snapshot.get(x, y, z) == Material.WATER) {
            extra += WATER_COST;
        }
        return extra;
    }

    private static double heuristic(int x, int y, int z, int ex, int ey, int ez) {
        double dx = Math.abs(ex - x);
        double dz = Math.abs(ez - z);
        double dy = Math.abs(ey - y);
        double min = Math.min(dx, dz);
        return dx + dz - (2 - SQRT2) * min + dy * 0.5;
    }

    private static List<Location> toLocations(World world, Node end) {
        List<Location> path = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) {
            path.add(new Location(world, node.x, node.y, node.z));
            Node parent = node.parent;
            if (parent != null && parent.y - node.y > 1) {
                for (int y = node.y + 1; y <= parent.y; y++) {
                    path.add(new Location(world, node.x, y, node.z));
                }
            }
        }
        Collections.reverse(path);
        return path;
    }

    private static List<Location> join(List<Location> prePath, List<Location> mainPath) {
        if (prePath.isEmpty()) {
            return mainPath;
        }
        List<Location> full = new ArrayList<>(prePath);
        full.addAll(mainPath.subList(Math.min(1, mainPath.size()), mainPath.size()));
        return full;
    }

    private static final class Moves {
        final int[] x = new int[16];
        final int[] y = new int[16];
        final int[] z = new int[16];
        final double[] cost = new double[16];
        int count;

        void add(int nx, int ny, int nz, double c) {
            x[count] = nx;
            y[count] = ny;
            z[count] = nz;
            cost[count] = c;
            count++;
        }
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
