FROM azul/zulu-openjdk-alpine:11.0.13

RUN apk add --update \
    curl \
    && rm -rf /var/cache/apk/*

RUN addgroup --gid 1000 jumper && adduser --uid 1000 -G jumper -D jumper --no-create-home

USER 1000:1000

EXPOSE 8080 8082

COPY application.jar /usr/share/
COPY app.pem /usr/share/keypair/

WORKDIR /usr/share/

CMD java $JVM_OPTS -jar /usr/share/application.jar

#CMD ["java", "$JVM_OPTS", "-jar", "/usr/share/application.jar"]
