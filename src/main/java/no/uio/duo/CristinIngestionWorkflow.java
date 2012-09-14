package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Context;
import org.dspace.harvest.HarvestedItem;
import org.dspace.harvest.IngestionWorkflow;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowManager;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CristinIngestionWorkflow implements IngestionWorkflow
{
    private boolean allowUpdateBitstreams = true;

    /* Namespaces */
    public static final Namespace ATOM_NS =
        Namespace.getNamespace("atom", "http://www.w3.org/2005/Atom");
    private static final Namespace ORE_ATOM =
        Namespace.getNamespace("oreatom", "http://www.openarchives.org/ore/atom/");
    private static final Namespace ORE_NS =
        Namespace.getNamespace("ore", "http://www.openarchives.org/ore/terms/");
    private static final Namespace RDF_NS =
        Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
    private static final Namespace DCTERMS_NS =
        Namespace.getNamespace("dcterms", "http://purl.org/dc/terms/");
    private static final Namespace DS_NS =
        Namespace.getNamespace("ds","http://www.dspace.org/objectModel/");


    public Item preUpdate(Context context, Item item, Collection targetCollection, HarvestedItem harvestedItem, List<Element> descMD, Element oreREM)
        throws SQLException, IOException, AuthorizeException
    {
        boolean inarch = item.isArchived();
        boolean docsChanged = false;
        if (inarch)
        {
            // if the item is in the archive, check to see if we need to update
            // the bitstreams
            docsChanged = this.haveDocsChanged(item, oreREM);
            if (docsChanged)
            {
                // if we need to update the bitstreams, then create a new
                // workspace item and return it
                Item newItem = this.newItem(context, item, targetCollection);
                harvestedItem.setItemID(newItem.getID());
                harvestedItem.update();
                this.allowUpdateBitstreams = true; // be explicit
                return newItem;
            }
            else
            {
                // in this case we don't want to do anything on the bitstreams,
                // so mark the bitstream update rule false
                this.allowUpdateBitstreams = false;
                return item;
            }
        }
        else
        {
            // the item is in the workspace or the workflow, in which case we
            // update it in-place
            this.allowUpdateBitstreams = true; // be explicit
            return item;
        }
    }

    public void postUpdate(Context context, Item item)
            throws SQLException, IOException, AuthorizeException
    {
        if (this.isItemInWorkspace(context, item))
        {
            this.startWorkflow(context, item);
        }
        else if (this.isItemInWorkflow(context, item))
        {
            this.restartWorkflow(context, item);
        }
        // if the item is in the archive, leave it where it is
    }

    // move a newly created item into the workflow
    public Item postCreate(Context context, WorkspaceItem wsi, String handle)
            throws SQLException, IOException, AuthorizeException
    {
        // kick off the workflow
		this.startWorkflow(context, wsi);

        // return the item, as that's what's required
        return wsi.getItem();
    }

    public boolean updateBitstreams(Context context, Item item, HarvestedItem hi)
    {
        // we already know whether we allow the bitstreams to be updated
        return this.allowUpdateBitstreams;
    }

    private Item newItem(Context context, Item item, Collection targetCollection)
            throws SQLException, IOException, AuthorizeException
    {
        WorkspaceItem wi = WorkspaceItem.create(context, targetCollection, false);
    	return wi.getItem();
    }

    private boolean haveDocsChanged(Item item, Element oreREM)
            throws IOException, SQLException
    {
        /*
        A set of documents can be said to have changed if:
            a/	There are more files than before in the incoming list
            b/	There are fewer files than before in the incoming list
            c/	The set of files contain different checksums than before
            d/	The set of files are in a different order than before
	*/
        // NOTE: we do not get file checksums from the ORE interface, so it is currently
        // impossible to determine if the file list has changed

        Document doc = new Document();
        doc.addContent(oreREM.detach());

        // get a list of the aggregated resources in the ORIGINAL bundle
        List<Element> incomingBitstreams = this.listBitstreamsInBundle(doc, "ORIGINAL");
        List<Bitstream> existingBitstreams = this.getExistingBitstreams(item, "ORIGINAL");

        // a/	There are more files than before in the incoming list
        // remember that there will be a metadata bitstream which we don't care
        // about couting
        if (incomingBitstreams.size() > existingBitstreams.size())
        {
            return true;
        }

        // b/	There are fewer files than before in the incoming list
        // remember that there will be a metadata bitstream which we don't care
        // about couting
        if (incomingBitstreams.size() < existingBitstreams.size())
        {
            return true;
        }

        // c/	The set of files contain different checksums than before
        // FIXME: we can't do this yet
        if (this.checksumsChanged(incomingBitstreams, existingBitstreams))
        {
            return true;
        }

        // d/	The set of files are in a different order than before
        if (this.fileOrderChanged(incomingBitstreams, existingBitstreams))
        {
            return true;
        }

        return false;
    }

    private boolean fileOrderChanged(List<Element> incomingBitstreams, List<Bitstream> existingBitstreams)
    {
        TreeMap<Integer, String> rawIncomingSeq = new TreeMap<Integer, String>();
        TreeMap<Integer, String> rawExistingSeq = new TreeMap<Integer, String>();

        // calculate the match map for the incoming items
        // we need to remember that in the incoming item, the metadata bitstream
        // is in the ORIGINAL bundle, but not in the existing item, so when determining
        // the order of the incoming files, we need to close the gap left by the
        // metadata bitstream
        for (Element element : incomingBitstreams)
        {
            String url = element.getAttributeValue("href");
            String[] urlParts = url.split("/");
            String[] fileSeq = urlParts[urlParts.length - 1].split("\\?");
            if (fileSeq.length == 2)
            {
                String filename = null;
                try
                {
                    filename = URLDecoder.decode(fileSeq[0], "UTF-8");
                }
                catch (UnsupportedEncodingException e)
                {
                    // can't parse the filename for whatever reason, just go ahead
                    // and re-import
                    return false;
                }
                String[] seqBits = fileSeq[1].split("=");
                if (seqBits.length == 2)
                {
                    int seqNo = Integer.parseInt(seqBits[1].trim());
                    rawIncomingSeq.put(seqNo, filename);
                }
                else
                {
                    // if the sequence number is not well formed, then force a re-import
                    return true;
                }
            }
            else
            {
                // if we can't get a sequence number for a file, then the sequence is changed
                // by definition (this ought not to happen)
                return true;
            }
        }

        // normalise the map order
        TreeMap<Integer, String> incomingSeq = this.normaliseSeq(rawIncomingSeq);

        // calculate the match map for the existing bitstreams
        for (Bitstream bitstream : existingBitstreams)
        {
            rawExistingSeq.put(bitstream.getSequenceID(), bitstream.getName());
        }
        TreeMap<Integer, String> existingSeq = this.normaliseSeq(rawExistingSeq);

        // now do the comparison
        int highest = incomingSeq.lastKey() > existingSeq.lastKey() ? incomingSeq.lastKey() : existingSeq.lastKey();
        for (int i = 1; i <= highest; i++)
        {
            String in = incomingSeq.get(i);
            String ex = existingSeq.get(i);
            if (in == null || ex == null)
            {
                // if one is populated and the other not, then the file order has necessarily changed
                return true;
            }
            if (!in.equals(ex))
            {
                // if two that are populated are not the same, the file order is different
                return true;
            }
        }

        // if we don't trip the checks, then the file order is the same
        return false;
    }

    private TreeMap<Integer, String> normaliseSeq(TreeMap<Integer, String> seqMap)
    {
        // go through the keys in order, looking for the gap left by the metadata
        // bitstream
        TreeMap<Integer, String> normSeq = new TreeMap<Integer, String>();
        int seq = 1;
        for (Integer key : seqMap.keySet())
        {
            normSeq.put(seq, seqMap.get(key));
            seq++;
        }
        return normSeq;
    }

    private boolean checksumsChanged(List<Element> incomingBitstreams, List<Bitstream> existingBitstreams)
    {
        // FIXME: we don't have enough information to do this yet
        return false;
    }

    private List<Bitstream> getExistingBitstreams(Item item, String bundleName)
            throws SQLException
    {
        List<Bitstream> bss = new ArrayList<Bitstream>();
        Bundle[] bundles = item.getBundles(bundleName);
        for (Bundle bundle : bundles)
        {
            Bitstream[] bitstreams = bundle.getBitstreams();
            for (Bitstream bitstream : bitstreams)
            {
                bss.add(bitstream);
            }
        }
        return bss;
    }

    private List<Element> listBitstreamsInBundle(Document doc, String bundleName)
            throws IOException
    {
        // FIXME: we need to check whether this is a metadata bitstream, which
        // we must then skip
        List<Element> bitstreams = new ArrayList<Element>();
        List<Element> links = this.listBitstreams(doc);
        for (Element link : links)
        {
            String incomingBundle = this.getIncomingBundleName(doc, link);

            if (bundleName.equals(incomingBundle))
            {
                // this is a bitstream from the correct bundle
                // only register it if it is not a metadata bitstream
                if (!this.isMetadataBitstream(link.getAttributeValue("href")))
                {
                    bitstreams.add(link);
                }
            }
        }
        return bitstreams;
    }

    private boolean isMetadataBitstream(String url)
    {
        // https://w3utv-dspace01.uio.no/dspace/xmlui/bitstream/handle/123456789/982/cristin-12087.xml?sequence=2

        // FIXME: yeah yeah, this would look better with a regex
        String[] bits = url.split("\\?");
        String[] urlParts = bits[0].split("/");
        String filename = urlParts[urlParts.length - 1];

        if (filename.startsWith("cristin-") && filename.endsWith(".xml"))
        {
            return true;
        }
        return false;
    }

    private String getIncomingBundleName(Document doc, Element link)
            throws IOException
    {
        try
        {
            String href = link.getAttributeValue("href");
            XPath xpathDesc = XPath.newInstance("/atom:entry/oreatom:triples/rdf:Description[@rdf:about=\"" + href + "\"]");
            xpathDesc.addNamespace(ATOM_NS);
            xpathDesc.addNamespace(ORE_ATOM);
            xpathDesc.addNamespace(RDF_NS);
            List<Element> descs = xpathDesc.selectNodes(doc);
            for (Element desc : descs)
            {
                Element dcdesc = desc.getChild("description", DCTERMS_NS);
                return dcdesc.getText();
            }
        }
        catch (JDOMException e)
        {
            throw new IOException("JDOM exception occured while ingesting the ORE", e);
        }

        return null;
    }

    private List<Element> listBitstreams(Document doc)
            throws IOException
    {
        XPath xpathLinks;
        List<Element> aggregatedResources;
        // String entryId;
        try
        {
            xpathLinks = XPath.newInstance("/atom:entry/atom:link[@rel=\"" + ORE_NS.getURI() + "aggregates" + "\"]");
            xpathLinks.addNamespace(ATOM_NS);
            aggregatedResources = xpathLinks.selectNodes(doc);

            // xpathLinks = XPath.newInstance("/atom:entry/atom:link[@rel='alternate']/@href");
            // xpathLinks.addNamespace(ATOM_NS);
            // entryId = ((Attribute) xpathLinks.selectSingleNode(doc)).getValue();
        }
        catch (JDOMException e)
        {
            throw new IOException("JDOM exception occured while ingesting the ORE", e);
        }

        return aggregatedResources;
    }

    private void restartWorkflow(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
	{
		// stop the workflow
		this.stopWorkflow(context, item);

		// now start the workflow again
		this.startWorkflow(context, item);
	}

    private void startWorkflow(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        WorkspaceItem wsi = this.getWorkspaceItem(context, item);
        this.startWorkflow(context, wsi);
    }

    private void startWorkflow(Context context, WorkspaceItem wsi)
			throws SQLException, AuthorizeException, IOException
	{
		WorkflowManager.startWithoutNotify(context, wsi);
	}

	private void stopWorkflow(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
	{
        // find the item in the workflow if it exists
        WorkflowItem wfi = this.getWorkflowItem(context, item);

        // abort the workflow
        if (wfi != null)
        {
            WorkflowManager.abort(context, wfi, context.getCurrentUser());
        }
	}

    public boolean isItemInWorkflow(Context context, Item item)
			throws SQLException
	{
        String query = "SELECT workflow_id FROM workflowitem WHERE item_id = ?";
        Object[] params = { item.getID() };
        TableRowIterator tri = DatabaseManager.query(context, query, params);
        if (tri.hasNext())
        {
            tri.close();
            return true;
        }
        return false;
	}

	// FIXME: this may become useful when we have a proper treatment for licences
	public boolean isItemInWorkspace(Context context, Item item)
			throws SQLException
	{
        String query = "SELECT workspace_item_id FROM workspaceitem WHERE item_id = ?";
        Object[] params = { item.getID() };
        TableRowIterator tri = DatabaseManager.query(context, query, params);
        if (tri.hasNext())
        {
            tri.close();
            return true;
        }
        return false;
	}

	//////////////////////////////////////////////
	// item access methods
	//////////////////////////////////////////////

	public WorkflowItem getWorkflowItem(Context context, Item item)
			throws SQLException
	{
        String query = "SELECT workflow_id FROM workflowitem WHERE item_id = ?";
        Object[] params = { item.getID() };
        TableRowIterator tri = DatabaseManager.query(context, query, params);
        if (tri.hasNext())
        {
            TableRow row = tri.next();
            int wfid = row.getIntColumn("workflow_id");
            WorkflowItem wfi = WorkflowItem.find(context, wfid);
            tri.close();
            return wfi;
        }
        return null;
	}

	public WorkspaceItem getWorkspaceItem(Context context, Item item)
			throws SQLException
	{
        String query = "SELECT workspace_item_id FROM workspaceitem WHERE item_id = ?";
        Object[] params = { item.getID() };
        TableRowIterator tri = DatabaseManager.query(context, query, params);
        if (tri.hasNext())
        {
            TableRow row = tri.next();
            int wsid = row.getIntColumn("workspace_item_id");
            WorkspaceItem wsi = WorkspaceItem.find(context, wsid);
            tri.close();
            return wsi;
        }
        return null;
	}
}
