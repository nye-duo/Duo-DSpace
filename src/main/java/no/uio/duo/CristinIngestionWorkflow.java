package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Context;
import org.dspace.harvest.HarvestedItem;
import org.dspace.harvest.IngestionWorkflow;
import org.jdom.Element;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class CristinIngestionWorkflow implements IngestionWorkflow
{
    public Item preUpdate(Context context, Item item, HarvestedItem harvestedItem, List<Element> elements, Element element)
    {
        return null;
    }

    public void postUpdate(Context context, Item item)
    {

    }

    public Item postCreate(Context context, WorkspaceItem workspaceItem, String s)
            throws SQLException, IOException, AuthorizeException
    {
        return null;
    }
}
