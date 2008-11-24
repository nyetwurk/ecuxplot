build/.ECUxPlot.app.stamp build/ECUxPlot.app: \
		build/ECUxPlot.app/Contents/Info.plist \
		build/ECUxPlot.app/Contents/PkgInfo \
		build/ECUxPlot.app/Contents/MacOS/JavaApplicationStub \
		build/ECUxPlot.app/Contents/Resources/ECUxPlot.icns \
		$(INSTALL_FILES)
	@rm -rf build/ECUxPlot.app/Contents/Resources/Java
	@mkdir -p build/ECUxPlot.app/Contents/Resources/Java
	cp -f $(INSTALL_FILES) build/ECUxPlot.app/Contents/Resources/Java
	touch build/.ECUxPlot.app.stamp

build/ECUxPlot.app/Contents/Info.plist: MacOS.data/Info.plist.template Makefile
	@mkdir -p build/ECUxPlot.app/Contents
	cat $< | $(GEN) > $@

build/ECUxPlot.app/Contents/PkgInfo: MacOS.data/PkgInfo
	install -D $< $@

#build/ECUxPlot.app/Contents/MacOS/JavaApplicationStub: scripts/MacOS.bk
	#@mkdir -p build/ECUxPlot.app/Contents/MacOS
	#ln -sf "/System/Library/Frameworks/JavaVM.framework/Resources/MacOS/JavaApplicationStub" $@

build/ECUxPlot.app/Contents/MacOS/JavaApplicationStub: MacOS.data/JavaApplicationStub
	install -D $< $@

build/ECUxPlot.app/Contents/Resources/%.icns: MacOS.data/%.icns
	install -D $< $@

$(TARGET).MacOS.tar.gz: build/ECUxPlot.app build/.ECUxPlot.app.stamp
	(cd build; tar czvf ../$@ ECUxPlot.app)
