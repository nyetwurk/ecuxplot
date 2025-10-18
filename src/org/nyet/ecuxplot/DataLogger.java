package org.nyet.ecuxplot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLogger {

    private static final Logger logger = LoggerFactory.getLogger(DataLogger.class);

    // Constants for logger types
    public static final String UNKNOWN = "UNKNOWN";

    // ============================================================================
    // CONFIGURATION STORAGE - SINGLE MAP OF CONFIG OBJECTS
    // ============================================================================
    private static Map<String, DataLoggerConfig> loggerConfigs = new HashMap<>();

    // ============================================================================
    // FIELD CATEGORIES - LOADED FROM YAML/XML (GLOBAL)
    // ============================================================================
    private static Map<String, String[]> fieldCategories = new HashMap<>();

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

	public ParserConfig(String[][] aliases, double timeTicksPerSec, int skipLines,
			   String[] headerFormatTokens, SkipRegex[] skipRegex, FieldTransformation fieldTransformation, String unitRegex) {
	    this.aliases = aliases;
	    this.timeTicksPerSec = timeTicksPerSec;
	    this.skipLines = skipLines;
	    this.headerFormatTokens = headerFormatTokens;
	    this.skipRegex = skipRegex;
	    this.fieldTransformation = fieldTransformation;
	    this.unitRegex = unitRegex;
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

	public void processAliases(String[] h) {
	    DataLogger.processAliases(h, this.type);
	}

	/**
	 * Apply field transformations (prepend/append) to field names
	 * @param id Array of field names to transform
	 * @param id2 Array of original field names (id2) to use when field is empty
	 */
	public void applyFieldTransformations(String[] id, String[] id2) {
	    FieldTransformation transformation = this.parser.fieldTransformation;
	    if (transformation == null) {
		return;
	    }

	    for (int i = 0; i < id.length; i++) {
		// Check if field should be excluded
		if (isExcluded(id[i], transformation.excludeFields)) {
		    continue;
		}

		// Check if transformations should only apply to empty fields
		boolean isEmpty = (id[i] == null || id[i].trim().isEmpty());
		if (transformation.ifEmpty && !isEmpty) {
		    continue;
		}

		// For empty fields with ifEmpty=true, use original field name if available
		String fieldToTransform = id[i];
		if (transformation.ifEmpty && isEmpty && id2 != null && i < id2.length && id2[i] != null) {
		    fieldToTransform = id2[i];
		}

		// Apply prepend
		if (transformation.prepend != null && !transformation.prepend.isEmpty()) {
		    id[i] = transformation.prepend + fieldToTransform;
		}

		// Apply append
		if (transformation.append != null && !transformation.append.isEmpty()) {
		    id[i] = fieldToTransform + transformation.append;
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

	    // Parse field categories if present
	    NodeList fieldCategoriesNodes = document.getElementsByTagName("field_categories");
	    if (fieldCategoriesNodes.getLength() > 0) {
		Element fieldCategoriesElement = (Element) fieldCategoriesNodes.item(0);
		NodeList childNodes = fieldCategoriesElement.getChildNodes();
		logger.debug("Found field categories section");

		for (int i = 0; i < childNodes.getLength(); i++) {
		    if (childNodes.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
			Element categoryElement = (Element) childNodes.item(i);
			String categoryName = categoryElement.getTagName();
			NodeList fieldNodes = categoryElement.getElementsByTagName("item");
			String[] fields = new String[fieldNodes.getLength()];

			for (int j = 0; j < fieldNodes.getLength(); j++) {
			    fields[j] = fieldNodes.item(j).getTextContent();
			}

			fieldCategories.put(categoryName, fields);
			logger.debug("Loaded field category '{}' with {} fields", categoryName, fields.length);
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

			// Skip field_categories - already processed
			if ("field_categories".equals(childTagName)) {
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

				    // Create DetectionConfig and ParserConfig objects
				    DetectionConfig detectionConfig = new DetectionConfig(loggerType, commentSigs, fieldSigs);
				    ParserConfig parserConfig = new ParserConfig(loggerAliases, loggerTimeTicksPerSec, loggerSkipLines,
									  headerFormatTokensArray, skipRegexArray, fieldTransformation, unitRegex);

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

    private static String[][] which(String loggerType) {
	// Find the aliases for this logger type
	DataLoggerConfig config = loggerConfigs.get(loggerType);
	if (config != null) {
	    logger.debug("which('{}'): found config with {} aliases", loggerType, config.parser.aliases.length);
	    return config.parser.aliases;
	}

	// If not found, use DEFAULT logger aliases
	DataLoggerConfig defaultConfig = loggerConfigs.get("DEFAULT");
	if (defaultConfig != null) {
	    logger.debug("which('{}'): using DEFAULT config with {} aliases", loggerType, defaultConfig.parser.aliases.length);
	    return defaultConfig.parser.aliases;
	}

	// Fallback to empty aliases if nothing found
	logger.debug("which('{}'): returning empty aliases", loggerType);
	return new String[0][0];
    }

    public static void processAliases(String[] h, String loggerType) {
	logger.debug("processAliases called with loggerType='{}', will use aliases from which(loggerType)", loggerType);
	String[][] aliasesToUse = which(loggerType);
	logger.debug("which('{}') returned {} aliases", loggerType, aliasesToUse.length);
	processAliases(h, aliasesToUse);
    }

    public static void processAliases(String[] h) {
	processAliases(h, UNKNOWN);
    }

    public static void processAliases(String[] h, String[][] a) {
	for(int i=0;i<h.length;i++) {
	    // logger.debug("{}: '{}'", i, h[i]);
	    for (final String [] s: a) {
		if (h[i].matches(s[0])) {
		    // Only rename the first one
		    if (!ArrayUtils.contains(h, s[1])) {
			//logger.debug("{}: '{}'->'{}'", i, h[i], s[1]);
			h[i]=s[1];
		    }
		}
	    }
	    // Make sure all columns are unique
	    if (i>0 && h[i].length() > 0) {
		String[] prev = Arrays.copyOfRange(h, 0, i);
		String renamed = h[i];
		boolean rename = false;
		for (int j = 2; ArrayUtils.contains(prev, renamed); j++)  {
		    renamed = h[i] + " " + Integer.toString(j);
		    // logger.debug("{}: renamed to '{}'", i, renamed);
		    rename = true;
		}
		if (rename) h[i] = renamed;
	    }
	}
    }

    // ============================================================================
    // PARSER CONFIGURATION GETTERS - ACTIVE FOR FUTURE PARSING PHASE
    // ============================================================================

    public static DataLoggerConfig getConfig(String type) {
	DataLoggerConfig config = loggerConfigs.get(type);
	if (config == null) {
	    // Return a default config for unknown logger types
	    DetectionConfig defaultDetection = new DetectionConfig(UNKNOWN, new CommentSignature[0], new FieldSignature[0]);
	    ParserConfig defaultParser = new ParserConfig(new String[0][0], 1.0, 0, new String[]{"id"}, new SkipRegex[0], new FieldTransformation(null, null, new String[0], false), null);
	    return new DataLoggerConfig(UNKNOWN, defaultDetection, defaultParser);
	}
	return config;
    }

    // ============================================================================
    // FIELD CATEGORY ENUM AND ACCESS METHODS
    // ============================================================================

    public enum FieldCategory {
	PEDAL("pedal"),
	THROTTLE("throttle"),
	GEAR("gear");

	private final String categoryName;

	FieldCategory(String categoryName) {
	    this.categoryName = categoryName;
	}

	public String[] fields() {
	    return fieldCategories.getOrDefault(categoryName, new String[0]);
	}
    }

    public static String[] fields(FieldCategory category) {
	return category.fields();
    }

    // Concise wrapper methods for common field categories
    public static String[] pedal() {
	return fields(FieldCategory.PEDAL);
    }

    public static String[] throttle() {
	return fields(FieldCategory.THROTTLE);
    }

    public static String[] gear() {
	return fields(FieldCategory.GEAR);
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    public static boolean isUnknown(String type) {
	return type == null || UNKNOWN.equals(type);
    }
}
