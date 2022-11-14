FROM openjdk:8-alpine
MAINTAINER Mat Pasquet <mat@skullery.pasquet.co>

ADD target/skullery-0.0.1-SNAPSHOT-standalone.jar /skullery/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/skullery/app.jar"]
