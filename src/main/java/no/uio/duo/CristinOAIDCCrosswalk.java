package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.IngestionCrosswalk;
import org.dspace.core.Context;
import org.jdom.Element;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class CristinOAIDCCrosswalk implements IngestionCrosswalk
{
    public void ingest(Context context, DSpaceObject dSpaceObject, List<Element> elements)
            throws CrosswalkException, IOException, SQLException, AuthorizeException
    {

    }

    public void ingest(Context context, DSpaceObject dSpaceObject, Element element)
            throws CrosswalkException, IOException, SQLException, AuthorizeException
    {
        
    }
}
