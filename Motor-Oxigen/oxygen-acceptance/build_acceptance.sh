#! /bin/bash
if ! command -v mvn &> /dev/null
then
  echo "mvn command now found."
  echo "Please install as indicated in instructions and add to PATH variable."
  echo
  exit
fi

# mvn command found -> build package
mvn -DskipTests clean package
