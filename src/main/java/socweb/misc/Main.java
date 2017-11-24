/*
 * This file is part of the Java Settlers Web App.
 *
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
 *
 * Open-source license: TBD
 *
 * The maintainer of this program can be reached at https://github.com/jdmonin/jsettlers-webapp
 */

package socweb.misc;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Main class -- placeholder until any other needed servlets are in place.
 * May remove later since this repo focuses on client-side code.
 */
public class Main implements ServletContextListener
{
    private ServletContext ctx;

    public void contextInitialized(ServletContextEvent e)
    {
        ctx = e.getServletContext();
        ctx.log("socweb Main: contextInitialized");
    }

    public void contextDestroyed(ServletContextEvent e)
    {
        ctx.log("socweb Main: contextDestroyed");
    }

    public static void main(String args[])
    {
        System.out.println
            ("This class is a placeholder, and must be started from within a server like Jetty or Tomcat.");
        System.exit(1);
    }

}
