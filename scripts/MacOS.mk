MAC_ZIP:=build/Darwin/$(TARGET)-MacOS.zip

MAC_APP:=build/Darwin/ECUxPlot.app
MAC_CONFIGS:=$(addprefix $(MAC_APP)/Contents/,Info.plist app/ECUxPlot.cfg )

build/Darwin/%: templates/Darwin/%.template build/version.txt Makefile scripts/MacOS.mk
	@echo "Generating $@"
	@mkdir -p $(dir $(MAC_CONFIGS))
	@cat $< | $(GEN) > $@

$(MAC_APP)/.stamp: runtime/Darwin/release $(ARCHIVE)
	@rm -rf $(MAC_APP)
	@$(MAKE) $(MAC_CONFIGS)
	@echo "Installing stubs to $(MAC_APP)"
	@tar -xzf templates/Darwin/stub.tar.gz
	@echo "Installing runtime to $(MACv0.9.9-rc3_APP)/Contents/runtime"
	@mkdir -p $(MAC_APP)/Contents/runtime/Contents/Home
	@rsync -a --delete runtime/Darwin/* $(MAC_APP)/Contents/runtime/Contents/Home
	@echo "Installing app to $(MAC_APP)/Contents/app"
	@tar -C $(MAC_APP)/Contents/app -xzf $(ARCHIVE) --strip-components=1
	@mkdir -p $(MAC_APP)/Contents/Resources
	@install -m 644 src/org/nyet/ecuxplot/icons/ECUxPlot$(ICON_EXT) $(MAC_APP)/Contents/Resources/
	@touch $@

$(MAC_ZIP): $(MAC_APP)/.stamp
	(cd $(dir $(MAC_APP)); zip -qr $(notdir $(MAC_ZIP)) ECUxPlot.app)

MAC_INSTALLER_DMG:=build/Darwin/$(TARGET).dmg
.PHONY: dmg latest-links-dmg rsync-dmg
dmg: $(MAC_INSTALLER_DMG)

latest-links-dmg: $(MAC_INSTALLER_DMG)
	@rm -f build/ECUxPlot-latest.dmg
	ln -sf $(notdir $(MAC_INSTALLER_DMG)) build/ECUxPlot-latest.dmg || true

rsync-dmg:
	$(MAKE) latest-links-dmg
	$(RSYNC) -at $(MAC_INSTALLER_DMG) nyet.org:public_html/cars/files/
