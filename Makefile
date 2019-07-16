ECUXPLOT_UID := 20150620L
ECUXPLOT_VER := $(shell git describe --tags --abbrev=4 --dirty --always | sed -e 's/v\(.*\)/\1/')
VERSION := $(shell echo $(ECUXPLOT_VER) | sed -e 's/\([^.]*\.[^r]*\)r.*/\1/')
RELEASE := $(shell echo $(ECUXPLOT_VER) | sed -e 's/[^.]*\.[^r]*r\([^.]*\.[^-]*\).*/\1/')
RC      := $(shell echo $(ECUXPLOT_VER) | sed -e 's/[^.]*\.[^r]*r[^.]*\.[^-]*\(-.*\)/\1/')

JCOMMON_VER := 1.0.17
JFREECHART_VER := 1.0.14
OPENCSV_VER := 2.3
COMMONS_CLI_VER := 1.3.1
COMMONS_LANG3_VER := 3.4
JAVA_TARGET_VER := 9

# things to pass to build/build.properties
PROPVARS:=ECUXPLOT_JARS COMMON_JARS TARGET JAVAC_MAJOR_VER JAVA_TARGET_VER

PWD := $(shell pwd)
UNAME := $(shell uname -s | cut -f 1 -d -)
READLINK_Linux_flags := "-e" # SIGH
READLINK_CYGWIN_NT_flags := "-e" # SIGH
JAVAC := $(shell readlink $(READLINK_$(UNAME)_flags) "$(shell which javac 2> /dev/null)")
JAVAC_DIR := $(shell dirname "$(JAVAC)")/..
JAVAC_VER := $(shell javac -version 2>&1 | sed -e 's/javac \([^.]*\.[^.]*\)\.\(.*\)/\1.\2/')
JAVAC_S_VER := $(subst ., ,$(JAVAC_VER))
JAVAC_MAJOR_VER := $(word 1,$(JAVAC_S_VER))
JAVAC_MINOR_VER := $(word 2,$(JAVAC_S_VER))

ifeq ($(findstring CYGWIN,$(UNAME)),CYGWIN)
# cygwin under Windows
LAUNCH4J := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\Launch4j;C:\Program Files (x86)\Launch4j")' which launch4jc 2> /dev/null)'
ECUXPLOT_XML := '$(shell cygpath -w $(PWD)/build/ECUxPlot.xml)'
MAPDUMP_XML := '$(shell cygpath -w $(PWD)/build/mapdump.xml)'

MAKENSIS := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\NSIS;C:\Program Files (x86)\NSIS")' which makensis 2> /dev/null)'

OPT_PRE := '/'

JAVA_HOME ?= $(shell cygpath -w "$(JAVAC_DIR)")
else # !cygwin
# Darwin or Linux
LAUNCH4J := $(shell PATH="$(PATH):/usr/local/launch4j" which launch4j)
ECUXPLOT_XML := $(PWD)/build/ECUxPlot.xml
MAPDUMP_XML := $(PWD)/build/mapdump.xml
MAKENSIS := $(shell which makensis 2> /dev/null)
OPT_PRE := '-'
ifeq ($(UNAME),Darwin)
# Darwin
JAVA_HOME ?= $(shell /usr/libexec/java_home)
else # !Darwin
# Linux
JAVA_HOME ?= $(JAVAC_DIR)
endif # Darwin
endif

INSTALL_DIR := /usr/local/ecuxplot

RSYNC := rsync

REFERENCE=data/4Z7907551R.kp

ECUXPLOT_JARS := \
    jcommon-$(JCOMMON_VER).jar \
    jfreechart-$(JFREECHART_VER).jar \
    applib.jar \
    flanagan.jar

COMMON_JARS := \
    opencsv-$(OPENCSV_VER).jar \
    commons-lang3-$(COMMONS_LANG3_VER).jar \
    commons-cli-$(COMMONS_CLI_VER).jar

JARS:=$(ECUXPLOT_JARS) $(COMMON_JARS)

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

.PHONY: binclean clean

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
	-e 's/%ECUXPLOT_UID/$(ECUXPLOT_UID)/g' \
	-e 's/%ECUXPLOT_VER/$(ECUXPLOT_VER)/g' \
	-e 's/%JFREECHART_VER/$(JFREECHART_VER)/g' \
	-e 's/%JCOMMON_VER/$(JCOMMON_VER)/g' \
	-e 's/%OPENCSV_VER/$(OPENCSV_VER)/g' \
	-e 's/%COMMONS_LANG3_VER/$(COMMONS_LANG3_VER)/g' \
	-e 's/%COMMONS_CLI_VER/$(COMMONS_CLI_VER)/g'

include scripts/Windows.mk
ifneq ($(UNAME),Linux)
include scripts/MacOS.mk
endif

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

.PHONY: install tag force

%: %.template Makefile
	@echo Creating $@
	@cat $< | $(GEN) > $@

build/build.properties: Makefile build/version.txt
	@mkdir -p build
	@echo Creating $@
	$(shell echo "" > $@) $(foreach V,$(PROPVARS),$(shell echo "$(V)=$($V)" >> $@))

latest-links: archives installers
	@[ -z "$(WIN_INSTALLER)" ] || ln -sf $(WIN_INSTALLER) ECUxPlot-latest-setup.exe
	@[ -z "$(MAC_INSTALLER)" ] || ln -sf $(MAC_INSTALLER) ECUxPlot-latest.$(MAC_TYPE)

vars:
	@echo ecuxplot_ver=$(ECUXPLOT_VER)
	@echo version=$(VERSION)
	@echo release=$(RELEASE)
	@echo rc=$(RC)
	@echo launch4j=$(LAUNCH4J)
	@echo 'JAVAC_DIR=$(JAVAC_DIR)'
	@echo 'JAVAC_VER=$(JAVAC_VER)'
	@echo 'JAVAC_MAJOR_VER=$(JAVAC_MAJOR_VER)'
	@echo 'JAVAC_MINOR_VER=$(JAVAC_MINOR_VER)'
	@echo 'JAVA_HOME=$(JAVA_HOME)'

.PHONY: latest-links vars

export JAVA_HOME
.PRECIOUS: $(VERSION_JAVA)
