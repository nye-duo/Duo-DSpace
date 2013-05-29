package no.uio.duo;

import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.commons.lang.StringUtils;
import org.dspace.app.xmlui.aspect.xmlworkflow.AbstractXMLUIAction;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Button;
import org.dspace.app.xmlui.wing.element.Cell;
import org.dspace.app.xmlui.wing.element.CheckBox;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.Highlight;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Para;
import org.dspace.app.xmlui.wing.element.Row;
import org.dspace.app.xmlui.wing.element.Select;
import org.dspace.app.xmlui.wing.element.Table;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * XMLUI Action to provide a bitstream reorder UI
 *
 * <p><strong>Configuration</strong></p>
 *
 * <p>In the spring/xmlui/workflow-actions-xmlui.xml definitions file, add a new bean:</p>
 *
 * <pre>
 *     &lt;bean id="bitstreamaction_xmlui" class="no.uio.duo.XmlUIBitstreamReorderUI" scope="singleton"/&gt;
 * </pre>
 *
 */
public class XmlUIBitstreamReorderUI extends AbstractXMLUIAction
{
    /** Language strings */
    private static final Message T_submit_return = message("xmlui.general.return");
    private static final Message T_edit_bitstreams_head = message("xmlui.XMLWorkflow.cristin.XmlUIBitstreamsReorderUI.head");
    private static final Message T_column1 = message("xmlui.administrative.item.EditItemBitstreamsForm.column1");
    private static final Message T_column2 = message("xmlui.administrative.item.EditItemBitstreamsForm.column2");
    private static final Message T_column3 = message("xmlui.administrative.item.EditItemBitstreamsForm.column3");
    private static final Message T_column4 = message("xmlui.administrative.item.EditItemBitstreamsForm.column4");
    private static final Message T_column5 = message("xmlui.administrative.item.EditItemBitstreamsForm.column5");
    private static final Message T_column6 = message("xmlui.administrative.item.EditItemBitstreamsForm.column6");
    private static final Message T_column7 = message("xmlui.administrative.item.EditItemBitstreamsForm.column7");
    private static final Message T_bundle_label = message("xmlui.administrative.item.EditItemBitstreamsForm.bundle_label");
    private static final Message T_primary_label = message("xmlui.administrative.item.EditItemBitstreamsForm.primary_label");
    private static final Message T_view_link = message("xmlui.administrative.item.EditItemBitstreamsForm.view_link");
    private static final Message T_submit_reorder = message("xmlui.administrative.item.EditItemBitstreamsForm.submit_reorder");
    private static final Message T_order_up = message("xmlui.administrative.item.EditItemBitstreamsForm.order_up");
    private static final Message T_order_down = message("xmlui.administrative.item.EditItemBitstreamsForm.order_down");

    @Override
    public void addPageMeta(PageMeta pageMeta)
            throws SAXException, WingException, UIException, SQLException, IOException, AuthorizeException
    {
        super.addPageMeta(pageMeta);
        pageMeta.addMetadata("javascript", "static").addContent("loadJQuery.js");
        pageMeta.addMetadata("javascript", "static").addContent("static/js/bitstream-reorder-workflow.js");
    }

    @Override
    public void addBody(Body body)
            throws SAXException, WingException, SQLException, IOException, AuthorizeException
    {
        // Get our parameters and state
		Item item = workflowItem.getItem();
        Collection collection = workflowItem.getCollection();
		Request request = ObjectModelHelper.getRequest(objectModel);
        String actionURL = contextPath + "/handle/"+collection.getHandle() + "/xmlworkflow";

		// DIVISION: main div
		Division main = body.addInteractiveDivision("edit-item-status", actionURL, Division.METHOD_POST,"primary administrative item");
		main.setHead(T_edit_bitstreams_head);

        // add the item meta, and also the hidden field required to make the full item display work
        addWorkflowItemInformation(main, item, request);
        main.addHidden("workflowID").setValue(workflowItem.getID());
        main.addHidden("stepID").setValue(request.getParameter("stepID"));
        main.addHidden("actionID").setValue(request.getParameter("actionID"));

		// TABLE: Bitstream summary
		Table files = main.addTable("editItemBitstreams", 1, 1);
        files.setHead("Bitstreams");

		Row header = files.addRow(Row.ROLE_HEADER);
		header.addCellContent(T_column1);
		header.addCellContent(T_column2);
		header.addCellContent(T_column4);
		header.addCellContent(T_column5);
		header.addCellContent("Seq");
        header.addCellContent("Was");
		header.addCellContent(T_column7);

		Bundle[] bundles = item.getBundles();

		for (Bundle bundle : bundles)
		{

			Cell bundleCell = files.addRow("bundle_head_" + bundle.getID(), Row.ROLE_DATA, "").addCell(1, 5);
			bundleCell.addContent(T_bundle_label.parameterize(bundle.getName()));

			Bitstream[] bitstreams = bundle.getBitstreams();
            ArrayList<Integer> bitstreamIdOrder = new ArrayList<Integer>();
            for (Bitstream bitstream : bitstreams) {
                bitstreamIdOrder.add(bitstream.getID());
            }

            for (int bitstreamIndex = 0; bitstreamIndex < bitstreams.length; bitstreamIndex++) {
                Bitstream bitstream = bitstreams[bitstreamIndex];
                boolean primary = (bundle.getPrimaryBitstreamID() == bitstream.getID());
                String name = bitstream.getName();

                if (name != null && name.length() > 50) {
                    // If the fiel name is too long the shorten it so that it will display nicely.
                    String shortName = name.substring(0, 15);
                    shortName += " ... ";
                    shortName += name.substring(name.length() - 25, name.length());
                    name = shortName;
                }

                String description = bitstream.getDescription();
                String format = null;
                BitstreamFormat bitstreamFormat = bitstream.getFormat();
                if (bitstreamFormat != null) {
                    format = bitstreamFormat.getShortDescription();
                }
                String editURL = contextPath + "/admin/item?administrative-continue=" + knot.getId() + "&bitstreamID=" + bitstream.getID() + "&submit_edit";
                String viewURL = contextPath + "/bitstream/id/" + bitstream.getID() + "/" + bitstream.getName();


                Row row = files.addRow("bitstream_row_" + bitstream.getID(), Row.ROLE_DATA, "");
                Cell moveCell = row.addCell();
                Select sel = moveCell.addSelect("move_" + bitstream.getID());
                sel.addOption("-1", "Move to ...");
                for (Bundle b : bundles)
                {
                    if (b.getID() != bundle.getID())
                    {
                        sel.addOption(Integer.toString(b.getID()), b.getName());
                    }
                }
                moveCell.addButton("submit_move").setValue("Go");

                if (AuthorizeManager.authorizeActionBoolean(context, bitstream, Constants.WRITE)) {
                    // The user can edit the bitstream give them a link.
                    Cell cell = row.addCell();
                    cell.addXref(editURL, name);
                    if (primary) {
                        cell.addXref(editURL, T_primary_label);
                    }

                    row.addCell().addXref(editURL, format);
                } else {
                    // The user can't edit the bitstream just show them it.
                    Cell cell = row.addCell();
                    cell.addContent(name);
                    if (primary) {
                        cell.addContent(T_primary_label);
                    }

                    row.addCell().addContent(format);
                }

                Highlight highlight = row.addCell().addHighlight("fade");
                highlight.addContent("[");
                highlight.addXref(viewURL, T_view_link);
                highlight.addContent("]");

                if (AuthorizeManager.authorizeActionBoolean(context, bundle, Constants.WRITE)) {
                    // FIXME: this need working out ...
                    Cell cell = row.addCell("bitstream_order_" + bitstream.getID(), Cell.ROLE_DATA, "");
                    cell.addContent(String.valueOf(bitstreamIndex + 1));

                    cell = row.addCell("original_order_" + bitstream.getID(), Cell.ROLE_DATA, "");
                    cell.addContent(String.valueOf(bitstreamIndex + 1));

                    // <div>
                    // <span id="aspect.administrative.item.EditItemBitstreamsForm.field.order_17803_new">2</span> (Previous:2)</div>


                    cell = row.addCell("bitstream_reorder_" + bitstream.getID(), Cell.ROLE_DATA, "");
                    //Add the +1 to make it more human readable
                    cell.addHidden("order_" + bitstream.getID()).setValue(String.valueOf(bitstreamIndex + 1));
                    Button upButton = cell.addButton("submit_order_" + bundle.getID() + "_" + bitstream.getID() + "_up", ((bitstreamIndex == 0) ? "disabled" : "") + " icon-button arrowUp ");
                    if((bitstreamIndex == 0)){
                        upButton.setDisabled();
                    }
                    upButton.setValue(T_order_up);
                    upButton.setHelp(T_order_up);
                    Button downButton = cell.addButton("submit_order_" + bundle.getID() + "_" + bitstream.getID() + "_down", (bitstreamIndex == (bitstreams.length - 1) ? "disabled" : "") + " icon-button arrowDown ");
                    if(bitstreamIndex == (bitstreams.length - 1)){
                        downButton.setDisabled();
                    }
                    downButton.setValue(T_order_down);
                    downButton.setHelp(T_order_down);

                    //These values will only be used IF javascript is disabled or isn't working
                    cell.addHidden(bundle.getID() + "_" + bitstream.getID() + "_up_value").setValue(retrieveOrderUpButtonValue((java.util.List<Integer>) bitstreamIdOrder.clone(), bitstreamIndex));
                    cell.addHidden(bundle.getID() + "_" + bitstream.getID() + "_down_value").setValue(retrieveOrderDownButtonValue((java.util.List<Integer>) bitstreamIdOrder.clone(), bitstreamIndex));

                                    }else{
                    row.addCell().addContent(String.valueOf(bitstreamIndex));
                }
            }
		}

        Table table = main.addTable("workflow-actions", 1, 1);

        // finish the workflow stage
        Row row = table.addRow();
        row.addCellContent("Save the updates to bitstream ordering and finish the workflow stage");
        row.addCell().addButton("submit_update_finish").setValue("Update ordering and finish");

        // save the changes but don't finish
        row = table.addRow();
        row.addCellContent("Save the updates to bitstream ordering");
        row.addCell().addButton("submit_update").setValue("Update ordering");

        // Everyone can just cancel
        row = table.addRow();
        row.addCell(0, 2).addButton("submit_leave").setValue("Return without saving");

        main.addHidden("submission-continue").setValue(knot.getId());
    }

    private String retrieveOrderUpButtonValue(java.util.List<Integer> bitstreamIdOrder, int bitstreamIndex) {
        if(0 != bitstreamIndex){
            //We don't have the first button, so create a value where the current bitstreamId moves one up
            Integer temp = bitstreamIdOrder.get(bitstreamIndex);
            bitstreamIdOrder.set(bitstreamIndex, bitstreamIdOrder.get(bitstreamIndex - 1));
            bitstreamIdOrder.set(bitstreamIndex - 1, temp);
        }
        return StringUtils.join(bitstreamIdOrder.toArray(new Integer[bitstreamIdOrder.size()]), ",");
    }

    private String retrieveOrderDownButtonValue(java.util.List<Integer> bitstreamIdOrder, int bitstreamIndex) {
        if(bitstreamIndex < (bitstreamIdOrder.size()) -1){
            //We don't have the first button, so create a value where the current bitstreamId moves one up
            Integer temp = bitstreamIdOrder.get(bitstreamIndex);
            bitstreamIdOrder.set(bitstreamIndex, bitstreamIdOrder.get(bitstreamIndex + 1));
            bitstreamIdOrder.set(bitstreamIndex + 1, temp);
        }
        return StringUtils.join(bitstreamIdOrder.toArray(new Integer[bitstreamIdOrder.size()]), ",");
    }
}
