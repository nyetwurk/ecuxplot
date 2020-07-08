REFS:=4Z7907551R 8D0907551F 8D0907551G
PACKS:=$(REFS) 8D0907551H 8D0907551K 8D0907551M 4Z7907551AA 4D1907558
ZIP_BINS:=8D0907551M 4D1907558

REFS_8D0907551H=-r $(addsuffix .kp,$(REFS))
REFS_8D0907551M=-r $(addsuffix .kp,$(REFS))

TARGETS=$(addsuffix .csv,$(PACKS)) $(addsuffix .xdf,$(PACKS))

DATE_Darwin=-j -f @%s
DATE_Linux=-d
DATE_CYGWIN_NT=-d

filedate=$(shell date $(DATE_$(shell uname -s | cut -f 1 -d -)) "@$(shell git log -n1 --format=%ct $(1).kp)" +%Y%m%d)

define ZIP_template
$(1)_DATE=$$(call filedate,$(1))
.PRECIOUS: $(addprefix $(1)-$$($(1)_DATE).,kp xdf csv)
$(1)-$$($(1)_DATE).zip: $(addprefix $(1)-$$($(1)_DATE).,kp xdf csv)
$(1)-$$($(1)_DATE).kp: $(1).kp
$(1)-$$($(1)_DATE).xdf: $(1).xdf
$(1)-$$($(1)_DATE).csv: $(1).csv
$(1)-$$($(1)_DATE).%: $(1).%
	cp $$< $$@
$(1)-latest.zip: $(1)-$$($(1)_DATE).zip
	ln -sf $$< $$@
ZIPS+=$(1)-latest.zip $(1)-$$($(1)_DATE).zip
endef

$(foreach bin,$(ZIP_BINS),$(eval $(call ZIP_template,$(bin))))

.PHONY: all zips clean upload

all: $(TARGETS)

clean:
	rm -f $(TARGETS)
	rm -f $(addprefix 8D0907551M-*.,csv xdf kp zip)

%.csv: %.kp %.bin ../mapdump.jar
	../mapdump.sh $(REFS_$(*)) -i $*.bin $< $@

%.xdf: %.kp ../mapdump.jar
	../mapdump.sh -i $*.bin -x $< $@

%.zip: %.kp %.xdf %.csv
	zip $@ $^

zips: $(ZIPS)

upload: zips
	rsync -l $(ZIPS) stupid:/var/www/s4wiki-files/public/defs/
