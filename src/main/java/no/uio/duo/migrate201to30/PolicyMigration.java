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
        CommandLine line = parser.parse(options, args);

        if (!line.hasOption("e"))
        {
            System.out.println("Please provide an eperson email with the -e argument");
            System.exit(0);
        }

        PolicyMigration pm = new PolicyMigration(line.getOptionValue("e"));
        pm.migrate();
    }

    private int itemCount = 0;

    public PolicyMigration(String epersonEmail)
            throws Exception
    {
        super(epersonEmail);
    }

    /**
     * Execute the migration
     *
     * @throws Exception
     */
    public void migrate()
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

            if (bundle.getBitstreams().length == 0)
            {
                item.removeBundle(bundle);
            }
        }

        // after the first stage, commit the context
        this.context.commit();

        // apply the policy pattern manager to items in the repository
        if (item.isArchived())
        {
            PolicyPatternManager ppm = new PolicyPatternManager();
            ppm.applyToExistingItem(item, this.context);

            // after this final stage, commit the context again
            this.context.commit();
        }

        this.itemCount++;

    }
}
