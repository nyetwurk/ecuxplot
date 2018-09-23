MAC_TYPE:=pkg
MAC_INSTALLER=$(TARGET).$(MAC_TYPE)

ICON=MacOS.data/ECUxPlot.icns
SRCFILES=$(notdir $(INSTALL_FILES)) $(PROFILES)

mac-install: $(MAC_INSTALLER)
	sudo installer -pkg $(MAC_INSTALLER) -target /

$(MAC_INSTALLER): $(ICON) $(INSTALL_FILES) $(PROFILES) MacOS.data/package/macosx/Info.plist scripts/MacOS.mk
	@rm -rf build/ECUxPlot.app
	#@mkdir -p build/ECUxPlot.app
	#install -m 644 $(INSTALL_FILES) build/ECUxPlot.app
	#rsync -aR $(PROFILES) build/ECUxPlot.app/
	cp -f build/version.txt version.txt
	javapackager -deploy -native $(MAC_TYPE) \
	    -name ECUxPlot \
	    -title ECUxPlot \
	    -appclass org.nyet.ecuxplot.ECUxPlot \
	    -BappVersion=$(ECUXPLOT_VER) \
	    -Bicon=$(ICON) \
	    -Bmac.category=public.app-category.utility \
	    -BdropinResourcesRoot=MacOS.data \
	    -nosign -verbose \
	    -srcdir . \
	    -srcfiles $(subst $() $(),:,$(SRCFILES)) \
	    -outdir . -outfile $(TARGET)
	rm -f version.txt
