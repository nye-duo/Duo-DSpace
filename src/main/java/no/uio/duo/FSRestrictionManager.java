package no.uio.duo;


import no.uio.duo.policy.ContextualBitstream;
import no.uio.duo.policy.PolicyApplicationFilter;
import no.uio.duo.policy.PolicyPatternManager;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;

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

    public void onInstall(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Processing install for StudentWeb item " + item.getID());

        boolean pass = this.isPass(item);
        boolean restricted = this.isRestricted(item);

        if (!pass || (pass && restricted))
        {
            log.info("Item " + item.getID() + " is a fail or a pass with a restricted embargo type");
            this.original2Admin(item);
            this.applyPolicyPatternManager(item, context);
            this.withdraw(item);
            this.alert(item, pass, restricted);
        }
        else
        {
            log.info("Item " + item.getID() + " is a pass without a restricted embargo type");
            this.applyPolicyPatternManager(item, context);
        }
    }

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

    private void applyPolicyPatternManager(Item item, Context context)
            throws SQLException, AuthorizeException, IOException
    {
        if (PolicyApplicationFilter.allow(context, item))
        {
            log.info("Applying PolicyPatternManager to item " + item.getID());
            PolicyPatternManager ppm = new PolicyPatternManager();
            ppm.applyToNewItem(item, context);
        }
        else
        {
            log.info("Not applying PolicyPatternManater to item " + item.getID());
        }
    }

    private void withdraw(Item item)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Withdrawing item " + item.getID());
        item.withdraw();
    }

    private void alert(Item item, boolean pass, boolean restricted)
            throws IOException
    {
        log.info("Sending email alert for failed or pass with restricted embargo type for item " + item.getID());

        Email email = Email.getEmail(I18nUtil.getEmailFilename(I18nUtil.getDefaultLocale(), "installrestricted"));
        String to = ConfigurationManager.getProperty("mail.admin");
        email.addRecipient(to);

        // add the arguments, which are:
        // {0} the prefix for the subject
        // {1} the item that has changed

        // {0} the prefix for the subject
        String prefix = "";
        if (!pass)
        {
            prefix = "FAILED";
        }
        else if (pass && restricted)
        {
            prefix = "RESTRICTED";
        }
        email.addArgument(prefix);

        // {1} the item that has changed
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
        email.addArgument(itemArg);

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
