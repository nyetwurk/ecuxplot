#!/usr/bin/perl -w

use strict;

while(<>) {
	my ($magic, $minor, $major) = unpack("Nnn", $_);
	printf("%x %02d.%02d\n", $magic, $major, $minor);
	exit(0);
}
