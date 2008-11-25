#!/bin/sh
set -e
PROG=`readlink -m "$0" 2>/dev/null || true`
[ -z "$PROG" ] && PROG=$0
DIR=`dirname "$PROG"`
java -Dawt.useSystemAAFontSettings=on -jar $DIR/ECUxPlot-`cat $DIR/version.txt`.jar "$@"
