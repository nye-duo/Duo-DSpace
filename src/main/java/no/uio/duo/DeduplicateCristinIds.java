package no.uio.duo;

import org.apache.log4j.Logger;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeduplicateCristinIds
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

    // dc value representing the cristin id field
    public DCValue dcv = null;

    // The log4j logger for this class
    private static Logger log = Logger.getLogger(DeduplicateCristinIds.class);

    public static void main(String[] args)
            throws Exception
    {
        DeduplicateCristinIds dedupe = new DeduplicateCristinIds();
        Map<String, List<Integer>> duplicates = dedupe.detect();
        String report = dedupe.reportOn(duplicates);
        System.out.println(report);
    }

    public String reportOn(Map<String, List<Integer>> duplicates)
            throws SQLException
    {
        Context context = new Context();
        StringBuilder sb = new StringBuilder();
        for (String cristinId : duplicates.keySet())
        {
            StringBuilder ib = new StringBuilder();
            boolean first = true;
            for (int iid : duplicates.get(cristinId))
            {
                String separator = first ? "" : ", ";
                first = false;
                Item item = Item.find(context, iid);
                ib.append(separator + getItemHandle(item) + " (id: " + item.getID() + ")");
            }
            sb.append("Cristin ID " + cristinId + " is shared by items: ");
            sb.append(ib);
            sb.append("\n");
        }
        context.abort();
        return sb.toString();
    }

    public Map<String, List<Integer>> detect()
            throws IOException, DuoException
    {
        this.getCristinIdMap();
        Map<String, List<Integer>> duplicates = this.extractDuplicates();
        return duplicates;
    }

    private Map<String, List<Integer>> extractDuplicates()
    {
        Map<String, List<Integer>> duplicates = new HashMap<String, List<Integer>>();
        for (String cristinId : this.cristinIds.keySet())
        {
            if (this.cristinIds.get(cristinId).size() > 1)
            {
                duplicates.put(cristinId, this.cristinIds.get(cristinId));
            }
        }
        return duplicates;
    }

    private void getCristinIdMap()
            throws IOException, DuoException
    {
        // just mine all of the cristin ids in the database, and record all the ids
        // of the items that have those ids.  This means by the end we will know
        // which cristin ids are associated with which item ids, and it iwll be easy to
        // test for and report on multiples
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
        catch (SQLException e)
        {
            throw new IOException(e);
        }
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
