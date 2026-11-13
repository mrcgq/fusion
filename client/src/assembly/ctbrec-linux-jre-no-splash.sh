#!/bin/bash

DIR="$(dirname "$0")"
pushd "${DIR}"
JAVA=./jre/bin/java
$JAVA -version
$JAVA -Xmx1g -Djdk.gtk.version=3 -Dfile.encoding=utf-8 -jar ${name.final}.jar
popd
