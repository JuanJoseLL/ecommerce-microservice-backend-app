FROM openjdk:11
WORKDIR /home/app
ENV SPRING_PROFILES_ACTIVE=dev
COPY target/api-gateway.jar api-gateway.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "-jar", "api-gateway.jar"]


