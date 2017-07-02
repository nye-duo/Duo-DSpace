package no.uio.duo.policy;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;

import java.sql.SQLException;
import java.util.Date;

/**
 * Class to represet the Anonymous READ policy that a bitstream is intended to have.  This covers a multitude of possible options:
 *
 * <ul>
 *     <li>A policy with a permanent embargo</li>
 *     <li>An unbound policy</li>
 *     <li>A policy with a specified embargo date</li>
 *     <li>A policy which already exists</li>
 * </ul>
 *
 * Construct this object around the parameters required for the bitstream.
 */
public class IntendedPolicy
{
    private Date embargo = null;
    private ResourcePolicy existing = null;
    private boolean satisfied = false;
    private boolean permanent = false;

    /**
     * Create the default policy, which is an unbound Anonymous READ
     */
    public IntendedPolicy() {}

    /**
     * Create a policy which is either permanently embargoed (pass true) or unbound (pass false)
     *
     * @param permanent
     */
    public IntendedPolicy(boolean permanent)
    {
        this.permanent = permanent;
    }

    /**
     * Create a policy which embargoes the item until the given date
     *
     * @param embargo
     */
    public IntendedPolicy(Date embargo)
    {
        this.embargo = embargo;
    }

    /**
     * Keep the existing policy
     *
     * @param existing
     */
    public IntendedPolicy(ResourcePolicy existing)
    {
        this.existing = existing;
    }

    /**
     * Determine whether this IntentedPolicy would produce a policy which matches (is equivalent to) the one passed in
     *
     * @param policy
     * @return
     */
    public boolean matches(ResourcePolicy policy)
    {
        if (this.existing != null)
        {
            if (policy.getID() == this.existing.getID())
            {
                return true;
            }

            Date psd = policy.getStartDate();
            Date esd = this.existing.getStartDate();
            boolean sd = (psd == null && esd == null) || (psd != null && psd.equals(esd));

            Date ped = policy.getEndDate();
            Date eed = this.existing.getEndDate();
            boolean ed = (ped == null && eed == null) || (ped != null && ped.equals(eed));

            boolean act = policy.getAction() == this.existing.getAction();
            boolean gid = policy.getGroupID() == this.existing.getGroupID();
            boolean epid = policy.getEPersonID() == this.existing.getEPersonID();
            boolean rid = policy.getResourceID() == this.existing.getEPersonID();
            boolean rt = policy.getResourceType() == this.existing.getResourceType();

            return sd && act && ed && gid && epid && rid && rt;
        }
        else if (this.embargo != null)
        {
            boolean ed = this.embargo.equals(policy.getStartDate());
            boolean act = policy.getAction() == Constants.READ;
            boolean gid = policy.getGroupID() == 0;

            return ed && act && gid;
        }
        else if (!this.permanent)
        {
            boolean ed = policy.getStartDate() == null;
            boolean act = policy.getAction() == Constants.READ;
            boolean gid = policy.getGroupID() == 0;

            return ed && act && gid;
        }
        else
        {
            // permanent embargo - no policy is a permanent embargo, so always return false
            return false;
        }
    }

    /**
     * Record that this policy is already satisfied for the intended bitstream
     *
     * @param satisfied
     */
    public void setSatisfied(boolean satisfied)
    {
        this.satisfied = satisfied;
    }

    /**
     * Is this policy satisfied for the intended bitstream?
     *
     * @return
     */
    public boolean isSatisfied()
    {
        return this.satisfied;
    }

    /**
     * Create the actual ResourcePolicy for the Bitstream
     *
     * This does not save the ResourcePolicy, you must call .update() yourself if you wish to keep it
     *
     * @param context
     * @param bitstream
     * @return
     * @throws SQLException
     * @throws AuthorizeException
     */
    public ResourcePolicy makePolicy(Context context, Bitstream bitstream)
            throws SQLException, AuthorizeException
    {
        // if there's an existing policy to keep, just return that
        if (this.existing != null)
        {
            return this.existing;
        }

        // otherwise create the base policy
        ResourcePolicy rp = ResourcePolicy.create(context);
        rp.setAction(Constants.READ);
        rp.setGroup(Group.find(context, 0));
        rp.setResource(bitstream);
        rp.setResourceType(Constants.BITSTREAM);

        // now set the start date
        if (this.embargo != null)
        {
            // if an embargo period is specified, set the start date
            rp.setStartDate(this.embargo);
        }
        else if (this.permanent)
        {
            // if the record is flagged as permanent embargo, set the future start date
            rp.setStartDate(this.getPermanentEmbargoDate());
        }
        // otherwise there is no need to set a start date

        return rp;
    }

    /**
     * Get the effective start date/embargo date for the policy
     *
     * @return
     */
    public Date getStartDate()
    {
        if (this.embargo != null)
        {
            return this.embargo;
        }
        else if (this.permanent)
        {
            return this.getPermanentEmbargoDate();
        }
        else if (this.existing != null)
        {
            return this.existing.getStartDate();
        }
        return null;
    }

    /**
     * Generate a date in the far future which is the effective permanent embargo date
     *
     * @return
     */
    private Date getPermanentEmbargoDate()
    {
        // set the date far in the future.  The year 2970 or thereabouts.
        // I know this is a magic number, so if this library breaks in 953 years time
        // feel free to have a go at me.
        long millis = 31536000000000L;
        return new Date(millis);
    }
}
