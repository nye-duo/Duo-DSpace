package no.uio.duo;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.harvest.MetadataRemover;
import org.dspace.harvest.OAIHarvester;
import org.dspace.sword2.DSpaceSwordException;

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
        catch (DuoException e)
        {
            throw new OAIHarvester.HarvestingException(e);
        }
    }
}
