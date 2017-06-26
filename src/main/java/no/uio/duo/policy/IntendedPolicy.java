package no.uio.duo.policy;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;

import java.sql.SQLException;
import java.util.Date;

public class IntendedPolicy
{
    private Date embargo = null;
    private ResourcePolicy existing = null;
    private boolean satisfied = false;
    private boolean permanent = false;

    public IntendedPolicy() {}

    public IntendedPolicy(boolean permanent)
    {
        this.permanent = permanent;
    }

    public IntendedPolicy(Date embargo)
    {
        this.embargo = embargo;
    }

    public IntendedPolicy(ResourcePolicy existing)
    {
        this.existing = existing;
    }

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

    public void setSatisfied(boolean satisfied)
    {
        this.satisfied = satisfied;
    }

    public boolean isSatisfied()
    {
        return this.satisfied;
    }

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

    private Date getPermanentEmbargoDate()
    {
        // set the date far in the future.  The year 2970 or thereabouts.
        // I know this is a magic number, so if this library breaks in 953 years time
        // feel free to have a go at me.
        long millis = 31536000000000L;
        return new Date(millis);
    }
}
