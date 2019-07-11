REFS=4Z7907551R 8D0907551F 8D0907551G
PACKS=$(REFS) 8D0907551H 8D0907551K 8D0907551M 4Z7907551AA

REFS_8D0907551H=-r $(addsuffix .kp,$(REFS))
REFS_8D0907551M=-r $(addsuffix .kp,$(REFS))

TARGETS=$(addsuffix .csv,$(PACKS)) $(addsuffix .xdf,$(PACKS))
DATE=$(shell date -d "$(shell stat -c %y 8D0907551M.kp)" +%Y%m%d)

.PHONY: all zip clean
.PRECIOUS: $(addprefix %-$(DATE).,csv xdf kp)

all: $(TARGETS)

zip: 8D0907551M-$(DATE).zip

clean:
	rm -f $(TARGETS)
	rm -f $(addprefix 8D0907551M-*.,csv xdf kp zip)

%.csv: %.kp %.bin ../mapdump.jar
	../mapdump.sh $(REFS_$(*)) -i $*.bin $< $@

%.xdf: %.kp ../mapdump.jar
	../mapdump.sh -i $*.bin -x $< $@

%-$(DATE).kp: %.kp
	cp $< $@
%-$(DATE).xdf: %.xdf
	cp $< $@
%-$(DATE).csv: %.csv
	cp $< $@

%.zip: %.kp %.xdf %.csv
	zip $@ $^

upload: zip
	ln -sf 8D0907551M-$(DATE).zip 8D0907551M-latest.zip
	rsync -l 8D0907551M-$(DATE).* 8D0907551M-latest.zip stupid:/var/www/s4wiki-files/public/defs/
