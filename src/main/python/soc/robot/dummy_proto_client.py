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


import argparse
import socket
import sys
import traceback
import google.protobuf
from google.protobuf.internal import encoder, decoder
import data_pb2
import message_pb2
import game_message_pb2

class DummyProtoClient(object):
    """
    Sample class to connect to a SOCServer over protobuf, and do one of:

    - Demonstrate third-party bot authentication, but not join any games
    - Observer Mode: Auth as a "human" client, join all newly created games
      (and one current game, if any) and print all received recognized messages

    Default server port is PORT_DEFAULT_PROTOBUF (4000).
    Call constructor, then auth_and_run() to loop.  Message send/receive methods
    are write_delimited_message() and read_delimited_message().
    """

    PORT_DEFAULT_PROTOBUF = 4000

    def __init__(self, srv, port=PORT_DEFAULT_PROTOBUF, cookie='??', is_observer=False):
        """
        Set fields and try to connect to srv on the given TCP port.
        To actually communicate with the server, you must call auth_and_run().
        srv: Server hostname (FQDN) or IP string ("127.0.0.1" etc)
        port: TCP port; default protobuf port for JSettlers is PORT_DEFAULT_PROTOBUF
        cookie: Server's required robot cookie (weak shared secret) if not Observer Mode, or None
        is_observer: If true, run in Observer Mode instead of Bot Mode
        sock: Socket connected to the server
        connected: Are we connected to the server, with no errors sending or decoding messages?
        nickname: Our nickname set during authentication, or None
        joined_games: Our set of joined games, if Observer Mode
        """
        self.srv = srv
        self.port = port
        self.is_observer = is_observer
        if is_observer:
            cookie = None
        self.cookie = cookie

        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect((srv, port))
        self.connected = False  # set true in auth_and_run
        self.nickname = None  # set during auth_and_run
        self.joined_games = set()  # for Observer Mode game tracking

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
        if self.is_observer:
            self.nickname = 'DummyProto-Observer'
            msg.auth_req.role = message_pb2.AuthRequest.GAME_PLAYER
            msg.auth_req.nickname = self.nickname
            msg.auth_req.auth_scheme = message_pb2.AuthRequest.CLIENT_PLAINTEXT
        else:
            self.nickname = 'DummyProto-Python'
            msg.im_a_robot.nickname = self.nickname
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
                print("* Error receiving/parsing message: " + str(e), file=sys.stderr)
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

    def request_join_game(self, ga_name):
        """
        Send a request to join a game in Observer Mode.
        Must be connected and in that mode, or raises AssertionError.
        """
        if not self.connected:
            raise AssertionError("not connected")
        if not self.is_observer:
            raise AssertionError("not observer mode")

        msg = message_pb2.FromClient()
        msg.ga_join.ga_name = ga_name
        self.write_delimited_message(msg)

    # All FromServer message treater functions; see msg_from_server_treaters, _game_msg_treaters.
    # The ordering of these declarations follows that within message.proto message FromServer.

    # auth/connect

    def _treat_vers(self, msg):
        print("  Version(" + str(msg.vers_num) + ", '"
            + msg.vers_str + "', '" + msg.vers_build + "', "
            + repr(msg.srv_feats) + ")" )

    def _treat_reject_connection(self, msg):
        print("  RejectConnection(" + repr(msg.reason_text) + ")" )
        print("  -- wait for server to close our connection")
        # let server disconnect us, to test its ability to cleanly do so

    def _treat_status_text(self, msg):
        print("  ServerStatusText(" + str(msg.sv)
            + ", " + repr(msg.text) + ")" )

    # robots

    def _treat_bot_update_params(self, msg):
        print("  BotUpdateParams(strat=" + str(msg.strategy_type)
            + ", tf=" + str(msg.trade_flag) + ", ...)" )

    # games

    def format_proto_game_str(self, proto_game):
        """Return a string with the contents of a _GameWithOptions protobuf message"""
        ret = '_GameWithOptions(' + repr(proto_game.ga_name) + ', opts=' + repr(proto_game.opts)
        if proto_game.unjoinable:
            ret += ', unjoinable=True'
        return ret + ')'

    def _treat_games(self, msg):
        """Current games on server; in Observer Mode, will try to join one."""
        # examine and print repeated game contents (_GameWithOptions)
        ga_name_to_join = None
        s = '  Games(game=['
        is_first = True
        for ga in msg.game:
            if is_first:
                is_first = False
            else:
                s += ', '
            s += self.format_proto_game_str(ga)
            if (ga_name_to_join is None) and not ga.unjoinable:
                ga_name_to_join = ga.ga_name
        s += '])'
        print(s)
        if ga_name_to_join is not None:
            self.request_join_game(ga_name_to_join)

    def _treat_new_game(self, msg):
        print("  NewGame(game=" + self.format_proto_game_str(msg.game)
            + ", min_version=" + str(msg.min_version) + ")" )
        print("  -- NEW GAME CREATED.")
        if not msg.game.unjoinable:
            self.request_join_game(msg.game.ga_name)

    def _treat_join_game(self, msg):
        print("  JoinGame(ga_name=" + repr(msg.ga_name)
            + ", member_name=" + repr(msg.member_name) + ")" )
        if msg.member_name == self.nickname:
            print("  -- JOINED GAME as observer.")
            self.joined_games.add(msg.ga_name)

    def _treat_bot_join_req(self, msg):
        """This client can't join games; BotJoinGameRequest is seen only in Bot Mode, not Observer Mode."""
        print("  BotJoinGameRequest(" + repr(msg.game.ga_name) + ", "
            + str(msg.seat_number) + ")" )
        print("  -- DISCONNECTING, this bot can't join games")
        self.disconnect()

    def _treat_leave_game(self, msg):
        print("  LeaveGame(ga_name=" + repr(msg.ga_name)
            + ", member_name=" + repr(msg.member_name) + ")" )
        if msg.member_name == self.nickname:
            print("  -- LEFT GAME.")
            self.joined_games.discard(msg.ga_name)

    def _treat_delete_game(self, msg):
        print("  DeleteGame(ga_name=" + repr(msg.ga_name) + ")" )
        print("  -- GAME DELETED.")
        self.joined_games.discard(msg.ga_name)  # in case bot was a member

    #### within a game ####

    # these treater functions all have the same formal arg list, which
    # includes the message's player number field; not all game message
    # types use player_number.

    # game and player state

    def _treat_ga_state(self, ga_name, pn, msg):
        print("  State(game=" + repr(ga_name) + ", state=" + data_pb2.GameState.Name(msg.state) + ")")

    def _treat_ga_player_element(self, ga_name, pn, msg):
        if pn is None:
            pn = 0
        if msg.is_news:
            s = ", is_news=True"
        else:
            s= ""
        print("  PlayerElement(game=" + repr(ga_name) + ", pn=" + str(pn)
            + ", action=" + game_message_pb2._PlayerElementAction.Name(msg.action)
            + ", " + game_message_pb2._PlayerElementType.Name(msg.element_type)
            + ": " + str(msg.amount) + s + ")")

    def _treat_ga_player_elements(self, ga_name, pn, msg):
        if pn is None:
            pn = 0
        s = ""
        for i in range(len(msg.element_types)):
            if i > 0:
                s += ", "
            s += (game_message_pb2._PlayerElementType.Name(msg.element_types[i])
                + ": " + str(msg.amounts[i]))
        print("  PlayerElements(game=" + repr(ga_name) + ", pn=" + str(pn)
            + ", action=" + game_message_pb2._PlayerElementAction.Name(msg.action)
            + ", {" + s + "})")

    def _treat_ga_game_elements(self, ga_name, pn, msg):
        s = ""
        for i in range(len(msg.element_types)):
            if i > 0:
                s += ", "
            s += (game_message_pb2.GameElements._ElementType.Name(msg.element_types[i])
                + ": " + str(msg.values[i]))
        print("  GameElements(game=" + repr(ga_name) + ", {" + s + "})")

    # board layout and contents

    def _treat_ga_board_layout(self, ga_name, pn, msg):
        print("  BoardLayout(game=" + repr(ga_name) + ", encoding="
              + game_message_pb2.BoardLayout._LayoutEncodingFormat.Name(msg.layout_encoding)
              + ", parts=" + repr(msg.parts) + ")")

    def _treat_ga_potential_settlements(self, ga_name, pn, msg):
        if pn is None:
            pn = 0
        s = "  PotentialSettlements(game=" + repr(ga_name) + ", pn=" + str(pn)
        if msg.area_count:
            s += ", area_count=" + str(msg.area_count)
        if msg.starting_land_area:
            s += ", starting_land_area=" + str(msg.starting_land_area)
        s += ", ps_nodes=[" + ', '.join([self.format_proto_coord(c) for c in msg.ps_nodes]) + "]"
        if msg.land_areas_legal_nodes:
            s += ", land_areas_legal_nodes={"
            for k, nlist in msg.land_areas_legal_nodes.items():
                s += " " + str(k) + ": [" + ', '.join([self.format_proto_coord(c) for c in nlist.node]) + "]"
            s += "}"
        if msg.legal_sea_edges:
            s += ", legal_sea_edges=["
            for elist in msg.legal_sea_edges:
                s += "{" + ', '.join([self.format_proto_coord(c) for c in elist.edge]) + "}"
            s += "]"
        s += ")"
        print(s)

    def _treat_ga_build_piece(self, ga_name, pn, msg):
        if pn is None:
            pn = 0
        ptype = msg.type  # not sent if ROAD (0)
        if not ptype:
            pt = data_pb2.ROAD
        print("  BuildPiece(game=" + repr(ga_name) + ", player_number=" + str(pn)
              + ", coordinates=" + self.format_proto_coord(msg.coordinates)
              + ", type=" + data_pb2.PieceType.Name(ptype) + ")")

    # turn

    def _treat_ga_start_game(self, ga_name, pn, msg):
        print("  StartGame(game=" + repr(ga_name) + ", state=" + data_pb2.GameState.Name(msg.state) + ")")

    def _treat_ga_turn(self, ga_name, pn, msg):
        if pn is None:
            pn = 0
        print("  Turn(game=" + repr(ga_name) + ", pn=" + str(pn) + ", state=" + data_pb2.GameState.Name(msg.state) + ")")

    def _treat_ga_set_turn(self, ga_name, pn, msg):
        if pn is None:
            pn = 0
        print("  SetTurn(game=" + repr(ga_name) + ", pn=" + str(pn) + ")")

    def _treat_ga_dice_result(self, ga_name, pn, msg):
        print("  DiceResult(game=" + repr(ga_name) + ", dice_total=" + str(msg.dice_total) + ")")

    # player actions
    def _treat_ga_inventory_item_action(self, ga_name, pn, msg):
        if pn is None:
            pn = 0
        atype = msg.action_type
        if not atype:
            atype = game_message_pb2.InventoryItemAction.DRAW
        if msg.dev_card_value:
            s = ", dev_card_value=" + data_pb2.DevCardValue.Name(msg.dev_card_value)
        elif msg.other_inv_item_type:
            s = ", other_inv_item_type=" + str(msg.other_inv_item_type)
        else:
            s = ", (unknown dev card or item)"
        if msg.reason_code:
            s += ", reason=" + str(msg.reason_code)
        if msg.is_playable:
            s += ", is_playable=True"
        if msg.is_kept:
            s += ", is_kept=True"
        if msg.is_VP:
            s += ", is_VP=True"
        if msg.can_cancel_play:
            s += ", can_cancel_play=True"
        print("  InventoryItemAction(game=" + repr(ga_name) + ", pn=" + str(pn)
            + ", action=" + game_message_pb2.InventoryItemAction._ActionType.Name(atype) + s + ")")


    # The ordering within this declaration follows that of game_message.proto message GameMessageFromServer.
    _game_msg_treaters = {
        # game and player state
        'game_state': _treat_ga_state,
        'player_element': _treat_ga_player_element,
        'player_elements': _treat_ga_player_elements,
        'game_elements': _treat_ga_game_elements,
        # board layout and contents
        'board_layout': _treat_ga_board_layout,
        'potential_settlements': _treat_ga_potential_settlements,
        'build_piece': _treat_ga_build_piece,
        # turn
        'start_game': _treat_ga_start_game,
        'turn': _treat_ga_turn,
        'set_turn': _treat_ga_set_turn,
        'dice_result': _treat_ga_dice_result,
        # player actions
        'inventory_item_action': _treat_ga_inventory_item_action,
    }

    def _treat_game_message(self, msg):
        """
        Treat an incoming game-specific message from the server; called from treat.
        In this dummy/demo client, most messages are ignored.
        """
        if msg is None:
            return
        typ = msg.WhichOneof("msg")
        if typ is None:
            return
        mdata = getattr(msg, typ, None)  # for message typ board_layout, get msg.board_layout contents
        pn = msg.player_number  # may be None if not used or if player 0
        if typ in self._game_msg_treaters:
            self._game_msg_treaters[typ](self, msg.game_name, pn, mdata)
        else:
            print("  treat_game_message(): No handler for message type " + str(typ)
                + "(pn=" + str(pn) + "): {{\n" + repr(mdata) + "  }}")

    # Static FromServer message-type handler switch dict for treat(), initialized once.
    # The ordering within this declaration follows that of message.proto message FromServer.

    _msg_from_server_treaters = {
        # auth/connect
        'vers': _treat_vers,
        'reject_connection': _treat_reject_connection,
        'status_text': _treat_status_text,

        # robots
        'bot_update_params': _treat_bot_update_params,

        # games
        'games': _treat_games,
        'ga_new': _treat_new_game,
        'ga_join': _treat_join_game,
        'bot_join_req': _treat_bot_join_req,
        'ga_leave': _treat_leave_game,
        'ga_delete': _treat_delete_game,

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
        mdata = getattr(msg, typ, None)  # for message typ bot_update_params, get msg.bot_update_params contents
        if typ in self._msg_from_server_treaters:
            self._msg_from_server_treaters[typ](self, mdata)
        else:
            print("  treat(): No handler for server message type " + str(typ)
                + ": {{\n" + repr(mdata) + "  }}")

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
        (size, position) = decoder._DecodeVarint(buf, 0)

        rCount = 0
        while rCount < size:
            data = self.sock.recv(size - rCount)
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

    # utility: misc data formatters

    def format_proto_coord(self, coord):
        """
        Returns a string for this data.proto 2D board coordinate, formatted as: '(0xRR, 0xCC)'
        coord can be a EdgeCoord, HexCoord, NodeCoord or BoardCoord.
        If BoardCoord:
            - Prepend 'edge_coord', 'hex_coord', or 'node_coord' to the '(0xRR, 0xCC)' returned.
            - If none of those 3 coordinate type fields are provided, returns 'board_coord(empty)'
        If coord isn't one of those types or None, raises a ValueError trying to get the coord_type OneOf value.
        """
        if coord is None:
            return 'None'
        if hasattr(coord, 'row') or hasattr(coord, 'column'):
            r = coord.row
            c = coord.column
            if r is None:
                r = 0
            if c is None:
                c = 0
            return '(' + format(r, '#04x') + ', ' + format(c, '#04x') + ')'
        else:
            try:
                cf_name = str(coord.WhichOneof("coord_type"))
            except:
                raise ValueError("coord has no coord_type: " + repr(coord))
            if cf_name in ['edge_coord', 'hex_coord', 'node_coord']:
                # format and return coord.edge_coord, .hex_coord or .node_coord
                return cf_name + self.format_proto_coord(getattr(coord, cf_name, None))
            else:
                return 'board_coord(empty)'

def main():
    """
    Start the client, try to connect to a server.
    Will need hostname, maybe port, and probably a cookie value.
    Run with no args for help summary.
    """

    port = DummyProtoClient.PORT_DEFAULT_PROTOBUF
    cookie = '??'

    # TODO consider use argparse for positional arguments too

    parser = argparse.ArgumentParser()
    parser.add_argument("-o", "--observe", help="Run in Observer Mode, not Bot Mode", action="store_true")
    args, rest_of_argv = parser.parse_known_args()
    sys.argv = sys.argv[:1] + rest_of_argv
    is_observer = args.observe

    L = len(sys.argv)
    if (L < 2) or (L > 4):
        sys.stderr.write("Arguments: serverhostname [cookie or --observe]   or serverhostname port cookie (or --observe)\n")
        sys.stderr.write("Default port is " + str(port) + ", default cookie is " + cookie + " and probably wrong\n")
        sys.stderr.write("For Observer Mode (not Bot Mode) use -o or --observe instead of a cookie\n")
        sys.stderr.flush()
        sys.exit(1)  # <--- Early exit: Printed usage ---

    srv = sys.argv[1]
    if (L == 4):
        port = int(sys.argv[2])
        cookie = sys.argv[3]
    elif (L == 3):
        cookie = sys.argv[2]

    # try/except would surround this in a non-test client:
    cli = DummyProtoClient(srv, port, cookie, is_observer)
    cli.auth_and_run()
    cli.close()

if __name__ == '__main__':
    main()


# This file is part of the JSettlers project.
#
# This file Copyright (C) 2017-2018 Jeremy D Monin <jeremy@nand.net>
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
