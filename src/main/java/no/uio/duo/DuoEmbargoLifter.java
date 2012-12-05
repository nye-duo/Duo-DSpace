package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.embargo.EmbargoLifter;

import java.io.IOException;
import java.sql.SQLException;

public class DuoEmbargoLifter implements EmbargoLifter
{
    public void liftEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        DuoPolicyManager dpm = new DuoPolicyManager();
        dpm.setDefaultPolicies(context, item);
    }
}
