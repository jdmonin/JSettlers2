/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/

package soctest.proto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import soc.proto.Message;

/**
 * Test the most basic of protobuf serialization/deserialization: A single known message.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
public class TestFileImARobot
{
    private static final String FIELD_NICKNAME = "testbot1", FIELD_COOKIE = "abc",
        FIELD_RBCLASS = soc.robot.SOCRobotBrain.RBCLASS_BUILTIN;

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private void writeMsg(final File f)
        throws IOException
    {
        Message.ImARobot msg = Message.ImARobot.newBuilder()
            .setNickname(FIELD_NICKNAME).setCookie(FIELD_COOKIE).setRbClass(FIELD_RBCLASS).build();

        FileOutputStream fos = new FileOutputStream(f);
        msg.writeDelimitedTo(fos);
        fos.flush();
        fos.close();
    }

    private Message.ImARobot readMsg(final File f)
        throws IOException
    {
        FileInputStream fis = new FileInputStream(f);
        Message.ImARobot msg = Message.ImARobot.parseDelimitedFrom(fis);
        fis.close();
        return msg;
    }

    @Test
    public void testWriteReadMsgFile()
        throws Exception
    {
        File tmpf = tmpDir.newFile("imarobot.msg");
        writeMsg(tmpf);
        Message.ImARobot msg = readMsg(tmpf);
        assertNotNull("Read message from file", msg);
        assertEquals("nickname", FIELD_NICKNAME, msg.getNickname());
        assertEquals("cookie", FIELD_COOKIE, msg.getCookie());
        assertEquals("rbclass", FIELD_RBCLASS, msg.getRbClass());
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.proto.TestFileImARobot");
    }

}