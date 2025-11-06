package org.nyet.logfile;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;

import com.opencsv.*;

import org.nyet.util.DoubleArray;

public class Dataset {
    public enum ColumnType {
        CSV_NATIVE,                    // Native from CSV file
        COMPILE_TIME_CONSTANTS,        // Calculated using only compile-time constants (UnitConstants)
        VEHICLE_CONSTANTS,             // Calculated using vehicle constants (rpm_per_mph, mass, Cd, etc.)
        PROCESSED_VARIANT,             // Processed versions of native columns (RPM smoothed, TIME converted, Sample index)
        OTHER_RUNTIME                  // Calculated using other runtime parameters (fueling params, filter params)
    }

    public static class DatasetId implements Comparable<Object> {
        public String id;
        public String id2;
        public String unit;
        public Object type;

        @Override
        public int compareTo(Object o) {
            final DatasetId id = (DatasetId) o;
            return this.id.compareTo(id.id);
        }

        @Override
        public String toString() { return this.id; }
        public DatasetId(String s) { this.id=s; }
        public DatasetId(String s, String id2, String unit) {
            this.id=s; this.id2=id2; this.unit=unit;
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
        public Column(Comparable<?> id, String id2, String units) {
            this(id, id2, units, new DoubleArray(), ColumnType.CSV_NATIVE);
        }

        // Calculated column constructors - default to COMPILE_TIME_CONSTANTS (most common)
        public Column(Comparable<?> id, String units, DoubleArray data) {
            this(id, null, units, data, ColumnType.COMPILE_TIME_CONSTANTS);
        }
        public Column(Comparable<?> id, String units, DoubleArray data, ColumnType columnType) {
            this(id, null, units, data, columnType);
        }
        public Column(Comparable<?> id, String id2, String units,
            DoubleArray data) {
            this(id, id2, units, data, ColumnType.COMPILE_TIME_CONSTANTS);
        }
        public Column(Comparable<?> id, String id2, String units,
            DoubleArray data, ColumnType columnType) {
            this.id = new DatasetId(id.toString(), id2, units);
            this.data = data;
            this.columnType = columnType;
        }

        public Column(DatasetId id, DoubleArray data) {
            this(id, data, ColumnType.COMPILE_TIME_CONSTANTS);
        }
        public Column(DatasetId id, DoubleArray data, ColumnType columnType) {
            this.id = id;
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
        public String getId2() {
            if(this.id==null) return null;
            return this.id.id2;
        }
        public String getUnits() {
            if(this.id==null) return null;
            return this.id.unit;
        }
        public String getLabel(boolean id2) {
            return (id2 && this.id.id2!=null)?this.id.id2:this.id.id;
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
        private boolean useId2() {
            return (this.data_cache != null && this.data_cache.useId2()
                    && this.id_cache != null && this.id_cache.id2 != null);
        }

        @Override
        public String toString() {
            String ret = this.useId2()?this.id_cache.id2:this.s;

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
        public String getId2()  { return (this.id_cache!=null && this.id_cache.id2!=null)?this.id_cache.id2:this.s; }
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
            String ret = this.useId2()?this.id_cache.id2:this.s;

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
        this.filePath = filename; // Store full path for detection purposes
        this.fileId = org.nyet.util.Files.filename(filename); // This was never meant to be a filename. It is just a key used to identify the dataset.
        this.rows = 0;
        this.columns = new LinkedHashMap<String, Column>();

        // Read file and separate comments from CSV data
        StringBuilder csvContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) continue; // skip empty lines
                if (IsLineComment(line)) {
                    this.comments.add(line);
                } else {
                    csvContent.append(line).append("\n");
                }
            }
        }

        // Do detection using collected comment lines BEFORE ParseHeaders
        this.detectLoggerType();

        // Create CSVReader from the filtered content
        CSVReader csvReader = createCSVReaderWithFallback(csvContent.toString(), (reader) -> {
            ParseHeaders(reader, verbose);
        });

        for (final DatasetId id : this.ids) {
            // Put column in map (will replace if duplicate ID exists, but shouldn't happen during CSV parsing)
            Column col = new Column(id.id, id.id2, id.unit);
            this.columns.put(id.id, col);
        }

        String [] nextLine;
        while((nextLine = csvReader.readNext()) != null) {
            if (nextLine.length>0) {
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
                if (gotone) this.rows++;
            }
        }
        buildRanges();
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

    public String id2(Comparable<?> id) {
        final Column c = this.get(id);
        if(c==null) return null;
        return c.getId2();
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
    public void setIds(org.nyet.ecuxplot.DataLogger.HeaderData h, org.nyet.ecuxplot.DataLogger.DataLoggerConfig config) {
        this.ids = config.createDatasetIds(h);
    }

    public ArrayList<String> getLastFilterReasons() { return this.lastFilterReasons; }
    public int length() { return this.rows; }
    public boolean useId2() { return false; }
}

// vim: set sw=4 ts=8 expandtab:
