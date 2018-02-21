package no.uio.duo.cleanup;

import no.uio.duo.MetadataManager;
import no.uio.duo.TraverseDSpace;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool to run a cleanup of all HTML in the item metadata
 */
public class MetadataCleanup extends TraverseDSpace
{
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();
        Options options = new Options();
        options.addOption("e", "eperson", true, "EPerson to do the cleanup as");
        options.addOption("i", "item", true, "Item id on which to perform the cleanup");
        options.addOption("h", "handle", true, "Item handle on which to perform the cleanup");
        options.addOption("l", "collection", true, "Collection handle on which to perform the cleanup");
        options.addOption("m", "community", true, "Community handle on which to perform the cleanup");
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

    /**
     * If a field has special rules associated with it, regarding which HTML tags are permitted,
     * this member variable holds the mapping between the dc field and the list of allowed tags
     */
    public static Map<String, List<String>> allowedHTMLByField = new HashMap<String, List<String>>();
    static {
        List<String> lineSeparators = new ArrayList<String>();
        lineSeparators.add("br");
        lineSeparators.add("p");

        allowedHTMLByField.put("dc.description.abstract", lineSeparators);
    }

    /**
     * Create a new instance of the metadata cleanup utility
     *
     * @param epersonEmail
     * @throws Exception
     */
    public MetadataCleanup(String epersonEmail)
            throws Exception
    {
        super(epersonEmail, true);
    }

    /**
     * Cleanup a given item
     *
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     * @throws Exception
     */
    public void processItem(Item item)
            throws SQLException, AuthorizeException, IOException, Exception
    {
        MetadataManager mm = new MetadataManager();

        Whitelist base = Whitelist.none();
        List<DCValue> cleanMetadata = new ArrayList<DCValue>();

        DCValue[] allMetadata = mm.allMetadata(item);
        for (DCValue dcv : allMetadata)
        {
            Whitelist custom = null;

            String fieldString = mm.makeFieldString(dcv);
            if (allowedHTMLByField.containsKey(fieldString))
            {
                custom = Whitelist.none();
                List<String> allowed = allowedHTMLByField.get(fieldString);
                for (String tag : allowed)
                {
                    custom.addTags(tag);
                }
            }

            Whitelist apply = base;
            if (custom != null)
            {
                apply = custom;
            }

            dcv.value = Jsoup.clean(dcv.value, apply);

            cleanMetadata.add(dcv);
        }

        mm.replaceMetadata(item, cleanMetadata);
        item.update();
    }
}
