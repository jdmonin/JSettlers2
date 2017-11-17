# Java Settlers Web App

A browser-based client-server version of Settlers of Catan


## Introduction

JSettlers-WebApp is a browser-based version of the board game
Settlers of Catan written in Java and HTML5. This client-server
system supports multiple simultaneous games between people and
computer-controlled opponents. Initially created as an AI research
project.

The client is browser-based in HTML5. The server is a Java web app
hosted in a servlet container like Jetty, including the html5 website.

The server can optionally use a database to store player account
information and game stats (details below).  A client java app to
create user accounts is also provided.

JSettlers-WebApp is an open-source project licensed under (TBD).
The project is hosted at https://github.com/jdmonin/jsettlers-webapp/
and at http://nand.net/jsettlers/devel/ .  Questions, bugs, patches,
and pull requests can be posted at the github page. The server's
game engine is https://github.com/jdmonin/JSettlers2/ .

\- The JSettlers-WebApp Development Team


## Contents

-  TBD -- This is under construction
-  Documentation
-  Requirements
-  Server Setup and Testing
-  Development and Building JSettlers-WebApp


## Documentation

Currently, this Readme and the `doc` directory are the only technical
documentation for running the client or server, setup and other issues.
Over time, more docs will be written. If you are interested in helping
write documentation please contact the development team from our github page.

the official location of this Readme and the docs is online at
https://github.com/jdmonin/jsettlers-webapp/blob/master/Readme.md .


## Requirements

To play JSettlers in a browser, you will need one new enough for HTML5
and Javascript version ES6.

To run the server, you will need a Java servlet container such as Jetty
and the `jsettlers-server` JAR v2.0.00 or newer from
https://github.com/jdmonin/JSettlers2/releases .

To Play JSettlers locally you need the Java Runtime 5 or above.
`JSettlers-full.jar` can connect directly to any server over the Internet.

To host a JSettlers server that provides a web applet for clients, you will
need an http server such as Apache's httpd, available from http://httpd.apache.org.

The JSettlers-full.jar file can also run locally as a server, without
needing a web server.  The applet is considered more convenient,
because you know everyone will have the same version.

To build JSettlers-WebApp from source, you will need Java JDK 1.7 or newer and Gradle,
or an IDE such as Eclipse which understands Gradle's build format.
See [doc/Readme.developer.md](doc/Readme.developer.md) for details.


## Server Setup and Testing

TBD

### Server Startup

### Parameters and game option defaults:

### jsserver.properties:

### Connect a client

### Server shutdown

### Installing a JSettlers-WebApp server

#### Checklist:

- TBD

#### Details:

## Development and Building JSettlers-WebApp

JSettlers-WebApp is an open-source project licensed under (TBD). The project
source code is hosted at https://github.com/jdmonin/jsettlers-webapp/ and
the project website is http://nand.net/jsettlers/devel/ . Questions,
bugs, patches, and pull requests can be posted at the github page.

For more information on building or developing JSettlers-WebApp, see
[doc/Readme.developer.md](doc/Readme.developer.md). That readme also has
information about translating jsettlers to other languages; see the
"I18N" section.

JSettlers-WebApp is licensed under (TBD full name).
Each source file lists contributors by year. A copyright year range (for
example, 2007-2011) means the file was contributed to by that person in
each year of that range. See individual source files for the license
heading and other details.

