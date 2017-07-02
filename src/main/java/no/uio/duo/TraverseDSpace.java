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

public abstract class TraverseDSpace
{
    protected Context context;
    protected EPerson eperson;

    public TraverseDSpace(String epersonEmail)
            throws Exception
    {
        this.context = new Context();

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);
    }

    public void doDSpace()
            throws Exception
    {
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

    public void doItem(Item item)
            throws SQLException, AuthorizeException, IOException, Exception
    {
        // this is probably the one you want to override
    }
}
