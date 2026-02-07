# 使用 Maven 构建镜像作为构建阶段
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# 复制 settings.xml 用于加速依赖下载
COPY settings.xml .

# 1. 首先只复制 pom.xml 文件以利用 Docker 缓存层
COPY pom.xml .
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY user-service/pom.xml user-service/
COPY course-service/pom.xml course-service/
COPY homework-service/pom.xml homework-service/
COPY progress-service/pom.xml progress-service/

# 下载依赖（即使源码未变，依赖层也会被缓存）
# 使用 -s settings.xml 指定配置文件
RUN mvn dependency:go-offline -B -s settings.xml

# 2. 复制源码并构建
COPY . .
# 使用 -s settings.xml 指定配置文件
RUN mvn clean package -DskipTests -s settings.xml

# 3. 运行阶段
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 从构建阶段复制 jar 包
COPY --from=build /app/gateway/target/*.jar gateway.jar
COPY --from=build /app/user-service/target/*.jar user-service.jar
COPY --from=build /app/course-service/target/*.jar course-service.jar
COPY --from=build /app/homework-service/target/*.jar homework-service.jar
COPY --from=build /app/progress-service/target/*.jar progress-service.jar

# 复制启动脚本
COPY wait-for-it.sh .
RUN chmod +x wait-for-it.sh

# 接收构建参数
ARG MODULE_NAME
# 设置环境变量
ENV APP_FILE=${MODULE_NAME}.jar

# 启动命令
CMD java -jar ${APP_FILE}
