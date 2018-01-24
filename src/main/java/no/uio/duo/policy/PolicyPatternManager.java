package no.uio.duo.policy;

import no.uio.duo.BitstreamIterator;
import no.uio.duo.DuoException;
import no.uio.duo.MetadataManager;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.PluginManager;
import org.dspace.embargo.EmbargoSetter;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <p>This class is responsible for applying a consistent and coherent set of resource policies to items it is handed.</p>
 *
 * <p>It carries out the following activities:</p>
 *
 * <ul>
 *     <li>inspects an existing item and determines what resource policies it should have on bundles and bitstreams</li>
 *     <li>compares existing policies with the intended policies and adds/removes policies as necessary</li>
 * </ul>
 *
 * <p>It can apply these rules in two different ways: on existing items, and on new items</p>
 *
 * <p>For existing items, broadly the rules are:</p>
 *
 * <ul>
 *     <li>Embargo in metadata: No, Anon READ policy in place: No ; Apply permanent embargo</li>
 *     <li>Embargo in metadata: No, Anon READ policy in place: Yes ; Keep Anonymous read policy</li>
 *     <li>Embargo in metadata: Past, Anon READ policy in place: No ; Apply permanent embargo</li>
 *     <li>Embargo in metadata: Past, Anon READ policy in place: Yes ; Keep Anonymous read policy</li>
 *     <li>Embargo in metadata: Future, Anon READ policy in place: No ; Apply metadata embargo</li>
 *     <li>Embargo in metadata: Future, Anon READ policy in place: Yes ; Keep most restrictive policy</li>
 * </ul>
 *
 * <p>For new items, the rules are:</p>
 *
 * <ul>
 *     <li>If there is no embargo date in the metadata, apply an active Anonymous READ policy</li>
 *     <li>If there is an embargo date in the metadata, apply an Anonymous READ policy which begins on that date</li>
 * </ul>
 *
 * <p>For details about actual expected behaviour, see the "testmatrix.csv" in test/resources</p>
 *
 * <p>The above rules are applied only to the ORIGINAL bundle, all other bundles are set to Admin READ only (they
 * have all their resource policies removed, which is effectively the same).</p>
 *
 */
public class PolicyPatternManager
{
    /** log4j logger */
    private static Logger log = Logger.getLogger(PolicyPatternManager.class);

    /**
     * Inner class to allow us to keep track of the latest (in time) embargo date, as processing
     * of bitstreams progresses
     */
    class EmbargoDateTracker
    {
        public List<Date> startDates = new ArrayList<Date>();
        public boolean unbound = false;

        /**
         * Track the embargo date from the intended policy
         *
         * @param intendedPolicy
         */
        public void track(IntendedPolicy intendedPolicy)
        {
            // record the start dates we see, and whether there is no start date on any policy (i.e. is unbound)
            Date start = intendedPolicy.getStartDate();
            if (start == null)
            {
                this.unbound = true;
            }
            else
            {
                this.startDates.add(start);
            }
        }
    }

    /**
     * Method which will apply the policy rules to the provided item, using the specific implementation of the IntendedPolicyInterface provided
     *
     * @param item
     * @param context
     * @param intendedPolicyImpl
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    private void applyPolicyRules(Item item, Context context, IntendedPolicyInterface intendedPolicyImpl)
            throws SQLException, AuthorizeException, IOException
    {
        Date embargoDate = this.getEmbargoDate(item, context);
        EmbargoDateTracker tracker = new EmbargoDateTracker();

        // first, remove all the bundle policies, we don't need any of them
        this.removeAllBundlePolicies(context, item);

        int originalCounter = 0;
        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            ContextualBitstream cb = bsi.next();
            List<ResourcePolicy> readPolicies = this.getReadPolicies(context, cb.getBitstream());
            List<ResourcePolicy> removePolicies = this.filterUnwantedPolicies(readPolicies);

            if ("ORIGINAL".equals(cb.getBundle().getName()))
            {
                originalCounter++;

                // if this is the original bundle, apply an intelligent approach to normalising the policies
                // IntendedPolicy intendedPolicy = this.getIntendedPolicies(readPolicies, embargoDate);
                IntendedPolicy intendedPolicy = intendedPolicyImpl.getIntendedPolicies(readPolicies, embargoDate);
                List<ResourcePolicy> alsoRemove = this.filterUnnecessaryPolicies(readPolicies, intendedPolicy);
                removePolicies.addAll(alsoRemove);
                this.removePolicies(removePolicies);
                if (removePolicies.size() > 0)
                {
                    log.info("PolicyPatternManager removed " + removePolicies.size() + " unwanted policies from bitstream " + cb.getBitstream().getID());
                }

                if (!intendedPolicy.isSatisfied())
                {
                    ResourcePolicy policy = intendedPolicy.makePolicy(context, cb.getBitstream());
                    policy.update();
                    log.info("PolicyPatternManager applied new policy on bitstream " + cb.getBitstream().getID());
                }

                // track the policy dates for later use
                tracker.track(intendedPolicy);
            }
            else
            {
                // just remove all the policies, we don't want any policies on other bundles' bitstreams
                this.removePolicies(removePolicies);
                this.removePolicies(readPolicies);

                if (removePolicies.size() > 0)
                {
                    log.info("PolicyPatternManager removed " + removePolicies.size() + " unwanted policies from bitstream " + cb.getBitstream().getID());
                }
            }
        }

        if (originalCounter > 0) {
            this.normaliseEmbargoDate(tracker, embargoDate, item, context);
        } else {
            this.removeDudEmbargoDate(embargoDate, item, context);
        }

        item.update();
    }

    /**
     * Apply the policy pattern to an existing item
     *
     * @param item
     * @param context
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void applyToExistingItem(Item item, Context context)
            throws SQLException, AuthorizeException, IOException
    {
        if (log.isDebugEnabled())
        {
            log.debug("Applying PolicyPatternManager to existing item " + item.getID());
        }

        this.applyPolicyRules(item, context, new IntendedPolicyExisting());

        if (log.isDebugEnabled())
        {
            log.debug("Finished applying PolicyPatternManager to existing item " + item.getID());
        }
    }

    /**
     * Apply the policy pattern to a new item
     *
     * @param item
     * @param context
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void applyToNewItem(Item item, Context context)
            throws SQLException, AuthorizeException, IOException
    {
        if (log.isDebugEnabled())
        {
            log.debug("Applying PolicyPatternManager to new item " + item.getID());
        }

        this.applyPolicyRules(item, context, new IntendedPolicyNew());

        if (log.isDebugEnabled())
        {
            log.debug("Finished applying PolicyPatternManager to new item " + item.getID());
        }
    }

    /**
     * Normalise the embargo date in the metadata with the embargo dates applied to bitstreams
     *
     * @param tracker
     * @param embargoDate
     * @param item
     * @param context
     */
    private void normaliseEmbargoDate(EmbargoDateTracker tracker, Date embargoDate, Item item, Context context)
    {
        if (log.isDebugEnabled())
        {
            log.debug("PolicyPatternManager.normaliseEmbargoDate on item " + item.getID());
        }

        // now determine how to set the embargo date in the metadata
        Date latest = null;
        if (tracker.startDates.size() > 0)
        {
            if (log.isDebugEnabled())
            {
                log.debug("PolicyPatternManager.normaliseEmbargoDate: one or more start dates present in resource policies set for item " + item.getID());
            }

            // look for the latest start date for a policy
            for (Date sd : tracker.startDates)
            {
                if (latest == null || sd.after(latest))
                {
                    latest = sd;
                }
            }

            // if the latest start date is in the past, ignore it
            if (latest != null && latest.before(new Date()))
            {
                latest = null;
            }
        }

        // if there is no latest start date from the policies (or they are all in the past)
        // AND
        // there is not an unbound policy
        // THEN
        // just keep the existing embargo date
        if (latest == null && !tracker.unbound)
        {
            if (log.isDebugEnabled())
            {
                log.debug("PolicyPatternManager.normaliseEmbargoDate: no latest start date, and no unbound policy for item " + item.getID());
            }
            latest = embargoDate;
        }

        // if there is no latest date, then, just remove the embargo metadata
        if (embargoDate != null && latest == null)
        {
            if (log.isDebugEnabled())
            {
                log.debug("PolicyPatternManager.normaliseEmbargoDate: no latest embargo date from policy for item " + item.getID() + " - removing embargo date from metadata");
            }
            this.removeEmbargoDate(item, context);
            log.info("Removed embargo date from item metadata: " + item.getID());
        }
        // if the embargo date is not set, but there is a latest date, set the latest date
        else if (embargoDate == null && latest != null)
        {
            if (log.isDebugEnabled())
            {
                log.debug("PolicyPatternManager.normaliseEmbargoDate: no embargo date in item, but date from policy, for item " + item.getID() + " - adding embargo date in metadata");
            }
            this.setEmbargoDate(latest, item, context);
            log.info("Added embargo date to item metadata: " + item.getID());
        }
        // if the latest start date is after the current embargo metadata, move the metadata forward
        else if (latest != null && latest.after(embargoDate))
        {
            if (log.isDebugEnabled())
            {
                log.debug("PolicyPatternManager.normaliseEmbargoDate: policy embargo date later than metadata embargo date for item " + item.getID() + " - updating embargo date in metadata");
            }
            this.setEmbargoDate(latest, item, context);
            log.info("Updated embargo date in item metadata: " + item.getID());
        }

        // in all other cases, just leave the embargo metadata as-is

        if (log.isDebugEnabled())
        {
            log.debug("Finished PolicyPatternManager.normaliseEmbargoDate on item " + item.getID());
        }
    }

    /**
     * Remove the embargo date for the item if it is in the present or the past
     *
     * @param embargoDate
     * @param item
     * @param context
     */
    private void removeDudEmbargoDate(Date embargoDate, Item item, Context context)
    {
        if (embargoDate == null)
        {
            return;
        }
        Date now = new Date();
        if (embargoDate.equals(now) || embargoDate.before(now))
        {
            this.removeEmbargoDate(item, context);
        }
    }

    /**
     * Get the embargo date from the item metadata
     *
     * @param item
     * @param context
     * @return
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
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
        if (embargoDate == null)
        {
            // this can happen if the embargo date was an empty field, or malformed in the metadata
            return null;
        }
        return embargoDate.toDate();
    }

    /**
     * Set the embargo date in the item metadata
     *
     * @param newDate
     * @param item
     * @param context
     */
    private void setEmbargoDate(Date newDate, Item item, Context context)
    {
        String liftDateField = ConfigurationManager.getProperty("embargo.field.lift");
        if (liftDateField == null)
        {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String formatted = sdf.format(newDate);

        MetadataManager mm = new MetadataManager();
        DCValue dcv;
        try
        {
            dcv = mm.makeDCValue(liftDateField, formatted);
        }
        catch (DuoException e)
        {
            log.warn("Embargo date could not be set, as embargo.field.lift could not be interpreted as metadata field");
            return;
        }

        DCValue[] originals = item.getMetadata(liftDateField);
        item.clearMetadata(dcv.schema, dcv.element, dcv.qualifier, Item.ANY);
        item.addMetadata(dcv.schema, dcv.element, dcv.qualifier, null, dcv.value);

        String original = "[no date]";
        if (originals.length > 0)
        {
            original = originals[0].value;
        }

        SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String prefix = "[" + stamp.format(new Date()) + "] ";

        String provenance = prefix + "Policy pattern application modified embargo date metadata: from '" + original + "' to '" + dcv.value + "'";
        item.addMetadata("dc", "description", "provenance", null, provenance);
        log.info("Item " + item.getID() + " " + provenance);
    }

    /**
     * Remove the embargo date from the metadata
     *
     * @param item
     * @param context
     */
    private void removeEmbargoDate(Item item, Context context)
    {
        String liftDateField = ConfigurationManager.getProperty("embargo.field.lift");
        if (liftDateField == null)
        {
            return;
        }

        String termsField = ConfigurationManager.getProperty("embargo.field.terms");

        MetadataManager mm = new MetadataManager();
        DCValue dcv;
        DCValue terms = null;
        try
        {
            dcv = mm.makeDCValue(liftDateField, "");
            if (termsField != null)
            {
                terms = mm.makeDCValue(termsField, "");
            }
        }
        catch (DuoException e)
        {
            log.warn("Embargo date could not be removed, as embargo.field.lift could not be interpreted as metadata field");
            return;
        }


        DCValue[] originals = item.getMetadata(liftDateField);
        item.clearMetadata(dcv.schema, dcv.element, dcv.qualifier, Item.ANY);
        if (terms != null)
        {
            item.clearMetadata(terms.schema, terms.element, terms.qualifier, Item.ANY);
        }

        String original = "[no date]";
        if (originals.length > 0)
        {
            original = originals[0].value;
        }

        SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String prefix = "[" + stamp.format(new Date()) + "] ";

        String provenance = prefix + "Policy pattern application removed embargo date, was: '" + original + "'";
        item.addMetadata("dc", "description", "provenance", null, provenance);
        log.info("Item " + item.getID() + " " + provenance);
    }

    /**
     * Get a list of all the READ policies on an item
     *
     * @param context
     * @param dso
     * @return
     * @throws SQLException
     */
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

    /**
     * Go through the list of policies and return a list of those that can be immediately thrown away
     *
     * @param existing
     * @return
     */
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

    /**
     * Filter out policies from the list of policies which are no longer necessary, given the IntededPolicy
     *
     * @param existing
     * @param intended
     * @return
     */
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

    /**
     * Remove all resource policies from a bundle.
     *
     * Bundle policies do not appear to make any difference to access of items or bitstreams in DSpace, so they
     * are redundant for our purposes
     *
     * @param context
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     */
    private void removeAllBundlePolicies(Context context, Item item)
            throws SQLException, AuthorizeException
    {
        Bundle[] bundles = item.getBundles();
        for (Bundle bundle : bundles)
        {
            List<ResourcePolicy> all = AuthorizeManager.getPolicies(context, bundle);
            this.removePolicies(all);
            bundle.update();
        }
    }

    /**
     * Remove the listed policies
     *
     * @param policies
     * @throws SQLException
     */
    private void removePolicies(List<ResourcePolicy> policies)
            throws SQLException
    {
        for (ResourcePolicy policy : policies)
        {
            policy.delete();
        }
    }
}
