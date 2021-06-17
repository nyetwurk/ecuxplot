ifeq ($(findstring CYGWIN,$(UNAME)),CYGWIN)
# cygwin under Windows
LAUNCH4J := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\Launch4j;C:\Program Files (x86)\Launch4j")' which launch4jc 2> /dev/null)'
MAKENSIS := '$(shell PATH='$(PATH):$(shell cygpath -pu \
    "C:\Program Files\NSIS;C:\Program Files (x86)\NSIS")' which makensis 2> /dev/null)'
OPT_PRE := '/'
else # !cygwin
# Darwin or Linux
LAUNCH4J := $(shell PATH="$(PATH):/usr/local/launch4j" which launch4j)
MAKENSIS := $(shell which makensis 2> /dev/null)
OPT_PRE := '-'
endif

PROFILES:= $(addprefix profiles/,B5S4/fueling.xml B5S4/constants.xml B8S4/constants.xml)

# base installer (arch independent tar file only)
ARCHIVE:=build/$(TARGET).tar.gz
INSTALL_DIR:=/usr/local/ecuxplot
INSTALL_FILES:= $(TARGET).jar mapdump.jar \
		$(subst :, ,$(JARS)) README-Zeitronix.txt \
		gpl-3.0.txt flanagan-license.txt

.PHONY: archive install tag
archive $(ARCHIVE): $(INSTALL_FILES) $(PROFILES) ECUxPlot.sh mapdump.sh build/version.txt Makefile
	@rm -f $@
	@rm -rf build/ECUxPlot
	mkdir -p build/ECUxPlot
	$(RSYNC) --del -aR $(INSTALL_FILES) $(PROFILES) build/ECUxPlot
	install -m 755 ECUxPlot.sh mapdump.sh build/version.txt build/ECUxPlot
	tar -C build -czf $@ ECUxPlot

install: $(ARCHIVE)
	mkdir -p $(INSTALL_DIR)
	rm -f $(INSTALL_DIR)/*.jar
	rm -rf $(INSTALL_DIR)/lib
	tar -C $(INSTALL_DIR) -xzf $(ARCHIVE) --strip-components=1

# These behave the same on all hosts
include scripts/Windows.mk
include scripts/MacOS.mk

.PHONY: archive mac-installer win-installer installers rsync
mac-installer: $(MAC_INSTALLER)
win-installer: $(WIN_INSTALLER)
installers: mac-installer win-installer

rsync: $(ARCHIVE) $(WIN_INSTALLER) $(MAC_INSTALLER)
	$(MAKE) latest-links
	[ "$(UNAME)" != Darwin ] || $(MAKE) rsync-dmg
	$(RSYNC) -at $^ build/*latest* nyet.org:public_html/cars/files/

.PHONY: latest-links installers vars
latest-links: installers
	@rm -f build/*-latest.*
	@[ -r "$(ARCHIVE)" ] && ln -sf $(notdir $(ARCHIVE)) build/ECUxPlot-latest.tar.gz
	@[ -r "$(WIN_INSTALLER)" ] && ln -sf $(notdir $(WIN_INSTALLER)) build/ECUxPlot-latest-setup.exe
	@[ -r "$(MAC_INSTALLER)" ] && ln -sf $(notdir $(MAC_INSTALLER)) build/ECUxPlot-latest-MacOS.zip
