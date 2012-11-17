DUO Extensions for DSpace
=========================

This module contains plugins, extensions, and configuration for a DSpace implementation which will

1. Support SWORDv2 deposit from StudentWeb
2. Support harvesting content directly from CRISTIN

Dependencies
------------

###BagIt

This library depends on the related BagIt library, which must be installed as per the instructions here:

[https://github.com/nye-duo/BagItLibrary](https://github.com/nye-duo/BagItLibrary)

###DSpace

It is designed to be installed into the Duo version of DSpace here:

[https://github.com/nye-duo/DSpace/tree/duo](https://github.com/nye-duo/DSpace/tree/duo)

This can be obtained with the following commands:

    git clone https://github.com/nye-duo/DSpace.git
    git checkout duo

This will be the source of your ultimate DSpace installation

###Duo-DSpace

You will also need to check this code library out of GitHub, which you can do with 

    git clone https://github.com/nye-duo/Duo-DSpace.git

You can then carry on with your preferred installation process below


Binary Installation (recommended)
---------------------------------

1. Customise the addbinarymodule.sh and postinstall.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

2. Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.

3. Run the addbinarymodule.sh script to prepare the dspace source to be built with the duo code incorporated.  This will install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build.

4. Append/Replace the values in dspace.cfg with the duo values (see the config/dspace.cfg in the duo binary release directory for fields required)

5. Build and install DSpace as normal

6. If you're installing for the first time on this DSpace instance, you should run the postinstall.sh script.  DO NOT RUN THIS SCRIPT ON A DSPACE UPON WHICH IT HAS PREVIOUSLY BEEN RUN.

7. Restart tomcat

7. Set up the cron job for lifting embargoes, which will need to use the command:

	./dspace embargo-lifter


Source Installation
-------------------

Since the source installation requires Duo-DSpace to be compiled against a modified version of DSpace, it is necessary to install that modified DSpace into the local maven repository /before/ following the steps below.  This can be done with:

    mvn install -Dlicense.skip=true
    
in the root of the modified DSPace instance.


1. Customise the addmodule.sh and postinstall.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

2. Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.

3. Run the addmodule.sh script to prepare the dspace source to be built with the duo code.  This will compile and install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build

4. Append/replace the values in dspace.cfg with the duo values (see config/dspace.cfg in the duo code for fields required)

5. Build and install DSpace as normal

6. If you're installing for the first time on this DSpace instance, you should run the postinstall.sh script.  DO NOT RUN THIS SCRIPT ON A DSPACE UPON WHICH IT HAS PREVIOUSLY BEEN RUN.

7. Restart tomcat

8. Set up the cron job for lifting embargoes, which will need to use the command:

	./dspace embargo-lifter


Manual Installation on running DSpace (only if you know what you're doing)
--------------------------------------------------------------------------

1. Build the code

    mvn clean package

2. Obtain the dependencies

    mvn dependency:copy-dependencies

3. Deploy to installed DSpace: Copy the duo-1.0.jar and its dependencies to the DSpace swordv2 webapp's WEB-INF/lib directory, and the DSpace lib directory (ensuring not to overwrite any existing .jar files in the DSpace lib directory)

4. Deploy the config: copy all the config files provided (except dspace.cfg), including xsl crosswalks (you might want to update the configs with localised values)

5. Update the DSpace config: add the values in the dspace.cfg file into the main DSpace config file

6. Update the database with the xml-workflow schema

7. copy poms/bitstream-reorder-workflow.js to the XMLUI webapp's static/js directory

8. copy poms/messages.xml to the XMLUI webapp's i18n directory

7. Import the required metadata schemas:

	./dspace dsrun org.dspace.administer.MetadataImporter -f config/registries/fs-metadata.xml
	
	./dspace dsrun org.dspace.administer.MetadataImporter -f config/registries/cristin-metadata.xml

9. Restart tomcat

10. Set up the cron job for lifting embargoes, which will need to use the command:

	./dspace embargo-lifter