from adoptopenjdk/openjdk11:ubuntu

USER root

RUN apt-get update && apt install -y gnupg && curl https://bazel.build/bazel-release.pub.gpg | apt-key add - \
    && echo "deb [arch=amd64] https://storage.googleapis.com/bazel-apt stable jdk1.8" | tee /etc/apt/sources.list.d/bazel.list

RUN apt update && apt install -y bazel && apt full-upgrade -y

RUN apt-get install -y software-properties-common \
    && apt update && add-apt-repository ppa:longsleep/golang-backports && apt-get update \
    && apt-get install -y golang-go git zip unzip python python3-pip vim

ENV GOPATH="${HOME}/go"
ENV PATH="${HOME}/go/bin:${PATH}"
ENV SHELL=/usr/b

RUN mkdir /root/.ssh && echo 'github.com ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAq2A7hRGmdnm9tUDbO9IDSwBK6TbQa+PXYPCPy6rbTrTtw7PHkccKrpp0yVhp5HdEIcKr6pLlVDBfOLX9QUsyCOV0wzfjIJNlGEYsdlLJizHhbn2mUjvSAHQqZETYP81eFzLQNnPHt4EVVUh7VfDESU84KezmD5QlWpXLmvU31/yMf+Se8xhHTvKSCZIFImWwoG6mbUoWf9nzpIoaSjB+weqqUUmpaaasXVal72J+UX2B+2RPW3RcT0eOzQgqlJL3RKrTJvdsjE3JEAvGq3lGHSZXy28G3skua2SmVi/w4yCE6gbODqnTWlg7+wC604ydGXA8VJiS5ap43JXiUFFAaQ==' >> ~/.ssh/known_hosts

RUN go get github.com/bazelbuild/buildtools/buildozer


WORKDIR /app

COPY ./docker/resources/* app/target/scala-2.*/app.jar ./repos.md ./

ENTRYPOINT ["./run_app.sh"]