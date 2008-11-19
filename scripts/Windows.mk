ECUxPlot.MF: Makefile
	@echo "Manifest-Version: 1.0" > $@
	@echo "Main-Class: org.nyet.ecuxplot.ECUxPlot" >> $@
	@echo "Class-Path: $(subst :, ,$(JARS))" >> $@

ECUxPlot-$(VERSION)r$(RELEASE).jar: ECUxPlot.MF $(EX_CLASSES)
	@rm -f $@
	jar cfm $@ ECUxPlot.MF `find org -name \*.class -o -name \*.png` `find vec_math -name \*.class`

%.xml: %.xml.template Makefile
	sed -e 's/VERSION/$(VERSION)/g' < $< | sed -e 's/RELEASE/$(RELEASE)/g' > $@

ECUxPlot.exe: ECUxPlot-$(VERSION)r$(RELEASE).jar ECUxPlot.xml ECUxPlot.ico version.txt
	$(LAUNCH4J) '$(PWD)ECUxPlot.xml'

$(INSTALLER): ECUxPlot.exe $(INSTALL_FILES) scripts/ECUxPlot.nsi
	makensis $(OPT_PRE)NOCD \
	    $(OPT_PRE)DVERSION=$(VERSION)r$(RELEASE) \
	    $(OPT_PRE)DJFREECHART_VER=$(JFREECHART_VER) \
	    $(OPT_PRE)DJCOMMON_VER=$(JCOMMON_VER) \
	    $(OPT_PRE)DOPENCSV_VER=$(OPENCSV_VER) \
	    scripts/ECUxPlot.nsi
	@chmod +x $(INSTALLER)
