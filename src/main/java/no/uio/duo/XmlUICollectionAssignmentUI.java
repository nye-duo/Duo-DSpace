package no.uio.duo;

import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.dspace.app.xmlui.aspect.xmlworkflow.AbstractXMLUIAction;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.Para;
import org.dspace.app.xmlui.wing.element.Select;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;

public class XmlUICollectionAssignmentUI extends AbstractXMLUIAction
{
    @Override
    public void addBody(Body body)
            throws SAXException, WingException, SQLException, IOException, AuthorizeException
    {
        Item item = workflowItem.getItem();
        Collection collection = workflowItem.getCollection();
        Request request = ObjectModelHelper.getRequest(objectModel);

        String actionURL = contextPath + "/handle/"+collection.getHandle() + "/xmlworkflow";

        Division div = body.addDivision("my-task");
        div.addPara("this would be your workflow stage");
    }
}
