package no.uio.duo.policy;

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


public class DuoEmbargoSetter implements EmbargoSetter
{
    private EmbargoSetter fallback;
    private PolicyPatternManager policies;

    public DuoEmbargoSetter()
    {
        this.fallback = (EmbargoSetter) PluginManager.getNamedPlugin(EmbargoSetter.class, "fallback");
        this.policies = new PolicyPatternManager();
    }

    @Override
    public DCDate parseTerms(Context context, Item item, String s)
            throws SQLException, AuthorizeException, IOException
    {
        return this.fallback.parseTerms(context, item, s);
    }

    @Override
    public void setEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        if (this.allow(context, item))
        {
            this.policies.applyToNewItem(item, context);
        }
        else
        {
            this.fallback.setEmbargo(context, item);
        }
    }

    @Override
    public void checkEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        if (this.allow(context, item))
        {
            this.policies.applyToExistingItem(item, context);
        }
        else
        {
            this.fallback.checkEmbargo(context, item);
        }
    }

    private boolean allow(Context context, Item item)
            throws SQLException
    {
        String liftDateField = ConfigurationManager.getProperty("embargo.field.lift");
        if (liftDateField == null)
        {
            return false;
        }

        DCValue[] dcvs = item.getMetadata(liftDateField);
        if (dcvs.length == 0)
        {
            return false;
        }

        String scopeCfg = ConfigurationManager.getProperty("duo.embargo.communities");
        if (scopeCfg == null)
        {
            return true;
        }

        List<Integer> communityIDs = new ArrayList<Integer>();
        String[] handles = scopeCfg.split(",");
        for (String handle : handles)
        {
            handle = handle.trim();
            DSpaceObject dso = HandleManager.resolveToObject(context, handle);
            if (dso instanceof Community)
            {
                communityIDs.add(dso.getID());
            }
        }

        Community[] coms = item.getCommunities();
        for (Community com : coms)
        {
            if (communityIDs.contains(com.getID()))
            {
                return true;
            }
        }

        return false;
    }
}
