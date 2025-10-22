#!/bin/bash
set -e

# Note: can't use readlink -m on MacOS
PROG=$(realpath "$0" 2>/dev/null || true)
[ -z "$PROG" ] && PROG=$0
DIR=`dirname "$PROG"`

jar=$DIR/ECUxPlot.jar

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
