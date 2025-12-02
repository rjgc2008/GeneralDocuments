#!/usr/bin/bash
count=$(sudo who | grep "root" | grep "pts"|  wc -l)
status=$(sudo systemctl status v2ray.service | grep "running" | wc -l)

echo $count
echo $status

if [ $count != 0 ]
then
    $(sudo systemctl stop v2ray.service)
elif [ $status != 1 ]
then
    $(sudo systemctl start v2ray.service)
fi