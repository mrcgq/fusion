#!/bin/bash

DIR=$(dirname $0)
pushd $DIR
JAVA=java
$JAVA -version
$JAVA -Xmx256m -cp "${DIR}:${name.final}.jar" -Dfile.encoding=utf-8 -Dctbrec.config=server.json ctbrec.recorder.server.HttpServer
popd
