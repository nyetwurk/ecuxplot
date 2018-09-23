#!/bin/bash
set -e

PROG=$(readlink -m "$0" 2>/dev/null || true)
[ -z "$PROG" ] && PROG=$0
DIR=`dirname "$PROG"`

for i in build/version.txt version.txt; do
    [ -r "$DIR/$i" ] && VER=$(cat "$DIR/$i")
done

jar=$DIR/ECUxPlot-$VER.jar

    IFS=$','
if [ ! -z $(which cygpath) ]; then
    args=()
    jar=$(cygpath -w $jar)
    for arg in "$@"; do
	if [ -r "$arg" ]; then
	    arg=($(cygpath -w "$arg"))
	fi
	args+=($arg)
    done
else
    args=($@)
fi

#echo exec java -jar $jar ${args[@]}
exec java -Dawt.useSystemAAFontSettings=gasp -jar $jar ${args[@]}
