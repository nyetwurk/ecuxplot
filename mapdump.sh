#!/bin/sh
set -e
# export LANG=en_US.iso885915
PROG=`readlink -m "$0" 2>/dev/null || true`
[ -z "$PROG" ] && PROG=$0
DIR=`dirname "$PROG"`
exec java -jar $DIR/mapdump.jar $@
