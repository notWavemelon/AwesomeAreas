package org.wavemelon.awesomeareas.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders categorized Google Maps-styled terrain colors for loaded Minecraft chunks.
 * Features:
 * - Specific palette: Green (natural surface), Gray (roads/paths), Dark Green (trees), Blue (water), Light Yellow (sand)
 * - Additional distinct colors: Snow white, Stone gray, Structure tint
 * - Adaptive zoom-based downsampling and horizontal run-length quad merging
 * - Strict per-frame chunk generation budget to guarantee zero frame drops
 */
public class TerrainMapRenderer {
    // Google Maps Styled Color Palette (ARGB)
    public static final int COLOR_MAP_BG     = 0xFFF1EFEA; // Soft light warm beige (Google Maps land canvas)
    public static final int COLOR_NATURAL    = 0xFFBDE5B8; // Soft natural surface green (grass, dirt, moss)
    public static final int COLOR_ROAD       = 0xFFB0BEC5; // Road / path gray
    public static final int COLOR_TREES      = 0xFF3E8E41; // Dark green for trees / foliage
    public static final int COLOR_WATER      = 0xFF94C5F8; // Clean water blue
    public static final int COLOR_SAND       = 0xFFF9E79F; // Light yellow for sand / beach
    public static final int COLOR_SNOW       = 0xFFF8FAFC; // Snow / frozen white
    public static final int COLOR_STONE      = 0xFF9E9E9E; // Mountain / stone gray
    public static final int COLOR_STRUCTURE  = 0xFFCFD8DC; // Wood / building structure tint

    private static final int GRID_SIZE = 8; // 8x8 sample points per chunk
    private static final int MAX_CHUNKS_PER_FRAME = 4; // Chunk sampling budget to prevent any frame drops

    public static record CachedChunk(int[] grid8x8, int dominantColor) {}

    private static final int CACHE_SIZE = 500;
    private final Map<Long, CachedChunk> chunkCache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CachedChunk> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public void clearCache() {
        chunkCache.clear();
    }

    public boolean isWaterAt(int worldX, int worldZ) {
        long key = ChunkPos.pack(worldX >> 4, worldZ >> 4);
        CachedChunk cached = chunkCache.get(key);
        if (cached != null) {
            int lx = Math.floorMod(worldX, 16) / 2;
            int lz = Math.floorMod(worldZ, 16) / 2;
            return cached.grid8x8()[lz * GRID_SIZE + lx] == COLOR_WATER;
        }
        return false;
    }

    public void renderTerrain(GuiGraphicsExtractor graphics, Minecraft client, double cameraX, double cameraZ, double zoom,
                              int mapLeft, int mapTop, int mapW, int mapH) {
        ClientLevel level = client.level;
        if (level == null) return;

        int mapCenterX = mapLeft + mapW / 2;
        int mapCenterY = mapTop + mapH / 2;

        // Compute visible chunk bounds
        int minWorldX = (int) (cameraX - (mapW / 2.0) / zoom);
        int maxWorldX = (int) (cameraX + (mapW / 2.0) / zoom);
        int minWorldZ = (int) (cameraZ - (mapH / 2.0) / zoom);
        int maxWorldZ = (int) (cameraZ + (mapH / 2.0) / zoom);

        int minChunkX = minWorldX >> 4;
        int maxChunkX = maxWorldX >> 4;
        int minChunkZ = minWorldZ >> 4;
        int maxChunkZ = maxWorldZ >> 4;

        int chunkSpanX = maxChunkX - minChunkX + 1;
        int chunkSpanZ = maxChunkZ - minChunkZ + 1;
        if (chunkSpanX * chunkSpanZ > 500) {
            return;
        }

        // Adaptive LOD based on zoom level:
        // zoom < 0.35: 1x1 (dominant chunk color)
        // 0.35 <= zoom < 0.85: 4x4 sub-grid (16 cells)
        // zoom >= 0.85: 8x8 sub-grid (64 cells)
        int lod;
        if (zoom < 0.35) {
            lod = 1;
        } else if (zoom < 0.85) {
            lod = 4;
        } else {
            lod = 8;
        }

        int chunksParsedThisFrame = 0;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                int chunkOriginX = cx << 4;
                int chunkOriginZ = cz << 4;

                int csx0 = (int) (mapCenterX + (chunkOriginX - cameraX) * zoom);
                int csz0 = (int) (mapCenterY + (chunkOriginZ - cameraZ) * zoom);
                int csx1 = (int) Math.ceil(mapCenterX + (chunkOriginX + 16 - cameraX) * zoom);
                int csz1 = (int) Math.ceil(mapCenterY + (chunkOriginZ + 16 - cameraZ) * zoom);

                // Quick chunk-level bounding box cull against viewport
                if (csx1 < mapLeft || csx0 > mapLeft + mapW || csz1 < mapTop || csz0 > mapTop + mapH) {
                    continue;
                }

                long key = ChunkPos.pack(cx, cz);
                CachedChunk cached = chunkCache.get(key);

                if (cached == null) {
                    if (chunksParsedThisFrame < MAX_CHUNKS_PER_FRAME) {
                        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                        if (chunk != null) {
                            cached = sampleChunk(level, chunk, cx, cz);
                            chunkCache.put(key, cached);
                            chunksParsedThisFrame++;
                        }
                    }
                }

                if (cached != null) {
                    drawChunkLOD(graphics, cx, cz, cached, lod, cameraX, cameraZ, zoom, mapCenterX, mapCenterY, mapLeft, mapTop, mapW, mapH);
                } else {
                    // Fast placeholder fill for un-sampled chunks in this frame
                    int drawX0 = Math.max(mapLeft, csx0);
                    int drawZ0 = Math.max(mapTop, csz0);
                    int drawX1 = Math.min(mapLeft + mapW, csx1);
                    int drawZ1 = Math.min(mapTop + mapH, csz1);
                    if (drawX1 > drawX0 && drawZ1 > drawZ0) {
                        graphics.fill(drawX0, drawZ0, drawX1, drawZ1, COLOR_NATURAL);
                    }
                }
            }
        }
    }

    private CachedChunk sampleChunk(ClientLevel level, LevelChunk chunk, int cx, int cz) {
        int[] grid = new int[GRID_SIZE * GRID_SIZE];
        Map<Integer, Integer> colorCounts = new LinkedHashMap<>();

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int r = 0; r < GRID_SIZE; r++) {
            int bz = r * 2 + 1; // sample points at 1, 3, 5, 7, 9, 11, 13, 15
            for (int c = 0; c < GRID_SIZE; c++) {
                int bx = c * 2 + 1;

                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
                int color = COLOR_NATURAL;

                if (y > level.getMinY()) {
                    mpos.set((cx << 4) + bx, y - 1, (cz << 4) + bz);
                    BlockState state = chunk.getBlockState(mpos);
                    // If air or structure void, peek down a few blocks
                    for (int d = 0; d < 3 && (state.isAir() || state.getBlock() == Blocks.STRUCTURE_VOID); d++) {
                        mpos.setY(mpos.getY() - 1);
                        state = chunk.getBlockState(mpos);
                    }
                    color = getBlockCategoryColor(state);
                }

                grid[r * GRID_SIZE + c] = color;
                colorCounts.merge(color, 1, Integer::sum);
            }
        }

        // Find dominant color
        int dominant = COLOR_NATURAL;
        int maxCount = -1;
        for (Map.Entry<Integer, Integer> entry : colorCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }

        return new CachedChunk(grid, dominant);
    }

    private void drawChunkLOD(GuiGraphicsExtractor graphics, int cx, int cz, CachedChunk cached, int lod,
                              double cameraX, double cameraZ, double zoom, int mapCenterX, int mapCenterY,
                              int mapLeft, int mapTop, int mapW, int mapH) {
        int chunkOriginX = cx << 4;
        int chunkOriginZ = cz << 4;

        if (lod == 1) {
            int sx0 = (int) (mapCenterX + (chunkOriginX - cameraX) * zoom);
            int sz0 = (int) (mapCenterY + (chunkOriginZ - cameraZ) * zoom);
            int sx1 = (int) Math.ceil(mapCenterX + (chunkOriginX + 16 - cameraX) * zoom);
            int sz1 = (int) Math.ceil(mapCenterY + (chunkOriginZ + 16 - cameraZ) * zoom);

            int drawX0 = Math.max(mapLeft, sx0);
            int drawZ0 = Math.max(mapTop, sz0);
            int drawX1 = Math.min(mapLeft + mapW, sx1);
            int drawZ1 = Math.min(mapTop + mapH, sz1);

            if (drawX1 > drawX0 && drawZ1 > drawZ0) {
                graphics.fill(drawX0, drawZ0, drawX1, drawZ1, cached.dominantColor());
            }
            return;
        }

        int stepGrid = GRID_SIZE / lod;
        int blockSize = 16 / lod;

        for (int r = 0; r < lod; r++) {
            int gridRow = r * stepGrid;
            int startC = 0;

            while (startC < lod) {
                int gridCol = startC * stepGrid;
                int color = cached.grid8x8()[gridRow * GRID_SIZE + gridCol];

                int endC = startC + 1;
                while (endC < lod) {
                    int nextCol = endC * stepGrid;
                    if (cached.grid8x8()[gridRow * GRID_SIZE + nextCol] != color) {
                        break;
                    }
                    endC++;
                }

                // Quad spanning from startC to endC horizontally
                int bx0 = startC * blockSize;
                int bx1 = endC * blockSize;
                int bz0 = r * blockSize;
                int bz1 = (r + 1) * blockSize;

                int sx0 = (int) (mapCenterX + (chunkOriginX + bx0 - cameraX) * zoom);
                int sz0 = (int) (mapCenterY + (chunkOriginZ + bz0 - cameraZ) * zoom);
                int sx1 = (int) Math.ceil(mapCenterX + (chunkOriginX + bx1 - cameraX) * zoom);
                int sz1 = (int) Math.ceil(mapCenterY + (chunkOriginZ + bz1 - cameraZ) * zoom);

                int drawX0 = Math.max(mapLeft, sx0);
                int drawZ0 = Math.max(mapTop, sz0);
                int drawX1 = Math.min(mapLeft + mapW, sx1);
                int drawZ1 = Math.min(mapTop + mapH, sz1);

                if (drawX1 > drawX0 && drawZ1 > drawZ0) {
                    graphics.fill(drawX0, drawZ0, drawX1, drawZ1, color);
                }

                startC = endC;
            }
        }
    }

    public static int getBlockCategoryColor(BlockState state) {
        if (state == null || state.isAir()) {
            return COLOR_NATURAL;
        }

        Block block = state.getBlock();

        // 1. Blue for water / liquids
        if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN || block == Blocks.KELP 
                || block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS) {
            return COLOR_WATER;
        }
        if (!state.getFluidState().isEmpty()) {
            return COLOR_WATER;
        }

        // 2. Dark green for trees (foliage & logs)
        if (block instanceof LeavesBlock || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || block == Blocks.MANGROVE_ROOTS || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA) {
            return COLOR_TREES;
        }

        // 3. Gray for roads / paths
        if (block == Blocks.DIRT_PATH || block == Blocks.GRAVEL || block == Blocks.COBBLESTONE 
                || block == Blocks.STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS 
                || block == Blocks.CRACKED_STONE_BRICKS || block == Blocks.CHISELED_STONE_BRICKS
                || block == Blocks.SMOOTH_STONE || block == Blocks.BRICKS || block == Blocks.MUD_BRICKS
                || block == Blocks.DEEPSLATE_BRICKS || block == Blocks.POLISHED_ANDESITE
                || block == Blocks.POLISHED_DIORITE || block == Blocks.POLISHED_GRANITE
                || block == Blocks.POLISHED_BLACKSTONE || block == Blocks.POLISHED_DEEPSLATE
                || state.is(BlockTags.CONCRETE) || state.is(BlockTags.RAILS) || state.is(BlockTags.STONE_BRICKS)) {
            return COLOR_ROAD;
        }

        // 4. Light yellow for sand / beach
        if (block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.SANDSTONE 
                || block == Blocks.RED_SANDSTONE || block == Blocks.SUSPICIOUS_SAND || state.is(BlockTags.SAND)) {
            return COLOR_SAND;
        }

        // 5. Crisp white for snow / ice
        if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW 
                || block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
            return COLOR_SNOW;
        }

        // 6. Rock / mountain stone
        if (block == Blocks.STONE || block == Blocks.ANDESITE || block == Blocks.DIORITE 
                || block == Blocks.GRANITE || block == Blocks.DEEPSLATE || block == Blocks.TUFF 
                || block == Blocks.BEDROCK || block == Blocks.DRIPSTONE_BLOCK) {
            return COLOR_STONE;
        }

        // 7. Structures / Urban / Planks
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.WOOL)) {
            return COLOR_STRUCTURE;
        }

        // 8. Green for natural surface blocks (grass, dirt, moss, farmland, mud, etc.)
        return COLOR_NATURAL;
    }
}
