FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25

WORKDIR /app

ENV TZ=Europe/Oslo

COPY target/*.jar app.jar
COPY target/lib/ lib/

EXPOSE 8080

CMD ["-jar", "app.jar"]
