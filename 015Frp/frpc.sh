#!/usr/bin/bash
my_port="2$(date +%m%d)"
sed -i  "3c server_port = $my_port" /usr/local/bin/frp/frp_0.44.0_linux_amd64/frpc.ini
systemctl restart frpc.service