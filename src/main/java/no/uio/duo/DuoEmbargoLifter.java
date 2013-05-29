package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.embargo.EmbargoLifter;

import java.io.IOException;
import java.sql.SQLException;

/**
 * <p>Implemetation of the EmbargoLifter, which ensures that Duo polices
 * are set as required</p>
 *
 * <p>This is basically a wrapper around a call to the DuoPolicyManager, which
 * sets the default policies on an item, in accordance with the Duo requirements.</p>
 */
public class DuoEmbargoLifter implements EmbargoLifter
{
    /**
     * Lift the embargo on the item
     *
     * This applies the Duo standard polices, see the {@link; DuoPolicyManager} for details
     *
     * @param context
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void liftEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        DuoPolicyManager dpm = new DuoPolicyManager();
        dpm.setDefaultPolicies(context, item);
    }
}
