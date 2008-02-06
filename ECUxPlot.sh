#!/bin/sh
if [ -r `dirname $0`/.classpath ]; then
    source `dirname $0`/.classpath
fi
java -jar ECUxPlot-`cat version.txt`.jar "$*"
