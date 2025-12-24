package com.dynop.graphhopper.matrix.sea;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChokepointAwareEdgeFilter}.
 * Tests the edge filtering logic for chokepoint exclusion.
 */
class ChokepointAwareEdgeFilterTest {

    private EdgeIteratorState edge;

    @BeforeEach
    void setUp() {
        edge = mock(EdgeIteratorState.class);
    }

    @Test
    void acceptsEdgeWhenNoExclusions() {
        ChokepointAwareEdgeFilter filter = new ChokepointAwareEdgeFilter(Set.of());
        
        when(edge.getBaseNode()).thenReturn(10);
        when(edge.getAdjNode()).thenReturn(20);
        
        assertTrue(filter.accept(edge));
    }

    @Test
    void rejectsEdgeWithExcludedBaseNode() {
        ChokepointAwareEdgeFilter filter = new ChokepointAwareEdgeFilter(Set.of(10, 11, 12));
        
        when(edge.getBaseNode()).thenReturn(10);  // Excluded
        when(edge.getAdjNode()).thenReturn(20);   // Not excluded
        
        assertFalse(filter.accept(edge));
    }

    @Test
    void rejectsEdgeWithExcludedAdjNode() {
        ChokepointAwareEdgeFilter filter = new ChokepointAwareEdgeFilter(Set.of(20, 21, 22));
        
        when(edge.getBaseNode()).thenReturn(10);  // Not excluded
        when(edge.getAdjNode()).thenReturn(20);   // Excluded
        
        assertFalse(filter.accept(edge));
    }

    @Test
    void rejectsEdgeWithBothNodesExcluded() {
        ChokepointAwareEdgeFilter filter = new ChokepointAwareEdgeFilter(Set.of(10, 20));
        
        when(edge.getBaseNode()).thenReturn(10);  // Excluded
        when(edge.getAdjNode()).thenReturn(20);   // Excluded
        
        assertFalse(filter.accept(edge));
    }

    @Test
    void acceptsEdgeWhenNeitherNodeExcluded() {
        ChokepointAwareEdgeFilter filter = new ChokepointAwareEdgeFilter(Set.of(100, 101, 102));
        
        when(edge.getBaseNode()).thenReturn(10);  // Not in exclusion set
        when(edge.getAdjNode()).thenReturn(20);   // Not in exclusion set
        
        assertTrue(filter.accept(edge));
    }

    @Test
    void handlesLargeExclusionSet() {
        // Create a large set of excluded nodes
        Set<Integer> excluded = Set.of(
            100, 101, 102, 103, 104, 105, 106, 107, 108, 109,
            200, 201, 202, 203, 204, 205, 206, 207, 208, 209,
            300, 301, 302, 303, 304, 305, 306, 307, 308, 309
        );
        
        ChokepointAwareEdgeFilter filter = new ChokepointAwareEdgeFilter(excluded);
        
        // Edge with excluded base node
        when(edge.getBaseNode()).thenReturn(205);
        when(edge.getAdjNode()).thenReturn(50);
        assertFalse(filter.accept(edge));
        
        // Edge with no excluded nodes
        when(edge.getBaseNode()).thenReturn(50);
        when(edge.getAdjNode()).thenReturn(60);
        assertTrue(filter.accept(edge));
    }

    @Test
    void multipleExcludedChokepointsWorkTogether() {
        // Simulate Suez (100-102) and Panama (200-201) exclusions
        Set<Integer> suezAndPanama = Set.of(100, 101, 102, 200, 201);
        
        ChokepointAwareEdgeFilter filter = new ChokepointAwareEdgeFilter(suezAndPanama);
        
        // Edge through Suez
        when(edge.getBaseNode()).thenReturn(101);
        when(edge.getAdjNode()).thenReturn(50);
        assertFalse(filter.accept(edge), "Suez edge should be rejected");
        
        // Edge through Panama
        when(edge.getBaseNode()).thenReturn(50);
        when(edge.getAdjNode()).thenReturn(200);
        assertFalse(filter.accept(edge), "Panama edge should be rejected");
        
        // Edge avoiding both
        when(edge.getBaseNode()).thenReturn(50);
        when(edge.getAdjNode()).thenReturn(60);
        assertTrue(filter.accept(edge), "Non-chokepoint edge should be accepted");
    }
}
