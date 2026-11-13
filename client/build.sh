#!/bin/bash
mvn clean
mvn -Djavafx.platform=win verify
mvn -Djavafx.platform=linux verify
mvn -Djavafx.platform=mac verify
