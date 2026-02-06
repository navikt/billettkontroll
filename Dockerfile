FROM gcr.io/distroless/java25-debian13

WORKDIR /app

ENV TZ=Europe/Oslo

COPY target/*.jar app.jar
COPY target/lib/ lib/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
