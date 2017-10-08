package no.uio.duo.policy;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.core.Constants;

import java.util.Date;
import java.util.List;

/**
 * Implementation of the IntededPolicyInterface which can determine the correct policy to be present on a bitstream
 * for an item that already exists in the repository.
 */
public class IntendedPolicyExisting implements IntendedPolicyInterface
{
    /**
     * Compute the IntendedPolicy for a bitstream given the list of existing policies and the
     * embargo date from the metadata
     *
     * @param existing
     * @param embargo
     * @return
     */
    @Override
    public IntendedPolicy getIntendedPolicies(List<ResourcePolicy> existing, Date embargo)
    {
        ResourcePolicy anonRead = null;
        for (ResourcePolicy policy : existing)
        {
            if (policy.getGroupID() == 0 && policy.getAction() == Constants.READ)
            {
                anonRead = policy;
                break;
            }
        }

        IntendedPolicy intended = null;

        Date now = new Date();
        if (embargo == null)    // no embargo
        {
            if (anonRead == null)
            {
                // no embargo date in metadata, and no anonymous read policy - permanent embargo
                intended = new IntendedPolicy(true);
            }
            else
            {
                Date start = anonRead.getStartDate();
                if (start == null)
                {
                    // no embargo date in metadata, and an existing unbound anonymous read policy - keep the existing policy
                    intended = new IntendedPolicy(anonRead);
                }
                else if (start.before(now))
                {
                    // no embargo date, and existing anonymous read in the past - new unbound anon read policy
                    intended = new IntendedPolicy(false);
                }
                else if (start.equals(now))
                {
                    // no embargo date, and existing anonymous read for today - new unbound anon read policy
                    intended = new IntendedPolicy(false);
                }
                else if (start.after(now))
                {
                    // no embargo date in metadata, and an existing future anonymous read policy - keep the existing policy
                    intended = new IntendedPolicy(anonRead);
                }
            }
        }
        else if (embargo.before(now))   // embargo in the past
        {
            if (anonRead == null)
            {
                // embargo is in the past, and no anonymous read policy - permanent embargo
                intended = new IntendedPolicy(true);
            }
            else
            {
                Date start = anonRead.getStartDate();
                if (start == null)
                {
                    // embargo is in the past, and an existing unbound anon read policy - keep the existing policy
                    intended = new IntendedPolicy(anonRead);
                }
                else if (start.before(now))
                {
                    // embargo is in the past, and an existing anon read policy starting in the past - a fresh unbound anon read policy
                    intended = new IntendedPolicy(false);
                }
                else
                {
                    // embargo is in the past, and an existing anon read policy starting today or in the future - keep the existing policy
                    intended = new IntendedPolicy(anonRead);
                }
            }
        }
        else if (embargo.after(now))    // embargo in the future
        {
            if (anonRead == null)
            {
                // embargo is in the future, and no anonymous read policy - embargo until the date in the metadata
                intended = new IntendedPolicy(embargo);
            }
            else
            {
                Date start = anonRead.getStartDate();
                if (start == null)
                {
                    // embargo is in the future, and an existing (active) anon read policy - embargo until the date in the metadata
                    intended = new IntendedPolicy(embargo);
                }
                else if (embargo.after(start))
                {
                    // embargo is in the future, and an existing anon read policy starting before the embargo ends - embargo until the date in the metadata
                    intended = new IntendedPolicy(embargo);
                }
                else
                {
                    // embargo is in the future, and an existing anon read policy starting after the embargo ends - keep the more restrictive anon read policy
                    intended = new IntendedPolicy(anonRead);
                }
            }
        }
        else                // embargo is today
        {
            if (anonRead == null)
            {
                // embargo is today, and no anonymous read policy - set up an embargo for today
                intended = new IntendedPolicy(embargo);
            }
            else
            {
                // embargo is today, and an anonymous read policy - keep the existing policy
                intended = new IntendedPolicy(anonRead);
            }
        }

        return intended;
    }
}
