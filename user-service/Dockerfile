
FROM openjdk:11
WORKDIR /home/app
ENV SPRING_PROFILES_ACTIVE dev
COPY target/user-service.jar app.jar
EXPOSE 8700
ENTRYPOINT ["java", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "-jar", "app.jar"]


