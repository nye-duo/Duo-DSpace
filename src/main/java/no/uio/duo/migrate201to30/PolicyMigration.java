package no.uio.duo.migrate201to30;

import no.uio.duo.BitstreamIterator;
import no.uio.duo.DuoConstants;
import no.uio.duo.TraverseDSpace;
import no.uio.duo.policy.ContextualBitstream;
import no.uio.duo.policy.PolicyPatternManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.handle.HandleManager;


import java.io.IOException;
import java.sql.SQLException;

/**
 * Script which carries out a migration of all items so that they match the new policy pattern.
 *
 * The following actions are taken:
 *
 * <ul>
 *     <li>Any bitstreams in bundle SECONDARY are moved to DUO_2NDRY_CLOSED</li>
 *     <li>Any bitstreams in bundle SECONDARY_CLOSED are moved to DUO_2NDRY_CLOSED</li>
 *     <li>Any bitstreams in bundle RESTRICTED are moved to DUO_ADMIN</li>
 *     <li>Any bitstreams in bundle METADATA are moved to DUO_ADMIN</li>
 *     <li>LICENSE bundle and associated bitstreams are removed</li>
 *     <li>DELETED bundle and associated bitstreams are removed</li>
 *     <li>PolicyPatternManager is applied to all items that are isArchived()</li>
 * </ul>
 *
 */
public class PolicyMigration extends TraverseDSpace
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

        if (line.hasOption("i") && line.hasOption("h")) {
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

        PolicyMigration pm = new PolicyMigration(line.getOptionValue("e"));
        if (line.hasOption("i") || line.hasOption("h"))
        {
            int iid = -1;
            String id = line.getOptionValue("i");
            if (id != null)
            {
                iid = Integer.parseInt(id);
            }
            pm.doItem(iid, line.getOptionValue("h"));
        }
        else if (line.hasOption("l"))
        {
            pm.doCollection(line.getOptionValue("l"));
        }
        else if (line.hasOption("m"))
        {
            pm.doCommunity(line.getOptionValue("m"));
        }
        else
        {
            pm.doDSpace();
        }
        pm.report();
    }

    /**
     * Create a new instance of the policy migration tool
     *
     * @param epersonEmail
     * @throws Exception
     */
    public PolicyMigration(String epersonEmail)
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
    public void processItem(Item item)
            throws SQLException, AuthorizeException, IOException, Exception
    {
        // first move all of the bitstreams
        Bundle secondary = null;
        Bundle admin = null;

        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            ContextualBitstream cb = bsi.next();
            Bundle bundle = cb.getBundle();
            String name = bundle.getName();

            Bitstream bs = cb.getBitstream();

            if ("SECONDARY".equals(name))
            {
                if (secondary == null)
                {
                    secondary = item.createBundle(DuoConstants.SECONDARY_BUNDLE);
                }
                secondary.addBitstream(bs);
                bundle.removeBitstream(bs);
            }
            else if ("SECONDARY_CLOSED".equals(name))
            {
                if (secondary == null)
                {
                    secondary = item.createBundle(DuoConstants.SECONDARY_BUNDLE);
                }
                secondary.addBitstream(bs);
                bundle.removeBitstream(bs);
            }
            else if ("RESTRICTED".equals(name))
            {
                if (admin == null)
                {
                    admin = item.createBundle(DuoConstants.ADMIN_BUNDLE);
                }
                admin.addBitstream(bs);
                bundle.removeBitstream(bs);
            }
            else if ("METADATA".equals(name))
            {
                if (admin == null)
                {
                    admin = item.createBundle(DuoConstants.ADMIN_BUNDLE);
                }
                admin.addBitstream(bs);
                bundle.removeBitstream(bs);
            }
            else if ("LICENSE".equals(name))
            {
                bundle.removeBitstream(bs);
            }
            else if ("DELETED".equals(name))
            {
                bundle.removeBitstream(bs);
            }
        }

        for (Bundle bundle : item.getBundles())
        {
            if (bundle.getBitstreams().length == 0)
            {
                item.removeBundle(bundle);
            }
        }
        
        // after the first stage, commit the context
        this.context.commit();

        // apply the policy pattern manager to items in the repository (archived or withdrawn)
        if (item.isArchived() || item.isWithdrawn())
        {
            PolicyPatternManager ppm = new PolicyPatternManager();
            ppm.applyToExistingItem(item, this.context);

            // after this final stage, commit the context again
            this.context.commit();
        }
    }
}
