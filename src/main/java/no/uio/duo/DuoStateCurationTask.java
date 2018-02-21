package no.uio.duo;

import no.uio.duo.policy.PolicyPatternManager;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Curation task which allows the {@link DuoState} method synchroniseItemState to be run from the user interface or
 * the command line
 */
public class DuoStateCurationTask extends AbstractCurationTask
{
    /**
     * Execute the {@link DuoState} synchroniseItemState over the given DSpace Object
     *
     * This will only run of the DSpace Object is an item.
     *
     * @param dso
     * @return
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso)
            throws IOException
    {
        if (!(dso instanceof Item))
        {
            return Curator.CURATE_SKIP;
        }

        Item item = (Item) dso;

        // we haven't been given a context, so make our own one
        Context context;
        try
        {
            context = new Context();
        }
        catch (SQLException e)
        {
            return Curator.CURATE_ERROR;
        }

        boolean error = false;
        try
        {
            // The results that we'll return
            StringBuilder results = new StringBuilder();

            // synchronise the current item state into the duo.state.field
            DuoState ds = new DuoState(item);
            ds.sychroniseItemState(true);

            this.setResult(results.toString());
            this.report(results.toString());
        }
        catch (Exception e)
        {
            context.abort();
            error = true;
        }
        finally
        {
            try
            {
                context.complete();
            }
            catch (SQLException e)
            {
                context.abort();
                error = true;
            }
        }

        if (error)
        {
            return Curator.CURATE_ERROR;
        }

        return Curator.CURATE_SUCCESS;
    }
}
