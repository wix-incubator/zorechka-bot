from adoptopenjdk/openjdk11:alpine

RUN apk update && \
    apk add bash tzdata procps tcpdump curl jq tree grep htop nload tmux nano mc && \
    rm -rf /var/cache/apk/* && \
    mkdir -p /app

WORKDIR /app

COPY ./docker/resources/run_app.sh app/target/scala-2.*/app.jar ./

ENTRYPOINT ["./run_app.sh"]