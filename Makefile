VERSION := 0.0
RELEASE := 1.4

UNAME := $(shell uname -o)

ifeq ($(UNAME),Cygwin)
CLASSPATH = '$(shell cygpath -wsp .:$(JARS))'
PWD := $(shell cygpath -d $(shell pwd))\\
LAUNCH4J := launch4jc
SCP := pscp
INSTALL_DIR := '$(shell cygpath -u "C:\Program Files\ECUxPlot")'
else
CLASSPATH = .:$(JARS)
PWD := $(shell pwd)/
LAUNCH4J := /usr/local/launch4j/launch4j
SCP := scp
INSTALL_DIR := /usr/local/ecuxplot
endif

MP_SOURCES= HexValue.java Map.java Parser.java Parse.java \
	    ParserException.java Project.java MapData.java

LF_SOURCES= Dataset.java Units.java CSVFileFilter.java CSVRow.java

UT_SOURCES= ExitListener.java WindowUtilities.java Cursors.java \
	    WaitCursor.java MMapFile.java \
	    MenuListener.java SubActionListener.java \
	    GenericFileFilter.java Unsigned.java DoubleArray.java \
	    MovingAverageSmoothing.java

VM_SOURCES= LinearSmoothing.java SavitzkyGolaySmoothing.java

EX_SOURCES= ECUxPlot.java ECUxChartFactory.java ECUxDataset.java ECUxChartPanel.java AxisMenu.java ECUxFilter.java FileMenu.java OptionsMenu.java

LF_CLASSES=$(LF_SOURCES:%.java=org/nyet/logfile/%.class)
UT_CLASSES=$(UT_SOURCES:%.java=org/nyet/util/%.class)
VM_CLASSES=$(VM_SOURCES:%.java=vec_math/%.class)
MP_CLASSES=$(MP_SOURCES:%.java=org/nyet/mappack/%.class)

EX_CLASSES=$(EX_SOURCES:%.java=org/nyet/ecuxplot/%.class)


TARGETS=mapdump.class $(EX_CLASSES)
REFERENCE=data/4Z7907551R.kp

JARS:=jcommon-1.0.12.jar:jfreechart-1.0.9.jar:opencsv-1.8.jar:applib.jar

JFLAGS=-classpath $(CLASSPATH) -Xlint:deprecation -Xlint:unchecked -target 1.5

all: $(TARGETS) .classpath version.txt
jar: ECUxPlot-$(VERSION)r$(RELEASE).jar
zip: ECUxPlot-$(VERSION)r$(RELEASE).zip
scp: ECUxPlot-$(VERSION)r$(RELEASE).zip
	$(SCP) $< nyet.org:public_html/cars/files/

clean:
	rm -f ECUxPlot.exe ECUxPlot*.zip ECUxPlot.jar ECUxPlot-$(VERSION)r$(RELEASE).jar ECUxPlot.xml version.txt .classpath
	rm -f *.class
	find org -name \*.class | xargs rm
	find vec_math -name \*.class | xargs rm

%.csv: %.kp mapdump
	./mapdump -r $(REFERENCE) $< > $@

.classpath: Makefile
	echo "export CLASSPATH=$(CLASSPATH)" > .classpath

version.txt: Makefile
	@rm -f version.txt
	echo $(VERSION)r$(RELEASE) > $@

mapdump.class: mapdump.java $(MP_CLASSES) $(UT_CLASSES)
$(MP_CLASSES): $(LF_CLASSES) $(UT_CLASSES)
$(EX_CLASSES): $(LF_CLASSES) $(UT_CLASSES) $(VM_CLASSES)

ECUxPlot.MF: Makefile
	@echo "Manifest-Version: 1.0" > $@
	@echo "Main-Class: org.nyet.ecuxplot.ECUxPlot" >> $@
	@echo "Class-Path: $(subst :, ,$(JARS))" >> $@

ECUxPlot-$(VERSION)r$(RELEASE).jar: ECUxPlot.MF $(EX_CLASSES)
	@rm -f $@
	jar cfm $@ ECUxPlot.MF `find org -name \*.class -o -name \*.png` `find vec_math -name \*.class`

%.xml: %.xml.template Makefile
	sed -e 's/VERSION/$(VERSION)/g' < $< | sed -e 's/RELEASE/$(RELEASE)/g' > $@
ECUxPlot.exe: ECUxPlot-$(VERSION)r$(RELEASE).jar ECUxPlot.xml ECUxPlot.ico version.txt
	$(LAUNCH4J) '$(PWD)ECUxPlot.xml'


INSTALL_FILES = ECUxPlot.exe ECUxPlot-$(VERSION)r$(RELEASE).jar ECUxPlot.sh \
		$(subst :, ,$(JARS)) version.txt

ECUxPlot-$(VERSION)r$(RELEASE).zip: $(INSTALL_FILES)
	@rm -f $@
	zip $@ $(INSTALL_FILES)

install: $(INSTALL_FILES)
	mkdir -p $(INSTALL_DIR)
	cp -avp $(INSTALL_FILES) $(INSTALL_DIR)/

%.class: %.java
	javac $(JFLAGS) $<
