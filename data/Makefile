PACKS=4Z7907551R 8D0907551F 8D0907551G

TARGETS=$(addsuffix .csv,$(PACKS)) $(addsuffix .xdf,$(PACKS))

all: $(TARGETS)

clean:
	rm -f $(TARGETS)

%.csv: %.kp %.bin ../mapdump.class
	(cd ..; ./mapdump -i data/$*.bin data/$< > data/$@)

%.xdf: %.kp ../mapdump.class
	(cd ..; ./mapdump -i data/$*.bin -x data/$< > data/$@)
