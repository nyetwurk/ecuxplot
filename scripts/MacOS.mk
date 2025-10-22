MAC_ZIP:=build/$(TARGET)-MacOS.zip

# Development version of the app without a bundled runtime (stripped from jpackage)
# TODO: Issue #55 - Investigate building macos-bare.app w/o jpackager (regression)
# Current implementation strips runtime from jpackage output, but there may be
# a way to build the bare app directly without jpackager dependency
MAC_BARE_NAME:=ECUxPlot-bare.app
MAC_APP:=build/Darwin/$(MAC_BARE_NAME)

$(MAC_APP)/.stamp: build/$(UNAME)/ECUxPlot.app
	@echo "Creating stripped app from jpackage output"
	@rm -rf $(MAC_APP)
	@cp -R build/$(UNAME)/ECUxPlot.app $(MAC_APP)
	@echo "Stripping Java runtime from $(MAC_APP)"
	@rm -rf $(MAC_APP)/Contents/runtime
	@echo "Moving app to replace MacOS"
	@rm -rf $(MAC_APP)/Contents/MacOS
	@mv $(MAC_APP)/Contents/app $(MAC_APP)/Contents/MacOS
	@echo "Creating entrypoint symlink to the shell script"
	@ln -s ECUxPlot.sh $(MAC_APP)/Contents/MacOS/ECUxPlot
	@echo "Stripped app created: $(MAC_APP)"
	@touch $@

# zip the bare app without the runtime
$(MAC_ZIP): $(MAC_APP)/.stamp
	(cd $(dir $(MAC_APP)); zip -qr ../$(notdir $(MAC_ZIP)) $(MAC_BARE_NAME))

MAC_INSTALLER:=build/$(TARGET).dmg
.PHONY: dmg latest-links-dmg rsync-dmg
dmg: $(MAC_INSTALLER)

latest-links-dmg: $(MAC_INSTALLER)
	@rm -f build/ECUxPlot-latest.dmg
	ln -sf $(notdir $(MAC_INSTALLER)) build/ECUxPlot-latest.dmg || true

rsync-dmg:
	$(MAKE) latest-links-dmg
	$(RSYNC) -at $(MAC_INSTALLER) nyet.org:public_html/cars/files/
