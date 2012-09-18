DUO Extensions for DSpace
=========================

This module contains plugins, extensions, and configuration for a DSpace implementation which will

1. Support SWORDv2 deposit from StudentWeb
2. Support harvesting content directly from CRISTIN


As a DSpace Module (recommended)
--------------------------------

1. Customise the addmodule.sh and postinstall.sh scripts with the path to the dspace source and maven executable

2. Run the addmodule.sh script to prepare the dspace source to be built with the duo code.  This will install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build

3. append/replace the values in dspace.cfg with the duo values (see config/dspace.cfg in the duo code for the additional fields required)

4. Build and install DSpace as normal

5. If you're installing for the first time on this DSpace instance, you should run the postinstall.sh script.  DO NOT RUN THIS SCRIPT ON A DSPACE UPON WHICH IT HAS PREVIOUSLY BEEN RUN.

6. Restart tomcat

7. Set up the cron job for lifting embargoes, which will need to use the command:

	./dspace embargo-lifter


Manual Installation
-------------------

1. Build the code

    mvn clean package

2. Obtain the dependencies

    mvn dependency:copy-dependencies

3. Deploy to installed DSpace: Copy the duo-1.0.jar and its dependencies to the DSpace swordv2 webapp's WEB-INF/lib directory, and the DSpace lib directory

4. Deploy the config: Copy the swordv2-server.cfg and studentweb.cfg files to the equivalent directory in DSpace

5. Update the DSpace config: add the values in the dspace.cfg file into the main DSpace config file

6. Deploy the crosswalk: Copy the fs-metadata.xml crosswalk into the appropriate directory in DSpace

7. Update the database with the xml-workflow schema

8. Import the required metadata schema:

	./dspace dsrun org.dspace.administer.MetadataImporter -f /home/richard/Code/External/Duo-DSpace/config/registries/fs-metadata.xml

9. Restart tomcat

10. Set up the cron job for lifting embargoes, which will need to use the command:

	./dspace embargo-lifter