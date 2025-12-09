package org.nyet.logfile;

import java.io.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.*;

import org.nyet.util.DoubleArray;

public class Dataset {
    private static final Logger logger = LoggerFactory.getLogger(Dataset.class);

    public enum ColumnType {
        CSV_NATIVE,                    // Native from CSV file
        COMPILE_TIME_CONSTANTS,        // Calculated using only compile-time constants (UnitConstants)
        VEHICLE_CONSTANTS,             // Calculated using vehicle constants (rpm_per_mph, mass, Cd, etc.)
        PROCESSED_VARIANT,             // Processed versions of native columns (RPM smoothed, TIME converted, Sample index)
        OTHER_RUNTIME                  // Calculated using other runtime parameters (fueling params, filter params)
    }

    public static class DatasetId implements Comparable<Object> {
        public String id_orig;  // Original field name (raw from CSV, unprocessed)
        public String id;
        public String unit;     // Normalized unit (preference-based, for menu)
        public String u2;       // Original unit intent (processed/normalized, but before preference conversion)
                                // NOTE: Unlike id_orig (truly original), u2 is the processed unit string
                                // after Units.normalize() and Units.find(), but before preference conversion
        public Object type;

        @Override
        public int compareTo(Object o) {
            final DatasetId id = (DatasetId) o;
            return this.id.compareTo(id.id);
        }

        @Override
        public String toString() { return this.id; }
        public DatasetId(String s) { this.id=s; }
        public DatasetId(String s, String id_orig, String unit) {
            this.id=s; this.id_orig=id_orig; this.unit=unit;
        }
        public DatasetId(String s, String id_orig, String unit, String u2) {
            this.id=s; this.id_orig=id_orig; this.unit=unit; this.u2=u2;
        }
        public boolean equals(Comparable<?> o) {
            return this.id.equals(o.toString());
        }
    }

    private DatasetId[] ids;
    private final String filePath; // Full file path for detection and other purposes
    private final String fileId; // This was never meant to be a filename. It is just a key used to identify the dataset. If you want path, use filePath instead.
    private final LinkedHashMap<String, Column> columns; // Use LinkedHashMap to prevent duplicates and maintain insertion order
    private ArrayList<Range> range_cache = new ArrayList<Range>();
    private int rows;
    protected ArrayList<String> lastFilterReasons = new ArrayList<String>();
    private ArrayList<String> comments = new ArrayList<String>();
    protected ProgressCallback progressCallback; // Progress callback for reporting loading progress

    public class Range {
        public int start;
        public int end;
        public Range(int s, int e) { this.start=s; this.end=e; }
        public Range(int s) { this(s,s); }
        public Range() { this(0,0); }
        public int size() { return this.end-this.start+1; }
        @Override
        public String toString() {
            return String.format("[%d:%d]", this.start, this.end);
        }
    }

    public class Column {
        private final DatasetId id;
        public DoubleArray data;
        private ColumnType columnType;

        // CSV parsing constructors - default to CSV_NATIVE
        public Column(Comparable<?> id, String units) {
            this(id, null, units, new DoubleArray(), ColumnType.CSV_NATIVE);
        }
        public Column(Comparable<?> id, String id_orig, String units) {
            this(id, id_orig, units, new DoubleArray(), ColumnType.CSV_NATIVE);
        }

        // Calculated column constructors - default to COMPILE_TIME_CONSTANTS (most common)
        public Column(Comparable<?> id, String units, DoubleArray data) {
            this(id, null, units, data, ColumnType.COMPILE_TIME_CONSTANTS);
        }
        public Column(Comparable<?> id, String units, DoubleArray data, ColumnType columnType) {
            this(id, null, units, data, columnType);
        }
        public Column(Comparable<?> id, String id_orig, String units,
            DoubleArray data) {
            this(id, id_orig, units, data, ColumnType.COMPILE_TIME_CONSTANTS);
        }
        public Column(Comparable<?> id, String id_orig, String units,
            DoubleArray data, ColumnType columnType) {
            this.id = new DatasetId(id.toString(), id_orig, units, null);
            this.data = data;
            this.columnType = columnType;
        }

        public Column(DatasetId id, DoubleArray data) {
            this(id, data, ColumnType.COMPILE_TIME_CONSTANTS);
        }
        public Column(DatasetId id, DoubleArray data, ColumnType columnType) {
            this.id = id;  // Share reference - no copy!
            this.data = data;
            this.columnType = columnType;
        }


        public ColumnType getColumnType() {
            return this.columnType;
        }

        public void add(String s) {
            // nuke non-printable chars
            final Pattern p = Pattern.compile("[^\\p{Print}]");
            s=p.matcher(s).replaceAll("");

            // look for time stamps, convert to seconds since midnight with monotonicity
            // Issue #58 - Fixed: Zeitronix timestamps are now properly parsed as seconds since midnight
            final Pattern p1 = Pattern.compile("\\d{2}:\\d{2}:\\d{2}.\\d{1,3}");
            final Pattern p2 = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");
            final Pattern p3 = Pattern.compile("\\d{2}:\\d{2}.\\d{1,3}");

            SimpleDateFormat fmt=null;
            if (p1.matcher(s).matches()) {
                fmt = new SimpleDateFormat("HH:mm:ss.SSS");
            } else if (p2.matcher(s).matches()) {
                fmt = new SimpleDateFormat("HH:mm:ss");
            } else if (p3.matcher(s).matches()) {
                fmt = new SimpleDateFormat("mm:ss.SSS");
            }
            if (fmt != null) {
                try {
                    // Parse timestamp directly as H:M:S without Date
                    String[] parts = s.split(":");
                    double secondsSinceMidnight = 0;

                    if (parts.length >= 3) {
                        // HH:mm:ss.SSS format
                        secondsSinceMidnight = Integer.parseInt(parts[0]) * 3600.0 +
                            Integer.parseInt(parts[1]) * 60.0 +
                            Double.parseDouble(parts[2]);
                    } else if (parts.length == 2) {
                        // mm:ss.SSS format
                        secondsSinceMidnight = Integer.parseInt(parts[0]) * 60.0 +
                            Double.parseDouble(parts[1]);
                    }

                    // Keep adding 24 hours until timestamp is greater than last time
                    double lastTimestamp = -1;
                    if (this.data.size() > 0) {
                        lastTimestamp = this.data.get(this.data.size() - 1);
                    }
                    while (lastTimestamp >= 0 && secondsSinceMidnight <= lastTimestamp) {
                        secondsSinceMidnight += 24 * 3600; // Add 24 hours
                    }

                    this.data.append(secondsSinceMidnight);
                } catch (final Exception e) {
                }
            } else {
                try {
                    this.data.append(Double.valueOf(s));
                } catch (final Exception e) {
                }
            }
        }

        public String getId() {
            if(this.id==null) return null;
            return this.id.id;
        }
        public String getIdOrig() {
            if(this.id==null) return null;
            return this.id.id_orig;
        }
        public String getUnits() {
            if(this.id==null) return null;
            // For CSV_NATIVE columns, return normalized unit (for display consistency)
            // For calculated columns, return unit (normalized or calculated)
            // NOTE: For unit conversion logic, use getNativeUnits() to get native units
            return this.id.unit;  // Normalized or calculated unit
        }
        public String getNativeUnits() {
            if(this.id==null) return null;
            // Return native unit (u2) for CSV_NATIVE columns, normalized unit for others
            // This is used by unit conversion logic which needs the original unit intent
            if (this.columnType == ColumnType.CSV_NATIVE && this.id.u2 != null) {
                return this.id.u2;  // Original unit intent (shares String reference)
            }
            return this.id.unit;  // Normalized or calculated unit
        }
        public String getLabel(boolean id_orig) {
            return (id_orig && this.id.id_orig!=null)?this.id.id_orig:this.id.id;
        }
    }

    public class Key implements Comparable<Object> {
        private String fn;
        private final String s;
        private Integer range;
        private final BitSet flags;
        private final Dataset data_cache; // dataset cache
        private DatasetId id_cache; // id cache
/*
        public Key (String fn, String s, int range, BitSet flags) {
            this.fn=fn;
            this.s=s;
            this.range=range;
            this.flags=flags;
        }
*/
        public Key (Key k, int range, Dataset data) {
            this.fn=k.fn;
            if (this.fn==null) this.fn = data.getFileId();
            this.s= new String(k.s);
            this.range=range;
            this.data_cache = data;
            this.flags=k.flags;
            data.get(this); //initialize id cache
        }

        public Key (Key k, Dataset data) {
            this (k, 0, data);
        }

        public Key (String s, Dataset data) {
            this.fn = data.getFileId();
            this.s=s;
            this.data_cache = data;
            this.range=0;
            this.flags=new BitSet(2);
            data.get(this); // initialize cache
        }

        /*
         * public Key (String fn, String s, int range) { this(fn, s, range, new
         * BitSet(2)); }
         *
         * public Key (String fn, String s) { this(fn, s, 0, new BitSet(2)); }
         */
        private boolean useIdOrig() {
            return (this.data_cache != null && this.data_cache.useIdOrig()
                    && this.id_cache != null && this.id_cache.id_orig != null);
        }

        @Override
        public String toString() {
            String ret = this.useIdOrig()?this.id_cache.id_orig:this.s;

            // don't skip file name, add to beginning
            if(!this.flags.get(0))
                ret = org.nyet.util.Files.filenameStem(this.fn) + ":" + ret;

            // don't skip range #, add to end
            if(!this.flags.get(1))
                ret += " " + (this.range+1);

            return ret;
        }

        public void hideFilename() { this.flags.set(0); }
        public void showFilename() { this.flags.clear(0); }
        public void hideRange() { this.flags.set(1); }
        public void showRange() { this.flags.clear(1); }

        public String getFilename() { return this.fn; }
        public String getIdOrig()  { return (this.id_cache!=null && this.id_cache.id_orig!=null)?this.id_cache.id_orig:this.s; }
        public String getString() { return this.s; }
        public Integer getRange() { return this.range; }
        public void setRange(int r) { this.range=r; }

        /**
         * Get display label with elided filename for legend display.
         * Elides only the filename portion (before the colon) while preserving
         * the variable name and range number for readability.
         * @param maxLength Maximum length for the filename portion (15 chars recommended)
         * @return Elided version of the label suitable for display
         */
        public String getDisplayLabel(int maxLength) {
            String ret = this.useIdOrig()?this.id_cache.id_orig:this.s;

            // Elide filename if present
            if(!this.flags.get(0)) {
                String filename = org.nyet.util.Files.filenameStem(this.fn);
                String elidedFilename = org.nyet.util.Strings.elide(filename, maxLength);
                ret = elidedFilename + ":" + ret;
            }

            // Add range number if present
            if(!this.flags.get(1))
                ret += " " + (this.range+1);

            return ret;
        }

        @Override
        public int compareTo(Object o) {
            if(o instanceof Key) {
                final Key k = (Key)o;
                int out = this.fn.compareTo(k.fn);
                if(out!=0) return out;
                out = this.s.compareTo(k.s);
                if(out!=0) return out;
                return this.range.compareTo(k.range);
            }
            if(o instanceof String) {
                return this.s.compareTo((String)o);
            }
            throw new ClassCastException("Not a Key or a String!");
        }
        @Override
        public boolean equals(Object o) {
            if(o==null) return false;
            if(o instanceof Key) {
                final Key k = (Key)o;
                if(!this.fn.equals(k.fn)) return false;
                if(!this.s.equals(k.s)) return false;
                return this.range.equals(k.range);
            }
            // if passed a string, only check the "s" portion
            if(o instanceof String) {
                return this.s.equals(o);
            }
            throw new ClassCastException(o + ": Not a Key or a String!");
        }
    }

    public static CSVReader DatasetReader(String filename) throws FileNotFoundException, UnsupportedEncodingException {
        return DatasetReader(filename, ',');
    }

    public static CSVReader DatasetReader(String filename, char separator) throws FileNotFoundException, UnsupportedEncodingException {
        return DatasetReader(filename, "ISO-8859-1", separator);
    }

    public static CSVReader DatasetReader(String filename, String encoding) throws FileNotFoundException, UnsupportedEncodingException {
        return DatasetReader(filename, encoding, ',');
    }

    public static CSVReader DatasetReader(String filename, String encoding, char separator) throws FileNotFoundException, UnsupportedEncodingException {
        Reader reader = new InputStreamReader(new FileInputStream(filename), encoding);
        return new CSVReaderBuilder(reader)
            .withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
            .build();
    }

    // Check if this is a comment line (starts with #, *)
    public static boolean IsLineComment(String line) {
        line = line.trim();
        if (line.length() == 0) return false; // technically and empty line is not a comment

        // Remove BOM (Byte Order Mark) characters if present
        // UTF-8 BOM: \uFEFF, UTF-16 BOMs can be \uFEFF, \uFFFE, or byte sequences
        if (line.length() > 0 && line.charAt(0) == '\uFEFF') {
            line = line.substring(1).trim();
        }

        if (line.length() == 0) return false;
        return line.startsWith("#") ||
            line.startsWith("*") ||
            line.startsWith("//");
    }

    // Functional interface for CSV operations
    @FunctionalInterface
    public interface CSVOperation {
        void execute(CSVReader reader) throws Exception;
    }

    // Reusable function for CSV operations with separator fallback
    public static CSVReader createCSVReaderWithFallback(String content, CSVOperation operation) throws Exception {
        // Try comma separator first
        StringReader stringReader = new StringReader(content);
        CSVReader csvReader = new CSVReaderBuilder(stringReader)
            .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
            .build();
        try {
            operation.execute(csvReader);
            return csvReader;
        } catch ( final Exception e ) {
            // Try semicolon separator
            StringReader stringReader2 = new StringReader(content);
            csvReader = new CSVReaderBuilder(stringReader2)
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build();
            operation.execute(csvReader);
            return csvReader;
        }
    }

    // Common CSV parsing method with separator fallback (comma -> semicolon)
    public static String[] parseCSVLineWithFallback(String line) throws Exception {
        // Try comma separator first
        StringReader stringReader = new StringReader(line + "\n");
        CSVReader csvReader = new CSVReaderBuilder(stringReader)
            .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
            .build();
        String[] csvLine = csvReader.readNext();

        // If comma parsing failed or produced empty results, try semicolon separator
        if (csvLine == null || csvLine.length == 0) {
            stringReader = new StringReader(line + "\n");
            csvReader = new CSVReaderBuilder(stringReader)
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build();
            csvLine = csvReader.readNext();
        }

        return csvLine;
    }

    public Dataset(String filename, int verbose) throws Exception {
        this(filename, verbose, null);
    }

    public Dataset(String filename, int verbose, ProgressCallback progressCallback) throws Exception {
        this.filePath = filename; // Store full path for detection purposes
        this.fileId = org.nyet.util.Files.filename(filename); // This was never meant to be a filename. It is just a key used to identify the dataset.
        this.rows = 0;
        this.columns = new LinkedHashMap<String, Column>();
        this.progressCallback = progressCallback;

        // Get file size for progress estimation
        File file = new File(filename);
        long fileSize = file.exists() ? file.length() : -1;
        String fileName = file.getName();

        // Read file and separate comments from CSV data
        StringBuilder csvContent = new StringBuilder();
        long bytesRead = 0;
        long linesRead = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;

            while ((line = reader.readLine()) != null) {
                linesRead++;
                bytesRead += line.length() + 1; // +1 for newline
                line = line.trim();
                if (line.length() == 0) continue; // skip empty lines
                if (IsLineComment(line)) {
                    this.comments.add(line);
                } else {
                    csvContent.append(line).append("\n");
                }

                // Report progress every 1000 lines or every 1MB
                if (progressCallback != null && (linesRead % 1000 == 0 || bytesRead % (1024 * 1024) == 0)) {
                    progressCallback.reportProgress(fileName, "Reading file", bytesRead, fileSize);
                }
            }
        }

        logger.debug("File reading complete: bytesRead={}, fileSize={}, fileName={}", bytesRead, fileSize, fileName);

        if (progressCallback != null) {
            // Report completion with actual bytes read (may differ slightly from fileSize due to line ending differences)
            long finalBytes = bytesRead > 0 ? bytesRead : fileSize;
            long finalTotal = fileSize > 0 ? fileSize : bytesRead;
            progressCallback.reportProgress(fileName, "Reading file", finalBytes, finalTotal);
        }

        // Do detection using collected comment lines BEFORE ParseHeaders
        this.detectLoggerType();

        if (progressCallback != null) {
            progressCallback.reportProgress(fileName, "Detecting logger type", 0, -1);
        }

        // Create CSVReader from the filtered content
        CSVReader csvReader = createCSVReaderWithFallback(csvContent.toString(), (reader) -> {
            ParseHeaders(reader, verbose);
        });

        if (progressCallback != null) {
            progressCallback.reportProgress(fileName, "Parsing headers", 0, -1);
        }

        for (final DatasetId id : this.ids) {
            // Put column in map (will replace if duplicate ID exists, but shouldn't happen during CSV parsing)
            // Share DatasetId reference - Column.id will reference the same DatasetId from ids[]
            Column col = new Column(id, new DoubleArray(), ColumnType.CSV_NATIVE);
            this.columns.put(id.id, col);
        }

        // Estimate total rows for progress reporting (will be corrected at completion)
        // Use a rough estimate based on CSV content length
        String csvString = csvContent.toString();
        long estimatedRows = csvString.length() > 0 ? csvString.split("\n").length : -1;
        long rowsProcessed = 0;

        String [] nextLine;
        while((nextLine = csvReader.readNext()) != null) {
            if (nextLine.length>0) {
                // Allow subclasses to skip non-data lines (e.g., header sections mid-file)
                if (shouldSkipDataLine(nextLine)) {
                    continue;
                }
                boolean gotone=false;
                for(int i=0;i<nextLine.length;i++) {
                    if (nextLine[i].trim().length()>0
                        && this.ids != null && i < this.ids.length) {
                        // Use ids[] array to get column ID, then look up in map
                        String columnId = this.ids[i].id;
                        Column col = this.columns.get(columnId);
                        if (col != null) {
                            // Automatically trim all CSV data values at the source
                            col.add(nextLine[i].trim());
                            gotone=true;
                        }
                    }
                }
                if (gotone) {
                    this.rows++;
                    rowsProcessed++;

                    // Report progress every 1000 rows
                    // Use estimatedRows if available, otherwise use rowsProcessed (indeterminate-like)
                    if (progressCallback != null && rowsProcessed % 1000 == 0) {
                        long totalForProgress = estimatedRows > 0 ? estimatedRows : rowsProcessed;
                        progressCallback.reportProgress(fileName, "Parsing CSV", rowsProcessed, totalForProgress);
                    }
                }
            }
        }

        if (progressCallback != null) {
            // Always report 100% completion for CSV parsing stage before moving to next stage
            progressCallback.reportProgress(fileName, "Parsing CSV", this.rows, this.rows);
            // Don't report "Building ranges" here - buildRanges() will report "Filtering data" with accurate progress
        }

        buildRanges();

        // Note: "Complete" is reported by ECUxDataset.buildRanges() after all work (including spline creation) is done
    }

    /**
     * Hook for subclasses to skip non-data lines during CSV parsing.
     * Called for each CSV line after header parsing is complete.
     *
     * Default implementation skips lines that are not mostly numeric (non-data lines).
     * Subclasses can override for more specific logic (e.g., VCDS header detection).
     *
     * @param line The CSV line as an array of strings
     * @return true if this line should be skipped, false if it should be processed as data
     */
    protected boolean shouldSkipDataLine(String[] line) {
        return !isDataLine(line); // Default: skip non-data lines
    }

    /**
     * Check if a CSV line appears to be a data line (mostly numeric fields).
     * Used to distinguish data lines from header/metadata lines.
     *
     * @param line The CSV line as an array of strings
     * @return true if the line appears to be data (majority of fields are numeric), false otherwise
     */
    protected boolean isDataLine(String[] line) {
        if (line == null || line.length == 0) {
            return false;
        }

        int numericCount = 0;
        int nonEmptyCount = 0;

        for (String field : line) {
            if (field != null && field.trim().length() > 0) {
                nonEmptyCount++;
                try {
                    Double.parseDouble(field.trim());
                    numericCount++;
                } catch (NumberFormatException e) {
                    // Not numeric
                }
            }
        }

        // If no non-empty fields, not a data line
        if (nonEmptyCount == 0) {
            return false;
        }

        // Consider it a data line if majority of non-empty fields are numeric
        return numericCount * 2 >= nonEmptyCount;
    }

    public ArrayList<Column> getColumns() {
        // Return ordered list maintaining insertion order (from LinkedHashMap)
        return new ArrayList<Column>(this.columns.values());
    }

    /**
     * Add or replace a column in the dataset.
     * If a column with the same ID already exists, it will be replaced.
     * @param column The column to add or replace
     */
    protected void putColumn(Column column) {
        this.columns.put(column.getId(), column);
    }

    /**
     * Remove a column from the dataset by ID.
     * @param columnId The ID of the column to remove
     * @return The removed column, or null if not found
     */
    protected Column removeColumn(String columnId) {
        return this.columns.remove(columnId);
    }

    public ArrayList<String> getComments() {return this.comments;}

    // Default implementation - subclasses can override
    protected void detectLoggerType() throws Exception {
        // Default: do nothing - subclasses can override for specific detection
        // Subclasses can use this.comments for detection
    }

    public void ParseHeaders(CSVReader reader, int verbose) throws Exception {
        final String [] line = reader.readNext();
        if (line.length>0 && line[0].trim().length()>0) {
            this.ids = new DatasetId[line.length];
            for(int i=0;i<line.length;i++) {
                // Automatically trim all CSV fields at the source
                this.ids[i].id = line[i].trim();
            }
        }
    }

    public Column get(int id) {
        // Use ids[] array to map index to column ID, then look up in map
        if (this.ids != null && id >= 0 && id < this.ids.length) {
            String columnId = this.ids[id].id;
            return this.columns.get(columnId);
        }
        return null;
    }

    public Column get(Dataset.Key key) {
        final Column c = this.get((Comparable<?>)key);

        if(c!=null) key.id_cache = c.id; // cache column id

        return c;
    }

    public Column get(Comparable<?> id) {
        // Direct map lookup - no duplicates possible with LinkedHashMap
        String idStr = id.toString();
        return this.columns.get(idStr);
    }

    public String units(Comparable<?> id) {
        final Column c = this.get(id);
        if(c==null) return null;
        return c.getUnits();
    }

    public String idOrig(Comparable<?> id) {
        final Column c = this.get(id);
        if(c==null) return null;
        return c.getIdOrig();
    }

    public String getLabel(Comparable<?> id, boolean altnames) {
        final Column c = this.get(id);
        if(c==null) return null;
        return c.getLabel(altnames);
    }

    public boolean exists(Comparable<?> id) {
        if (this.get(id) == null) return false;
        if (this.get(id).data == null) return false;
        if (this.get(id).data.size() == 0) return false;
        return true;
    }

    protected boolean dataValid(int i) { return true; }
    protected boolean rangeValid(Range r) { return true; }

    public ArrayList<Range> getRanges() {
        return this.range_cache;
    }

    protected void buildRanges() {
        this.range_cache = new ArrayList<Range>();
        Range r = null;
        String fileName = org.nyet.util.Files.filename(this.filePath);

        // Report progress every 1000 rows for better visibility
        long lastReported = 0;

        for(int i=0;i<this.rows; i++) {
            boolean end = false;
            if(dataValid(i)) {
                if(r==null) {
                    r = new Range(i);
                    this.lastFilterReasons=new ArrayList<String>();
                }
                if(i==this.rows-1) end=true; // we hit end of data
            } else {
                end = true;
            }
            if(r!=null && end) {
                r.end=i-1;
                if(rangeValid(r)) {
                    /*
                    if(this.lastFilterReasons.size()!=0) {
                        System.out.println(Strings.join(":", this.lastFilterReasons) +
                                ": adding range " + r.toString());
                    }
                    */
                    this.range_cache.add(r);
                }
                r=null;
            }

            // Report progress periodically (every 1000 rows for better visibility)
            if (this.progressCallback != null && (i % 1000 == 0 || i == this.rows - 1)) {
                this.progressCallback.reportProgress(fileName, "Filtering data", i + 1, this.rows);
                lastReported = i + 1;
            }
        }

        // Final progress report if we didn't report the last row
        if (this.progressCallback != null && lastReported < this.rows) {
            this.progressCallback.reportProgress(fileName, "Filtering data", this.rows, this.rows);
        }
    }

    public double[] getData(Key id, Range r) {
        // only match the string portion of the key
        final Column c = this.get(id.getString());
        if (c==null) return null;
        // If range is null, use full dataset
        if (r == null) {
            if (this.length() == 0) {
                return null;
            }
            r = new Range(0, this.length() - 1);
        }
        return c.data.toArray(r.start, r.end);
    }

    public double[] getData(Comparable<?> id, Range r) {
        final Column c = this.get(id);
        if (c==null) return null;
        // If range is null, use full dataset
        if (r == null) {
            if (this.length() == 0) {
                return null;
            }
            r = new Range(0, this.length() - 1);
        }
        return c.data.toArray(r.start, r.end);
    }

    public String getFilePath() { return this.filePath; }
    public String getFileId() { return this.fileId; }

    public DatasetId [] getIds() { return this.ids; }
    /**
     * Set DatasetId objects from processed header data with normalized and native units support.
     *
     * @param h Processed header data
     * @param config DataLoggerConfig for creating DatasetId objects
     * @param normalizedUnits Map of canonical column names to normalized unit strings,
     *                       or null to use native units from h.u[]
     * @param nativeUnits Map of canonical column names to original unit intent strings
     *                    (processed/normalized, but before preference conversion),
     *                    or null to use units from h.u[]
     */
    public void setIds(org.nyet.ecuxplot.DataLogger.HeaderData h,
                       org.nyet.ecuxplot.DataLogger.DataLoggerConfig config,
                       java.util.Map<String, String> normalizedUnits,
                       java.util.Map<String, String> nativeUnits) {
        this.ids = config.createDatasetIds(h, normalizedUnits, nativeUnits);
    }

    public ArrayList<String> getLastFilterReasons() { return this.lastFilterReasons; }
    public int length() { return this.rows; }
    public boolean useIdOrig() { return false; }
}

// vim: set sw=4 ts=8 expandtab:
