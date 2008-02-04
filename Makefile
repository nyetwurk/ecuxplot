UNAME := $(shell uname -o)

ifeq ($(UNAME),Cygwin)
CLASSPATH_SEP :='\;'
PWD := $(shell cygpath -d $(shell pwd))\\
LAUNCH4J := launch4jc
else
CLASSPATH_SEP :=':'
PWD := $(shell pwd)/
LAUNCH4J := /usr/local/launch4j/launch4j
endif

MP_SOURCES= HexValue.java Map.java Parser.java Parse.java \
	    ParserException.java Project.java MapData.java

LF_SOURCES= Dataset.java Units.java CSVFileFilter.java CSVRow.java

UT_SOURCES= ExitListener.java WindowUtilities.java Cursors.java \
	    WaitCursor.java MMapFile.java \
	    MenuListener.java SubActionListener.java \
	    GenericFileFilter.java Unsigned.java DoubleArray.java

EX_SOURCES= ECUxPlot.java ECUxChartFactory.java ECUxDataset.java AxisMenu.java

MP_CLASSES=$(MP_SOURCES:%.java=org/nyet/mappack/%.class)
LF_CLASSES=$(LF_SOURCES:%.java=org/nyet/logfile/%.class)
UT_CLASSES=$(UT_SOURCES:%.java=org/nyet/util/%.class)
EX_CLASSES=$(EX_SOURCES:%.java=org/nyet/ecuxplot/%.class)

TARGETS=mapdump.class $(EX_CLASSES)
REFERENCE=data/4Z7907551R.kp

CLASSPATH=jcommon-1.0.12.jar$(CLASSPATH_SEP)jfreechart-1.0.9.jar$(CLASSPATH_SEP)opencsv-1.8.jar

JFLAGS=-classpath .$(CLASSPATH_SEP)$(CLASSPATH) -Xlint:deprecation -target 1.5

all: $(TARGETS) .classpath
zip: ECUxPlot.zip
clean:
	rm ECUxPlot.exe ECUxPlot.zip ECUxPlot.jar .classpath
	rm *.class
	find org/nyet -name \*.class | xargs rm

%.csv: %.kp mapdump
	./mapdump -r $(REFERENCE) $< > $@

.classpath: Makefile
	echo export CLASSPATH='`dirname $$0`$(CLASSPATH_SEP)$(CLASSPATH)' > .classpath

mapdump.class: mapdump.java $(MP_CLASSES)
$(EX_CLASSES): $(UT_CLASSES) $(LF_CLASSES)
ECUxPlot.jar: $(EX_CLASSES)
	@rm -f $@
	jar cfe $@ org.nyet.ecuxplot.ECUxPlot `find org/nyet -name \*.class`

ECUxPlot.exe: ECUxPlot.jar ECUxPlot.xml
	$(LAUNCH4J) '$(PWD)ECUxPlot.xml'

ECUxPlot.zip: ECUxPlot.exe
	@rm -f $@
	zip $@ ECUxPlot.exe jcommon-1.0.12.jar jfreechart-1.0.9.jar opencsv-1.8.jar

%.class: %.java
	javac $(JFLAGS) $<
