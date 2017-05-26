package no.uio.duo.policy;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.DCDate;
import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.PluginManager;
import org.dspace.embargo.EmbargoSetter;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PolicyPatternManager
{
    public void applyToExistingItem(Item item, Context context)
            throws SQLException, AuthorizeException, IOException
    {
        Date embargoDate = this.getEmbargoDate(item, context);

        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            ContextualBitstream cb = bsi.next();
            List<ResourcePolicy> readPolicies = this.getReadPolicies(context, cb.getBitstream());
            List<ResourcePolicy> removePolicies = this.filterUnwantedPolicies(readPolicies);

            if ("ORIGINAL".equals(cb.getBundle().getName()))
            {
                // if this is the original bundle, apply an intelligent approach to normalising the policies
                IntendedPolicy intendedPolicy = this.getIntendedPolicies(readPolicies, embargoDate);
                List<ResourcePolicy> alsoRemove = this.filterUnnecessaryPolicies(readPolicies, intendedPolicy);
                removePolicies.addAll(alsoRemove);

                this.removePolicies(removePolicies);
                if (!intendedPolicy.isSatisfied())
                {
                    ResourcePolicy policy = intendedPolicy.makePolicy(context, cb.getBitstream());
                    policy.update();
                }
            }
            else
            {
                // just remove all the policies, we don't want any policies on other bundle's bitstreams
                this.removePolicies(removePolicies);
                this.removePolicies(readPolicies);
            }
        }
    }

    public void applyToNewItem(Item item, Context context)
            throws SQLException, AuthorizeException, IOException
    {
        Date embargoDate = this.getEmbargoDate(item, context);

        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            ContextualBitstream cb = bsi.next();
            List<ResourcePolicy> readPolicies = this.getReadPolicies(context, cb.getBitstream());
            List<ResourcePolicy> removePolicies = this.filterUnwantedPolicies(readPolicies);

            if ("ORIGINAL".equals(cb.getBundle().getName()))
            {
                if (embargoDate == null)
                {
                    // if there is no embargo date in the metadata, apply an unbound Anon READ
                    IntendedPolicy intended = new IntendedPolicy(false);
                    ResourcePolicy policy = intended.makePolicy(context, cb.getBitstream());
                    policy.update();
                }
                else
                {
                    // if there is an embargo date in the metadata, apply an Anon READ active from that date
                    IntendedPolicy intended = new IntendedPolicy(embargoDate);
                    ResourcePolicy policy = intended.makePolicy(context, cb.getBitstream());
                    policy.update();
                }

                this.removePolicies(removePolicies);
            }
            else
            {
                // just remove all the policies, we don't want any policies on other bundle's bitstreams
                this.removePolicies(removePolicies);
                this.removePolicies(readPolicies);
            }
        }
    }

    private Date getEmbargoDate(Item item, Context context)
            throws SQLException, AuthorizeException, IOException
    {
        // first check that there is a date
        String liftDateField = ConfigurationManager.getProperty("embargo.field.lift");
        if (liftDateField == null)
        {
            return null;
        }

        // if there is no embargo value, the item isn't embargoed
        DCValue[] embargoes = item.getMetadata(liftDateField);
        if (embargoes.length == 0)
        {
            return null;
        }

        // then generate a java Date object
        // we can't use this, as it validates the date of the embargo, which we don't want
        // DCDate embargoDate = EmbargoManager.getEmbargoTermsAsDate(context, item);

        EmbargoSetter setter = (EmbargoSetter) PluginManager.getSinglePlugin(EmbargoSetter.class);
        DCDate embargoDate = setter.parseTerms(context, item, embargoes[0].value);
        return embargoDate.toDate();
    }

    private List<ResourcePolicy> getReadPolicies(Context context, DSpaceObject dso)
            throws SQLException
    {
        List<ResourcePolicy> read = new ArrayList<ResourcePolicy>();
        List<ResourcePolicy> all = AuthorizeManager.getPolicies(context, dso);
        for (ResourcePolicy rp : all)
        {
            if (rp.getAction() == Constants.READ)
            {
                read.add(rp);
            }
        }
        return read;
    }

    private List<ResourcePolicy> filterUnwantedPolicies(List<ResourcePolicy> existing)
    {
        Date now = new Date();
        List<ResourcePolicy> unwanted = new ArrayList<ResourcePolicy>();
        List<Integer> idxs = new ArrayList<Integer>();

        for (int i = 0; i < existing.size(); i++)
        {
            ResourcePolicy policy = existing.get(i);

            // if this is an Admin READ policy
            if (policy.getGroupID() == 1 && policy.getAction() == Constants.READ)
            {
                idxs.add(i);
                unwanted.add(policy);
                continue;
            }

            // if the policy end date has passed
            Date end = policy.getEndDate();
            if (end != null && end.before(now))
            {
                idxs.add(i);
                unwanted.add(policy);
            }
        }

        for (int i = idxs.size() - 1; i >= 0; i--)
        {
            int idx = idxs.get(i);
            existing.remove(idx);
        }

        return unwanted;
    }

    private IntendedPolicy getIntendedPolicies(List<ResourcePolicy> existing, Date embargo)
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
        if (embargo == null)
        {
            if (anonRead == null)
            {
                // no embargo date in metadata, and no anonymous read policy - permanent embargo
                intended = new IntendedPolicy(true);
            }
            else
            {
                // no embargo date in metadata, and an existing anonymous read policy - keep the existing policy
                intended = new IntendedPolicy(anonRead);
            }
        }
        else if (embargo.before(now))
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
                    // embargo is in the past, and an existing anon read policy starting in the future - keep the existing policy
                    intended = new IntendedPolicy(anonRead);
                }
            }
        }
        else if (embargo.after(now))
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
        else
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

    private List<ResourcePolicy> filterUnnecessaryPolicies(List<ResourcePolicy> existing, IntendedPolicy intended)
    {
        List<ResourcePolicy> remove = new ArrayList<ResourcePolicy>();
        List<Integer> idxs = new ArrayList<Integer>();

        for (int i = 0; i < existing.size(); i++)
        {
            ResourcePolicy policy = existing.get(i);
            if (intended.matches(policy))
            {
                intended.setSatisfied(true);
            }
            else
            {
                idxs.add(i);
                remove.add(policy);
            }
        }

        for (int i = idxs.size() - 1; i >= 0; i--)
        {
            int idx = idxs.get(i);
            existing.remove(idx);
        }

        return remove;
    }

    private void removePolicies(List<ResourcePolicy> policies)
            throws SQLException
    {
        for (ResourcePolicy policy : policies)
        {
            policy.delete();
        }
    }
}
