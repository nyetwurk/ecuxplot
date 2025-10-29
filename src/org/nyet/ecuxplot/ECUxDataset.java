package org.nyet.ecuxplot;

import java.io.PrintStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    private double time_ticks_per_sec;  // ECUx has time in ms, JB4 in 1/10s
    private double samples_per_sec=0;
    private CubicSpline [] splines;     // rpm vs time splines
    private String log_detected;
    // Track which columns need moving average smoothing
    // Maps column name to smoothing window size (in samples)
    private final Map<String, Integer> smoothingWindows = new HashMap<>();

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




    public String getLogDetected() {
        return this.log_detected;
    }

    public ECUxDataset(String filename, Env env, Filter filter, int verbose)
            throws Exception {
        super(filename, verbose);

        this.env = env;
        this.filter = filter;

        // Debug logging to understand filter state
        if (filter == null) {
            logger.debug("ECUxDataset constructor: filter is null");
        } else {
            logger.debug("ECUxDataset constructor: filter is not null, enabled={}", filter.enabled());
        }

        // Get pedal, throttle, and gear columns using field preferences
        this.pedal = get(DataLogger.pedalField());
        this.throttle = get(DataLogger.throttleField());
        this.gear = get(DataLogger.gearField());

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
        // Note: buildRanges() is called by parent constructor, but we need to call it again
        // after filter is assigned to ensure proper spline creation
        buildRanges(); // regenerate ranges, splines
    }

    private int MAW() {
        // HPTQMAW is in seconds, convert to samples
        return (int)Math.floor(this.samples_per_sec * this.filter.HPTQMAW());
    }

    private int AccelMAW() {
        // accelMAW is in seconds, convert to samples
        return (int)Math.floor(this.samples_per_sec * this.filter.accelMAW());
    }


    @Override
    public void ParseHeaders(CSVReader reader, int verbose) throws Exception {
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

        // Process all headers using the logger configuration
        DataLogger.HeaderData h = config.processHeaders(reader, verbose);
        if (h == null) {
            logger.error("Failed to process headers for {}", logType);
            return;
        }

        // Create DatasetId objects
        this.setIds(h, config);
    }

    private DoubleArray drag (DoubleArray v) {
        final DoubleArray windDrag = v.pow(3).mult(0.5 * UnitConstants.AIR_DENSITY_STANDARD * this.env.c.Cd() *
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

    @Override
    public Column get(Comparable<?> id) {
        try {
            return _get(id);
        } catch (final NullPointerException e) {
            return null;
        }
    }

    private Column _get(Comparable<?> id) {
        // ========== GENERIC UNIT CONVERSION HANDLER ==========
        // This handler parses "FieldName (unit)" pattern from menu items (e.g., "VehicleSpeed (mph)"),
        // retrieves the base field (e.g., "VehicleSpeed"), performs the unit conversion, and returns
        // a new Column with the converted data. This eliminates the need for individual handlers for
        // each unit-converted field (previously there were 16+ hardcoded conversion handlers).
        //
        // Flow:
        // 1. parseUnitConversion() extracts base field and target unit
        // 2. Retrieve base field from dataset
        // 3. convertUnits() performs the actual conversion math
        // 4. Returns new Column with converted data
        Units.ParsedUnitConversion parsed = Units.parseUnitConversion(id.toString());
        if (parsed != null) {
            Column baseColumn = super.get(parsed.baseField);
            if (baseColumn != null) {
                return convertUnits(baseColumn, parsed.targetUnit);
            }
        }

        Column c = null;

        // ========== BASIC FIELDS ==========
        if(id.equals("Sample")) {
            final double[] idx = new double[this.length()];
            for (int i=0;i<this.length();i++)
                idx[i]=i;
            final DoubleArray a = new DoubleArray(idx);
            c = new Column("Sample", "#", a);
        } else if(id.equals("TIME")) {
            final DoubleArray a = super.get("TIME").data;
            c = new Column("TIME", UnitConstants.UNIT_SECONDS, a.div(this.time_ticks_per_sec));
        } else if(id.equals("RPM")) {
            // smooth sampling quantum noise/jitter, RPM is an integer!
            if (this.samples_per_sec>10) {
                final DoubleArray a = super.get("RPM").data.smooth();
                c = new Column(id, UnitConstants.UNIT_RPM, a);
            }
        } else if(id.equals("RPM - raw")) {
            c = new Column(id, UnitConstants.UNIT_RPM, super.get("RPM").data);

        // ========== CALCULATED MAF & FUEL FIELDS ==========
        } else if(id.equals("Sim Load")) {
            // g/sec to kg/hr
            final DoubleArray a = super.get("MassAirFlow").data.mult(UnitConstants.GPS_PER_KGH);
            final DoubleArray b = super.get("RPM").data.smooth();

            // KUMSRL
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.div(b).div(.001072));
        } else if(id.equals("Sim Load Corrected")) {
            // g/sec to kg/hr
            final DoubleArray a = this.get("Sim MAF").data.mult(UnitConstants.GPS_PER_KGH);
            final DoubleArray b = this.get("RPM").data;

            // KUMSRL
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.div(b).div(.001072));
        } else if(id.equals("Sim MAF")) {
            // mass in g/sec
            final DoubleArray a = super.get("MassAirFlow").data.
                mult(this.env.f.MAF_correction()).add(this.env.f.MAF_offset());
            c = new Column(id, UnitConstants.UNIT_GPS, a);
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
        } else if(id.equals("Sim Fuel Mass")) { // based on te
            final double gps = this.env.f.injector()*UnitConstants.GPS_PER_CCMIN;
            final double cylinders = this.env.f.cylinders();
            final Column bank1 = this.get("EffInjectorDutyCycle");
            final Column bank2 = this.get("EffInjectorDutyCycleBank2");
            DoubleArray duty = bank1.data;
            /* average two duties for overall mass */
            if (bank2!=null) duty = duty.add(bank2.data).div(2);
            final DoubleArray a = duty.mult(cylinders*gps/100);
            c = new Column(id, "g/sec", a);

        // ========== CALCULATED AIR-FUEL RATIO FIELDS ==========
        // Note: AFR conversions (lambda to AFR) are now handled by generic unit conversion handler
        } else if(id.equals("Sim AFR")) {
            final DoubleArray a = this.get("Sim MAF").data;
            final DoubleArray b = this.get("Sim Fuel Mass").data;
            c = new Column(id, UnitConstants.UNIT_AFR, a.div(b));
        } else if(id.equals("Sim lambda")) {
            final DoubleArray a = this.get("Sim AFR").data.div(UnitConstants.STOICHIOMETRIC_AFR);
            c = new Column(id, UnitConstants.UNIT_LAMBDA, a);
        } else if(id.equals("Sim lambda error")) {
            final DoubleArray a = super.get("AirFuelRatioDesired").data;
            final DoubleArray b = this.get("Sim lambda").data;
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.div(b).mult(-1).add(1).mult(100).
                max(-25).min(25));

        } else if(id.equals("FuelInjectorDutyCycle")) {
            final DoubleArray a = super.get("FuelInjectorOnTime").data. /* ti */
                div(60*1000);   /* assumes injector on time is in ms */

            final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %
        } else if(id.equals("EffInjectorDutyCycle")) {          /* te */
            final DoubleArray a = super.get("EffInjectionTime").data.
                div(60*1000);   /* assumes injector on time is in ms */

            final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %
        } else if(id.equals("EffInjectorDutyCycleBank2")) {             /* te */
            final DoubleArray a = super.get("EffInjectionTimeBank2").data.
                div(60*1000);   /* assumes injector on time is in ms */

            final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %

        // ========== SPECIAL HANDLERS: ENGINE TORQUE/HP ==========
        // if log contains Engine torque / converts TorqueDesired (Nm) to ft-lb and calculates HP
        // See MenuHandlerRegistry.REGISTRY["Engine torque (ft-lb)"], etc.
        } else if(id.equals("Engine torque (ft-lb)")) {
            final DoubleArray tq = this.get("TorqueDesired").data;
            final DoubleArray value = tq.mult(UnitConstants.NM_PER_FTLB);       // nm to ft-lb
            c = new Column(id, UnitConstants.UNIT_FTLB, value);
        } else if(id.equals("Engine HP")) {
            final DoubleArray tq = this.get("Engine torque (ft-lb)").data;
            final DoubleArray rpm = this.get("RPM").data;
            final DoubleArray value = tq.div(UnitConstants.HP_CALCULATION_FACTOR).mult(rpm);
            c = new Column(id, UnitConstants.UNIT_HP, value);

        // ========== CALCULATED FIELDS: VELOCITY & ACCELERATION ==========
        // Calc Velocity, Acceleration (RPM/s), Acceleration (m/s^2), Acceleration (g)
        // See MenuHandlerRegistry.REGISTRY["Calc Velocity"], etc.
        } else if(id.equals("Calc Velocity")) {
            // Calculate vehicle speed from RPM and gear ratio (more accurate than VehicleSpeed sensor)
            // Uses user-specified rpm_per_mph for calibration
            final DoubleArray rpm = this.get("RPM").data;
            c = new Column(id, UnitConstants.UNIT_MPS, rpm.div(this.env.c.rpm_per_mph()).
                mult(UnitConstants.MPS_PER_MPH));
        } else if(id.equals("Acceleration (RPM/s)")) {
            final DoubleArray y = this.get("RPM").data;
            final DoubleArray x = this.get("TIME").data;
            c = new Column(id, UnitConstants.UNIT_RPS, y.derivative(x, this.AccelMAW()).max(0));
        } else if(id.equals("Acceleration - raw (RPM/s)")) {
            final DoubleArray y = this.get("RPM - raw").data;
            final DoubleArray x = this.get("TIME").data;
            c = new Column(id, UnitConstants.UNIT_RPS, y.derivative(x));
        } else if(id.equals("Acceleration (m/s^2)")) {
            final DoubleArray y = this.get("Calc Velocity").data;
            final DoubleArray x = this.get("TIME").data;
            final DoubleArray derivative = y.derivative(x, this.MAW()).max(0);
            // Store unsmoothed data and record smoothing requirement
            c = new Column(id, "m/s^2", derivative);
            this.smoothingWindows.put(id.toString(), this.MAW());
        } else if(id.equals("Acceleration (g)")) {
            final DoubleArray a = this.get("Acceleration (m/s^2)").data;
            c = new Column(id, UnitConstants.UNIT_G, a.div(UnitConstants.STANDARD_GRAVITY));

        // ========== CALCULATED FIELDS: POWER ==========
        // WHP, WTQ, HP, TQ, Drag
        // See MenuHandlerRegistry.REGISTRY["WHP"], etc.
        } else if(id.equals("WHP")) {
            final DoubleArray a = this.get("Acceleration (m/s^2)").data;
            final DoubleArray v = this.get("Calc Velocity").data;
            final DoubleArray whp = a.mult(v).mult(this.env.c.mass()).
                add(this.drag(v));      // in watts

            DoubleArray value = whp.mult(1.0 / UnitConstants.HP_PER_WATT);
            String l = UnitConstants.UNIT_HP;
            if(this.env.sae.enabled()) {
                value = value.mult(this.env.sae.correction());
                l += " (SAE)";
            }
            // Store unsmoothed data and record smoothing requirement
            c = new Column(id, l, value);
            this.smoothingWindows.put(id.toString(), this.MAW());
        } else if(id.equals("HP")) {
            final DoubleArray whp = this.get("WHP").data;
            final DoubleArray value = whp.div((1-this.env.c.driveline_loss())).
                    add(this.env.c.static_loss());
            String l = UnitConstants.UNIT_HP;
            if(this.env.sae.enabled()) l += " (SAE)";
            c = new Column(id, l, value);
        } else if(id.equals("WTQ")) {
            final DoubleArray whp = this.get("WHP").data;
            final DoubleArray rpm = this.get("RPM").data;
            final DoubleArray value = whp.mult(UnitConstants.HP_CALCULATION_FACTOR).div(rpm);
            String l = UnitConstants.UNIT_FTLB;
            if(this.env.sae.enabled()) l += " (SAE)";
            c = new Column(id, l, value);
        } else if(id.toString().equals(idWithUnit("WTQ", UnitConstants.UNIT_NM))) {
            final DoubleArray wtq = this.get("WTQ").data;
            final DoubleArray value = wtq.mult(UnitConstants.FTLB_PER_NM); // ft-lb to Nm
            String l = UnitConstants.UNIT_NM;
            if(this.env.sae.enabled()) l += " (SAE)";
            c = new Column(id, l, value);
        } else if(id.equals("TQ")) {
            final DoubleArray hp = this.get("HP").data;
            final DoubleArray rpm = this.get("RPM").data;
            final DoubleArray value = hp.mult(UnitConstants.HP_CALCULATION_FACTOR).div(rpm);
            String l = UnitConstants.UNIT_FTLB;
            if(this.env.sae.enabled()) l += " (SAE)";
            c = new Column(id, l, value);
        } else if(id.toString().equals(idWithUnit("TQ", UnitConstants.UNIT_NM))) {
            final DoubleArray tq = this.get("TQ").data;
            final DoubleArray value = tq.mult(UnitConstants.FTLB_PER_NM); // ft-lb to Nm
            String l = UnitConstants.UNIT_NM;
            if(this.env.sae.enabled()) l += " (SAE)";
            c = new Column(id, l, value);
        } else if(id.equals("Drag")) {
            final DoubleArray v = this.get("Calc Velocity").data;
            final DoubleArray drag = this.drag(v);
            c = new Column(id, "HP", drag.mult(1.0 / UnitConstants.HP_PER_WATT));

        // ========== BOOST PRESSURE & ZEITRONIX HANDLERS ==========
        // BoostPressureDesired, Zeitronix Boost, Zeitronix AFR, Zeitronix Lambda
        // See MenuHandlerRegistry.REGISTRY["Zeitronix Boost (PSI)"], etc.
        } else if(id.equals("BoostPressureDesired")) {
            final Column delta = super.get("BoostPressureDesiredDelta");
            if (delta != null) {
                final Column ecu = super.get("ECUBoostPressureDesired");
                if (ecu != null) {
                    c = new Column(id, UnitConstants.UNIT_PSI, ecu.data.add(delta.data));
                }
            }
        } else if(id.toString().equals(idWithUnit("Zeitronix Boost", UnitConstants.UNIT_PSI))) {
            final DoubleArray boost = super.get("Zeitronix Boost").data;
            // Store unsmoothed data and record smoothing requirement
            c = new Column(id, UnitConstants.UNIT_PSI, boost);
            // Convert seconds to samples
            int smoothingWindow = (int)Math.floor(this.samples_per_sec * this.filter.ZeitMAW());
            this.smoothingWindows.put(id.toString(), smoothingWindow);
        } else if(id.equals("Zeitronix Boost")) {
            final DoubleArray boost = this.get(idWithUnit("Zeitronix Boost", UnitConstants.UNIT_PSI)).data;
            c = new Column(id, UnitConstants.UNIT_MBAR, boost.mult(UnitConstants.MBAR_PER_PSI).add(UnitConstants.MBAR_PER_ATM));
        } else if(id.toString().equals(idWithUnit("Zeitronix AFR", UnitConstants.UNIT_LAMBDA))) {
            final DoubleArray abs = super.get("Zeitronix AFR").data;
            c = new Column(id, UnitConstants.UNIT_LAMBDA, abs.div(UnitConstants.STOICHIOMETRIC_AFR));
        } else if(id.toString().equals(idWithUnit("Zeitronix Lambda", UnitConstants.UNIT_AFR))) {
            final DoubleArray abs = super.get("Zeitronix Lambda").data;
            c = new Column(id, UnitConstants.UNIT_AFR, abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
        } else if(id.equals("BoostDesired PR")) {
            final Column act = super.get("BoostPressureDesired");
            try {
                final DoubleArray ambient = super.get("BaroPressure").data;
                c = new Column(id, "PR", act.data.div(ambient));
            } catch (final Exception e) {
                if (act.getUnits().matches(UnitConstants.UNIT_PSI))
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
                if (act.getUnits().matches(UnitConstants.UNIT_PSI))
                    c = new Column(id, "PR", act.data.div(UnitConstants.STOICHIOMETRIC_AFR));
                else
                    c = new Column(id, "PR", act.data.div(UnitConstants.MBAR_PER_ATM));
            }
        } else if(id.equals("Sim evtmod")) {
            final DoubleArray tans = this.get(idWithUnit("IntakeAirTemperature", UnitConstants.UNIT_CELSIUS)).data;
            DoubleArray tmot = tans.ident(95);
            try {
                tmot = this.get("CoolantTemperature").data;
            } catch (final Exception e) {}

            // KFFWTBR=0.02
            // evtmod = tans + (tmot-tans)*KFFWTBR
            final DoubleArray evtmod = tans.add((tmot.sub(tans)).mult(0.02));
            c = new Column(id, "\u00B0C", evtmod);
        } else if(id.equals("Sim ftbr")) {
            final DoubleArray tans = this.get(idWithUnit("IntakeAirTemperature", UnitConstants.UNIT_CELSIUS)).data;
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
                ambient = super.get("BaroPressure").data;
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
                load = load.max(0);     // rlfgs
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
            boost = boost.div(1.016);   // pssol

            // vplsspls from KFVPDKSD/KFVPDKSDSE
            boost = boost.div(1.016);   // plsol

            c = new Column(id, UnitConstants.UNIT_MBAR, boost.max(ambient));
        } else if(id.equals("Boost Spool Rate (RPM)")) {
            final DoubleArray abs = super.get("BoostPressureActual").data.smooth();
            final DoubleArray rpm = this.get("RPM").data;
            c = new Column(id, "mBar/RPM", abs.derivative(rpm).max(0));
        } else if(id.equals("Boost Spool Rate Zeit (RPM)")) {
            final DoubleArray boost = this.get("Zeitronix Boost").data.smooth();
            final DoubleArray rpm =
                this.get("RPM").data; // Remove movingAverage - apply per-range
            c = new Column(id, "mBar/RPM", boost.derivative(rpm).max(0));
            // Note: RPM smoothing handled in getData(), but we need to mark RPM column
            // This is complex because RPM is used in derivative - needs special handling
        } else if(id.equals("Boost Spool Rate (time)")) {
            final DoubleArray abs = this.get(idWithUnit("BoostPressureActual", UnitConstants.UNIT_PSI)).data.smooth();
            final DoubleArray time = this.get("TIME").data;
            final DoubleArray derivative = abs.derivative(time, this.MAW()).max(0);
            // Store unsmoothed data and record smoothing requirement
            c = new Column(id, "PSI/sec", derivative);
            this.smoothingWindows.put(id.toString(), this.MAW());
        } else if(id.equals("ps_w error")) {
            final DoubleArray abs = super.get("BoostPressureActual").data.max(900);
            final DoubleArray ps_w = super.get("ME7L ps_w").data.max(900);
            //c = new Column(id, "%", abs.div(ps_w).sub(1).mult(-100));
            c = new Column(id, UnitConstants.UNIT_LAMBDA, ps_w.div(abs));
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
    public double[] getData(Comparable<?> id, Range r) {
        // If range is null, use full dataset
        if (r == null) {
            if (this.length() == 0) {
                return null;
            }
            r = new Range(0, this.length() - 1);
        }
        final Column c = this.get(id);
        if (c==null) return null;

        // Check if this column needs range-aware smoothing
        final String columnName = id.toString();
        Integer smoothingWindow = this.smoothingWindows.get(columnName);

        if (smoothingWindow != null && smoothingWindow > 0) {
            // Apply moving average smoothing using only data within the range
            // This prevents interference from filtered data outside the range
            logger.trace("Applying range-aware smoothing to '{}' with window {} over range {}",
                columnName, smoothingWindow, r);

            // Extract the raw data for this range
            final double[] rawData = c.data.toArray(r.start, r.end);

            // Apply smoothing using a window that doesn't extend beyond range bounds
            final org.nyet.util.MovingAverageSmoothing s = new org.nyet.util.MovingAverageSmoothing(smoothingWindow);
            final double[] smoothed = s.smoothAll(rawData, -s.getNk(), rawData.length + s.getNk() - 1);

            // Return the smoothed data within range bounds
            final double[] result = new double[rawData.length];
            System.arraycopy(smoothed, 0, result, 0, rawData.length);
            return result;
        }

        // No smoothing needed, return raw data
        return c.data.toArray(r.start, r.end);
    }

    @Override
    public void buildRanges() {
        super.buildRanges();

        // Handle filter null case (timing issue during construction)
        if (this.filter == null) {
            logger.trace("Spline creation disabled: filter is null (called from parent constructor before filter assignment)");
            this.splines = new CubicSpline[0];
            return;
        }

        // Handle filter disabled case (user has explicitly disabled the filter)
        if (!this.filter.enabled()) {
            logger.debug("Spline creation disabled: filter is explicitly disabled by user");
            this.splines = new CubicSpline[0];
            return;
        }

        final ArrayList<Dataset.Range> ranges = this.getRanges();
        this.splines = new CubicSpline[ranges.size()];
        if (ranges.size() > 0) {
            logger.debug("Creating {} splines for {} ranges (filter enabled)", ranges.size(), ranges.size());
        } else {
            logger.trace("No valid ranges found for spline creation (filter enabled but no data passes filter criteria)");
        }
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
                original = nullStdout();        // hack to disable junk that CubicSpline prints
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
     * Calculate FATS (For the Advancement of the S4) for a specific run
     *
     * This is the unified method that handles all speed units (RPM, MPH, KPH).
     * It converts speed values to RPM if needed, then performs the FATS calculation.
     *
     * @param run The run number (0-based index into the ranges array)
     * @param speedStart The starting speed value (in RPM, MPH, or KPH)
     * @param speedEnd The ending speed value (in RPM, MPH, or KPH)
     * @param speedUnit The unit of the speed values (RPM, MPH, or KPH)
     * @return The elapsed time in seconds between the specified speed points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    public double calcFATS(int run, double speedStart, double speedEnd, FATS.SpeedUnit speedUnit) throws Exception {
        if (this.filter == null) {
            logger.warn("FATS calculation disabled: filter is null");
            throw new Exception("FATS calculation requires filter to be initialized");
        }

        if (!this.filter.enabled()) {
            logger.warn("FATS calculation disabled: filter is explicitly disabled by user");
            throw new Exception("FATS calculation requires filter to be enabled");
        }

        final ArrayList<Dataset.Range> ranges = this.getRanges();
        if(run<0 || run>=ranges.size())
            throw new Exception("FATS run " + run + " not found (available: 0-" + (ranges.size()-1) + ")");

        if(this.splines[run]==null)
            throw new Exception("FATS run " + run + " interpolation failed - check filter settings");

        final Dataset.Range r=ranges.get(run);
        logger.trace("FATS {} calculation: run={}, range={}-{}", speedUnit.getDisplayName(), run, r.start, r.end);

        int rpmStart, rpmEnd;
        switch (speedUnit) {
            case RPM:
                // Direct RPM values
                rpmStart = (int) Math.round(speedStart);
                rpmEnd = (int) Math.round(speedEnd);
                logger.trace("FATS RPM calculation: {} RPM -> {} RPM", rpmStart, rpmEnd);
                break;
            case mph:
                // Convert MPH to RPM
                double rpmPerMph = this.env.c.rpm_per_mph();
                rpmStart = (int) Math.round(speedStart * rpmPerMph);
                rpmEnd = (int) Math.round(speedEnd * rpmPerMph);
                logger.trace("FATS MPH->RPM conversion: {} mph -> {} RPM, {} mph -> {} RPM",
                    speedStart, rpmStart, speedEnd, rpmEnd);
                break;
            case kmh:
                // Convert KPH to RPM
                double rpmPerKph = this.env.c.rpm_per_kph();
                rpmStart = (int) Math.round(speedStart * rpmPerKph);
                rpmEnd = (int) Math.round(speedEnd * rpmPerKph);
                logger.trace("FATS KPH->RPM conversion: {} kph -> {} RPM, {} kph -> {} RPM",
                    speedStart, rpmStart, speedEnd, rpmEnd);
                break;
            default:
                throw new IllegalArgumentException("Unsupported speed unit: " + speedUnit);
        }

        // Use RPM calculation
        return calcFATSRPM(run, rpmStart, rpmEnd);
    }

    /**
     * Calculate FATS (For the Advancement of the S4) for a specific run using RPM values
     *
     * @param run The run number (0-based index into the ranges array)
     * @param RPMStart The starting RPM value for the calculation
     * @param RPMEnd The ending RPM value for the calculation
     * @return The elapsed time in seconds between the specified RPM points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    public double calcFATS(int run, int RPMStart, int RPMEnd) throws Exception {
        if (this.filter == null) {
            logger.warn("FATS calculation disabled: filter is null");
            throw new Exception("FATS calculation requires filter to be initialized");
        }

        if (!this.filter.enabled()) {
            logger.warn("FATS calculation disabled: filter is explicitly disabled by user");
            throw new Exception("FATS calculation requires filter to be enabled");
        }
        return calcFATS(run, (double)RPMStart, (double)RPMEnd, FATS.SpeedUnit.RPM);
    }

    /**
     * Calculate FATS (For the Advancement of the S4) for a specific run using speed values
     *
     * @param run The run number (0-based index into the ranges array)
     * @param speedStart The starting speed value (in MPH or KPH)
     * @param speedEnd The ending speed value (in MPH or KPH)
     * @param speedUnit The unit of the speed values (MPH or KPH)
     * @return The elapsed time in seconds between the specified speed points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    public double calcFATSBySpeed(int run, double speedStart, double speedEnd, FATS.SpeedUnit speedUnit) throws Exception {
        if (this.filter == null) {
            logger.warn("FATS calculation disabled: filter is null");
            throw new Exception("FATS calculation requires filter to be initialized");
        }

        if (!this.filter.enabled()) {
            logger.warn("FATS calculation disabled: filter is explicitly disabled by user");
            throw new Exception("FATS calculation requires filter to be enabled");
        }
        return calcFATS(run, speedStart, speedEnd, speedUnit);
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
        if (this.filter == null) {
            logger.warn("FATS calculation disabled: filter is null");
            throw new Exception("FATS calculation requires filter to be initialized");
        }

        if (!this.filter.enabled()) {
            logger.warn("FATS calculation disabled: filter is explicitly disabled by user");
            throw new Exception("FATS calculation requires filter to be enabled");
        }

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

    /**
     * Generic unit conversion method.
     * Converts data from one unit to another using conversion factors from UnitConstants.
     *
     * Supported conversions:
     * - Air-Fuel Ratio: lambda ↔ AFR (stoichiometric factor)
     * - Temperature: Celsius ↔ Fahrenheit (temperature conversion)
     * - Pressure: mBar ↔ PSI (with ambient pressure handling)
     * - Speed: mph ↔ km/h (speed conversion)
     * - Mass flow: g/sec ↔ kg/hr (mass flow conversion)
     * - Torque: ft-lb ↔ Nm (torque conversion)
     *
     * @param baseColumn The base column to convert from
     * @param targetUnit The target unit to convert to (from UnitConstants)
     * @return A new Column with converted data, or the base column if no conversion needed
     */
    private Column convertUnits(Column baseColumn, String targetUnit) {
        String baseUnit = baseColumn.getUnits();
        DoubleArray data = baseColumn.data;
        String newUnit = targetUnit;

        // Air-Fuel Ratio: lambda <-> AFR
        if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_AFR, UnitConstants.UNIT_LAMBDA)) {
            data = data.mult(UnitConstants.STOICHIOMETRIC_AFR);
            newUnit = UnitConstants.UNIT_AFR;
        } else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_LAMBDA, UnitConstants.UNIT_AFR)) {
            data = data.mult(UnitConstants.LAMBDA_PER_AFR);
            newUnit = UnitConstants.UNIT_LAMBDA;
        }

        // Temperature: Celsius <-> Fahrenheit
        else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_FAHRENHEIT, UnitConstants.UNIT_CELSIUS)) {
            data = toFahrenheit(data);
            newUnit = UnitConstants.UNIT_FAHRENHEIT;
        } else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_CELSIUS, UnitConstants.UNIT_FAHRENHEIT)) {
            data = toCelcius(data);
            newUnit = UnitConstants.UNIT_CELSIUS;
        }

        // Pressure: mBar <-> PSI
        // Note: PSI is typically gauge pressure, mBar is absolute pressure
        else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_PSI, UnitConstants.UNIT_MBAR)) {
            data = toPSI(data);
            newUnit = UnitConstants.UNIT_PSI;
        } else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_MBAR, UnitConstants.UNIT_PSI)) {
            // PSI gauge to mBar absolute
            // Get ambient pressure from dataset or use standard
            double ambient = UnitConstants.MBAR_PER_ATM; // 1013 mBar standard
            try {
                Column baro = super.get("BaroPressure");
                if (baro != null && baro.data != null) {
                    ambient = baro.data.get(0); // Use first value as ambient
                }
            } catch (Exception e) {
                // Use standard atmospheric pressure
            }
            data = data.mult(UnitConstants.MBAR_PER_PSI).add(ambient);
            newUnit = UnitConstants.UNIT_MBAR;
        }

        // Speed: mph <-> km/h
        else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_MPH, UnitConstants.UNIT_KMH)) {
            data = data.mult(UnitConstants.MPH_PER_KPH);
            newUnit = UnitConstants.UNIT_MPH;
        } else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_KMH, UnitConstants.UNIT_MPH)) {
            data = data.mult(UnitConstants.KMH_PER_MPH);
            newUnit = UnitConstants.UNIT_KMH;
        }

        // Mass flow: g/sec <-> kg/hr
        else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_KGH, UnitConstants.UNIT_GPS)) {
            data = data.mult(UnitConstants.KGH_PER_GPS);
            newUnit = UnitConstants.UNIT_KGH;
        } else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_GPS, UnitConstants.UNIT_KGH)) {
            data = data.mult(UnitConstants.GPS_PER_KGH);
            newUnit = UnitConstants.UNIT_GPS;
        }

        // Torque: ft-lb <-> Nm
        else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_NM, UnitConstants.UNIT_FTLB)) {
            data = data.mult(UnitConstants.FTLB_PER_NM);
            newUnit = UnitConstants.UNIT_NM;
        } else if (tryConversion(targetUnit, baseUnit, UnitConstants.UNIT_FTLB, UnitConstants.UNIT_NM)) {
            data = data.mult(UnitConstants.NM_PER_FTLB);
            newUnit = UnitConstants.UNIT_FTLB;
        }

        // If no conversion matched, return base column as-is
        // (This handles cases where units are already correct or not in our conversion list)
        if (data == baseColumn.data) {
            return baseColumn;
        }

        String baseId = baseColumn.getId();
        return new Column(baseId, newUnit, data);
    }

    /**
     * Helper method to construct a unit-converted column ID.
     * @param originalId The original column ID (e.g., "IntakeAirTemperature")
     * @param unit The target unit constant (e.g., UnitConstants.UNIT_CELSIUS)
     * @return Formatted string like "IntakeAirTemperature (°C)"
     */
    private static String idWithUnit(String originalId, String unit) {
        return String.format("%s (%s)", originalId, unit);
    }

    /**
     * Helper method to check if a unit conversion should be applied.
     */
    private static boolean tryConversion(String targetUnit, String baseUnit, String expectedTarget, String expectedBase) {
        return expectedTarget.equals(targetUnit) && expectedBase.equals(baseUnit);
    }

}

// vim: set sw=4 ts=8 expandtab:
