import socket
import sys
import os
from subprocess import call

PORT = 8887

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
print 'Socket created'

# Bind socket
try:
    s.bind(('', PORT)) # zamiast socket.gethostname() mozna '', zeby byl osiagalny ze wszystkich interfejsow
except socket.error as msg:
    print 'Bind failed. Error code: ' + str(msg[0]) + ' Message ' + msg[1]
    sys.exit()

print 'Socket bind complete'

#now keep talking with the client
while 1:
    #wait to accept a connection - blocking call
    versionStringClient, addr = s.recvfrom(1024)
    print 'Connected with ' + addr[0] + ':' + str(addr[1])

    #compatibility string
    versionStringServer = 'v05/10/2015'
    versionStringClient = versionStringClient.decode()
    if versionStringClient == versionStringServer:
        print "VERSION MATCH!"
        s.sendto('VERSION MATCH', (addr[0], 8888))
        while 1:
            data, addr = s.recvfrom(1024)
            data = data.decode()
            #OBSLUGA SPECJALNYCH KOMEND
            if data == 'CONN_CLOSE':
                print "CLIENT CLOSED CONNECTION\n"
                break;

            if data == versionStringServer:
                print "CLIENT RECONNECTED!"
                s.sendto('VERSION MATCH', (addr[0], 8888))

            #OBSLUGA STANDARDOWYCH WARTOSCI
            #a='0=100%,1=55%,2=21%,6=99%'
            #b=a.split(','); print b[0]
            values = data.split(',')
            path = os.path.dirname(sys.argv[0]) + 'plik.txt'
            for value in values:
                call('echo ' + value + ' >> ' +  path, shell=True) #zmienic potem na wysylanie do urzadzenia!
            print values
            s.sendto('RECV_OK', (addr[0], 8888))
    else:
        s.sendto('VERSION MISMATCH', (addr[0], 8888))
        print "VERSION MISMATCH: " + versionStringClient
        break;
s.close()
