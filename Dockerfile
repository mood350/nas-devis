FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/nasimmobilier-*.jar app.jar
# Pas de root dans le conteneur
RUN useradd --system --uid 1001 app
USER app
EXPOSE 8080
# curl/wget absents de l'image : requete HTTP via bash. /api/units passe par la base : un 200 prouve aussi la connexion SQL.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /api/units?size=1 HTTP/1.0\r\n\r\n" >&3 && head -n1 <&3 | grep -q " 200 "'
ENTRYPOINT ["java", "-jar", "app.jar"]
