package no.uio.duo;

import org.dspace.app.util.Util;
import org.dspace.app.xmlui.aspect.administrative.FlowResult;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
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

public class XmlUIBitstreamReorder extends ProcessingAction
{
    @Override
    public void activate(Context context, XmlWorkflowItem xmlWorkflowItem)
            throws SQLException, IOException, AuthorizeException, WorkflowException
    {
        // do nothing
    }

    @Override
    public ActionResult execute(Context context, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException, WorkflowException
    {
        String button = Util.getSubmitButton(request, "submit_leave");

        if ("submit_update".equals(button))
        {
            this.save(context, wfi, request);
        }
        else if ("submit_update_finish".equals(button))
        {
            this.save(context, wfi, request);
            return new ActionResult(ActionResult.TYPE.TYPE_OUTCOME, ActionResult.OUTCOME_COMPLETE);
        }

        return new ActionResult(ActionResult.TYPE.TYPE_CANCEL);
    }

    private void save(Context context, XmlWorkflowItem wfi, HttpServletRequest request)
            throws SQLException, AuthorizeException
    {
        Item item = wfi.getItem();
        Bundle[] bundles = item.getBundles();
        for (Bundle bundle : bundles)
        {
            Bitstream[] bitstreams = bundle.getBitstreams();

            int[] newBitstreamOrder = new int[bitstreams.length];

            for (Bitstream bitstream : bitstreams)
            {
                //The order is determined by javascript
                //For each of our bitstream retrieve the order value
                int order = Util.getIntParameter(request, "order_" + bitstream.getID());
                //-1 the order since the order needed to start from one
                order--;
                //Place the bitstream identifier in the correct order
                newBitstreamOrder[order] = bitstream.getID();
            }

            if (newBitstreamOrder != null)
            {
                //Set the new order in our bundle !
                bundle.setOrder(newBitstreamOrder);
                bundle.update();
            }
        }
    }
}
