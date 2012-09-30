package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.harvest.IngestFilter;
import org.jdom.Element;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class CristinIngestFilter implements IngestFilter
{
    public boolean acceptIngest(List<Element> elements, Element element)
            throws SQLException, IOException, AuthorizeException
    {
        // for the time being this lets everything through, but in the
        // future this is where we can plug in filtering features
        return true;
    }
}
