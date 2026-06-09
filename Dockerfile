# Build: empacota o jar. Limita os nativos do JavaCV ao Linux para reduzir o
# download. Os testes nativos (OpenCV/ONNX) rodam no CI/local; aqui pulamos.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests "-Djavacpp.platform=linux-x86_64" clean package

# Runtime: JRE 21 + libgomp1, necessária pelos nativos do OpenCV/ONNX Runtime.
FROM eclipse-temurin:21-jre
RUN apt-get update \
	&& apt-get install -y --no-install-recommends libgomp1 \
	&& rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/face.jar face.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "face.jar"]
