# Testing

This document describes some manual and automated tests that you can run against DSpace to determine appropriate
behaviour of the Duo extensions.

## Policy Pattern Manager

### IMPORTANT: Before testing

To test the policy pattern manager, we need to create a number of test items in the DSpace instance.  To do this
in such a way as the tests can then successfully run, we need to disable a number of components of DSpace which
act when an item is added to the repository: the embargo setter and the Duo install-consumer.

To do this, in dspace.cfg:

* specify a duo.embargo.communities value which contains a non-existent community
* Comment out the DuoInstallConsumer configuration:

Replace: 

    event.dispatcher.default.consumers = versioning, discovery, eperson, harvester, duo
    event.consumer.duo.class = no.uio.duo.DuoInstallConsumer
    event.consumer.duo.filters = Item+Install

with

    event.dispatcher.default.consumers = versioning, discovery, eperson, harvester 
    #, duo
    # event.consumer.duo.class = no.uio.duo.DuoInstallConsumer
    # event.consumer.duo.filters = Item+Install

Be sure to restart DSpace after making these changes, and don't forget to put them back after you have finished running
the tests.

### Running the tests

To test the policy pattern manager you can run a live functional test on a running DSpace with the following command:

    [dspace]/bin/dspace dsrun no.uio.duo.policy.LivePolicyTest -e [eperson email] -b [path to bitstream] -u [dspace base url] -m [test matrix file] -o [output report path]
    
**DO NOT UNDER ANY CIRCUMSTANCES RUN THIS ON A PRODUCTION SYSTEM** - it makes changes to the community and collection 
structure, and adds/removes items from the system.
    
For example in [dspace]/bin:

    ./dspace dsrun no.uio.duo.policy.LivePolicyTest -e richard@cottagelabs.com -b /home/richard/Code/External/Duo-DSpace/TEST.md -u http://localhost:8080/xmlui -m /home/richard/Code/External/Duo-DSpace/src/test/resources/testmatrix.csv -o /home/richard/Code/External/Duo-DSpace/src/test/resources/check.csv

This will execute the tests as defined in src/test/resources/testmatrix.csv

The output of this process will be a csv file which you can open in Excel, which will give you the test number (from testmatrix.csv) and
a before/after URL which will take you to items which can be compared to show you what the item was like before the policy
system was applied, and then again after.  This can be used for manually checking the results of the process.  Note that the
policy test system does also check the results automatically.

## Migration

In order to test for the migration, we just need to run the migrate script knowing that there are items which need
to be migrated.  The following script will create a single item in the DSpace system which will need to be migrated.

Run:

    [dspace]/bin/dspace dsrun no.uio.duo.migrate201to30.LiveMigrateTest -e [admin username] -b [path to test bitstream]

This will create a new community and collection and deposit an item.  The item ID will be output to the screen, so you
can look it up via the DSpace interface.

You should then run the main migrate script, which can be done with:

    [dspace]/bin/dspace dsrun no.uio.duo.migrate201to30.PolicyMigration -e [admin account email]
    
Once this has been done, check the item created in the first step to ensure it has been restructured appropriately.