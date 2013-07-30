PACKS=4Z7907551R 8D0907551F 8D0907551G 8D0907551H 8D0907551M

TARGETS=$(addsuffix .csv,$(PACKS)) $(addsuffix .xdf,$(PACKS))
DATE=$(shell date +%Y%m%d)

all: $(TARGETS)

zip: 8D0907551M-$(DATE).zip

clean:
	rm -f $(TARGETS)

%.csv: %.kp %.bin ../mapdump.jar
	../mapdump.sh -i $*.bin $< > $@

%.xdf: %.kp ../mapdump.jar
	../mapdump.sh -i $*.bin -x $< > $@

%-$(DATE).kp: %.kp
	cp $< $@
%-$(DATE).xdf: %.xdf
	cp $< $@
%-$(DATE).csv: %.xdf
	cp $< $@

%.zip: %.kp %.xdf %.csv
	zip $@ $^
