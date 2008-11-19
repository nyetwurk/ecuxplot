build/.ECUxPlot.app.stamp build/ECUxPlot.app: \
		build/ECUxPlot.app/Contents/Info.plist \
		build/ECUxPlot.app/Contents/PkgInfo \
		build/ECUxPlot.app/Contents/MacOS/JavaApplicationStub \
		build/ECUxPlot.app/Contents/Resources/ECUxPlot.icns \
		$(JARFILES) $(TARGET).jar
	@mkdir -p build/ECUxPlot.app/Contents/Resources/Java
	cp -f $(JARFILES) $(TARGET).jar build/ECUxPlot.app/Contents/Resources/Java
	touch build/.ECUxPlot.app.stamp

build/ECUxPlot.app/Contents/Info.plist: MacOS.data/Info.plist.template Makefile
	@mkdir -p build/ECUxPlot.app/Contents
	cat $< | $(GEN) > $@

build/ECUxPlot.app/Contents/PkgInfo: MacOS.data/PkgInfo
	@mkdir -p build/ECUxPlot.app/Contents
	cp -f $< $@

build/ECUxPlot.app/Contents/MacOS/JavaApplicationStub: Makefile
	@mkdir -p build/ECUxPlot.app/Contents/MacOS
	ln -sf "/System/Library/Frameworks/JavaVM.framework/Resources/MacOS/JavaApplicationStub" $@

build/ECUxPlot.app/Contents/Resources/%.icns: MacOS.data/%.icns
	@mkdir -p build/ECUxPlot.app/Contents/Resources
	cp -f $< $@

$(TARGET).MacOS.tar.gz: build/ECUxPlot.app build/.ECUxPlot.app.stamp
	(cd build; tar czvf ../$@ ECUxPlot.app)
