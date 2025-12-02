#!/usr/bin/bash
# value=$(<1.txt)
# echo "$value"

file='1.txt'
# i=1
iptables -F BLACKIPS

while read line; do
    # body
    # echo "Line NO. $i : $line"
    # cmds="iptables -I BLACKIPS -s $line -j DROP"
    iptables -I BLACKIPS -s "$line" -j DROP
    # country=$(whois "$line" | grep "country")
    # country=$(curl cip.cc/"$line" -s | sed -n '2p' | awk '{print $3}')
    # sleep 2
    # echo "$line" : "$country" >> 2.txt
    # echo "commands is $cmds"
    # i=$((i+1))
done < $file
iptables -A BLACKIPS -j RETURN
