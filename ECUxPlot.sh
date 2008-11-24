#!/bin/sh
set -e
PROG=`readlink -m "$0"`
DIR=`dirname "$PROG"`
java -Dawt.useSystemAAFontSettings=on -jar $DIR/ECUxPlot-`cat $DIR/version.txt`.jar "$@"
