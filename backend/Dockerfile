ARG DOCKER_MIRROR=docker.io

FROM ${DOCKER_MIRROR}/library/maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

ARG MODULE_NAME

COPY settings.xml .
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY course-service/pom.xml course-service/pom.xml
COPY homework-service/pom.xml homework-service/pom.xml
COPY progress-service/pom.xml progress-service/pom.xml

# 预下载依赖（仅 pom.xml 变更时才会重新执行此层）
RUN mvn dependency:go-offline -pl ${MODULE_NAME} -am -B --no-transfer-progress -s settings.xml || true

COPY . .
RUN mvn -pl ${MODULE_NAME} -am clean package -DskipTests -Dmaven.test.skip=true -B --no-transfer-progress -s settings.xml

FROM ${DOCKER_MIRROR}/library/eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring

ARG MODULE_NAME
COPY --from=build /app/${MODULE_NAME}/target/*.jar app.jar

USER spring:spring
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+UseStringDeduplication"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
