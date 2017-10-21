#!/bin/bash
path="$*"
IFS=$':'
for i in $path; do
    if [ -r "$i/rt.jar" ]; then
	echo "$i/rt.jar"
	break
    fi
done
unset IFS
