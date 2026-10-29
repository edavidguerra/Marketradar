# Se construye desde la raíz del repositorio porque el frontend se empaqueta con el backend.
FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -q dependency:go-offline
COPY backend backend
COPY frontend frontend
COPY base-de-datos base-de-datos
RUN mvn -f backend/pom.xml -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system marketradar
USER marketradar
COPY --from=compilacion /app/backend/target/marketradar-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
