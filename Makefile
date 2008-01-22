MP_SOURCES=HexValue.java Map.java Parser.java Parse.java \
	ParserException.java Project.java

LF_SOURCES=Dataset.java

MP_CLASSES=$(MP_SOURCES:%.java=MapPack/%.class)
LF_CLASSES=$(LF_SOURCES:%.java=LogFile/%.class)

TARGETS=mapdump.class ECUxPlot.class
REFERENCE=data/4Z7907551R.kp

all: $(TARGETS)
clean:
	rm *.class

%.csv: %.kp mapdump
	./mapdump -r $(REFERENCE) $< > $@

mapdump.class: mapdump.java $(MP_CLASSES)
ECUxPlot.class: ECUxPlot.java WindowUtilities.class ExitListener.class $(LF_CLASSES)

%.class: %.java
	javac -classpath '.;MapPack;LogFile;jcommon-1.0.12.jar;jfreechart-1.0.9.jar;opencsv-1.8.jar' $<
