package art.arcane.optics.aperture;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Vec3d;
import art.arcane.optics.math.CellKeys;
import art.arcane.optics.math.Box;
import art.arcane.optics.math.Face;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

public final class ApertureCells implements CellAperture {
    private static final Comparator<int[]> CELL_ORDER = Comparator.<int[]>comparingInt(cell -> cell[0])
        .thenComparingInt(cell -> cell[1]).thenComparingInt(cell -> cell[2]);

    private final LongOpenHashSet blockKeys = new LongOpenHashSet();
    private final List<Vec3d> blockPositions = new ArrayList<>();
    private final ConcurrentHashMap<Face, List<Box>> apertureFaceCache = new ConcurrentHashMap<>();
    private Box area;
    private Vec3d apertureCenter;
    private long revision;

    public void setArea(Box area) {
        this.area = area;
        blockKeys.clear();
        blockPositions.clear();
        blockKeys.ensureCapacity(getBoundingBlockVolume());
        for (int x = (int) Math.floor(area.getXa()); x <= (int) Math.floor(area.getXb()); x++) {
            for (int y = (int) Math.floor(area.getYa()); y <= (int) Math.floor(area.getYb()); y++) {
                for (int z = (int) Math.floor(area.getZa()); z <= (int) Math.floor(area.getZb()); z++) {
                    addBlockCell(x, y, z);
                }
            }
        }
        invalidate();
    }

    public void restore(Box area, Collection<Vec3d> cells) {
        this.area = area;
        blockKeys.clear();
        blockPositions.clear();
        for (Vec3d cell : cells) {
            addBlockCell(cell.blockX(), cell.blockY(), cell.blockZ());
        }
        invalidate();
    }

    public void setBlocks(Collection<Vec3d> cells) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        blockKeys.clear();
        blockPositions.clear();
        int xa = Integer.MAX_VALUE;
        int ya = Integer.MAX_VALUE;
        int za = Integer.MAX_VALUE;
        int xb = Integer.MIN_VALUE;
        int yb = Integer.MIN_VALUE;
        int zb = Integer.MIN_VALUE;
        for (Vec3d cell : cells) {
            int x = cell.blockX();
            int y = cell.blockY();
            int z = cell.blockZ();
            addBlockCell(x, y, z);
            xa = Math.min(xa, x);
            ya = Math.min(ya, y);
            za = Math.min(za, z);
            xb = Math.max(xb, x);
            yb = Math.max(yb, y);
            zb = Math.max(zb, z);
        }
        area = new Box(xa, xb + 0.999D, ya, yb + 0.999D, za, zb + 0.999D);
        invalidate();
    }

    @Override
    public Box getArea() {
        return area;
    }

    @Override
    public Vec3d getApertureCenter() {
        return apertureCenter;
    }

    public long getRevision() {
        return revision;
    }

    public List<Vec3d> getBlockPositions() {
        return List.copyOf(blockPositions);
    }

    public Vec3d randomBlockPosition() {
        int size = blockPositions.size();
        return size == 0 ? null : blockPositions.get(ThreadLocalRandom.current().nextInt(size));
    }

    public Vec3d randomCellCentre() {
        Vec3d block = randomBlockPosition();
        if (block == null) {
            if (area == null) {
                return null;
            }
            block = area.random(ThreadLocalRandom.current());
        }
        return new Vec3d(Math.floor(block.x()) + 0.5D, Math.floor(block.y()) + 0.5D, Math.floor(block.z()) + 0.5D);
    }

    public boolean contains(Vec3d point) {
        return point != null && area != null && area.containsPrimitive(point.x(), point.y(), point.z())
            && containsBlock(point.blockX(), point.blockY(), point.blockZ());
    }

    public Box captureZone(double radius) {
        Vec3d padding = new Vec3d(radius, radius, radius);
        return area == null ? null : new Box(area.min().subtract(padding), area.max().add(padding));
    }

    private void addBlockCell(int x, int y, int z) {
        if (blockKeys.add(CellKeys.pack(x, y, z))) {
            blockPositions.add(new Vec3d(x, y, z));
        }
    }

    private void invalidate() {
        apertureCenter = cellCenter();
        apertureFaceCache.clear();
        revision++;
    }
    private Vec3d cellCenter() {
        if (blockPositions.isEmpty()) {
            return area == null ? null : area.min().add(area.max().subtract(area.min()).multiply(0.5D));
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Vec3d cell : blockPositions) {
            minX = Math.min(minX, cell.blockX());
            minY = Math.min(minY, cell.blockY());
            minZ = Math.min(minZ, cell.blockZ());
            maxX = Math.max(maxX, cell.blockX());
            maxY = Math.max(maxY, cell.blockY());
            maxZ = Math.max(maxZ, cell.blockZ());
        }
        return new Vec3d((minX + (double) maxX + 1.0D) * 0.5D,
            (minY + (double) maxY + 1.0D) * 0.5D, (minZ + (double) maxZ + 1.0D) * 0.5D);
    }

	public boolean containsBlock(int x, int y, int z)
	{
		if(blockKeys.isEmpty())
		{
			return getArea() != null && getArea().containsPrimitive(x + 0.5D, y + 0.5D, z + 0.5D);
		}

		return blockKeys.contains(CellKeys.pack(x, y, z));
	}

	public boolean containsOrAdjoinsBlock(int x, int y, int z)
	{
		for(int offsetX = -1; offsetX <= 1; offsetX++)
		{
			for(int offsetY = -1; offsetY <= 1; offsetY++)
			{
				for(int offsetZ = -1; offsetZ <= 1; offsetZ++)
				{
					if(containsBlock(x + offsetX, y + offsetY, z + offsetZ))
					{
						return true;
					}
				}
			}
		}

		return false;
	}

	public List<Box> getCachedApertureFaces(Face face)
	{
		List<Box> cached = apertureFaceCache.get(face);
		if(cached != null)
		{
			return cached;
		}

		List<Box> immutable = blockPositions.isEmpty() || isFullCuboid() ? List.of(getArea().getFace(face)) : mergedFaces(face);
		List<Box> raced = apertureFaceCache.putIfAbsent(face, immutable);
		return raced == null ? immutable : raced;
	}

	public boolean isFullCuboid()
	{
		return !blockKeys.isEmpty() && blockKeys.size() == getBoundingBlockVolume();
	}

	private int getBoundingBlockVolume()
	{
		int xa = (int) Math.floor(getArea().getXa());
		int ya = (int) Math.floor(getArea().getYa());
		int za = (int) Math.floor(getArea().getZa());
		int xb = (int) Math.floor(getArea().getXb());
		int yb = (int) Math.floor(getArea().getYb());
		int zb = (int) Math.floor(getArea().getZb());
		return Math.max(0, (xb - xa + 1) * (yb - ya + 1) * (zb - za + 1));
	}

	private List<Box> mergedFaces(Face face)
	{
		Frame canonical = Frame.canonical(face);
		int layerAxis = face.axisIndex();
		int columnAxis = canonical.getRight().axisIndex();
		int rowAxis = canonical.getUp().axisIndex();
		int[][] cells = new int[blockPositions.size()][];
		for(int index = 0; index < cells.length; index++)
		{
			Vec3d block = blockPositions.get(index);
			int[] coordinates = {block.blockX(), block.blockY(), block.blockZ()};
			cells[index] = new int[]{coordinates[layerAxis], coordinates[rowAxis], coordinates[columnAxis]};
		}
		Arrays.sort(cells, CELL_ORDER);
		List<int[]> open = new ArrayList<>();
		List<int[]> next = new ArrayList<>();
		List<Box> faces = new ArrayList<>();
		int index = 0;
		while(index < cells.length)
		{
			int layer = cells[index][0];
			int row = cells[index][1];
			next.clear();
			while(index < cells.length && cells[index][0] == layer && cells[index][1] == row)
			{
				int start = cells[index][2];
				int end = start;
				index++;
				while(index < cells.length && cells[index][0] == layer && cells[index][1] == row && cells[index][2] == end + 1)
				{
					end++;
					index++;
				}
				int[] run = continued(open, layer, row, start, end);
				next.add(run == null ? new int[]{layer, row, row, start, end} : new int[]{layer, run[1], row, start, end});
			}
			for(int[] run : open)
			{
				if(!(run[0] == layer && run[2] == row - 1 && continues(next, run)))
				{
					faces.add(faceBox(face, layerAxis, rowAxis, columnAxis, run));
				}
			}
			List<int[]> swap = open;
			open = next;
			next = swap;
		}
		for(int[] run : open)
		{
			faces.add(faceBox(face, layerAxis, rowAxis, columnAxis, run));
		}
		return List.copyOf(faces);
	}

	private static int[] continued(List<int[]> open, int layer, int row, int start, int end)
	{
		for(int[] run : open)
		{
			if(run[0] == layer && run[2] == row - 1 && run[3] == start && run[4] == end)
			{
				return run;
			}
		}
		return null;
	}

	private static boolean continues(List<int[]> next, int[] run)
	{
		for(int[] candidate : next)
		{
			if(candidate[1] == run[1] && candidate[3] == run[3] && candidate[4] == run[4])
			{
				return true;
			}
		}
		return false;
	}

	private static Box faceBox(Face face, int layerAxis, int rowAxis, int columnAxis, int[] run)
	{
		double[] min = new double[3];
		double[] max = new double[3];
		min[layerAxis] = run[0];
		max[layerAxis] = run[0] + 0.999D;
		min[rowAxis] = run[1];
		max[rowAxis] = run[2] + 0.999D;
		min[columnAxis] = run[3];
		max[columnAxis] = run[4] + 0.999D;
		return new Box(min[0], max[0], min[1], max[1], min[2], max[2]).getFace(face);
	}

}
