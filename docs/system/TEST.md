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

    ./dspace dsrun no.uio.duo.policy.LivePolicyTest -e richard@cottagelabs.com -b /home/richard/Code/External/Duo-DSpace/docs/system/TEST.md -u http://localhost:8080/xmlui -m /home/richard/Code/External/Duo-DSpace/src/test/resources/testmatrix.csv -o /home/richard/Code/External/Duo-DSpace/src/test/resources/check.csv

This will execute the tests as defined in src/test/resources/testmatrix.csv

The output of this process will be a csv file which you can open in Excel, which will give you the test number (from testmatrix.csv) and
a before/after URL which will take you to items which can be compared to show you what the item was like before the policy
system was applied, and then again after.  This can be used for manually checking the results of the process.  Note that the
policy test system does also check the results automatically.

## Migration from 2.0.1 to 3.1.0

In order to test for the migration, we just need to run the migrate script knowing that there are items which need
to be migrated.  The following script will create a single item in the DSpace system which will need to be migrated.

Run:

    [dspace]/bin/dspace dsrun no.uio.duo.migrate201to30.LiveMigrateTest -e [admin username] -b [path to test bitstream]

This will create a new community and collection and deposit an item.  The item ID will be output to the screen, so you
can look it up via the DSpace interface.

You should then run the main migrate script, which can be done with:

    [dspace]/bin/dspace dsrun no.uio.duo.migrate201to30.PolicyMigration -e [admin account email]
    
Once this has been done, check the item created in the first step to ensure it has been restructured appropriately.

You can run just the single item created in the first step by specifying either the item id or the handle, e.g.

    [dspace]/bin/dspace dsrun no.uio.duo.migrate201to30.PolicyMigration -e [admin account email] -i 123
    
    [dspace]/bin/dspace dsrun no.uio.duo.migrate201to30.PolicyMigration -e [admin account email] -h 123456789/111
    
    
## HTML Cleanup

In order to test for HTML cleanup, we need to load an item with HTML in the metadata into the system.  The following
script will create a community, collection and a single item which has HTML for the metadata cleanup script to clean
for you:

    [dspace]/bin/dspace dsrun no.uio.duo.cleanup.LiveMetadataCleanupTest -e [admin account email]
    
The item ID and handle will output to the screen, so you can look it up via the DSpace interface.

You should then run the MetadataCleanup script, which can be done with:

    [dspace]/bin/dspace dsrun no.uio.duo.cleanup.MetadataCleanup -e [admin email] -i [item id]
    
Once this has been done, check the item created in the first step to ensure that the HTML has been cleaned correctly.


## Event Consumer

In order to ensure that the DuoEventConsumer is behaving correctly with regard to applying the appropriate policies
when an item is installed into the archive, you can use the following Live test.

### IMPORTANT: Before testing

To test the event consumer, we need to ensure that the consumer will run when an item is installed/modified in DSpace.

To do this, in dspace.cfg:

* Comment out duo.embargo.communities - this means it will run on all communities
* Ensure the DuoEventConsumer configuration is set

The configuration should look like this:

    event.dispatcher.default.consumers = versioning, discovery, eperson, harvester, duo
    event.consumer.duo.class = no.uio.duo.DuoEventConsumer
    event.consumer.duo.filters = Item+Install|Modify_Metadata

Be sure to restart DSpace after making these changes, and don't forget to put them back after you have finished running
the tests.

### Testing Standard Policy Patterns

To test that policy patterns are appropriately applied by the PolicyPatternManager during a normal install, you can run
the above LivePolicyTest with a special mode.

The above LivePolicyTest is designed to test the PolicyPatternManager itself, and not whether it is applied correctly
during an install of a new item.  The test below ensures that newly submitted items passing through the DuoInstallConsumer
have the PolicyPatternManager applied correctly.

    [dspace]/bin/dspace dsrun no.uio.duo.policy.LivePolicyTest -e [eperson email] -b [path to bitstream] -u [dspace base url] -m [test matrix file] -o [output report path] -w

Note the addition of the -w option - this causes the test to leave the reference item in the user workspace, so you can
compare the before and after submission items.  Administrator URLs for the item in the user workspace are output in
the final report from the test.

Additionally, the test matrix file is different to the one used before.  Instead we are only testing items which are "new",
and we give them the type "other" instead, to distinguish them from the previous test.  The test resource "makematrix.csv"
provides the appropriate test parameters.
    
**DO NOT UNDER ANY CIRCUMSTANCES RUN THIS ON A PRODUCTION SYSTEM** - it makes changes to the community and collection 
structure, and adds/removes items from the system.
    
For example in [dspace]/bin:

    ./dspace dsrun no.uio.duo.policy.LivePolicyTest -e richard@cottagelabs.com -b /home/richard/Code/External/Duo-DSpace/docs/system/TEST.md -u http://localhost:8080/xmlui -m /home/richard/Code/External/Duo-DSpace/src/test/resources/makematrix.csv -o /home/richard/Code/External/Duo-DSpace/src/test/resources/check.csv -w


### Testing FS Policies

To test the install consumer you can run a live functional test on a running DSpace with the following command:

    [dspace]/bin/dspace dsrun no.uio.duo.livetest.LiveInstallTest -e [eperson email] -b [path to bitstream] -u [dspace base url] -m [test matrix file] -o [output report path]
    
**DO NOT UNDER ANY CIRCUMSTANCES RUN THIS ON A PRODUCTION SYSTEM** - it makes changes to the community and collection 
structure, and adds/removes items from the system.
    
For example in [dspace]/bin:

    ./dspace dsrun no.uio.duo.livetest.LiveInstallTest -e richard@cottagelabs.com -b /home/richard/Code/External/Duo-DSpace/docs/system/TEST.md -u http://localhost:8080/xmlui -m /home/richard/Code/External/Duo-DSpace/src/test/resources/install_testmatrix.csv -o /home/richard/Code/External/Duo-DSpace/src/test/resources/check.csv

This will execute the tests as defined in src/test/resources/install_testmatrix.csv

The output of this process will be a csv file which you can open in Excel, which will give you the test number (from install_testmatrix.csv) and
a before/after URL which will take you to items which can be compared to show you what the item was like before the install to the repo
was applied, and then again after.  This can be used for manually checking the results of the process.  Note that the
install test system does also check the results automatically.