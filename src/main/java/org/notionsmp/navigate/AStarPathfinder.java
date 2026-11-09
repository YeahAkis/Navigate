package org.notionsmp.navigate;

import com.destroystokyo.paper.MaterialSetTag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
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
    private static final int WATER = Material.WATER.ordinal();
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    private final boolean safeMode;
    private final boolean[] allowed;
    private final boolean[] passable;
    private final boolean[] hazardFloor;

    public AStarPathfinder(boolean safeMode, Set<Material> allowedMaterials) {
        this.safeMode = safeMode;
        this.passable = PASSABLE.clone();
        this.hazardFloor = new boolean[HAZARD_FLOOR.length];
        for (int i = 0; i < passable.length; i++) {
            if (safeMode && HAZARD_FEET[i]) {
                passable[i] = false;
            }
            hazardFloor[i] = HAZARD_FLOOR[i] && (safeMode || i == Material.LAVA.ordinal());
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
        if (!Objects.equals(world, start.getWorld()) || !Objects.equals(world, end.getWorld())) {
            return Collections.emptyList();
        }

        int sx = start.getBlockX();
        int sy = start.getBlockY();
        int sz = start.getBlockZ();
        int ex = end.getBlockX();
        int ey = end.getBlockY();
        int ez = end.getBlockZ();

        if (!passable[snapshot.id(sx, sy, sz)] && passable[snapshot.id(sx, sy + 1, sz)] && passable[snapshot.id(sx, sy + 2, sz)]) {
            sy++;
        }

        Moves moves = new Moves();
        Nodes nodes = new Nodes(1024);
        List<Location> prePath = Collections.emptyList();
        if (allowed != null && !allowed[snapshot.id(sx, sy - 1, sz)]) {
            int reached = findClosestAllowed(snapshot, moves, nodes, sx, sy, sz);
            if (reached < 0) {
                return Collections.emptyList();
            }
            prePath = toLocations(world, nodes, reached);
            sx = nodes.x[reached];
            sy = nodes.y[reached];
            sz = nodes.z[reached];
            nodes = new Nodes(1024);
        }

        Heap open = new Heap(1024);
        int startNode = nodes.add(sx, sy, sz, -1, 0, heuristic(sx, sy, sz, ex, ey, ez));
        open.push(startNode, nodes.h[startNode] * HEURISTIC_WEIGHT);

        int best = startNode;
        int iterations = 0;

        while (open.size > 0 && iterations < MAX_ITERATIONS) {
            int current = open.pop();
            if (nodes.closed[current]) {
                continue;
            }
            nodes.closed[current] = true;
            iterations++;

            int cx = nodes.x[current];
            int cy = nodes.y[current];
            int cz = nodes.z[current];
            int gx = cx - ex;
            int gy = cy - ey;
            int gz = cz - ez;
            if (gx * gx + gy * gy + gz * gz <= 2) {
                return join(prePath, toLocations(world, nodes, current));
            }

            double ch = nodes.h[current];
            double bh = nodes.h[best];
            if (ch < bh || ch == bh && nodes.g[current] < nodes.g[best]) {
                best = current;
            }

            int count = generate(snapshot, moves, cx, cy, cz, true);
            double cg = nodes.g[current];
            for (int i = 0; i < count; i++) {
                int nx = moves.x[i];
                int ny = moves.y[i];
                int nz = moves.z[i];
                double g = cg + moves.cost[i];
                int existing = nodes.find(nx, ny, nz);
                if (existing < 0) {
                    int node = nodes.add(nx, ny, nz, current, g, heuristic(nx, ny, nz, ex, ey, ez));
                    open.push(node, g + nodes.h[node] * HEURISTIC_WEIGHT);
                } else if (!nodes.closed[existing] && g < nodes.g[existing]) {
                    nodes.g[existing] = g;
                    nodes.parent[existing] = current;
                    open.push(existing, g + nodes.h[existing] * HEURISTIC_WEIGHT);
                }
            }
        }

        if (best != startNode) {
            return join(prePath, toLocations(world, nodes, best));
        }
        return Collections.emptyList();
    }

    private int findClosestAllowed(WorldSnapshot snapshot, Moves moves, Nodes nodes, int sx, int sy, int sz) {
        nodes.add(sx, sy, sz, -1, 0, 0);

        double maxSq = (double) MAX_PRE_PATHFINDING_RADIUS * MAX_PRE_PATHFINDING_RADIUS;
        for (int head = 0; head < nodes.size && head < MAX_ITERATIONS; head++) {
            int cx = nodes.x[head];
            int cy = nodes.y[head];
            int cz = nodes.z[head];

            if (allowed[snapshot.id(cx, cy - 1, cz)]) {
                return head;
            }

            int count = generate(snapshot, moves, cx, cy, cz, false);
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
                if (nodes.find(nx, ny, nz) < 0) {
                    nodes.add(nx, ny, nz, head, 0, 0);
                }
            }
        }
        return -1;
    }

    private int generate(WorldSnapshot snapshot, Moves moves, int cx, int cy, int cz, boolean strict) {
        moves.count = 0;
        boolean headroom = passable[snapshot.id(cx, cy + 2, cz)];
        boolean swimming = snapshot.id(cx, cy, cz) == WATER;

        for (int d = 0; d < 4; d++) {
            int nx = cx + DX[d];
            int nz = cz + DZ[d];
            if (!snapshot.contains(nx, nz)) {
                continue;
            }
            int feet = snapshot.id(nx, cy, nz);
            if (standable(snapshot, nx, cy, nz, strict)) {
                moves.add(nx, cy, nz, 1 + extra(snapshot, nx, cy, nz));
            } else if (SOLID[feet]) {
                if (headroom && standable(snapshot, nx, cy + 1, nz, strict)) {
                    moves.add(nx, cy + 1, nz, STEP_UP_COST + extra(snapshot, nx, cy + 1, nz));
                }
            } else if (passable[feet] && passable[snapshot.id(nx, cy + 1, nz)]) {
                drop(snapshot, moves, nx, cy, nz, strict);
            }
        }

        for (int d = 4; d < 8; d++) {
            int dx = DX[d];
            int dz = DZ[d];
            int nx = cx + dx;
            int nz = cz + dz;
            if (!snapshot.contains(nx, nz) || !standable(snapshot, nx, cy, nz, strict)) {
                continue;
            }
            if (passable[snapshot.id(cx + dx, cy, cz)]
                    && passable[snapshot.id(cx + dx, cy + 1, cz)]
                    && passable[snapshot.id(cx, cy, cz + dz)]
                    && passable[snapshot.id(cx, cy + 1, cz + dz)]) {
                moves.add(nx, cy, nz, SQRT2 + extra(snapshot, nx, cy, nz));
            }
        }

        if (headroom) {
            int above = snapshot.id(cx, cy + 1, cz);
            if (CLIMBABLE[above] || swimming && passable[above]) {
                moves.add(cx, cy + 1, cz, 1 + (swimming ? WATER_COST : 0));
            }
        }
        int below = snapshot.id(cx, cy - 1, cz);
        if (CLIMBABLE[below] || swimming && passable[below]) {
            moves.add(cx, cy - 1, cz, 1 + (swimming ? WATER_COST : 0));
        }
        return moves.count;
    }

    private void drop(WorldSnapshot snapshot, Moves moves, int x, int y, int z, boolean strict) {
        int limit = safeMode ? MAX_SAFE_DROP : MAX_DROP;
        int minY = snapshot.getMinY();
        for (int depth = 1; depth <= limit; depth++) {
            int ly = y - depth;
            if (ly < minY || !passable[snapshot.id(x, ly, z)]) {
                return;
            }
            if (standable(snapshot, x, ly, z, strict)) {
                moves.add(x, ly, z, depth + extra(snapshot, x, ly, z));
                return;
            }
            if (hazardFloor[snapshot.id(x, ly - 1, z)]) {
                return;
            }
        }
    }

    private boolean standable(WorldSnapshot snapshot, int x, int y, int z, boolean strict) {
        int feet = snapshot.id(x, y, z);
        if (!passable[feet] || !passable[snapshot.id(x, y + 1, z)]) {
            return false;
        }

        int below = snapshot.id(x, y - 1, z);
        if (hazardFloor[below]) {
            return false;
        }

        boolean supported = CLIMBABLE[feet] || feet == WATER;
        if (strict && allowed != null && !supported && !allowed[below]) {
            return false;
        }

        return supported || SOLID[below] || below == WATER;
    }

    private double extra(WorldSnapshot snapshot, int x, int y, int z) {
        int feet = snapshot.id(x, y, z);
        double extra = 0;
        if (Openables.isOpenable(feet) || Openables.isOpenable(snapshot.id(x, y + 1, z))) {
            extra += OPENABLE_COST;
        }
        if (feet == WATER) {
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

    private static List<Location> toLocations(World world, Nodes nodes, int end) {
        List<Location> path = new ArrayList<>();
        for (int node = end; node >= 0; node = nodes.parent[node]) {
            int x = nodes.x[node];
            int y = nodes.y[node];
            int z = nodes.z[node];
            path.add(new Location(world, x, y, z));
            int parent = nodes.parent[node];
            if (parent >= 0 && nodes.y[parent] - y > 1) {
                for (int fall = y + 1; fall <= nodes.y[parent]; fall++) {
                    path.add(new Location(world, x, fall, z));
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

    private static final class Nodes {
        int[] x;
        int[] y;
        int[] z;
        int[] parent;
        double[] g;
        double[] h;
        boolean[] closed;
        long[] keys;
        int[] table;
        int mask;
        int size;

        Nodes(int capacity) {
            x = new int[capacity];
            y = new int[capacity];
            z = new int[capacity];
            parent = new int[capacity];
            g = new double[capacity];
            h = new double[capacity];
            closed = new boolean[capacity];
            keys = new long[capacity];
            int slots = Integer.highestOneBit(capacity * 2 - 1) << 1;
            table = new int[slots];
            mask = slots - 1;
        }

        int find(int nx, int ny, int nz) {
            long key = pack(nx, ny, nz);
            int slot = hash(key) & mask;
            while (true) {
                int entry = table[slot];
                if (entry == 0) {
                    return -1;
                }
                if (keys[entry - 1] == key) {
                    return entry - 1;
                }
                slot = (slot + 1) & mask;
            }
        }

        int add(int nx, int ny, int nz, int from, double cost, double estimate) {
            if (size == x.length) {
                grow();
            }
            int index = size++;
            x[index] = nx;
            y[index] = ny;
            z[index] = nz;
            parent[index] = from;
            g[index] = cost;
            h[index] = estimate;
            long key = pack(nx, ny, nz);
            keys[index] = key;
            int slot = hash(key) & mask;
            while (table[slot] != 0) {
                slot = (slot + 1) & mask;
            }
            table[slot] = index + 1;
            return index;
        }

        private void grow() {
            int capacity = x.length * 2;
            x = Arrays.copyOf(x, capacity);
            y = Arrays.copyOf(y, capacity);
            z = Arrays.copyOf(z, capacity);
            parent = Arrays.copyOf(parent, capacity);
            g = Arrays.copyOf(g, capacity);
            h = Arrays.copyOf(h, capacity);
            closed = Arrays.copyOf(closed, capacity);
            keys = Arrays.copyOf(keys, capacity);
            int slots = Integer.highestOneBit(capacity * 2 - 1) << 1;
            table = new int[slots];
            mask = slots - 1;
            for (int i = 0; i < size; i++) {
                int slot = hash(keys[i]) & mask;
                while (table[slot] != 0) {
                    slot = (slot + 1) & mask;
                }
                table[slot] = i + 1;
            }
        }

        private static int hash(long key) {
            key *= 0x9E3779B97F4A7C15L;
            return (int) (key >>> 32);
        }

        private static long pack(int x, int y, int z) {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
        }
    }

    private static final class Heap {
        int[] nodes;
        double[] priorities;
        int size;

        Heap(int capacity) {
            nodes = new int[capacity];
            priorities = new double[capacity];
        }

        void push(int node, double priority) {
            if (size == nodes.length) {
                nodes = Arrays.copyOf(nodes, size * 2);
                priorities = Arrays.copyOf(priorities, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (priorities[parent] <= priority) {
                    break;
                }
                nodes[i] = nodes[parent];
                priorities[i] = priorities[parent];
                i = parent;
            }
            nodes[i] = node;
            priorities[i] = priority;
        }

        int pop() {
            int top = nodes[0];
            size--;
            if (size > 0) {
                int node = nodes[size];
                double priority = priorities[size];
                int i = 0;
                int half = size >>> 1;
                while (i < half) {
                    int child = 2 * i + 1;
                    if (child + 1 < size && priorities[child + 1] < priorities[child]) {
                        child++;
                    }
                    if (priorities[child] >= priority) {
                        break;
                    }
                    nodes[i] = nodes[child];
                    priorities[i] = priorities[child];
                    i = child;
                }
                nodes[i] = node;
                priorities[i] = priority;
            }
            return top;
        }
    }

    public static String getDirection(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) {
            return "unknown";
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "south";
        if (angle < 67.5) return "south-west";
        if (angle < 112.5) return "west";
        if (angle < 157.5) return "north-west";
        if (angle < 202.5) return "north";
        if (angle < 247.5) return "north-east";
        if (angle < 292.5) return "east";
        return "south-east";
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
}
