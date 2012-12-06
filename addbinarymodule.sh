#!/bin/bash

DSPACE_SRC="/Users/richard/tmp/DSpace"
MAVEN="mvn"

# copy the relevant contents of the root config file
cp config/input-forms.xml $DSPACE_SRC/dspace/config
cp config/workflow.xml $DSPACE_SRC/dspace/config
cp config/xmlui.xconf $DSPACE_SRC/dspace/config

# copy the crosswalks
cp config/crosswalks/* $DSPACE_SRC/dspace/config/crosswalks/

# copy the module configuration
cp config/modules/* $DSPACE_SRC/dspace/config/modules/

# copy the registries
cp config/registries/* $DSPACE_SRC/dspace/config/registries

# copy the spring configuration for workflows
cp config/spring/api/* $DSPACE_SRC/dspace/config/spring/api/
cp config/spring/xmlui/* $DSPACE_SRC/dspace/config/spring/xmlui/

# copy the customised messages fle
cp deploy/messages.xml $DSPACE_SRC/dspace/modules/xmlui/overlays/org.dspace.dspace-xmlui-lang-1.8.0.2/i18n/

# copy the javascript for the workflow
cp deploy/bitstream-reorder-workflow.js $DSPACE_SRC/dspace-xmlui/dspace-xmlui-webapp/src/main/webapp/static/js/

# install the module
$MAVEN install:install-file -Dfile=duo-1.0.jar -DpomFile=pom.xml

# send the poms over to incorporate the dependency
mv $DSPACE_SRC/dspace/pom.xml $DSPACE_SRC/dspace/original.pom.xml
cp deploy/dspace.pom.xml $DSPACE_SRC/dspace/pom.xml

mv $DSPACE_SRC/dspace/modules/pom.xml $DSPACE_SRC/dspace/modules/original.pom.xml
cp deploy/modules.pom.xml $DSPACE_SRC/dspace/modules/pom.xml