package no.uio.duo.policy;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Curation task which allows the {@link PolicyPatternManager} to be run from the user interface or
 * the command line
 */
public class DuoPolicyCurationTask extends AbstractCurationTask
{
    /**
     * Execute the {@link PolicyPatternManager} over the given DSpace Object
     *
     * This will only run of the DSpace Object is an item, and it will execute PolicyPatternManager.applyToExistingItem
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

            PolicyPatternManager ppm = new PolicyPatternManager();
            ppm.applyToExistingItem(item, context);

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
