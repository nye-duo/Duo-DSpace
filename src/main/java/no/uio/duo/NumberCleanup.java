package no.uio.duo;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.xml.serialize.XMLSerializer;
import org.apache.xpath.XPathAPI;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.sql.SQLException;

public class NumberCleanup
{
    private Context context;

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

        CommandLine line = parser.parse(options, args);

        String scope = null;
        String handle = null;
        boolean doAll = false;

        NumberCleanup nc = new NumberCleanup();

        if (line.hasOption("s") && line.hasOption("h"))
        {
            scope = line.getOptionValue("s");
            handle = line.getOptionValue("h");
        }

        if (line.hasOption("d"))
        {
            doAll = true;
        }

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

    public NumberCleanup()
            throws SQLException
    {
        this.context = new Context();
        this.context.turnOffAuthorisationSystem();
    }

    public void doDSpace()
            throws Exception
    {
        System.out.println("Cleaning DSpace");

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
        System.out.println("Cleaning Community " + Integer.toString(community.getID()));

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
        System.out.println("Cleaning Collection " + Integer.toString(collection.getID()));

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
        System.out.println("Cleaning Item " + Integer.toString(item.getID()));

        // first remove the licence bundle
        Bundle[] license = item.getBundles("LICENSE");
        for (int i = 0; i < license.length; i++)
        {
            Bundle bundle = license[i];
            item.removeBundle(bundle);
        }

        // now remove the deposit.zip file from the sword bundle
        Bundle[] sword = item.getBundles("SWORD");
        for (int i = 0; i < sword.length; i++)
        {
            Bundle bundle = sword[i];
            Bitstream bs = bundle.getBitstreamByName("deposit.zip");
            if (bs != null)
            {
                bundle.removeBitstream(bs);
                bundle.update();

                if (bundle.getBitstreams().length == 0) {
                    item.removeBundle(bundle);
                }
            }
        }

        // now get at the metadata bitstream
        Bundle[] mdbs = item.getBundles("METADATA");
        for (int i = 0; i < mdbs.length; i++) {
            Bundle mdb = mdbs[i];
            Bitstream bs = mdb.getBitstreamByName("metadata.xml");
            if (bs != null) {
                InputStream newmd = this.cleanMetadata(bs);
                Bitstream nbs = mdb.createBitstream(newmd);

                nbs.setDescription(bs.getDescription());
                nbs.setName(bs.getName());
                nbs.setFormat(bs.getFormat());
                nbs.update();

                mdb.removeBitstream(bs);
                mdb.update();
            }
        }

        item.update();
        this.context.commit();
    }

    private InputStream cleanMetadata(Bitstream bitstream)
            throws ParserConfigurationException, IOException, SQLException, AuthorizeException, SAXException, TransformerException
    {
        // load the bitstream up as an xml document
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        org.w3c.dom.Document document = builder.parse(bitstream.retrieve());

        // get the node(s) containing the foedselsnummer
        NodeList nummers = XPathAPI.selectNodeList(document, "/metadata/foedselsnummer", document);

        for (int i = 0; i < nummers.getLength(); i++)
        {
            Node n = nummers.item(i);
            Node p = n.getParentNode();
            p.removeChild(n);
        }

        StringWriter writer = new StringWriter();
        XMLSerializer serializer = new XMLSerializer(writer, null);
        serializer.serialize(document);

        InputStream is = new ByteArrayInputStream(writer.toString().getBytes());
        return is;
    }
}
