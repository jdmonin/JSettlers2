#!/usr/bin/env python3

# JSettlers sample/dummy Protobuf client.
# See bottom of file for copyright and license information (GPLv3+).

# Your PYTHONPATH should include these dirs within the JSettlers2 repo:
#    generated/src/proto/main/python
#    src/main/python
# Example run:
#    sh$ export PYTHONPATH=./generated/src/proto/main/python:./src/main/python:$PYTHONPATH
#    sh$ src/main/python/soc/robot/dummy_proto_client.py localhost PRO
#
# Since this is for testing only, error conditions will throw an exception instead of being caught.
# This sample client has rough feature parity with src/main/java/soc/robot/protobuf/DummyProtoClient.java.
#
# See bottom of file for main() function.


import socket
import sys
import traceback
import google.protobuf
from google.protobuf.internal import encoder, decoder
import message_pb2

class DummyProtoClient(object):
    """
    Simple class to connect to a SOCServer over protobuf, but not join games.
    Default server port is PORT_DEFAULT_PROTOBUF (4000).
    Call constructor, then auth_and_run() to loop.  Message send/receive methods
    are write_delimited_message() and read_delimited_message().
    """

    PORT_DEFAULT_PROTOBUF = 4000

    def __init__(self, srv, port=PORT_DEFAULT_PROTOBUF, cookie='??'):
        """
        Set fields and try to connect to srv on the given TCP port.
        To actually communicate with the server, you must call auth_and_run().
        srv: Server hostname (FQDN) or IP string ("127.0.0.1" etc)
        port: TCP port; default protobuf port for JSettlers is PORT_DEFAULT_PROTOBUF
        cookie: Server's required robot cookie (weak shared secret)
        sock: Socket connected to the server
        connected: Are we connected to the server, with no errors sending or decoding messages?
        """
        self.srv = srv
        self.port = port
        self.cookie = cookie
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect((srv, port))
        self.connected = False  # set true in auth_and_run

    def disconnect(self, send_leave_all=True):
        """Clear our 'connected' flag field. Optionally send a LeaveAll message first."""
        if self.connected and send_leave_all:
            msg = message_pb2.FromClient()
            msg.leave_all.SetInParent()  # send LeaveAll message, which has no fields of its own
            self.write_delimited_message(msg)
        self.connected = False

    def close(self):
        """Close the socket connection. Ignores any IOException because this is a test client."""
        try:
            self.sock.close()
        except:
            pass

    def auth_and_run(self):
        """
        Encode and send opening protobuf messages to the connected server,
        then loop to receive replies and treat(msg) until a break.
        No error-checking/exception-handling is done because this is a test client.
        """

        self.connected = True

        msg = message_pb2.FromClient()
        msg.vers.vers_num = 3000
        msg.vers.vers_str = '3.0.00'
        msg.vers.vers_build = 'DUMMYBUILD'
        self.write_delimited_message(msg)

        msg = message_pb2.FromClient()
        msg.im_a_robot.nickname = 'DummyProto-Python'
        msg.im_a_robot.cookie = self.cookie
        msg.im_a_robot.rb_class = 'soc.robot.DummyProtoClient_py'
        self.write_delimited_message(msg)

        # Loop to read any proto messages from the server, until ^C
        print("Entering message receive loop; ^C to exit");
        while self.connected:
            try:
                msg = self.read_delimited_message()
            except KeyboardInterrupt as e:
                print("Disconnecting from server")
                self.disconnect()
                msg = None  # clear previous iteration
            except Exception as e:
                print("Error receiving/parsing message: " + str(e), file=sys.stderr)
                self.disconnect(False)
            if msg is not None:
                try:
                    self.treat(msg)
                except Exception as e:
                    print("Error in treat(" + str(msg.WhichOneof("msg")) + "): " + str(e), file=sys.stderr)
                    traceback.print_tb(e.__traceback__)
            else:
                # EOF
                self.disconnect(False)

    # All FromServer message treater functions; see msg_from_server_treaters.
    # The ordering of these declarations follows that within message.proto message FromServer.

    # auth

    def _treat_vers(self, msg):
        print("  Version(" + str(msg.vers.vers_num) + ", '"
            + msg.vers.vers_str + "', '" + msg.vers.vers_build + "', "
            + repr(msg.vers.srv_feats) + ")" )

    def _treat_status_text(self, msg):
        print("  ServerStatusText(" + str(msg.status_text.sv)
            + ", " + repr(msg.status_text.text) + ")" )

    def _treat_bot_update_params(self, msg):
        print("  BotUpdateParams(strat=" + str(msg.bot_update_params.strategy_type)
            + ", tf=" + str(msg.bot_update_params.trade_flag) + ", ...)" )

    # games

    def _treat_bot_join_req(self, msg):
        print("  BotJoinGameRequest('" + msg.bot_join_req.game.ga_name + "', "
            + str(msg.bot_join_req.seat_number) + ")" )
        print("  -- DISCONNECTING, this bot can't join games");
        self.disconnect()

    # within a game

    def _treat_game_message(self, msg):
        print("  GameMessage -- not implemented at this bot")

    # Static FromServer message-type handler switch dict for treat(), initialized once.
    # The ordering within this declaration follows that of message.proto message FromServer.

    _msg_from_server_treaters = {
        # auth
        'vers': _treat_vers,
        'status_text': _treat_status_text,
        'bot_update_params': _treat_bot_update_params,

        # games
        'bot_join_req': _treat_bot_join_req,

        # within a game
        'game_message': _treat_game_message,
    }

    def treat(self, msg):
        """
        Treat an incoming message from the server.
        Messages of unknown type are ignored.
        Reference: https://developers.google.com/protocol-buffers/docs/reference/python-generated
        """
        if msg is None:
            return
        typ = msg.WhichOneof("msg")
        if typ is None:
            return
        if typ in self._msg_from_server_treaters:
            self._msg_from_server_treaters[typ](self, msg)

    # based on same as read_delimited_message()
    def write_delimited_message(self, msg):
        """
        Write a Message.FromClient to our socket, using the same
        wire format as java msg.writeDelimitedTo.
        """
        message_bytes = msg.SerializeToString()
        msglen_delimiter = encoder._VarintBytes(len(message_bytes))
        self.sock.send(msglen_delimiter + message_bytes)

    # based on 2017-05-15 https://stackoverflow.com/questions/43897955/receive-delimited-protobuf-message-in-python-via-tcp/ ,
    #          2016-08-10 https://stackoverflow.com/questions/2340730/are-there-c-equivalents-for-the-protocol-buffers-delimited-i-o-functions-in-ja/
    # and combined and adapted to python3 by jdmonin
    def read_delimited_message(self):
        """
        Read from our socket and return a Message.FromServer, or None at EOF;
        expects the same wire format as java parseDelimitedFrom.
        """
        data, size, position = self._read_java_varint_delimited_stream()
        if not size:
            return None
        msg = message_pb2.FromServer()
        msg.ParseFromString(data[position:position + size])
        return msg

    # based on same as read_delimited_message()
    def _read_java_varint_delimited_stream(self):
        """Read from our socket and return bytes of a delimited message, or (b'', 0, 0) at EOF"""
        buf = self._read_raw_varint32()
        if buf is None:
            return (b'', 0, 0)
        rCount = len(buf)
        (size, position) = decoder._DecodeVarint(buf, 0)  # may need byts(buf)

        while rCount < size + 1:
            data = self.sock.recv(size + 1 - rCount)
            rCount += len(data)
            buf.extend(data)

        return bytes(buf), size, position

    # based on same as read_delimited_message()
    def _read_raw_varint32(self):
        """Read and return a varint32 from our socket, as a bytearray, or None if EOF"""
        mask = 0x80 # (1 << 7)  byte with high bit 1 = continuation
        raw_varint32 = bytearray()
        while True:
            b = self.sock.recv(1)
            if not len(b):
                return None  # EOF
            raw_varint32.extend(b)
            if not (ord(b) & mask):
                # we found a byte with high bit 0, which means it's the last byte of this varint
                break
        return raw_varint32

def main():
    """
    Start the client, try to connect to a server.
    Will need hostname, maybe port, and probably a cookie value.
    Run with no args for help summary.
    """

    port = DummyProtoClient.PORT_DEFAULT_PROTOBUF
    cookie = '??'

    L = len(sys.argv)
    if (L < 2) or (L > 4):
        sys.stderr.write("Arguments: serverhostname [cookie]   or serverhostname port cookie")
        sys.stderr.write("Default port is " + str(port) + ", default cookie is " + cookie + " and probably wrong")
        sys.stderr.flush()
        sys.exit(1)  # <--- Early exit: Printed usage ---

    srv = sys.argv[1]
    if (L == 4):
        port = int(sys.argv[2])
        cookie = sys.argv[3]
    elif (L == 3):
        cookie = sys.argv[2]

    # try/except would surround this in a non-test client:
    cli = DummyProtoClient(srv, port, cookie)
    cli.auth_and_run()
    cli.close()

if __name__ == '__main__':
    main()


# This file is part of the JSettlers project.
#
# This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see http://www.gnu.org/licenses/ .
