package no.uio.duo.policy;

import org.dspace.authorize.ResourcePolicy;

import java.util.Date;
import java.util.List;

/**
 * Interface to be implemented by any class which will make decisions about the intended policy
 * on a bitstream under specific circumstances.
 */
public interface IntendedPolicyInterface
{
    /**
     * Compute the IntendedPolicy for a bitstream given the list of existing policies and the
     * embargo date from the metadata
     *
     * @param existing
     * @param embargo
     * @return
     */
    IntendedPolicy getIntendedPolicies(List<ResourcePolicy> existing, Date embargo);
}
