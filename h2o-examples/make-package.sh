#!/usr/bin/env bash

rm target/*.jar
mvn package -DXX:MaxPermSize=128m -DskipTests -Dclean.skip -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dscalastyle.skip=true -Dmaven.scaladoc.skip=true -Dskip=true
