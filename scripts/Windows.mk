EXES:=build/ECUxPlot.exe build/mapdump.exe

WIN_INSTALLER:=$(TARGET)-setup.exe

build/%.xml: scripts/%.xml.template build/version.txt Makefile scripts/Windows.mk
	@mkdir -p build
	cat $< | $(GEN) > $@

# unix launch4j requires full path to .xml
build/ECUxPlot.exe: ECUxPlot-$(ECUXPLOT_VER).jar build/ECUxPlot.xml ECUxPlot.ico build/version.txt
	@[ -x $(LAUNCH4J) ] || ( echo "Can't find launch4j!"; false)
	cp -f ECUxPlot-$(ECUXPLOT_VER).jar build/
	$(LAUNCH4J) $(ECUXPLOT_XML)

build/mapdump.exe: mapdump.jar build/mapdump.xml ECUxPlot.ico build/version.txt
	cp -f mapdump.jar build/
	$(LAUNCH4J) $(MAPDUMP_XML)


exes: $(EXES)

$(WIN_INSTALLER): $(EXES) $(INSTALL_FILES) ECUxPlot.sh scripts/ECUxPlot.nsi
	@[ -x $(MAKENSIS) ] || (echo "Can't find NSIS!"; false)
	$(MAKENSIS) $(OPT_PRE)NOCD \
	    $(OPT_PRE)DVERSION=$(ECUXPLOT_VER) \
	    $(OPT_PRE)DJFREECHART_VER=$(JFREECHART_VER) \
	    $(OPT_PRE)DJCOMMON_VER=$(JCOMMON_VER) \
	    $(OPT_PRE)DOPENCSV_VER=$(OPENCSV_VER) \
	    $(OPT_PRE)DCOMMONS_LANG3_VER=$(COMMONS_LANG3_VER) \
	    $(OPT_PRE)DCOMMONS_CLI_VER=$(COMMONS_CLI_VER) \
	    scripts/ECUxPlot.nsi
	@chmod +x $(WIN_INSTALLER)
