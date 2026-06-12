FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn .mvn

RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests dependency:go-offline

COPY src src

RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "if [ -n \"$FIREBASE_CREDENTIALS_BASE64\" ]; then mkdir -p /app/firebase && echo \"$FIREBASE_CREDENTIALS_BASE64\" | base64 -d > /app/firebase/firebase.json; fi && java $JAVA_OPTS -jar /app/app.jar"]
