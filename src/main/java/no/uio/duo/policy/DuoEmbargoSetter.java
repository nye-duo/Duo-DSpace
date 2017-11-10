package no.uio.duo.policy;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.PluginManager;
import org.dspace.embargo.EmbargoSetter;
import org.dspace.handle.HandleManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Implementation of the DSpace EmbargoSetter interface which applies the duo policy pattern where appropriate.</p>
 *
 * <p>Overall its behaviour is:</p>
 *
 * <ul>
 *     <li>Check if an item has an embargo date</li>
 *     <li>Check if the item is in the scope configured for this implementation (i.e. within an identified community)</li>
 * </ul>
 *
 * <p>If both of these checks pass, then the setter runs the {@link PolicyPatternManager} on the item.  If
 * either of the checks fail, then the fallback embargo setter is called instead (this can be the default DSpace one,
 * for example).</p>
 *
 * <p>Configuration required to make this work:</p>
 *
 * <code>
 * plugin.single.org.dspace.embargo.EmbargoSetter = no.uio.duo.policy.DuoEmbargoSetter<br>
 * plugin.named.org.dspace.embargo.EmbargoSetter = org.dspace.embargo.DefaultEmbargoSetter=fallback<br>
 * duo.embargo.communities = 123456789/10, 123456789/11
 * </code>
 *
 * <p>(duo.embargo.communities is optional, if you want to restrict behaviour to one or more communities)</p>
 */
public class DuoEmbargoSetter implements EmbargoSetter
{
    /** log4j logger */
    private static Logger log = Logger.getLogger(DuoEmbargoSetter.class);

    private EmbargoSetter fallback;
    private PolicyPatternManager policies;

    public DuoEmbargoSetter()
    {
        this.fallback = (EmbargoSetter) PluginManager.getNamedPlugin(EmbargoSetter.class, "fallback");
        this.policies = new PolicyPatternManager();
    }

    /**
     * Parse the embargo date.  Falls back to the fallback implementation in all cases
     *
     * @param context
     * @param item
     * @param s
     * @return
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    @Override
    public DCDate parseTerms(Context context, Item item, String s)
            throws SQLException, AuthorizeException, IOException
    {
        return this.fallback.parseTerms(context, item, s);
    }

    /**
     * Set the embargo on an item.  This runs {@link PolicyPatternManager}.applyToNewItem()
     * or calls setEmbargo on the fallback, depending on whether this setter is allowed to
     * run on this item
     *
     * @param context
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    @Override
    public void setEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        log.info("Setting embargo on item " + item.getID());
        if (PolicyApplicationFilter.allow(context, item))
        {
            log.info("Apply policy pattern manager to item " + item.getID());
            this.policies.applyToNewItem(item, context);
        }
        else
        {
            log.info("Falling back to standard Embargo Setter for item " + item.getID());
            this.fallback.setEmbargo(context, item);
        }
    }

    /**
     * Check the embargo on an item.  This runs {@link PolicyPatternManager}.applyToExistingItem() or
     * calls checkEmbargo on the fallback, depending on whether this setter is allowed to run on this
     * item
     *
     * @param context
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    @Override
    public void checkEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        if (PolicyApplicationFilter.allow(context, item))
        {
            this.policies.applyToExistingItem(item, context);
        }
        else
        {
            this.fallback.checkEmbargo(context, item);
        }
    }


}
