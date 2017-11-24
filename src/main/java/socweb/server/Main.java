/*
 * This file is part of the Java Settlers Server Web App.
 *
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
 * The maintainer of this program can be reached at https://github.com/jdmonin/JSettlers2
 */

package socweb.server;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;

import soc.server.SOCServer;
import soc.server.genericServer.Server;
import soc.util.Version;

/**
 * Main class to run web app's SOCServer as a background thread.
 */
public class Main implements ServletContextListener, ServletRequestListener
{
    /** The SOCServer started here; shared by all servlets in the web app context. */
    static SOCServer srv;

    private ServletContext ctx;

    /**
     * Thread executor to start and run the server.
     */
    private ExecutorService srvRunner;

    public void contextInitialized(ServletContextEvent e)
    {
        srvRunner = Executors.newSingleThreadExecutor();
        ctx = e.getServletContext();
        ctx.log("Main: contextInitialized");
        srvRunner.submit(new Runner());
    }

    public void contextDestroyed(ServletContextEvent e)
    {
        if (srv != null)
        {
            srv.stopServer();
            srv = null;
            // TODO wait for any server shutdown needed?
        }
        srvRunner.shutdownNow();  // TODO is .shutdown() too polite?
    }

    /**
     * When a new request arrives, use {@link HttpServletRequest#getSession()}
     * create a session for it if needed, so won't be null after WebSocket upgrade.
     */
    public void requestInitialized(ServletRequestEvent e)
    {
        // Needed per https://stackoverflow.com/questions/20240591/websocket-httpsession-returns-null
        // and https://stackoverflow.com/questions/17936440/accessing-httpsession-from-httpservletrequest-in-a-web-socket-serverendpoint
        // for JSR356 (updated Nov 2016) websocket connections.

        ServletRequest sr = e.getServletRequest();
        if (sr instanceof HttpServletRequest)
            ((HttpServletRequest) sr).getSession();
    }

    public void requestDestroyed(ServletRequestEvent e) {}

    /**
     * Main thread for SOCServer; constructs one, sets {@link Main#srv}, calls {@link SOCServer#run()}.
     * If {@code srv} is already set somehow, does nothing.
     */
    public class Runner implements Runnable
    {
        public void run()
        {
            if (srv != null)
            {
                ctx.log("Runner not starting: Internal error, Main.srv already != null");
                return;
            }

            ctx.log("SOCServer Runner starting.");
            ctx.log("JSettlers Server version " + Version.version() + " build " + Version.buildnum());
            try
            {
                Properties pr = new Properties();  // TODO how to specify properties operationally at runtime
                pr.put(Server.PROP_SERVER_PROTOBUF, "Y");
                pr.put(SOCServer.PROP_JSETTLERS_BOTS_SHOWCOOKIE, "Y");  // for bot testing
                srv = new SOCServer(SOCServer.SOC_PORT_DEFAULT, pr);
            } catch (Throwable th) {
                srv = null;
                ctx.log("SOCServer startup failed", th);
                return;
            }

            try
            {
                srv.run();
            } finally {
                srv = null;
                ctx.log("SOCServer Runner shutting down.");
            }
        }
    }

    public static void main(String args[])
    {
        System.out.println
            ("This class launches SOCServer within its servlet container, and must be started from within a server like Jetty or Tomcat.");
        System.exit(1);
    }

}