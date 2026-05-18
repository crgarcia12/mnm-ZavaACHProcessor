FROM gradle:7.6-jdk8 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
CMD ["java", "-jar", "app.jar"]
