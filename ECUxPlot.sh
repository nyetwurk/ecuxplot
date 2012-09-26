#!/bin/bash
set -e
PROG=`readlink -m "$0" 2>/dev/null || true`
[ -z "$PROG" ] && PROG=$0
DIR=`dirname "$PROG"`

for i in build/version.txt version.txt; do
    [ -r "$DIR/$i" ] && VER=`cat "$DIR/$i"`
done

exec java -Dawt.useSystemAAFontSettings=on -jar "$DIR/ECUxPlot-$VER.jar" "$@"
