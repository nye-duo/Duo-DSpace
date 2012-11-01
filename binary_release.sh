#!/bin/bash

OUT="./dspace-duo-1.0"
MAVEN="mvn"

# compile the library
$MAVEN clean package

# make the output directory
rm -r $OUT
mkdir $OUT

# copy the code into the output directory
cp pom.xml $OUT
cp target/duo-1.0.jar $OUT

# copy all the supporting material into the output directory
cp -r config $OUT
cp -r poms $OUT
cp postinstall.sh $OUT
cp addbinarymodule.sh $OUT
cp README.md $OUT

tar -zcvf dspace-duo-1.0.tar.gz $OUT