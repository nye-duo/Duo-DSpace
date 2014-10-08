#!/bin/bash

DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
MAVEN="mvn"

# copy everything except the readme and the dspace.cfg extension
shopt -s extglob
cd config
cp -r !(dspace.cfg|README.md) $DSPACE_SRC/dspace/config
cd ..

# NOTE: the following lines are all superseded by the above line, and could be removed in the long run ...
# copy the relevant contents of the root config file
#cp config/input-forms.xml $DSPACE_SRC/dspace/config
#cp config/workflow.xml $DSPACE_SRC/dspace/config
#cp config/xmlui.xconf $DSPACE_SRC/dspace/config

# copy the crosswalks
#cp -r config/crosswalks/* $DSPACE_SRC/dspace/config/crosswalks/

# copy the module configuration
#cp -r config/modules/* $DSPACE_SRC/dspace/config/modules/

# copy the registries
#cp -r config/registries/* $DSPACE_SRC/dspace/config/registries/

# copy the emails
#cp -r config/emails/* $DSPACE_SRC/dspace/config/emails/

# copy the spring configuration for workflows
#cp -r config/spring/api/* $DSPACE_SRC/dspace/config/spring/api/
#cp -r config/spring/xmlui/* $DSPACE_SRC/dspace/config/spring/xmlui/

# copy the customised messages file
mkdir -p $DSPACE_SRC/dspace/modules/xmlui/src/main/webapp/i18n/
cp deploy/messages.xml $DSPACE_SRC/dspace/modules/xmlui/src/main/webapp/i18n/

# copy the javascript for the workflow
mkdir -p $DSPACE_SRC/dspace/modules/xmlui/src/main/webapp/static/js/
cp deploy/bitstream-reorder-workflow.js $DSPACE_SRC/dspace/modules/xmlui/src/main/webapp/static/js/

# build and install the module
$MAVEN install

# send the poms over to incorporate the dependency
# this one makes sure that the dependency ends up in the DSpace lib directory
mv $DSPACE_SRC/dspace/pom.xml $DSPACE_SRC/dspace/original.pom.xml
cp deploy/dspace.pom.xml $DSPACE_SRC/dspace/pom.xml

# this one makes sure it is available in the webapps (I think!)
mv $DSPACE_SRC/dspace/modules/pom.xml $DSPACE_SRC/dspace/modules/original.pom.xml
cp deploy/modules.pom.xml $DSPACE_SRC/dspace/modules/pom.xml