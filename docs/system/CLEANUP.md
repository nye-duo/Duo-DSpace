# DUO Cleanup Script

## Initial Cleanup

To run the initial cleanup script, execute the following 2 commands:

    [dspace]/bin/dspace dsrun no.uio.duo.NumberCleanup -d
    [dspace]/bin/dspace cleanup

If you want to run the script on an individual item, collection or community, you can use it as follows:

    [dspace]/bin/dspace dsrun no.uio.duo.NumberCleanup -s item -h 123456789/12

    [dspace]/bin/dspace dsrun no.uio.duo.NumberCleanup -s collection -h 123456789/2

    [dspace]/bin/dspace dsrun no.uio.duo.NumberCleanup -s community -h 123456789/1

Where -s is the scope (item, collection or community) and -h is the handle in DSpace.

If you want to run this on a workspace or a workflow item, you can do so using its workspace id (-w) or workflow id (-f)

    [dspace]/bin/dspace dsrun no.uio.duo.NumberCleanup -s item -w 23

    [dspace]/bin/dspace dsrun no.uio.duo.NumberCleanup -s item -f 55

Remember to run

    [dspace]/bin/dspace cleanup

after any of these commands

## Ongoing Monitoring

To check files (without taking action), execute:

    [dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -d

For additional checking logs to be output, use:

    dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -d -v

If you want to run the script on an individual item, collection or community, you can use it as follows:

    [dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -s item -h 123456789/12

    [dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -s collection -h 123456789/2

    [dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -s community -h 123456789/1

Where -s is the scope (item, collection or community) and -h is the handle in DSpace.

If you want to run this on a workspace or a workflow item, you can do so using its workspace id (-w) or workflow id (-f)

    [dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -s item -w 23

    [dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -s item -f 55