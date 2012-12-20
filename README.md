DUO Extensions for DSpace
=========================

This module contains plugins, extensions, and configuration for a DSpace implementation which will

1. Support SWORDv2 deposit from StudentWeb
2. Support harvesting content directly from CRISTIN

How to use this documentation
-----------------------------

Before going on to any form of installation, be sure that the Dependencies are satisfied, as documented in the next section

If you are installing a fresh DSpace, you can safely follow the sections **Fresh Installation** section.

If you are installing on an existing DSpace instace, you should follow the section **Update existing DSpace**

Dependencies
------------

###Java 1.6

Both DSpace 1.8.2 and the code provided here are dependent on a Java 1.6 installation.  It may well not work with the 1.7 version.

###Maven 2.2

The build of some parts of the system won't work properly with a Maven version prior to 2.2, but later versions of Maven such as Maven 3 should also be fine.

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

####SWORDv2 Server Library

In order to build this you will also need to have installed the new swordv2-server library upon which this version of DSpace depends.  This can be obtained with

    git clone https://github.com/swordapp/JavaServer2.0.git

and then installed to the local maven repository with

    mvn install

###Duo-DSpace

You will also need to check this code library out of GitHub, which you can do with 

    git clone https://github.com/nye-duo/Duo-DSpace.git

Or you can download the binary version from:

[https://github.com/nye-duo/Duo-DSpace/blob/master/release/dspace-duo-1.0.tar.gz](https://github.com/nye-duo/Duo-DSpace/blob/master/release/dspace-duo-1.0.tar.gz)

You can then carry on with your preferred installation process below


Fresh Installation
------------------

###Binary Installation (recommended)

1. Customise the addbinarymodule.sh and postinstall.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

2. Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.

3. Run the addbinarymodule.sh script to prepare the dspace source to be built with the duo code incorporated.  This will install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build.

Now go on to the **Common Installation Steps**


###Source Installation

Since the source installation requires Duo-DSpace to be compiled against a modified version of DSpace, it is necessary to install that modified DSpace into the local maven repository /before/ following the steps below.  This can be done with:

    mvn install -Dlicense.skip=true
    
in the root of the modified DSpace instance.

1. Customise the addmodule.sh and postinstall.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

2. Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.

3. Run the addmodule.sh script to prepare the dspace source to be built with the duo code.  This will compile and install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build

Now go on to the **Common Installation Steps**


###Common Installation Steps

4. Append/replace the values in dspace.cfg with the duo values (see config/dspace.cfg in the duo code for fields required)

5. Build and install DSpace as normal

6. If you're installing for the first time on this DSpace instance, you should customise and run the postinstall.sh script.  DO NOT RUN THIS SCRIPT ON A DSPACE UPON WHICH IT HAS PREVIOUSLY BEEN RUN.

7. Restart tomcat

8. Set up the cron job for lifting embargoes, which will need to use the command:

	./dspace embargo-lifter

Update existing DSpace
----------------------

This approach should be used if you want to install the Duo Extensions onto an existing vanilla DSpace instance.

Start by following **Preparing the DSpace source** and then choose one of **Binary Installation** or **Source Installation** and finish with the **Custom Installation Steps**

###Preparing the DSpace source

1. Checkout the Duo version of DSpace listed in the **Dependencies** section above

2. Migrate all of your existing localisations for your DSpace installation into this newly checked out version of DSpace (this will include things such as your themes, custom/modified DSpace classes, and configuration)

3. The Duo Extensions will overwrite any existing messages files and config files for your DSpace, so you should be sure
to merge the provided messages and configs with your existing ones, and place them into the config and deploy directories
within the Duo Extension codebase before proceeding.

Configuration can all be found in the sub-directory:

    config

The Duo Extension messages file can be found in the sub-directory:

    deploy

During installation, the files in these two directories will overwrite any files in the DSpace source directories, so
you shoud merge your custom configuration into the files in the directories within this package.

(At the University of Oslo, this can be partially automated by customising and running the migratedspace.sh script provided)


###Binary Installation (recommended)

1. Customise the addbinarymodule.sh and postinstall.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

2. Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.

3. Run the addbinarymodule.sh script to prepare the dspace source to be built with the duo code incorporated.  This will install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build.

Now go on to the **Common Installation Steps**


###Source Installation

Since the source installation requires Duo-DSpace to be compiled against a modified version of DSpace, it is necessary to install that modified DSpace into the local maven repository /before/ following the steps below.  This can be done with:

    mvn install -Dlicense.skip=true

in the root of the modified DSPace instance.

1. Customise the addmodule.sh and postupdate.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

2. Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.

3. Run the addmodule.sh script to prepare the dspace source to be built with the duo code.  This will compile and install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build

Now go on to the **Common Installation Steps**


###Common Installation Steps

4. Append/replace the values in dspace.cfg with the duo values (see config/dspace.cfg in the duo code for fields required)

5. Build and update DSpace as normal

6. If you're installing for the first time on this DSpace instance, you should customise and run the postupdate.sh script.  DO NOT RUN THIS SCRIPT ON A DSPACE UPON WHICH IT HAS PREVIOUSLY BEEN RUN.

7. Restart tomcat

8. Set up the cron job for lifting embargoes, which will need to use the command:

	./dspace embargo-lifter


Setting up a Cristin Workflow
-----------------------------

Once you have set up a Collection for harvesting from Cristin, you need to enable the correct workflow for it.  To do this edit the file

	[dspace]/config/workflow.xml

And add a name-map reference in the heading section of the file, mapping your collection's handle to the "cristin" workflow, for example:

	<name-map collection="123456789/4404" workflow="cristin"/>

For this change to take effect, you will need to restart tomcat.


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
