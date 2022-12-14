FROM openjdk:17
EXPOSE 8080:8080
RUN mkdir /app
COPY ./build/libs/*.jar /app/ktor-encryption-server.jar
WORKDIR /app
ENTRYPOINT ["java","-jar","/app/ktor-encryption-server.jar"]