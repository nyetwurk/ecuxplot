SOURCES=HexValue.java Map.java MapPackParser.java Parse.java \
	ParserException.java Project.java SmartIntArray.java

CLASSES=$(SOURCES:%.java=%.class)
TARGET=mapdump.class

all: $(TARGET)

mapdump.class: mapdump.java $(CLASSES)

%.class: %.java
	javac $<
