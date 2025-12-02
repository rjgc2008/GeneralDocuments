dsadd user "CN=rita20,OU=IT,DC=tgqs,DC=org" -samid rita20 -upn rita20@tgqs.org -display rita20 -disabled yes
dsmod user "CN=rita03,OU=IT,DC=tgqs,DC=org" -upn rita03modify@tgqs.org -pwd 111aaAA@ -tel 12345678
dsrm "CN=rita02,OU=IT,DC=tgqs,DC=org" -noprompt
pause