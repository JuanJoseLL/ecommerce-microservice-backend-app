FROM openjdk:11-jre-slim

WORKDIR /app

COPY target/service-discovery-v0.1.0.jar app.jar

ENV SPRING_PROFILES_ACTIVE=kubernetes

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"] 