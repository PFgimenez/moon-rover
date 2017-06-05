# -*- coding: utf-8 -*-

import socket

_last_speed = 0
_last_direction = 0

def init():
    global _socket
    _socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    _socket.connect(("localhost", 13371))
    

def close():
    global _socket
    # envoi d'un shutdown
    message = bytearray()
    message.append(8)
    message.append(0)
    _socket.send(message)
    _socket.close()

def set_speed(speed):
    global _last_speed, _socket
    if speed != _last_speed:
        print "Speed set to: " + str(speed)
        _last_speed = speed
        message = bytearray()
        message.append(2)
        message.append(speed)
        _socket.send(message)

# direction entre -20 et 20
def set_direction(direction):
    global _last_direction, _socket
    if direction != _last_direction or True:
        print "Direction set to: " + str(direction)
        _last_direction = direction
        message = bytearray()
        message.append(5)
        message.append(direction)
        _socket.send(message)

def robot_stop():
    global _socket
    print "Stop"
    message = bytearray()
    message.append(6)
    message.append(0)
    _socket.send(message)

def pull_up_net():
    global _socket
    print "pull_up_net"
    message = bytearray()
    message.append(10)
    message.append(0)
    _socket.send(message)

def pull_down_net():
    global _socket
    print "pull_down_net"
    message = bytearray()
    message.append(11)
    message.append(0)
    _socket.send(message)

def open_net():
    global _socket
    print "open_net"
    message = bytearray()
    message.append(13)
    message.append(0)
    _socket.send(message)

def close_net():
    global _socket
    print "close_net"
    message = bytearray()
    message.append(12)
    message.append(0)
    _socket.send(message)

def eject_left_side():
    global _socket
    print "eject_left_side"
    message = bytearray()
    message.append(14)
    message.append(0)
    _socket.send(message)

def rearm_left_side():
    global _socket
    print "rearm_left_side"
    message = bytearray()
    message.append(16)
    message.append(0)
    _socket.send(message)

def eject_right_side():
    global _socket
    print "eject_right_side"
    message = bytearray()
    message.append(15)
    message.append(0)
    _socket.send(message)

def rearm_right_side():
    global _socket
    print "rearm_right_side"
    message = bytearray()
    message.append(17)
    message.append(0)
    _socket.send(message)
