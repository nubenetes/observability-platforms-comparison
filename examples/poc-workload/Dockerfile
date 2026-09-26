# Multi-Stage Build for Enterprise Air-Gapped OpenShift Deployment
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests || mvn clean package -DskipTests

# Runtime Stage on Minimal Hardened Base Image
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20
USER 1001
WORKDIR /opt/app

COPY --from=builder /workspace/target/*.jar app.jar

# JVM options optimized for cgroups v2, JFR continuous profiling, and container rightsizing
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+ExitOnOutOfMemoryError \
               -XX:+FlightRecorder \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
