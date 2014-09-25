#!/bin/bash

DSPACE_SRC="/Users/richard/tmp/DSpace"
DSPACE="/srv/dspace/dspace-duo"

# migrate to the configurable workflow
$DSPACE/bin/dspace dsrun org.dspace.storage.rdbms.InitializeDatabase $DSPACE_SRC/dspace/etc/postgres/xmlworkflow/workflow_migration.sql
$DSPACE/bin/dspace dsrun org.dspace.storage.rdbms.InitializeDatabase $DSPACE_SRC/dspace/etc/postgres/xmlworkflow/xml_workflow.sql

# load the metadata registries
$DSPACE/bin/dspace dsrun org.dspace.administer.MetadataImporter -f config/registries/fs-metadata.xml
$DSPACE/bin/dspace dsrun org.dspace.administer.MetadataImporter -f config/registries/cristin-metadata.xml
