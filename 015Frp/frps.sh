#!/usr/bin/bash
my_port="2$(date +%m%d)"
echo "$my_port"
sed -i  "2c bind_port = $my_port" /usr/local/bin/frp/frp_0.44.0_linux_arm/frps.ini
systemctl restart frps.service