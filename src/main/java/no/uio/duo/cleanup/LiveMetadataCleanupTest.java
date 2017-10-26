package no.uio.duo.cleanup;

import no.uio.duo.WorkflowManagerWrapper;
import no.uio.duo.livetest.LiveTest;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;

public class LiveMetadataCleanupTest extends LiveTest
{
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();
        Options options = new Options();
        options.addOption("e", "eperson", true, "EPerson to do the item construction as");
        CommandLine line = parser.parse(options, args);

        if (!line.hasOption("e"))
        {
            System.out.println("Please provide an eperson email with the -e argument");
            System.exit(0);
        }

        LiveMetadataCleanupTest lmct = new LiveMetadataCleanupTest(line.getOptionValue("e"));
        lmct.runAll();
    }

    // private Collection collection;

    public LiveMetadataCleanupTest(String epersonEmail)
            throws Exception
    {
        super(epersonEmail);
        this.collection = this.makeCollection();
    }

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

        item.addMetadata("dc", "title", null, null, "This title has <br> <html> in it </div>");
        item.addMetadata("dc", "description", "abstract", null, "This abstract also has <br> <html> in it </div>");

        item.update();
        this.context.commit();

        return item;
    }
}
