package no.uio.duo.policy;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.embargo.DefaultEmbargoSetter;
import org.dspace.embargo.EmbargoSetter;

import java.io.IOException;
import java.sql.SQLException;


public class DuoEmbargoSetter implements EmbargoSetter
{
    private EmbargoSetter dspaceDefault;
    private PolicyPatternManager policies;

    public DuoEmbargoSetter()
    {
        this.dspaceDefault = new DefaultEmbargoSetter();
        this.policies = new PolicyPatternManager();
    }

    @Override
    public DCDate parseTerms(Context context, Item item, String s)
            throws SQLException, AuthorizeException, IOException
    {
        return this.dspaceDefault.parseTerms(context, item, s);
    }

    @Override
    public void setEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        this.policies.applyToNewItem(item, context);
    }

    @Override
    public void checkEmbargo(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        this.policies.applyToExistingItem(item, context);
    }
}
