package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.handle.HandleManager;
import org.dspace.workflow.WorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Utility base class for scripts that want to be able to traverse the whole of DSpace, or parts of it.
 *
 * This class solves the issue of handling workflow and workspace items too.  Choose your entry point, and then
 * this class will iterate down from there, passing through each community/sub-community/collection/item/workflow item/workspace item
 *
 * Subclasses should at least implement doItem, but may override any of the other methods too.
 */
public abstract class TraverseDSpace
{
    protected Context context;
    protected EPerson eperson;

    /**
     * Create an instance of the object, where the contex will be initialised around the eperson account provided
     * @param epersonEmail
     * @throws Exception
     */
    public TraverseDSpace(String epersonEmail)
            throws Exception
    {
        this.context = new Context();

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);
    }

    /**
     * Hit every object in the whole of DSpace
     * @throws Exception
     */
    public void doDSpace()
            throws Exception
    {
        Community[] comms = Community.findAllTop(this.context);
        for (int i = 0; i < comms.length; i++)
        {
            this.doCommunity(comms[i]);
        }
    }

    public void doDSpaceManageContext()
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
    }

    /**
     * Do community and everything therein
     *
     * @param handle
     * @throws Exception
     */
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

    /**
     * Do community and everything therein
     *
     * @param community
     * @throws Exception
     */
    public void doCommunity(Community community)
            throws Exception
    {
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

    

    /**
     * Do collection and everything therein
     *
     * @param handle
     * @throws SQLException
     * @throws Exception
     */
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

    /**
     * Do collection and everything therein
     *
     * @param collection
     * @throws SQLException
     * @throws Exception
     */
    public void doCollection(Collection collection)
            throws SQLException, Exception
    {
        // do all the items in the collection, withdrawn or not
        ItemIterator ii = collection.getAllItems();
        while (ii.hasNext())
        {
            Item item = ii.next();
            this.doItem(item);
        }

        // do all the items in the collection's workflow (both normal and XML)
        WorkflowItem[] wfis = WorkflowItem.findByCollection(this.context, collection);
        for (int i = 0; i < wfis.length; i++)
        {
            WorkflowItem wfi = wfis[i];
            Item item = wfi.getItem();
            this.doItem(item);
        }

        XmlWorkflowItem[] xwfis = XmlWorkflowItem.findByCollection(this.context, collection);
        for (int i = 0; i < xwfis.length; i++)
        {
            XmlWorkflowItem xwfi = xwfis[i];
            Item item = xwfi.getItem();
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

    /**
     * Do item
     *
     * @param handle
     * @throws SQLException
     * @throws Exception
     */
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

    /**
     * Do workflow item
     *
     * @param wfid
     * @throws SQLException
     * @throws Exception
     */
    public void doWorkflowItem(int wfid)
            throws SQLException, Exception
    {
        InProgressSubmission wfi = null;
        wfi = WorkflowItem.find(this.context, wfid);
        if (wfi == null)
        {
            wfi = XmlWorkflowItem.find(this.context, wfid);
        }
        if (wfi == null)
        {
            throw new Exception(Integer.toString(wfid) + " does not resolve to a workflow item");
        }
        this.doItem(wfi.getItem());
    }

    /**
     * Do workspace item
     *
     * @param wsid
     * @throws SQLException
     * @throws Exception
     */
    public void doWorkspaceItem(int wsid)
            throws SQLException, Exception
    {
        WorkspaceItem wsi = WorkspaceItem.find(this.context, wsid);
        if (wsi == null)
        {
            throw new Exception(Integer.toString(wsid) + " does not resolve to a workspace item");
        }
        this.doItem(wsi.getItem());
    }

    /**
     * Do item.
     *
     * This is the one you probably want to override in your subclass
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
        // this is probably the one you want to override
    }
}
