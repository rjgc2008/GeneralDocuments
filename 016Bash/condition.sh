#!/usr/bin/bash
if [ "$1" = "hi" ]; then
     echo 'The first argument was "hi"'
else
     echo -n 'The first arguemnt was not "hi" --'
     echo It was '"'"$1"'"'
fi
