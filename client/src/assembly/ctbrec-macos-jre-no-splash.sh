#!/bin/bash

DIR=$(dirname $0)
pushd "$DIR"
JAVA_HOME="$DIR/jre/Contents/Home"
JAVA="$JAVA_HOME/bin/java"
$JAVA -version
$JAVA -Xmx1g -Dfile.encoding=utf-8 -jar ${name.final}.jar
popd
