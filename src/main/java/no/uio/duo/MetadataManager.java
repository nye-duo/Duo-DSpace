package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.IngestionCrosswalk;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.PluginManager;
import org.dspace.sword2.DSpaceSwordException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.sql.SQLException;

public class MetadataManager
{
    public void addMetadataFromBitstream(Context context, Item item, Bitstream bitstream)
            throws AuthorizeException, IOException, SQLException, DSpaceSwordException
    {
        try
        {
            // prep up the ingestion kit
            IngestionCrosswalk inxwalk = (IngestionCrosswalk) PluginManager.getNamedPlugin(IngestionCrosswalk.class, "FS");
            if (inxwalk == null)
            {
                throw new DSpaceSwordException("No IngestionCrosswalk configured for FS");
            }
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(bitstream.retrieve());
            Element element = document.getRootElement();

            // before we do the ingest, we need to preserve some fields

            // get the fields which need special treatment
            String embargoEndMd = ConfigurationManager.getProperty("embargo.field.terms");
            String embargoTypeMd = ConfigurationManager.getProperty("studentweb", "embargo-type.field");
            String gradeMd = ConfigurationManager.getProperty("studentweb", "grade.field");

            DCValue[] embargoEnds = null;
            DCValue[] embargoType = null;
            DCValue[] grade = null;

            // take copies of and then remove these fields
            if (embargoEndMd != null && !"".equals(embargoEndMd))
            {
                embargoEnds = item.getMetadata(embargoEndMd);
                DCValue dcv = this.makeDCValue(embargoEndMd, null);
                item.clearMetadata(dcv.schema, dcv.element, dcv.qualifier, dcv.language);
            }
            if (embargoTypeMd != null && !"".equals(embargoTypeMd))
            {
                embargoType = item.getMetadata(embargoTypeMd);
                DCValue dcv = this.makeDCValue(embargoTypeMd, null);
                item.clearMetadata(dcv.schema, dcv.element, dcv.qualifier, dcv.language);
            }
            if (gradeMd != null && !"".equals(gradeMd))
            {
                grade = item.getMetadata(gradeMd);
                DCValue dcv = this.makeDCValue(gradeMd, null);
                item.clearMetadata(dcv.schema, dcv.element, dcv.qualifier, dcv.language);
            }

            // now we can do the ingest
            inxwalk.ingest(context, item, element);

            // now check to see if any of the above fields have been replaced.  If they have
            // not, then write the old value back in again

            if ((embargoEndMd != null && item.getMetadata(embargoEndMd).length == 0) && (embargoEnds != null && embargoEnds.length > 0))
            {
                item.addMetadata(embargoEnds[0].schema, embargoEnds[0].element, embargoEnds[0].qualifier, embargoEnds[0].language, embargoEnds[0].value);
            }
            if ((embargoTypeMd != null && item.getMetadata(embargoTypeMd).length == 0) && (embargoType != null && embargoType.length > 0))
            {
                item.addMetadata(embargoType[0].schema, embargoType[0].element, embargoType[0].qualifier, embargoType[0].language, embargoType[0].value);
            }
            if ((gradeMd != null && item.getMetadata(gradeMd).length == 0) && (grade != null && grade.length > 0))
            {
                item.addMetadata(grade[0].schema, grade[0].element, grade[0].qualifier, grade[0].language, grade[0].value);
            }

            // finally, write the changes
            item.update();
        }
        catch (JDOMException e)
        {
            throw new DSpaceSwordException(e);
        }
        catch (CrosswalkException e)
        {
            throw new DSpaceSwordException(e);
        }
    }

    public void removeAuthorityMetadata(Context context, Item item)
            throws DSpaceSwordException
    {
        this.removeAuthorityMetadata(context, item, "swordv2-server", "metadata.replaceable");
    }

    public void removeAuthorityMetadata(Context context, Item item, String module, String config)
            throws DSpaceSwordException
    {
        String raw = ConfigurationManager.getProperty(module, config);
        if (raw == null || "".equals(raw))
        {
            return;
        }
        String[] parts = raw.split(",");
        for (String part : parts)
        {
            DCValue dcv = this.makeDCValue(part.trim(), null);
            item.clearMetadata(dcv.schema, dcv.element, dcv.qualifier, Item.ANY);
        }
    }

    public DCValue makeDCValue(String field, String value)
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
}
