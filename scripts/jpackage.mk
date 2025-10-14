APP_EXT_Linux :=
APP_EXT_CYGWIN_NT :=
APP_EXT_Darwin := .app
APP_EXT := $(APP_EXT_$(UNAME))

ICON_EXT_Linux := .png
ICON_EXT_CYGWIN_NT := .ico
ICON_EXT_Darwin := .icns
ICON_EXT := $(ICON_EXT_$(UNAME))

UNAMES:=Darwin CYGWIN_NT # Linux # Linux runtime is broken
RUNTIMES:=$(addprefix runtime/,$(addsuffix /release,$(UNAMES)))
OTHER_UNAMES:=$(filter-out $(UNAME),$(UNAMES))
OTHER_RUNTIMES:=$(addprefix runtime/,$(addsuffix /release,$(OTHER_UNAMES)))

.PHONY: runtimes runtime-archive
runtimes: $(RUNTIMES)

runtime/%/release:
	@mkdir -p runtime
	wget -c https://nyet.org/cars/ECUxPlot/jre/runtime-$*-latest.tar.gz -O runtime/runtime-$*-latest.tar.gz
	tar xzf runtime/runtime-$*-latest.tar.gz

#JLINK_MODULES:=ALL-MODULE-PATH
JLINK_MODULES:=java.base,java.desktop,java.datatransfer

MY_RUNTIME:=runtime/$(UNAME)
$(MY_RUNTIME)/release:
	@rm -rf $(MY_RUNTIME)
	"$(JAVA_HOME)/bin/jlink" --no-header-files --no-man-pages \
		--compress=2 --strip-debug --add-modules $(JLINK_MODULES) \
		--output $(MY_RUNTIME)

runtime-archive runtime/runtime-$(UNAME)-$(JAVAC_VER).tar.gz: $(MY_RUNTIME)/release
	tar czf runtime/runtime-$(UNAME)-$(JAVAC_VER).tar.gz $(MY_RUNTIME)

ifeq ($(UNAME),Darwin)
.PHONY: stub-archive
STUB_DIR:=build/Darwin/ECUxPlot.app
STUB_FILES:=MacOS PkgInfo runtime/Contents/MacOS runtime/Contents/Info.plist
stub-archive templates/Darwin/stub.tar.gz: $(STUB_DIR)
	tar czf templates/Darwin/stub.tar.gz $(addprefix $(STUB_DIR)/Contents/,$(STUB_FILES))
endif

# Note: --app-version can't have dashes in windows
PACKAGER_OPTS:=\
    --name ECUxPlot \
    --description "ECUxPlot $(ECUXPLOT_VER)" \
    --app-version $(VERSION) \
    --dest build/$(UNAME)

# Not supported on windows or linux(?) in app
PACKAGER_APP_OPTS_Darwin:=--file-associations scripts/assoc.prop

build/$(UNAME)/ECUxPlot$(APP_EXT): $(ARCHIVE) $(MY_RUNTIME)/release
	@mkdir -p build/ECUxPlot; rm -rf build/ECUxPlot build/$(UNAME)/ECUxPlot$(APP_EXT)
	tar -C build -xzf $(ARCHIVE)
	"$(JAVA_HOME)/bin/jpackage" $(PACKAGER_OPTS) $(PACKAGER_APP_OPTS_$(UNAME)) --type app-image \
	    --input build/ECUxPlot \
	    --icon src/org/nyet/ecuxplot/icons/ECUxPlot$(ICON_EXT) \
	    --main-jar $(TARGET).jar \
	    --main-class org.nyet.ecuxplot.ECUxPlot \
	    --runtime-image $(MY_RUNTIME)

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
	mv ECUxPlot-$(VERSION).dmg ../$(notdir $(MAC_INSTALLER)) && \
	rm -rf dmg-temp
