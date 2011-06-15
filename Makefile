VERSION := 0.9
RELEASE := 2.6
RC := -rc1
ECUXPLOT_VER := $(VERSION)r$(RELEASE)$(RC)

JCOMMON_VER := 1.0.16
JFREECHART_VER := 1.0.13
OPENCSV_VER := 1.8

PWD := $(shell pwd)
UNAME := $(shell uname -s)
JAVAC_VER := $(shell javac -version 2>&1 | sed -e 's/javac \([^.]*\.[^.]*\)\.\(.*\)/\1 \2/')
JAVAC_MAJOR_VER := $(word 1,$(JAVAC_VER))
JAVAC_MINOR_VER := $(word 2,$(JAVAC_VER))

ifeq ($(findstring CYGWIN,$(UNAME)),CYGWIN)
CLASSPATH = '$(shell cygpath -wp .:$(JARS))'

LAUNCH4J := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\Launch4j;C:\Program Files (x86)\Launch4j")' which launch4jc)'

MAKENSIS := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\NSIS;C:\Program Files (x86)\NSIS")' which makensis)'

INSTALL_DIR := '$(shell cygpath -u "C:\Program Files\ECUxPlot")'
OPT_PRE := '/'
else
CLASSPATH = .:$(JARS)
LAUNCH4J := /usr/local/launch4j/launch4j
MAKENSIS := makensis
INSTALL_DIR := /usr/local/ecuxplot
OPT_PRE := '-'
endif
RSYNC := rsync

MP_SOURCES= HexValue.java Map.java Parser.java Parse.java \
	    ParserException.java Project.java MapData.java \
	    Folder.java

LF_SOURCES= Dataset.java CSVFileFilter.java CSVRow.java

UT_SOURCES= ExitListener.java WindowUtilities.java Cursors.java \
	    WaitCursor.java MMapFile.java \
	    MenuListener.java SubActionListener.java \
	    GenericFileFilter.java Unsigned.java Signed.java DoubleArray.java \
	    MovingAverageSmoothing.java Files.java Version.java \
	    BrowserLaunch.java Strings.java Locate.java

VM_SOURCES= LinearSmoothing.java SavitzkyGolaySmoothing.java

EX_SOURCES= ECUxPlot.java ECUxChartFactory.java ECUxDataset.java \
	    ECUxChartPanel.java AboutPanel.java \
	    FATSChartFrame.java FATSDataset.java \
	    FileMenu.java OptionsMenu.java ProfileMenu.java \
	    AxisMenu.java HelpMenu.java \
	    PreferencesEditor.java Env.java Units.java \
	    Filter.java FilterEditor.java \
	    Constants.java ConstantsEditor.java \
	    PID.java PIDEditor.java \
	    Fueling.java FuelingEditor.java \
	    SAE.java SAEEditor.java \
	    Preset.java ECUxPresets.java

LF_CLASSES=$(LF_SOURCES:%.java=org/nyet/logfile/%.class)
UT_CLASSES=$(UT_SOURCES:%.java=org/nyet/util/%.class)
VM_CLASSES=$(VM_SOURCES:%.java=vec_math/%.class)
MP_CLASSES=$(MP_SOURCES:%.java=org/nyet/mappack/%.class)

EX_CLASSES=$(EX_SOURCES:%.java=org/nyet/ecuxplot/%.class)

TARGETS=mapdump.class $(EX_CLASSES)
REFERENCE=data/4Z7907551R.kp

JARS:=jcommon-$(JCOMMON_VER).jar:jfreechart-$(JFREECHART_VER).jar:opencsv-$(OPENCSV_VER).jar:applib.jar:flanagan.jar:AppleJavaExtensions.jar

JFLAGS=-classpath $(CLASSPATH) -Xlint:deprecation -Xlint:unchecked -target 1.5
TARGET=ECUxPlot-$(ECUXPLOT_VER)
INSTALLER=ECUxPlot-$(ECUXPLOT_VER)-setup.exe

ARCHIVES=$(TARGET).tar.gz $(TARGET).MacOS.tar.gz
all: $(TARGETS) .classpath version.txt jar exe

jar: $(TARGET).jar
run: jar
	java -jar $(TARGET).jar
archives: $(ARCHIVES)
exe: ECUxPlot.exe
installer: $(INSTALLER)
rsync: $(ARCHIVES) $(INSTALLER)
	$(RSYNC) $^ nyet.org:public_html/cars/files/

binclean:
	rm -f ECUxPlot*.{exe,jar,zip,tar.gz}

clean: binclean
	rm -rf build
	rm -f ECUxPlot.xml version.txt .classpath org/nyet/util/Version.java
	rm -f *.class
	find org -name \*.class | xargs rm
	find vec_math -name \*.class | xargs rm

%.csv: %.kp mapdump
	./mapdump -r $(REFERENCE) $< > $@

.classpath: Makefile
	echo "export CLASSPATH=$(CLASSPATH)" > .classpath

version.txt: Makefile
	@rm -f version.txt
	echo $(ECUXPLOT_VER) > $@

mapdump.class: mapdump.java $(MP_CLASSES) $(UT_CLASSES)
$(MP_CLASSES): $(LF_CLASSES) $(UT_CLASSES)
$(EX_CLASSES): $(LF_CLASSES) $(UT_CLASSES) $(VM_CLASSES)

PROFILES:= $(addprefix profiles/,B5S4/fueling.xml B5S4/constants.xml B8S4/constants.xml)

INSTALL_FILES:= ECUxPlot-$(ECUXPLOT_VER).jar \
		$(subst :, ,$(JARS)) version.txt README-Zeitronix.txt \
		gpl-3.0.txt flanagan-license.txt

GEN:=	sed -e 's/VERSION/$(VERSION)/g' | \
	sed -e 's/RELEASE/$(RELEASE)/g' | \
	sed -e 's/JAVAC_MAJOR_VER/$(JAVAC_MAJOR_VER)/g' | \
	sed -e 's/JAVAC_MINOR_VER/$(JAVAC_MINOR_VER)/g' | \
	sed -e 's/ECUXPLOT_VER/$(ECUXPLOT_VER)/g' | \
	sed -e 's/JFREECHART_VER/$(JFREECHART_VER)/g' | \
	sed -e 's/JCOMMON_VER/$(JCOMMON_VER)/g' | \
	sed -e 's/OPENCSV_VER/$(OPENCSV_VER)/g'

include scripts/Windows.mk
include scripts/MacOS.mk

ECUxPlot-$(ECUXPLOT_VER).tar.gz: $(INSTALL_FILES) $(PROFILES) ECUxPlot.sh
	@rm -f $@
	@rm -rf build/ECUxPlot
	mkdir -p build/ECUxPlot
	install -D -m 644 $(INSTALL_FILES) build/ECUxPlot
	install ECUxPlot.sh build/ECUxPlot
	cp -a --parents $(PROFILES) build/ECUxPlot
	(cd build; tar czvf ../$@ ECUxPlot)

install: $(INSTALL_FILES) $(PROFILES)
	mkdir -p $(INSTALL_DIR)
	rm -f $(INSTALL_DIR)/ECUxPlot*.jar
	install -D -m 644 $(INSTALL_FILES) $(INSTALL_DIR)
	install ECUxPlot.sh $(INSTALL_DIR)
	cp -a --parents $(PROFILES) $(INSTALL_DIR)
	ln -sf ../ecuxplot/ECUxPlot.sh /usr/local/bin/ecuxplot

tag:
	scripts/svn-tag $(ECUXPLOT_VER)

%.java: %.java.template Makefile
	cat $< | $(GEN) > $@

%.class: %.java
	javac $(JFLAGS) $<

.PRECIOUS: org/nyet/util/Version.java
