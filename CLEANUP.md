# DUO Cleanup Script

## Initial Cleanup

To run the initial cleanup script, execute the following 2 commands:

    [dspace]/bin/dspace dsrun no.uio.duo.NumberCleanup -d
    [dspace]/bin/dspace cleanup

## Ongoing Monitoring

To check files (without taking action), execute:

    [dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -d

For additional checking logs to be output, use:

    dspace]/bin/dspace dsrun no.uio.duo.NumberChecker -d -v