APP_EXT_Linux :=
APP_EXT_CYGWIN_NT :=
APP_EXT_Darwin := .app
APP_EXT := $(APP_EXT_$(UNAME))

ICON_EXT_Linux := .png
ICON_EXT_CYGWIN_NT := .ico
ICON_EXT_Darwin := .icns
ICON_EXT := $(ICON_EXT_$(UNAME))

# Platform-specific file extensions and names
FILE_EXT_Linux:=tar.gz
FILE_EXT_CYGWIN_NT:=zip
FILE_EXT_Darwin:=tar.gz
PLATFORM_NAME_Linux:=linux
PLATFORM_NAME_CYGWIN_NT:=windows
PLATFORM_NAME_Darwin:=mac

# Download JRE if stamp file doesn't exist or is wrong version
runtime/%/release runtime/%/java-$(JAVA_TARGET_VER).stamp:
	@echo "Downloading JRE for $*..."
	@mkdir -p runtime
	scripts/download-jre.sh $(JAVA_TARGET_VER) $*


# Note: --app-version can't have dashes in windows
PACKAGER_OPTS:=\
    --name ECUxPlot \
    --description "ECUxPlot $(ECUXPLOT_VER)" \
    --app-version $(VERSION) \
    --dest build/$(UNAME)

# Not supported on windows or linux(?) in app
PACKAGER_APP_OPTS_Darwin:=--file-associations scripts/assoc.prop

.PHONY: sanity-check
sanity-check build/$(UNAME)/ECUxPlot$(APP_EXT): $(ARCHIVE) runtime/$(UNAME)/release runtime/$(UNAME)/java-$(JAVA_TARGET_VER).stamp
	@mkdir -p build/ECUxPlot; rm -rf build/ECUxPlot build/$(UNAME)/ECUxPlot$(APP_EXT)
	tar -C build -xzf $(ARCHIVE)
	"$(JAVA_HOME)/bin/jpackage" $(PACKAGER_OPTS) $(PACKAGER_APP_OPTS_$(UNAME)) --type app-image \
	    --input build/ECUxPlot \
	    --icon src/org/nyet/ecuxplot/icons/ECUxPlot$(ICON_EXT) \
	    --main-jar $(TARGET).jar \
	    --main-class org.nyet.ecuxplot.ECUxPlot \
	    --runtime-image runtime/$(UNAME)
	@echo "Running sanity check after jpackage..."
	@./scripts/sanity-check.sh jpackage build/$(UNAME)/ECUxPlot$(APP_EXT)
	@echo "Installing runtime after jpackage..."
	@rsync -a runtime/$(UNAME)/Contents/Home/ build/$(UNAME)/ECUxPlot$(APP_EXT)/Contents/runtime/
	@echo "Running sanity check after runtime installation..."
	@./scripts/sanity-check.sh runtime build/$(UNAME)/ECUxPlot$(APP_EXT)

$(MAC_INSTALLER): build/$(UNAME)/ECUxPlot$(APP_EXT)
	@if [ "$(UNAME)" != "Darwin" ]; then \
		echo "Error: DMG creation only supported on macOS (Darwin), current platform: $(UNAME)"; \
		exit 1; \
	fi
	rm -rf build/$(UNAME)/*.dmg build/$(UNAME)/dmg-temp
	# Create proper installer DMG with Applications folder
	mkdir -p build/$(UNAME)/dmg-temp
	cp -R build/$(UNAME)/ECUxPlot$(APP_EXT) build/$(UNAME)/dmg-temp/
	ln -s /Applications build/$(UNAME)/dmg-temp/Applications
	cd build/$(UNAME) && \
		hdiutil create -srcfolder dmg-temp \
			-volname "ECUxPlot $(VERSION)" \
			-fs HFS+ \
			-fsargs "-c c=64,a=16,e=16" \
			-format UDZO \
			ECUxPlot-$(VERSION).dmg && \
		rm -rf dmg-temp
	@mv build/$(UNAME)/ECUxPlot-$(VERSION).dmg $(MAC_INSTALLER)
	@echo "moved to: $(MAC_INSTALLER)"
