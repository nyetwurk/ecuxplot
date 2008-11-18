VERSION := 0.9
RELEASE := 1.2

JCOMMON_VER := 1.0.14
JFREECHART_VER := 1.0.11
OPENCSV_VER := 1.8

UNAME := $(shell uname -o)

ifeq ($(UNAME),Cygwin)
CLASSPATH = '$(shell cygpath -wsp .:$(JARS))'
PWD := $(shell cygpath -d $(shell pwd))\\
LAUNCH4J := launch4jc
INSTALL_DIR := '$(shell cygpath -u "C:\Program Files\ECUxPlot")'
OPT_PRE := '/'
else
CLASSPATH = .:$(JARS)
PWD := $(shell pwd)/
LAUNCH4J := /usr/local/launch4j/launch4j
INSTALL_DIR := /usr/local/ecuxplot
OPT_PRE := '-'
endif
SCP := scp

MP_SOURCES= HexValue.java Map.java Parser.java Parse.java \
	    ParserException.java Project.java MapData.java

LF_SOURCES= Dataset.java CSVFileFilter.java CSVRow.java

UT_SOURCES= ExitListener.java WindowUtilities.java Cursors.java \
	    WaitCursor.java MMapFile.java \
	    MenuListener.java SubActionListener.java \
	    GenericFileFilter.java Unsigned.java DoubleArray.java \
	    MovingAverageSmoothing.java Files.java Version.java

VM_SOURCES= LinearSmoothing.java SavitzkyGolaySmoothing.java

EX_SOURCES= ECUxPlot.java ECUxChartFactory.java ECUxDataset.java \
	    ECUxChartPanel.java \
	    HelpMenu.java AxisMenu.java FileMenu.java OptionsMenu.java \
	    PreferencesEditor.java Env.java Units.java \
	    Filter.java FilterEditor.java \
	    Constants.java ConstantsEditor.java \
	    PID.java PIDEditor.java \
	    Fueling.java FuelingEditor.java \
	    SAE.java SAEEditor.java

LF_CLASSES=$(LF_SOURCES:%.java=org/nyet/logfile/%.class)
UT_CLASSES=$(UT_SOURCES:%.java=org/nyet/util/%.class)
VM_CLASSES=$(VM_SOURCES:%.java=vec_math/%.class)
MP_CLASSES=$(MP_SOURCES:%.java=org/nyet/mappack/%.class)

EX_CLASSES=$(EX_SOURCES:%.java=org/nyet/ecuxplot/%.class)

TARGETS=mapdump.class $(EX_CLASSES)
REFERENCE=data/4Z7907551R.kp

JARFILES:=jcommon-$(JCOMMON_VER).jar jfreechart-$(JFREECHART_VER).jar opencsv-$(OPENCSV_VER).jar applib.jar AppleJavaExtensions.jar
JARS:=jcommon-$(JCOMMON_VER).jar:jfreechart-$(JFREECHART_VER).jar:opencsv-$(OPENCSV_VER).jar:applib.jar:AppleJavaExtensions.jar

JFLAGS=-classpath $(CLASSPATH) -Xlint:deprecation -Xlint:unchecked # -target 1.5
TARGET=ECUxPlot-$(VERSION)r$(RELEASE)
INSTALLER=ECUxPlot-$(VERSION)r$(RELEASE)-setup.exe

ZIPS=$(TARGET).zip $(TARGET).MacOS.zip
all: $(TARGETS) .classpath version.txt jar exe

jar: $(TARGET).jar
zip: $(ZIPS)
exe: ECUxPlot.exe
installer: $(INSTALLER)
scp: $(ZIPS) $(INSTALLER)
	$(SCP) $^ nyet.org:public_html/cars/files/

clean:
	rm -f ECUxPlot.exe ECUxPlot*.zip ECUxPlot.jar ECUxPlot-$(VERSION)r$(RELEASE).jar ECUxPlot.xml version.txt .classpath
	rm -f *.class
	rm -rf ECUxPlot.app
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

include scripts/Windows.mk
include scripts/MacOS.mk

INSTALL_FILES = ECUxPlot.exe ECUxPlot-$(VERSION)r$(RELEASE).jar ECUxPlot.sh \
		$(subst :, ,$(JARS)) version.txt

ECUxPlot-$(VERSION)r$(RELEASE).zip: $(INSTALL_FILES)
	@rm -f $@
	zip $@ $(INSTALL_FILES)

install: $(INSTALL_FILES)
	mkdir -p $(INSTALL_DIR)
	cp -avp $(INSTALL_FILES) $(INSTALL_DIR)/

$(INSTALLER): $(INSTALL_FILES) ECUxPlot.nsi
	makensis \
	    $(OPT_PRE)DVERSION=$(VERSION)r$(RELEASE) \
	    $(OPT_PRE)DJFREECHART_VER=$(JFREECHART_VER) \
	    $(OPT_PRE)DJCOMMON_VER=$(JCOMMON_VER) \
	    $(OPT_PRE)DOPENCSV_VER=$(OPENCSV_VER) \
	    ECUxPlot.nsi
	chmod +x $(INSTALLER)

tag: version.txt
	scripts/svn-tag `cat version.txt`

%.java: %.java.template Makefile
	sed -e 's/VERSION/$(VERSION)/g' < $< | \
	sed -e 's/RELEASE/$(RELEASE)/g' | \
	sed -e 's/JFREECHART_VER/$(JFREECHART_VER)/g' | \
	sed -e 's/JCOMMON_VER/$(JCOMMON_VER)/g' | \
	sed -e 's/OPENCSV_VER/$(OPENCSV_VER)/g' \
	> $@

%.class: %.java
	javac $(JFLAGS) $<
