package com.dynop.graphhopper.matrix.sea;

import com.graphhopper.util.shapes.GHPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loader for UN/LOCODE seaport data from official CSV files.
 * 
 * <p>This loader processes the official UN/LOCODE CSV files and filters entries to include only:
 * <ul>
 *   <li>Locations with Function position 1 = '1' (seaports)</li>
 *   <li>Locations with valid status codes (AA, AC, AF, AI, AS, RL)</li>
 *   <li>Locations with valid coordinates</li>
 *   <li>Locations not marked for removal (Ch ≠ 'X')</li>
 * </ul>
 * 
 * <h2>CSV Column Structure</h2>
 * <table>
 *   <tr><th>Index</th><th>Name</th><th>Description</th></tr>
 *   <tr><td>0</td><td>Ch</td><td>Change indicator (+, #, X, |, =, !)</td></tr>
 *   <tr><td>1</td><td>Country</td><td>ISO 3166 alpha-2 country code</td></tr>
 *   <tr><td>2</td><td>Location</td><td>3-character location code</td></tr>
 *   <tr><td>3</td><td>Name</td><td>Location name (with diacritics)</td></tr>
 *   <tr><td>4</td><td>NameWoDiacritics</td><td>Name without diacritics</td></tr>
 *   <tr><td>5</td><td>SubDiv</td><td>ISO 3166-2 subdivision code</td></tr>
 *   <tr><td>6</td><td>Function</td><td>8-digit function classifier</td></tr>
 *   <tr><td>7</td><td>Status</td><td>Entry status code</td></tr>
 *   <tr><td>8</td><td>Date</td><td>Last update date (YYMM)</td></tr>
 *   <tr><td>9</td><td>IATA</td><td>IATA code if different</td></tr>
 *   <tr><td>10</td><td>Coordinates</td><td>Geographic coordinates</td></tr>
 * </table>
 * 
 * @see Port
 * @see UnlocodeCoordinateParser
 */
public final class UnlocodePortLoader {
    
    private static final Logger LOGGER = Logger.getLogger(UnlocodePortLoader.class.getName());
    
    // Column indices
    private static final int COL_CHANGE = 0;
    private static final int COL_COUNTRY = 1;
    private static final int COL_LOCATION = 2;
    private static final int COL_NAME = 3;
    private static final int COL_NAME_ASCII = 4;
    private static final int COL_SUBDIV = 5;
    private static final int COL_FUNCTION = 6;
    private static final int COL_STATUS = 7;
    private static final int COL_DATE = 8;
    private static final int COL_IATA = 9;
    private static final int COL_COORDINATES = 10;
    
    // Valid status codes for reliable port entries
    private static final Set<String> VALID_STATUSES = Set.of(
        "AA", // Approved by government agency
        "AC", // Approved by Customs Authority
        "AF", // Approved by facilitation body
        "AI", // Adopted by IATA/ECLAC
        "AS", // Approved by standardisation body
        "RL"  // Recognised location (verified)
    );
    
    /**
     * Load seaports from UN/LOCODE CSV files.
     * 
     * <p>Filters to include only:
     * <ul>
     *   <li>Locations with Function position 1 = '1' (ports)</li>
     *   <li>Locations with valid status codes</li>
     *   <li>Locations with valid coordinates</li>
     *   <li>Locations not marked for removal</li>
     * </ul>
     * 
     * @param csvFiles Paths to UN/LOCODE CSV files (CodeListPart1, Part2, Part3)
     * @return List of valid seaports
     * @throws IOException if reading files fails
     */
    public List<Port> loadSeaports(Path... csvFiles) throws IOException {
        List<Port> ports = new ArrayList<>();
        
        for (Path csvFile : csvFiles) {
            if (!Files.exists(csvFile)) {
                LOGGER.warning(() -> "UN/LOCODE file not found: " + csvFile);
                continue;
            }
            
            int[] lineNumber = {0};
            int portsFromFile = 0;
            
            try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNumber[0]++;
                    try {
                        Port port = parseAndCreatePort(line);
                        if (port != null) {
                            ports.add(port);
                            portsFromFile++;
                        }
                    } catch (Exception e) {
                        final int currentLine = lineNumber[0];
                        LOGGER.log(Level.FINE, () -> 
                            String.format("Error parsing line %d in %s: %s", 
                                currentLine, csvFile.getFileName(), e.getMessage()));
                    }
                }
            }
            
            int finalPortsFromFile = portsFromFile;
            LOGGER.info(() -> String.format("Loaded %d seaports from %s", 
                finalPortsFromFile, csvFile.getFileName()));
        }
        
        LOGGER.info(() -> String.format("Total seaports loaded: %d", ports.size()));
        return ports;
    }
    
    /**
     * Parse a CSV line and create a Port if it meets all filtering criteria.
     * 
     * @param line CSV line to parse
     * @return Port object if valid, null otherwise
     */
    private Port parseAndCreatePort(String line) {
        String[] cols = parseCSVLine(line);
        if (cols.length < 11) {
            return null;
        }
        
        // Skip entries marked for removal
        String changeIndicator = cols[COL_CHANGE].trim();
        if ("X".equals(changeIndicator)) {
            return null;
        }
        
        // Skip country header rows (location code is empty)
        String locationCode = cols[COL_LOCATION].trim();
        if (locationCode.isEmpty()) {
            return null;
        }
        
        // Filter: Must be a seaport (Function position 1 = '1')
        String function = cols[COL_FUNCTION].trim();
        if (function.isEmpty() || function.charAt(0) != '1') {
            return null;
        }
        
        // Filter: Must have valid status
        String status = cols[COL_STATUS].trim();
        if (!VALID_STATUSES.contains(status)) {
            return null;
        }
        
        // Filter: Must have coordinates
        Optional<GHPoint> coordOpt = UnlocodeCoordinateParser.parse(cols[COL_COORDINATES]);
        if (coordOpt.isEmpty()) {
            return null;
        }
        
        GHPoint coord = coordOpt.get();
        String countryCode = cols[COL_COUNTRY].trim();
        String unlocode = countryCode + locationCode;  // e.g., "NLRTM"
        String name = cols[COL_NAME_ASCII].trim();     // Use ASCII name for consistency
        String subdivision = cols[COL_SUBDIV].trim();
        
        // Use original name if ASCII name is empty
        if (name.isEmpty()) {
            name = cols[COL_NAME].trim();
        }
        
        return new Port(
            unlocode,
            name,
            countryCode,
            subdivision,
            coord.getLat(),
            coord.getLon(),
            function,
            status
        );
    }
    
    /**
     * Parse a CSV line handling quoted fields and commas within quotes.
     * 
     * @param line CSV line to parse
     * @return Array of field values
     */
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                // Handle escaped quotes (two consecutive quotes)
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // Skip the next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        
        return fields.toArray(new String[0]);
    }
}
