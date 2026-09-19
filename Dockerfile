FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system ebuddy && adduser --system --ingroup ebuddy ebuddy
COPY target/ebuddy-backend-1.0.0.jar app.jar
USER ebuddy
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
