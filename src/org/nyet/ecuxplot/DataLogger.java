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
    // FILTER ASSOCIATIONS - LOADED FROM YAML/XML (GLOBAL)
    // ============================================================================
    private static Map<String, String> filterAssociations = new HashMap<>();

    // ============================================================================
    // GLOBAL REQUIRED COLUMNS - LOADED FROM YAML/XML (GLOBAL)
    // ============================================================================
    private static String[] globalRequiredColumns = new String[0];

    // ============================================================================
    // AXIS PRESET CATEGORIES - LOADED FROM YAML/XML (GLOBAL)
    // ============================================================================
    private static Map<String, String[]> axisPresetCategories = new HashMap<>();

    // ============================================================================
    // CANONICAL UNIT STANDARDS - LOADED FROM YAML/XML (GLOBAL)
    // ============================================================================
    // List of [pattern, unit] pairs for regex matching
    // Overrides global unit preference for special case columns
    // Patterns are matched in order, first match wins
    private static java.util.List<String[]> canonicalUnitStandards = new java.util.ArrayList<>();

    // ============================================================================
    // PRESET DEFAULTS - LOADED FROM YAML/XML (GLOBAL)
    // ============================================================================
    /**
     * Holds default preset configuration (xkey, ykeys0, ykeys1, scatter)
     */
    public static class PresetDefault {
        public final String xkey;
        public final String[] ykeys0;
        public final String[] ykeys1;
        public final boolean scatter;

        public PresetDefault(String xkey, String[] ykeys0, String[] ykeys1, boolean scatter) {
            this.xkey = xkey;
            this.ykeys0 = ykeys0 != null ? ykeys0 : new String[0];
            this.ykeys1 = ykeys1 != null ? ykeys1 : new String[0];
            this.scatter = scatter;
        }
    }

    private static Map<String, PresetDefault> presetDefaults = new HashMap<>();

    // ============================================================================
    // PRESET SUPPORT PROFILES - LOADED FROM YAML/XML (GLOBAL)
    // ============================================================================
    /**
     * Represents a single profile item (either a category reference, direct column, or pattern).
     */
    public static class ProfileItem {
        public final String type;  // "category", "column", or "pattern"
        public final String value; // category name, column name, or regex pattern

        public ProfileItem(String type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    /**
     * Map structure: profileName -> presetName -> List<ProfileItem>
     */
    private static Map<String, Map<String, java.util.List<ProfileItem>>> presetSupportProfiles = new HashMap<>();

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
         * Validate if an extracted unit string is actually a valid unit or descriptive text.
         * Uses Units.normalize() to check if the unit is recognized.
         *
         * @param unit The unit string to validate
         * @return true if unit appears valid, false if it looks like descriptive text
         */
        private static boolean isValidUnit(String unit) {
            if (unit == null || unit.trim().isEmpty()) {
                return false;
            }

            String normalized = Units.normalize(unit.trim());

            // If normalize() returns empty, it was recognized as invalid
            if (normalized.isEmpty()) {
                return false;
            }

            // If normalize() returns unchanged AND string looks like descriptive text, it's invalid
            if (normalized.equals(unit.trim())) {
                // Heuristic: descriptive text usually has spaces or is very long
                // Valid units are typically short without spaces (e.g., "V", "PSI", "AFR", "λ")
                if (normalized.contains(" ") || normalized.length() > 15) {
                    return false;  // Looks like descriptive text
                }
            }

            return true;  // Valid unit
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
                // If unit was already extracted (e.g., by unit_regex), validate it first
                if (this.u[i] != null && this.u[i].length() > 0) {
                    if (!isValidUnit(this.u[i])) {
                        // Invalid unit (looks like descriptive text) - clear it for inference
                        logger.debug("{}: Extracted unit '{}' appears to be descriptive text, clearing for inference",
                                    i, this.u[i]);
                        this.u[i] = null;
                    }
                    // Note: Don't continue here - alias targets may add units to field names
                }

                // Note: Unit extraction from parentheses is handled by extractUnitsFromParentheses()
                // which is called after aliases are applied, so no need to do it here
                // If no unit pattern found and unit is missing, processUnits() will try Units.find() later
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
                logger.trace("{}: Checking alias for '{}'", i, this.id[i]);

                // First check ME7_ALIASES map for exact match (O(1))
                if (me7AliasesMap != null && this.id[i] != null) {
                    String target = me7AliasesMap.get(this.id[i]);
                    if (target != null) {
                        if (!java.util.Arrays.asList(this.id).contains(target)) {
                            logger.debug("{}: ME7 alias '{}'->'{}'", i, this.id[i], target);
                            this.id[i] = target;
                            continue;
                        }
                    }
                }

                // Then check regex-based aliases (logger-specific and DEFAULT)
                // First try matching against id (field name after unit extraction)
                boolean matched = false;
                for (final String [] s: aliasesToUse) {
                    if (this.id[i] != null && this.id[i].matches(s[0])) {
                        logger.debug("{}: alias '{}'->'{}'", i, this.id[i], s[1]);
                        this.id[i] = s[1];
                        matched = true;
                        break; // Stop after first matching alias
                    }
                }
                // If no match against id, try matching against id2 (original field name)
                // This handles cases where unit_regex removed info needed for aliasing
                if (!matched && this.id2 != null && i < this.id2.length && this.id2[i] != null) {
                    for (final String [] s: aliasesToUse) {
                        if (this.id2[i].matches(s[0])) {
                            logger.debug("{}: alias (using id2) '{}'->'{}'", i, this.id2[i], s[1]);
                            this.id[i] = s[1];
                            matched = true;
                            break; // Stop after first matching alias
                        }
                    }
                }
            }

            // Extract units from parentheses in aliased field names (e.g., "FieldName (unit)")
            // This handles alias targets that include units (e.g., "BoostPressureRelative (mBar[gauge])")
            this.extractUnitsFromParentheses();

            return this;
        }

        /**
         * Extract units from parentheses in field names (e.g., "FieldName (unit)").
         * This is called after aliases are applied to handle alias targets that include units.
         *
         * @return This HeaderData object for method chaining
         */
        private HeaderData extractUnitsFromParentheses() {
            if (this.id == null) {
                return this;
            }

            for (int i = 0; i < this.id.length; i++) {
                if (this.id[i] == null) {
                    continue;
                }

                final java.util.regex.Pattern unitsRegEx =
                    java.util.regex.Pattern.compile("([\\S\\s]+)\\(([\\S\\s].*)\\)");
                final java.util.regex.Matcher matcher = unitsRegEx.matcher(this.id[i]);
                if (matcher.find()) {
                    String extractedUnit = matcher.group(2).trim();
                    logger.debug("{}: Extracted unit from parentheses: '{}'", i, extractedUnit);

                    if (isValidUnit(extractedUnit)) {
                        this.id[i] = matcher.group(1).trim();
                        // Ensure units array exists and is large enough
                        if (this.u == null) {
                            this.u = new String[this.id.length];
                        } else if (this.u.length < this.id.length) {
                            this.u = java.util.Arrays.copyOf(this.u, this.id.length);
                        }
                        this.u[i] = extractedUnit;
                        logger.debug("{}: Extracted and validated unit '{}' from '{}'", i, extractedUnit, this.id[i]);
                    } else {
                        logger.debug("{}: Extracted unit '{}' appears to be descriptive text, leaving as is", i, extractedUnit);
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
                    for (int j = 2; java.util.Arrays.asList(prev).contains(renamed); j++)  {
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

    // ============================================================================
    // GENERIC XML PARSING HELPERS - REDUCE DUPLICATION
    // ============================================================================

    /**
     * Get element name - checks "name" attribute first (for sanitized names with spaces),
     * otherwise uses tag name.
     */
    private static String getElementName(Element element) {
        return element.hasAttribute("name") ? element.getAttribute("name") : element.getTagName();
    }

    /**
     * Parse array of strings from <item> elements under a parent element.
     */
    private static String[] parseStringArrayFromItems(Element parentElement) {
        NodeList itemNodes = parentElement.getElementsByTagName("item");
        String[] result = new String[itemNodes.getLength()];
        for (int i = 0; i < itemNodes.getLength(); i++) {
            result[i] = itemNodes.item(i).getTextContent().trim();
        }
        return result;
    }

    /**
     * Parse map of string arrays from child elements (e.g., axis_preset_categories).
     * Each child element is a key, its <item> children are the array values.
     */
    private static Map<String, String[]> parseMapOfStringArrays(Element parentElement) {
        Map<String, String[]> result = new HashMap<>();
        NodeList childNodes = parentElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element childElement = (Element) childNodes.item(i);
                String key = getElementName(childElement);
                String[] values = parseStringArrayFromItems(childElement);
                result.put(key, values);
            }
        }
        return result;
    }

    /**
     * Get attribute value or child element text content.
     * Checks attribute first, then child element with same name.
     */
    private static String getAttributeOrElementText(Element element, String name, String defaultValue) {
        if (element.hasAttribute(name)) {
            return element.getAttribute(name);
        }
        NodeList nodes = element.getElementsByTagName(name);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return defaultValue;
    }

    /**
     * Get boolean from attribute or child element.
     */
    private static boolean getAttributeOrElementBoolean(Element element, String name, boolean defaultValue) {
        if (element.hasAttribute(name)) {
            return Boolean.parseBoolean(element.getAttribute(name));
        }
        NodeList nodes = element.getElementsByTagName(name);
        if (nodes.getLength() > 0) {
            return Boolean.parseBoolean(nodes.item(0).getTextContent().trim());
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
         * Create DatasetId objects from processed header data with normalized and native units support.
         *
         * If normalizedUnits map is provided, uses normalized units for columns in the map;
         * otherwise falls back to native units from h.u[].
         * If nativeUnits map is provided, uses it for u2 (original unit intent);
         * otherwise falls back to units from h.u[].
         *
         * @param h Processed header data
         * @param normalizedUnits Map of canonical column names to normalized unit strings,
         *                       or null to use native units from h.u[]
         * @param nativeUnits Map of canonical column names to original unit intent strings
         *                    (processed/normalized, but before preference conversion),
         *                    or null to use units from h.u[]
         * @return Array of DatasetId objects
         */
        public DatasetId[] createDatasetIds(HeaderData h,
                                            java.util.Map<String, String> normalizedUnits,
                                            java.util.Map<String, String> nativeUnits) {
            DatasetId[] ids = new DatasetId[h.id.length];
            for (int i = 0; i < h.id.length; i++) {
                // Normalized unit
                String unit = null;
                if (normalizedUnits != null && normalizedUnits.containsKey(h.id[i])) {
                    unit = normalizedUnits.get(h.id[i]);
                } else if (h.u != null && i < h.u.length) {
                    unit = h.u[i];
                }

                // Original unit intent - prefer nativeUnits map (shares String reference)
                // NOTE: This is the processed unit (after normalize/find), not the raw CSV unit
                // Unlike id2 which is truly original, u2 represents "original unit intent" before preference conversion
                String u2 = null;
                if (nativeUnits != null && nativeUnits.containsKey(h.id[i])) {
                    u2 = nativeUnits.get(h.id[i]);  // Share String reference
                } else if (h.u != null && i < h.u.length) {
                    u2 = h.u[i];  // Share String reference (processed unit from h.u[])
                }

                ids[i] = new DatasetId(h.id[i],
                                      (h.id2 != null && i < h.id2.length) ? h.id2[i] : null,
                                      unit, u2);
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

            // Parse filter associations if present
            NodeList filterAssociationsNodes = document.getElementsByTagName("filter_associations");
            if (filterAssociationsNodes.getLength() > 0) {
                Element filterAssociationsElement = (Element) filterAssociationsNodes.item(0);
                // Check if filter associations are stored as attributes (YAML converts single values to attributes)
                if (filterAssociationsElement.hasAttributes()) {
                    var attributes = filterAssociationsElement.getAttributes();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        var attr = attributes.item(i);
                        String associationName = attr.getNodeName();
                        String fieldName = attr.getNodeValue();
                        filterAssociations.put(associationName, fieldName);
                        logger.debug("Loaded filter association '{}' = '{}'", associationName, fieldName);
                    }
                } else {
                    // Legacy: check for child elements
                    NodeList childNodes = filterAssociationsElement.getChildNodes();
                    for (int i = 0; i < childNodes.getLength(); i++) {
                        if (childNodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element associationElement = (Element) childNodes.item(i);
                            String associationName = associationElement.getTagName();
                            String fieldName = associationElement.getTextContent().trim();
                            filterAssociations.put(associationName, fieldName);
                            logger.debug("Loaded filter association '{}' = '{}'", associationName, fieldName);
                        }
                    }
                }
            }

            // Parse global required columns if present
            NodeList globalColumnsNodes = document.getElementsByTagName("global_required_columns");
            if (globalColumnsNodes.getLength() > 0) {
                Element globalColumnsElement = (Element) globalColumnsNodes.item(0);
                globalRequiredColumns = parseStringArrayFromItems(globalColumnsElement);
                logger.debug("Loaded {} global required columns", globalRequiredColumns.length);
            }

            // Parse canonical unit standards if present
            NodeList canonicalUnitStandardsNodes = document.getElementsByTagName("canonical_unit_standards");
            if (canonicalUnitStandardsNodes.getLength() > 0) {
                Element canonicalUnitStandardsElement = (Element) canonicalUnitStandardsNodes.item(0);

                // Backward compatibility: Check if stored as attributes (old YAML format: BaroPressure: "mBar")
                if (canonicalUnitStandardsElement.hasAttributes()) {
                    var attributes = canonicalUnitStandardsElement.getAttributes();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        var attr = attributes.item(i);
                        String columnName = attr.getNodeName();
                        String unit = attr.getNodeValue();
                        // Convert exact match to regex pattern (escape special regex chars)
                        // matches() automatically anchors, so no need for ^ and $
                        String pattern = java.util.regex.Pattern.quote(columnName);
                        canonicalUnitStandards.add(new String[]{pattern, unit});
                        logger.debug("Loaded canonical unit standard (attribute format) '{}' = '{}'", columnName, unit);
                    }
                }

                // New format: Parse as list of items (YAML list format: [["pattern", "unit"], ...])
                // YAML converter uses "target" attribute for second element in 2-item list
                NodeList itemList = canonicalUnitStandardsElement.getElementsByTagName("item");
                for (int i = 0; i < itemList.getLength(); i++) {
                    Element item = (Element) itemList.item(i);
                    String pattern = item.getAttribute("pattern");
                    String unit = item.getAttribute("target");
                    if (pattern != null && !pattern.isEmpty() && unit != null && !unit.isEmpty()) {
                        canonicalUnitStandards.add(new String[]{pattern, unit});
                        logger.debug("Loaded canonical unit standard pattern '{}' = '{}'", pattern, unit);
                    }
                }
                logger.debug("Loaded {} canonical unit standards", canonicalUnitStandards.size());
            }

            // Parse axis preset categories if present
            NodeList categoriesNodes = document.getElementsByTagName("axis_preset_categories");
            if (categoriesNodes.getLength() > 0) {
                Element categoriesElement = (Element) categoriesNodes.item(0);
                axisPresetCategories = parseMapOfStringArrays(categoriesElement);
                logger.debug("Loaded {} axis preset categories", axisPresetCategories.size());
            }

            // Parse preset defaults if present
            NodeList presetDefaultsNodes = document.getElementsByTagName("preset_defaults");
            if (presetDefaultsNodes.getLength() > 0) {
                Element presetDefaultsElement = (Element) presetDefaultsNodes.item(0);
                NodeList presetNodes = presetDefaultsElement.getChildNodes();
                for (int i = 0; i < presetNodes.getLength(); i++) {
                    if (presetNodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element presetElement = (Element) presetNodes.item(i);
                        String presetName = getElementName(presetElement);

                        String xkey = getAttributeOrElementText(presetElement, "xkey", null);
                        String[] ykeys0 = new String[0];
                        String[] ykeys1 = new String[0];
                        boolean scatter = getAttributeOrElementBoolean(presetElement, "scatter", false);

                        // Parse ykeys0
                        NodeList ykeys0Nodes = presetElement.getElementsByTagName("ykeys0");
                        if (ykeys0Nodes.getLength() > 0) {
                            ykeys0 = parseStringArrayFromItems((Element) ykeys0Nodes.item(0));
                        }

                        // Parse ykeys1
                        NodeList ykeys1Nodes = presetElement.getElementsByTagName("ykeys1");
                        if (ykeys1Nodes.getLength() > 0) {
                            ykeys1 = parseStringArrayFromItems((Element) ykeys1Nodes.item(0));
                        }

                        if (xkey != null) {
                            PresetDefault presetDefault = new PresetDefault(xkey, ykeys0, ykeys1, scatter);
                            presetDefaults.put(presetName, presetDefault);
                            logger.debug("Loaded preset default '{}' with xkey='{}', {} ykeys0, {} ykeys1, scatter={}",
                                    presetName, xkey, ykeys0.length, ykeys1.length, scatter);
                        }
                    }
                }
            }

            // Parse preset support profiles if present
            NodeList presetSupportProfilesNodes = document.getElementsByTagName("preset_support_profiles");
            if (presetSupportProfilesNodes.getLength() > 0) {
                Element profilesElement = (Element) presetSupportProfilesNodes.item(0);
                NodeList profileNodes = profilesElement.getChildNodes();
                for (int i = 0; i < profileNodes.getLength(); i++) {
                    if (profileNodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element profileElement = (Element) profileNodes.item(i);
                        String profileName = getElementName(profileElement);
                        Map<String, java.util.List<ProfileItem>> profilePresets = new HashMap<>();

                        // Iterate through preset elements (e.g., Timing, Fueling, Power)
                        NodeList presetNodes = profileElement.getChildNodes();
                        for (int j = 0; j < presetNodes.getLength(); j++) {
                            if (presetNodes.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element presetElement = (Element) presetNodes.item(j);
                                String presetName = getElementName(presetElement);
                                java.util.List<ProfileItem> items = new java.util.ArrayList<>();

                                // Parse items (category, column, or pattern references)
                                NodeList itemNodes = presetElement.getElementsByTagName("item");
                                for (int k = 0; k < itemNodes.getLength(); k++) {
                                    Element itemElement = (Element) itemNodes.item(k);
                                    if (itemElement.hasAttribute("category")) {
                                        items.add(new ProfileItem("category", itemElement.getAttribute("category")));
                                    } else if (itemElement.hasAttribute("column")) {
                                        items.add(new ProfileItem("column", itemElement.getAttribute("column")));
                                    } else if (itemElement.hasAttribute("pattern")) {
                                        items.add(new ProfileItem("pattern", itemElement.getAttribute("pattern")));
                                    }
                                }

                                if (!items.isEmpty()) {
                                    profilePresets.put(presetName, items);
                                }
                            }
                        }

                        if (!profilePresets.isEmpty()) {
                            presetSupportProfiles.put(profileName, profilePresets);
                            logger.debug("Loaded preset support profile '{}' with {} presets", profileName, profilePresets.size());
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

                        // Skip filter_associations - already processed
                        if ("filter_associations".equals(childTagName)) {
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
    // FILTER ASSOCIATION ENUM AND ACCESS METHODS
    // ============================================================================

    public enum FilterAssociation {
        PEDAL("pedal"),
        THROTTLE("throttle"),
        GEAR("gear");

        private final String associationName;

        FilterAssociation(String associationName) {
            this.associationName = associationName;
        }

        public String field() {
            return filterAssociations.getOrDefault(associationName, "");
        }
    }

    // Concise wrapper methods for common filter associations
    public static String pedalField() {
        return FilterAssociation.PEDAL.field();
    }

    public static String throttleField() {
        return FilterAssociation.THROTTLE.field();
    }

    public static String gearField() {
        return FilterAssociation.GEAR.field();
    }

    // ============================================================================
    // PRESET DEFAULTS - ACCESS METHODS
    // ============================================================================

    /**
     * Get preset default configuration by preset name.
     * @param presetName Name of the preset (e.g., "Power", "Timing")
     * @return PresetDefault object or null if not found
     */
    public static PresetDefault getPresetDefault(String presetName) {
        return presetDefaults.get(presetName);
    }

    /**
     * Get all available preset default names.
     * @return Array of preset names
     */
    public static String[] getPresetDefaultNames() {
        return presetDefaults.keySet().toArray(new String[0]);
    }

    /**
     * Get global required columns.
     * @return Array of canonical column names that should exist in all log formats
     */
    public static String[] getGlobalRequiredColumns() {
        return globalRequiredColumns;
    }

    /**
     * Get canonical unit standard for a column.
     * Returns the configured preferred unit string or null if no special case is configured.
     * Matches patterns in order, first match wins.
     *
     * @param canonicalName The canonical column name
     * @return Preferred unit string, or null if not configured
     */
    public static String getCanonicalUnitStandard(String canonicalName) {
        if (canonicalName == null || canonicalName.isEmpty()) {
            return null;
        }
        // Match patterns in order, first match wins
        // Java's matches() requires full string match, so patterns must match entire canonical name
        for (String[] patternUnit : canonicalUnitStandards) {
            if (canonicalName.matches(patternUnit[0])) {
                return patternUnit[1];
            }
        }
        return null;
    }

    /**
     * Get columns for a specific axis preset category.
     * @param categoryName Name of the category
     * @return Array of column names in the category, or empty array if category not found
     */
    public static String[] getAxisPresetCategory(String categoryName) {
        return axisPresetCategories.getOrDefault(categoryName, new String[0]);
    }

    /**
     * Get all available axis preset category names.
     * @return Set of category names
     */
    public static java.util.Set<String> getAxisPresetCategoryNames() {
        return axisPresetCategories.keySet();
    }

    // ============================================================================
    // PRESET SUPPORT PROFILES - ACCESS METHODS
    // ============================================================================

    /**
     * Get preset support profile by profile name.
     * @param profileName Name of the profile (e.g., "full_timing", "partial_timing")
     * @return Map of preset name to list of ProfileItems, or null if profile not found
     */
    public static Map<String, java.util.List<ProfileItem>> getPresetSupportProfile(String profileName) {
        return presetSupportProfiles.get(profileName);
    }

    /**
     * Get all available preset support profile names.
     * @return Set of profile names
     */
    public static java.util.Set<String> getPresetSupportProfileNames() {
        return presetSupportProfiles.keySet();
    }

    /**
     * Expand a profile's preset items into a set of canonical column names.
     * Categories are expanded using axis_preset_categories, columns are added directly.
     * Patterns are added as-is (with pattern prefix) and must be matched at test time.
     * @param profileName Name of the profile
     * @param presetName Name of the preset within the profile
     * @return Set of canonical column names or patterns (patterns prefixed with "pattern:"), or empty set if profile/preset not found
     */
    public static java.util.Set<String> expandProfilePreset(String profileName, String presetName) {
        java.util.Set<String> columns = new java.util.HashSet<>();
        Map<String, java.util.List<ProfileItem>> profile = presetSupportProfiles.get(profileName);
        if (profile == null) {
            return columns;
        }

        java.util.List<ProfileItem> items = profile.get(presetName);
        if (items == null) {
            return columns;
        }

        for (ProfileItem item : items) {
            if ("category".equals(item.type)) {
                // Expand category
                String[] categoryColumns = getAxisPresetCategory(item.value);
                for (String column : categoryColumns) {
                    columns.add(column);
                }
            } else if ("column".equals(item.type)) {
                // Add column directly
                columns.add(item.value);
            } else if ("pattern".equals(item.type)) {
                // Add pattern with prefix for later matching
                columns.add("pattern:" + item.value);
            }
        }

        return columns;
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
     * Copy and trim string array elements.
     * Also removes trailing " ()" or "()" patterns from column names, which can occur
     * when loggers include empty unit information in headers (e.g., "FieldName ()").
     */
    private static String[] copyAndTrim(String[] input) {
        if (input == null) return null;
        String[] result = new String[input.length];
        for (int i = 0; i < input.length; i++) {
            if (input[i] != null) {
                String trimmed = input[i].trim();
                // Remove trailing " ()" or "()" patterns
                while (trimmed.endsWith(" ()") || trimmed.endsWith("()")) {
                    trimmed = trimmed.replaceAll("\\s*\\(\\)$", "");
                }
                result[i] = trimmed;
            } else {
                result[i] = null;
            }
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
