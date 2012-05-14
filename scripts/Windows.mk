%.xml: %.xml.template Makefile scripts/Windows.mk
	cat $< | $(GEN) > $@

# unix launch4j requires full path to .xml
ECUxPlot.exe: ECUxPlot-$(ECUXPLOT_VER).jar ECUxPlot.xml ECUxPlot.ico version.txt
	$(LAUNCH4J) $(ECUXPLOT_XML)

mapdump.exe: mapdump.java mapdump.xml ECUxPlot.ico version.txt
	$(LAUNCH4J) $(MAPDUMP_XML)

$(INSTALLER): ECUxPlot.exe $(INSTALL_FILES) ECUxPlot.sh scripts/ECUxPlot.nsi
	$(MAKENSIS) $(OPT_PRE)NOCD \
	    $(OPT_PRE)DVERSION=$(ECUXPLOT_VER) \
	    $(OPT_PRE)DJFREECHART_VER=$(JFREECHART_VER) \
	    $(OPT_PRE)DJCOMMON_VER=$(JCOMMON_VER) \
	    $(OPT_PRE)DOPENCSV_VER=$(OPENCSV_VER) \
	    scripts/ECUxPlot.nsi
	@chmod +x $(INSTALLER)
