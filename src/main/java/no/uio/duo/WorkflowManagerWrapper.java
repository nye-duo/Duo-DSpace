package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;
import org.dspace.workflow.WorkflowItem;
import org.dspace.workflow.WorkflowManager;
import org.dspace.xmlworkflow.WorkflowConfigurationException;
import org.dspace.xmlworkflow.WorkflowException;
import org.dspace.xmlworkflow.XmlWorkflowManager;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;

public class WorkflowManagerWrapper
{
    // workflow manager methods that we want across both implementations
    ////////////////////////////////////////////////////////////////////

    public static void start(Context context, WorkspaceItem wsItem)
            throws SQLException, AuthorizeException, IOException, WorkflowException, WorkflowConfigurationException, MessagingException
    {
        if (ConfigurationManager.getProperty("workflow", "workflow.framework").equals("xmlworkflow"))
        {
            XmlWorkflowManager.start(context, wsItem);
        }
        else
        {
            WorkflowManager.start(context, wsItem);
        }
    }

    public static void startWithoutNotify(Context context, WorkspaceItem wsItem)
            throws SQLException, AuthorizeException, IOException, WorkflowException, WorkflowConfigurationException, MessagingException
    {
        if (ConfigurationManager.getProperty("workflow", "workflow.framework").equals("xmlworkflow"))
        {
            XmlWorkflowManager.startWithoutNotify(context, wsItem);
        }
        else
        {
            WorkflowManager.startWithoutNotify(context, wsItem);
        }
    }

    public static void abort(Context context, InProgressSubmission wfItem, EPerson ePerson)
            throws SQLException, AuthorizeException, IOException, WorkflowException, WorkflowConfigurationException, MessagingException
    {
        if (ConfigurationManager.getProperty("workflow", "workflow.framework").equals("xmlworkflow"))
        {
            XmlWorkflowManager.sendWorkflowItemBackSubmission(context, (XmlWorkflowItem) wfItem, ePerson, "", "");
        }
        else
        {
            WorkflowManager.abort(context, (WorkflowItem) wfItem, ePerson);
        }
    }

    // our own workflow control
    ///////////////////////////

    public static void restartWorkflow(Context context, Item item)
                throws SQLException, AuthorizeException, IOException
    {
        // stop the workflow
        WorkflowManagerWrapper.stopWorkflow(context, item);

        // now start the workflow again
        WorkflowManagerWrapper.startWorkflow(context, item);
    }

    public static void startWorkflow(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        WorkspaceItem wsi = WorkflowManagerWrapper.getWorkspaceItem(context, item);
        WorkflowManagerWrapper.startWorkflow(context, wsi);
    }

    public static void startWorkflow(Context context, WorkspaceItem wsi)
            throws SQLException, AuthorizeException, IOException
    {
        try
        {
            WorkflowManagerWrapper.startWithoutNotify(context, wsi);
        }
        catch (WorkflowException e)
        {
            throw new IOException(e);
        }
        catch (WorkflowConfigurationException e)
        {
            throw new IOException(e);
        }
        catch (MessagingException e)
        {
            throw new IOException(e);
        }
    }

    public static void stopWorkflow(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        try
        {
            // find the item in the workflow if it exists
            InProgressSubmission wfi = WorkflowManagerWrapper.getWorkflowItem(context, item);

            // abort the workflow
            if (wfi != null)
            {
                WorkflowManagerWrapper.abort(context, wfi, context.getCurrentUser());
            }
        }
        catch (WorkflowException e)
        {
            throw new IOException(e);
        }
        catch (WorkflowConfigurationException e)
        {
            throw new IOException(e);
        }
        catch (MessagingException e)
        {
            throw new IOException(e);
        }
    }

    //////////////////////////////////////////////
	// item access methods
	//////////////////////////////////////////////

    public static boolean isItemInWorkflow(Context context, Item item)
            throws SQLException
    {
        if (ConfigurationManager.getProperty("workflow", "workflow.framework").equals("xmlworkflow"))
        {
            return WorkflowManagerWrapper.isItemInXmlWorkflow(context, item);
        }
        else
        {
            return WorkflowManagerWrapper.isItemInOriginalWorkflow(context, item);
        }
    }

    public static boolean isItemInOriginalWorkflow(Context context, Item item)
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

    public static boolean isItemInXmlWorkflow(Context context, Item item)
            throws SQLException
    {
        String query = "SELECT workflowitem_id FROM cwf_workflowitem WHERE item_id = ?";
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
    public static boolean isItemInWorkspace(Context context, Item item)
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

    public static InProgressSubmission getWorkflowItem(Context context, Item item)
            throws SQLException, AuthorizeException, IOException
    {
        if (ConfigurationManager.getProperty("workflow", "workflow.framework").equals("xmlworkflow"))
        {
            return WorkflowManagerWrapper.getXmlWorkflowItem(context, item);
        }
        else
        {
            return WorkflowManagerWrapper.getOriginalWorkflowItem(context, item);
        }
    }

    public static XmlWorkflowItem getXmlWorkflowItem(Context context, Item item)
			throws SQLException, AuthorizeException, IOException
	{
        String query = "SELECT workflowitem_id FROM cwf_workflowitem WHERE item_id = ?";
        Object[] params = { item.getID() };
        TableRowIterator tri = DatabaseManager.query(context, query, params);
        if (tri.hasNext())
        {
            TableRow row = tri.next();
            int wfid = row.getIntColumn("workflow_id");
            XmlWorkflowItem.find(context, wfid);
            XmlWorkflowItem wfi = XmlWorkflowItem.find(context, wfid);
            tri.close();
            return wfi;
        }
        return null;
	}

	public static WorkflowItem getOriginalWorkflowItem(Context context, Item item)
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

	public static WorkspaceItem getWorkspaceItem(Context context, Item item)
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
