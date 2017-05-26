# Testing

## Policy Pattern Manager

To test the policy pattern manager you can run a live functional test on a running DSpace with the following command:

    [dspace]/bin/dspace dsrun no.uio.duo.policy.LivePolicyTest -e [eperson email]
    
**DO NOT UNDER ANY CIRCUMSTANCES RUN THIS ON A PRODUCTION SYSTEM** - it makes changes to the community and collection structure, and adds/removes items from the system.
    