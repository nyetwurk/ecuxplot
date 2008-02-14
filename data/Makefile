TARGETS=4Z7907551R.csv 8D0907551K.csv

all: $(TARGETS)

clean:
	rm -f $(TARGETS)

%.csv: %.kp %.bin
	(cd ..; ./mapdump -i data/$*.bin data/$< > data/$@)
