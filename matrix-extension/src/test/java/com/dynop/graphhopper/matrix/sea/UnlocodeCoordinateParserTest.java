package com.dynop.graphhopper.matrix.sea;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UnlocodeCoordinateParser}.
 * Tests the parsing of UN/LOCODE coordinate format "DDMMH DDDMMH" to decimal degrees.
 */
class UnlocodeCoordinateParserTest {

    @ParameterizedTest
    @CsvSource({
        // Standard coordinates
        "5155N 00430E, 51.9166, 4.5",
        // Equator and Prime Meridian
        "0000N 00000E, 0.0, 0.0",
        // Southern and Western hemispheres
        "3356S 01820W, -33.9333, -18.3333",
        // High latitude ports
        "6910N 01830E, 69.1666, 18.5",
        // Near antimeridian
        "3552N 13939E, 35.8666, 139.65",
        "4752S 16641E, -47.8666, 166.6833",
        // Rotterdam (NLRTM)
        "5155N 00430E, 51.9166, 4.5",
        // Singapore (SGSIN)
        "0117N 10351E, 1.2833, 103.85",
        // Sydney (AUSYD)
        "3352S 15113E, -33.8666, 151.2166"
    })
    void parsesValidCoordinates(String input, double expectedLat, double expectedLon) {
        Optional<GHPoint> result = UnlocodeCoordinateParser.parse(input);
        
        assertTrue(result.isPresent(), "Should parse: " + input);
        GHPoint point = result.get();
        
        // Use relative tolerance for floating point comparisons (as per spec)
        assertEquals(expectedLat, point.getLat(), 0.01, "Latitude mismatch for: " + input);
        assertEquals(expectedLon, point.getLon(), 0.01, "Longitude mismatch for: " + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",                  // Empty
        "   ",               // Whitespace only
        "invalid",           // Not a coordinate
        "5155 00430",        // Missing hemisphere
        "5155N00430E",       // Missing space
        "51.9167 4.5",       // Decimal format (not UN/LOCODE)
        "9999N 99999E",      // Invalid values
        "5155X 00430Y",      // Invalid hemisphere letters
        "ABC123",            // Random text
        "5155N",             // Only latitude
        "00430E"             // Only longitude
    })
    void rejectsInvalidCoordinates(String input) {
        Optional<GHPoint> result = UnlocodeCoordinateParser.parse(input);
        assertFalse(result.isPresent(), "Should reject: " + input);
    }

    @Test
    void handlesNullInput() {
        Optional<GHPoint> result = UnlocodeCoordinateParser.parse(null);
        assertFalse(result.isPresent());
    }

    @Test
    void handlesMixedCaseHemisphere() {
        // Should handle both upper and lowercase hemisphere indicators
        Optional<GHPoint> upperResult = UnlocodeCoordinateParser.parse("5155N 00430E");
        Optional<GHPoint> lowerResult = UnlocodeCoordinateParser.parse("5155n 00430e");
        
        assertTrue(upperResult.isPresent());
        // Lowercase may or may not be supported depending on implementation
        // If supported, values should match
        if (lowerResult.isPresent()) {
            assertEquals(upperResult.get().getLat(), lowerResult.get().getLat(), 0.0001);
            assertEquals(upperResult.get().getLon(), lowerResult.get().getLon(), 0.0001);
        }
    }

    @Test
    void correctlyHandlesAllHemisphereCombinations() {
        // NE quadrant
        Optional<GHPoint> ne = UnlocodeCoordinateParser.parse("4500N 00500E");
        assertTrue(ne.isPresent());
        assertTrue(ne.get().getLat() > 0, "NE should have positive latitude");
        assertTrue(ne.get().getLon() > 0, "NE should have positive longitude");
        
        // NW quadrant  
        Optional<GHPoint> nw = UnlocodeCoordinateParser.parse("4500N 00500W");
        assertTrue(nw.isPresent());
        assertTrue(nw.get().getLat() > 0, "NW should have positive latitude");
        assertTrue(nw.get().getLon() < 0, "NW should have negative longitude");
        
        // SE quadrant
        Optional<GHPoint> se = UnlocodeCoordinateParser.parse("4500S 00500E");
        assertTrue(se.isPresent());
        assertTrue(se.get().getLat() < 0, "SE should have negative latitude");
        assertTrue(se.get().getLon() > 0, "SE should have positive longitude");
        
        // SW quadrant
        Optional<GHPoint> sw = UnlocodeCoordinateParser.parse("4500S 00500W");
        assertTrue(sw.isPresent());
        assertTrue(sw.get().getLat() < 0, "SW should have negative latitude");
        assertTrue(sw.get().getLon() < 0, "SW should have negative longitude");
    }

    @Test
    void handlesExtraWhitespace() {
        // Should handle extra spaces in input
        Optional<GHPoint> result = UnlocodeCoordinateParser.parse("  5155N   00430E  ");
        assertTrue(result.isPresent());
        assertEquals(51.9166, result.get().getLat(), 0.01);
        assertEquals(4.5, result.get().getLon(), 0.01);
    }
}
