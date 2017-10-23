package no.uio.duo.cleanup;

import no.uio.duo.TraverseDSpace;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.handle.HandleManager;

import java.io.IOException;
import java.sql.SQLException;

public class MetadataCleanup extends TraverseDSpace
{
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();
        Options options = new Options();
        options.addOption("e", "eperson", true, "EPerson to do the migration as");
        options.addOption("i", "item", true, "Item id on which to perform the migration");
        options.addOption("h", "handle", true, "Item handle on which to perform the migration");
        options.addOption("l", "collection", true, "Collection handle on which to perform the migration");
        options.addOption("m", "community", true, "Community handle on which to perform the migration");
        CommandLine line = parser.parse(options, args);

        if (!line.hasOption("e"))
        {
            System.out.println("Please provide an eperson email with the -e argument");
            System.exit(0);
        }

        if (line.hasOption("i") && line.hasOption("h"))
        {
            System.out.println("Please provide either -i or -h but not both");
            System.exit(0);
        }

        int idents = 0;
        if (line.hasOption("i") || line.hasOption("h"))
        {
            idents++;
        }
        if (line.hasOption("l"))
        {
            idents++;
        }
        if (line.hasOption("m"))
        {
            idents++;
        }
        if (idents > 1)
        {
            System.out.println("Please provide -i, -h, -l or -m but not more than one of those");
            System.exit(0);
        }

        MetadataCleanup mc = new MetadataCleanup(line.getOptionValue("e"));
        if (line.hasOption("i") || line.hasOption("h"))
        {
            int iid = -1;
            String id = line.getOptionValue("i");
            if (id != null)
            {
                iid = Integer.parseInt(id);
            }
            mc.cleanItem(iid, line.getOptionValue("h"));
        }
        else if (line.hasOption("l"))
        {
            mc.cleanCollection(line.getOptionValue("l"));
        }
        else if (line.hasOption("m"))
        {
            mc.cleanCommunity(line.getOptionValue("m"));
        }
        else
        {
            mc.cleanAll();
        }
    }
    public MetadataCleanup(String epersonEmail)
            throws Exception
    {
        super(epersonEmail);
    }

    /**
     * Migrate the collection specified by the handle.  This does all items in workspace, workflow, archive and withdrawn
     *
     * @param handle
     * @throws Exception
     */
    public void cleanCollection(String handle)
            throws Exception
    {
        try
        {
            this.doCollection(handle);
        }
        catch (Exception e)
        {
            this.context.abort();
            throw e;
        }
        finally
        {
            if (this.context.isValid())
            {
                this.context.complete();
            }
        }

        System.out.println("Processed 1 Collection");
    }

    /**
     * Migrate the community specified by the handle.  This does all sub communities, collections and their items
     * in workspace, workflow, archive and withdrawn
     *
     * @param handle
     * @throws Exception
     */
    public void cleanCommunity(String handle)
            throws Exception
    {
        try
        {
            this.doCommunity(handle);
        }
        catch (Exception e)
        {
            this.context.abort();
            throw e;
        }
        finally
        {
            if (this.context.isValid())
            {
                this.context.complete();
            }
        }

        System.out.println("Processed 1 Community");
    }

    /**
     * On an item identified either by the given id or the given handle
     *
     * @param id
     * @param handle
     * @throws Exception
     */
    public void cleanItem(int id, String handle)
            throws Exception
    {
        try
        {
            Item item = null;
            if (id > -1)
            {
                item = Item.find(this.context, id);
            }
            else if (handle != null)
            {
                item = (Item) HandleManager.resolveToObject(this.context, handle);
            }
            if (item != null)
            {
                this.doItem(item);
            }
        }
        catch (Exception e)
        {
            this.context.abort();
            throw e;
        }
        finally
        {
            if (this.context.isValid())
            {
                this.context.complete();
            }
        }

        System.out.println("Processed 1 Item");
    }

    /**
     * Execute the migration on all DSpace items
     *
     * @throws Exception
     */
    public void cleanAll()
            throws Exception
    {
        try
        {
            this.doDSpace();
        }
        catch (Exception e)
        {
            this.context.abort();
            throw e;
        }
        finally
        {
            if (this.context.isValid())
            {
                this.context.complete();
            }
        }

        System.out.println("Processed " + this.itemCount + " Items");
    }

    /**
     * Migrate a given item
     *
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     * @throws Exception
     */
    public void doItem(Item item)
            throws SQLException, AuthorizeException, IOException, Exception
    {

    }
}
