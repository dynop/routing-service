package com.dynop.graphhopper.matrix.sea;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChokepointRegistry}.
 * Tests loading, querying, and managing chokepoint metadata.
 */
class ChokepointRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsChokepointsFromJson() throws IOException {
        String json = """
            {
              "chokepoints": [
                {
                  "id": "SUEZ",
                  "name": "Suez Canal",
                  "region": "Middle East",
                  "lat": 30.585,
                  "lon": 32.265,
                  "radiusDegrees": 0.1,
                  "nodeIds": [100, 101, 102]
                },
                {
                  "id": "PANAMA",
                  "name": "Panama Canal",
                  "region": "Central America",
                  "lat": 9.0,
                  "lon": -79.5,
                  "radiusDegrees": 0.1,
                  "nodeIds": [200, 201]
                }
              ]
            }
            """;
        
        Path metadataFile = tempDir.resolve("chokepoint_metadata.json");
        Files.writeString(metadataFile, json);
        
        ChokepointRegistry registry = ChokepointRegistry.loadFrom(metadataFile);
        
        assertEquals(2, registry.size());
        assertNotNull(registry.getChokepoint("SUEZ"));
        assertNotNull(registry.getChokepoint("PANAMA"));
    }

    @Test
    void returnsChokepointById() throws IOException {
        String json = """
            {
              "chokepoints": [
                {
                  "id": "SUEZ",
                  "name": "Suez Canal",
                  "region": "Middle East",
                  "lat": 30.585,
                  "lon": 32.265,
                  "radiusDegrees": 0.1,
                  "nodeIds": [100, 101, 102]
                }
              ]
            }
            """;
        
        Path metadataFile = tempDir.resolve("chokepoint_metadata.json");
        Files.writeString(metadataFile, json);
        
        ChokepointRegistry registry = ChokepointRegistry.loadFrom(metadataFile);
        Chokepoint suez = registry.getChokepoint("SUEZ");
        
        assertNotNull(suez);
        assertEquals("SUEZ", suez.getId());
        assertEquals("Suez Canal", suez.getName());
        assertEquals("Middle East", suez.getRegion());
        assertEquals(30.585, suez.getLat(), 0.001);
        assertEquals(32.265, suez.getLon(), 0.001);
        assertEquals(Set.of(100, 101, 102), suez.getNodeIds());
    }

    @Test
    void returnsNullForUnknownChokepoint() {
        ChokepointRegistry registry = new ChokepointRegistry();
        assertNull(registry.getChokepoint("UNKNOWN"));
    }

    @Test
    void collectsExcludedNodeIds() throws IOException {
        String json = """
            {
              "chokepoints": [
                {
                  "id": "SUEZ",
                  "name": "Suez Canal",
                  "lat": 30.585,
                  "lon": 32.265,
                  "nodeIds": [100, 101, 102]
                },
                {
                  "id": "PANAMA",
                  "name": "Panama Canal",
                  "lat": 9.0,
                  "lon": -79.5,
                  "nodeIds": [200, 201]
                },
                {
                  "id": "MALACCA",
                  "name": "Strait of Malacca",
                  "lat": 2.5,
                  "lon": 101.0,
                  "nodeIds": [300, 301, 302, 303]
                }
              ]
            }
            """;
        
        Path metadataFile = tempDir.resolve("chokepoint_metadata.json");
        Files.writeString(metadataFile, json);
        
        ChokepointRegistry registry = ChokepointRegistry.loadFrom(metadataFile);
        
        // Exclude Suez and Panama
        Set<Integer> excluded = registry.getExcludedNodeIds(List.of("SUEZ", "PANAMA"));
        
        assertEquals(Set.of(100, 101, 102, 200, 201), excluded);
        assertFalse(excluded.contains(300), "Malacca should not be excluded");
    }

    @Test
    void ignoresUnknownChokepointIds() throws IOException {
        String json = """
            {
              "chokepoints": [
                {
                  "id": "SUEZ",
                  "name": "Suez Canal",
                  "lat": 30.585,
                  "lon": 32.265,
                  "nodeIds": [100, 101, 102]
                }
              ]
            }
            """;
        
        Path metadataFile = tempDir.resolve("chokepoint_metadata.json");
        Files.writeString(metadataFile, json);
        
        ChokepointRegistry registry = ChokepointRegistry.loadFrom(metadataFile);
        
        // Include unknown chokepoint ID
        Set<Integer> excluded = registry.getExcludedNodeIds(List.of("SUEZ", "UNKNOWN"));
        
        // Should still return Suez nodes, ignoring unknown
        assertEquals(Set.of(100, 101, 102), excluded);
    }

    @Test
    void handlesEmptyExclusionList() throws IOException {
        String json = """
            {
              "chokepoints": [
                {
                  "id": "SUEZ",
                  "name": "Suez Canal",
                  "lat": 30.585,
                  "lon": 32.265,
                  "nodeIds": [100, 101, 102]
                }
              ]
            }
            """;
        
        Path metadataFile = tempDir.resolve("chokepoint_metadata.json");
        Files.writeString(metadataFile, json);
        
        ChokepointRegistry registry = ChokepointRegistry.loadFrom(metadataFile);
        
        Set<Integer> excluded = registry.getExcludedNodeIds(List.of());
        assertTrue(excluded.isEmpty());
    }

    @Test
    void handlesNullExclusionList() throws IOException {
        String json = """
            {
              "chokepoints": [
                {
                  "id": "SUEZ",
                  "name": "Suez Canal",
                  "lat": 30.585,
                  "lon": 32.265,
                  "nodeIds": [100, 101, 102]
                }
              ]
            }
            """;
        
        Path metadataFile = tempDir.resolve("chokepoint_metadata.json");
        Files.writeString(metadataFile, json);
        
        ChokepointRegistry registry = ChokepointRegistry.loadFrom(metadataFile);
        
        Set<Integer> excluded = registry.getExcludedNodeIds(null);
        assertTrue(excluded.isEmpty());
    }

    @Test
    void savesToJson() throws IOException {
        ChokepointRegistry registry = new ChokepointRegistry();
        
        // Pass nodeIds in constructor - Chokepoint.getNodeIds() returns immutable set
        Chokepoint suez = new Chokepoint("SUEZ", "Suez Canal", "Middle East", 30.585, 32.265, 0.1, 0.01, Set.of(100, 101, 102));
        registry.addChokepoint(suez);
        
        Path outputFile = tempDir.resolve("output.json");
        registry.saveTo(outputFile);
        
        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertTrue(content.contains("SUEZ"));
        assertTrue(content.contains("Suez Canal"));
    }

    @Test
    void roundTripsChokepointData() throws IOException {
        ChokepointRegistry original = new ChokepointRegistry();
        
        // Pass nodeIds in constructor - Chokepoint.getNodeIds() returns immutable set
        Chokepoint suez = new Chokepoint("SUEZ", "Suez Canal", "Middle East", 30.585, 32.265, 0.1, 0.01, Set.of(100, 101, 102));
        original.addChokepoint(suez);
        
        Chokepoint panama = new Chokepoint("PANAMA", "Panama Canal", "Central America", 9.0, -79.5, 0.1, 0.01, Set.of(200, 201));
        original.addChokepoint(panama);
        
        Path outputFile = tempDir.resolve("roundtrip.json");
        original.saveTo(outputFile);
        
        ChokepointRegistry loaded = ChokepointRegistry.loadFrom(outputFile);
        
        assertEquals(2, loaded.size());
        
        Chokepoint loadedSuez = loaded.getChokepoint("SUEZ");
        assertNotNull(loadedSuez);
        assertEquals("Suez Canal", loadedSuez.getName());
        assertEquals(Set.of(100, 101, 102), loadedSuez.getNodeIds());
        
        Chokepoint loadedPanama = loaded.getChokepoint("PANAMA");
        assertNotNull(loadedPanama);
        assertEquals("Panama Canal", loadedPanama.getName());
        assertEquals(Set.of(200, 201), loadedPanama.getNodeIds());
    }

    @Test
    void addChokepointAddsNewChokepoint() {
        ChokepointRegistry registry = new ChokepointRegistry();
        assertEquals(0, registry.size());
        
        Chokepoint suez = new Chokepoint("SUEZ", "Suez Canal", "Middle East", 30.585, 32.265, 0.1, 0.01);
        registry.addChokepoint(suez);
        
        assertEquals(1, registry.size());
        assertNotNull(registry.getChokepoint("SUEZ"));
    }
}
