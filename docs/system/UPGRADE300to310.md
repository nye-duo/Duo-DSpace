# Upgrade from Duo 3.0.0 to Duo 3.1.0

This file contains notes about how to upgrade from duo 3.0.0 to duo 3.1.0.

## Changes

The following changes have been made to the software in 3.1.0

* A metadata cleanup script has been added, to clear HTML tags from the item metadata
* The restrictions applied to items coming from StudentWeb have been extended
* A notification email is now sent when a failed or restricted item is archived from StudentWeb
* The Install Consumer has been generalised as an event consumer, and now also applies policies when the item is Reinstated from Withdrawn status.
* A new curation task has been added which can add required state metadata for operation with 3.1.0 event handlers

In addition, this version contains a disabled feature to allow policies to be applied when the metadata is edited.  This feature requires
minor code changes to enable.


## How to update

### Code and Configuration

You will need to install the new code, and update the configuration in the usual way, as per INSTALL.md.

Note that there are several new new email templates, and changes to configuration files, so ensure that you fully synchronise the new configuraiton with your existing
configuration.

In particular, the following configuration files have changed:

* dspace.cfg


## Actions after the upgrade

### Run the Metadata Cleanup

After the code has been deployed, the following script should be run:

    [dspace]/bin/dspace dsrun no.uio.duo.cleanup.MetadataCleanup -e [admin email]
    
This will clean all HTML from the item metadata.  See CLEANUP.md and TEST.md for more information and options for 
this script.



## If Enabling Modify_Metadata

If, at a later date, it is decided to enable support for metadata modify, the following information is relevant:

### Required Metadata

**IMPORTANT: this step does NOT need to be done unless enabling Modify_Metadata event handling**

In order to support the new metadata fields required, there is a new schema document: config/registries/duo-metadata.xml

This schema will be imported automatically when you run either postupdate.sh or postinstall.sh

To run this yourself, you can use:

    [dspace]/bin/dspace dsrun org.dspace.administer.MetadataImporter -f config/registries/duo-metadata.xml


### Update the item metadata to work with the new event system

**IMPORTANT: this step does NOT need to be done unless enabling the Modify_Metadata event handling**

So that all the items have the appropriate state information associated with them, you need to run a curation task to update
the item metadata.  This is to allow the event handlers that were added in this version to correctly identify item states
and take appropriate actions with regard to setting policies.  **If this is not done, items may behave unexpectedly under changes
to metadata, with regard to how their resource policies/security is set**.

This migration is provided as a curation task, so can be executed as follows:

* Log in to Duo as an administrator
* Go to the "Curation Tasks" admin page
* Enter [handle prefix]/0, to tell the task to run on all of Duo
* Select "Set Duo Event State" from the list of curation tasks
* Click "Perform"

To be sure that this task has worked correctly on a given item, go to the item metadata page as an administrator.  You should see
the field "duo.state" set on the item, with information about the item's state.
