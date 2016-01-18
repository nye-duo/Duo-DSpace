package no.uio.duo;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.IOUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;
import org.dspace.workflow.WorkflowItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NumberChecker
{
    private Context context;
    private boolean verbose = false;

    /**
     * The mimetypes that will have their content checked
     */
    private static final Set<String> MIMETYPES = new HashSet<String>(Arrays.asList(
            new String[] {"text/xml", "text/plain", "text/html", "application/atom+xml;type=entry", "application/atom+xml; type=entry"}
    ));

    /**
     * The file extension that will have their content checked
     */
    private static final Set<String> EXTENSIONS = new HashSet<String>(Arrays.asList(
            new String[] {"xml", "txt"}
    ));

    /**
     * Run this script.  No arguments are required
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();

        Options options = new Options();

        options.addOption("s", "scope", true, "item|collection|community");
        options.addOption("h", "handle", true, "item/collection/community handle");
        options.addOption("d", "dspace", false, "Run on entire DSpace");
        options.addOption("v", "verbose", false, "Output verbose check logging, which may obscure warnings");

        CommandLine line = parser.parse(options, args);

        String scope = null;
        String handle = null;
        boolean doAll = false;
        boolean verbose = false;

        if (line.hasOption("s") && line.hasOption("h"))
        {
            scope = line.getOptionValue("s");
            handle = line.getOptionValue("h");
        }

        if (line.hasOption("d"))
        {
            doAll = true;
        }

        if (line.hasOption("v"))
        {
            verbose = true;
        }

        NumberChecker nc = new NumberChecker(verbose);

        if (doAll)
        {
            nc.doDSpace();
        }
        else
        {
            if ("item".equals(scope))
            {
                nc.doItem(handle);
            }
            else if ("collection".equals(scope))
            {
                nc.doCollection(handle);
            }
            else if ("community".equals(scope))
            {
                nc.doCommunity(handle);
            }
            else
            {
                System.out.println("NO ACTION TAKEN: Specify a scope (-s) and a handle (-h) or to run on all of DSpace (-d)");
            }
        }
    }

    public NumberChecker(boolean verbose)
            throws Exception
    {
        this.verbose = verbose;
        this.context = new Context();
        this.context.turnOffAuthorisationSystem();
    }

    public void doDSpace()
            throws Exception
    {
        if (this.verbose)
        {
            System.out.println("Checking DSpace");
        }

        Community[] comms = Community.findAllTop(this.context);
        for (int i = 0; i < comms.length; i++)
        {
            this.doCommunity(comms[i]);
        }
    }

    public void doCommunity(String handle)
            throws Exception
    {
        DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
        if (!(dso instanceof Community))
        {
            throw new Exception(handle + " does not resolve to a Community");
        }
        this.doCommunity((Community) dso);
    }

    public void doCommunity(Community community)
            throws Exception
    {
        if (this.verbose)
        {
            System.out.println("Checking Community " + Integer.toString(community.getID()));
        }

        Community[] comms = community.getSubcommunities();
        for (int i = 0; i < comms.length; i++)
        {
            this.doCommunity(comms[i]);
        }

        Collection[] cols = community.getCollections();
        for (int i = 0; i < cols.length; i++)
        {
            this.doCollection(cols[i]);
        }
    }

    public void doCollection(String handle)
            throws SQLException, Exception
    {
        DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
        if (!(dso instanceof Collection))
        {
            throw new Exception(handle + " does not resolve to a Collection");
        }
        this.doCollection((Collection) dso);
    }

    public void doCollection(Collection collection)
            throws SQLException, Exception
    {
        if (this.verbose)
        {
            System.out.println("Checking Collection " + Integer.toString(collection.getID()));
        }

        // do all the items in the collection, withdrawn or not
        ItemIterator ii = collection.getAllItems();
        while (ii.hasNext())
        {
            Item item = ii.next();
            this.doItem(item);
        }

        // do all the items in the collection's workflow
        WorkflowItem[] wfis = WorkflowItem.findByCollection(this.context, collection);
        for (int i = 0; i < wfis.length; i++)
        {
            WorkflowItem wfi = wfis[i];
            Item item = wfi.getItem();
            this.doItem(item);
        }

        // do all the items in the collection's workspace
        WorkspaceItem[] wsis = WorkspaceItem.findByCollection(this.context, collection);
        for (int i = 0; i < wsis.length; i++)
        {
            WorkspaceItem wsi = wsis[i];
            Item item = wsi.getItem();
            this.doItem(item);
        }
    }

    public void doItem(String handle)
            throws SQLException, Exception
    {
        DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
        if (!(dso instanceof Item))
        {
            throw new Exception(handle + " does not resolve to an Item");
        }
        this.doItem((Item) dso);
    }

    public void doItem(Item item)
            throws SQLException, AuthorizeException, IOException, Exception
    {
        if (this.verbose)
        {
            System.out.println("Checking Item " + Integer.toString(item.getID()));
        }

        // first look in the ORIGINAL bundle
        this.checkBundle(item, "ORIGINAL");
        this.checkBundle(item, "LICENSE");
        this.checkBundle(item, "METADATA");
        this.checkBundle(item, "SWORD");
    }

    private void checkBundle(Item item, String name)
            throws Exception
    {
        Bundle[] bundles = item.getBundles(name);
        for (int i = 0; i < bundles.length; i++)
        {
            Bundle bundle = bundles[i];
            Bitstream[] bitstreams = bundle.getBitstreams();
            for (int j = 0; j < bitstreams.length; j++)
            {
                Bitstream bitstream = bitstreams[j];

                String bname = bitstream.getName();
                String bext = bname.substring(bname.lastIndexOf(".") + 1);

                BitstreamFormat format = bitstream.getFormat();
                String mime = format.getMIMEType();

                if (
                        (bext != null && NumberChecker.EXTENSIONS.contains(bext)) ||
                         (mime != null && NumberChecker.MIMETYPES.contains(mime))
                   )
                {
                    String hdl = item.getHandle();
                    if (hdl == null)
                    {
                        hdl = "no handle available";
                    }
                    try
                    {
                        if (this.checkBitstream(bitstream))
                        {
                            System.out.println("WARNING: item id " + Integer.toString(item.getID()) + " (" + hdl + "), bundle " + bundle.getName() + ", bitstream '" + bitstream.getName() + "' contains the text 'foedselsnummer'");
                        }
                    }
                    catch (FileNotFoundException fnf)
                    {
                        System.out.println("ERROR: item id " + Integer.toString(item.getID()) + " (" + hdl + "), bundle " + bundle.getName() + ", bitstream '" + bitstream.getName() + "' does not refer to a valid file on disk");
                    }
                }
            }
        }
    }

    private boolean checkBitstream(Bitstream bitstream)
            throws Exception
    {
        if (this.verbose)
        {
            System.out.println("Checking bitstream " + Integer.toString(bitstream.getID()) + " - " + bitstream.getName());
        }
        
        InputStream is = bitstream.retrieve();
        StringWriter writer = new StringWriter();
        IOUtils.copy(is, writer, "utf-8");
        String contents = writer.toString();
        int idx = contents.indexOf("foedselsnummer");
        return idx > -1;
    }
}
