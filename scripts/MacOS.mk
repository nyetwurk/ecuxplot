build/.ECUxPlot.app.stamp build/ECUxPlot.app: \
		build/ECUxPlot.app/Contents/Info.plist \
		build/ECUxPlot.app/Contents/PkgInfo \
		build/ECUxPlot.app/Contents/MacOS/JavaApplicationStub \
		build/ECUxPlot.app/Contents/Resources/MRJApp.properties \
		build/ECUxPlot.app/Contents/Resources/ECUxPlot.icns \
		$(INSTALL_FILES)
	@rm -rf build/ECUxPlot.app/Contents/Resources/Java
	@mkdir -p build/ECUxPlot.app/Contents/Resources/Java
	install -m 644 $(INSTALL_FILES) build/ECUxPlot.app/Contents/Resources/Java
	install ECUxPlot.sh build/ECUxPlot.app/Contents/Resources/Java
	cp -a --parents $(PROFILES) build/ECUxPlot.app/Contents/Resources/Java
	touch build/.ECUxPlot.app.stamp

build/ECUxPlot.app/Contents/%: MacOS.data/%.template Makefile
	@mkdir -p `dirname $@`
	cat $< | $(GEN) > $@

build/ECUxPlot.app/Contents/PkgInfo: MacOS.data/PkgInfo
	@mkdir -p build/ECUxPlot.app/Contents
	install -m 644 $< $@

#build/ECUxPlot.app/Contents/MacOS/JavaApplicationStub: scripts/MacOS.mk
#	@mkdir -p build/ECUxPlot.app/Contents/MacOS
#	ln -sf "/System/Library/Frameworks/JavaVM.framework/Resources/MacOS/JavaApplicationStub" $@

build/ECUxPlot.app/Contents/MacOS/JavaApplicationStub: MacOS.data/JavaApplicationStub
	@mkdir -p build/ECUxPlot.app/Contents/MacOS
	install $< $@

build/ECUxPlot.app/Contents/Resources/%.icns: MacOS.data/%.icns
	@mkdir -p build/ECUxPlot.app/Contents/Resources
	install -m 644 $< $@

$(TARGET).MacOS.tar.gz: build/ECUxPlot.app build/.ECUxPlot.app.stamp
	(cd build; tar czvf ../$@ ECUxPlot.app)
