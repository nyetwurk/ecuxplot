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

JAVA_TARGET_VER := 11

PWD := $(shell pwd)
UNAME := $(shell uname -s | cut -f 1 -d -)

READLINK_Linux_flags:=-e
READLINK_CYGWIN_NT_flags:=-e
READLINK_Darwin_flags:=	# SIGH

JAVAC := $(shell readlink $(READLINK_$(UNAME)_flags) "$(shell which javac 2> /dev/null)")
JAVAC_DIR := $(shell dirname "$(JAVAC)")/..
JAVAC_VER := $(shell javac -version 2>&1 | cut -f 2 -d " ")
JAVAC_S_VER := $(subst ., ,$(JAVAC_VER))
JAVAC_MAJOR_VER := $(word 1,$(JAVAC_S_VER))
JAVAC_MINOR_VER := $(word 2,$(JAVAC_S_VER))

# lazy evaluation, these don't work on every arch
JAVA_HOME_CYGWIN_NT = $(shell cygpath -w "$(JAVAC_DIR)")
JAVA_HOME_Darwin = $(shell /usr/libexec/java_home)
JAVA_HOME_Linux := $(JAVAC_DIR)

JAVA_HOME ?= $(JAVA_HOME_$(UNAME))
export JAVA_HOME

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

VERSION_JAVA:=src/org/nyet/util/Version.java
TARGET:=ECUxPlot-$(ECUXPLOT_VER)

# ant build target
all $(TARGET).jar mapdump.jar: build/version.txt
	@$(ANT) all

# ant compile target
compile: build.xml build/build.properties $(VERSION_JAVA)
	@$(ANT) compile

# ant run target
run: $(TARGET).jar
	@$(ANT) run

# clean targets
binclean:
	rm -f $(addprefix *.,jar zip tar gz exe)
	rm -f $(addprefix build/*.,jar zip tar gz exe)

clean: binclean
	rm -rf build
	rm -f $(VERSION_JAVA)

# hack to pass version to java app
.PHONY: FORCE
.PRECIOUS: $(VERSION_JAVA)
$(VERSION_JAVA): FORCE

ANT:=ant
RSYNC:=rsync
.PHONY: all compile run binclean clean

# used by wrapper scripts
build/version.txt: FORCE
	@mkdir -p build
	@echo '$(ECUXPLOT_VER)' | cmp -s - $@ || echo '$(ECUXPLOT_VER)' > $@

# used by subbuild.xml
PROPVARS:=ECUXPLOT_JARS COMMON_JARS TARGET JAVAC_MAJOR_VER JAVA_TARGET_VER
build/build.properties: Makefile build/version.txt
	@mkdir -p build
	@echo Creating $@
	$(shell echo "" > $@) $(foreach V,$(PROPVARS),$(shell echo "$(V)=$($V)" >> $@))

tag:
	@if [ -z $(VER) ]; then \
	    echo "usage: 'make tag VER=1.1.1'"; \
	    echo "Existing tags:"; \
	    git tag; \
	    false; \
	fi
	git tag -d v$(VER) > /dev/null 2>&1 || true
	git tag -a v$(VER) -m "Version v$(VER)"

# debug
vars:
	@echo 'ECUXPLOT_VER=$(ECUXPLOT_VER)'
	@echo 'VERSION=$(VERSION)'
	@echo 'RC=$(RC)'
	@echo 'JAVAC_DIR=$(JAVAC_DIR)'
	@echo 'JAVAC_VER=$(JAVAC_VER)'
	@echo 'JAVAC_MAJOR_VER=$(JAVAC_MAJOR_VER)'
	@echo 'JAVAC_MINOR_VER=$(JAVAC_MINOR_VER)'
	@echo 'JAVA_HOME=$(JAVA_HOME)'
	@echo 'JAVA_TARGET_VER=$(JAVA_TARGET_VER)'
	@echo 'JARS=$(JARS)'

# templating machine
GEN:=	sed -e 's/%VERSION/$(VERSION)/g' \
	-e 's/%JAVAC_MAJOR_VER/$(JAVAC_MAJOR_VER)/g' \
	-e 's/%JAVAC_MINOR_VER/$(JAVAC_MINOR_VER)/g' \
	-e 's/%JAVA_TARGET_VER/$(JAVA_TARGET_VER)/g' \
	-e 's/%ECUXPLOT_UID/$(ECUXPLOT_UID)/g' \
	-e 's/%ECUXPLOT_VER/$(ECUXPLOT_VER)/g' \
	-e 's/%JFREECHART_VER/$(JFREECHART_VER)/g' \
	-e 's/%JCOMMON_VER/$(JCOMMON_VER)/g' \
	-e 's/%OPENCSV_VER/$(OPENCSV_VER)/g' \
	-e 's/%COMMONS_LANG3_VER/$(COMMONS_LANG3_VER)/g' \
	-e 's/%COMMONS_TEXT_VER/$(COMMONS_TEXT_VER)/g' \
	-e 's/%COMMONS_CLI_VER/$(COMMONS_CLI_VER)/g'

%: %.template Makefile
	@echo Creating $@
	@cat $< | $(GEN) > $@

# Wrapper/installer builders
# Behaves the same on all hosts
include scripts/installer.mk

# Used to create runtime and stub tar files
# Behaves differently on each host type
include scripts/jpackage.mk
