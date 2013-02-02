EXES:=build/ECUxPlot/ECUxPlot.exe build/ECUxPlot/mapdump.exe

build/%.xml: %.xml.template build/version.txt Makefile scripts/Windows.mk
	@mkdir -p build
	cat $< | $(GEN) > $@

# unix launch4j requires full path to .xml
build/ECUxPlot/ECUxPlot.exe: ECUxPlot-$(ECUXPLOT_VER).jar build/ECUxPlot.xml ECUxPlot.ico build/version.txt
	@mkdir -p build/ECUxPlot
	$(LAUNCH4J) $(ECUXPLOT_XML)

build/ECUxPlot/mapdump.exe: mapdump.jar build/mapdump.xml ECUxPlot.ico build/version.txt
	@mkdir -p build/ECUxPlot
	$(LAUNCH4J) $(MAPDUMP_XML)

$(INSTALLER): $(EXES) $(INSTALL_FILES) ECUxPlot.sh scripts/ECUxPlot.nsi
	$(MAKENSIS) $(OPT_PRE)NOCD \
	    $(OPT_PRE)DVERSION=$(ECUXPLOT_VER) \
	    $(OPT_PRE)DJFREECHART_VER=$(JFREECHART_VER) \
	    $(OPT_PRE)DJCOMMON_VER=$(JCOMMON_VER) \
	    $(OPT_PRE)DOPENCSV_VER=$(OPENCSV_VER) \
	    $(OPT_PRE)DCOMMONS_LANG3_VER=$(COMMONS_LANG3_VER) \
	    scripts/ECUxPlot.nsi
	@chmod +x $(INSTALLER)
