#!/usr/bin/bash
if grep -q daemon /etc/passwd; then
     echo find '"daemon"' user
else
     echo not find '"daemon"' user
fi  
