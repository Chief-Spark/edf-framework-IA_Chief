FROM python:3.11-slim-bullseye

ARG spark_version=3.3.1
ARG hadoop_version=3
ARG scala_version=2.12.15

ENV SPARK_HOME /opt/spark
ENV PATH $PATH:${SPARK_HOME}/bin:/usr/local/scala/bin

RUN apt-get update && apt-get install -y wget bash openjdk-11-jdk && rm -rf /var/lib/apt/lists/*

RUN apt-get update && apt-get install -y procps && rm -rf /var/lib/apt/lists/*

RUN wget --no-check-certificate https://downloads.lightbend.com/scala/${scala_version}/scala-${scala_version}.tgz && \
   tar xvf scala-${scala_version}.tgz && \
   mv scala-${scala_version} /usr/local/scala && \
   rm scala-${scala_version}.tgz

RUN wget --no-check-certificate https://archive.apache.org/dist/spark/spark-${spark_version}/spark-${spark_version}-bin-hadoop${hadoop_version}.tgz && \
   tar xvf spark-${spark_version}-bin-hadoop${hadoop_version}.tgz && \
   mv spark-${spark_version}-bin-hadoop${hadoop_version} ${SPARK_HOME} && \
   rm spark-${spark_version}-bin-hadoop${hadoop_version}.tgz

WORKDIR /opt/python

COPY requirements.txt .

RUN apt-get clean && rm -rf /var/lib/apt/lists/* && \
    apt-get update --fix-missing && \
    apt-get install -y nano python python3-pip

RUN pip install --trusted-host pypi.org --trusted-host files.pythonhosted.org --upgrade pip && pip install --trusted-host pypi.org --trusted-host files.pythonhosted.org -r requirements.txt

EXPOSE 8888

WORKDIR /data
