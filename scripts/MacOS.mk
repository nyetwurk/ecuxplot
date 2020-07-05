#MAC_INSTALLER:=build/$(TARGET).dmg
MAC_INSTALLER:=build/$(TARGET)-MacOS.zip

MAC_APP:=build/Darwin/ECUxPlot.app
MAC_CONFIGS:=$(addprefix $(MAC_APP)/Contents/,Info.plist app/ECUxPlot.cfg )

build/Darwin/%: templates/Darwin/%.template build/version.txt Makefile scripts/MacOS.mk
	@echo "Generating $@"
	@mkdir -p $(dir $(MAC_CONFIGS))
	@cat $< | $(GEN) > $@

# unix launch4j requires full path to .xml
$(MAC_APP)/.stamp: runtime/Darwin/release $(ARCHIVE) $(MAC_CONFIGS)
	@rm -f $@
	@echo "Installing stubs to $(MAC_APP)"
	@tar -xzf templates/Darwin/stub.tar.gz
	@echo "Installing runtime to $(MACv0.9.9-rc3_APP)/Contents/runtime"
	@mkdir -p $(MAC_APP)/Contents/runtime/Contents/Home
	@rsync -a --delete runtime/Darwin/* $(MAC_APP)/Contents/runtime/Contents/Home
	@echo "Installing app to $(MAC_APP)/Contents/app"
	@tar -C $(MAC_APP)/Contents/app -xzf $(ARCHIVE) --strip-components=1
	@touch $@

$(MAC_INSTALLER): $(MAC_APP)/.stamp # $(EXES) $(INSTALL_FILES) ECUxPlot.sh scripts/ECUxPlot.nsi runtime/Darwin/release
	(cd $(dir $(MAC_APP)); zip -qr ../$(notdir $(MAC_INSTALLER)) ECUxPlot.app)
