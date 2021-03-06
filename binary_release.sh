#!/bin/bash

VERSION="3.1.0"
OUT="./dspace-duo-3.1.0"
MAVEN="mvn"

# compile the library
$MAVEN clean package

# make the output directory
rm -r $OUT
mkdir $OUT

# copy the code into the output directory
cp pom.xml $OUT
cp target/duo-$VERSION.jar $OUT
cp -r lib $OUT

# copy all the supporting material into the output directory
cp -r config $OUT
cp -r deploy $OUT
cp postinstall.sh $OUT
cp postupdate.sh $OUT
cp addbinarymodule.sh $OUT
cp README.md $OUT
cp docs/system/INSTALL.md $OUT
cp migratedspace.sh $OUT

tar -zcvf dspace-duo-$VERSION.tar.gz $OUT
mv dspace-duo-$VERSION.tar.gz release