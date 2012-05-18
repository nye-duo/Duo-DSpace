package no.uio.duo;

import org.apache.abdera.model.Element;
import org.apache.abdera.model.Entry;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.DCDate;
import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.sword2.DSpaceSwordException;
import org.dspace.sword2.DepositResult;
import org.dspace.sword2.SwordEntryIngester;
import org.dspace.sword2.VerboseDescription;
import org.swordapp.server.Deposit;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordEntry;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;
import org.swordapp.server.UriRegistry;

import javax.xml.namespace.QName;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * This takes the FS metadata as embedded in an atom entry and adds it to
 * an existing object
 */
public class FSEntryIngester implements SwordEntryIngester
{
    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        return this.ingest(context, deposit, dso, verboseDescription, null, false);
    }

    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription, DepositResult result, boolean replace)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        if (dso instanceof Collection)
        {
            return this.ingestToCollection(context, deposit, (Collection) dso, verboseDescription, result);
        }
        else if (dso instanceof Item)
        {
            return this.ingestToItem(context, deposit, (Item) dso, verboseDescription, result, replace);
        }
        return null;
    }

    public DepositResult ingestToItem(Context context, Deposit deposit, Item item, VerboseDescription verboseDescription, DepositResult result, boolean replace)
                throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        try
        {
            if (result == null)
            {
                result = new DepositResult();
            }
            result.setItem(item);

            // add the metadata to the item
            this.addMetadataToItem(deposit, item);

            // update the item metadata to inclue the current time as
            // the updated date
            this.setUpdatedDate(item, verboseDescription);

            // in order to write these changes, we need to bypass the
            // authorisation briefly, because although the user may be
            // able to add stuff to the repository, they may not have
            // WRITE permissions on the archive.
            boolean ignore = context.ignoreAuthorization();
            context.setIgnoreAuthorization(true);
            item.update();
            context.setIgnoreAuthorization(ignore);

            verboseDescription.append("Update successful");

            result.setItem(item);
            result.setTreatment(this.getTreatment());

            return result;
        }
        catch (SQLException e)
        {
            throw new DSpaceSwordException(e);
        }
        catch (AuthorizeException e)
        {
            throw new DSpaceSwordException(e);
        }
    }

    public DepositResult ingestToCollection(Context context, Deposit deposit, Collection collection, VerboseDescription verboseDescription, DepositResult result)
                throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        // entry documents cannot be used to deposit items afresh.
        throw new SwordError(UriRegistry.ERROR_METHOD_NOT_ALLOWED, "You are not allowed to create items with only at Atom Entry");
    }

    private void addMetadataToItem(Deposit deposit, Item item)
            throws DSpaceSwordException
    {
        SwordEntry se = deposit.getSwordEntry();
        if (se == null)
        {
            return;
        }

        // deal with the grade
        String gradeField = ConfigurationManager.getProperty("studentweb", "grade.field");
        if (gradeField == null || "".equals(gradeField))
        {
            throw new DSpaceSwordException("No configuration, or configuration is invalid for: studentweb:grade.field");
        }
        this.addFieldToItem(se.getEntry(), item, DuoConstants.GRADE_QNAME, gradeField);

        // deal with the embargo end date
        String embargoEndField = ConfigurationManager.getProperty("embargo.field.terms");
        if (embargoEndField == null || "".equals(embargoEndField))
        {
            throw new DSpaceSwordException("No configuration, or configuration is invalid for: embargo.field.lift");
        }
        this.addFieldToItem(se.getEntry(), item, DuoConstants.EMBARGO_END_DATE_QNAME, embargoEndField);

        // deal with the embargo type
        String embargoTypeField = ConfigurationManager.getProperty("studentweb", "embargo-type.field");
        if (embargoTypeField == null || "".equals(embargoTypeField))
        {
            throw new DSpaceSwordException("No configuration, or configuration is invalid for: embargo-type.field");
        }
        this.addFieldToItem(se.getEntry(), item, DuoConstants.EMBARGO_TYPE_QNAME, embargoTypeField);
    }

    private void addFieldToItem(Entry entry, Item item, QName qname, String field)
            throws DSpaceSwordException
    {
        List<Element> elements = entry.getExtensions(qname);
        if (elements.size() != 0)
        {
            Element element = elements.get(0);
            String text = element.getText();
            if (text != null)
            {
                DCValue dc = this.makeDCValue(field, null);
                item.clearMetadata(dc.schema, dc.element, dc.qualifier, Item.ANY);
                item.addMetadata(dc.schema, dc.element, dc.qualifier, null, text.trim());
            }
        }
    }

    /**
     * Add the current date to the item metadata.  This looks up
     * the field in which to store this metadata in the configuration
     * sword.updated.field
     *
     * @param item
     * @throws DSpaceSwordException
     */
    protected void setUpdatedDate(Item item, VerboseDescription verboseDescription)
            throws DSpaceSwordException
    {
        String field = ConfigurationManager.getProperty("swordv2-server", "updated.field");
        if (field == null || "".equals(field))
        {
            throw new DSpaceSwordException("No configuration, or configuration is invalid for: sword.updated.field");
        }

        DCValue dc = this.makeDCValue(field, null);
        item.clearMetadata(dc.schema, dc.element, dc.qualifier, Item.ANY);
        DCDate date = new DCDate(new Date());
        item.addMetadata(dc.schema, dc.element, dc.qualifier, null, date.toString());

        verboseDescription.append("Updated date added to response from item metadata where available");
    }

    private DCValue makeDCValue(String field, String value)
                throws DSpaceSwordException
    {
        DCValue dcv = new DCValue();
        String[] bits = field.split("\\.");
        if (bits.length < 2 || bits.length > 3)
        {
            throw new DSpaceSwordException("invalid DC value: " + field);
        }
        dcv.schema = bits[0];
        dcv.element = bits[1];
        if (bits.length == 3)
        {
            dcv.qualifier = bits[2];
        }
        dcv.value = value;
        return dcv;
    }

    /**
     * The human readable description of the treatment this ingester has
     * put the deposit through
     *
     * @return
     * @throws DSpaceSwordException
     */
    private String getTreatment() throws DSpaceSwordException
    {
        return "The grade and/or embargo date have been updated";
    }
}
