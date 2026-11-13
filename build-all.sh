#!/bin/sh
mvn clean -f ./master
mvn verify -am -f ./master -pl :client -Djavafx.platform=win
mvn verify -am -f ./master -pl :client -Djavafx.platform=linux
mvn verify -am -f ./master -pl :client -Djavafx.platform=mac
mvn verify -am -f ./master -pl :server