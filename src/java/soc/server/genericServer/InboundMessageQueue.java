/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010,2015-2016 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2016 Alessandro D'Ottavio
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
 **/
package soc.server.genericServer;

import java.util.Vector;

import soc.message.SOCMessage;

/**
 *
 * this is one of the main class in the messaging infrastructure implemented in the {@link Server}
 * <P>
 *
 * The main target of this class is to store all the inbound {@link SOCMessage} in text format received from the server by the client connected
 * and solicit the server to  processing the message
 *
 * <P>
 * The internal implementation of the {@link InboundMessageQueue} use an internal thread implemented by the inner class {@link Treater} to process the messages received
 * in the queue and solicit the {@link Server} using the method {@link Server#processCommand(String, StringConnection)}
 *
 * <P>
 * to initialize {@link InboundMessageQueue} use the constructor {@link #SOCInboundMessageQueue(Server)}.
 * This constructor will start only the behavior of the SOCInboundMessageQueue of receive the messages.... <br>
 * To start the processing of the message you have to call {@link #startMessageProcessing()} when the {@link Server} is ready to process the messages.
 *
 * <P>
 * Actually this class is used  by the {@link StringConnection} instances and derived instances classes to store the new message received.<br>
 * The method used to put messages in this queue is {@link #push(String, StringConnection)} <br>
 * the implementation of the  {@link InboundMessageQueue} use an internal thread-safe implementation queue, so it doesn't need to be synchronized
 *
 * <P>
 * The {@link InboundMessageQueue} must be stopped when the {@link Server} owner of this queue is stopped calling {@link #stopMessageProcessing()}.<br>
 * This will stop also the {@link Treater} instance of this queue
 *
 * <UL>
 * <LI> See {@link SOCMessage} for details of the client/server protocol messaging.
 * <LI> See {@link StringConnection} for details of the client/server communication.
 * <LI> See {@link Server#processCommand(String, StringConnection)} for details of the message processing.
 * </UL>
 *
 *
 * @author Alessandro D'Ottavio
 * @since 2.0.00
 */
public class InboundMessageQueue
{

    /**
     * this queue is used to store the {@link MessageData}
     */
    private Vector<MessageData> inQueue;

    /**
     * the Thread responsible to process the data in the {@link #inQueue}
     */
    private Treater treater;

    /**
     * the server that has initialized this queue
     */
    private Server server;


    /**
     * Constructor of the SOCInboundMessageQueue.
     *
     * @param server that will use this SOCInboundMessageQueue to store messages and that the SOCInboundMessageQueue will use to treat the messages
     */
    public InboundMessageQueue(Server server){
        inQueue = new Vector<MessageData>();
        this.server = server;
    }

    /**
     * Start the {@link Treater} internal thread that is responsible to solicit the server that new messages are arrived
     */
    public void startMessageProcessing(){
        treater = new Treater();
        treater.start();
    }

    /**
     * Stop the {@link Treater} internal thread
     */
    public void stopMessageProcessing(){
        treater.stopTreater();
    }

    /**
     * appends an elements in the end of the inbound queue.
     * this notify the {@link Treater} in case it is in wait state becouse the queue was empty
     *
     * @param receivedMessage from the connection
     * @param clientConnection that send the message
     */
    public void push(String receivedMessage, StringConnection clientConnection)
    {
        synchronized (inQueue)
        {
            inQueue.addElement(new MessageData(receivedMessage, clientConnection));
            inQueue.notify();
        }
    }

    /**
     * Retrieves and removes the head of this queue, or returns null if this queue is empty.
     * 
     * @return the head of this queue, or null if this queue is empty. 
     */
    protected final MessageData poll()
    {
        synchronized (inQueue)
        {
            if (inQueue.size() > 0){
                return inQueue.remove(0);    
            }
        }
        return null;

    }


    /**
     * Internal class user to process the message stored in the {@link #inQueue}
     * <P>
     * the thread can be stopped calling {@link #stopTreater()}
     *
     *
     * @author Alessandro
     */
    class Treater extends Thread
    {

        /**
         * this variable is used to control the processing of the message
         */
        private volatile boolean processMessage;

        public Treater()  // Server parameter is also passed in, since this is an inner class
        {
            setName("treater");  // Thread name for debug
            processMessage = true;
        }

        public void stopTreater(){
            processMessage = false;
        }

        public void run()
        {
            while (processMessage)
            {
                MessageData messageData = poll();

                try
                {
                    if (messageData != null)
                    {
                        server.processCommand(messageData.getStringMessage(), messageData.getClientConnection());
                    }
                }
                catch (Exception e)
                {
                    System.out.println("Exception in treater (processCommand) - " + e.getMessage());
                    e.printStackTrace();
                }

                yield();

                synchronized (inQueue)
                {
                    if (inQueue.size() == 0)
                    {
                        try
                        {
                            //D.ebugPrintln("treater waiting");
                            inQueue.wait(1000);
                        }
                        catch (Exception ex)
                        {
                            ;   // catch InterruptedException from inQueue.notify() in treat(...)
                        }
                    }
                }
            }
        }
    }


    /**
     * internal class used to store a message in text format and the client, owner of the message
     */
    private class MessageData
    {
        public String stringMessage;
        public StringConnection clientConnection;

        public MessageData(String stringMessage, StringConnection clientConnection)
        {
            this.stringMessage = stringMessage;
            this.clientConnection = clientConnection;
        }

        public String getStringMessage()
        {
            return stringMessage;
        }

        public StringConnection getClientConnection()
        {
            return clientConnection;
        }

    }


}
