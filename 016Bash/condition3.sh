#!/usb/bin/bash
if [ "$1" = "hi" ]; then
     # if body
     echo 'The first argument was "hi"'
elif [ "$2" = "bye" ]; then
     # else if body
     echo 'The first argument was "bye"'
else
     # else body
     echo -n 'The first argument was not "hi" and the second argument was not "bye" '
     echo  They were '"'"$1"'"' and '"'"$2"'"'
fi
