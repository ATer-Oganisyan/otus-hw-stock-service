FROM alpine:3.14
EXPOSE 8000
ARG HOST
ARG PORT
ARG USER
ARG PASSWRORD
ARG DB
WORKDIR /www
RUN apk update
RUN apk add openjdk11
RUN apk add git 
RUN git clone https://github.com/ATer-Oganisyan/otus-hw-stock-service.git 
RUN cd otus-hw-stock-service && jar xf mysql.jar && javac StockService.java && apk del git && rm StockService.java
ENTRYPOINT java -classpath /www/otus-hw-stock-service StockService $HOST $PORT $USER $PASSWRORD $DB v9