#!/bin/bash

DSPACE_SRC="/Users/richard/tmp/DSpace"
DSPACE="/srv/dspace/dspace-duo"

# migrate to the configurable workflow
$DSPACE/bin/dspace dsrun org.dspace.storage.rdbms.InitializeDatabase $DSPACE_SRC/dspace/etc/postgres/xmlworkflow/workflow_migration.sql
$DSPACE/bin/dspace dsrun org.dspace.storage.rdbms.InitializeDatabase $DSPACE_SRC/dspace/etc/postgres/xmlworkflow/xml_workflow.sql

# load the metadata registries
$DSPACE/bin/dspace dsrun org.dspace.administer.MetadataImporter -f config/registries/fs-metadata.xml
$DSPACE/bin/dspace dsrun org.dspace.administer.MetadataImporter -f config/registries/cristin-metadata.xml

# copy over the configuration
#
# copy the relevant contents of the root config file
mv $DSPACE/config/dspace.cfg $DSPACE/config/dspace.cfg.old
mv $DSPACE/config/dspace.cfg.new $DSPACE/config/dspace.cfg
cp $DSPACE_SRC/dspace/config/input-forms.xml $DSPACE/config
cp $DSPACE_SRC/dspace/config/workflow.xml $DSPACE/config
cp $DSPACE_SRC/dspace/config/xmlui.xconf $DSPACE/config

# copy the crosswalks
cp $DSPACE_SRC/dspace/config/crosswalks/* $DSPACE/config/crosswalks/

# copy the module configuration
cp $DSPACE_SRC/dspace/config/modules/* $DSPACE/config/modules/

# copy the registries
cp $DSPACE_SRC/dspace/config/registries/* $DSPACE/config/registries

# copy the emails
cp $DSPACE_SRC/dspace/config/emails/* $DSPACE/config/emails

# copy the spring configuration for workflows
cp $DSPACE_SRC/dspace/config/spring/api/* $DSPACE/config/spring/api/
cp $DSPACE_SRC/dspace/config/spring/xmlui/* $DSPACE/config/spring/xmlui/