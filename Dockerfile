FROM gradle:8.12.0-jdk21 AS build-stage

WORKDIR /home/gradle
COPY . .

RUN gradle clean build --no-daemon

FROM azul/zulu-openjdk:21

COPY --from=build-stage /home/gradle/build/libs/request-service-*.jar /request-service.jar


ENTRYPOINT ["java","-jar", "/request-service.jar" ]
HEALTHCHECK CMD curl --fail http://localhost:8080/actuator/health || exit