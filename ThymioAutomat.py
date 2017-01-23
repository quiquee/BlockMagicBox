import dbus
import dbus.mainloop.glib
import gobject
import time
import subprocess
import select
import re
from array import array
from optparse import OptionParser

proxSensorsVal=[0,0,0,0,0]

def Braitenberg():
    #get the values of the sensors
    network.GetVariable("thymio-II", "prox.horizontal",reply_handler=get_variables_reply,error_handler=get_variables_error)

    #print the proximity sensors value in the terminal
    print proxSensorsVal[0],proxSensorsVal[1],proxSensorsVal[2],proxSensorsVal[3],proxSensorsVal[4]

    #Parameters of the Braitenberg, to give weight to each wheels
    leftWheel=[-0.01,-0.005,-0.0001,0.006,0.015]
    rightWheel=[0.012,+0.007,-0.0002,-0.0055,-0.011]

    #Braitenberg algorithm
    totalLeft=0
    totalRight=0
    for i in range(5):
         totalLeft=totalLeft+(proxSensorsVal[i]*leftWheel[i])
         totalRight=totalRight+(proxSensorsVal[i]*rightWheel[i])

    #add a constant speed to each wheels so the robot moves always forward
    totalRight=totalRight+50
    totalLeft=totalLeft+50

    #print in terminal the values that is sent to each motor
    print "totalLeft"
    print totalLeft
    print "totalRight"
    print totalRight

    #send motor value to the robot
    network.SetVariable("thymio-II", "motor.left.target", [totalLeft])
    network.SetVariable("thymio-II", "motor.right.target", [totalRight])

    return True

def get_variables_reply(r):
    global proxSensorsVal
    proxSensorsVal=r

def get_variables_error(e):
    print 'error:'
    print str(e)
    loop.quit()

if __name__ == '__main__':
    parser = OptionParser()
    parser.add_option("-s", "--system", action="store_true", dest="system", default=False,help="use the system bus instead of the session bus")
    parser.add_option("-f", "--logfile", dest="logfile", default="blockmagicbox.log",help="log file from blockmagicbox")
    (options, args) = parser.parse_args()

    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)

    if options.system:
        bus = dbus.SystemBus()
    else:
        bus = dbus.SessionBus()


    if options.logfile:
        print "Listing from " + options.logfile
    else:
        print "You need to specify a logfile"
        end


    #Create Aseba network
    network = dbus.Interface(bus.get_object('ch.epfl.mobots.Aseba', '/'), dbus_interface='ch.epfl.mobots.AsebaNetwork')

    #print in the terminal the name of each Aseba NOde
    print network.GetNodesList()

    f = subprocess.Popen(['tail','-F',options.logfile],\
        stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    i = 0
    while True:
        try:
            line = f.stdout.readline()
            btc = re.findall('for ([\.\d]+) BTC:', line)
            if btc:
                print `i` + " Received " + btc[0] + " BTC"
                i = i +1

                if btc and float(btc[0]) > 0:
                    distance = 1000 * float(btc[0])
                    print "Moving for " + `distance` + " secs"
                    #send motor value to the robot
                    network.SetVariable("thymio-II", "motor.left.target", [200] )
                    network.SetVariable("thymio-II", "motor.right.target", [200] )
                    time.sleep(distance)
                    network.SetVariable("thymio-II", "motor.left.target", [0] )
                    network.SetVariable("thymio-II", "motor.right.target", [0] )
        except KeyboardInterrupt:
            raise
            print "Stoping thymio-II"

            end
    #GObject loop
