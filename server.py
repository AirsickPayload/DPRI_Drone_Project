#!/usr/bin/env python
# -*- coding: utf-8 -*-
import socket
import sys
import time
import thread
import os
import commands
import re
import RPi.GPIO as GPIO
from subprocess import call
clientPort = 8888
serverPort = 8887
pingPort = 8889
awaiting = closed = emergency = False
lastThrottleValue = '0=0%'
throttleEmergencyMinVal = 25
emergencyDownThrottleInterval = 0.3
# led connected by resistor to GPIO 20
# battery connected (using resistor divider [1kom-1kom]) to GPIO 16 (from white battery cable)
lowVoltageGuardSleep = 20	# sekundy
ledPin = 20
lipoPin = 16
raspiPowerPin = 35

GPIO.setmode(GPIO.BCM)
GPIO.setup(lipoPin, GPIO.IN, pull_up_down=GPIO.PUD_UP)
GPIO.setup(raspiPowerPin, GPIO.IN)
GPIO.setup(ledPin, GPIO.OUT)

def emergencyDownThrottling(placeholder1, placeholder2):
    global emergency, lastThrottleValue
    # 1=XX%
    # rozdzielenie nr pinu od wartości poprzez znak '='
    split = lastThrottleValue.split('=')
    throttlePin = split[0]
    # wydobycie wartości ze stringa
    throttleVal = split[1].split('%')
    throttleVal = int(float(throttleVal[0]))
    while throttleVal > throttleEmergencyMinVal:
        throttleVal -= 5
        call('echo ' + str(throttlePin) + '=' + str(throttleVal) + '% > ' + '/dev/servoblaster', shell=True)
        call('echo ' + 'EMERGENCY: ' + str(throttleVal) + '%', shell=True)
        time.sleep(emergencyDownThrottleInterval)
        print throttleVal
    emergency = False

def lowVoltageGuardThread(placeholder1, placeholder2):
    GPIO.output(ledPin, True)	# light up LED
    while 1:
        if not (GPIO.input(lipoPin)):
            GPIO.output(ledPin, False)	# light up LED
            thread.start_new_thread(emergencyDownThrottling, ('',''))
            break
        time.sleep(lowVoltageGuardSleep)	# sprawdzaj stopien naladowania co n sekund

def pingThreadMethod(addr, ignored):
    global closed, emergency, awaiting
    pingsocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        pingsocket.bind(('', pingPort)) # zamiast socket.gethostname() mozna '', zeby byl osiagalny ze wszystkich interfejsow
    except socket.error as msg:
        print 'PingSocket bind failed. Poprzedni wątek pingujący prawdopodobnie wciąż pracuje - OK' #+ str(msg[0]) + ' Message ' + msg[1]
        sys.exit()
    # oczekiwanie na odp. ze strony klienta nie moze trwac wiecznie
    pingsocket.settimeout(2) # max 2s
    while 1:
        # w przypadku poprawnego rozlaczenia odczekac 1.5sek przed ponowna proba polaczenia; w przeciwnym przypadku zostanie wyrzucony
        # błąd bindowania socketu - patrz try--except metody pingThreadMethod -> jeżeli w ciągu 1.5s zostanie poprawnie nawiązane połączenie
        # działający wątek nie będzie sprawiał problemów.
        time.sleep(1.5)

        # jeśli połączenie zostanie poprawnie zakończone w głównym wątku - wyłącz wątek pingujący
        if closed:
            pingsocket.close()
            break
        try:
            if awaiting:
                print 'PING?',
                pingsocket.sendto('PING?', (addr, 8889))
                # oczekiwanie na odp. ze strony klienta
                pingData, addr2 = pingsocket.recvfrom(1024)
                pingData = pingData.decode()

                if pingData == 'PONG!':
                    print ' PONG!'
                    continue
                else:
                    print 'ERROR, PING MISMATCH RESPONSE: ' + pingData
                    emergency = True
                    closed = True
                    thread.start_new_thread(emergencyDownThrottling, ('',''))
                    pingsocket.close()
                    break
        except socket.error as msg:
            emergency = True
            closed = True
            thread.start_new_thread(emergencyDownThrottling, ('',''))
            print 'PingSocket timeout - EMERGENCY!'
            pingsocket.close()
            break

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
print 'Socket created'

try:
    s.bind(('', serverPort)) # zamiast socket.gethostname() mozna '', zeby byl osiagalny ze wszystkich interfejsow
except socket.error as msg:
    print 'Bind failed. Error code: ' + str(msg[0]) + ' Message ' + msg[1]
    sys.exit()

print 'Socket bind complete'

print 'My IP : ' + commands.getoutput("hostname -I")

regexCheck = re.compile('([0-9]{1,2}=[0-9]{1,3}%,){3,3}[0-9]{1,2}=[0-9]{1,3}%')

while 1:
    # jeśli nastąpił błąd w wątku pingującym (oraz trwa downthrottling) - pomiń
    if emergency:
        continue
    # oczekiwanie na inizjalizację połączenia (string z wersją 'protokołu') ze strony klienta
    versionStringClient, addr = s.recvfrom(1024)
    print 'Connected with ' + addr[0] + ':' + str(addr[1])
    #compatibility string
    versionStringServer = 'v28/10/2015'
    versionStringClient = versionStringClient.decode()
    if versionStringClient == versionStringServer:
        print "VERSION MATCH!"
        s.sendto('VERSION MATCH', (addr[0], clientPort))
        closed = False
        thread.start_new_thread(pingThreadMethod, addr)
        thread.start_new_thread(lowVoltageGuardThread, ('',''))
        while 1:
            awaiting = True
            data, addr = s.recvfrom(1024)

            # jeśli nastąpił błąd w wątku pingującym (oraz trwa downthrottling), a pomimo tego serwer otrzymał wartość - pomiń
            if emergency:
                continue

            awaiting = False

            data = data.decode()
            #OBSLUGA SPECJALNYCH KOMEND
            if data == 'CONN_CLOSE':
                print "CLIENT CLOSED THE CONNECTION\n"
                closed = True
                break

            if data == versionStringServer:
                print "CLIENT RECONNECTED!"
                closed = False
                s.sendto('VERSION MATCH', (addr[0], clientPort))
                thread.start_new_thread(pingThreadMethod, addr)

            # OBSLUGA STANDARDOWYCH WARTOSCI
            # Sprawdzenie poprawności budowy stringa wykorzystując wyrażenie regularne.
            if regexCheck.match(data) != None:
                values = data.split(',')
                for value in values:
                    call('echo ' + value + ' > ' + '/dev/servoblaster', shell=True)
                lastThrottleValue = values[0]
                print values
                s.sendto('RECV_OK', (addr[0], clientPort))
    else:
        s.sendto('VERSION MISMATCH', (addr[0], clientPort))
        print "VERSION MISMATCH: " + versionStringClient
s.close()
