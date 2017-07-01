# Testing

## Policy Pattern Manager

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


To test the policy pattern manager you can run a live functional test on a running DSpace with the following command:

    [dspace]/bin/dspace dsrun no.uio.duo.policy.LivePolicyTest -e [eperson email] -b [path to bitstream] -u [dspace base url]
    
**DO NOT UNDER ANY CIRCUMSTANCES RUN THIS ON A PRODUCTION SYSTEM** - it makes changes to the community and collection structure, and adds/removes items from the system.
    
For example in [dspace]/bin:

    ./dspace dsrun no.uio.duo.policy.LivePolicyTest -e richard@cottagelabs.com -b /home/richard/Code/External/Duo-DSpace/TEST.md -u http://localhost:8080/xmlui
    
