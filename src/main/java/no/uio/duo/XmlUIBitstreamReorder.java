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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Processing action to handle the change of bitstream orders in the
 * customisable workflow</p>
 *
 * <p>This presents to the user (via the related {@link XmlUIBitstreamReorderUI}) a page
 * offering javascript based re-sequencing of bitstreams, and the option to move bitstreams
 * between bundles, and to finally save the result.</p>
 *
 * <p><strong>Configuration</strong></p>
 *
 * <p>In the spring/api/workflow-actions.xml definition file add a new bean for this class:</p>
 *
 * <pre>
 *     &lt;bean id="bitstreamactionAPI" class="no.uio.duo.XmlUIBitstreamReorder" scope="prototype"/&gt;
 * </pre>
 *
 */
public class XmlUIBitstreamReorder extends ProcessingAction
{
    @Override
    public void activate(Context context, XmlWorkflowItem xmlWorkflowItem)
            throws SQLException, IOException, AuthorizeException, WorkflowException
    {
        // do nothing
    }

    /**
     * Execute a bitstream reorder
     *
     * @param context
     * @param wfi
     * @param step
     * @param request
     * @return
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     * @throws WorkflowException
     */
    @Override
    public ActionResult execute(Context context, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException, WorkflowException
    {
        String button = Util.getSubmitButton(request, "submit_leave");

        if ("submit_move".equals(button))
        {
            this.move(context, wfi, request);
            return new ActionResult(ActionResult.TYPE.TYPE_PAGE);
        }
        else if ("submit_update".equals(button))
        {
            this.reorder(context, wfi, request);
        }
        else if ("submit_update_finish".equals(button))
        {
            this.reorder(context, wfi, request);
            return new ActionResult(ActionResult.TYPE.TYPE_OUTCOME, ActionResult.OUTCOME_COMPLETE);
        }

        return new ActionResult(ActionResult.TYPE.TYPE_CANCEL);


    }

    private void move(Context context, XmlWorkflowItem wfi, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException
    {
        Map<Integer, Integer> moves = new HashMap<Integer, Integer>();
        Enumeration params = request.getParameterNames();
        while (params.hasMoreElements())
        {
            String key = (String) params.nextElement();
            if (key.startsWith("move_"))
            {
                String val = request.getParameter(key);
                if (!"-1".equals(val))
                {
                    int bsid = Integer.parseInt(key.substring("move_".length()));
                    int bundleid = Integer.parseInt(val);
                    moves.put(bsid, bundleid);
                }
            }
        }

        for (Integer bsid : moves.keySet())
        {
            Bitstream bitstream = Bitstream.find(context, bsid);
            Bundle target = Bundle.find(context, moves.get(bsid));

            Bundle[] existing = bitstream.getBundles();
            target.addBitstream(bitstream);
            target.update();
            for (Bundle b : existing)
            {
                b.removeBitstream(bitstream);
                b.update();
            }
        }
    }

    private void reorder(Context context, XmlWorkflowItem wfi, HttpServletRequest request)
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
