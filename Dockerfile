# Etapa 1: construir el JAR
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw clean package -DskipTests


# Etapa 2: ejecutar la aplicación
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "if [ -n \"$FIREBASE_CREDENTIALS_BASE64\" ]; then mkdir -p /app/firebase && echo \"$FIREBASE_CREDENTIALS_BASE64\" | base64 -d > /app/firebase/firebase.json; fi && java -jar app.jar"]