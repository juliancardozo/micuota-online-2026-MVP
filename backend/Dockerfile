FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /src

COPY pom.xml /src/pom.xml
COPY src /src/src

RUN mvn -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /src/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "if [ -n \"$DB_URL\" ] && [ \"${DB_URL#postgresql://}\" != \"$DB_URL\" ]; then export DB_URL=\"jdbc:$DB_URL\"; fi; exec java -jar /app/app.jar"]
