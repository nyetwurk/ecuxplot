package org.nyet.ecuxplot;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVReader;

import org.nyet.logfile.Dataset.DatasetId;

public class DataLogger {

    private static final Logger logger = LoggerFactory.getLogger(DataLogger.class);

    // Constants for logger types
    public static final String UNKNOWN = "UNKNOWN";

    // ============================================================================
    // CONFIGURATION STORAGE - SINGLE MAP OF CONFIG OBJECTS
    // ============================================================================
    private static Map<String, DataLoggerConfig> loggerConfigs = new HashMap<>();

    // ============================================================================
    // FIELD PREFERENCES - LOADED FROM YAML/XML (GLOBAL)
    // ============================================================================
    private static Map<String, String> fieldPreferences = new HashMap<>();

    // ============================================================================
    // HEADER DATA CLASS
    // ============================================================================

    /**
     * Encapsulates parsed header data (field names, units, and group/block detection arrays).
     */
    public static class HeaderData {
        private static final Logger logger = LoggerFactory.getLogger(HeaderData.class);

        public String[] g;     // Group line
        public String[] id;    // Field names array - post Aliasing
        public String[] u;     // Units array - sometimes a continuation of id
        public String[] id2;   // Original field names array, as read directly from the CSV, possibly modified by field transformations
        public String[] u2;    // Second units array - units if u is not a continuation of id or empty

        public HeaderData(String[] g, String[] id, String[] u, String[] id2, String[] u2) {
            this.g = g;
            this.id = id;
            this.u = u;
            this.id2 = id2;
            this.u2 = u2;
        }

        /**
         * Process and normalize units for this HeaderData.
         * Fills out any missing units to match the length of field names,
         * normalizes existing units, and guesses units from field names where missing.
         *
         * @return This HeaderData object for method chaining
         */
        public HeaderData processUnits() {
            if (this.id == null) {
                return this;
            }

            // Fill pad out any units missing to match length of field names
            if (this.u == null || this.u.length < this.id.length) {
                this.u = java.util.Arrays.copyOf(this.u != null ? this.u : new String[0], this.id.length);
            }

            for (int i = 0; i < this.id.length; i++) {
                // Convert to conventional units
                String originalUnit = this.u[i];
                this.u[i] = Units.normalize(this.u[i]);
                if (this.id[i].length() > 0 && (this.u[i] == null || this.u[i].length() == 0)) {
                    // Whatever is missing, try to guess from name
                    String inferredUnit = Units.find(this.id[i]);
                    if (inferredUnit != null && inferredUnit.length() > 0) {
                        logger.debug("{}: field '{}' unit inferred from name: '{}'->'{}'", i, this.id[i], originalUnit, inferredUnit);
                        this.u[i] = inferredUnit;
                    } else {
                        if (this.id[i].length() > 0) {
                            logger.warn("Can't find units for '{}'", this.id[i]);
                        }
                    }
                }
            }
            return this;
        }

        /**
         * Parse units from field names using regex pattern.
         * Extracts units from field names in format "FieldName (unit)".
         * Modifies the field names to remove the unit part and populates the units array.
         *
         * @param verbose Verbose logging level
         * @return This HeaderData object for method chaining
         */
        public HeaderData parseUnits(int verbose) {
            if (this.id == null) {
                return this;
            }

            // Preserve existing units if they've already been extracted (e.g., by unit_regex)
            // Only create new array if units haven't been initialized OR if the array is empty
            // (parseHeaderFormat initializes u as new String[0] which is not null but empty)
            if (this.u == null || this.u.length == 0) {
                this.u = new String[this.id.length];
            } else {
                // Ensure array is the right length
                if (this.u.length < this.id.length) {
                    this.u = java.util.Arrays.copyOf(this.u, this.id.length);
                }
            }

            for (int i = 0; i < this.id.length; i++) {
                // Check if field name still contains units in parentheses (e.g., from alias targets like "AirFuelRatioDesired (AFR)")
                final java.util.regex.Pattern unitsRegEx =
                    java.util.regex.Pattern.compile("([\\S\\s]+)\\(([\\S\\s].*)\\)");
                final java.util.regex.Matcher matcher = unitsRegEx.matcher(this.id[i]);
                if (matcher.find()) {
                    // Extract unit from field name even if unit was already set (e.g., from unit_regex)
                    // This handles alias targets that include units: "FieldName (unit)" -> "FieldName" + unit
                    // Trim needed: Regex groups contain untrimmed content from regex extraction
                    // Example: "AirFuelRatioDesired (AFR)" -> id[i]="AirFuelRatioDesired", u[i]="AFR"
                    this.id[i] = matcher.group(1).trim();
                    this.u[i] = matcher.group(2).trim();
                } else {
                    // No unit pattern in field name - only try to extract if unit is missing
                    if (this.u[i] != null && this.u[i].length() > 0) {
                        continue; // Skip - units already extracted and field name has no unit pattern
                    }
                    // If unit is missing and no pattern found, processUnits() will try Units.find() later
                }
            }
            for (int i = 0; i < this.id.length; i++)
                logger.trace("pu: '{}' [{}]", this.id[i], this.u[i]);
            return this;
        }

        /**
         * Process aliases for field names using the specified logger type.
         * Transforms field names according to alias mappings defined for the logger type.
         *
         * @param loggerType The logger type to use for alias lookup
         * @return This HeaderData object for method chaining
         */
        public HeaderData processAliases(String loggerType) {
            if (this.id == null) {
                return this;
            }

            logger.debug("processAliases called with loggerType='{}', will use aliases from which(loggerType)", loggerType);
            String[][] aliasesToUse = DataLogger.which(loggerType);
            logger.debug("which('{}') returned {} aliases", loggerType, aliasesToUse.length);

            // Get ME7_ALIASES map for O(1) lookup
            java.util.Map<String, String> me7AliasesMap = getMe7AliasesMap();

            for(int i = 0; i < this.id.length; i++) {
                // logger.debug("{}: '{}'", i, this.id[i]);

                // First check ME7_ALIASES map for exact match (O(1))
                if (me7AliasesMap != null && this.id[i] != null) {
                    String target = me7AliasesMap.get(this.id[i]);
                    if (target != null) {
                        if (!org.apache.commons.lang3.ArrayUtils.contains(this.id, target)) {
                            logger.debug("{}: ME7 alias '{}'->'{}'", i, this.id[i], target);
                            this.id[i] = target;
                            continue;
                        }
                    }
                }

                // Then check regex-based aliases (logger-specific and DEFAULT)
                for (final String [] s: aliasesToUse) {
                    if (this.id[i] != null && this.id[i].matches(s[0])) {
                        logger.debug("{}: alias '{}'->'{}'", i, this.id[i], s[1]);
                        this.id[i] = s[1];
                        break; // Stop after first matching alias
                    }
                }
            }

            return this;
        }

        /**
         * Ensure all field names in the id array are unique by appending numeric suffixes
         * to duplicate names (e.g., "Field", "Field 2", "Field 3").
         */
        private void ensureUniqueFieldNames() {
            if (this.id == null) {
                return;
            }

            for (int i = 0; i < this.id.length; i++) {
                if (i > 0 && this.id[i].length() > 0) {
                    String[] prev = java.util.Arrays.copyOfRange(this.id, 0, i);
                    String renamed = this.id[i];
                    boolean rename = false;
                    for (int j = 2; org.apache.commons.lang3.ArrayUtils.contains(prev, renamed); j++)  {
                        renamed = this.id[i] + " " + Integer.toString(j);
                        // logger.debug("{}: renamed to '{}'", i, renamed);
                        rename = true;
                    }
                    if (rename) this.id[i] = renamed;
                }
                // logger.debug("{}: renamed to '{}'", i, this.id[i]);
            }
        }


        @Override
        public String toString() {
            java.lang.StringBuilder sb = new java.lang.StringBuilder();
            dumpArray(sb, "g", this.g);
            dumpArray(sb, "id", this.id);
            dumpArray(sb, "u", this.u);
            dumpArray(sb, "id2", this.id2);
            dumpArray(sb, "u2", this.u2);
            return sb.toString();
        }

        private static void dumpArray(java.lang.StringBuilder sb, String name, String[] arr) {
            sb.append(name).append(":");
            if (arr != null) {
                for (int i = 0; i < arr.length; i++) {
                    sb.append(" ").append("[").append(i).append("]:'").append(arr[i]).append("', ");
                }
                sb.append("\n");
            } else {
                sb.append(" ").append(name).append(" is null\n");
            }
        }

    }

    // ============================================================================
    // HEADER PROCESSING INTERFACE AND REGISTRY
    // ============================================================================

    /**
     * Functional interface for logger-specific header processing.
     * Implementations can register custom header processing logic for specific logger types.
     */
    @FunctionalInterface
    public interface HeaderProcessor {
        /**
         * Process headers for a specific logger type.
         *
         * @param h Header data containing id, u, and id2 arrays (will be modified)
         * @param loggerType Logger type name
         */
        void processHeaders(HeaderData h, String loggerType);
    }

    // Registry for logger-specific header processors
    private static Map<String, HeaderProcessor> headerProcessors = new HashMap<>();

    // Helper method to safely parse integer attributes from XML
    private static int parseXmlIntAttribute(Element element, String attributeName, int defaultValue) {
        String value = element.getAttribute(attributeName);
        if (value != null && !value.isEmpty() && !value.equals("None")) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Return default if parsing fails
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // Helper method to safely parse string attributes from XML
    private static String parseXmlStringAttribute(Element element, String attributeName, String defaultValue) {
        String value = element.getAttribute(attributeName);
        if (value != null && !value.isEmpty() && !value.equals("None")) {
            return value;
        }
        return defaultValue;
    }

    // Helper method to safely parse boolean attributes from XML
    private static boolean parseXmlBooleanAttribute(Element element, String attributeName, boolean defaultValue) {
        String value = element.getAttribute(attributeName);
        if (value != null && !value.isEmpty()) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    // Helper method to safely parse double attributes from XML
    private static double parseXmlDoubleAttribute(Element element, String attributeName, double defaultValue) {
        String value = element.getAttribute(attributeName);
        if (value != null && !value.isEmpty() && !value.equals("None")) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // Helper method to parse skip_regex patterns from XML
    private static SkipRegex[] parseSkipRegex(Element loggerElement) {
        NodeList skipRegexNodes = loggerElement.getElementsByTagName("skip_regex");
        if (skipRegexNodes.getLength() == 0) {
            return new SkipRegex[0];
        }

        NodeList itemNodes = ((Element) skipRegexNodes.item(0)).getElementsByTagName("item");
        SkipRegex[] skipRegexArray = new SkipRegex[itemNodes.getLength()];

        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element itemElement = (Element) itemNodes.item(i);
            String regex = itemElement.getAttribute("regex");
            int column = parseXmlIntAttribute(itemElement, "column", -1);
            skipRegexArray[i] = new SkipRegex(regex, column);
        }

        return skipRegexArray;
    }

    // Helper method to parse field transformations from XML
    private static FieldTransformation parseFieldTransformation(Element loggerElement) {
        NodeList fieldTransformationNodes = loggerElement.getElementsByTagName("field_transformations");
        if (fieldTransformationNodes.getLength() == 0) {
            return new FieldTransformation(null, null, new String[0], false);
        }

        Element fieldTransformationElement = (Element) fieldTransformationNodes.item(0);
        String prepend = parseXmlStringAttribute(fieldTransformationElement, "prepend", null);
        String append = parseXmlStringAttribute(fieldTransformationElement, "append", null);
        boolean ifEmpty = parseXmlBooleanAttribute(fieldTransformationElement, "if_empty", false);

        // Parse exclude_fields if present
        String[] excludeFields = new String[0];
        NodeList excludeFieldsNodes = fieldTransformationElement.getElementsByTagName("exclude_fields");
        if (excludeFieldsNodes.getLength() > 0) {
            NodeList itemNodes = ((Element) excludeFieldsNodes.item(0)).getElementsByTagName("item");
            excludeFields = new String[itemNodes.getLength()];
            for (int i = 0; i < itemNodes.getLength(); i++) {
                excludeFields[i] = itemNodes.item(i).getTextContent();
            }
        }

        return new FieldTransformation(prepend, append, excludeFields, ifEmpty);
    }

    private static String parseUnitRegex(Element loggerElement) {
        String unitRegex = loggerElement.getAttribute("unit_regex");
        if (unitRegex == null || unitRegex.trim().isEmpty()) {
            return null;
        }
        return unitRegex.trim();
    }

    // ============================================================================
    // DETECTION SIGNATURE CLASSES
    // ============================================================================

    // Signature for comment detection
    public static class CommentSignature {
        public final String regex;
        public final String type;

        public CommentSignature(String regex, String type) {
            this.regex = regex;
            this.type = type;
        }
    }

    // Signature for field detection
    public static class FieldSignature {
        public final String regex;
        public final String type;
        public final Integer columnIndex; // null means any column

        public FieldSignature(String regex, String type) {
            this.regex = regex;
            this.type = type;
            this.columnIndex = null; // any column
        }

        public FieldSignature(String regex, String type, int columnIndex) {
            this.regex = regex;
            this.type = type;
            this.columnIndex = columnIndex;
        }
    }

    // Detection configuration for a single logger
    public static class DetectionConfig {
        public final String type;
        public final CommentSignature[] commentSignatures;
        public final FieldSignature[] fieldSignatures;

        public DetectionConfig(String type, CommentSignature[] commentSignatures, FieldSignature[] fieldSignatures) {
            this.type = type;
            this.commentSignatures = commentSignatures;
            this.fieldSignatures = fieldSignatures;
        }
    }

    // Skip regex configuration for dynamic line skipping
    public static class SkipRegex {
        public final String regex;
        public final int column;

        public SkipRegex(String regex, int column) {
            this.regex = regex;
            this.column = column;
        }
    }

    // Field transformation configuration for a single logger
    public static class FieldTransformation {
        public final String prepend;
        public final String append;
        public final String[] excludeFields;
        public final boolean ifEmpty;

        public FieldTransformation(String prepend, String append, String[] excludeFields, boolean ifEmpty) {
            this.prepend = prepend;
            this.append = append;
            this.excludeFields = excludeFields;
            this.ifEmpty = ifEmpty;
        }
    }

    // Parser configuration for a single logger
    public static class ParserConfig {
        public final String[][] aliases;
        public final double timeTicksPerSec;
        public final int skipLines;
        public final String[] headerFormatTokens;
        public final SkipRegex[] skipRegex;
        public final FieldTransformation fieldTransformation;
        public final String unitRegex;
        public final String aliasesFrom;

        public ParserConfig(String[][] aliases, double timeTicksPerSec, int skipLines,
                           String[] headerFormatTokens, SkipRegex[] skipRegex, FieldTransformation fieldTransformation, String unitRegex, String aliasesFrom) {
            this.aliases = aliases;
            this.timeTicksPerSec = timeTicksPerSec;
            this.skipLines = skipLines;
            this.headerFormatTokens = headerFormatTokens;
            this.skipRegex = skipRegex;
            this.fieldTransformation = fieldTransformation;
            this.unitRegex = unitRegex;
            this.aliasesFrom = aliasesFrom;
        }
    }

    // Configuration class for a single logger
    public static class DataLoggerConfig {
        public final String type;
        public final DetectionConfig detection;
        public final ParserConfig parser;

        public DataLoggerConfig(String type, DetectionConfig detection, ParserConfig parser) {
            this.type = type;
            this.detection = detection;
            this.parser = parser;
        }

        // Instance methods for cleaner API
        public boolean hasToken(String token) {
            for (String formatToken : this.parser.headerFormatTokens) {
                if (token.equals(formatToken)) {
                    return true;
                }
            }
            return false;
        }

        public String[] getHeaderFormatTokens() {
            return this.parser.headerFormatTokens;
        }

        public int getSkipLines() {
            return this.parser.skipLines;
        }


        public double getTimeTicksPerSec() {
            return this.parser.timeTicksPerSec;
        }

        public String[][] getAliases() {
            return this.parser.aliases;
        }

        public SkipRegex[] getSkipRegex() {
            return this.parser.skipRegex;
        }

        public String getType() {
            return this.detection.type;
        }

        public CommentSignature[] getCommentSignatures() {
            return this.detection.commentSignatures;
        }

        public FieldSignature[] getFieldSignatures() {
            return this.detection.fieldSignatures;
        }

        /**
         * Process headers using registered processor for this logger type.
         * If no processor is registered for the logger type, no processing is performed.
         *
         * @param h Header data containing id, u, and id2 arrays (will be modified)
         */
        public void processLoggerHeaders(HeaderData h) {
            HeaderProcessor processor = headerProcessors.get(this.type);
            if (processor != null) {
                logger.debug("Processing headers for logger type: {}", this.type);
                processor.processHeaders(h, this.type);
            } else {
                logger.debug("No header processor registered for logger type: {}", this.type);
            }
        }


        /**
         * Process skip lines and skip regex patterns.
         * Returns the matched line if skip_regex found a match, null otherwise.
         *
         * @param reader CSVReader to read lines from
         * @return String[] of the matched line, or null if no match found
         * @throws Exception if reading fails
         */
        public String[] processSkipLines(CSVReader reader) throws Exception {
            int skipLines = this.getSkipLines();
            DataLogger.SkipRegex[] skipRegex = this.getSkipRegex();

            // Only skip if either skipLines or skipRegex are defined
            boolean do_skip = skipLines > 0 || skipRegex.length > 0;
            if (!do_skip) {
                return null;
            }

            boolean found = false;
            int skipped = 0;
            String[] matchedLine = null; // Store the line that matched skip_regex

            while (!found && skipped < skipLines) {
                String[] line = reader.readNext();
                if (line == null) {
                    logger.error("Reached end of file while skipping lines for {}", this.type);
                    break;
                }

                // Skip empty lines - they don't count towards skip_lines
                if (line.length == 0 || (line.length == 1 && (line[0] == null || line[0].trim().isEmpty()))) {
                    logger.debug("{} {}: Skipped empty line", this.type, skipped);
                    continue;
                }

                // Test line against regex patterns (if any)
                if (skipRegex.length > 0) {
                    for (DataLogger.SkipRegex skipRegexItem : skipRegex) {
                        boolean match = false;
                        if (skipRegexItem.column == -1) {
                            // Check any column (loop through all fields)
                            for (int i = 0; i < line.length; i++) {
                                String cellValue = line[i];
                                if (cellValue != null && cellValue.matches(skipRegexItem.regex)) {
                                    logger.debug("{}: Found skip_regex match '{}' in column {}: '{}'", this.type, skipRegexItem.regex, i, cellValue);
                                    match = true;
                                    break;
                                }
                            }
                        } else if (line.length > skipRegexItem.column) {
                            // Check specific column
                            String cellValue = line[skipRegexItem.column];
                            if (cellValue != null && cellValue.matches(skipRegexItem.regex)) {
                                logger.debug("{}: Found skip_regex match '{}' in column {}: '{}'", this.type, skipRegexItem.regex, skipRegexItem.column, cellValue);
                                match = true;
                            }
                        }
                        if (match) {
                            found = true;
                            matchedLine = line; // Store the matched line
                            break; // Found match, exit loop
                        }
                    }
                }

                // Count this non-empty line
                skipped++;
                logger.debug("{} {}/{}: Skipped {}", this.type, skipped, skipLines, line);
            }

            return matchedLine;
        }

        /**
         * Parse header format and return the parsed header data.
         *
         * @param reader CSVReader to read header lines from
         * @param matchedLine Previously matched line from skip processing, or null
         * @return HeaderData containing id, u, and id2 arrays, or null if parsing fails
         * @throws Exception if reading fails
         */
        public HeaderData parseHeaderFormat(CSVReader reader, String[] matchedLine) throws Exception {
            // Generic header format parsing (YAML feature)
            // Note that the default header format is "id" (single header line with field names), so there should always be at least one header token
            String[] formatTokens = this.getHeaderFormatTokens();
            logger.debug("Processing header format for {}: {}", this.type, formatTokens);

            String[][] headerLines = new String[formatTokens.length][];
            int lineNum = 0;

            // If we found a regex match, use that line as the first header line
            if (matchedLine != null) {
                headerLines[0] = matchedLine;
                lineNum = 1;
                logger.debug("Using matched line as headerLines[0]: {} fields", matchedLine.length);
            }

            // Read remaining header lines
            while (lineNum < formatTokens.length && (headerLines[lineNum] = reader.readNext()) != null) {
                logger.debug("Read headerLines {}/{}: {} fields", lineNum+1, formatTokens.length, headerLines[lineNum].length);
                lineNum++;
            }

            if (lineNum < formatTokens.length) {
                logger.error("Expected {} lines but only found {}", formatTokens.length, lineNum);
                logger.error("Skipping tokens: {}", java.util.Arrays.toString(java.util.Arrays.copyOfRange(formatTokens, lineNum, formatTokens.length)));
            }

            // Parse the header format tokens
            String[] g = null, id = null, u = null, u2 = null, id2 = null;

            // If lineNum < formatTokens.length, we will not be able to handle all the tokens
            for (int i = 0; i < lineNum; i++) {
                switch (formatTokens[i]) {
                    case "g":
                        g = copyAndTrim(headerLines[i]);
                        logger.debug("Groups: Using {}: {} fields", g, headerLines[i].length);
                        break;
                    case "id":
                        id = copyAndTrim(headerLines[i]);
                        // Only populate id2 with original field names if no explicit id2 token exists
                        if (!this.hasToken("id2")) {
                            id2 = copyAndTrim(headerLines[i]);
                            logger.debug("ID2 from ID: Using {}: {} fields", id2, headerLines[i].length);
                        }
                        logger.debug("ID: Using {}: {} fields", id, headerLines[i].length);
                        break;
                    case "u":
                        u = copyAndTrim(headerLines[i]);
                        logger.debug("Units: {}: {} fields", u, headerLines[i].length);
                        break;
                    case "u2":
                        if (!allFieldsAreNumeric(headerLines[i], true)) {
                            // u2 has the real units, append the original units to the id
                            append(id, u);
                            logger.debug("Appended old u to id: {}: {} fields", id, headerLines[i].length);

                            u = copyAndTrim(headerLines[i]);
                            logger.debug("Units (from u2): {}: {} fields", u, headerLines[i].length);
                        }
                        u2 = copyAndTrim(headerLines[i]);
                        logger.debug("Units2: {}: {} fields", u2, headerLines[i].length);
                        break;
                    case "id2":
                        id2 = copyAndTrim(headerLines[i]);
                        logger.debug("ID2: Using {}: {} fields", id2, headerLines[i].length);
                        break;
                    default:
                        logger.error("Unknown token '{}' at position {}", formatTokens[i], i);
                        break;
                }
            }

            return new HeaderData(g, id, u, id2, u2);
        }

        /**
         * Apply unit regex parsing if configured.
         * This extracts field names and units from combined field names using regex patterns.
         *
         * @param h Header data containing id, u, and id2 arrays (will be modified)
         */
        public void applyUnitRegex(HeaderData h) {
            if (this.parser.unitRegex == null) {
                return;
            }

            logger.debug("Applying unit_regex: '{}'", this.parser.unitRegex);
            java.util.regex.Pattern unitRegexPattern = java.util.regex.Pattern.compile(this.parser.unitRegex);

            String[] id = h.id;
            String[] u = h.u;
            String[] id2 = h.id2;

            // Initialize units array if not already done
            if (u == null || u.length == 0) {
                u = new String[id.length];
                h.u = u;
            }

            // Initialize id2 array to preserve original field names
            if (id2 == null) {
                id2 = new String[id.length];
                h.id2 = id2;
            }

            for (int i = 0; i < id.length; i++) {
                if (id[i] != null) {
                    // Always preserve original field name in id2 before any processing
                    String originalField = id[i];
                    if (id2[i] == null) {
                        id2[i] = originalField;
                    }

                    java.util.regex.Matcher matcher = unitRegexPattern.matcher(id[i]);
                    if (matcher.find()) {
                        // Group 1 -> field name, Group 2 -> unit, Group 3 -> additional info (optional, e.g., id2 for VOLVOLOGGER)
                        id[i] = matcher.group(1).trim();
                        u[i] = matcher.group(2).trim();
                        // Use Group 3 for id2 ONLY if it's non-empty AND looks like a valid field name
                        // (e.g., VOLVOLOGGER's short field names like "RPM", "BoostPressure")
                        // Don't use Group 3 if it's just punctuation (e.g., "]" from incorrectly matched nested parens)
                        // If Group 3 is empty or invalid, keep the original field name in id2 (for alias mechanism to work)
                        if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
                            String group3 = matcher.group(3).trim();
                            // Only use Group 3 if it looks like a field name (not just punctuation/short garbage)
                            if (!group3.isEmpty() && group3.length() > 2 && !group3.matches("^[\\]\\[\\)\\(,\\.]+$")) {
                                id2[i] = group3;
                            }
                            // If group3 is empty or invalid, do nothing - id2 already has original field name from line 685
                        }
                        // Otherwise, id2 already has the original field name from line 685
                        logger.debug("Unit regex matched field {}: '{}' -> id='{}', unit='{}', id2='{}'", i, originalField, id[i], u[i], id2[i]);
                    } else {
                        // No regex match - keep original field name in both id and id2
                        // id2 already has the original field name
                        logger.debug("Unit regex did not match field {}: '{}', preserving in id2", i, id[i]);
                    }
                }
            }
        }

        /**
         * Apply general unit parsing for loggers that don't have their own unit processing.
         * Only applies if logger doesn't have header_format with units ("u" token).
         *
         * @param h Header data containing id, u, and id2 arrays (will be modified)
         * @param verbose Verbose logging level
         */
        public void applyGeneralUnitParsing(HeaderData h, int verbose) {
            if (DataLogger.isUnknown(this.type)) {
                return;
            }

            String[] loggerHeaderFormatTokens = this.getHeaderFormatTokens();
            // If no header_format or header_format doesn't include units ("u"), apply general unit parsing
            boolean hasUnitsToken = false;
            for (String token : loggerHeaderFormatTokens) {
                if ("u".equals(token)) {
                    hasUnitsToken = true;
                    break;
                }
            }
            if (loggerHeaderFormatTokens.length == 0 || !hasUnitsToken) {
                h.parseUnits(verbose);
            }
        }

        /**
         * Process all header data using this logger configuration.
         * This is the main entry point for header processing.
         *
         * @param reader CSVReader to read header lines from
         * @param verbose Verbose logging level
         * @return Processed HeaderData object, or null if processing fails
         * @throws Exception if reading fails
         */
        public HeaderData processHeaders(CSVReader reader, int verbose) throws Exception {
            // Process skip lines and regex patterns
            String[] matchedLine = this.processSkipLines(reader);

            // Parse header format
            HeaderData h = this.parseHeaderFormat(reader, matchedLine);
            if (h == null) {
                logger.error("Failed to parse header format for {}", this.type);
                return null;
            }

            this.applyUnitRegex(h); // Extract units first before aliasing (uses: unit_regex)
            h.processAliases(this.type); // Convert id to canonical names, save original to id2 for later use (uses: aliases, aliases_from, ME7_ALIASES, DEFAULT aliases)
            this.processLoggerHeaders(h); // Log format specific header processing, post aliasing (uses: registered header processors, e.g. VCDS/VCDS_LEGACY)
            this.applyGeneralUnitParsing(h, verbose); // Now units can be found for fully processed fields (uses: header_format to check for "u" token)

            // Apply field transformations for all logger types (prepend/append)
            // This must happen BEFORE unit processing so units can be found for transformed field names
            this.applyFieldTransformations(h); // (uses: field_transformations)

            // Process and normalize units
            h.processUnits(); // uses regex via Units.normalize() and Units.find() in Units class

            // Make sure all columns are unique (after all aliases are applied and units are inferred)
            h.ensureUniqueFieldNames();

            // Log final results
            for (int i = 0; i < h.id.length; i++) {
                if (h.id2 != null && i < h.id2.length) {
                    logger.debug("Final field {}: '{}' (original: '{}') [{}]", i, h.id[i], h.id2[i], h.u[i]);
                } else {
                    logger.debug("Final field {}: '{}' [{}]", i, h.id[i], h.u[i]);
                }
            }

            return h;
        }

        /**
         * Create DatasetId objects from processed header data.
         *
         * @param h Processed header data
         * @return Array of DatasetId objects
         */
        public DatasetId[] createDatasetIds(HeaderData h) {
            DatasetId[] ids = new DatasetId[h.id.length];
            for (int i = 0; i < h.id.length; i++) {
                ids[i] = new DatasetId(h.id[i]);
                if (h.id2 != null && i < h.id2.length) ids[i].id2 = h.id2[i];
                if (h.u != null && i < h.u.length) ids[i].unit = h.u[i];
                ids[i].type = this.type;
            }
            return ids;
        }

        /**
         * Apply field transformations (prepend/append) to field names
         * @param h HeaderData containing field names to transform
         */
        public void applyFieldTransformations(HeaderData h) {
            FieldTransformation transformation = this.parser.fieldTransformation;
            if (transformation == null) {
                return;
            }

            for (int i = 0; i < h.id.length; i++) {
                // Check if field should be excluded
                if (isExcluded(h.id[i], transformation.excludeFields)) {
                    continue;
                }

                // Check if transformations should only apply to empty fields
                boolean isEmpty = (h.id[i] == null || h.id[i].trim().isEmpty());
                if (transformation.ifEmpty && !isEmpty) {
                    continue;
                }

                // For empty fields with ifEmpty=true, use original field name if available
                String fieldToTransform = h.id[i];
                if (transformation.ifEmpty && isEmpty && h.id2 != null && i < h.id2.length && h.id2[i] != null) {
                    fieldToTransform = h.id2[i];
                }

                // Apply prepend
                if (transformation.prepend != null && !transformation.prepend.isEmpty()) {
                    h.id[i] = transformation.prepend + fieldToTransform;
                }

                // Apply append
                if (transformation.append != null && !transformation.append.isEmpty()) {
                    h.id[i] = fieldToTransform + transformation.append;
                }
            }
        }

        /**
         * Apply field transformations to a specific field name
         * @param fieldName Field name to transform
         * @return Transformed field name
         */
        public String applyFieldTransformation(String fieldName) {
            FieldTransformation transformation = this.parser.fieldTransformation;
            if (transformation == null || fieldName == null || fieldName.isEmpty()) {
                return fieldName;
            }

            // Check if field should be excluded
            if (isExcluded(fieldName, transformation.excludeFields)) {
                return fieldName;
            }

            String result = fieldName;

            // Apply prepend
            if (transformation.prepend != null && !transformation.prepend.isEmpty()) {
                result = transformation.prepend + result;
            }

            // Apply append
            if (transformation.append != null && !transformation.append.isEmpty()) {
                result = result + transformation.append;
            }

            return result;
        }

        /**
         * Check if a field name should be excluded from transformations
         * @param fieldName Field name to check
         * @param excludeFields Array of field names to exclude
         * @return true if field should be excluded
         */
        private boolean isExcluded(String fieldName, String[] excludeFields) {
            if (excludeFields == null || excludeFields.length == 0) {
                return false;
            }

            for (String excludeField : excludeFields) {
                if (fieldName.equals(excludeField)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ============================================================================
    // CONFIGURATION LOADING
    // ============================================================================

    // Dynamic Log definitions loaded from properties file
    static {
        loadLogDefinitions();
        // Register VCDS header processor
        registerHeaderProcessor("VCDS_LEGACY", (h, type) -> VCDSHeaderProcessor.processVCDSHeader(h, type));
        registerHeaderProcessor("VCDS", (h, type) -> VCDSHeaderProcessor.processVCDSHeader(h, type));
        logger.debug("DataLogger class loaded, logger definitions loaded from XML");
    }

    private static void loadLogDefinitions() {
        try {
            InputStream is = DataLogger.class.getResourceAsStream("loggers.xml");
            if (is == null) {
                logger.error("Error: loggers.xml not found - system cannot function without configuration");
                return;
            }
            logger.info("Loading loggers.xml...");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(is);
            is.close();

            // Parse field preferences if present
            NodeList fieldPreferencesNodes = document.getElementsByTagName("field_preferences");
            if (fieldPreferencesNodes.getLength() > 0) {
                Element fieldPreferencesElement = (Element) fieldPreferencesNodes.item(0);
                // Check if field preferences are stored as attributes (YAML converts single values to attributes)
                if (fieldPreferencesElement.hasAttributes()) {
                    var attributes = fieldPreferencesElement.getAttributes();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        var attr = attributes.item(i);
                        String preferenceName = attr.getNodeName();
                        String fieldName = attr.getNodeValue();
                        fieldPreferences.put(preferenceName, fieldName);
                        logger.debug("Loaded field preference '{}' = '{}'", preferenceName, fieldName);
                    }
                } else {
                    // Legacy: check for child elements
                    NodeList childNodes = fieldPreferencesElement.getChildNodes();
                    for (int i = 0; i < childNodes.getLength(); i++) {
                        if (childNodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element preferenceElement = (Element) childNodes.item(i);
                            String preferenceName = preferenceElement.getTagName();
                            String fieldName = preferenceElement.getTextContent().trim();
                            fieldPreferences.put(preferenceName, fieldName);
                            logger.debug("Loaded field preference '{}' = '{}'", preferenceName, fieldName);
                        }
                    }
                }
            }

            // Parse logger definitions - handle nested loggers structure
            NodeList loggersNodes = document.getElementsByTagName("loggers");
            if (loggersNodes.getLength() > 0) {
                Element loggersElement = (Element) loggersNodes.item(0);
                NodeList childNodes = loggersElement.getChildNodes();
                logger.debug("Found loggers section");

                for (int i = 0; i < childNodes.getLength(); i++) {
                    if (childNodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element childElement = (Element) childNodes.item(i);
                        String childTagName = childElement.getTagName();

                        // Skip field_preferences - already processed
                        if ("field_preferences".equals(childTagName)) {
                            continue;
                        }

                        // Handle nested loggers element
                        if ("loggers".equals(childTagName)) {
                            NodeList nestedChildren = childElement.getChildNodes();
                            for (int j = 0; j < nestedChildren.getLength(); j++) {
                                if (nestedChildren.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                    Element loggerElement = (Element) nestedChildren.item(j);
                                    String loggerName = loggerElement.getTagName();
                String loggerType = loggerElement.getAttribute("type");
                logger.debug("Found logger: {} = {}", loggerName, loggerType);

                // Collect comment signatures
                CommentSignature[] commentSigs = collectCommentSignatures(loggerElement);

                // Collect field signatures
                FieldSignature[] fieldSigs = collectFieldSignatures(loggerElement);

                // Get aliases
                String[][] loggerAliases = collectAliases(loggerElement);

                                    // Get logger configuration attributes
                                    double loggerTimeTicksPerSec = parseXmlDoubleAttribute(loggerElement, "time_ticks_per_sec", 1.0);
                                    int loggerSkipLines = parseXmlIntAttribute(loggerElement, "skip_lines", 0);


                                    // Get header format and split into tokens
                                    String loggerHeaderFormat = parseXmlStringAttribute(loggerElement, "header_format", "");
                                    String[] headerFormatTokensArray = loggerHeaderFormat.isEmpty() ? new String[]{"id"} : loggerHeaderFormat.split(",");
                                    org.nyet.util.Strings.trimArray(headerFormatTokensArray);

                                    // Parse skip_regex patterns
                                    SkipRegex[] skipRegexArray = parseSkipRegex(loggerElement);

                                    // Parse field transformations
                                    FieldTransformation fieldTransformation = parseFieldTransformation(loggerElement);

                                    // Parse unit regex
                                    String unitRegex = parseUnitRegex(loggerElement);

                                    // Parse aliases_from
                                    String aliasesFrom = parseXmlStringAttribute(loggerElement, "aliases_from", null);

                                    // Create DetectionConfig and ParserConfig objects
                                    DetectionConfig detectionConfig = new DetectionConfig(loggerType, commentSigs, fieldSigs);
                                    ParserConfig parserConfig = new ParserConfig(loggerAliases, loggerTimeTicksPerSec, loggerSkipLines,
                                                                          headerFormatTokensArray, skipRegexArray, fieldTransformation, unitRegex, aliasesFrom);

                                    // Create and store DataLoggerConfig object
                                    DataLoggerConfig config = new DataLoggerConfig(loggerName, detectionConfig, parserConfig);
                                    loggerConfigs.put(loggerName, config);
                                    logger.debug("Loaded logger '{}' with {} aliases", loggerName, loggerAliases.length);
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error loading loggers.xml: {}", e.getMessage());
            logger.error("No logger definitions available - system cannot function without configuration");
        }
    }

    // ============================================================================
    // DETECTION CONFIGURATION COLLECTION METHODS
    // ============================================================================

    private static CommentSignature[] collectCommentSignatures(Element loggerElement) {
        NodeList commentNodes = loggerElement.getElementsByTagName("comment_signatures");
        if (commentNodes.getLength() == 0) return new CommentSignature[0];

        Element commentElement = (Element) commentNodes.item(0);
        NodeList signatureNodes = commentElement.getElementsByTagName("item");
        CommentSignature[] sigs = new CommentSignature[signatureNodes.getLength()];

        for (int i = 0; i < signatureNodes.getLength(); i++) {
            Element sigElement = (Element) signatureNodes.item(i);
            String regex = sigElement.getAttribute("regex");
            String type = loggerElement.getAttribute("type");
            sigs[i] = new CommentSignature(regex, type);
        }
        return sigs;
    }

    private static FieldSignature[] collectFieldSignatures(Element loggerElement) {
        NodeList fieldNodes = loggerElement.getElementsByTagName("field_signatures");
        if (fieldNodes.getLength() == 0) return new FieldSignature[0];

        Element fieldElement = (Element) fieldNodes.item(0);
        NodeList signatureNodes = fieldElement.getElementsByTagName("item");
        FieldSignature[] sigs = new FieldSignature[signatureNodes.getLength()];

        for (int i = 0; i < signatureNodes.getLength(); i++) {
            Element sigElement = (Element) signatureNodes.item(i);
            String regex = sigElement.getAttribute("regex");
            String columnIndexStr = sigElement.getAttribute("column_index");
            Integer columnIndex = null;
            if (columnIndexStr != null && !columnIndexStr.isEmpty()) {
                columnIndex = Integer.parseInt(columnIndexStr);
            }
            String type = loggerElement.getAttribute("type");
            if (columnIndex != null) {
                sigs[i] = new FieldSignature(regex, type, columnIndex);
            } else {
                sigs[i] = new FieldSignature(regex, type);
            }
        }
        return sigs;
    }

    // ============================================================================
    // PARSER CONFIGURATION COLLECTION METHODS
    // ============================================================================

    private static String[][] collectAliases(Element loggerElement) {
        NodeList aliasNodes = loggerElement.getElementsByTagName("aliases");
        if (aliasNodes.getLength() == 0) return new String[0][0];

        Element aliasElement = (Element) aliasNodes.item(0);
        NodeList aliasList = aliasElement.getElementsByTagName("item");
        String[][] aliases = new String[aliasList.getLength()][2];

        for (int i = 0; i < aliasList.getLength(); i++) {
            Element alias = (Element) aliasList.item(i);
            aliases[i][0] = alias.getAttribute("pattern");
            aliases[i][1] = alias.getAttribute("target");
        }
        return aliases;
    }


    // ============================================================================
    // DETECTION METHODS - ACTIVE FOR DETECTION PHASE
    // ============================================================================

    // New detection methods using DataLoggerConfig objects
    public static String detectComment(String comment) {
        // Check all logger types
        for (Map.Entry<String, DataLoggerConfig> entry : loggerConfigs.entrySet()) {
            String loggerName = entry.getKey();
            DataLoggerConfig config = entry.getValue();
            CommentSignature[] sigs = config.detection.commentSignatures;
            if (sigs != null) {
                for (CommentSignature sig : sigs) {
                    if (comment.matches(sig.regex)) {
                        return loggerName; // Return logger name, not type
                    }
                }
            }
        }
        return UNKNOWN;
    }

    public static String detectField(String[] fields) {
        // Check all logger types
        for (Map.Entry<String, DataLoggerConfig> entry : loggerConfigs.entrySet()) {
            String loggerName = entry.getKey();
            DataLoggerConfig config = entry.getValue();
            FieldSignature[] sigs = config.detection.fieldSignatures;
            if (sigs != null) {
                for (FieldSignature sig : sigs) {
                    if (sig.columnIndex != null) {
                        // Check specific column
                        if (sig.columnIndex < fields.length) {
                            if (fields[sig.columnIndex].matches(sig.regex)) {
                                return loggerName; // Return logger name, not type
                            }
                        }
                    } else {
                        // Check any column
                        for (int i = 0; i < fields.length; i++) {
                            if (fields[i].matches(sig.regex)) {
                                return loggerName; // Return logger name, not type
                            }
                        }
                    }
                }
            }
        }
        return UNKNOWN;
    }



    // ============================================================================
    // PARSER METHODS - ACTIVE FOR FUTURE PARSING PHASE
    // ============================================================================

    private static java.util.Map<String, String> me7AliasesMap = null;

    private static String[][] which(String loggerType) {
        // Find the aliases for this logger type
        DataLoggerConfig config = loggerConfigs.get(loggerType);

        // If this logger has aliases_from, use that logger's aliases instead
        if (config != null && config.parser.aliasesFrom != null && !config.parser.aliasesFrom.isEmpty()) {
            DataLoggerConfig fromConfig = loggerConfigs.get(config.parser.aliasesFrom);
            if (fromConfig != null) {
                config = fromConfig;
            }
        }

        DataLoggerConfig me7Config = loggerConfigs.get("ME7_ALIASES");
        DataLoggerConfig defaultConfig = loggerConfigs.get("DEFAULT");

        int totalAliases = 0;
        if (config != null) totalAliases += config.parser.aliases.length;
        // ME7_ALIASES now uses Map for efficiency (O(1) lookup vs O(n))
        if (defaultConfig != null) totalAliases += defaultConfig.parser.aliases.length;

        if (totalAliases == 0) {
            logger.debug("which('{}'): returning empty aliases", loggerType);
            return new String[0][0];
        }

        // Combine aliases: logger-specific first, then DEFAULT
        String[][] combinedAliases = new String[totalAliases][2];
        int pos = 0;

        if (config != null) {
            System.arraycopy(config.parser.aliases, 0, combinedAliases, pos, config.parser.aliases.length);
            pos += config.parser.aliases.length;
        }

        if (defaultConfig != null) {
            System.arraycopy(defaultConfig.parser.aliases, 0, combinedAliases, pos, defaultConfig.parser.aliases.length);
            pos += defaultConfig.parser.aliases.length;
        }

        logger.debug("which('{}'): returning {} combined aliases ({} logger + {} me7 map + {} default)",
                    loggerType, totalAliases,
                    config != null ? config.parser.aliases.length : 0,
                    me7Config != null ? 1 : 0,
                    defaultConfig != null ? defaultConfig.parser.aliases.length : 0);
        return combinedAliases;
    }

    private static java.util.Map<String, String> getMe7AliasesMap() {
        if (me7AliasesMap == null && loggerConfigs.containsKey("ME7_ALIASES")) {
            DataLoggerConfig me7Config = loggerConfigs.get("ME7_ALIASES");
            me7AliasesMap = new java.util.HashMap<String, String>();
            for (String[] alias : me7Config.parser.aliases) {
                me7AliasesMap.put(alias[0], alias[1]);
            }
            logger.debug("Initialized ME7_ALIASES map with {} entries", me7AliasesMap.size());
        }
        return me7AliasesMap;
    }


    // ============================================================================
    // PARSER CONFIGURATION GETTERS - ACTIVE FOR FUTURE PARSING PHASE
    // ============================================================================

    public static DataLoggerConfig getConfig(String type) {
        DataLoggerConfig config = loggerConfigs.get(type);
        if (config == null) {
            // Return a default config for unknown logger types
            // Inherit unit_regex from DEFAULT if available
            DataLoggerConfig defaultConfig = loggerConfigs.get("DEFAULT");
            String inheritedUnitRegex = null;
            if (defaultConfig != null && defaultConfig.parser.unitRegex != null) {
                inheritedUnitRegex = defaultConfig.parser.unitRegex;
            }

            DetectionConfig defaultDetection = new DetectionConfig(UNKNOWN, new CommentSignature[0], new FieldSignature[0]);
            ParserConfig defaultParser = new ParserConfig(new String[0][0], 1.0, 0, new String[]{"id"}, new SkipRegex[0], new FieldTransformation(null, null, new String[0], false), inheritedUnitRegex, null);
            return new DataLoggerConfig(UNKNOWN, defaultDetection, defaultParser);
        }
        return config;
    }

    // ============================================================================
    // FIELD CATEGORY ENUM AND ACCESS METHODS
    // ============================================================================

    public enum FieldPreference {
        PEDAL("pedal"),
        THROTTLE("throttle"),
        GEAR("gear");

        private final String preferenceName;

        FieldPreference(String preferenceName) {
            this.preferenceName = preferenceName;
        }

        public String field() {
            return fieldPreferences.getOrDefault(preferenceName, "");
        }
    }

    // Concise wrapper methods for common field preferences
    public static String pedalField() {
        return FieldPreference.PEDAL.field();
    }

    public static String throttleField() {
        return FieldPreference.THROTTLE.field();
    }

    public static String gearField() {
        return FieldPreference.GEAR.field();
    }

    // ============================================================================
    // LOGGER-SPECIFIC HEADER PROCESSING METHODS
    // ============================================================================

    /**
     * Register a header processor for a specific logger type.
     *
     * @param logType The logger type (e.g., "JB4", "ECUX", etc.)
     * @param processor The header processor implementation
     */
    public static void registerHeaderProcessor(String logType, HeaderProcessor processor) {
        headerProcessors.put(logType, processor);
        logger.debug("Registered header processor for logger type: {}", logType);
    }


    // ============================================================================
    // UTILITY METHODS
    // ============================================================================


    /**
     * Copy and trim string array elements
     */
    private static String[] copyAndTrim(String[] input) {
        if (input == null) return null;
        String[] result = new String[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = (input[i] != null) ? input[i].trim() : null;
        }
        return result;
    }

    /**
     * Check if all fields in the array are numeric
     */
    private static boolean allFieldsAreNumeric(String[] fields, boolean allowEmpty) {
        if (fields == null) return true;
        for (String field : fields) {
            if (field == null || field.trim().isEmpty()) {
                if (!allowEmpty) return false;
                continue;
            }
            try {
                Double.parseDouble(field.trim());
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean isUnknown(String type) {
        return type == null || UNKNOWN.equals(type);
    }

    /*
    * Append array elements to id array if both have content at that position
    * @param id The id array to append to
    * @param arr The array to append
    */
    private static void append(String[] id, String[] arr) {
        if (id == null || arr == null || id.length == 0 || arr.length == 0) return;
        for (int i = 0; i < id.length && i < arr.length; i++) {
            if (id[i] != null && arr[i] != null && !arr[i].trim().isEmpty()) {
                String trimmed = arr[i].trim();
                String capitalized = trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1);
                id[i] = id[i] + capitalized;
            }
        }
    }


}

// vim: set sw=4 ts=8 expandtab:
