#!/bin/bash

while true ; do
   if ifconfig wlan0 | grep -q "inet addr:" ; then
      sleep 1
   else
      echo "Network connection down! Attempting reconnection."
      echo "0=0%" > /dev/servoblaster
      ifup --force wlan0
      sleep 5
   fi
done
