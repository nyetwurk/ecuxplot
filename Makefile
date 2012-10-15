VERSION := 0.9
RELEASE := 4.3
RC := -rc1
ECUXPLOT_VER := $(VERSION)r$(RELEASE)$(RC)

JCOMMON_VER := 1.0.17
JFREECHART_VER := 1.0.14
OPENCSV_VER := 2.3
COMMONS_LANG3_VER := 3.1

PWD := $(shell pwd)
UNAME := $(shell uname -s)
JAVAC_VER := $(shell javac -version 2>&1 | sed -e 's/javac \([^.]*\.[^.]*\)\.\(.*\)/\1 \2/')
JAVAC_MAJOR_VER := $(word 1,$(JAVAC_VER))
JAVAC_MINOR_VER := $(word 2,$(JAVAC_VER))

space:=
space+=
ifeq ($(findstring CYGWIN,$(UNAME)),CYGWIN)
LAUNCH4J := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\Launch4j;C:\Program Files (x86)\Launch4j")' which launch4jc)'
ECUXPLOT_XML := '$(shell cygpath -w $(PWD)/build/ECUxPlot.xml)'
MAPDUMP_XML := '$(shell cygpath -w $(PWD)/build/mapdump.xml)'

MAKENSIS := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\NSIS;C:\Program Files (x86)\NSIS")' which makensis)'

INSTALL_DIR := '$(shell cygpath -u "C:\Program Files\ECUxPlot")'
OPT_PRE := '/'
else
LAUNCH4J := /usr/local/launch4j/launch4j
ECUXPLOT_XML := $(PWD)/build/ECUxPlot.xml
MAPDUMP_XML := $(PWD)/build/mapdump.xml
MAKENSIS := makensis
INSTALL_DIR := /usr/local/ecuxplot
OPT_PRE := '-'
endif
RSYNC := rsync

REFERENCE=data/4Z7907551R.kp

ECUXPLOT_JARS:=jcommon-$(JCOMMON_VER).jar jfreechart-$(JFREECHART_VER).jar applib.jar flanagan.jar AppleJavaExtensions.jar
COMMON_JARS:=opencsv-$(OPENCSV_VER).jar commons-lang3-$(COMMONS_LANG3_VER).jar

JARS:=$(ECUXPLOT_JARS) $(COMMON_JARS)

TARGET=ECUxPlot-$(ECUXPLOT_VER)
INSTALLER=ECUxPlot-$(ECUXPLOT_VER)-setup.exe

ARCHIVES=$(TARGET).tar.gz $(TARGET).MacOS.tar.gz

AFLAGS:= -Decuxplot_jars="$(ECUXPLOT_JARS)" -Dcommon_jars="$(COMMON_JARS)" -Dtarget="$(TARGET)"
ANT:=ant $(AFLAGS)

VERSION_JAVA:=src/org/nyet/util/Version.java

all: $(TARGET).jar mapdump.jar build/version.txt

compile: build.xml $(VERSION_JAVA)
	$(ANT) compile

$(TARGET).jar: compile
	$(ANT) ecuxplot

mapdump.jar: compile
	$(ANT) mapdump

run: $(TARGET).jar
	$(ANT) run

archives: $(ARCHIVES)
installer: $(INSTALLER)
rsync: $(ARCHIVES) $(INSTALLER)
	$(RSYNC) $^ nyet.org:public_html/cars/files/

binclean:
	rm -f ECUxPlot*.{jar,zip,tar.gz} mapdump.jar *.exe

clean: binclean
	rm -rf build
	rm -f $(VERSION_JAVA)

%.csv: %.kp mapdump
	./mapdump -r $(REFERENCE) $< > $@

build/version.txt: Makefile
	@mkdir -p build
	@rm -f $@
	@echo $(ECUXPLOT_VER) > $@

PROFILES:= $(addprefix profiles/,B5S4/fueling.xml B5S4/constants.xml B8S4/constants.xml)

INSTALL_FILES:= ECUxPlot-$(ECUXPLOT_VER).jar mapdump.jar \
		$(subst :, ,$(JARS)) build/version.txt README-Zeitronix.txt \
		gpl-3.0.txt flanagan-license.txt

GEN:=	sed -e 's/%VERSION/$(VERSION)/g' | \
	sed -e 's/%RELEASE/$(RELEASE)/g' | \
	sed -e 's/%JAVAC_MAJOR_VER/$(JAVAC_MAJOR_VER)/g' | \
	sed -e 's/%JAVAC_MINOR_VER/$(JAVAC_MINOR_VER)/g' | \
	sed -e 's/%ECUXPLOT_VER/$(ECUXPLOT_VER)/g' | \
	sed -e 's/%JFREECHART_VER/$(JFREECHART_VER)/g' | \
	sed -e 's/%JCOMMON_VER/$(JCOMMON_VER)/g' | \
	sed -e 's/%OPENCSV_VER/$(OPENCSV_VER)/g' | \
	sed -e 's/%COMMONS_LANG3_VER/$(COMMONS_LANG3_VER)/g'

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
	rm -f $(INSTALL_DIR)/commons-lang3-*.jar
	rm -f $(INSTALL_DIR)/jcommon-*.jar
	rm -f $(INSTALL_DIR)/jfreechart-*.jar
	rm -f $(INSTALL_DIR)/opencsv-*.jar
	install -D -m 644 $(INSTALL_FILES) $(INSTALL_DIR)
	install ECUxPlot.sh $(INSTALL_DIR)
	install mapdump.sh $(INSTALL_DIR)
	cp -a --parents $(PROFILES) $(INSTALL_DIR)

tag:
	git tag -a $(ECUXPLOT_VER) -m "Version $(ECUXPLOT_VER)"

%.java: %.java.template Makefile
	cat $< | $(GEN) > $@

.PRECIOUS: $(VERSION_JAVA)
