package no.uio.duo;

import org.apache.abdera.Abdera;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Element;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.sword2.DSpaceSwordException;
import org.dspace.sword2.SimpleDCEntryDisseminator;
import org.dspace.sword2.SwordEntryDisseminator;
import org.swordapp.server.DepositReceipt;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;

import java.io.IOException;
import java.sql.SQLException;

public class DuoEntryDisseminator extends SimpleDCEntryDisseminator implements SwordEntryDisseminator
{
    public DepositReceipt disseminate(Context context, Item item, DepositReceipt depositReceipt)
            throws DSpaceSwordException, SwordError, SwordServerException
    {
        try
        {
            // first get a standard DC deposit entry
            DepositReceipt receipt = super.disseminate(context, item, depositReceipt);

            // now include the FS specific metadata
            Bundle[] bundles = item.getBundles(DuoConstants.METADATA_BUNDLE);

            for (Bundle bundle : bundles)
            {
                // get hold of the bitstream
                Bitstream bitstream = bundle.getBitstreamByName(DuoConstants.METADATA_FILE);

                // if there's no bitstream of that name, move on
                if (bitstream == null)
                {
                    continue;
                }

                // parse the bitstream into an abdera element
                Abdera ab = new Abdera();
                Document<Element> doc = ab.getParserFactory().getParser().parse(bitstream.retrieve());
                Element element = doc.getRoot();

                // add the element as an extension to the receipt.
                receipt.getWrappedEntry().addExtension(element);

                // just do the first one
                break;
            }

            return receipt;
        }
        catch (SQLException e)
        {
            throw new DSpaceSwordException(e);
        }
        catch (IOException e)
        {
            throw new DSpaceSwordException(e);
        }
        catch (AuthorizeException e)
        {
            throw new DSpaceSwordException(e);
        }
    }
}
