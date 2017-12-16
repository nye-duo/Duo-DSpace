package no.uio.duo;


import no.uio.duo.policy.ContextualBitstream;
import no.uio.duo.policy.PolicyApplicationFilter;
import no.uio.duo.policy.PolicyPatternManager;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.*;
import org.dspace.embargo.EmbargoSetter;
import org.dspace.workflow.WorkflowItem;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

/**
 * Class to apply restriction rules to items coming from StudentWeb
 *
 */
public class FSRestrictionManager
{
    /** log4j logger */
    private static Logger log = Logger.getLogger(FSRestrictionManager.class);

    /**
     * Should this class process the given item.
     *
     * This happens if the item in question has the grade field from FS.
     *
     * @param item
     * @return
     */
    public static boolean consumes(Item item)
    {
        String gradeField = ConfigurationManager.getProperty("studentweb", "grade.field");
        DCValue[] dcvs = item.getMetadata(gradeField);
        return dcvs.length > 0;
    }

    /**
     * Method to run when an item is installed in the repository
     *
     * @param context
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void onInstall(Context context, Item item)
            throws SQLException, AuthorizeException, IOException, DuoException
    {
        DuoState stateObj = new DuoState(item);
        if (stateObj.isInstalled())
        {
            log.info("Item " + item.getID() + " is already installed, no need to run");
            return;
        }
        log.info("Processing install for StudentWeb item " + item.getID());

        boolean pass = this.isPass(item);
        boolean restricted = this.isRestricted(item);
        Date embargo = this.getEmbargoDate(item, context);

        String newState = this.getNewState(pass, restricted);
        this.processStateTransition(context, item, null, newState, embargo);
    }

    /**
     * Method to run when an item is installed in the repository
     *
     * @param context
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void onModifyMetadata(Context context, Item item)
            throws SQLException, AuthorizeException, IOException, DuoException
    {
        DuoState stateObj = new DuoState(item);
        if (!stateObj.isInstalled())
        {
            log.info("Item " + item.getID() + " is in workflow; not applying changes");
            return;
        }
        log.info("Processing Modify_Metadata for StudentWeb item " + item.getID());

        boolean pass = this.isPass(item);
        boolean restricted = this.isRestricted(item);
        boolean withdrawn = item.isWithdrawn();
        Date embargo = this.getEmbargoDate(item, context);

        String oldState = null;
        String newState = this.getNewState(pass, restricted);

        if (!withdrawn)
        {
            oldState = "pass";
        }
        else
        {
            if (restricted)
            {
                oldState = "restricted";
            }
            else
            {
                oldState = "fail";
            }
        }

        this.processStateTransition(context, item, oldState, newState, embargo);
    }

    public void onReinstate(Context context, Item item)
            throws SQLException, AuthorizeException, IOException, DuoException
    {
        log.info("Processing Reinstate for StudentWeb item " + item.getID());

        boolean pass = this.isPass(item);
        boolean restricted = this.isRestricted(item);
        Date embargo = this.getEmbargoDate(item, context);

        String oldState = "withdrawn";
        String newState = this.getNewState(pass, restricted);

        this.processStateTransition(context, item, oldState, newState, embargo);
    }

    private String getNewState(boolean pass, boolean restricted)
    {
        String newState = null;
        if (!pass)
        {
            newState = "fail";
        }
        else if (pass & !restricted)
        {
            newState = "pass";
        }
        else if (pass & restricted)
        {
            newState = "restricted";
        }
        return newState;
    }

    private void processStateTransition(Context context, Item item, String oldState, String newState, Date embargo)
            throws SQLException, AuthorizeException, IOException, DuoException
    {
        if (newState == null)
        {
            throw new DuoException("FSRestrictionManager cannot implement a restriction for a newState that is null");
        }

        if (oldState != null && !"restricted".equals(oldState) && !"fail".equals(oldState) && !"pass".equals(oldState) && !"withdrawn".equals(oldState))
        {
            throw new DuoException("FSRestrictionManager received invalid oldState " + oldState);
        }

        if (!"restricted".equals(newState) && !"fail".equals(newState) && !"pass".equals(newState))
        {
            throw new DuoException("FSRestrictionManager received invalid newState " + newState);
        }

        log.info("Processing state transition for item " + item.getID() + "; oldState=" + oldState + " newState=" + newState + " embargo=" + embargo);

        // first, let's ignore any null state transitions

        // if the states are the same and we don't have to worry about an embargo, no need to go further
        /*
        if (newState.equals(oldState) && embargo == null)
        {
            log.info("Before and after states are equal, and no embargo.  No action.");
            return;
        }

        // if we are moving from restricted to fail (or vice versa), and we don't have to worry about an embargo
        if ("restricted".equals(oldState) && "fail".equals(newState) && embargo == null)
        {
            log.info("Before and after states are equivalent, and no embargo.  No action.");
            return;
        }
        if ("fail".equals(oldState) && "restricted".equals(newState) && embargo == null)
        {
            log.info("Before and after states are equivalent, and no embargo.  No action.");
            return;
        }*/

        // for convenience, make a bunch of booleans
        boolean fromWithdrawn = "withdrawn".equals(oldState);
        boolean fromRestrictedFail = "restricted".equals(oldState) || "fail".equals(oldState);
        boolean toRestrictedFail = "restricted".equals(newState) || "fail".equals(newState);
        boolean toPass = "pass".equals(newState);
        boolean fromPass = "pass".equals(oldState);
        boolean fromNull = oldState == null;

        // explicitly lay out the transitions for clarity
        if (fromNull)
        {
            if (toRestrictedFail)
            {
                this.fromPassNullToRestrictedFail(context, item, oldState, newState);
            }
            else if (toPass)
            {
                this.fromNullToPass(context, item);
            }
        }
        else if (fromRestrictedFail)
        {
            if (toPass)
            {
                this.fromRestrictedFailToPass(context, item, oldState);
            }
            else if (toRestrictedFail)
            {
                log.info("Transition from fail/restricted to fail/restricted; embargo may need to be applied/removed");
                this.applyPolicyPatternManager(item, context, false);
            }
        }
        else if (fromPass)
        {
            if (toRestrictedFail)
            {
                this.fromPassNullToRestrictedFail(context, item, oldState, newState);
            }
            else if (toPass)
            {
                log.info("Transition from pass to pass; embargo may need to be applied/removed");
                this.applyPolicyPatternManager(item, context, false);
            }
        }
        else if (fromWithdrawn)
        {
            if (toRestrictedFail)
            {
                this.fromWithdrawnToRestrictedFail(context, item);
            }
            else if (toPass)
            {
                this.fromWithdrawnToPass(context, item);
            }
        }
        else
        {
            log.info("You shouldn't be in this state, how did you get here?  Applying policy manager anyway");
            this.applyPolicyPatternManager(item, context, false);
        }
    }

    private void fromNullToPass(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Item " + item.getID() + " transitioning from no state to pass");
        this.applyPolicyPatternManager(item, context, true);
    }

    private void fromRestrictedFailToPass(Context context, Item item, String oldState)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Item " + item.getID() + " transitioning from no restricted/fail to pass");
        this.applyPolicyPatternManager(item, context, false);
        this.alert(item, oldState, "pass");
    }

    private void fromPassNullToRestrictedFail(Context context, Item item, String oldState, String newState)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Item " + item.getID() + " transitioning from pass/new to restricted/fail");
        this.original2Admin(item);
        boolean asNew = oldState == null;
        this.applyPolicyPatternManager(item, context, asNew);
        this.withdraw(item);
        this.alert(item, oldState, newState);
    }

    private void fromWithdrawnToPass(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Item " + item.getID() + " being restored to archive in state: pass");
        this.applyPolicyPatternManager(item, context, false);
    }

    private void fromWithdrawnToRestrictedFail(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Item " + item.getID() + " transitioning from withdrawn to archive in state: restricted/fail");
        this.original2Admin(item);
        this.applyPolicyPatternManager(item, context, false);
        this.withdraw(item);
    }

    /**
     * Does the item have a pass grade?
     *
     * @param item
     * @return
     */
    private boolean isPass(Item item)
    {
        String gradeField = ConfigurationManager.getProperty("studentweb", "grade.field");
        DCValue[] dcvs = item.getMetadata(gradeField);
        for (DCValue dcv : dcvs)
        {
            if (DuoConstants.FS_PASS.equals(dcv.value.trim()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Does the item have a restricted embargo type
     *
     * @param item
     * @return
     */
    private boolean isRestricted(Item item)
    {
        String gradeField = ConfigurationManager.getProperty("studentweb", "embargo-type.field");
        DCValue[] dcvs = item.getMetadata(gradeField);
        for (DCValue dcv : dcvs)
        {
            if (DuoConstants.FS_RESTRICTED.equals(dcv.value.trim()))
            {
                return true;
            }
        }
        return false;
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
        return embargoDate.toDate();
    }

    /**
     * Move all the bitstreams in the ORIGINAL bundle to the DUO_ADMIN bundle
     *
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    private void original2Admin(Item item)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Moving " + DuoConstants.ORIGINAL_BUNDLE + " bundle bitstreams to " + DuoConstants.ADMIN_BUNDLE + " for item " + item.getID());

        Bundle admin = null;
        Bundle[] admins = item.getBundles(DuoConstants.ADMIN_BUNDLE);
        if (admins.length > 0)
        {
            admin = admins[0];
        }
        else
        {
            admin = item.createBundle(DuoConstants.ADMIN_BUNDLE);
        }

        BitstreamIterator bsi =  new BitstreamIterator(item, DuoConstants.ORIGINAL_BUNDLE);
        while (bsi.hasNext())
        {
            ContextualBitstream cb = bsi.next();
            Bitstream bs = cb.getBitstream();
            Bundle bundle = cb.getBundle();

            admin.addBitstream(bs);
            bundle.removeBitstream(bs);
            log.info("Moved bitstream " + bs.getID() + " from " + DuoConstants.ORIGINAL_BUNDLE + " to " + DuoConstants.ADMIN_BUNDLE + " for item " + item.getID());
        }

        for (Bundle bundle : item.getBundles())
        {
            if (bundle.getBitstreams().length == 0)
            {
                item.removeBundle(bundle);
            }
        }
    }

    /**
     * Apply the policy pattern manager (if the collection configured is the correct one) to the item
     *
     * @param item
     * @param context
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    private void applyPolicyPatternManager(Item item, Context context, boolean asNew)
            throws SQLException, AuthorizeException, IOException
    {
        if (PolicyApplicationFilter.allow(context, item))
        {
            log.info("Applying PolicyPatternManager to item " + item.getID());
            PolicyPatternManager ppm = new PolicyPatternManager();
            if (asNew)
            {
                ppm.applyToNewItem(item, context);
            }
            else
            {
                ppm.applyToExistingItem(item, context);
            }
        }
        else
        {
            log.info("Not applying PolicyPatternManater to item " + item.getID());
        }
    }

    /**
     * Withdraw the item from the repository
     *
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    private void withdraw(Item item)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Withdrawing item " + item.getID());
        item.withdraw();
    }

    /**
     * Send an email alert to the repository administrator indicating that the item has been restricted
     *
     * @param item
     * @param oldState
     * @param newState
     * @throws IOException
     */
    private void alert(Item item, String oldState, String newState)
            throws IOException
    {
        // unpack the incoming strings into useful booleans
        boolean install = oldState == null;
        boolean modify = "pass".equals(oldState) || "restricted".equals(oldState) || "fail".equals(oldState);
        boolean restricted = "restricted".equals(newState);
        boolean fail = "fail".equals(newState);
        boolean pass = "pass".equals(newState);

        String to = ConfigurationManager.getProperty("mail.admin");

        // the item that has changed
        String itemArg = "";
        DCValue[] titles = item.getMetadata("dc.title");
        if (titles.length > 0)
        {
            itemArg += titles[0].value;
        }
        else
        {
            itemArg += "Untitled";
        }
        itemArg += " (" + item.getHandle() + ")";

        Email email;
        if (install)
        {
            log.info("Sending email alert for install; failed or pass with restricted embargo type for item " + item.getID());

            email = Email.getEmail(I18nUtil.getEmailFilename(I18nUtil.getDefaultLocale(), "installrestricted"));
            email.addRecipient(to);

            // add the arguments, which are:
            // {0} the prefix for the subject
            // {1} the item that has changed

            // {0} the prefix for the subject
            String prefix = restricted ? "RESTRICTED" : "FAILED";
            email.addArgument(prefix);

            // {1} the item that has changed
            email.addArgument(itemArg);
        }
        else
        {
            if (pass)
            {
                log.info("Sending email alert for modify_metadata; pass for item " + item.getID());

                email = Email.getEmail(I18nUtil.getEmailFilename(I18nUtil.getDefaultLocale(), "modifymetadatacheckfiles"));
                email.addRecipient(to);

                // add the arguments, which are:
                // {0} the item that has changed
                email.addArgument(itemArg);
            }
            else
            {
                log.info("Sending email alert for modify_metadata; failed or pass with restricted embargo type for item " + item.getID());

                email = Email.getEmail(I18nUtil.getEmailFilename(I18nUtil.getDefaultLocale(), "modifymetadatarestricted"));
                email.addRecipient(to);

                // add the arguments, which are:
                // {0} the prefix for the subject
                // {1} the item that has changed

                // {0} the prefix for the subject
                String prefix = restricted ? "RESTRICTED" : "FAILED";
                email.addArgument(prefix);

                // {1} the item that has changed
                email.addArgument(itemArg);
            }
        }

        // now send it
        try
        {
            email.send();
        }
        catch (MessagingException e)
        {
            throw new IOException(e);
        }
    }
}
