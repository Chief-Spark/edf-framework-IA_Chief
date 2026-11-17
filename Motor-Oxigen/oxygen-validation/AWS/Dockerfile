FROM public.ecr.aws/emr-serverless/spark/emr-6.10.0:latest

USER root
ENV JAVA_HOME /etc/alternatives/jre

RUN amazon-linux-extras install java-openjdk11 
RUN alternatives --set java  /usr/lib/jvm/$(ls /usr/lib/jvm | grep java-11 | cut -f 3)/bin/java
# # Use hadoop user and group 
USER hadoop:hadoop