MP_SOURCES=HexValue.java Map.java Parser.java Parse.java \
	ParserException.java Project.java

SOURCES=$(addprefix MapPack/,$(MP_SOURCES))

CLASSES=$(SOURCES:%.java=%.class)
TARGETS=mapdump.class csvdump.class
REFERENCE=data/4Z7907551R.kp

all: $(TARGETS)
clean:
	rm *.class

%.csv: %.kp mapdump
	./mapdump -r $(REFERENCE) $< > $@

mapdump.class: mapdump.java $(CLASSES)
csvdump.class: csvdump.java

%.class: %.java
	javac -classpath 'MapPack;opencsv-1.8.jar' $<
