FROM openjdk:13
MAINTAINER Mike Travers "mt@alum.mit.edu"

ENV PORT 1996
EXPOSE 1996

ADD target ~/target
WORKDIR ~/target

ENTRYPOINT ["java", "-jar", "enflame-standalone.jar", "opencandel-config.edn"]
