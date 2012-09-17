#!/bin/bash

DSPACE="/Users/richard/tmp/DSpace"
MAVEN="mvn"

# copy the relevant contents of the root config file
cp config/input-forms.xml $DSPACE/dspace/config
cp config/workflow.xml $DSPACE/dspace/config
cp config/xmlui.xconf $DSPACE/dspace/config

# copy the crosswalks
cp config/crosswalks/* $DSPACE/dspace/config/crosswalks/

# copy the module configuration
cp config/modules/* $DSPACE/dspace/config/modules/

# copy the registries
cp config/registries/* $DSPACE/dspace/config/registries

# copy the spring configuration for workflows
cp config/spring/api/* $DSPACE/dspace/config/spring/api/

# build and install the module
$MAVEN install

# send the pom over to incorporate the dependency
mv $DSPACE/dspace-api/pom.xml $DSPACE/dspace-api/original.pom.xml
cp poms/dspace-api.pom.xml $DSPACE/dspace-api/pom.xml
