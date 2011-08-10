ECUxPlot.MF: Makefile
	@echo "Manifest-Version: 1.0" > $@
	@echo "Main-Class: org.nyet.ecuxplot.ECUxPlot" >> $@
	@echo "Class-Path: $(subst :, ,$(JARS))" >> $@

ECUxPlot-$(ECUXPLOT_VER).jar: ECUxPlot.MF $(EX_CLASSES)
	@rm -f $@
	jar cfm $@ ECUxPlot.MF `find org -name \*.class -o -name \*.png` `find vec_math -name \*.class`

%.xml: %.xml.template Makefile scripts/Windows.mk
	cat $< | $(GEN) > $@

# unix launch4j requires full path to .xml
ECUxPlot.exe: ECUxPlot-$(ECUXPLOT_VER).jar ECUxPlot.xml ECUxPlot.ico version.txt
	$(LAUNCH4J) "$(shell cygpath -w $(PWD)/ECUxPlot.xml)"

$(INSTALLER): ECUxPlot.exe $(INSTALL_FILES) ECUxPlot.sh scripts/ECUxPlot.nsi
	$(MAKENSIS) $(OPT_PRE)NOCD \
	    $(OPT_PRE)DVERSION=$(ECUXPLOT_VER) \
	    $(OPT_PRE)DJFREECHART_VER=$(JFREECHART_VER) \
	    $(OPT_PRE)DJCOMMON_VER=$(JCOMMON_VER) \
	    $(OPT_PRE)DOPENCSV_VER=$(OPENCSV_VER) \
	    scripts/ECUxPlot.nsi
	@chmod +x $(INSTALLER)
