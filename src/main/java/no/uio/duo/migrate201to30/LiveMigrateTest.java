package no.uio.duo.migrate201to30;

import no.uio.duo.WorkflowManagerWrapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LiveMigrateTest
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

    private Context context;
    private EPerson eperson;
    private Collection collection;
    private File bitstream;

    public LiveMigrateTest(String epersonEmail, String bitstreamPath)
            throws Exception
    {
        this.bitstream = new File(bitstreamPath);
        this.context = new Context();

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);

        this.collection = this.makeCollection();
    }

    public void runAll()
            throws Exception
    {
        Item item = this.makeTestItem();
        this.context.complete();

        System.out.println("Made Item " + item.getID());
    }

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
        DCValue dcv = this.stringToDC(md);
        item.addMetadata(dcv.schema, dcv.element, dcv.qualifier, null, ed);

        // create a file in all the relevant bundles
        this.makeBitstream(item, "ORIGINAL");
        this.makeBitstream(item, "SECONDARY");
        this.makeBitstream(item, "SECONDARY_CLOSED");
        this.makeBitstream(item, "RESTRICTED");
        this.makeBitstream(item, "SWORD");
        this.makeBitstream(item, "ORE");
        this.makeBitstream(item, "METADATA");
        this.makeBitstream(item, "LICENSE");
        this.makeBitstream(item, "DELETED");

        item.update();
        this.context.commit();

        return item;
    }

    private Bitstream makeBitstream(Item item, String bundle)
            throws Exception
    {
        InputStream originalFile = new FileInputStream(this.bitstream);
        Bitstream bs = item.createSingleBitstream(originalFile, bundle);
        bs.setName(bundle + "file.txt");
        bs.update();
        return bs;
    }

    private Collection makeCollection()
            throws Exception
    {
        Community community = Community.create(null, this.context);
        community.setMetadata("name", "Policy Test Community " + community.getID());
        community.update();

        Collection collection = community.createCollection();
        collection.setMetadata("name", "Policy Test Collection " + collection.getID());
        collection.update();

        this.context.commit();

        System.out.println("Created community with id " + community.getID() + "; handle " + community.getHandle());
        System.out.println("Created collection with id " + collection.getID() + "; handle " + collection.getHandle());

        return collection;
    }

    private DCValue stringToDC(String field)
    {
        String[] bits = field.split("\\.");
        DCValue dcv = new DCValue();
        dcv.schema = bits[0];
        dcv.element = bits[1];
        if (bits.length > 2)
        {
            dcv.qualifier = bits[2];
        }
        return dcv;
    }
}
