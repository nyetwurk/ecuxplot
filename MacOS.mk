ECUxPlot.app:   ECUxPlot.app/Contents/Info.plist \
		ECUxPlot.app/Contents/PkgInfo \
		ECUxPlot.app/Contents/MacOS/JavaApplicationStub \
		ECUxPlot.app/Contents/Resources/ECUxPlot.icns \
		$(JARFILES) $(TARGET).jar
	@mkdir -p ECUxPlot.app/Contents/Resources/Java
	cp -f $(JARFILES) $(TARGET).jar ECUxPlot.app/Contents/Resources/Java

ECUxPlot.app/Contents/Info.plist: MacOS.data/Info.plist.template Makefile
	@mkdir -p ECUxPlot.app/Contents
	sed -e 's/VERSION/$(VERSION)/g' < $< | sed -e 's/RELEASE/$(RELEASE)/g' > $@

ECUxPlot.app/Contents/PkgInfo: MacOS.data/PkgInfo
	@mkdir -p ECUxPlot.app/Contents
	cp -f $< $@

ECUxPlot.app/Contents/MacOS/JavaApplicationStub: MacOS.data/JavaApplicationStub
	@mkdir -p ECUxPlot.app/Contents/MacOS
	cp -f $< $@

ECUxPlot.app/Contents/Resources/%.icns: MacOS.data/%.icns
	@mkdir -p ECUxPlot.app/Contents/Resources
	cp -f $< $@
