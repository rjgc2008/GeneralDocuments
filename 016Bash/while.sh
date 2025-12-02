#!/usr/bin/bash
FILE=/tmp/whiletest.$$;
echo firstline > $FILE

while tail -10 $FILE | grep -q firstline ; do
    # body
    echo Number of lines in $FILE:' '
    echo newline >> $FILE
done
rm $FILE
