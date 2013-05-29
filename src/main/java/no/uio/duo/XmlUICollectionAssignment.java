package no.uio.duo;

import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.xmlworkflow.WorkflowException;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.state.actions.processingaction.ProcessingAction;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * <p>Processing action to handle the assignment of an item to multiple collections</p>
 *
 * <p>This presents the user (via the related {@link XmlUICollectionAssignmentUI}) with a
 * full list of the collections in the reposiory, and options to check/un-check those
 * collections to which the item should be mapped.</p>
 *
 * <p><strong>Configuration</strong></p>
 *
 * <p>In the spring/api/workflow-actions.xml definition file add a new bean for this class:</p>
 *
 * <pre>
 *     &lt;bean id="assignmentactionAPI" class="no.uio.duo.XmlUICollectionAssignment" scope="prototype"/&gt;
 * </pre>
 */
public class XmlUICollectionAssignment extends ProcessingAction
{
    @Override
    public void activate(Context context, XmlWorkflowItem xmlWorkflowItem)
            throws SQLException, IOException, AuthorizeException, WorkflowException
    {
        // no need to do anything here
    }

    @Override
    public ActionResult execute(Context context, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException, WorkflowException
    {
        String button = Util.getSubmitButton(request, "submit_leave");

        if ("submit_save".equals(button))
        {
            this.save(context, wfi, request);
        }
        else if ("submit_finished".equals(button))
        {
            this.save(context, wfi, request);
            return new ActionResult(ActionResult.TYPE.TYPE_OUTCOME, ActionResult.OUTCOME_COMPLETE);
        }

        return new ActionResult(ActionResult.TYPE.TYPE_CANCEL);
    }

    private void save(Context context, XmlWorkflowItem wfi, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException
    {
        // first get a list of the ids to map to
        List<Integer> mapTo = new ArrayList<Integer>();

        Enumeration e = request.getParameterNames();
        while (e.hasMoreElements())
        {
            String key = (String) e.nextElement();
            if (key.startsWith("mapped_collection_"))
            {
                mapTo.add(Integer.parseInt(request.getParameter(key)));
            }
        }

        // now pass through the item and add it to/remove it from the relevant collections
        Item item = wfi.getItem();
        Collection[] collections = item.getCollections();
        Collection owner = wfi.getCollection();

        // remove it from existing collections (or record that the item is
        // already in a collection it is supposed to me in)
        List<Integer> alreadyIn = new ArrayList<Integer>();
        for (Collection col : collections)
        {
            // if the collection's id is not in the mapTo list and is not the owning
            // collection then delete the item from that collection
            if (!mapTo.contains(col.getID()) && col.getID() != owner.getID())
            {
                col.removeItem(item);
                col.update();
            }
            else if (mapTo.contains(col.getID()))
            {
                alreadyIn.add(col.getID());
            }
        }

        // add the item to all the necessary collections that it is not
        // already added to
        for (Integer colID : mapTo)
        {
            // if it is already in the desired collection, just carry on
            if (alreadyIn.contains(colID))
            {
                continue;
            }
            Collection col = Collection.find(context, colID);
            col.addItem(item);
            col.update();
        }
    }
}
