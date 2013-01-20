#!/bin/bash
set -e

PROG=$(readlink -m "$0" 2>/dev/null || true)
[ -z "$PROG" ] && PROG=$0
DIR=`dirname "$PROG"`

for i in build/version.txt version.txt; do
    [ -r "$DIR/$i" ] && VER=$(cat "$DIR/$i")
done

jar=$DIR/ECUxPlot-$VER.jar

args=()
if [ ! -z $(which cygpath) ]; then
    jar=$(cygpath -w $jar)
    for arg in "$@"; do
	if [ -r "$arg" ]; then
	    args+=($(cygpath -w "$arg"))
	else
	    args+=$arg
	fi
    done
else
    args=$@
fi

# echo exec java -Dawt.useSystemAAFontSettings=on -jar "$jar" "${args[@]}"
exec java -Dawt.useSystemAAFontSettings=on -jar "$jar" "${args[@]}"
