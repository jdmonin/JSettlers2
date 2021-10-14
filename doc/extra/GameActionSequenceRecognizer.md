soc.extra.robot.GameActionSequenceRecognizer

Extracts basic higher-level actions in a game from its message sequences
in a `soc.extra.server.GameEventLog`.

Can be used by bots or any other code which wants to examine a game's logs and actions.

## Approach

Based on [Message-Sequences-for-Game-Actions.md](../Message-Sequences-for-Game-Actions.md)'s
list of the message sequences for all the recognized game actions.
Analyze to find common starting messages and build a tree of sequences.
Read through the game's event messages looking for a starting message that's in the roots of that tree.
If a sequence doesn't match any known sequence, scan forward until a starting message is shown,
and group the unknown messages before it for possible later analysis.

More info and some consistency testing of those message sequences:
- `/src/test/java/soctest/server/TestRecorder.java` `testLoadAndBasicSequences()` runs through some basic game actions
- `/src/extraTest/java/soctest/server/TestActionsMessages.java` runs through the rest of them
- `/src/test/resources/resources/gameevent/all-basic-actions.soclog` has all of these sequences and some non-sequence messages

## Analysis: Tree of sequences recognized as game actions

Server and client version 2.5.00 or newer.
Analysis ignores all `SOCGameServerText`, since server may not localize and send text to bots.
Tree is used to determine the game action based on sequence seen.

(TBD from analyzing the list mentioned)


