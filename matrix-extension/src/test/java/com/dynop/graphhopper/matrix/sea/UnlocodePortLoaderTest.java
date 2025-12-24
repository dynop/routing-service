package com.dynop.graphhopper.matrix.sea;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UnlocodePortLoader}.
 * Tests loading and filtering of UN/LOCODE seaport data from CSV files.
 * 
 * UN/LOCODE CSV format (11 columns):
 * 0: Change, 1: Country, 2: Location, 3: Name, 4: NameAscii, 5: SubDiv, 
 * 6: Function, 7: Status, 8: Date, 9: IATA, 10: Coordinates
 */
class UnlocodePortLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSeaportsFromValidCsv() throws IOException {
        // Format: Change,Country,Location,Name,NameAscii,SubDiv,Function,Status,Date,IATA,Coordinates
        String csvContent = """
            ,"NL","RTM","Rotterdam","Rotterdam","ZH","1--3----","AI","0701",,"5155N 00430E"
            ,"SG","SIN","Singapore","Singapore","","1-345---","AI","0701",,"0117N 10351E"
            ,"DE","HAM","Hamburg","Hamburg","HH","12345---","AI","0701",,"5332N 00959E"
            """;
        
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile);
        
        assertEquals(3, ports.size());
        
        Port rotterdam = ports.stream()
                .filter(p -> "NLRTM".equals(p.getUnlocode()))
                .findFirst()
                .orElseThrow();
        assertEquals("Rotterdam", rotterdam.getName());
        assertEquals("NL", rotterdam.getCountryCode());
        assertEquals("1--3----", rotterdam.getFunction());
        assertTrue(rotterdam.getLat() > 51.0 && rotterdam.getLat() < 52.0);
        assertTrue(rotterdam.getLon() > 4.0 && rotterdam.getLon() < 5.0);
    }

    @Test
    void filtersOutNonSeaports() throws IOException {
        // Function position 1 must be '1' for seaports
        String csvContent = """
            ,"NL","RTM","Rotterdam","Rotterdam","ZH","1--3----","AI","0701",,"5155N 00430E"
            ,"NL","AMS","Amsterdam","Amsterdam","NH","-23-----","AI","0701",,"5223N 00455E"
            ,"DE","BER","Berlin","Berlin","BE","--3-----","AI","0701",,"5231N 01323E"
            """;
        
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile);
        
        assertEquals(1, ports.size());
        assertEquals("NLRTM", ports.get(0).getUnlocode());
    }

    @Test
    void filtersOutInvalidStatus() throws IOException {
        // Status must be AA, AC, AF, AI, AS, or RL
        String csvContent = """
            ,"NL","RTM","Rotterdam","Rotterdam","ZH","1--3----","AI","0701",,"5155N 00430E"
            ,"NL","XXX","Removed Port","Removed Port","ZH","1--3----","XX","0701",,"5155N 00430E"
            ,"NL","YYY","Unknown Port","Unknown Port","ZH","1--3----","RQ","0701",,"5155N 00430E"
            """;
        
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile);
        
        assertEquals(1, ports.size());
        assertEquals("NLRTM", ports.get(0).getUnlocode());
    }

    @Test
    void filtersOutMissingCoordinates() throws IOException {
        String csvContent = """
            ,"NL","RTM","Rotterdam","Rotterdam","ZH","1--3----","AI","0701",,"5155N 00430E"
            ,"NL","XXX","No Coords","No Coords","ZH","1--3----","AI","0701",,""
            ,"NL","YYY","Invalid Coords","Invalid Coords","ZH","1--3----","AI","0701",,"invalid"
            """;
        
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile);
        
        assertEquals(1, ports.size());
        assertEquals("NLRTM", ports.get(0).getUnlocode());
    }

    @Test
    void loadsFromMultipleFiles() throws IOException {
        String csv1 = """
            ,"NL","RTM","Rotterdam","Rotterdam","ZH","1--3----","AI","0701",,"5155N 00430E"
            """;
        String csv2 = """
            ,"SG","SIN","Singapore","Singapore","","1-345---","AI","0701",,"0117N 10351E"
            """;
        
        Path csvFile1 = tempDir.resolve("part1.csv");
        Path csvFile2 = tempDir.resolve("part2.csv");
        Files.writeString(csvFile1, csv1, StandardCharsets.UTF_8);
        Files.writeString(csvFile2, csv2, StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile1, csvFile2);
        
        assertEquals(2, ports.size());
    }

    @Test
    void handlesQuotedFieldsWithCommas() throws IOException {
        // The loader uses NameAscii (column 4) first for consistency
        String csvContent = """
            ,"NL","RTM","Rotterdam, Port of","Rotterdam, Port","ZH","1--3----","AI","0701",,"5155N 00430E"
            """;
        
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile);
        
        assertEquals(1, ports.size());
        // Uses NameAscii column which is "Rotterdam, Port"
        assertEquals("Rotterdam, Port", ports.get(0).getName());
    }

    @Test
    void handlesEmptyFile() throws IOException {
        Path csvFile = tempDir.resolve("empty.csv");
        Files.writeString(csvFile, "", StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile);
        
        assertTrue(ports.isEmpty());
    }

    @Test
    void identifiesMajorPorts() throws IOException {
        // Major ports have 3+ non-dash functions (e.g., "123-----" = port + rail + road)
        String csvContent = """
            ,"NL","RTM","Rotterdam","Rotterdam","ZH","123-----","AI","0701",,"5155N 00430E"
            """;
        
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile);
        
        assertEquals(1, ports.size());
        assertTrue(ports.get(0).isMajorPort());
    }

    @Test
    void detectsRailAndRoadConnections() throws IOException {
        // Position 2 = rail, Position 3 = road
        String csvContent = """
            ,"NL","RTM","Rotterdam","Rotterdam","ZH","12------","AI","0701",,"5155N 00430E"
            ,"SG","SIN","Singapore","Singapore","","1-3-----","AI","0701",,"0117N 10351E"
            ,"DE","HAM","Hamburg","Hamburg","HH","123-----","AI","0701",,"5332N 00959E"
            """;
        
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, csvContent, StandardCharsets.UTF_8);
        
        UnlocodePortLoader loader = new UnlocodePortLoader();
        List<Port> ports = loader.loadSeaports(csvFile);
        
        Port rotterdam = ports.stream().filter(p -> "NLRTM".equals(p.getUnlocode())).findFirst().orElseThrow();
        assertTrue(rotterdam.hasRailConnection(), "Rotterdam should have rail");
        assertFalse(rotterdam.hasRoadConnection(), "Rotterdam should not have road");
        
        Port singapore = ports.stream().filter(p -> "SGSIN".equals(p.getUnlocode())).findFirst().orElseThrow();
        assertFalse(singapore.hasRailConnection(), "Singapore should not have rail");
        assertTrue(singapore.hasRoadConnection(), "Singapore should have road");
        
        Port hamburg = ports.stream().filter(p -> "DEHAM".equals(p.getUnlocode())).findFirst().orElseThrow();
        assertTrue(hamburg.hasRailConnection(), "Hamburg should have rail");
        assertTrue(hamburg.hasRoadConnection(), "Hamburg should have road");
    }
}
