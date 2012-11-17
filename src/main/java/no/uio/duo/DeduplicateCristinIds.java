package no.uio.duo;

import org.apache.log4j.Logger;
import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeduplicateCristinIds extends AbstractCurationTask
{
    /*
    select metadatavalue.item_id, metadatavalue.text_value
    from metadatafieldregistry
        join metadatavalue on metadatafieldregistry.metadata_field_id = metadatavalue.metadata_field_id
    where metadatafieldregistry.element = 'identifier' and metadatafieldregistry.qualifier = 'cristinID';
    */
    private static String query = "" +
            "SELECT metadatavalue.item_id AS item_id, metadatavalue.text_value AS text_value " +
            "FROM metadatafieldregistry " +
            "   JOIN metadatavalue ON metadatafieldregistry.metadata_field_id = metadatavalue.metadata_field_id " +
            "WHERE metadatafieldregistry.element = ? and metadatafieldregistry.qualifier = ?";

    // map to hold the item id to cristin id mapping
    private Map<String, List<Integer>> cristinIds = new HashMap<String, List<Integer>>();

    // dc value representing the cristing id field
    private DCValue dcv = null;

    // The status of the de-duplicating of this item
    private int status = Curator.CURATE_UNSET;

    private List<String> results = null;

    // The log4j logger for this class
    private static Logger log = Logger.getLogger(DeduplicateCristinIds.class);

    @Override
    public void init(Curator curator, String taskId) throws IOException
    {
        super.init(curator, taskId);

        // set the result list to an empty list, since it might have stuff left over from
        // the last run
        this.results = new ArrayList<String>();

        // populate the cristin id mapping, remembering that this sits in memory after
        // the first time it is run, so we must only add new things, not repeatedly add
        // the same data
        try
        {
            String cfg = ConfigurationManager.getProperty("cristin", "cristinid.field");
            this.dcv = new MetadataManager().makeDCValue(cfg, null);

            Object[] params = { this.dcv.element, this.dcv.qualifier };
            Context context = new Context();
            TableRowIterator tri = DatabaseManager.query(context, this.query, params);
            while (tri.hasNext())
            {
                TableRow row = tri.next();
                int iid = row.getIntColumn("item_id");
                String cid = row.getStringColumn("text_value");
                if (this.cristinIds.containsKey(cid))
                {
                    if (!this.cristinIds.get(cid).contains(iid))
                    {
                        this.cristinIds.get(cid).add(iid);
                    }
                }
                else
                {
                    List<Integer> iids = new ArrayList<Integer>();
                    iids.add(iid);
                    this.cristinIds.put(cid, iids);
                }
            }

            tri.close();
            context.abort();
        }
        catch (DuoException e)
        {
            throw new IOException(e);
        }
        catch (SQLException e)
        {
            throw new IOException(e);
        }
    }

    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        // Unless this is  an item, we'll skip this item
        status = Curator.CURATE_SKIP;
        if (!(dso instanceof Item))
        {
            return status;
        }

        Item item = (Item)dso;
        DCValue[] cids = item.getMetadata(this.dcv.schema, this.dcv.element, this.dcv.qualifier, Item.ANY);
        for (DCValue cristinid : cids)
        {
            // if the cristin id isn't registered for whatever reason, just carry on
            if (!this.cristinIds.containsKey(cristinid.value))
            {
                continue;
            }

            // if the list of ids in the cristin id registry is greater than one
            // then we have a duplicate
            if (this.cristinIds.get(cristinid.value).size() > 1)
            {
                StringBuilder itemList = new StringBuilder();
                for (int iid : this.cristinIds.get(cristinid.value))
                {
                    boolean first = true;
                    if (iid != item.getID())
                    {
                        if (!first)
                        {
                            itemList.append(",");
                        }
                        else
                        {
                            first = false;
                        }
                        itemList.append(" ").append(iid);
                    }
                }

                String reportable = "Item: " + getItemHandle(item) + "(id: " + item.getID() +
                            ") has a duplicate Cristin ID with the following items: " +
                            itemList.toString() + "\n";

                this.results.add(reportable);
                status = Curator.CURATE_FAIL;
            }
            else
            {
                status = Curator.CURATE_SUCCESS;
            }
        }

        // FIXME: this seems terribly inefficient, but seems to be the only way to go in a curation task
        StringBuilder out = new StringBuilder();
        for (String r : this.results)
        {
            out.append(r);
        }
        setResult(out.toString());
        report(out.toString());

        return status;
    }

    /**
     * Internal utitity method to get a description of the handle
     *
     * @param item The item to get a description of
     * @return The handle, or in workflow
     */
    private static String getItemHandle(Item item)
    {
        String handle = item.getHandle();
        return (handle != null) ? handle: " in workflow";
    }
}
