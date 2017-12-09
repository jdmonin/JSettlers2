Details TBD

Quick notes:

To build and run this web app and its server
you will need Java 7 or higher, gradle, a Java servlet container
such as Jetty or Tomcat, and a checkout of both repositories:

- `v3` branch of server repository https://github.com/jdmonin/JSettlers2/

- `master` branch of this repository https://github.com/jdmonin/jsettlers-webapp/

In the server repo, run `gradle war` or `gradle build` to compile all
components into **build/libs/socserver.war**

In this client repo, run `gradle war` or `gradle build` to assemble all
components into **build/libs/socweb.war**

The server has other run-time requirements (like protobuf JARs) which won't
be part of its .war file: Those must be downloaded and placed into your Jetty
or Tomcat installation. See
https://github.com/jdmonin/JSettlers2/blob/v3/doc/Readme.developer.md
sections "Download required library JARs" and "SOCServer Web Server for
HTML5", and note any command-line flags you may need for Jetty or Tomcat to
use those extra runtime JARs.

