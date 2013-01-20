#!/bin/bash
set -e

PROG=$(readlink -m "$0" 2>/dev/null || true)
[ -z "$PROG" ] && PROG=$0
DIR=`dirname "$PROG"`

jar=$DIR/mapdump.jar

args=()
if [ ! -z $(which cygpath) ]; then
    jar=$(cygpath -w $jar)
    for arg in "$@"; do
	if [ -r "$arg" ]; then
	    args+=($(cygpath -w "$arg"))
	else
	    args+=($arg)
	fi
    done
else
    args=$@
fi

exec java -jar "$jar" "${args[@]}"
