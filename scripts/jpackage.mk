.PHONY: runtime-archive
#JLINK_MODULES:=ALL-MODULE-PATH
JLINK_MODULES:=java.base,java.desktop,java.datatransfer
runtime/$(UNAME)/release:
	@rm -rf runtime/$(UNAME)
	"$(JAVA_HOME)/bin/jlink" --no-header-files --no-man-pages --compress=2 --strip-debug --add-modules $(JLINK_MODULES) --output runtime/$(UNAME)

runtime-archive runtime-$(UNAME)-$(JAVAC_VER).tar.gz: runtime/$(UNAME)/release
	tar czf runtime-$(UNAME)-$(JAVAC_VER).tar.gz runtime/$(UNAME)

.PHONY: jpackage jpackage-installer
# Note: --app-version can't have dashes in windows
PACKAGER_OPTS:=\
    --name ECUxPlot \
    --description "ECUxPlot $(ECUXPLOT_VER)" \
    --app-version $(VERSION) \
    --dest build/$(UNAME)

# Not supported on windows or linux(?) in app
PACKAGER_APP_OPTS_Darwin:=--file-associations scripts/assoc.prop

jpackage build/$(UNAME)/ECUxPlot$(APP_EXT): $(TARGET).jar mapdump.jar runtime/$(UNAME)/release
	@mkdir -p build/ECUxPlot; rm -rf build/ECUxPlot build/$(UNAME)/ECUxPlot$(APP_EXT)
	@rsync --del -aR $(INSTALL_FILES) $(PROFILES) build/ECUxPlot
	"$(JAVA_HOME)/bin/jpackage" $(PACKAGER_OPTS) $(PACKAGER_APP_OPTS_$(UNAME)) --type app-image \
	    --input build/ECUxPlot \
	    --icon src/org/nyet/ecuxplot/icons/ECUxPlot$(ICON_EXT) \
	    --main-jar $(TARGET).jar \
	    --main-class org.nyet.ecuxplot.ECUxPlot \
	    --runtime-image runtime/$(UNAME)

jpackage-installer: build/$(UNAME)/ECUxPlot$(APP_EXT)
	"$(JAVA_HOME)/bin/jpackage" $(PACKAGER_OPTS) --app-image build/$(UNAME)
