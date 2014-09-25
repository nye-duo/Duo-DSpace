#!/bin/bash

OUT="./dspace-duo-2.0"
MAVEN="mvn"

# compile the library
$MAVEN clean package

# make the output directory
rm -r $OUT
mkdir $OUT

# copy the code into the output directory
cp pom.xml $OUT
cp target/duo-2.0.jar $OUT
cp -r lib $OUT

# copy all the supporting material into the output directory
cp -r config $OUT
cp -r deploy $OUT
cp postinstall.sh $OUT
cp postupdate.sh $OUT
cp addbinarymodule.sh $OUT
cp README.md $OUT
cp migratedspace.sh $OUT

tar -zcvf dspace-duo-2.0.tar.gz $OUT
mv dspace-duo-2.0.tar.gz release