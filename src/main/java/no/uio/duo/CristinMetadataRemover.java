package no.uio.duo;

import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.harvest.MetadataRemover;
import org.dspace.harvest.OAIHarvester;
import org.dspace.sword2.DSpaceSwordException;

import java.util.ArrayList;
import java.util.List;

public class CristinMetadataRemover implements MetadataRemover
{
    public void clearMetadata(Context context, Item item)
            throws OAIHarvester.HarvestingException
    {
        MetadataManager mdm = new MetadataManager();
        try
        {
            mdm.removeAuthorityMetadata(context, item, "cristin", "metadata.authority");
        }
        catch (DSpaceSwordException e)
        {
            throw new OAIHarvester.HarvestingException(e);
        }
    }
}
