package no.uio.duo;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.harvest.BundleVersioningStrategy;

/*
 * This versioning strategy does nothing, it simply returns leaving all
 * the bundles alone, as the versioning will be handled by the ORE
 * ingester
 */
public class CristinBundleVersioningStrategy implements BundleVersioningStrategy
{
    public void versionBundles(Context context, Item item)
    {
        // does nothing, the ORE ingestion for CRISTIN handles bitstream
        // versioning
        return;
    }
}
