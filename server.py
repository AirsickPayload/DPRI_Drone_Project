import socket
import sys
import time
import thread
import os
from subprocess import call
clientPort = 8888
serverPort = 8887
pingPort = 8889
awaiting = closed = emergency = False
lastThrottleValue = '0=0%'
throttleEmergencyMinVal = 25

def emergencyDownThrottling(placeholder1, placeholder2):
    #1=XX%
    split = lastThrottleValue.split('=')
    throttlePin = split[0]
    throttleVal = split[1].split('%')
    throttleVal = int(float(throttleVal[0]))
    while throttleVal > throttleEmergencyMinVal:
        throttleVal -= 5
        print throttleVal
        call('echo ' + str(throttlePin) + '=' + ' > ' + '/dev/servoblaster', shell=True)
        time.sleep(0.3)
    emergency = False

def pingThreadMethod(addr, ignored):
    global closed
    pingsocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        pingsocket.bind(('', pingPort)) # zamiast socket.gethostname() mozna '', zeby byl osiagalny ze wszystkich interfejsow
    except socket.error as msg:
        print 'PingSocket bind failed. Error code: ' + str(msg[0]) + ' Message ' + msg[1]
        sys.exit()
    #oczekiwanie na odp. ze strony klienta nie moze trwac wiecznie
    pingsocket.settimeout(2) # max 2s
    while 1:
        time.sleep(1.5)
        if closed:
            continue
        try:
            if awaiting:
                print 'PING?'
                pingsocket.sendto('PING?', (addr, 8889))
                pingData, addr2 = pingsocket.recvfrom(1024)
                pingData = pingData.decode()

                if pingData == 'PONG!':
                    print ' PONG!'
                    continue
                else:
                    print 'ERROR: ' + pingData
                    emergency = True
                    break
            if closed:
                break
        except socket.error as msg:
            emergency = True
            closed = True
            thread.start_new_thread(emergencyDownThrottling, ('',''))
            print 'PingSocket timeout - EMERGENCY!'

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
print 'Socket created'

try:
    s.bind(('', serverPort)) # zamiast socket.gethostname() mozna '', zeby byl osiagalny ze wszystkich interfejsow
except socket.error as msg:
    print 'Bind failed. Error code: ' + str(msg[0]) + ' Message ' + msg[1]
    sys.exit()

print 'Socket bind complete'

while 1:
    versionStringClient, addr = s.recvfrom(1024)
    print 'Connected with ' + addr[0] + ':' + str(addr[1])
    #compatibility string
    versionStringServer = 'v20/10/2015'
    versionStringClient = versionStringClient.decode()
    if versionStringClient == versionStringServer:
        print "VERSION MATCH!"
        s.sendto('VERSION MATCH', (addr[0], clientPort))
        thread.start_new_thread(pingThreadMethod, addr)
        while 1:
            if emergency:
                continue

            awaiting = True
            data, addr = s.recvfrom(1024)
            awaiting = False

            data = data.decode()
            #OBSLUGA SPECJALNYCH KOMEND
            if data == 'CONN_CLOSE':
                print "CLIENT CLOSED CONNECTION\n"
                closed = True
                break

            if data == versionStringServer:
                print "CLIENT RECONNECTED!"
                closed = False
                s.sendto('VERSION MATCH', (addr[0], clientPort))
                #thread.start_new_thread(pingThreadMethod, addr)

            #OBSLUGA STANDARDOWYCH WARTOSCI
            values = data.split(',')
            #for value in values:
                #call('echo ' + value + ' > ' + '/dev/servoblaster', shell=True)
            lastThrottleValue = values[0]
            print values
            s.sendto('RECV_OK', (addr[0], clientPort))

    else:
        s.sendto('VERSION MISMATCH', (addr[0], clientPort))
        print "VERSION MISMATCH: " + versionStringClient
s.close()
