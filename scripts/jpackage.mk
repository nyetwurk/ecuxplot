APP_EXT_Linux :=
APP_EXT_CYGWIN_NT :=
APP_EXT_Darwin := .app
APP_EXT := $(APP_EXT_$(UNAME))

ICON_EXT_Linux := .png
ICON_EXT_CYGWIN_NT := .ico
ICON_EXT_Darwin := .icns
ICON_EXT := $(ICON_EXT_$(UNAME))

UNAMES:=Linux Darwin CYGWIN_NT

# Platform-specific file extensions and names
FILE_EXT_Linux:=tar.gz
FILE_EXT_CYGWIN_NT:=zip
FILE_EXT_Darwin:=tar.gz
PLATFORM_NAME_Linux:=linux
PLATFORM_NAME_CYGWIN_NT:=windows
PLATFORM_NAME_Darwin:=mac

#JLINK_MODULES:=ALL-MODULE-PATH
JLINK_MODULES:=java.base,java.desktop,java.datatransfer

# Mark stamp files as precious so Make doesn't delete them
.PRECIOUS: runtime/%/java-$(JAVA_TARGET_VER).stamp

# Create version stamp file for a specific platform
runtime/%/java-$(JAVA_TARGET_VER).stamp:
	@mkdir -p runtime/$*
	@touch runtime/$*/java-$(JAVA_TARGET_VER).stamp

# Download JDK for a specific platform
runtime/jdk-%.$(FILE_EXT_%): runtime/%/java-$(JAVA_TARGET_VER).stamp
	@echo "Downloading $* JDK..."
	@mkdir -p runtime
	@LATEST_VERSION=$$(curl -s \
		"https://api.github.com/repos/adoptium/temurin$(JAVA_TARGET_VER)-binaries/releases/latest" \
		| grep -E '"tag_name"' | cut -d'"' -f4); \
	echo "Latest version: $$LATEST_VERSION"; \
	FILE_VERSION=$$(echo $$LATEST_VERSION | sed 's/jdk-//' | sed 's/+/_/'); \
	wget -O runtime/jdk-$*.$(FILE_EXT_$*) \
		"https://github.com/adoptium/temurin$(JAVA_TARGET_VER)-binaries/releases/download/$$LATEST_VERSION/OpenJDK$(JAVA_TARGET_VER)U-jdk_x64_$(PLATFORM_NAME_$*)_hotspot_$$FILE_VERSION.$(FILE_EXT_$*)"

# Create runtime for a specific platform
runtime/%/release: runtime/%/java-$(JAVA_TARGET_VER).stamp
	@echo "Creating runtime for $*..."
	@mkdir -p runtime
	scripts/download-jre.sh $(JAVA_TARGET_VER) $*

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

build/$(UNAME)/ECUxPlot$(APP_EXT): $(ARCHIVE) runtime/$(UNAME)/release runtime/$(UNAME)/java-$(JAVA_TARGET_VER).stamp
	@mkdir -p build/ECUxPlot; rm -rf build/ECUxPlot build/$(UNAME)/ECUxPlot$(APP_EXT)
	tar -C build -xzf $(ARCHIVE)
	"$(JAVA_HOME)/bin/jpackage" $(PACKAGER_OPTS) $(PACKAGER_APP_OPTS_$(UNAME)) --type app-image \
	    --input build/ECUxPlot \
	    --icon src/org/nyet/ecuxplot/icons/ECUxPlot$(ICON_EXT) \
	    --main-jar $(TARGET).jar \
	    --main-class org.nyet.ecuxplot.ECUxPlot \
	    --runtime-image runtime/$(UNAME)

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
