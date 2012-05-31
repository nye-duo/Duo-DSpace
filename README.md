DUO Extensions for DSpace
=========================

This module contains plugins, extensions, and configuration for a DSpace implementation which will

1. Support SWORDv2 deposit from StudentWeb
2. Support harvesting content directly from CRISTIN


Installation
------------

FIXME: this should be created installed overlay, to avoid complex installation

1. Build the code

    mvn clean package

2. Obtain the dependencies

    mvn dependency:copy-dependencies

3. Deploy to installed DSpace: Copy the duo-1.0.jar and its dependencies to the DSpace swordv2 webapp's WEB-INF/lib directory, and the DSpace lib directory

4. Deploy the config: Copy the swordv2-server.cfg and studentweb.cfg files to the equivalent directory in DSpace

5. Update the DSpace config: add the values in the dspace.cfg file into the main DSpace config file

6. Deploy the crosswalk: Copy the fs-metadata.xml crosswalk into the appropriate directory in DSpace

7. Import the required metadata schema:

	./dspace dsrun org.dspace.administer.MetadataImporter -f /home/richard/Code/External/Duo-DSpace/config/registries/fs-metadata.xml

8. Restart tomcat

9. Set up the cron job for lifting embargoes, which will need to use the command:

	./dspace embargo-lifter

