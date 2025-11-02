package org.nyet.ecuxplot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes VCDS headers and extracts group ID mappings from header data.
 */
public class VCDSHeaderProcessor {
    private static final Logger logger = LoggerFactory.getLogger(VCDSHeaderProcessor.class);

    /**
     * VCDS-specific group data associated with a HeaderData instance.
     */
    private static class VCDSGroupData {
        String[] columnToGroupId;  // Array of group IDs indexed by column (e.g., "115")
        String firstGroupId;  // First group ID found (cached for quick access)
        String lastGroupId;   // Last group ID found (cached for quick access)
    }

    // Map to store VCDS group data for each HeaderData instance
    private static final java.util.Map<DataLogger.HeaderData, VCDSGroupData> groupDataMap = new java.util.HashMap<>();

    /**
     * Column structure that stores factual data about a column.
     * Questions about the column are answered via methods.
     */
    private static class ColumnInfo {
        final int index;                // Column index (factual)
        final String fieldName;         // Field name from h.id[index] (factual)
        final String groupId;           // Group ID this column belongs to (factual, or null)

        ColumnInfo(int index, String fieldName, String groupId) {
            this.index = index;
            this.fieldName = fieldName;
            this.groupId = groupId;
        }

        /**
         * Does this column have identical field names in the same group?
         */
        boolean hasIdenticalInSameGroup(ColumnInfo[] allColumns) {
            if (fieldName == null || groupId == null) {
                return false;
            }
            int count = 0;
            for (ColumnInfo other : allColumns) {
                if (other.fieldName != null
                    && other.groupId != null
                    && fieldName.equals(other.fieldName)
                    && groupId.equals(other.groupId)) {
                    count++;
                }
            }
            return count > 1;
        }

        /**
         * What is the position (1-based) of this column among same-name fields in the same group?
         */
        int positionInSameGroup(ColumnInfo[] allColumns) {
            if (fieldName == null || groupId == null) {
                return 0;
            }
            int position = 0;
            for (ColumnInfo other : allColumns) {
                if (other.fieldName != null
                    && other.groupId != null
                    && fieldName.equals(other.fieldName)
                    && groupId.equals(other.groupId)) {
                    position++;
                    if (other.index == this.index) {
                        break;
                    }
                }
            }
            return position;
        }

        /**
         * Returns a string representation of this column with computed information.
         */
        String toString(String firstGroupId, ColumnInfo[] allColumns) {
            // Inline isInFirstGroup logic for debug
            boolean inFirstGroup = groupId != null && firstGroupId != null && groupId.equals(firstGroupId);

            // Inline sameGroupDuplicateCount logic for debug
            int sameGroupCount = 0;
            if (fieldName != null && groupId != null) {
                for (ColumnInfo other : allColumns) {
                    if (other.fieldName != null
                        && other.groupId != null
                        && fieldName.equals(other.fieldName)
                        && groupId.equals(other.groupId)) {
                        sameGroupCount++;
                    }
                }
            }

            return String.format("Column %d: field='%s', group='%s', inFirstGroup=%s, hasIdenticalInSameGroup=%s, sameGroupCount=%d, position=%d",
                index,
                fieldName,
                groupId,
                inFirstGroup,
                hasIdenticalInSameGroup(allColumns),
                sameGroupCount,
                positionInSameGroup(allColumns));
        }
    }

    /**
     * Extract group IDs into an array indexed by column number (as string to preserve leading zeros).
     * @param h HeaderData instance
     * @param loggerType Logger type ("VCDS" or "VCDS_LEGACY") to determine format
     */
    private static VCDSGroupData extractGroupIdMap(DataLogger.HeaderData h, String loggerType) {
        VCDSGroupData groupData = new VCDSGroupData();

        if (h.g == null || h.g.length == 0) {
            return groupData;
        }

        groupData.columnToGroupId = new String[h.g.length];

        // Unified regex: matches "Group A:" or "Group 23"
        java.util.regex.Pattern groupPattern = java.util.regex.Pattern.compile("Group ([A-Z0-9]+)");
        java.util.regex.Pattern quotedNumPattern = java.util.regex.Pattern.compile("'?([0-9]+)");

        // Phase 1: Extract group markers
        java.util.Map<Integer, String> markerPositions = new java.util.HashMap<>();

        for (int i = 0; i < h.g.length; i++) {
            if (h.g[i] == null) {
                continue;
            }
            java.util.regex.Matcher m = groupPattern.matcher(h.g[i]);
            if (m.find()) {
                String capture = m.group(1);
                if (capture == null) {
                    continue;
                }

                if ("VCDS_LEGACY".equals(loggerType)) {
                    // Format 1: "Group A:" - get group ID from next column
                    if (i + 1 < h.g.length && h.g[i + 1] != null) {
                        String nextCol = h.g[i + 1].trim();
                        String groupId = null;

                        // Try quoted number pattern (e.g., '002)
                        java.util.regex.Matcher numMatcher = quotedNumPattern.matcher(nextCol);
                        if (numMatcher.find()) {
                            groupId = numMatcher.group(1);
                        } else if (!nextCol.isEmpty()) {
                            // Use as-is for text like "Not Running"
                            groupId = nextCol;
                        }

                        if (groupId != null) {
                            markerPositions.put(i, groupId);
                        }
                    }
                } else {
                    // Format 2: "Group 23" - use capture directly as group ID
                    // For VCDS format, use look-ahead: marker at position i means column i-1 gets the group
                    // (column BEFORE the marker gets the group ID)
                    if (i > 0) {
                        markerPositions.put(i - 1, capture);
                    }
                    // If marker is at position 0, skip it (no column before it)
                }
            }
        }

        // Phase 2: Assign groups to all columns
        // For VCDS format: Use look-ahead - when marker is at i, assign group to i-1 (column before marker)
        // This is handled in Phase 1 by adjusting markerPositions for VCDS
        String currentGroup = null;
        String firstGroup = null;
        String lastGroup = null;

        for (int i = 0; i < h.g.length; i++) {
            // Check if this column is a group marker
            if (markerPositions.containsKey(i)) {
                currentGroup = markerPositions.get(i);
                if (firstGroup == null) {
                    firstGroup = currentGroup;
                }
                lastGroup = currentGroup;
            }
            // Assign current group to this column
            groupData.columnToGroupId[i] = currentGroup;
        }

        // Cache first and last group IDs
        groupData.firstGroupId = firstGroup;
        groupData.lastGroupId = lastGroup;

        return groupData;
    }

    /**
     * Get group ID for a column index.
     * @param h HeaderData instance
     * @param index Column index
     * @return Group ID string, or null if no groups defined or column is before first group marker
     */
    public static String columnToGroupId(DataLogger.HeaderData h, int index) {
        VCDSGroupData groupData = groupDataMap.get(h);
        if (groupData == null || groupData.columnToGroupId == null) {
            return null;
        }

        // If index is longer than array, return the last group
        if (index >= groupData.columnToGroupId.length) {
            return groupData.lastGroupId;
        }

        // Return the value at the index (null means column is before first group marker)
        return groupData.columnToGroupId[index];
    }

    /**
     * Process headers for VCDS logger types.
     * Handles group extraction, STAMP->TIME hack, and duplicate detection.
     * @param h HeaderData instance
     * @param loggerType Logger type ("VCDS" or "VCDS_LEGACY") to determine format
     */
    public static void processVCDSHeader(DataLogger.HeaderData h, String loggerType) {
        // Generate column to group ID mapping
        VCDSGroupData groupData = extractGroupIdMap(h, loggerType);
        groupDataMap.put(h, groupData);

        // Print output of converter method
        if (h.id != null) {
            logger.debug("Column to Group ID (from converter method):");
            for (int i = 0; i < h.id.length; i++) {
                String groupId = columnToGroupId(h, i);
                if (groupId != null) {
                    logger.debug("  column {} -> group '{}'", i, groupId);
                }
            }

            // Hack: If a header field name is empty, use TIME if unit is STAMP or MARKE (German)
            if (h.u != null) {
                for (int i = 0; i < h.id.length && i < h.u.length; i++) {
                    if ((h.id[i] == null || h.id[i].length() == 0) && h.u[i] != null) {
                        String unit = h.u[i].trim();
                        if ("STAMP".equals(unit) || "MARKE".equals(unit)) {
                            h.id[i] = "TIME";
                        }
                    }
                }
            }

            // =====================================================================
            // VCDS DUPLICATE FIELD NAME DISAMBIGUATION RULES
            // =====================================================================
            //
            // VCDS files can have duplicate field names across groups (e.g., Group 115, Group 118)
            // and within the same group. The disambiguation rules are:
            //
            // RULE 1: Duplicates WITHIN the same group
            //   - All duplicates get numbered with their position: "Field1", "Field2", etc.
            //   - Example: Group 115 has two "Absolute Pres." fields
            //     -> "Absolute Pres.1", "Absolute Pres.2", etc.
            //
            // RULE 2: Duplicates ACROSS different groups
            //   - The FIRST global occurrence (lowest column index, first group) remains unchanged: "Field", "Field1", "Field2", etc.
            //   - Subsequent occurrences get group ID suffix: "Field [GroupID]", "Field1 [GroupID]", "Field2 [GroupID]", etc.
            //   - Example: "TIME" in Group 115 (first) and Group 118 (later)
            //     -> Column 3: "TIME" (unchanged, first occurrence)
            //     -> Column 7: "TIME [118]" (has group ID, not first)
            //
            // RULE 3: Edge cases
            //   - Fields with null/empty fieldName or groupId are skipped (no disambiguation)
            //   - First group members that are unique within their group stay unchanged
            //   - This algorithm runs BEFORE generic ensureUniqueFieldNames(), so VCDS takes precedence
            //
            // ALGORITHM (3-pass approach):
            //   Pass 1: Build ColumnInfo array with field names and group IDs
            //   Pass 2: Compute duplicate information for each column
            //   Pass 3: Apply disambiguation rules based on computed information
            //
            // =====================================================================

            // Multi-pass algorithm for duplicate detection
            // Pass 1: Build ColumnInfo for all columns
            ColumnInfo[] columns = new ColumnInfo[h.id.length];
            String firstGroupId = groupData.firstGroupId;

            for (int i = 0; i < h.id.length; i++) {
                String fieldName = (h.id[i] != null && h.id[i].length() > 0) ? h.id[i] : null;
                String groupId = columnToGroupId(h, i);
                columns[i] = new ColumnInfo(i, fieldName, groupId);
            }

            // Pass 2: Compute and log column information
            logger.debug("Column Information:");
            for (ColumnInfo col : columns) {
                if (col.fieldName == null || col.groupId == null) {
                    continue;
                }
                logger.debug("  {}", col.toString(firstGroupId, columns));
            }

            // Pass 3: Apply disambiguation based on computed information
            // Pass 3a: Handle same-group duplicates - add numbers only (no group ID)
            for (ColumnInfo col : columns) {
                if (col.fieldName == null || col.groupId == null) {
                    continue;
                }

                if (col.hasIdenticalInSameGroup(columns)) {
                    // RULE 1: Multiple occurrences in same group - add numbers only: "Field1", "Field2", etc.
                    int position = col.positionInSameGroup(columns);
                    h.id[col.index] = col.fieldName + position;
                }
            }

            // Pass 3.5: Apply aliasing rules BEFORE adding group suffixes
            // This allows us to alias base names directly without stripping/re-adding suffixes
            applyPostDisambiguationAliases(h, groupData, loggerType);

            // Pass 3b: Handle cross-group duplicates - add group ID suffix if not first occurrence
            // Note: Now using aliased names from h.id for duplicate detection
            for (ColumnInfo col : columns) {
                // For TIME columns, allow processing even if groupId is null (may happen for edge cases)
                String currentFieldName = h.id[col.index];
                if (currentFieldName == null || currentFieldName.isEmpty()) {
                    continue;
                }

                boolean isTimeColumn = currentFieldName.matches("^TIME\\s*\\d*$");

                // Skip non-TIME columns with null fieldName or groupId
                if (!isTimeColumn && (col.fieldName == null || col.groupId == null)) {
                    continue;
                }

                // Compute isFirstGlobally regardless of appearsInOtherGroups
                // For TIME columns: check against any TIME variant; for others: check exact match
		// This is required because all columns are disambiguated before this step.
                boolean isFirstGlobally = true;
                for (int j = 0; j < col.index; j++) {
                    String prevFieldName = h.id[j];
                    if (prevFieldName != null) {
                        boolean matches = isTimeColumn
                            ? prevFieldName.matches("^TIME\\s*\\d*$")
                            : currentFieldName.equals(prevFieldName);
                        if (matches) {
                            isFirstGlobally = false;
                            break;
                        }
                    }
                }

                // Blacklist TIME columns that aren't first globally (regardless of appearsInOtherGroups)
                if (isTimeColumn && !isFirstGlobally) {
                    h.id[col.index] = "";
                    logger.debug("Blacklisting excess TIME column at index {} (group: {}, field: '{}')", col.index, col.groupId, currentFieldName);
                    continue; // Skip the appearsInOtherGroups check for blacklisted columns
                }

                // Check if this field (now aliased) appears in other groups
                boolean appearsInOtherGroups = false;
                for (ColumnInfo other : columns) {
                    if (other.fieldName == null || other.groupId == null || other.index == col.index) {
                        continue;
                    }
                    String otherFieldName = h.id[other.index];
                    if (otherFieldName != null
                        && currentFieldName.equals(otherFieldName)
                        && !col.groupId.equals(other.groupId)) {
                        appearsInOtherGroups = true;
                        break;
                    }
                }

                if (appearsInOtherGroups && !isFirstGlobally) {
                    // Not first occurrence - add group ID suffix
                    h.id[col.index] = currentFieldName + " [" + col.groupId + "]";
                }
                // Otherwise leave as-is (first occurrence, keeps aliased name from pass 3.5)
            }
        }
    }

    /**
     * Post-disambiguation alias rule.
     * Matches field names and group IDs using regex patterns.
     */
    private static class PostDisambiguationAliasRule {
        final java.util.regex.Pattern fieldPattern;  // Regex pattern for field name
        final String groupIdPattern;                  // Exact group ID match (null = match any)
        final String replacement;                     // Replacement string (can use $1, $2, etc. for capture groups)

        PostDisambiguationAliasRule(String fieldPattern, String groupIdPattern, String replacement) {
            this.fieldPattern = java.util.regex.Pattern.compile(fieldPattern);
            this.groupIdPattern = groupIdPattern;  // null means match any group ID
            this.replacement = replacement;
        }
    }

    // VCDS_LEGACY post-disambiguation aliases
    private static final java.util.Map<String, java.util.List<PostDisambiguationAliasRule>> ALIASES = new java.util.HashMap<>();

    /**
     * Helper function to add a post-disambiguation alias rule.
     *
     * @param loggerType Logger type ("VCDS" or "VCDS_LEGACY")
     * @param fieldPattern Regex pattern for matching field names
     * @param groupIdPattern Exact group ID match (null = match any group ID)
     * @param replacement Replacement string (can use $1, $2, etc. for capture groups)
     */
    private static void addAlias(String loggerType, String fieldPattern, String groupIdPattern, String replacement) {
        PostDisambiguationAliasRule rule = new PostDisambiguationAliasRule(fieldPattern, groupIdPattern, replacement);
	ALIASES.putIfAbsent(loggerType, new java.util.ArrayList<>());
	ALIASES.get(loggerType).add(rule);
    }

    static {
        // Initialize VCDS_LEGACY aliases
	// german oddities - not standard 007, has two unit lines
        addAlias("VCDS_LEGACY", "Lambda", "007", "LambdaControl"); // first unit line is "Regular"
        addAlias("VCDS_LEGACY", "Lambda", "007", "LambdaCorrectionFactor"); // first unit line is "Korrekturfaktor"

	// Standard VCDS Legacy groups
        addAlias("VCDS_LEGACY", "IgnitionRetard1", "020", "IgnitionRetardCyl1");
        addAlias("VCDS_LEGACY", "IgnitionRetard2", "020", "IgnitionRetardCyl2");
        addAlias("VCDS_LEGACY", "IgnitionRetard3", "020", "IgnitionRetardCyl3");
        addAlias("VCDS_LEGACY", "IgnitionRetard4", "020", "IgnitionRetardCyl4");
        addAlias("VCDS_LEGACY", "IgnitionRetard1", "021", "IgnitionRetardCyl5");
        addAlias("VCDS_LEGACY", "IgnitionRetard2", "021", "IgnitionRetardCyl6");
        addAlias("VCDS_LEGACY", "IgnitionRetard3", "021", "IgnitionRetardCyl7");
        addAlias("VCDS_LEGACY", "IgnitionRetard4", "021", "IgnitionRetardCyl8");

        addAlias("VCDS_LEGACY", "Voltage1", "026", "KnockVoltageSensor1");
        addAlias("VCDS_LEGACY", "Voltage2", "026", "KnockVoltageSensor2");
        addAlias("VCDS_LEGACY", "Voltage3", "026", "KnockVoltageSensor3");
        addAlias("VCDS_LEGACY", "Voltage4", "026", "KnockVoltageSensor4");
        addAlias("VCDS_LEGACY", "Voltage1", "027", "KnockVoltageSensor5");
        addAlias("VCDS_LEGACY", "Voltage2", "027", "KnockVoltageSensor6");
        addAlias("VCDS_LEGACY", "Voltage3", "027", "KnockVoltageSensor7");
        addAlias("VCDS_LEGACY", "Voltage4", "027", "KnockVoltageSensor8");

        addAlias("VCDS_LEGACY", "Voltage1", "031", "O2SVoltageSensor1Bank1");
        addAlias("VCDS_LEGACY", "Voltage2", "031", "O2SVoltageSensor2Bank1");
        addAlias("VCDS_LEGACY", "Voltage3", "031", "O2SVoltageSensor1Bank2");
        addAlias("VCDS_LEGACY", "Voltage4", "031", "O2SVoltageSensor2Bank2");

        addAlias("VCDS_LEGACY", "TIME2", "114", "WastegateOnTime"); // oddity
        addAlias("VCDS_LEGACY", "TIME3", "114", "EGROnTime"); // oddity
        addAlias("VCDS_LEGACY", "TIME4", "114", "DiverterOnTime"); // oddity
        addAlias("VCDS_LEGACY", "DutyCycle", "114", "WastegateDutyCycle"); // oddity

        addAlias("VCDS_LEGACY", "BoostPressure1", "115", "BoostPressureDesired");
        addAlias("VCDS_LEGACY", "BoostPressure2", "115", "BoostPressureActual");

        addAlias("VCDS_LEGACY", "Temperature", "118", "IntakeAirTemperature"); // oddity
        addAlias("VCDS_LEGACY", "EngineLoad", "118", "WastegateDutyCycle"); // oddity
        addAlias("VCDS_LEGACY", "BoostPressure", "118", "BoostPressureBeforeThrottle"); // oddity

        // VCDS blacklists: Don't use Accelerator position for filtering
        addAlias("VCDS", "Accelerator position", "24", "Accelerator position [24]"); // blacklist
        addAlias("VCDS", "Accelerator position", "508", "Accelerator position [508]"); // blacklist
    }

    /**
     * Apply post-disambiguation aliasing rules using a list of alias rules.
     *
     * @param h HeaderData instance with disambiguated field names
     * @param groupData VCDS group data containing column-to-group mapping
     * @param loggerType Logger type string for debugging
     */
    private static void applyPostDisambiguationAliases(DataLogger.HeaderData h, VCDSGroupData groupData, String loggerType) {

        if (h.id == null || groupData == null || groupData.columnToGroupId == null) {
            return;
        }

	java.util.List<PostDisambiguationAliasRule> aliases = ALIASES.get(loggerType);
	if (aliases == null || aliases.isEmpty()) {
		return;
	}

        for (int i = 0; i < h.id.length; i++) {
            if (h.id[i] == null || h.id[i].isEmpty()) {
                continue;
            }

            String fieldName = h.id[i];
            String newName = fieldName;

            // Get the group ID for this column explicitly (may be null if column is before first group)
            String groupId = null;
            if (i < groupData.columnToGroupId.length) {
                groupId = groupData.columnToGroupId[i];
            } else if (groupData.lastGroupId != null) {
                groupId = groupData.lastGroupId;
            }

            // Note: At this point, field names have numbering from same-group duplicates but no group suffixes
            // (e.g., "BoostPressure2", not "BoostPressure2 [115]")

            // Try each alias rule
            for (PostDisambiguationAliasRule rule : aliases) {
                // Match against field name (may have numbering but no group suffix)
                java.util.regex.Matcher matcher = rule.fieldPattern.matcher(fieldName);
                if (!matcher.matches()) {
                    continue;
                }

                // Match group ID (null pattern means match any group ID)
                if (rule.groupIdPattern != null) {
                    if (groupId == null || !rule.groupIdPattern.equals(groupId)) {
                        continue;
                    }
                }

                // Apply replacement (support $1, $2, etc. for capture groups)
                newName = matcher.replaceFirst(rule.replacement);

                logger.debug("{} mid-disambiguation alias: column {} (group {}): '{}' -> '{}'", loggerType, i, groupId, fieldName, newName);
                break;  // First matching rule wins
            }

            if (!newName.equals(fieldName)) {
                h.id[i] = newName;
            }
        }
    }

}
