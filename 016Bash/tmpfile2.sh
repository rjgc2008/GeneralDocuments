#!/usr/bin/bash
TMPFILE1=$(mktemp /tmp/im1.XXXXXX)
TMPFILE2=$(mktemp /tmp/im2.XXXXXX)
trap "rm -f $TMPFILE1 $TMPFILE2" INT

cat /proc/interrupts > "$TMPFILE1"
sleep 2
cat /proc/interrupts > "$TMPFILE2"
diff "$TMPFILE1" "$TMPFILE2"
rm -f "$TMPFILE1" "$TMPFILE2"