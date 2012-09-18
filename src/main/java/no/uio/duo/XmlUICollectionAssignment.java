package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.xmlworkflow.WorkflowException;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.state.actions.processingaction.ProcessingAction;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.SQLException;

public class XmlUICollectionAssignment extends ProcessingAction
{
    @Override
    public void activate(Context context, XmlWorkflowItem xmlWorkflowItem)
            throws SQLException, IOException, AuthorizeException, WorkflowException
    {
        // no need to do anything here
    }

    @Override
    public ActionResult execute(Context context, XmlWorkflowItem xmlWorkflowItem, Step step, HttpServletRequest httpServletRequest)
            throws SQLException, AuthorizeException, IOException, WorkflowException
    {
        // not sure what to do here yet!
        return null;
    }
}
