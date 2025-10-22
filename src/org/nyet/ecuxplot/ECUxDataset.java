package org.nyet.ecuxplot;

import java.io.PrintStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.opencsv.CSVReader;
import flanagan.interpolation.CubicSpline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.nyet.ecuxplot.DataLogger.DataLoggerConfig;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;

public class ECUxDataset extends Dataset {
    private static final Logger logger = LoggerFactory.getLogger(ECUxDataset.class);

    private final Column rpm;
    private Column pedal;
    private Column throttle;
    private Column gear;
    private final Column zboost;
    private final Env env;
    private final Filter filter;
    private double time_ticks_per_sec;	// ECUx has time in ms, JB4 in 1/10s
    private double samples_per_sec=0;
    private CubicSpline [] splines;	// rpm vs time splines
    private String log_detected;

    protected void detectLoggerType() throws Exception {
	// Only detect if not already detected
	if (!DataLogger.isUnknown(this.log_detected)) {
	    logger.info("{}: already detected as {}", this.getFileId(), this.log_detected);
	    return;
	}

	logger.debug("Starting detection for {}", this.getFileId());

	// Step 1: Look in comment lines for signatures (these are raw text, NOT CSV)
	for (int lineNum = 0; lineNum < this.getComments().size(); lineNum++) {
	    // Already trimmed by Dataset.getComments()
	    String line = this.getComments().get(lineNum);
	    logger.debug("Comment line {}: {}", lineNum, line);

	    // Try detection on the entire comment line as raw text
	    String t = DataLogger.detectComment(line);
	    logger.debug("detectComment('{}') returned: '{}'", line, t);
	    if (!DataLogger.isUnknown(t)) {
		logger.info("Detected {} based on comment line {}: \"{}\"", t, lineNum, line);
		this.log_detected = t;
		return;
	    }
	}

	// Step 2: If no detection in comments, try field detection
	logger.debug("No logger type detected in comment lines, trying field detection");

	// Scan CSV lines for field detection
	String t = detectFieldInstance();
	logger.debug("detectField() returned: '{}'", t);
	if (!DataLogger.isUnknown(t)) {
		logger.info("Detected {} based on field line");
		this.log_detected = t;
		return;
	}

	// Step 3: Fallback to UNKNOWN
	logger.debug("No logger type detected, using UNKNOWN");
	this.log_detected = DataLogger.UNKNOWN;
    }

    private String detectFieldInstance() {
	try {
	    // If filePath is not set yet (called from parent constructor), skip field detection
	    if (this.getFilePath() == null) {
		logger.debug("filePath not set yet, skipping field detection");
		return null;
	    }

	    // Read CSV lines from the file
	    try (BufferedReader reader = new BufferedReader(new FileReader(this.getFilePath()))) {
		String line;
		boolean foundFirstCsvLine = false;
		while ((line = reader.readLine()) != null) {
		    // Skip comment empty or comment lines
		    if (line.trim().length() == 0 || Dataset.IsLineComment(line)) continue;
		    // Parse the CSV line using common method with separator fallback
		    String[] csvLine = Dataset.parseCSVLineWithFallback(line);
		    //  unexpected null or empty line
		    if (csvLine == null || csvLine.length == 0) return null;

		    // Skip lines where all CSV fields are empty (e.g., ",,,,,,,,,,,,,,,")
		    // Check if all fields are empty by seeing if any field has content
		    boolean hasContent = false;
		    for (String field : csvLine) {
			if (field != null && field.trim().length() > 0) {
			    hasContent = true;
			    break;
			}
		    }
		    if (!hasContent) continue;

		    boolean numericOrEmpty = allFieldsAreNumeric(csvLine, true);
		    if (!foundFirstCsvLine) {
			if (numericOrEmpty) continue; // Skip numeric data, keep looking
			foundFirstCsvLine = true;
		    } else if (numericOrEmpty) {
			// We've found the first CSV line, and we've hit numeric data, so we're done
			return null;
		    }

		    // Found first non-numeric CSV line (header) or continuing non-numeric data - try detection on it
		    String t = DataLogger.detectField(csvLine);
		    if (t != DataLogger.UNKNOWN) {
			return t;
		    }
		}
	    }
	} catch (Exception e) {
	    logger.debug("Failed to read CSV line for detection: {}", e.getMessage());
	}
	return null;
    }

    private boolean allFieldsAreNumeric(String[] fields, boolean ignoreEmpty) {
	for (String field : fields) {
	    if (ignoreEmpty && (field == null || field.trim().length() == 0)) continue;
	    try {
		Float.parseFloat(field);
	    } catch (NumberFormatException e) {
		return false;
	    }
	}
	return true;
    }



    /**
     * Creates a copy of a String array and trims all non-null elements.
     * @param source the source String array to copy and trim
     * @return a new String array with trimmed elements
     */
    private String[] copyAndTrim(String[] source) {
	String[] result = Arrays.copyOf(source, source.length);
	for (int i = 0; i < result.length; i++) {
	    if (result[i] != null) {
		result[i] = result[i].trim();
	    }
	}
	return result;
    }

    public String getLogDetected() {
	return this.log_detected;
    }

    public ECUxDataset(String filename, Env env, Filter filter, int verbose)
	    throws Exception {
	super(filename, verbose);

	this.env = env;
	this.filter = filter;

	// Get pedal, throttle, and gear columns from DataLogger.pedal(), DataLogger.throttle(), and DataLogger.gear()
	this.pedal = get(DataLogger.pedal());
	if (this.pedal!=null && this.pedal.data.isZero()) this.pedal=null;

	this.throttle = get(DataLogger.throttle());
	if (this.throttle!=null && this.throttle.data.isZero()) this.throttle=null;

	this.gear = get(DataLogger.gear());
	if (this.gear!=null && this.gear.data.isZero()) this.gear=null;

	// look for zeitronix boost for filtering
	this.zboost = get("Zeitronix Boost");
	/*
	if(this.pedal==null && this.throttle==null) {
	    if(this.pedal==null) System.out.println("could not find pedal position data");
	    if(this.throttle==null) System.out.println("could not find throttle position data");
	}
	*/
	/* calculate smallest samples per second */
	final Column time = get("TIME");
	if (time!=null) {
	    for(int i=1;i<time.data.size();i++) {
		final double delta=time.data.get(i)-time.data.get(i-1);
		if(delta>0) {
		    final double rate = 1/delta;
		    if(rate>this.samples_per_sec) this.samples_per_sec=rate;
		}
	    }
	}

	// check for 5120 logged without a 5120 template and double columns with unit of mBar if so
	final Column baroPressure = get("BaroPressure");
	if (baroPressure != null && baroPressure.data.size() > 0 && baroPressure.data.get(0) < 600) {
	    //double time! ;)
	    for (Column column : getColumns()) {
		if (column.getUnits() != null && column.getUnits().toLowerCase().equals("mbar")) {
		    for (int i = 1; i < column.data.size(); i++) {
			column.data.set(i, column.data.get(i) * 2);
		    }
		}
	    }
	}

	// get RPM AFTER getting TIME, so we have an accurate samples per sec
	this.rpm = get("RPM");
	buildRanges(); // regenerate ranges, splines
    }

    private int MAW() {
	/* assume 10 == 1 sec smoothing */
	return (int)Math.floor((this.samples_per_sec/10.0)*this.filter.HPTQMAW());
    }

    private int AccelMAW() {
	/* assume 10 == 1 sec smoothing */
	return (int)Math.floor((this.samples_per_sec/10.0)*this.filter.accelMAW());
    }


    private String [] ParseUnits(String [] id, int verbose) {
	final String [] u = new String[id.length];
	for(int i=0;i<id.length;i++) {
	    final Pattern unitsRegEx =
		Pattern.compile("([\\S\\s]+)\\(([\\S\\s].*)\\)");
	    final Matcher matcher = unitsRegEx.matcher(id[i]);
	    if(matcher.find()) {
		// Trim needed: Regex groups contain untrimmed content from regex extraction
		// Example: "Time (sec)" -> id[i]="Time", u[i]="sec"
		id[i]=matcher.group(1).trim();
		u[i]=matcher.group(2).trim();
	    }
	}
	for(int i=0;i<id.length;i++)
	    logger.trace("pu: '{}' [{}]", id[i], u[i]);
	return u;
    }

    @Override
    public void ParseHeaders(CSVReader reader, int verbose) throws Exception {
	String[] id = null, u = new String[0], id2 = null;
	final String logType = this.log_detected;

	logger.debug("ParseHeaders starting for {}, currently {} (verbose={})", logType, verbose);

	// Get logger configuration for cleaner API
	DataLoggerConfig config = DataLogger.getConfig(logType);
	if (config == null) {
	    logger.error("No configuration found for logger type: {}", logType);
	    return;
	}

	// Apply TIME scaling correction (ticks per second) from logger configuration
        this.time_ticks_per_sec = config.getTimeTicksPerSec();

	// Apply skip_lines/skip_regex (skip_lines is max, skip_regex can exit early)
	// NOTE: This code is messy due to a fundamental design conflict:
	// - skip_regex needs to READ a line to test it against the pattern
	// - But that line might BE a header line that header parsing expects to read
	// - CSVReader has no "peek" or "reset" capability, so we can't unread
	// - Solution: Store the matched line and use it as the first header line
	int skipLines = config.getSkipLines();
	DataLogger.SkipRegex[] skipRegex = config.getSkipRegex();

	// Only skip if either skipLines or skipRegex are defined
	boolean do_skip = skipLines > 0 || skipRegex.length > 0;

	boolean found = false;
	int skipped = 0;
	String[] matchedLine = null; // Store the line that matched skip_regex
	while (do_skip && !found && skipped < skipLines) {
	    String[] line = reader.readNext();
	    if (line == null) {
		logger.error("Reached end of file while skipping lines for {}", logType);
		break;
	    }

	    // Skip empty lines - they don't count towards skip_lines
	    if (line.length == 0 || (line.length == 1 && (line[0] == null || line[0].trim().isEmpty()))) {
		logger.debug("{} {}: Skipped empty line", logType, skipped);
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
				logger.debug("{}: Found skip_regex match '{}' in column {}: '{}'", logType, skipRegexItem.regex, i, cellValue);
				match = true;
				break;
			    }
			}
		    } else if (line.length > skipRegexItem.column) {
			// Check specific column
			String cellValue = line[skipRegexItem.column];
			if (cellValue != null && cellValue.matches(skipRegexItem.regex)) {
			    logger.debug("{}: Found skip_regex match '{}' in column {}: '{}'", logType, skipRegexItem.regex, skipRegexItem.column, cellValue);
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
	    logger.debug("{} {}/{}: Skipped {}", logType, skipped, skipLines, line);
	}

	// Generic header format parsing (YAML feature)
	// Note that the default header format is "id" (single header line with field names), so there should always be at leaset one header token
	String[] formatTokens = config.getHeaderFormatTokens();
	logger.debug("Processing header format for {}: {}", logType, formatTokens);

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
		logger.error("Skipping tokens: {}", Arrays.toString(Arrays.copyOfRange(formatTokens, lineNum, formatTokens.length)));
	}

	// If lineNum < formatTokens.length, we will not be able to handle all the tokens
	for (int i = 0; i < lineNum; i++) {
	    switch (formatTokens[i]) {
		case "id":
		    id = copyAndTrim(headerLines[i]);
		    // Only populate id2 with original field names if no explicit id2 token exists
		    if (!config.hasToken("id2")) {
			id2 = copyAndTrim(headerLines[i]);
		    }
		    logger.debug("Using {} for id: {} fields", id, headerLines[i].length);
		    break;
		case "u":
		    // Check if this is a second 'u' token and if it contains non-numeric strings
		    if (u != null && !allFieldsAreNumeric(headerLines[i], true)) {
			// Second 'u' line contains non-numeric strings (real units), overwrite first 'u'
			u = copyAndTrim(headerLines[i]);
			logger.debug("Second 'u' line contains non-numeric strings, overwriting first 'u': {} fields", headerLines[i].length);
		    } else if (u == null) {
			// First 'u' token, always use it
			u = copyAndTrim(headerLines[i]);
			logger.debug("Using {} for units: {} fields", u, headerLines[i].length);
		    } else {
			// Second 'u' token but doesn't contain non-numeric strings, keep first 'u'
			logger.debug("Second 'u' line doesn't contain non-numeric strings, keeping first 'u'");
		    }
		    break;
		case "id2":
		    id2 = copyAndTrim(headerLines[i]);
		    logger.debug("Using {} for aliases: {} fields", id2, headerLines[i].length);
		    break;
		default:
		    logger.error("Unknown token '{}' at position {}", formatTokens[i], i);
		    break;
	    }
	}

	// Logger-specific header processing
	switch(logType) {
	    case "VCDS": {
		// VCDS uses header_format: "id2,id,u" which gives us:
		// id2 = Group line (for Group/Block detection)
		// id = Field names line (clean field names)
		// u = Units line (actual units)
		// NOTE: VCDS files have varying header formats. Standard files (vcds-1.csv, vcds-german.csv)
		// work correctly, but non-standard files like vcds-2.csv may have extra header lines
		// that shift the units to the wrong position, making unit extraction unreliable.

		for (int i = 0; i < id.length; i++) {
		    String g = (id2 != null && i < id2.length) ? id2[i] : null; // save off group for later, with bounds check
		    if (id2 != null && i < id2.length) {
			id2[i] = id[i]; // id2 gets copy of original field names, before aliases are applied
		    }
		    // VCDS TIME field detection - use field name as source of truth, fallback to units for empty fields
		    if (u != null && i < u.length) {
			// Use field name as the source of truth for TIME fields
			if (id[i] != null && id[i].matches("^(TIME|Zeit|Time|STAMP|MARKE)$")) {
			    id[i] = "TIME";  // Normalize to uppercase
			    u[i] = "s";      // Set units to seconds
			} else if ((id[i] == null || id[i].trim().isEmpty()) && u[i] != null && u[i].matches("^(STAMP|MARKE)$")) {
			    // For empty field names, check if unit is STAMP or MARKE
			    id[i] = "TIME";
			    u[i] = "s";
			}
		    }
		    // Group 24 blacklist logic (using id2 as Group line)
		    if (g != null && g.matches("^Group 24.*") && id[i].equals("Accelerator position")) {
			id[i] = "AcceleratorPedalPosition (G024)";
		    }
		    logger.debug("VCDS field {}: '{}' (unit: '{}')", i, id[i], u != null && i < u.length ? u[i] : "N/A");
		}
		break;
	    }

	}

	// Apply unit_regex parsing if configured
	if (config.parser.unitRegex != null) {
	    logger.debug("Applying unit_regex: '{}'", config.parser.unitRegex);
	    Pattern unitRegexPattern = Pattern.compile(config.parser.unitRegex);

	    // Initialize units array if not already done
	    if (u == null || u.length == 0) {
		u = new String[id.length];
	    }

	    // Initialize id2 array to preserve original field names
	    if (id2 == null) {
		id2 = new String[id.length];
	    }

	    for (int i = 0; i < id.length; i++) {
		if (id[i] != null) {
		    Matcher matcher = unitRegexPattern.matcher(id[i]);
		    if (matcher.find()) {
			// Group 1 -> field name, Group 2 -> unit, Group 3 -> ME7L variable
			String originalField = id[i];
			id[i] = matcher.group(1).trim();
			u[i] = matcher.group(2).trim();
			// Store ME7L variable in id2 if available
			if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
			    id2[i] = matcher.group(3).trim();
			}
			logger.debug("Unit regex matched field {}: '{}' -> id='{}', unit='{}', id2='{}'", i, originalField, id[i], u[i], id2[i]);
		    } else {
			logger.debug("Unit regex did not match field {}: '{}'", i, id[i]);
		    }
		}
	    }
	}

	// Process aliases for all logger types
	config.processAliases(id);

	// Parse units for loggers that don't have their own unit processing
	// Only apply general unit parsing if logger doesn't have header_format with units
	if (!DataLogger.isUnknown(logType)) {
	    String[] loggerHeaderFormatTokens = config.getHeaderFormatTokens();
	    // If no header_format or header_format doesn't include units ("u"), apply general unit parsing
	    boolean hasUnitsToken = false;
	    for (String token : loggerHeaderFormatTokens) {
		if ("u".equals(token)) {
		    hasUnitsToken = true;
		    break;
		}
	    }
	    if (loggerHeaderFormatTokens.length == 0 || !hasUnitsToken) {
		u = ParseUnits(id, verbose);
	    }
	}

	// Process and normalize units
	u = Units.processUnits(id, u);

	// Apply field transformations for all logger types (prepend/append)
	// This must happen AFTER unit processing so units can be found for original field names
	config.applyFieldTransformations(id, id2);

	// Log final results
	for (int i = 0; i < id.length; i++) {
	    if (id2 != null && i < id2.length) {
		logger.debug("Final field {}: '{}' (original: '{}') [{}]", i, id[i], id2[i], u[i]);
	    } else {
		logger.debug("Final field {}: '{}' [{}]", i, id[i], u[i]);
	    }
	}

	// Create DatasetId objects
	DatasetId[] ids = new DatasetId[id.length];
	for (int i = 0; i < id.length; i++) {
	    ids[i] = new DatasetId(id[i]);
	    if (id2 != null && i < id2.length) ids[i].id2 = id2[i];
	    if (u != null && i < u.length) ids[i].unit = u[i];
	    ids[i].type = this.log_detected;
	}
	this.setIds(ids);
    }

    private DoubleArray drag (DoubleArray v) {
	final double rho=1.293;	// kg/m^3 air, standard density

	final DoubleArray windDrag = v.pow(3).mult(0.5 * rho * this.env.c.Cd() *
	    this.env.c.FA());

	final DoubleArray rollingDrag = v.mult(this.env.c.rolling_drag() *
	    this.env.c.mass() * UnitConstants.STANDARD_GRAVITY);

	return windDrag.add(rollingDrag);
    }

    private DoubleArray toPSI(DoubleArray abs) {
	final Column ambient = this.get("BaroPressure");
	if(ambient==null) return abs.add(-UnitConstants.MBAR_PER_ATM).div(UnitConstants.MBAR_PER_PSI);
	return abs.sub(ambient.data).div(UnitConstants.MBAR_PER_PSI);
    }

    private static DoubleArray toCelcius(DoubleArray f) {
	return f.add(-UnitConstants.CELSIUS_TO_FAHRENHEIT_OFFSET).mult(1.0/UnitConstants.CELSIUS_TO_FAHRENHEIT_FACTOR);
    }

    private static DoubleArray toFahrenheit(DoubleArray c) {
	return c.mult(UnitConstants.CELSIUS_TO_FAHRENHEIT_FACTOR).add(UnitConstants.CELSIUS_TO_FAHRENHEIT_OFFSET);
    }

    // given a list of id's, find the first that exists
    public Column get(Comparable<?> [] id) {
	for (final Comparable<?> k : id) {
	    Column ret = null;
	    try { ret=_get(k);
	    } catch (final NullPointerException e) {
	    }
	    if(ret!=null) return ret;
	}
	return null;
    }

    @Override
    public Column get(Comparable<?> id) {
	try {
	    return _get(id);
	} catch (final NullPointerException e) {
	    return null;
	}
    }

    private Column _get(Comparable<?> id) {
	Column c=null;
	if(id.equals("Sample")) {
	    final double[] idx = new double[this.length()];
	    for (int i=0;i<this.length();i++)
		idx[i]=i;
	    final DoubleArray a = new DoubleArray(idx);
	    c = new Column("Sample", "#", a);
	} else if(id.equals("TIME")) {
	    final DoubleArray a = super.get("TIME").data;
	    c = new Column("TIME", "s", a.div(this.time_ticks_per_sec));
	} else if(id.equals("RPM")) {
	    // smooth sampling quantum noise/jitter, RPM is an integer!
	    if (this.samples_per_sec>10) {
		final DoubleArray a = super.get("RPM").data.smooth();
		c = new Column(id, "RPM", a);
	    }
	} else if(id.equals("RPM - raw")) {
	    c = new Column(id, "RPM", super.get("RPM").data);
	} else if(id.equals("Sim Load")) {
	    // g/sec to kg/hr
	    final DoubleArray a = super.get("MassAirFlow").data.mult(UnitConstants.GPS_PER_KGH);
	    final DoubleArray b = super.get("RPM").data.smooth();

	    // KUMSRL
	    c = new Column(id, "%", a.div(b).div(.001072));
	} else if(id.equals("Sim Load Corrected")) {
	    // g/sec to kg/hr
	    final DoubleArray a = this.get("Sim MAF").data.mult(UnitConstants.GPS_PER_KGH);
	    final DoubleArray b = this.get("RPM").data;

	    // KUMSRL
	    c = new Column(id, "%", a.div(b).div(.001072));
	} else if(id.equals("MassAirFlow (kg/hr)")) {
	    // mass in g/sec
	    final DoubleArray maf = super.get("MassAirFlow").data;
	    c = new Column(id, "kg/hr", maf.mult(UnitConstants.GPS_PER_KGH));
	} else if(id.equals("Sim MAF")) {
	    // mass in g/sec
	    final DoubleArray a = super.get("MassAirFlow").data.
		mult(this.env.f.MAF_correction()).add(this.env.f.MAF_offset());
	    c = new Column(id, "g/sec", a);
	} else if(id.equals("MassAirFlow df/dt")) {
	    // mass in g/sec
	    final DoubleArray maf = super.get("MassAirFlow").data;
	    final DoubleArray time = this.get("TIME").data;
	    c = new Column(id, "g/sec^s", maf.derivative(time).max(0));
	} else if(id.equals("Turbo Flow")) {
	    final DoubleArray a = this.get("Sim MAF").data;
	    c = new Column(id, "m^3/sec", a.div(1225*this.env.f.turbos()));
	} else if(id.equals("Turbo Flow (lb/min)")) {
	    final DoubleArray a = this.get("Sim MAF").data;
	    c = new Column(id, "lb/min", a.div(7.55*this.env.f.turbos()));
	} else if(id.equals("Sim Fuel Mass")) {	// based on te
	    final double gps_per_ccmin = 0.0114; // (grams/sec) per (cc/min)
	    final double gps = this.env.f.injector()*gps_per_ccmin;
	    final double cylinders = this.env.f.cylinders();
	    final Column bank1 = this.get("EffInjectorDutyCycle");
	    final Column bank2 = this.get("EffInjectorDutyCycleBank2");
	    DoubleArray duty = bank1.data;
	    /* average two duties for overall mass */
	    if (bank2!=null) duty = duty.add(bank2.data).div(2);
	    final DoubleArray a = duty.mult(cylinders*gps/100);
	    c = new Column(id, "g/sec", a);
	} else if(id.equals("TargetAFRDriverRequest (AFR)")) {
	    final DoubleArray abs = super.get("TargetAFRDriverRequest").data;
	    c = new Column(id, "AFR", abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("AirFuelRatioDesired (AFR)")) {
	    final DoubleArray abs = super.get("AirFuelRatioDesired").data;
	    c = new Column(id, "AFR", abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("AirFuelRatioCurrent (AFR)")) {
	    final DoubleArray abs = super.get("AirFuelRatioCurrent").data;
	    c = new Column(id, "AFR", abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("AirFuelRatioCurrentBank1 (AFR)")) {
	    final DoubleArray abs = super.get("AirFuelRatioCurrentBank1").data;
	    c = new Column(id, "AFR", abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("AirFuelRatioCurrentBank2 (AFR)")) {
	    final DoubleArray abs = super.get("AirFuelRatioCurrentBank2").data;
	    c = new Column(id, "AFR", abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("Lambda Bank 1 (AFR)")) {
	    final DoubleArray abs = super.get("Lambda Bank 1").data;
	    c = new Column(id, "AFR", abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("Lambda Bank 2 (AFR)")) {
	    final DoubleArray abs = super.get("Lambda Bank 2").data;
	    c = new Column(id, "AFR", abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("Sim AFR")) {
	    final DoubleArray a = this.get("Sim MAF").data;
	    final DoubleArray b = this.get("Sim Fuel Mass").data;
	    c = new Column(id, "AFR", a.div(b));
	} else if(id.equals("Sim lambda")) {
	    final DoubleArray a = this.get("Sim AFR").data.div(UnitConstants.STOICHIOMETRIC_AFR);
	    c = new Column(id, "lambda", a);
	} else if(id.equals("Sim lambda error")) {
	    final DoubleArray a = super.get("AirFuelRatioDesired").data;
	    final DoubleArray b = this.get("Sim lambda").data;
	    c = new Column(id, "%", a.div(b).mult(-1).add(1).mult(100).
		max(-25).min(25));

	} else if(id.equals("FuelInjectorDutyCycle")) {
	    final DoubleArray a = super.get("FuelInjectorOnTime").data.	/* ti */
		div(60*1000);	/* assumes injector on time is in ms */

	    final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("EffInjectorDutyCycle")) {		/* te */
	    final DoubleArray a = super.get("EffInjectionTime").data.
		div(60*1000);	/* assumes injector on time is in ms */

	    final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	} else if(id.equals("EffInjectorDutyCycleBank2")) {		/* te */
	    final DoubleArray a = super.get("EffInjectionTimeBank2").data.
		div(60*1000);	/* assumes injector on time is in ms */

	    final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
/*****************************************************************************/
	/* if log contains Engine torque */
	} else if(id.equals("Engine torque (ft-lb)")) {
	    final DoubleArray tq = this.get("Engine torque").data;
	    final DoubleArray value = tq.mult(UnitConstants.NM_PER_FTLB);	// nm to ft-lb
	    c = new Column(id, "ft-lb", value);
	} else if(id.equals("Engine HP")) {
	    final DoubleArray tq = this.get("Engine torque (ft-lb)").data;
	    final DoubleArray rpm = this.get("RPM").data;
	    final DoubleArray value = tq.div(UnitConstants.HP_CALCULATION_FACTOR).mult(rpm);
	    c = new Column(id, "HP", value);
/*****************************************************************************/
	} else if(id.equals("VehicleSpeed (MPH)")) {
	    Column rawVehicleSpeed = this.get("VehicleSpeed");
	    if (rawVehicleSpeed != null) {
		// Case 1: Convert raw VehicleSpeed to MPH
		final DoubleArray a = rawVehicleSpeed.data.mult(UnitConstants.KMH_PER_MPH);
		c = new Column(id, "MPH", a);
	    } else {
		// Case 2: Calculate VehicleSpeed from RPM
		final DoubleArray rpm = this.get("RPM").data;
		final DoubleArray calculatedMph = rpm.div(this.env.c.rpm_per_mph());
		c = new Column(id, "MPH", calculatedMph);
	    }
	} else if(id.equals("Calc Velocity")) {
	    // TODO: Issue #57 - Rework unit conversions to be based on units, not column name
	    // Current conversions are hardcoded based on field names rather than actual units
	    // This makes the system brittle and requires manual updates for each field
	    // TODO: give user option to use raw VehicleSpeed or calculated from RPM
	    // VehicleSpeed sensors are notorioiusly inaccurate.
	    // Better to depend on RPM and user specified rpm_per_mph
	    final DoubleArray rpm = this.get("RPM").data;
	    c = new Column(id, "m/s", rpm.div(this.env.c.rpm_per_mph()).
		div(UnitConstants.MPS_PER_MPH));
	} else if(id.equals("Acceleration (RPM/s)")) {
	    final DoubleArray y = this.get("RPM").data;
	    final DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x, this.AccelMAW()).max(0));
	} else if(id.equals("Acceleration - raw (RPM/s)")) {
	    final DoubleArray y = this.get("RPM - raw").data;
	    final DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "RPM/s", y.derivative(x));
	} else if(id.equals("Acceleration (m/s^2)")) {
	    final DoubleArray y = this.get("Calc Velocity").data;
	    final DoubleArray x = this.get("TIME").data;
	    c = new Column(id, "m/s^2", y.derivative(x, this.MAW()).max(0));
	} else if(id.equals("Acceleration (g)")) {
	    final DoubleArray a = this.get("Acceleration (m/s^2)").data;
	    c = new Column(id, "g", a.div(UnitConstants.STANDARD_GRAVITY));
/*****************************************************************************/
	} else if(id.equals("WHP")) {
	    final DoubleArray a = this.get("Acceleration (m/s^2)").data;
	    final DoubleArray v = this.get("Calc Velocity").data;
	    final DoubleArray whp = a.mult(v).mult(this.env.c.mass()).
		add(this.drag(v));	// in watts

	    DoubleArray value = whp.mult(1.0 / UnitConstants.HP_PER_WATT);
	    String l = "HP";
	    if(this.env.sae.enabled()) {
		value = value.mult(this.env.sae.correction());
		l += " (SAE)";
	    }
	    c = new Column(id, l, value.movingAverage(this.MAW()));
	} else if(id.equals("HP")) {
	    final DoubleArray whp = this.get("WHP").data;
	    final DoubleArray value = whp.div((1-this.env.c.driveline_loss())).
		    add(this.env.c.static_loss());
	    String l = "HP";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("WTQ")) {
	    final DoubleArray whp = this.get("WHP").data;
	    final DoubleArray rpm = this.get("RPM").data;
	    final DoubleArray value = whp.mult(UnitConstants.HP_CALCULATION_FACTOR).div(rpm);
	    String l = "ft-lb";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("TQ")) {
	    final DoubleArray hp = this.get("HP").data;
	    final DoubleArray rpm = this.get("RPM").data;
	    final DoubleArray value = hp.mult(UnitConstants.HP_CALCULATION_FACTOR).div(rpm);
	    String l = "ft-lb";
	    if(this.env.sae.enabled()) l += " (SAE)";
	    c = new Column(id, l, value);
	} else if(id.equals("Drag")) {
	    final DoubleArray v = this.get("Calc Velocity").data;
	    final DoubleArray drag = this.drag(v);
	    c = new Column(id, "HP", drag.mult(1.0 / UnitConstants.HP_PER_WATT));
	} else if(id.equals("IntakeAirTemperature")) {
	    c = super.get(id);
	    if (c.getUnits().matches(".*C$"))
		c = new Column(id, "\u00B0F", ECUxDataset.toFahrenheit(c.data));
	} else if(id.equals("IntakeAirTemperature (C)")) {
	    c = super.get("IntakeAirTemperature");
	    if (c.getUnits().matches(".*F$"))
		c = new Column(id, "\u00B0C", ECUxDataset.toCelcius(c.data));
	} else if(id.equals("BoostPressureDesired (PSI)")) {
	    c = super.get("BoostPressureDesired");
	    if (!c.getUnits().matches("PSI"))
		c = new Column(id, "PSI", this.toPSI(c.data));
	} else if(id.equals("BoostPressureDesired")) {
	    final Column delta = super.get("BoostPressureDesiredDelta");
	    if (delta != null) {
		final Column ecu = super.get("ECUBoostPressureDesired");
		if (ecu != null) {
		    c = new Column(id, "PSI", ecu.data.add(delta.data));
		}
	    }
	} else if(id.equals("BoostPressureActual (PSI)")) {
	    c = super.get("BoostPressureActual");
	    if (!c.getUnits().matches("PSI"))
		c = new Column(id, "PSI", this.toPSI(c.data));
	} else if(id.equals("Zeitronix Boost (PSI)")) {
	    final DoubleArray boost = super.get("Zeitronix Boost").data;
	    c = new Column(id, "PSI", boost.movingAverage(this.filter.ZeitMAW()));
	} else if(id.equals("Zeitronix Boost")) {
	    final DoubleArray boost = this.get("Zeitronix Boost (PSI)").data;
	    c = new Column(id, "mBar", boost.mult(UnitConstants.MBAR_PER_PSI).add(UnitConstants.MBAR_PER_ATM));
	} else if(id.equals("Zeitronix AFR (lambda)")) {
	    final DoubleArray abs = super.get("Zeitronix AFR").data;
	    c = new Column(id, "lambda", abs.div(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("Zeitronix Lambda (AFR)")) {
	    final DoubleArray abs = super.get("Zeitronix Lambda").data;
	    c = new Column(id, "AFR", abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
	} else if(id.equals("BoostDesired PR")) {
	    final Column act = super.get("BoostPressureDesired");
	    try {
		final DoubleArray ambient = super.get("BaroPressure").data;
		c = new Column(id, "PR", act.data.div(ambient));
	    } catch (final Exception e) {
		if (act.getUnits().matches("PSI"))
		    c = new Column(id, "PR", act.data.div(UnitConstants.STOICHIOMETRIC_AFR));
		else
		    c = new Column(id, "PR", act.data.div(UnitConstants.MBAR_PER_ATM));
	    }

	} else if(id.equals("BoostActual PR")) {
	    final Column act = super.get("BoostPressureActual");
	    try {
		final DoubleArray ambient = super.get("BaroPressure").data;
		c = new Column(id, "PR", act.data.div(ambient));
	    } catch (final Exception e) {
		if (act.getUnits().matches("PSI"))
		    c = new Column(id, "PR", act.data.div(UnitConstants.STOICHIOMETRIC_AFR));
		else
		    c = new Column(id, "PR", act.data.div(UnitConstants.MBAR_PER_ATM));
	    }
	} else if(id.equals("Sim evtmod")) {
	    final DoubleArray tans = this.get("IntakeAirTemperature (C)").data;
	    DoubleArray tmot = tans.ident(95);
	    try {
		tmot = this.get("CoolantTemperature").data;
	    } catch (final Exception e) {}

	    // KFFWTBR=0.02
	    // evtmod = tans + (tmot-tans)*KFFWTBR
	    final DoubleArray evtmod = tans.add((tmot.sub(tans)).mult(0.02));
	    c = new Column(id, "\u00B0C", evtmod);
	} else if(id.equals("Sim ftbr")) {
	    final DoubleArray tans = this.get("IntakeAirTemperature (C)").data;
	    final DoubleArray evtmod = this.get("Sim evtmod").data;
	    // linear fit to stock FWFTBRTA
	    // fwtf = (tans+637.425)/731.334

	    final DoubleArray fwft = tans.add(673.425).div(731.334);

	    // ftbr = 273/(tans+273) * fwft

	    //    (tans+637.425)      273
	    //    -------------- *  -------
	    //      (tans+273)      731.334

	    // ftbr=273/(evtmod-273) * fwft
	    c = new Column(id, "", evtmod.ident(273).div(evtmod.add(273)).mult(fwft));
	} else if(id.equals("Sim BoostIATCorrection")) {
	    final DoubleArray ftbr = this.get("Sim ftbr").data;
	    c = new Column(id, "", ftbr.inverse());
	} else if(id.equals("Sim BoostPressureDesired")) {
	    final boolean SY_BDE = false;
	    final boolean SY_AGR = true;
	    DoubleArray load;
	    DoubleArray ps;

	    try {
		load = super.get("EngineLoadRequested").data; // rlsol
	    } catch (final Exception e) {
		load = super.get("EngineLoadCorrected").data; // rlmax
	    }

	    try {
		ps = super.get("ME7L ps_w").data;
	    } catch (final Exception e) {
		ps = super.get("BoostPressureActual").data;
	    }

	    DoubleArray ambient = ps.ident(UnitConstants.MBAR_PER_ATM); // pu
	    try {
		ambient	= super.get("BaroPressure").data;
	    } catch (final Exception e) { }

	    DoubleArray fupsrl = load.ident(0.1037); // KFURL
	    try {
		final DoubleArray ftbr = this.get("Sim ftbr").data;
		// fupsrl = KFURL * ftbr
		fupsrl = fupsrl.mult(ftbr);
	    } catch (final Exception e) {}

	    // pirg = fho * KFPRG = (pu/UnitConstants.MBAR_PER_ATM) * 70
	    final DoubleArray pirg = ambient.mult(70/UnitConstants.MBAR_PER_ATM);

	    if (!SY_BDE) {
		//load = load.sub(rlr);
		load = load.max(0);	// rlfgs
		if (SY_AGR) {
		    // pbr = ps * fpbrkds
		    // rfges = (pbr-pirg).max(0)*fupsrl
		    final DoubleArray rfges = (ps.mult(1.106)).sub(pirg).max(0).mult(fupsrl);
		    // psagr = 250??
		    // rfagr = rfges * psagr/ps
		    // load = rlfgs + rfagr;
		    load = load.add(rfges.mult(250).div(ps));
		}
		//load = load.add(rlr);
	    }

	    DoubleArray boost = load.div(fupsrl);

	    if (SY_BDE) {
		boost = boost.add(pirg);
	    }

	    // fpbrkds from KFPBRK/KFPBRKNW
	    boost = boost.div(1.016);	// pssol

	    // vplsspls from KFVPDKSD/KFVPDKSDSE
	    boost = boost.div(1.016);	// plsol

	    c = new Column(id, "mBar", boost.max(ambient));
	} else if(id.equals("Boost Spool Rate (RPM)")) {
	    final DoubleArray abs = super.get("BoostPressureActual").data.smooth();
	    final DoubleArray rpm = this.get("RPM").data;
	    c = new Column(id, "mBar/RPM", abs.derivative(rpm).max(0));
	} else if(id.equals("Boost Spool Rate Zeit (RPM)")) {
	    final DoubleArray boost = this.get("Zeitronix Boost").data.smooth();
	    final DoubleArray rpm =
		this.get("RPM").data.movingAverage(this.filter.ZeitMAW()).smooth();
	    c = new Column(id, "mBar/RPM", boost.derivative(rpm).max(0));
	} else if(id.equals("Boost Spool Rate (time)")) {
	    final DoubleArray abs = this.get("BoostPressureActual (PSI)").data.smooth();
	    final DoubleArray time = this.get("TIME").data;
	    c = new Column(id, "PSI/sec", abs.derivative(time, this.MAW()).max(0));
	} else if(id.equals("ps_w error")) {
	    final DoubleArray abs = super.get("BoostPressureActual").data.max(900);
	    final DoubleArray ps_w = super.get("ME7L ps_w").data.max(900);
	    //c = new Column(id, "%", abs.div(ps_w).sub(1).mult(-100));
	    c = new Column(id, "lambda", ps_w.div(abs));
	} else if(id.equals("LDR error")) {
	    final DoubleArray set = super.get("BoostPressureDesired").data;
	    final DoubleArray out = super.get("BoostPressureActual").data;
	    c = new Column(id, "100mBar", set.sub(out).div(100));
	} else if(id.equals("LDR de/dt")) {
	    final DoubleArray set = super.get("BoostPressureDesired").data;
	    final DoubleArray out = super.get("BoostPressureActual").data;
	    final DoubleArray t = this.get("TIME").data;
	    final DoubleArray o = set.sub(out).derivative(t,this.MAW());
	    c = new Column(id,"100mBar",o.mult(this.env.pid.time_constant).div(100));
	} else if(id.equals("LDR I e dt")) {
	    final DoubleArray set = super.get("BoostPressureDesired").data;
	    final DoubleArray out = super.get("BoostPressureActual").data;
	    final DoubleArray t = this.get("TIME").data;
	    final DoubleArray o = set.sub(out).
		integral(t,0,this.env.pid.I_limit/this.env.pid.I*100);
	    c = new Column(id,"100mBar",o.div(this.env.pid.time_constant).div(100));
	} else if(id.equals("LDR PID")) {
	    final DoubleArray.TransferFunction fP =
		new DoubleArray.TransferFunction() {
		    @Override
		    public final double f(double x, double y) {
			if(Math.abs(x)<ECUxDataset.this.env.pid.P_deadband/100) return 0;
			return x*ECUxDataset.this.env.pid.P;
		    }
	    };
	    final DoubleArray.TransferFunction fD =
		new DoubleArray.TransferFunction() {
		    @Override
		    public final double f(double x, double y) {
			y=Math.abs(y);
			if(y<3) return x*ECUxDataset.this.env.pid.D[0];
			if(y<5) return x*ECUxDataset.this.env.pid.D[1];
			if(y<7) return x*ECUxDataset.this.env.pid.D[2];
			return x*ECUxDataset.this.env.pid.D[3];
		    }
	    };
	    final DoubleArray E = this.get("LDR error").data;
	    final DoubleArray P = E.func(fP);
	    final DoubleArray I = this.get("LDR I e dt").data.mult(this.env.pid.I);
	    final DoubleArray D = this.get("LDR de/dt").data.func(fD,E);
	    c = new Column(id, "%", P.add(I).add(D).max(0).min(95));
	} else if(id.equals("Sim pspvds")) {
	    final DoubleArray ps_w = super.get("ME7L ps_w").data;
	    final DoubleArray pvdkds = super.get("BoostPressureActual").data;
	    c = new Column(id,"",ps_w.div(pvdkds));
/*****************************************************************************/
	} else if(id.equals("IgnitionTimingAngleOverallDesired")) {
	    DoubleArray averetard = null;
	    int count=0;
	    for(int i=0;i<8;i++) {
		final Column retard = this.get("IgnitionRetardCyl" + i);
		if(retard!=null) {
		    if(averetard==null) averetard = retard.data;
		    else averetard = averetard.add(retard.data);
		    count++;
		}
	    }
	    DoubleArray out = this.get("IgnitionTimingAngleOverall").data;
	    if(count>0) {
		// assume retard is always positive... some loggers log it negative
		// abs it to normalize
		out = out.add(averetard.div(count).abs());
	    }
	    c = new Column(id, "\u00B0", out);
/*****************************************************************************/
	} else if(id.equals("Sim LoadSpecified correction")) {
	    final DoubleArray cs = super.get("EngineLoadCorrected").data;
	    final DoubleArray s = super.get("EngineLoadSpecified").data;
	    c = new Column(id, "K", cs.div(s));
/*****************************************************************************/
	}

	if(c==null) {
	    /* Calc True Timing */
	    if (id.toString().endsWith(" (ms)")) {
		String s = id.toString();
		s = s.substring(0, s.length()-5);
		final Column t = this.get(s);
		if (t!=null) {
		    final DoubleArray r = this.get("RPM").data;
		    c = new Column(id, "(ms)", t.data.div(r.mult(.006)));
		}
	    }
	}

	if(c!=null) {
	    this.getColumns().add(c);
	    return c;
	}
	return super.get(id);
    }

    @Override
    protected boolean dataValid(int i) {
	boolean ret = true;
	if(this.filter==null) return ret;
	if(!this.filter.enabled()) return ret;

	final ArrayList<String> reasons = new ArrayList<String>();

	if(this.filter.gear()>=0 && this.gear!=null && Math.round(this.gear.data.get(i)) != this.filter.gear()) {
	    reasons.add("gear " + Math.round(this.gear.data.get(i)) +
		    "!=" + this.filter.gear());
	    ret=false;
	}
	if(this.pedal!=null && this.pedal.data.get(i)<this.filter.minPedal()) {
	    reasons.add("pedal " + this.pedal.data.get(i) +
		    "<" + this.filter.minPedal());
	    ret=false;
	}
	if(this.throttle!=null && this.throttle.data.get(i)<this.filter.minThrottle()) {
	    reasons.add("throttle " + this.throttle.data.get(i) +
		    "<" + this.filter.minThrottle());
	    ret=false;
	}
	if(this.filter.minAcceleration()>0) {
	    final Column accel = this.get("Acceleration (RPM/s)");
	    if(accel!=null && accel.data.get(i)<this.filter.minAcceleration()) {
		reasons.add("acceleration " + accel.data.get(i) +
		    "<" + this.filter.minAcceleration() + " RPM/s");
		ret=false;
	    }
	}
	if(this.zboost!=null && this.zboost.data.get(i)<0) {
	    reasons.add("zboost " + this.zboost.data.get(i) +
		    "<0");
	    ret=false;
	}
	// Check for negative boost pressure (wheel spin detection)
	final Column boostActual = this.get("BoostPressureActual");
	if(boostActual!=null && boostActual.data.get(i)<1000) {
	    reasons.add("boost actual " + boostActual.data.get(i) +
		    "<1000 mBar (vacuum)");
	    ret=false;
	}
	final Column boostDesired = this.get("BoostPressureDesired");
	if(boostDesired!=null && boostDesired.data.get(i)<1000) {
	    reasons.add("boost desired " + boostDesired.data.get(i) +
		    "<1000 mBar (vacuum)");
	    ret=false;
	}
	if(this.rpm!=null) {
	    if(this.rpm.data.get(i)<this.filter.minRPM()) {
		reasons.add("rpm " + this.rpm.data.get(i) +
		    "<" + this.filter.minRPM());
		ret=false;
	    }
	    if(this.rpm.data.get(i)>this.filter.maxRPM()) {
		reasons.add("rpm " + this.rpm.data.get(i) +
		    ">" + this.filter.maxRPM());
		ret=false;
	    }
	    if(i>0 && this.rpm.data.size()>i+2 &&
		this.rpm.data.get(i-1)-this.rpm.data.get(i+1)>this.filter.monotonicRPMfuzz()) {
		reasons.add("rpm delta " +
		    this.rpm.data.get(i-1) + "-" + this.rpm.data.get(i+1) + ">" +
		    this.filter.monotonicRPMfuzz());
		ret=false;
	    }
	}

	if (!ret) {
	    this.lastFilterReasons = reasons;
	    logger.trace("Filter rejected data point {}: {}", i, String.join(", ", reasons));
	}

	return ret;
    }

    @Override
    protected boolean rangeValid(Range r) {
	boolean ret = true;
	if(this.filter==null) return ret;
	if(!this.filter.enabled()) return ret;

	final ArrayList<String> reasons = new ArrayList<String>();

	if(r.size()<this.filter.minPoints()) {
	    reasons.add("points " + r.size() + "<" +
		this.filter.minPoints());
	    ret=false;
	}
	if(this.rpm!=null) {
	    if(this.rpm.data.get(r.end)<this.rpm.data.get(r.start)+this.filter.minRPMRange()) {
		reasons.add("RPM Range " + this.rpm.data.get(r.end) +
		    "<" + this.rpm.data.get(r.start) + "+" +this.filter.minRPMRange());
		ret=false;
	    }
	}

	if (!ret) {
	    this.lastFilterReasons = reasons;
	    logger.trace("Filter rejected range {}: {}", r, String.join(", ", reasons));
	}

	return ret;
    }

    private static final PrintStream nullStdout() {
	PrintStream original = System.out;
	System.setOut(new PrintStream(new OutputStream() {
		    public void write(int b) {
			//DO NOTHING
		    }
		}));
	return original;
    }

    @Override
    public void buildRanges() {
	super.buildRanges();
	final ArrayList<Dataset.Range> ranges = this.getRanges();
	this.splines = new CubicSpline[ranges.size()];
	for(int i=0;i<ranges.size();i++) {
	    this.splines[i] = null;
	    final Dataset.Range r=ranges.get(i);
	    final double [] rpm = this.getData("RPM", r);
	    final double [] time = this.getData("TIME", r);

	    // Need three points for a spline
	    if(rpm == null || time == null || time.length != rpm.length || rpm.length<3)
		continue;

	    PrintStream original = null;
	    try {
		original = nullStdout();	// hack to disable junk that CubicSpline prints
		this.splines[i] = new CubicSpline(rpm, time);
		System.setOut(original);
		original = null;
	    } catch (final Exception e) {
		// restore stdout if we caught something
		if(original != null) System.setOut(original);
		logger.warn("CubicSpline:", e);
	    }
	}
    }

    /**
     * Calculate FATS (Fast Acceleration Time System) for a specific run using RPM values
     *
     * @param run The run number (0-based index into the ranges array)
     * @param RPMStart The starting RPM value for the calculation
     * @param RPMEnd The ending RPM value for the calculation
     * @return The elapsed time in seconds between the specified RPM points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    public double calcFATS(int run, int RPMStart, int RPMEnd) throws Exception {
	return calcFATSRPM(run, RPMStart, RPMEnd);
    }

    /**
     * Calculate FATS (Fast Acceleration Time System) for a specific run using speed values
     *
     * This method converts the provided speed values to RPM using the rpm_per_mph constant,
     * then performs the FATS calculation using RPM-based interpolation for consistency.
     *
     * @param run The run number (0-based index into the ranges array)
     * @param speedStart The starting speed value (in MPH)
     * @param speedEnd The ending speed value (in MPH)
     * @return The elapsed time in seconds between the specified speed points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    public double calcFATSBySpeed(int run, double speedStart, double speedEnd) throws Exception {
	return calcFATSMPH(run, speedStart, speedEnd);
    }

    /**
     * Internal RPM-based FATS calculation method
     *
     * This method performs the core FATS calculation using cubic spline interpolation
     * on RPM vs time data. It is used by both RPM and MPH modes for consistency.
     *
     * @param run The run number (0-based index into the ranges array)
     * @param RPMStart The starting RPM value for the calculation
     * @param RPMEnd The ending RPM value for the calculation
     * @return The elapsed time in seconds between the specified RPM points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    private double calcFATSRPM(int run, int RPMStart, int RPMEnd) throws Exception {
	final ArrayList<Dataset.Range> ranges = this.getRanges();
	if(run<0 || run>=ranges.size())
	    throw new Exception("FATS run " + run + " not found (available: 0-" + (ranges.size()-1) + ")");

	if(this.splines[run]==null)
	    throw new Exception("FATS run " + run + " interpolation failed - check filter settings");

	final Dataset.Range r=ranges.get(run);
	logger.trace("FATS RPM calculation: run={}, range={}-{}", run, r.start, r.end);

	// RPM-based calculation using filter range
	logger.trace("FATS RPM calculation: {} RPM -> {} RPM", RPMStart, RPMEnd);
	// Trust the filter - if we have a valid range, use spline interpolation
	final double et = this.splines[run].interpolate(RPMEnd) -
		this.splines[run].interpolate(RPMStart);
	if(et<=0)
	    throw new Exception("FATS RPM calculation failed: timeEnd <= timeStart for RPM range " + RPMStart + "-" + RPMEnd);

	return et;
    }

    /**
     * Internal MPH-based FATS calculation method
     *
     * This method converts MPH values to RPM using the rpm_per_mph constant,
     * then delegates to the RPM calculation method for consistency.
     *
     * @param run The run number (0-based index into the ranges array)
     * @param speedStart The starting speed value (in MPH)
     * @param speedEnd The ending speed value (in MPH)
     * @return The elapsed time in seconds between the specified speed points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    private double calcFATSMPH(int run, double speedStart, double speedEnd) throws Exception {
	final ArrayList<Dataset.Range> ranges = this.getRanges();
	if(run<0 || run>=ranges.size())
	    throw new Exception("FATS run " + run + " not found (available: 0-" + (ranges.size()-1) + ")");

	if(this.splines[run]==null)
	    throw new Exception("FATS run " + run + " interpolation failed - check filter settings");

	final Dataset.Range r=ranges.get(run);
	logger.trace("FATS MPH calculation: run={}, range={}-{}", run, r.start, r.end);

	double rpmPerMph = this.env.c.rpm_per_mph();

	// Convert MPH to RPM then use RPM calculation
	int rpmStart = (int) Math.round(speedStart * rpmPerMph);
	int rpmEnd = (int) Math.round(speedEnd * rpmPerMph);

	logger.trace("FATS MPH->RPM conversion: {} mph -> {} RPM, {} mph -> {} RPM",
	    speedStart, rpmStart, speedEnd, rpmEnd);

	// Use RPM calculation
	return calcFATSRPM(run, rpmStart, rpmEnd);
    }

    public double[] calcFATS(int RPMStart, int RPMEnd) {
	final ArrayList<Dataset.Range> ranges = this.getRanges();
	final double [] out = new double[ranges.size()];
	for(int i=0;i<ranges.size();i++) {
	    try {
		out[i]=calcFATS(i, RPMStart, RPMEnd);
	    } catch (final Exception e) {
	    }
	}
	return out;
    }

    public Filter getFilter() { return this.filter; }
    //public void setFilter(Filter f) { this.filter=f; }
    public Env getEnv() { return this.env; }
    //public void setEnv(Env e) { this.env=e; }
    @Override
    public boolean useId2() { return this.env.prefs.getBoolean("altnames", false); }
}
