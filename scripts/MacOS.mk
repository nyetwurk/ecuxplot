MAC_ZIP:=build/$(TARGET)-MacOS.zip

MAC_APP:=build/Darwin/ECUxPlot.app
MAC_CONFIGS:=$(addprefix $(MAC_APP)/Contents/,Info.plist app/ECUxPlot.cfg )

build/Darwin/%: templates/Darwin/%.template build/version.txt Makefile scripts/MacOS.mk
	@echo "Generating $@"
	@mkdir -p $(dir $(MAC_CONFIGS))
	@cat $< | $(GEN) > $@

$(MAC_APP)/.stamp: $(ARCHIVE)
	@rm -rf $(MAC_APP)
	@$(MAKE) $(MAC_CONFIGS)
	@echo "Installing app to $(MAC_APP)/Contents/app"
	@tar -C $(MAC_APP)/Contents/app -xzf $(ARCHIVE) --strip-components=1
	@mkdir -p $(MAC_APP)/Contents/Resources
	@install -m 644 src/org/nyet/ecuxplot/icons/ECUxPlot$(ICON_EXT) $(MAC_APP)/Contents/Resources/
	@touch $@

$(MAC_ZIP): $(MAC_APP)/.stamp
	(cd $(dir $(MAC_APP)); zip -qr ../$(notdir $(MAC_ZIP)) ECUxPlot.app)

MAC_INSTALLER:=build/$(TARGET).dmg
.PHONY: dmg latest-links-dmg rsync-dmg
dmg: $(MAC_INSTALLER)

latest-links-dmg: $(MAC_INSTALLER)
	@rm -f build/ECUxPlot-latest.dmg
	ln -sf $(notdir $(MAC_INSTALLER)) build/ECUxPlot-latest.dmg || true

rsync-dmg:
	$(MAKE) latest-links-dmg
	$(RSYNC) -at $(MAC_INSTALLER) nyet.org:public_html/cars/files/
