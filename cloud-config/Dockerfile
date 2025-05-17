
FROM openjdk:11
WORKDIR /home/app
COPY target/cloud-config.jar app.jar
EXPOSE 9296
ENTRYPOINT ["java", "-jar", "app.jar"]


