# Install Launch4j for Linux
install-launch4j:
	@echo "Installing Launch4j..."
	@LAUNCH4J_VERSION=$$(cat scripts/launch4j-version.txt | grep -v '^#' | head -1); \
	LAUNCH4J_VER_NUM=$$(echo $$LAUNCH4J_VERSION | sed 's/launch4j-//'); \
	LAUNCH4J_URL="https://sourceforge.net/projects/launch4j/files/launch4j-3/$$LAUNCH4J_VER_NUM/launch4j-$$LAUNCH4J_VERSION-linux-x64.tgz/download"; \
	echo "Installing Launch4j $$LAUNCH4J_VER_NUM..."; \
	rm -rf /tmp/launch4j; \
	curl -sSL $$LAUNCH4J_URL | tar xzf - -C /tmp; \
	dos2unix /tmp/launch4j/launch4j /tmp/launch4j/launch4jc; \
	sudo rm -rf /usr/local/launch4j; \
	sudo mv /tmp/launch4j /usr/local


EXES:=build/CYGWIN_NT/ECUxPlot.exe build/CYGWIN_NT/mapdump.exe

WIN_INSTALLER:=build/$(TARGET)-setup.exe

build/%.xml: templates/%.xml.template build/version.txt Makefile scripts/Windows.mk
	@mkdir -p build
	cat $< | $(GEN) > $@

build/CYGWIN_NT/ECUxPlot.exe: ECUxPlot-$(ECUXPLOT_VER).jar build/ECUxPlot.xml
	@[ -x $(LAUNCH4J) ] || ( echo "Can't find launch4j!"; false)
	@mkdir -p build/CYGWIN_NT
	cp -f ECUxPlot-$(ECUXPLOT_VER).jar build/
	$(LAUNCH4J) build/ECUxPlot.xml

build/CYGWIN_NT/mapdump.exe: mapdump.jar build/mapdump.xml
	@mkdir -p build/CYGWIN_NT
	cp -f mapdump.jar build/
	$(LAUNCH4J) build/mapdump.xml

.PHONY: exes
exes: $(EXES)

$(WIN_INSTALLER): $(EXES) $(INSTALL_FILES) ECUxPlot.sh scripts/ECUxPlot.nsi runtime/CYGWIN_NT/release
	@[ -x $(MAKENSIS) ] || (echo "Can't find NSIS!"; false)
	$(MAKENSIS) $(OPT_PRE)NOCD \
	    $(OPT_PRE)DVERSION=$(ECUXPLOT_VER) \
	    $(OPT_PRE)DJFREECHART_VER=$(JFREECHART_VER) \
	    $(OPT_PRE)DJCOMMON_VER=$(JCOMMON_VER) \
	    $(OPT_PRE)DOPENCSV_VER=$(OPENCSV_VER) \
	    $(OPT_PRE)DCOMMONS_CLI_VER=$(COMMONS_CLI_VER) \
	    $(OPT_PRE)DCOMMONS_LANG3_VER=$(COMMONS_LANG3_VER) \
	    $(OPT_PRE)DCOMMONS_TEXT_VER=$(COMMONS_TEXT_VER) \
	    scripts/ECUxPlot.nsi
	@chmod +x $(WIN_INSTALLER)
