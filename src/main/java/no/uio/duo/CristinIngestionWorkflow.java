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
        if (incomingBitstreams.size() > existingBitstreams.size())
        {
            return true;
        }

        // b/	There are fewer files than before in the incoming list
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
        TreeMap<Integer, String> incomingSeq = new TreeMap<Integer, String>();
        TreeMap<Integer, String> existingSeq = new TreeMap<Integer, String>();

        // calculate the match map for the incoming items
        for (Element element : incomingBitstreams)
        {
            String url = element.getAttribute("about", RDF_NS).getValue();
            String[] urlParts = url.split("/");
            String[] fileSeq = urlParts[urlParts.length - 1].split("\\?");
            if (fileSeq.length == 2)
            {
                String filename = fileSeq[0];
                String[] seqBits = fileSeq[1].split("=");
                if (seqBits.length == 2)
                {
                    int seqNo = Integer.parseInt(seqBits[1].trim());
                    incomingSeq.put(seqNo, filename);
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

        // calculate the match map for the existing bitstreams
        for (Bitstream bitstream : existingBitstreams)
        {
            existingSeq.put(bitstream.getSequenceID(), bitstream.getName());
        }

        // now do the comparison
        int highest = incomingSeq.lastKey() > existingSeq.lastKey() ? incomingSeq.lastKey() : existingSeq.lastKey();
        for (int i = 0; i < highest; i++)
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
        List<Element> bitstreams = new ArrayList<Element>();
        List<Element> elements = this.listBitstreams(doc);
        for (Element element : elements)
        {
            Element desc = element.getChild("description", DCTERMS_NS);
            if (bundleName.equals(desc.getText()))
            {
                // urls.add(element.getAttribute("about", RDF_NS).getValue());
                bitstreams.add(desc);
            }
        }
        return bitstreams;
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
        String query = "SELECT workspace_id FROM workspaceitem WHERE item_id = ?";
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
