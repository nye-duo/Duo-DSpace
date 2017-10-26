package no.uio.duo.cleanup;

import no.uio.duo.TraverseDSpace;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;

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
            mc.doItem(iid, line.getOptionValue("h"));
        }
        else if (line.hasOption("l"))
        {
            mc.doCollection(line.getOptionValue("l"));
        }
        else if (line.hasOption("m"))
        {
            mc.doCommunity(line.getOptionValue("m"));
        }
        else
        {
            mc.doDSpace();
        }
        mc.report();
    }

    public MetadataCleanup(String epersonEmail)
            throws Exception
    {
        super(epersonEmail, true);
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
        super.doItem(item);
    }
}
