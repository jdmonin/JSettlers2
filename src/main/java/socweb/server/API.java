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

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import soc.util.Version;

/**
 * API paths, for JSON requests and their responses.
 */
@WebServlet("/api/*")
public class API extends HttpServlet
{
    private static final long serialVersionUID = 3000L;  // 3.0.00

    private ServletContext ctx;

    @Override
    public void init() {
        ctx = getServletContext();
    }

    @Override
    public void destroy() {
        ctx = null;
    }

    @Override
    public void service(final HttpServletRequest req, final HttpServletResponse resp)
        throws ServletException, IOException
    {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // TODO dispatch based on request.getPathInfo():
        //   /socserver/api   null
        //   /socserver/api/  "/"
        //   /socserver/api/something  "/something"
        //   /socserver/api/somethingElse?abc=123  "/somethingElse"

        // TODO use a json library
        StringBuilder sb = new StringBuilder("{");
        sb.append("version: \"");
        sb.append(Version.version());  // per convention, won't contain " or '
        sb.append("\", buildnum: \"");
        sb.append(Version.buildnum());
        sb.append("\", getPathInfo: \"");
        String path = req.getPathInfo();
        if (path != null)
            sb.append(path.replace("\"", "\\\""));
        else
            sb.append("(null)");
        sb.append("\"}");
        PrintWriter out = resp.getWriter();
        out.print(sb.toString());
        out.flush();
    }

}
