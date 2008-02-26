TARGETS=4Z7907551R.csv 8D0907551F.csv

all: $(TARGETS)

clean:
	rm -f $(TARGETS)

%.csv: %.kp %.bin ../mapdump.class
	(cd ..; ./mapdump -i data/$*.bin data/$< > data/$@)
