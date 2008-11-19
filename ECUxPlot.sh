#!/bin/sh
set -e
DIR=`dirname "$0"`
java -Dawt.useSystemAAFontSettings=on -jar $DIR/ECUxPlot-`cat $DIR/version.txt`.jar "$@"
