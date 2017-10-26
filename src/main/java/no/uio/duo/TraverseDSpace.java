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

    protected boolean manageContext = false;
    protected String contextEntryPoint = null;

    protected int communityCount = 0;
    protected int collectionCount = 0;
    protected int workspaceCount = 0;
    protected int workflowCount = 0;
    protected int itemCount = 0;

    public TraverseDSpace(String epersonEmail)
            throws Exception
    {
        this(epersonEmail, false);
    }

    /**
     * Create an instance of the object, where the contex will be initialised around the eperson account provided
     * @param epersonEmail
     * @throws Exception
     */
    public TraverseDSpace(String epersonEmail, boolean manageContext)
            throws Exception
    {
        this.manageContext = manageContext;
        this.context = new Context();

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);
    }

    private void setContextEntryPoint(String entryPoint)
    {
        if (this.contextEntryPoint == null)
        {
            this.contextEntryPoint = entryPoint;
        }
    }

    private boolean contextManaged(String entryPoint)
    {
        return this.manageContext && entryPoint.equals(this.contextEntryPoint);
    }

    /**
     * Hit every object in the whole of DSpace
     * @throws Exception
     */
    public void doDSpace()
            throws Exception
    {
        String entryPoint = "DSpace";
        this.setContextEntryPoint(entryPoint);

        try
        {
            Community[] comms = Community.findAllTop(this.context);
            for (int i = 0; i < comms.length; i++)
            {
                this.doCommunity(comms[i]);
            }
        }
        catch (Exception e)
        {
            System.out.println(e.getClass().getName());
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
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
        String entryPoint = "CommunityHandle";
        this.setContextEntryPoint(entryPoint);

        try
        {
            DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
            if (!(dso instanceof Community))
            {
                throw new Exception(handle + " does not resolve to a Community");
            }
            this.doCommunity((Community) dso);
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
        }
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
        String entryPoint = "Community";
        this.setContextEntryPoint(entryPoint);

        try
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

            this.communityCount++;
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
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
        String entryPoint = "CollectionHandle";
        this.setContextEntryPoint(entryPoint);

        try
        {
            DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
            if (!(dso instanceof Collection))
            {
                throw new Exception(handle + " does not resolve to a Collection");
            }
            this.doCollection((Collection) dso);
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
        }
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
        String entryPoint = "Collection";
        this.setContextEntryPoint(entryPoint);

        try
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

            this.collectionCount++;
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
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
        String entryPoint = "ItemHandle";
        this.setContextEntryPoint(entryPoint);

        try
        {
            DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
            if (!(dso instanceof Item))
            {
                throw new Exception(handle + " does not resolve to an Item");
            }
            this.doItem((Item) dso);
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
        }
    }

    public void doItem(int id, String handle)
            throws SQLException, Exception
    {
        String entryPoint = "ItemIDHandle";
        this.setContextEntryPoint(entryPoint);

        try
        {
            if (id > -1)
            {
                Item item = Item.find(this.context, id);
                if (item != null)
                {
                    this.doItem(item);
                }
            }
            else if (handle != null)
            {
                this.doItem(handle);
            }
            else
            {
                throw new Exception("You must provide one of id or handle");
            }
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
        }
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
        String entryPoint = "WorkflowID";
        this.setContextEntryPoint(entryPoint);

        try
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

            this.workflowCount++;
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
        }
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
        String entryPoint = "WorkspaceID";
        this.setContextEntryPoint(entryPoint);

        try
        {
            WorkspaceItem wsi = WorkspaceItem.find(this.context, wsid);
            if (wsi == null)
            {
                throw new Exception(Integer.toString(wsid) + " does not resolve to a workspace item");
            }
            this.doItem(wsi.getItem());

            this.workspaceCount++;
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
        }
    }

    /**
     * Do item.
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
        String entryPoint = "WorkspaceID";
        this.setContextEntryPoint(entryPoint);

        try
        {
            this.processItem(item);
            this.itemCount++;
        }
        catch (Exception e)
        {
            if (this.contextManaged(entryPoint))
            {
                this.context.abort();
            }
            throw e;
        }
        finally
        {
            if (this.contextManaged(entryPoint))
            {
                if (this.context.isValid())
                {
                    this.context.complete();
                }
                this.contextEntryPoint = null;
            }
        }
    }

    public void report()
    {
        System.out.println("Processed " + this.communityCount + " Communities");
        System.out.println("Processed " + this.collectionCount + " Collections");
        System.out.println("Processed " + this.itemCount + " Items");
        System.out.println("\tof which " + this.workflowCount + " Workflow Items");
        System.out.println("\tof which " + this.workspaceCount + " Workspace Items");
    }

    protected abstract void processItem(Item item) throws SQLException, AuthorizeException, IOException, Exception;
}
