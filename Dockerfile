FROM openjdk:13
MAINTAINER Mike Travers "mt@alum.mit.edu"

ENV PORT 1991
EXPOSE 1991

ADD target ~/target
WORKDIR ~/target

ENTRYPOINT ["java", "-jar", "enflame-standalone.jar", "1991", "resources/opencandel-config.edn"]
