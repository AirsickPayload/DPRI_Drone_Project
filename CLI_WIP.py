#import RPi.GPIO as GPIO
# -*- coding: utf-8 -*-
import sys,tty,termios, curses, argparse
from subprocess import call

class Wartosci:
    def __init__(self):
        self.throttle = 0
        self.yaw = 0
        self.pitch = 0
        self.roll = 0

class Procentowe(Wartosci):
    def setDefaultStates(self):
        self.throttle = 0
        self.yaw = 50
        self.pitch = 50
        self.roll = 50

    def setInitValues(self):
        self.throttle = 0
        self.yaw = 100
        self.pitch = 50
        self.roll = 50

class Sekundowe(Wartosci):
    def setDefaultStates(self):
        self.throttle = 1500
        self.yaw = 2000
        self.pitch = 2000
        self.roll = 2000

    def setInitValues(self):
        self.throttle = 1500
        self.yaw = 2500
        self.pitch = 2000
        self.roll = 2000

# Zmienne potrzebne do obsługi drona
#throttleVal = yawVal = pitchVal = rollVal = 0
throttle = yaw = pitch = roll = 0
procentowe = Procentowe()
sekundowe = Sekundowe()
mode = 'p'

# Zmienne obsługujące interfejs
char = None
screen = None

def keypress():
    global char, screen
    char = screen.getch()

def initServos():
    global throttle, yaw, pitch, roll
    throttle = int(input("Throttle servo? ")) #0
    yaw = int(input("Yaw servo? ")) #1
    pitch = int(input("Pitch servo? ")) #2
    roll = int(input("Roll servo? ")) #6

def exeC(servoNumber, valueHolder):
    global mode
    execString = "echo " + str(servoNumber) + "=" + str(valueHolder)
    if mode == 'p':
        execString += "% "
    else:
        execString += "us "
    execString += "> /dev/servoblaster" # > /dev/servoblaster
    call(execString, shell=True)

def valSet(servoNumber, wartosc):
    exeC(servoNumber, wartosc)
    return wartosc

def ensureBoundaries(valueHolder, changeVal):
    if valueHolder + changeVal > 100 or valueHolder + changeVal < 0:
        return valueHolder
    else:
        return valueHolder + changeVal

def valChange(servoNumber, valueHolder, value):
    global mode
    if value == "UP":
        if mode == 'p':
            valueHolder =  ensureBoundaries(valueHolder, 1) # %
        else:
            valueHolder =  ensureBoundaries(valueHolder, 10) # us
    elif value == "DOWN":
        if mode == 'p':
            valueHolder =  ensureBoundaries(valueHolder, -1) # %
        else:
            valueHolder =  ensureBoundaries(valueHolder, -10) # us
    exeC(servoNumber, valueHolder)
    return valueHolder

def flightControllerInit(wartosci):
    global throttle, yaw, pitch, roll, throttleVal, yawVal, pitchVal, rollVal
    wartosci.setInitValues()
    exeC(throttle, wartosci.throttle)
    exeC(yaw, wartosci.yaw)
    exeC(roll, wartosci.roll)
    exeC(pitch, wartosci.pitch)
    return wartosci

def menu_help():
    global screen
    screen.addstr(0, 20, "ARROW UP - zwieksz Throttle o 1% / 10us")
    screen.addstr(1, 20, "ARROW DOWN - obniz Throttle o 1% / 10us\n")
    screen.addstr(2, 20, "ARROW RIGHT - zwieksz Yaw o 1% / 10us")
    screen.addstr(3, 20, "ARROW LEFT - obniz Yaw o 1% / 10us\n")
    screen.addstr(4, 20, "d - zwieksz Roll o 1% / 10us")
    screen.addstr(5, 20, "a - obniz Roll of 1% / 10us\n")
    screen.addstr(6, 20, "w - zwieksz Pitch o 1% / 10us")
    screen.addstr(7, 20, "s - obniz Pitch o 1% / 10us\n")
    screen.addstr(8, 20, "c - zatrzymanie dzialania")
    screen.addstr(9, 20, "z - zmien nr serwo")
    screen.addstr(10, 20, "q - wyjscie z programu")
    screen.addstr(11, 20, "i - uzbrojenie flight controllera")
    screen.addstr(12, 20, "h - menu pomocy")
    screen.addstr(13,20, "m - zmiana trybu pracy")
    screen.addstr(15,10, "Throttle PIN: " + str(throttle))
    screen.addstr(16,10, "Yaw PIN: " + str(yaw))
    screen.addstr(17,10, "Pitch PIN: " + str(pitch))
    screen.addstr(18,10, "Roll PIN: " + str(roll))
    screen.addstr(19,10, "Tryb pracy: " + mode)

def revertToDefaultStates(wartosci):
    global throttle, yaw, pitch, roll
    wartosci.setDefaultStates()
    exeC(throttle, wartosci.throttle)
    exeC(yaw, wartosci.yaw)
    exeC(roll, wartosci.roll)
    exeC(pitch, wartosci.pitch)
    return wartosci

def main(throttlePin, yawPin, pitchPin, rollPin):
    global char, throttle, yaw, pitch, roll, screen, mode, procentowe, sekundowe

    wartosci = None
    if mode == 'p':
        wartosci = procentowe
    else:
        wartosci = sekundowe

    throttle = throttlePin
    yaw = yawPin
    pitch = pitchPin
    roll = rollPin

    screen = curses.initscr()
    curses.noecho()
    curses.cbreak()
    screen.keypad(True)
    menu_help()

    while True:
       keypress()
       screen.clear()
       if char == curses.KEY_UP:
           wartosci.throttle = valChange(throttle, wartosci.throttle, "UP")
       elif char == curses.KEY_DOWN:
            wartosci.throttle  = valChange(throttle, wartosci.throttle, "DOWN")
       elif char == ord('c'):
           wartosci = revertToDefaultStates(wartosci)
           screen.addstr(0, 0, "STOP!")
       elif char == curses.KEY_RIGHT:
           wartosci.yaw = valChange(yaw, wartosci.yaw, "UP")
       elif char == curses.KEY_LEFT:
            wartosci.yaw = valChange(yaw, wartosci.yaw, "DOWN")
       elif char == ord('d'):
           wartosci.roll = valChange(roll, wartosci.roll, "UP")
       elif char == ord('a'):
           wartosci.roll = valChange(roll, wartosci.roll, "DOWN")
       elif char == ord('w'):
           wartosci.pitch = valChange(pitch, wartosci.pitch, "UP")
       elif char == ord('s'):
           wartosci.pitch = valChange(pitch, wartosci.pitch, "DOWN")
       elif char == ord('z'):
           initServos()
           screen.addstr(0, 0, "OK!")
       elif char == ord("i"):
           wartosci = flightControllerInit(wartosci)
           screen.addstr(0, 0, "Flight controller uzbrojony!")
       elif char == ord('q'):
           wartosci = revertToDefaultStates(wartosci)
           screen.addstr(0, 0, "KONCZE")
           curses.nocbreak()
           screen.keypad(0)
           curses.echo()
           curses.endwin()
           break
       elif char == ord('h'):
           menu_help()
           screen.getch()
       elif char == ord('m'):
           if mode == 'p':
               mode  = 's'
               wartosci = sekundowe
           elif mode == 's':
               mode = 'p'
               wartosci = procentowe
           screen.addstr(0, 0, "Zmiana trybu pracy!")
       screen.addstr(1, 0, "Tryb pracy: " + mode)
       screen.addstr(2, 0, "Wartosc Throttle: " + str(wartosci.throttle))
       screen.addstr(3, 0, "Wartosc Yaw: " + str(wartosci.yaw))
       screen.addstr(4, 0, "Wartosc Roll: " + str(wartosci.roll))
       screen.addstr(5, 0, "Wartosc Pitch: " + str(wartosci.pitch) + "\n")

parser = argparse.ArgumentParser(description='Skrypt sterujący dronem.')
parser.add_argument('pins', metavar='p', nargs=4, default=[0,1,2,6],
                    help="Numery pinów odpowiedzialne za sterowanie Throttle/Yaw/Pitch/Roll")
parser.add_argument("mode", metavar='t',
                    help="Tryb pracy - przekazywanie wartości przy pomocy [p]rocentów lub bez posrednio mikro[s]ekund")
args = parser.parse_args()
mode = args.mode
main(args.pins[0], args.pins[1], args.pins[2], args.pins[3])
