DUO Extensions Installation
===========================


How to use this documentation
-----------------------------

Before going on to any form of installation, be sure that the Dependencies are satisfied, as documented in the next section

If you are installing a fresh DSpace, you can safely follow the sections **Fresh Installation** section.

If you are installing on an existing DSpace instance, you should follow the section **Update existing DSpace**

If you are upgrading from Duo 1.8.2 to Duo 4.2, you should follow the section **Upgrading from 1.8.2 to 4.2**

Dependencies
------------

### Java 1.7 or Java 1.8

Both DSpace 4.2 and the code provided here are dependent on a Java 1.7 or 1.8 installation.  DSpace 4.2 will not compile on Java 1.6.

### Maven 3+

DSpace requires Maven 3+ to compile - it may work on earlier versions, but is not recommended.  The Duo extensions will compile under Maven 3+.

### BagIt

This library depends on the related BagIt library, which must be downloaded and compiled as per the instructions here:

[https://github.com/nye-duo/BagItLibrary](https://github.com/nye-duo/BagItLibrary)

You can do this quickly with:

    git clone https://github.com/nye-duo/BagItLibrary.git
    cd BagItLibrary
    mvn clean package

To use this in the build you should, once you have successfully compiled the library, install it into your local maven repository

    mvn install

#### IdService Client

In order to generate URNs for items in the repository using the National Library's API, we need to include the client library which will allow us to connect to it

It is bundled here for your convenience.  Install it into your local maven repository with:

    mvn install:install-file -Dfile=lib/idservice-client/idservice-client.jar -DpomFile=lib/idservice-client/pom.xml

### DSpace

It is designed to be installed into the Duo version of DSpace here:

[https://github.com/nye-duo/DSpace/tree/duo](https://github.com/nye-duo/DSpace/tree/duo)

This can be obtained with the following commands:

    git clone https://github.com/nye-duo/DSpace.git
    git checkout duo42

This will be the source of your ultimate DSpace installation

### Duo-DSpace

You will also need to check this code library out of GitHub, which you can do with 

    git clone https://github.com/nye-duo/Duo-DSpace.git

Or you can download the binary version from:

[https://github.com/nye-duo/Duo-DSpace/blob/master/release/dspace-duo-2.0.1.tar.gz](https://github.com/nye-duo/Duo-DSpace/blob/master/release/dspace-duo-2.0.1tar.gz)

You can then carry on with your preferred installation process below


Fresh Installation
------------------

### Binary Installation (recommended)

**1/** Customise the addbinarymodule.sh and postinstall.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

in addbinarymodule.sh, look for the lines:

    DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
    MAVEN="mvn"

and in postinstall.sh, look for the lines:

    DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
    DSPACE="/srv/duo/dspace-duo-test"

and update these with the paths to your DSPACE_SRC, your DSPACE install directory, and your MAVEN executable with any additional command line arguments you want it to use.

**2/** Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.  You can find detailed documentation about the configuration options here: [https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md](https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md)


**3/** Run the addbinarymodule.sh script to prepare the dspace source to be built with the duo code incorporated.  This will install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build.

    bash addbinarymodule.sh

Note that you **must** use bash to execute this file, or it will cause an error.

Now go on to the **Common Installation Steps**


### Source Installation

Since the source installation requires Duo-DSpace to be compiled against a modified version of DSpace, it is necessary to install that modified DSpace into the local maven repository /before/ following the steps below.  This can be done with:

    mvn install -Dlicense.skip=true
    
in the root of the modified DSpace instance.

**1/** Customise the addmodule.sh and postinstall.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

in addmodule.sh, look for the lines:

    DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
    MAVEN="mvn"

and in postinstall.sh, look for the lines:

    DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
    DSPACE="/srv/duo/dspace-duo-test"

and update these with the paths to your DSPACE_SRC, your DSPACE install directory, and your MAVEN executable with any additional command line arguments you want it to use.

**2/** Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.  You can find detailed documentation about the configuration options here: [https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md](https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md)

**3/** Run the addmodule.sh script to prepare the dspace source to be built with the duo code.  This will compile and install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build

    bash addmodule.sh

Note that you **must** use bash to execute this file, or it will cause an error.

Now go on to the **Common Installation Steps**


### Common Installation Steps

**4/** Append/replace the values in dspace.cfg with the duo values (see config/dspace.cfg in the duo code for fields required)

You can start by just concatenating the two files

    cat [duo-source]/config/dspace.cfg >> [dspace-source]/dspace/config/dspace.cfg
    
Note, though, that some of the fields provided by the Duo config file are duplicates of ones which appear in the DSpace configuration, and these need to be used instead of the DSpace configuration value, or need to be merged with any of your existing local configuration as required.

The duplicated fields are:

    embargo.field.terms
    embargo.field.lift
    plugin.single.org.dspace.embargo.EmbargoLifter
    event.dispatcher.default.consumers
    plugin.named.org.dspace.content.crosswalk.IngestionCrosswalk

You can find detailed documentation about the configuration options here: [https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md](https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md)

**5/** Build and install DSpace as normal.  You may find the DSpace install documentation useful: [https://wiki.duraspace.org/display/DSDOC4x/Installing+DSpace](https://wiki.duraspace.org/display/DSDOC4x/Installing+DSpace)

At this stage, do not worry about starting tomcat, we will do that once the rest of the installation steps below have been carried out.  You should, though, be sure to point the tomcat webapps directory at the DSpace webapps directory.  For example:

This can be done with a symlink (if your tomcat will allow it):

    ln -s [dspace-live]/webapps [tomcat]/webapps

otherwise, you must copy the webapps directory into your tomcat working directory, as normal.

**6/** If you're installing for the first time on this DSpace instance, you should run your customised postinstall.sh script.  

    sh postinstall.sh

**DO NOT RUN THIS SCRIPT ON A DSPACE UPON WHICH IT HAS PREVIOUSLY BEEN RUN** - it makes changes to the database schema and loads metadata registries, and re-runs may damage your installation.

If you find yourself wanting to run parts of this script but not other parts (in particular, metadata registry loading), you can just run the relevant lines from the script.  It is very short and each section is labelled so it is clear what it does.

**7/** Start tomcat

    [tomcat]/bin/catalina.sh start

Once tomcat has started, you should be able to access your DSpace instance, at - for example: [http://localhost:8080/xmlui](http://localhost:8080/xmlui)

**8/** Set up the cron job for lifting embargoes, which will need to use the command:

	[dspace]/bin/dspace embargo-lifter

**9/** Set up the Cristin Workflow, as per the section further down this document

Update existing DSpace
----------------------

This approach should be used if you want to install the Duo Extensions onto an existing vanilla DSpace instance.

Start by following **Preparing the DSpace source** and then choose one of **Binary Installation** or **Source Installation** and finish with the **Custom Installation Steps**

### Preparing the DSpace source

**1/** Checkout the Duo version of DSpace listed in the **Dependencies** section above

**2/** Migrate all of your existing localisations for your DSpace installation into this newly checked out version of DSpace (this will include things such as your themes, custom/modified DSpace classes, and configuration)

**3/** The Duo Extensions will overwrite any existing messages files and config files for your DSpace, so you should be sure
to merge the provided messages and configs with your existing ones, and place them into the config and deploy directories
within the Duo Extension codebase before proceeding.

Configuration can all be found in the sub-directory:

    config

The Duo Extension messages file can be found in the sub-directory:

    deploy

During installation, the files in these two directories will overwrite any files in the DSpace source directories (with the exception of dspace.cfg, which will not be copied), so
you should merge your all your custom configuration into the files in the directories within this package.

**NOTE: the following may no longer be true:** (At the University of Oslo, this can be partially automated by customising and running the migratedspace.sh script provided)

You can find detailed documentation about the configuration options here: [https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md](https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md)

### Binary Installation (recommended)

**4/** Customise the addbinarymodule.sh and postupdate.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

in addbinarymodule.sh, look for the lines:

    DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
    MAVEN="mvn"

and in postupdate.sh, look for the lines:

    DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
    DSPACE="/srv/duo/dspace-duo-test"

and update these with the paths to your DSPACE_SRC, your DSPACE install directory, and your MAVEN executable with any additional command line arguments you want it to use.

**5/** Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.  You can find detailed documentation about the configuration options here: [https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md](https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md)


**6/** Run the addbinarymodule.sh script to prepare the dspace source to be built with the duo code incorporated.  This will install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build.

    bash addbinarymodule.sh

Note that you **must** use bash to execute this file, or it will cause an error.

Now go on to the **Common Installation Steps**


### Source Installation

Since the source installation requires Duo-DSpace to be compiled against a modified version of DSpace, it is necessary to install that modified DSpace into the local maven repository /before/ following the steps below.  This can be done with:

    mvn install -Dlicense.skip=true
    
in the root of the modified DSpace instance.

**4/** Customise the addmodule.sh and postupdate.sh scripts with the paths to your DSpace source, DSpace live and Maven installs as appropriate.

in addmodule.sh, look for the lines:

    DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
    MAVEN="mvn"

and in postupdate.sh, look for the lines:

    DSPACE_SRC="/home/richard/Code/External/Duo-Dev/DSpace-42-Scratch"
    DSPACE="/srv/duo/dspace-duo-test"

and update these with the paths to your DSPACE_SRC, your DSPACE install directory, and your MAVEN executable with any additional command line arguments you want it to use.

**5/** Go through the *.cfg files in the config and config/modules directories and update any values which are relevant to your installation environment.  You can find detailed documentation about the configuration options here: [https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md](https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md)

**6/** Run the addmodule.sh script to prepare the dspace source to be built with the duo code.  This will compile and install the duo code library into your local maven repository and prepare DSpace to incorporate it during the build

    bash addmodule.sh

Note that you **must** use bash to execute this file, or it will cause an error.

Now go on to the **Common Installation Steps**

### Common Installation Steps

**7/** Append/replace the values in dspace.cfg with the duo values (see config/dspace.cfg in the duo code for fields required)

You can start by just concatenating the two files

    cat [duo-source]/config/dspace.cfg >> [dspace-source]/dspace/config/dspace.cfg
    
Note, though, that some of the fields provided by the Duo config file are duplicates of ones which appear in the DSpace configuration, and these need to be used instead of the DSpace configuration value, or need to be merged with any of your existing local configuration as required.

The duplicated fields are:

    embargo.field.terms
    embargo.field.lift
    plugin.single.org.dspace.embargo.EmbargoLifter
    event.dispatcher.default.consumers
    plugin.named.org.dspace.content.crosswalk.IngestionCrosswalk

You can find detailed documentation about the configuration options here: [https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md](https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md)

**8/** Build and install DSpace as normal.  You may find the DSpace install documentation useful: [https://wiki.duraspace.org/display/DSPACE/Installation](https://wiki.duraspace.org/display/DSPACE/Installation)

**9/** If you're installing the extensions for the first time on this DSpace instance, you should run your customised postupdate.sh script.

    sh postupdate.sh

**DO NOT RUN THIS SCRIPT ON A DSPACE UPON WHICH IT HAS PREVIOUSLY BEEN RUN** - it makes changes to the database schema and loads metadata registries, and re-runs may damage your installation.  It also copies over the updated configurations to your live DSpace.

**NOTE**: this script enables the XML workflow, so if your DSpace already has that enabled, you should not run this script.  You should open up the file and comment out the line which enables the XML workflow before running it.

If you find yourself wanting to run parts of this script but not other parts (in particular, metadata registry loading), you can just run the relevant lines from the script.  It is very short and each section is labelled so it is clear what it does.

**10/** Updating DSpace does not update the database schema under normal circumstances, so we need to run the following command to load the schema changes used by the Duo version of DSpace (where [dspace-live] is the installed DSpace instance and [dspace-src] is the Duo DSpace source code):

    [dspace-live]/bin/dspace dsrun org.dspace.storage.rdbms.InitializeDatabase [dspace-src]/dspace/etc/postgres/database_schema_duo.sql

**11/** Restart tomcat

**12/** Set up the cron job for lifting embargoes, which will need to use the command:

	[dspace-live]/dspace embargo-lifter


Upgrading from 1.8.2 to 4.2
---------------------------

This section takes you through the process of upgrading an existing Duo 1.8.2 to Duo 4.2.  The upgrade is based heavily on the DSpace documentation for the upgrade path, which can be found here:

* [From 1.8.x to 3.x](https://wiki.duraspace.org/display/DSDOC4x/Upgrading+From+1.8.x+to+3.x)
* [From 3.x to 4.x](https://wiki.duraspace.org/display/DSDOC4x/Upgrading+From+3.x+to+4.x)

**1/** Back up the existing database, assetstore and configuration:

Database:

    pg_dump -U [database-user] -f [backup-file-location] [database-name]

Assetstore: Backup the directory ([dspace]/assetstore by default, and any other assetstores configured in the [dspace]/config/dspace.cfg "assetstore.dir" and "assetstore.dir.#" settings)

Configuration: Backup the entire directory content of [dspace]/config.

**2/** Ensure the **Dependencies** listed above are met (upgrades are required to Java and Maven, at least)

**3/** Build the modified Duo DSpace 4.2 with the Duo extensions installed: up to and including step **7** of the instructions above to **Update Existing DSpace**

**4/** Build DSpace as normal with:

    mvn clean package

**5/** Stop tomcat.

If you are running Tomcat 6, then this will be the last time we use it - when you start DSpace up again later you should be using Tomcat 7+.

**6/** Update DSpace.  The following commands will deploy the new code to the existing installation directory, backing up what was there previously.

    cd [dspace-source]/dspace/target/dspace-4.2-build.dir
    ant update

**7/** Update the database schema.

    psql -U [dspace-dbms-user] [database] -f [dspace-source]/dspace/etc/postgres/database_schema_18-3.sql
    psql -U [dspace-dbms-user] [database] -f [dspace-source]/dspace/etc/postgres/database_schema_3-4.sql

*Note: in some cases this seems to create the new database tables as a different user to the ones created by the original install.  If this causes problems, database tables can be re-set to the correct owner with:*

    ALTER TABLE [table] OWNER TO [correct owner]

**8/** Initialise the Lucene Index

    [dspace-live]/bin/dspace index-lucene-init

**9/** Deploy the webapps and start tocmat.  Be sure at this stage to move to Tomcat 7+.

    cp -R [dspace]/webapps/* [tomcat]/webapps/
    [tomcat]/bin/catalina.sh start

**10/** Optimise the solr index for this version of DSpace:

    wget http://localhost:8080/solr/oai/update?optimize=true
    wget http://localhost:8080/solr/search/update?optimize=true
    wget http://localhost:8080/solr/statistics/update?optimize=true

*Note: wget will create files on the local disk called update?optimize=true, update?optimize=true.1 and update?optimize=true.2 - these can safely be deleted*

**11/** Populate the Solr indices:

    [dspace-live]/bin/dspace index-discovery -f
    [dspace-live]/bin/dspace oai import



Setting up a Cristin Workflow
-----------------------------

Once you have set up a Collection for harvesting from Cristin, you need to enable the correct workflow for it.  To do this edit the file

	[dspace]/config/workflow.xml

And add a name-map reference in the heading section of the file, mapping your collection's handle to the "cristin" workflow, for example:

	<name-map collection="123456789/4404" workflow="cristin"/>

Then edit the file

    [dspace]/config/input-forms.xml

And add a name-map reference in the "form-map" section of the file, mapping your collection's handle to the "cristin" metadata form, for example:

    <name-map collection-handle="123456789/4404" form-name="cristin" />

For these changes to take effect, you will need to restart tomcat.

Running the De-Duplication Task
-------------------------------

Under specific circumstances more than one item with the same Cristin ID can be created in DSpace.  This happens when an item has been harvested from Cristin and subsequently archived, and then a new version of that item (whose files have changed) becomes available in Cristin.  When DSpace harvests the new version of the item, it will not update the existing archived version, but will instead create a new version in the archive which will have the same Cristin ID.

In order to check for duplicate Cristin IDs, we can use a command-line script which will output a human readable summary of any duplicate identifiers, for case-by-case analysis by the administrator.

    [dspace]/bin/dspace dsrun no.uio.duo.DeduplicateCristinIds

This will output something like:

    Cristin ID 12345 is shared by items: 123456789/23 (ID: 876), 123456789/98 (ID: 900)
    Cristin ID 56789 is shared by items: 123456789/120 (ID: 1145), 123456789/177 (ID: 1367)

and so on.

Running the URN Generator
-------------------------

In order to register the items in the repository with the National Library's URN service, and to add the bitstream urls to the item metadata in DSpace, you should regularly run the URNGenerator

To generate the URNs for all items that do not have one, and to add the bitstream URLs to those items, run the command with only the -e argument (specifying the username of the user the operation should run as - recommended to be an administrator):

    [dspace]/bin/dspace dsrun no.uio.duo.URNGenerator -e [username]

In order to force the regeneration of all bitstream URLs you can run the command with the -f argument.  This will still generate URNs for all items that do not already have one:

    [dspace]/bin/dspace dsrun no.uio.duo.URNGenerator -f -e [username]

In order to force the regeneration of all item URLs and to update the URN registry with those URLs where they have changed since last time, use the -a option:

    [dspace]/bin/dspace dsrun no.uio.duo.URNGenerator -a -e [username]

The URN generator can be run on the whole archive, or it can be run on a single item as identified by its handle:

    [dspace]/bin/dspace dsrun no.uio.duo.URNGenerator -h 12345678/100 -a -e [username]


Manual Installation on running DSpace (only if you know what you're doing)
--------------------------------------------------------------------------

1. Build the code

    mvn clean package

2. Obtain the dependencies

    mvn dependency:copy-dependencies

3. Deploy to installed DSpace: Copy the duo-2.0.1.jar and its dependencies to the DSpace swordv2 webapp's WEB-INF/lib directory, and the DSpace lib directory (ensuring not to overwrite any existing .jar files in the DSpace lib directory)

4. Deploy the config: copy all the config files provided (except dspace.cfg), including xsl crosswalks (you might want to update the configs with localised values).  You can find detailed documentation about the configuration options here: [https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md](https://github.com/nye-duo/Duo-DSpace/blob/master/config/README.md)

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

11. Sort out the configuration for the Cristin Workflow, as described above
