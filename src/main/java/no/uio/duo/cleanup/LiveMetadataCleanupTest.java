package no.uio.duo.cleanup;

import no.uio.duo.WorkflowManagerWrapper;
import no.uio.duo.livetest.LiveTest;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;

/**
 * A Live test for ensuring that HTML can be cleared from metadata.  When run it produces an item with
 * metadata containing HTML suitable for testing the cleanup tool.
 */
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

    /**
     * Create a new metadata cleanup test instance
     *
     * @param epersonEmail
     * @throws Exception
     */
    public LiveMetadataCleanupTest(String epersonEmail)
            throws Exception
    {
        super(epersonEmail);
        this.collection = this.makeCollection();
    }

    /**
     * Run the utility, which will produce a single item with HTML in the metadata
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
     * Make a test item for the cleanup
     *
     * A test item consists of a minimal amount of metadata containing HTML.  The item does not contain
     * any bitstreams.
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
