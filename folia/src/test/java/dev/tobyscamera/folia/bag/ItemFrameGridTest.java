package dev.tobyscamera.folia.bag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class ItemFrameGridTest {
    @Test
    void mapsTileCoordinatesUsingAConsistentVisibleRightAndDownForEveryFace() {
        assertEquals(new GridVector(-1, 0, 0), ItemFrameGrid.forFace(BlockFace.NORTH).right());
        assertEquals(new GridVector(0, -1, 0), ItemFrameGrid.forFace(BlockFace.NORTH).down());
        assertEquals(new GridVector(1, 0, 0), ItemFrameGrid.forFace(BlockFace.SOUTH).right());
        assertEquals(new GridVector(0, 0, -1), ItemFrameGrid.forFace(BlockFace.EAST).right());
        assertEquals(new GridVector(0, 0, 1), ItemFrameGrid.forFace(BlockFace.WEST).right());
        assertEquals(new GridVector(1, 0, 0), ItemFrameGrid.forFace(BlockFace.UP).right());
        assertEquals(new GridVector(0, 0, 1), ItemFrameGrid.forFace(BlockFace.UP).down());
        assertEquals(new GridVector(1, 0, 0), ItemFrameGrid.forFace(BlockFace.DOWN).right());
        assertEquals(new GridVector(0, 0, -1), ItemFrameGrid.forFace(BlockFace.DOWN).down());
    }

    @Test
    void calculatesRowMajorOffsets() {
        ItemFrameGrid grid = ItemFrameGrid.forFace(BlockFace.NORTH);
        assertEquals(new GridVector(-2, -3, 0), grid.offset(2, 3));
    }

    @Test
    void derivesTheTopLeftAnchorOffsetForAnyClickedGridMember() {
        ItemFrameGrid grid = ItemFrameGrid.forFace(BlockFace.NORTH);

        assertEquals(new GridVector(0, 0, 0), grid.anchorOffsetForMember(0, 0));
        assertEquals(new GridVector(1, 1, 0), grid.anchorOffsetForMember(1, 1));
    }
}
