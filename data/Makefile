PACKS=4Z7907551R 8D0907551F 8D0907551G 8D0907551H 8D0907551M

TARGETS=$(addsuffix .csv,$(PACKS)) $(addsuffix .xdf,$(PACKS))

all: $(TARGETS)

clean:
	rm -f $(TARGETS)

%.csv: %.kp %.bin ../mapdump.jar
	../mapdump.sh -i $*.bin $< > $@

%.xdf: %.kp ../mapdump.jar
	../mapdump.sh -i $*.bin -x $< > $@
