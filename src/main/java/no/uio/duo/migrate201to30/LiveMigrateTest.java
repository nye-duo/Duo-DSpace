package no.uio.duo.migrate201to30;

import no.uio.duo.MetadataManager;
import no.uio.duo.WorkflowManagerWrapper;
import no.uio.duo.livetest.LiveTest;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * <p>A migration test to be run against a live DSpace.  All this actually does is create a single item suitable
 * for testing a run of the migration script itself.</p>
 *
 * <p><strong>DO NOT RUN THIS ON A PRODUCTION SYSTEM</strong></p>
 */
public class LiveMigrateTest extends LiveTest
{
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();
        Options options = new Options();
        options.addOption("e", "eperson", true, "EPerson to do the migration as");
        options.addOption("b", "bistream", true, "File path to bitstream to use for testing");
        CommandLine line = parser.parse(options, args);

        if (!line.hasOption("e"))
        {
            System.out.println("Please provide an eperson email with the -e argument");
            System.exit(0);
        }
        if (!line.hasOption("b"))
        {
            System.out.println("Please provide a path to a bitstream to use for testing with the -b option");
            System.exit(0);
        }

        LiveMigrateTest lmt = new LiveMigrateTest(line.getOptionValue("e"), line.getOptionValue("b"));
        lmt.runAll();
    }

    public LiveMigrateTest(String epersonEmail, String bitstreamPath)
            throws Exception
    {
        super(epersonEmail);

        this.bitstream = new File(bitstreamPath);
        this.collection = this.makeCollection();
    }

    /**
     * Execute all test activities.  This just creates a single item with makeTestItem
     *
     * @throws Exception
     */
    public void runAll()
            throws Exception
    {
        Item item = this.makeTestItem();
        this.context.complete();

        System.out.println("Made Item with ID " + item.getID() + " and handle " + item.getHandle());
    }

    /**
     * Make a test item for the migration.
     *
     * A test item consists of a minimal amount of metadata, including an embargo date from today (new Date()),
     * and a lot of bundles each containing a single file.
     *
     * @return
     * @throws Exception
     */
    public Item makeTestItem()
            throws Exception
    {
        // make the item in the collection
        WorkspaceItem wsi = WorkspaceItem.create(this.context, this.collection, false);
        Item item = wsi.getItem();

        WorkflowManagerWrapper.startWithoutNotify(this.context, wsi);
        item = Item.find(this.context, item.getID());
        item.addMetadata("dc", "title", null, null, "Item ID " + item.getID());

        // set the embargo date
        SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");
        String ed = sdf.format(new Date());
        String md = ConfigurationManager.getProperty("embargo.field.terms");
        MetadataManager mm = new MetadataManager();
        DCValue dcv = mm.makeDCValue(md, null);
        item.addMetadata(dcv.schema, dcv.element, dcv.qualifier, null, ed);

        // create a file in all the relevant bundles
        this.makeBitstream(item, "ORIGINAL", 1);
        this.makeBitstream(item, "ORIGINAL", 2);
        this.makeBitstream(item, "SECONDARY");
        this.makeBitstream(item, "SECONDARY_CLOSED");
        this.makeBitstream(item, "RESTRICTED");
        this.makeBitstream(item, "SWORD");
        this.makeBitstream(item, "ORE");
        this.makeBitstream(item, "METADATA");
        this.makeBitstream(item, "LICENSE");
        this.makeBitstream(item, "DELETED");

        // add an empty bundle, to make sure they are treated right
        this.makeBundle(item, "EMPTY");

        item.update();
        this.context.commit();

        return item;
    }
}
