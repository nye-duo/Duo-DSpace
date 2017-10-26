# Upgrade from Duo 3.0.0 to Duo 3.1.0

This file contains notes about how to upgrade from duo 3.0.0 to duo 3.1.0.

## Changes

The following changes have been made to the software in 3.1.0

* A metadata cleanup script has been added, to clear HTML tags from the item metadata


## How to update

You will need to install the new code, and update the configuration in the usual way, as per INSTALL.md.


## Other notes and actions on the update

### Run the Metadata Cleanup

After the code has been deployed, the following script should be run:

    [dspace]/bin/dspace dsrun no.uio.duo.cleanup.MetadataCleanup -e [admin email]
    
This will clean all HTML from the item metadata.  See CLEANUP.md and TEST.md for more information and options for 
this script.

