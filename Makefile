ECUXPLOT_VER := $(shell git describe --tags --abbrev=4 --dirty --always | sed -e 's/v\(.*\)/\1/')
VERSION := $(shell echo $(ECUXPLOT_VER) | sed -e 's/\([^.]*\.[^r]*\)r.*/\1/')
RELEASE := $(shell echo $(ECUXPLOT_VER) | sed -e 's/[^.]*\.[^r]*r\([^.]*\.[^-]*\).*/\1/')
RC      := $(shell echo $(ECUXPLOT_VER) | sed -e 's/[^.]*\.[^r]*r[^.]*\.[^-]*\(-.*\)/\1/')

JCOMMON_VER := 1.0.17
JFREECHART_VER := 1.0.14
OPENCSV_VER := 2.3
COMMONS_LANG3_VER := 3.1
COMMONS_CLI_VER := 1.2
JAVA_TARGET_VER := 7

# things to pass to build/build.properties
PROPVARS:=ECUXPLOT_JARS COMMON_JARS TARGET JAVA_TARGET_VER JAVA_RT_PATH

PWD := $(shell pwd)
UNAME := $(shell uname -s)
JAVAC := $(shell readlink -e "$(shell which javac)")
JAVAC_DIR := $(shell dirname "$(JAVAC)")/..
JAVAC_VER := $(shell javac -version 2>&1 | sed -e 's/javac \([^.]*\.[^.]*\)\.\(.*\)/\1 \2/')
JAVAC_MAJOR_VER := $(word 1,$(JAVAC_VER))
JAVAC_MINOR_VER := $(word 2,$(JAVAC_VER))

ifeq ($(findstring CYGWIN,$(UNAME)),CYGWIN)
LAUNCH4J := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\Launch4j;C:\Program Files (x86)\Launch4j")' which launch4jc)'
ECUXPLOT_XML := '$(shell cygpath -w $(PWD)/build/ECUxPlot.xml)'
MAPDUMP_XML := '$(shell cygpath -w $(PWD)/build/mapdump.xml)'

MAKENSIS := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\NSIS;C:\Program Files (x86)\NSIS")' which makensis)'

OPT_PRE := '/'

JAVA_HOME ?= $(shell cygpath -w $(JAVAC_DIR))
JAVA_RT_PATH := $(PROGRAMFILES)/Java/jre$(JAVA_TARGET_VER)
ifdef ProgramW6432
JAVA_RT_PATH := $(JAVA_RT_PATH);$(ProgramW6432)/Java/jre$(JAVA_TARGET_VER)
endif
JAVA_RT_PATH := $(subst \,/,$(JAVA_RT_PATH))

else
#ARCH_x86_64 := amd64
#ARCH_i686 := i386
#UNAME_ARCH := $(ARCH_$(shell uname -m))
DPKG_ARCH := $(shell dpkg --print-architecture)
LAUNCH4J := /usr/local/launch4j/launch4j
ECUXPLOT_XML := $(PWD)/build/ECUxPlot.xml
MAPDUMP_XML := $(PWD)/build/mapdump.xml
MAKENSIS := makensis
OPT_PRE := '-'
JAVA_HOME ?= $(JAVAC_DIR)
JAVA_RT_PATH := /usr/lib/jvm/java-$(JAVA_TARGET_VER)-openjdk-$(DPKG_ARCH)/jre
endif

INSTALL_DIR := /usr/local/ecuxplot

RSYNC := rsync

REFERENCE=data/4Z7907551R.kp

ECUXPLOT_JARS := \
    jcommon-$(JCOMMON_VER).jar \
    jfreechart-$(JFREECHART_VER).jar \
    applib.jar \
    flanagan.jar \
    AppleJavaExtensions.jar

COMMON_JARS := \
    opencsv-$(OPENCSV_VER).jar \
    commons-lang3-$(COMMONS_LANG3_VER).jar \
    commons-cli-$(COMMONS_CLI_VER).jar

JARS:=$(ECUXPLOT_JARS) $(COMMON_JARS)

TARGET=ECUxPlot-$(ECUXPLOT_VER)

INSTALLER=$(TARGET)-setup.exe
ARCHIVES=$(TARGET).tar.gz $(TARGET).MacOS.tar.gz

ANT:=ant

VERSION_JAVA:=src/org/nyet/util/Version.java

all $(TARGET).jar mapdump.jar: build/version.txt
	@$(ANT) all

compile: build.xml build/build.properties $(VERSION_JAVA)
	@$(ANT) compile

run: $(TARGET).jar
	@$(ANT) run

archives: $(ARCHIVES)
installer: $(INSTALLER)
rsync: $(ARCHIVES) $(INSTALLER)
	$(RSYNC) $^ nyet.org:public_html/cars/files/

binclean:
	rm -f $(addprefix ECUxPlot*.,jar zip tar gz) mapdump.jar *.exe

clean: binclean
	rm -rf build
	rm -f $(VERSION_JAVA)

%.csv: %.kp mapdump
	./mapdump -r $(REFERENCE) $< > $@

build/version.txt: force
	@mkdir -p build
	@echo '$(ECUXPLOT_VER)' | cmp -s - $@ || echo '$(ECUXPLOT_VER)' > $@

$(VERSION_JAVA): build/version.txt

PROFILES:= $(addprefix profiles/,B5S4/fueling.xml B5S4/constants.xml B8S4/constants.xml)

INSTALL_FILES:= $(TARGET).jar mapdump.jar \
		$(subst :, ,$(JARS)) build/version.txt README-Zeitronix.txt \
		gpl-3.0.txt flanagan-license.txt

GEN:=	sed -e 's/%VERSION/$(VERSION)/g' \
	-e 's/%RELEASE/$(RELEASE)/g' \
	-e 's/%JAVAC_MAJOR_VER/$(JAVAC_MAJOR_VER)/g' \
	-e 's/%JAVAC_MINOR_VER/$(JAVAC_MINOR_VER)/g' \
	-e 's/%ECUXPLOT_VER/$(ECUXPLOT_VER)/g' \
	-e 's/%JFREECHART_VER/$(JFREECHART_VER)/g' \
	-e 's/%JCOMMON_VER/$(JCOMMON_VER)/g' \
	-e 's/%OPENCSV_VER/$(OPENCSV_VER)/g' \
	-e 's/%COMMONS_LANG3_VER/$(COMMONS_LANG3_VER)/g' \
	-e 's/%COMMONS_CLI_VER/$(COMMONS_CLI_VER)/g'

include scripts/Windows.mk
include scripts/MacOS.mk

$(TARGET).tar.gz: $(INSTALL_FILES) $(PROFILES) ECUxPlot.sh
	@rm -f $@
	@rm -rf build/ECUxPlot
	mkdir -p build/ECUxPlot
	install -m 644 $(INSTALL_FILES) build/ECUxPlot
	install ECUxPlot.sh build/ECUxPlot
	@mkdir -p build/ECUxPlot/profiles
	(cd profiles; tar cf - .) | (cd build/ECUxPlot/profiles && tar xf -)
	(cd build; tar czvf ../$@ ECUxPlot)

install: $(INSTALL_FILES) $(PROFILES)
	mkdir -p $(INSTALL_DIR)
	mkdir -p $(INSTALL_DIR)/profiles
	rm -f $(INSTALL_DIR)/ECUxPlot*.jar
	rm -f $(INSTALL_DIR)/commons-lang3-*.jar
	rm -f $(INSTALL_DIR)/jcommon-*.jar
	rm -f $(INSTALL_DIR)/jfreechart-*.jar
	rm -f $(INSTALL_DIR)/opencsv-*.jar
	install -D -m 644 $(INSTALL_FILES) $(INSTALL_DIR)
	install ECUxPlot.sh $(INSTALL_DIR)
	install mapdump.sh $(INSTALL_DIR)
	(cd profiles; tar cf - .) | (cd $(INSTALL_DIR)/profiles && tar xf -)

tag:	force
	@if [ -z $(VER) ]; then \
	    echo "usage: 'make tag VER=1.1r1.1'"; \
	    echo "Existing tags:"; \
	    git tag; \
	    false; \
	fi
	git tag -a v$(VER) -m "Version v$(VER)"

%.java: %.java.template Makefile
	@echo Creating $@
	@cat $< | $(GEN) > $@

build/build.properties: Makefile build/version.txt
	@mkdir -p build
	@echo Creating $@
	$(shell echo "" > $@) $(foreach V,$(PROPVARS),$(shell echo $(V)='$($V)' >> $@))

versioninfo:
	@echo ecuxplot_ver=$(ECUXPLOT_VER)
	@echo version=$(VERSION)
	@echo release=$(RELEASE)
	@echo rc=$(RC)
	@echo 'JAVA_HOME=$(JAVA_HOME)'
	@echo 'JAVA_RT_PATH=$(JAVA_RT_PATH)'

export JAVA_HOME
.PRECIOUS: $(VERSION_JAVA)
.PHONY: force compile clean binclean
