#!/bin/sh
exec java -jar "$(dirname "$0")/.mvn/wrapper/maven-wrapper.jar" "$@"
