FROM adoptopenjdk
EXPOSE 8880
ARG JAVA_OPTS
ENV JAVA_OPTS=$JAVA_OPTS
RUN mkdir /opt/app
WORKDIR /opt/app
COPY jsettlers-2.2.00-server.jar /opt/app/japp.jar
COPY mariadb-java-client-2.6.0.jar /opt/app/mariadbclient.jar
#COPY jsserver.properties /opt/app/jsserver.properties
ENTRYPOINT exec java $JAVA_OPTS -jar /opt/app/japp.jar
