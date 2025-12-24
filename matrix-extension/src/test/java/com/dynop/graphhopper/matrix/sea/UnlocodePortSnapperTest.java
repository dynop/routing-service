package com.dynop.graphhopper.matrix.sea;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UnlocodePortSnapper}.
 * Tests Stage 1 port snapping: user coordinates to nearest UN/LOCODE seaport.
 */
class UnlocodePortSnapperTest {

    private UnlocodePortSnapper snapper;

    @BeforeEach
    void setUp() {
        // Create test ports
        List<Port> ports = List.of(
            createPort("NLRTM", "Rotterdam", 51.9167, 4.5),
            createPort("DEHAM", "Hamburg", 53.5333, 9.9833),
            createPort("SGSIN", "Singapore", 1.2833, 103.85),
            createPort("JPYOK", "Yokohama", 35.4433, 139.6380),
            createPort("AUSYD", "Sydney", -33.8667, 151.2167),
            createPort("USNYC", "New York", 40.7128, -74.0060),
            createPort("CNSHA", "Shanghai", 31.2304, 121.4737)
        );
        
        snapper = new UnlocodePortSnapper(ports, 300.0); // 300km max snap distance
    }

    @Test
    void snapsToNearestPort() throws PortSnapException {
        // Query point near Rotterdam
        double queryLat = 52.0;
        double queryLon = 4.3;
        
        PortSnapResult result = snapper.snap(queryLat, queryLon, PortRole.PORT_OF_LOADING);
        
        assertEquals("NLRTM", result.getUnlocode());
        assertEquals("Rotterdam", result.getName());
        assertTrue(result.getSnapDistanceKm() < 50, 
                "Should be within 50km: " + result.getSnapDistanceKm());
    }

    @Test
    void returnsCorrectSnapMetadata() throws PortSnapException {
        double queryLat = 51.9;
        double queryLon = 4.4;
        
        PortSnapResult result = snapper.snap(queryLat, queryLon, PortRole.PORT_OF_LOADING);
        
        assertEquals(queryLat, result.getOriginalLat(), 0.0001);
        assertEquals(queryLon, result.getOriginalLon(), 0.0001);
        assertEquals(PortRole.PORT_OF_LOADING, result.getRole());
        assertEquals("NEAREST_SEAPORT", result.getSnapMethod());
        assertTrue(result.getSnapDistanceKm() > 0);
    }

    @Test
    void respectsPortRole() throws PortSnapException {
        double queryLat = 51.9;
        double queryLon = 4.4;
        
        PortSnapResult pol = snapper.snap(queryLat, queryLon, PortRole.PORT_OF_LOADING);
        PortSnapResult pod = snapper.snap(queryLat, queryLon, PortRole.PORT_OF_DISCHARGE);
        
        assertEquals(PortRole.PORT_OF_LOADING, pol.getRole());
        assertEquals(PortRole.PORT_OF_DISCHARGE, pod.getRole());
        
        // Both should snap to the same port
        assertEquals(pol.getUnlocode(), pod.getUnlocode());
    }

    @Test
    void throwsWhenNoPortWithinRange() {
        // Create snapper with very small max distance
        UnlocodePortSnapper restrictedSnapper = new UnlocodePortSnapper(
            List.of(createPort("NLRTM", "Rotterdam", 51.9167, 4.5)),
            1.0 // Only 1km max
        );
        
        // Query point far from Rotterdam
        double queryLat = 45.0;
        double queryLon = 10.0;
        
        PortSnapException ex = assertThrows(PortSnapException.class,
                () -> restrictedSnapper.snap(queryLat, queryLon, PortRole.PORT_OF_LOADING));
        
        assertEquals("NO_SEAPORT_WITHIN_RANGE", ex.getErrorCode());
    }

    @ParameterizedTest
    @CsvSource({
        // Query near Rotterdam -> Rotterdam
        "52.0, 4.5, NLRTM",
        // Query near Hamburg -> Hamburg  
        "53.5, 10.0, DEHAM",
        // Query near Singapore -> Singapore
        "1.5, 104.0, SGSIN",
        // Query near Sydney -> Sydney
        "-33.5, 151.0, AUSYD",
        // Query near New York -> New York
        "40.5, -74.0, USNYC"
    })
    void selectsCorrectNearestPort(double queryLat, double queryLon, String expectedUnlocode) 
            throws PortSnapException {
        PortSnapResult result = snapper.snap(queryLat, queryLon, PortRole.PORT_OF_LOADING);
        assertEquals(expectedUnlocode, result.getUnlocode());
    }

    @Test
    void handlesAntimeridianProperly() throws PortSnapException {
        // Create snapper with ports on both sides of antimeridian
        List<Port> ports = List.of(
            createPort("FJSUV", "Suva", -18.1248, 178.4501),  // Fiji (east of antimeridian)
            createPort("NZAKL", "Auckland", -36.8485, 174.7633) // New Zealand
        );
        UnlocodePortSnapper antiSnapper = new UnlocodePortSnapper(ports, 3000.0);
        
        // Query just west of antimeridian
        double queryLat = -18.0;
        double queryLon = 179.0;
        
        PortSnapResult result = antiSnapper.snap(queryLat, queryLon, PortRole.PORT_OF_LOADING);
        assertEquals("FJSUV", result.getUnlocode(), "Should snap to Suva across antimeridian");
    }

    @Test
    void reportsPortCount() {
        assertEquals(7, snapper.getPortCount());
    }

    @Test
    void throwsWhenNoPortsLoaded() {
        UnlocodePortSnapper emptySnapper = new UnlocodePortSnapper(List.of(), 300.0);
        
        PortSnapException ex = assertThrows(PortSnapException.class,
                () -> emptySnapper.snap(51.9, 4.5, PortRole.PORT_OF_LOADING));
        
        assertEquals("NO_SEAPORT_FOUND", ex.getErrorCode());
    }

    @Test
    void snapDistanceIsReasonable() throws PortSnapException {
        // Query exactly at Rotterdam coordinates
        PortSnapResult result = snapper.snap(51.9167, 4.5, PortRole.PORT_OF_LOADING);
        
        // Should snap to Rotterdam with very small distance
        assertEquals("NLRTM", result.getUnlocode());
        assertTrue(result.getSnapDistanceKm() < 1.0, 
                "Should be very close: " + result.getSnapDistanceKm());
    }

    private Port createPort(String unlocode, String name, double lat, double lon) {
        return new Port(
            unlocode,
            name,
            unlocode.substring(0, 2),
            "",
            lat,
            lon,
            "1-------", // Function: seaport
            "AI"        // Status: approved
        );
    }
}
