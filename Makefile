ECUXPLOT_UID := 20150620L
ECUXPLOT_VER := $(shell git describe --tags --abbrev=4 --dirty --always)
VERSION := $(subst v,,$(shell echo $(ECUXPLOT_VER) | cut -f 1 -d -))
RC      := $(shell echo $(ECUXPLOT_VER) | cut -f 2- -d -)

jar_version = $(lastword $(subst -, ,$(shell basename $(lastword $(wildcard lib/$(1)-*.jar)) .jar)))
JCOMMON_VER := $(call jar_version,jcommon)
JFREECHART_VER := $(call jar_version,jfreechart)
OPENCSV_VER := $(call jar_version,opencsv)
COMMONS_CLI_VER := $(call jar_version,commons-cli)
COMMONS_LANG3_VER := $(call jar_version,commons-lang3)
COMMONS_TEXT_VER := $(call jar_version,commons-text)

JAVA_TARGET_VER := 9

# things to pass to build/build.properties
PROPVARS:=ECUXPLOT_JARS COMMON_JARS TARGET JAVAC_MAJOR_VER JAVA_TARGET_VER

PWD := $(shell pwd)
UNAME := $(shell uname -s | cut -f 1 -d -)

READLINK_Linux_flags := "-e" # SIGH
READLINK_CYGWIN_NT_flags := "-e" # SIGH

JAVAC := $(shell readlink $(READLINK_$(UNAME)_flags) "$(shell which javac 2> /dev/null)")
JAVAC_DIR := $(shell dirname "$(JAVAC)")/..
JAVAC_VER := $(shell javac -version 2>&1 | cut -f 2 -d " ")
JAVAC_S_VER := $(subst ., ,$(JAVAC_VER))
JAVAC_MAJOR_VER := $(word 1,$(JAVAC_S_VER))
JAVAC_MINOR_VER := $(word 2,$(JAVAC_S_VER))

APP_EXT_Linux :=
APP_EXT_CYGWIN_NT :=
APP_EXT_Darwin := .app
APP_EXT := $(APP_EXT_$(UNAME))

ICON_EXT_Linux := .png
ICON_EXT_CYGWIN_NT := .ico
ICON_EXT_Darwin := .icns
ICON_EXT := $(ICON_EXT_$(UNAME))

ifeq ($(findstring CYGWIN,$(UNAME)),CYGWIN)
# cygwin under Windows
JAVA_HOME_CYGWIN_NT := $(shell cygpath -w "$(JAVAC_DIR)")
LAUNCH4J := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\Launch4j;C:\Program Files (x86)\Launch4j")' which launch4jc 2> /dev/null)'
ECUXPLOT_XML := '$(shell cygpath -w $(PWD)/build/ECUxPlot.xml)'
MAPDUMP_XML := '$(shell cygpath -w $(PWD)/build/mapdump.xml)'
MAKENSIS := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\NSIS;C:\Program Files (x86)\NSIS")' which makensis 2> /dev/null)'
OPT_PRE := '/'
else # !cygwin
# Darwin or Linux
JAVA_HOME_Darwin := $(shell /usr/libexec/java_home)
JAVA_HOME_Linux := $(JAVAC_DIR)
LAUNCH4J := $(shell PATH="$(PATH):/usr/local/launch4j" which launch4j)
ECUXPLOT_XML := $(PWD)/build/ECUxPlot.xml
MAPDUMP_XML := $(PWD)/build/mapdump.xml
MAKENSIS := $(shell which makensis 2> /dev/null)
OPT_PRE := '-'
endif

JAVA_HOME ?= $(JAVA_HOME_$(UNAME))

INSTALL_DIR := /usr/local/ecuxplot

RSYNC := rsync

REFERENCE=data/4Z7907551R.kp

ECUXPLOT_JARS := \
    jcommon-$(JCOMMON_VER).jar \
    jfreechart-$(JFREECHART_VER).jar \
    jspline.jar \
    flanagan.jar

COMMON_JARS := \
    opencsv-$(OPENCSV_VER).jar \
    commons-cli-$(COMMONS_CLI_VER).jar \
    commons-lang3-$(COMMONS_LANG3_VER).jar \
    commons-text-$(COMMONS_TEXT_VER).jar

JARS:=$(addprefix lib/,$(ECUXPLOT_JARS) $(COMMON_JARS))

TARGET=ECUxPlot-$(ECUXPLOT_VER)

ARCHIVES=$(TARGET).tar.gz

ANT:=ant

VERSION_JAVA:=src/org/nyet/util/Version.java

all $(TARGET).jar mapdump.jar: build/version.txt
	@$(ANT) all

compile: build.xml build/build.properties $(VERSION_JAVA)
	@$(ANT) compile

run: $(TARGET).jar
	@$(ANT) run

.PHONY: all compile run

binclean:
	rm -f $(addprefix ECUxPlot*.,jar zip tar gz) mapdump.jar *.exe *.pkg *.dmg

clean: binclean
	rm -rf build
	rm -f $(VERSION_JAVA)

.PHONY: binclean clean force

%.csv: %.kp mapdump
	./mapdump -r $(REFERENCE) $< > $@

build/version.txt: force
	@mkdir -p build
	@echo '$(ECUXPLOT_VER)' | cmp -s - $@ || echo '$(ECUXPLOT_VER)' > $@

$(VERSION_JAVA): build/version.txt

PROFILES:= $(addprefix profiles/,B5S4/fueling.xml B5S4/constants.xml B8S4/constants.xml)

INSTALL_FILES:= $(TARGET).jar mapdump.jar \
		$(subst :, ,$(JARS)) README-Zeitronix.txt \
		gpl-3.0.txt flanagan-license.txt

GEN:=	sed -e 's/%VERSION/$(VERSION)/g' \
	-e 's/%JAVAC_MAJOR_VER/$(JAVAC_MAJOR_VER)/g' \
	-e 's/%JAVAC_MINOR_VER/$(JAVAC_MINOR_VER)/g' \
	-e 's/%ECUXPLOT_UID/$(ECUXPLOT_UID)/g' \
	-e 's/%ECUXPLOT_VER/$(ECUXPLOT_VER)/g' \
	-e 's/%JFREECHART_VER/$(JFREECHART_VER)/g' \
	-e 's/%JCOMMON_VER/$(JCOMMON_VER)/g' \
	-e 's/%OPENCSV_VER/$(OPENCSV_VER)/g' \
	-e 's/%COMMONS_LANG3_VER/$(COMMONS_LANG3_VER)/g' \
	-e 's/%COMMONS_TEXT_VER/$(COMMONS_TEXT_VER)/g' \
	-e 's/%COMMONS_CLI_VER/$(COMMONS_CLI_VER)/g'

include scripts/Windows.mk

archives: $(ARCHIVES)
mac-installer: $(MAC_INSTALLER)
win-installer: $(WIN_INSTALLER)
installers: mac-installer win-installer

rsync: $(ARCHIVES) $(WIN_INSTALLER) $(MAC_INSTALLER)
	$(RSYNC) $^ nyet.org:public_html/cars/files/

.PHONY: archives mac-installer win-installer installers rsync

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
	rm -f $(INSTALL_DIR)/commons-*-*.jar
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

.PHONY: install tag force

%: %.template Makefile
	@echo Creating $@
	@cat $< | $(GEN) > $@

#JLINK_MODULES:=ALL-MODULE-PATH
JLINK_MODULES:=java.base,java.desktop,java.datatransfer
runtime/$(UNAME)/release:
	@rm -rf runtime/$(UNAME)
	"$(JAVA_HOME)/bin/jlink" --no-header-files --no-man-pages --compress=2 --strip-debug --add-modules $(JLINK_MODULES) --output runtime/$(UNAME)

.PHONY: app installer

# --app-version can't have dashes in windows
PACKAGER_OPTS:=$(PACKAGER_OPTS_$(UNAME)) \
    --name ECUxPlot \
    --description "ECUxPlot $(ECUXPLOT_VER)" \
    --app-version $(VERSION) \
    --dest build/$(UNAME)

app build/$(UNAME)/ECUxPlot$(APP_EXT): $(TARGET).jar mapdump.jar runtime/$(UNAME)/release
	@mkdir -p build/ECUxPlot; rm -rf build/ECUxPlot build/$(UNAME)/ECUxPlot$(APP_EXT)
	@rsync --del -aR $(INSTALL_FILES) $(PROFILES) build/ECUxPlot
	"$(JAVA_HOME)/bin/jpackage" $(PACKAGER_OPTS) --type app-image \
	    --input build/ECUxPlot \
	    --icon src/org/nyet/ecuxplot/icons/ECUxPlot$(ICON_EXT) \
	    --file-associations scripts/assoc.prop \
	    --main-jar $(TARGET).jar \
	    --main-class org.nyet.ecuxplot.ECUxPlot \
	    --runtime-image runtime/$(UNAME)

installer: build/$(UNAME)/ECUxPlot$(APP_EXT)
	"$(JAVA_HOME)/bin/jpackage" $(PACKAGER_OPTS) --app-image build/$(UNAME)

build/build.properties: Makefile build/version.txt
	@mkdir -p build
	@echo Creating $@
	$(shell echo "" > $@) $(foreach V,$(PROPVARS),$(shell echo "$(V)=$($V)" >> $@))

.PHONY: latest-links archives installers vars
latest-links: archives installers
	@[ -z "$(WIN_INSTALLER)" ] || ln -sf $(WIN_INSTALLER) ECUxPlot-latest-setup.exe
	@[ -z "$(MAC_INSTALLER)" ] || ln -sf $(MAC_INSTALLER) ECUxPlot-latest.$(MAC_TYPE)

vars:
	@echo ecuxplot_ver=$(ECUXPLOT_VER)
	@echo version=$(VERSION)
	@echo rc=$(RC)
	@echo launch4j=$(LAUNCH4J)
	@echo 'JAVAC_DIR=$(JAVAC_DIR)'
	@echo 'JAVAC_VER=$(JAVAC_VER)'
	@echo 'JAVAC_MAJOR_VER=$(JAVAC_MAJOR_VER)'
	@echo 'JAVAC_MINOR_VER=$(JAVAC_MINOR_VER)'
	@echo 'JAVA_HOME=$(JAVA_HOME)'
	@echo 'JARS=$(JARS)'

.PHONY: latest-links vars

export JAVA_HOME
.PRECIOUS: $(VERSION_JAVA)
