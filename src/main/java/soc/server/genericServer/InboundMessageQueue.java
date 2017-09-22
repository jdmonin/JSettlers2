/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2015-2017 Jeremy D Monin <jeremy@nand.net>
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
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.server.genericServer;

import java.util.Vector;

import soc.message.SOCMessage;

/**
 * The single Inbound Message Queue for all messages coming from clients.
 * Stores all unparsed inbound {@link SOCMessage}s received from the server from all
 * connected clients' {@link StringConnection} threads through {@link #push(String, StringConnection)},
 * then dispatched to the {@link Server} for parsing and processing.
 *<P>
 * That dispatch is done through this class's single internal {@link Treater} thread, which de-queues
 * the received messages from the queue and forwards them to the {@link Server} by calling
 * {@link Server.InboundMessageDispatcher#dispatch(String, StringConnection)}
 * for each inbound message.
 *<P>
 * Some dispatched message handlers may want to do work in other Threads without tying up the Treater thread,
 * but then finish handling that message in the Treater to simplify locking of other objects.
 * For this, call {@link #post(Runnable)}: Same concept as {@link java.awt.EventQueue#invokeLater(Runnable)}.
 *
 *<H3>Startup:</H3>
 * This queue's constructor only sets up the InboundMessageQueue to receive messages. Afterwards when the
 * {@link Server} is ready to process inbound messages, you must call {@link #startMessageProcessing()}
 * to start this queue's thread to forward messages into the dispatcher.
 *
 *<H3>Shutdown:</H3>
 * At server shutdown time, {@code InboundMessageQueue} can be stopped by calling {@link #stopMessageProcessing()}
 * which will stop its {@link Treater} thread.
 *
 *<H3>More Information:</H3>
 *<UL>
 * <LI> See {@link Server} class javadoc for an overall picture of inbound processing.
 * <LI> See {@link SOCMessage} for details of the client/server protocol messaging.
 * <LI> See {@link StringConnection} for details of the client/server communication.
 * <LI> See {@link Server.InboundMessageDispatcher#dispatch(String, StringConnection)}
 *      for details on further message processing.
 *</UL>
 *
 * @author Alessandro D'Ottavio
 * @since 2.0.00
 */
public class InboundMessageQueue
{

    /**
     * Internal queue to used to store all clients' {@link MessageData}.
     */
    private Vector<MessageData> inQueue;

    /**
     * Internal thread to process data out of the {@link #inQueue}.
     */
    private Treater treater;

    /**
     * Message dispatcher at the server which will receive all messages from this queue.
     */
    private final Server.InboundMessageDispatcher dispatcher;

    /**
     * Create a new InboundMessageQueue. Afterwards when the server is ready
     * to receive messages, you must call {@link #startMessageProcessing()}.
     *
     * @param imd Message dispatcher at the server which will receive messages from this queue
     */
    public InboundMessageQueue(Server.InboundMessageDispatcher imd)
    {
        inQueue = new Vector<MessageData>();
        dispatcher = imd;
    }

    /**
     * Start the {@link Treater} internal thread that calls the server when new messages arrive.
     */
    public void startMessageProcessing()
    {
        treater = new Treater();
        treater.start();
    }

    /**
     * Stop the {@link Treater} internal thread
     */
    public void stopMessageProcessing()
    {
        treater.stopTreater();
    }

    /**
     * Append an element to the end of the inbound queue.
     *<P>
     *<B>Threads:</B>
     * This method notifies the {@link Treater}, waking that thread if it
     * was {@link Object#wait()}ing because the queue was empty.
     * Although {@code push(..)} isn't declared {@code synchronized},
     * it's thread-safe because it synchronizes on the internal queue object.
     *
     * @param receivedMessage from the connection; will never be {@code null}
     * @param clientConnection that send the message; will never be {@code null}
     * @see #post(Runnable)
     */
    public void push(String receivedMessage, StringConnection clientConnection)
    {
        final MessageData md = new MessageData(receivedMessage, clientConnection);
        synchronized (inQueue)
        {
            inQueue.addElement(md);
            inQueue.notify();
        }
    }

    /**
     * Post some Runnable code to be queued and then run on the Treater thread.
     *<P>
     *<B>Threads:</B>
     * This method notifies the {@link Treater}, waking that thread if it
     * was {@link Object#wait()}ing because the queue was empty.
     * Although {@code post(..)} isn't declared {@code synchronized},
     * it's thread-safe because it synchronizes on the internal queue object.
     * @param run  Runnable code
     * @see #push(String, StringConnection)
     * @see #isCurrentThreadTreater()
     * @since 1.2.00
     */
    public void post(Runnable run)
    {
        final MessageData md = new MessageData(run);
        synchronized (inQueue)
        {
            inQueue.addElement(md);
            inQueue.notify();
        }
    }

    /**
     * Retrieves and removes the head of this queue, or returns null if this queue is empty.
     * Returns as soon as possible; if queue empty, this method doesn't wait until another thread
     * notifies a message has been added.
     *
     * @return the head of this queue, or null if this queue is empty.
     */
    protected final MessageData poll()
    {
        synchronized (inQueue)
        {
            if (inQueue.size() > 0)
                return inQueue.remove(0);
        }

        return null;
    }

    /**
     * Is our Treater the currently executing thread?
     * If not, you can use {@link #post(Runnable)} to do work on that thread.
     * @return true if {@link Thread#currentThread()} is this queue's Treater
     */
    public final boolean isCurrentThreadTreater()
    {
        return (Thread.currentThread() == treater);
    }

    /**
     * {@link InboundMessageQueue}'s internal single-threaded reader to de-queue each message
     * stored in the {@link #inQueue} and send it to the server dispatcher.
     *<P>
     * This thread can be stopped by calling {@link #stopTreater()}.
     *<P>
     * Before v2.0.00 this class was {@code Server.Treater}.
     *
     * @author Alessandro
     */
    final class Treater extends Thread
    {

        /**
         * Is the Treater started and running? Controls the processing of messages:
         * While true, keep looping. When this flag becomes false, Treater's
         * {@link #run()} will exit and end the thread.
         */
        private volatile boolean processMessage;

        public Treater()  // Server parameter is also passed in, since this is an inner class
        {
            setName("treater");  // Thread name for debug
            processMessage = true;
        }

        public void stopTreater()
        {
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
                        if (messageData.run != null)
                            messageData.run.run();
                        else
                            dispatcher.dispatch(messageData.stringMessage, messageData.clientSender);
                    }
                }
                catch (Exception e)  // for anything thrown by bugs in server or game code called from dispatch
                {
                    System.out.println("Exception in treater (dispatch) - " + e.getMessage());
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
                            inQueue.wait(1000);  // timeout to help avoid deadlock
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
     * Nested class to store a message's contents and sender.
     * For simplicity and quick access, final fields are used instead of getters.
     *<P>
     * Before v2.0.00 this class was {@code soc.server.genericServer.Server.Command},
     * with fields {@code str} and {@code con}.
     */
    private static class MessageData
    {
        /** Message data contents in text format */
        public final String stringMessage;

        /** Client which sent this message */
        public final StringConnection clientSender;

        /**
         * Or, some code to run on our Treater thread
         * @since 1.2.00
         */
        public final Runnable run;

        public MessageData(final String stringMessage, final StringConnection clientSender)
        {
            this.stringMessage = stringMessage;
            this.clientSender = clientSender;
            this.run = null;
        }

        public MessageData(final Runnable run)
        {
            this.run = run;
            this.stringMessage = null;
            this.clientSender = null;
        }

    }


}
