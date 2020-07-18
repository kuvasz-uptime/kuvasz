FROM oracle/graalvm-ce:20.1.0-java11 as graalvm
RUN gu install native-image

COPY . /home/app/kuvasz
WORKDIR /home/app/kuvasz

RUN native-image --no-server -cp build/libs/kuvasz-*-all.jar

FROM frolvlad/alpine-glibc
RUN apk update && apk add libstdc++
EXPOSE 8080
COPY --from=graalvm /home/app/kuvasz/kuvasz /app/kuvasz
ENTRYPOINT ["/app/kuvasz"]
