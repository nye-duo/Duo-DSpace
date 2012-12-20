package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DCDate;
import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.embargo.EmbargoManager;
import org.dspace.eperson.Group;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DuoInstallConsumer implements Consumer
{
    public void initialize() throws Exception { }

    public void end(Context context) throws Exception { }

    public void finish(Context context) throws Exception { }

    public void consume(Context context, Event event) throws Exception
    {
        Item item = (Item) event.getSubject(context);

        // we mustn't set the policies if the item is embargoed; these will have already
        // been removed, and should stay that way
        if (this.isEmbargoed(context, item))
        {
            return;
        }

        DuoPolicyManager dpm = new DuoPolicyManager();
        dpm.setDefaultPolicies(context, item);
    }

    private boolean isEmbargoed(Context context, Item item)
            throws Exception
    {
        // if an embargo field isn't configured, then the item can't be embargoed
        String liftDateField = ConfigurationManager.getProperty("embargo.field.lift");
        if (liftDateField == null)
        {
            return false;
        }

        // if there is no embargo value, the item isn't embargoed
        DCValue[] embargoes = item.getMetadata(liftDateField);
        if (embargoes.length == 0)
        {
            return false;
        }

        // if the embargo date is in the past, then the item isn't embargoed
        try
        {
            DCDate embargoDate = EmbargoManager.getEmbargoDate(context, item);
            if ((new Date()).getTime() > embargoDate.toDate().getTime())
            {
                return false;
            }
        }
        catch (SQLException e)
        {
            throw new Exception(e);
        }
        catch (AuthorizeException e)
        {
            throw new Exception(e);
        }
        catch (IOException e)
        {
            throw new Exception(e);
        }

        return true;
    }


}