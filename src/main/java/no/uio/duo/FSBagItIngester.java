package no.uio.duo;

import no.uio.duo.bagit.BagIt;
import no.uio.duo.bagit.BaggedItem;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.IngestionCrosswalk;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.PluginManager;
import org.dspace.eperson.Group;
import org.dspace.sword2.AbstractSwordContentIngester;
import org.dspace.sword2.DSpaceSwordException;
import org.dspace.sword2.DepositResult;
import org.dspace.sword2.VerboseDescription;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.swordapp.server.Deposit;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * <p>Sword Content Ingester to deal with the BagIt format used by the StudentWeb/FS
 * integration</p>
 *
 * <p>This utilises the BagIt Library developed for the StudentWeb integration, to allow
 * us to unpack and allocate the bitstreams and metadata into the appropriate parts of the
 * DSpace item.  The structure of the DSpace item is as per the requirements documentation:</p>
 *
 * <ul>
 *     <li>Metadata file -&gt; DUO_ADMIN bundle</li>
 *     <li>Primary files -&gt; ORIGINAL bundle</li>
 *     <li>Secondary files (open access) -&gt; DUO_2NDRY_CLOSED bundle</li>
 *     <li>Secondary files (closed access) -&gt; DUO_2NDRY_CLOSED bundle</li>
 *     <li>Licence file -&gt; not imported</li>
 * </ul>
 *
 * <p>The importer also preserves the sequencing of bitstreams as indicated in the package</p>
 *
 * <p><strong>Configuration</strong></p>
 *
 * <p>Add this in the modules/swordv2-server.cfg file as a named plugin implementing the SwordContentIngester
 * interface.  It should be configured to respond to the package identifier for this format: http://duo.uio.no/terms/package/FSBagIt</p>
 *
 * <pre>
 * plugin.named.org.dspace.sword2.SwordContentIngester = \
    org.dspace.sword2.SimpleZipContentIngester = http://purl.org/net/sword/package/SimpleZip, \
    org.dspace.sword2.SwordMETSIngester = http://purl.org/net/sword/package/METSDSpaceSIP, \
    org.dspace.sword2.BinaryContentIngester = http://purl.org/net/sword/package/Binary, \
    no.uio.duo.FSBagItIngester = http://duo.uio.no/terms/package/FSBagIt
 * </pre>
 */
public class FSBagItIngester extends AbstractSwordContentIngester
{
    /**
     * ingest the given Deposit object into the given DSpace object (an Item or a Collection).  Verbose messages can be written
     * to the verboseDescription object
     *
     * @param context
     * @param deposit
     * @param dso
     * @param verboseDescription
     * @return
     * @throws DSpaceSwordException
     * @throws SwordError
     * @throws SwordAuthException
     * @throws SwordServerException
     */
    @Override
    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        return this.ingest(context, deposit, dso, verboseDescription, null);
    }

    /**
     * ingest the given Deposit object into the given DSpace object (an Item or a Collection).  Verbose messages can be written
     * to the verboseDescription object.  This method allows you to re-use an existing DepositResult object if one
     * is already in use (this could be because a metadata deposit has already taken place)
     *
     * @param context
     * @param deposit
     * @param dso
     * @param verboseDescription
     * @param result
     * @return
     * @throws DSpaceSwordException
     * @throws SwordError
     * @throws SwordAuthException
     * @throws SwordServerException
     */
    @Override
    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription, DepositResult result)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        if (dso instanceof Collection)
        {
            return this.ingestToCollection(context, deposit, (Collection) dso, verboseDescription, result);
        }
        else if (dso instanceof Item)
        {
            return this.ingestToItem(context, deposit, (Item) dso, verboseDescription, result);
        }
        return null;
    }

    /**
     * Ingest the given deposit into the provided collection.
     *
     * If the DepositResult parameter is not null, any Item contained therein will be used as the target
     * of the deposit.  Otherwise a new item will be created in the Collection.
     *
     * @param context
     * @param deposit
     * @param collection
     * @param verboseDescription
     * @param result
     * @return
     * @throws DSpaceSwordException
     * @throws SwordError
     * @throws SwordAuthException
     * @throws SwordServerException
     */
    public DepositResult ingestToCollection(Context context, Deposit deposit, Collection collection, VerboseDescription verboseDescription, DepositResult result)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        try
        {
            // decide whether we have a new item or an existing one
            Item item = null;
            WorkspaceItem wsi = null;
            if (result != null)
            {
                item = result.getItem();
            }
            else
            {
                result = new DepositResult();
            }
            if (item == null)
            {
                // use the item template, which is good practice
                wsi = WorkspaceItem.create(context, collection, true);
                item = wsi.getItem();
            }

            // at this point, one way or another we have an item to work with

            // we can use the item deposit mechanism now that we have a shell item to populate,
            // so all we need to do is jig the metadata relevant property to ensure that the
            // item ingest method does the metadata, and we are good to go
            deposit.setMetadataRelevant(true);
            result = this.ingestToItem(context, deposit, item, verboseDescription, result);

            // since this is a create, add the item id to the verbose description
            verboseDescription.append("Item created with internal identifier: " + item.getID());

            return result;
        }
        catch (AuthorizeException e)
        {
            throw new SwordAuthException(e);
        }
        catch (SQLException e)
        {
            throw new DSpaceSwordException(e);
        }
        catch (IOException e)
        {
            throw new DSpaceSwordException(e);
        }
    }

    /**
     * Replace the passed item with the content of the deposit.
     *
     * NOTE: there is no versioning here, or any clever merging, all of the existing bundles in the item will
     * be emptied and their contents replaced with the new deposit
     *
     * @param context
     * @param deposit
     * @param item
     * @param verboseDescription
     * @param result
     * @return
     * @throws DSpaceSwordException
     * @throws SwordError
     * @throws SwordAuthException
     * @throws SwordServerException
     */
    public DepositResult ingestToItem(Context context, Deposit deposit, Item item, VerboseDescription verboseDescription, DepositResult result)
    			throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        try
        {
            if (result == null)
            {
                result = new DepositResult();
            }

            // prepare a registry for all the bitstreams we derive from the bag
            List<Bitstream> derivedResources = new ArrayList<Bitstream>();

            // now, pass the file into the bag it library so we can start to access the content
            BagIt bag = new BagIt(deposit.getFile());

            // we are going to be unpacking the item to a variety of bundles:

            // ORIGINAL - for all Primary files
            // DUO_2NDRY_CLOSED - for all Secondary files which have access privileges "open"
            // DUO_ADMIN - for the metadata.xml file
            //
            // we can prepare these bundles up-front
            Bundle original = this.getBundle(context, item, DuoConstants.ORIGINAL_BUNDLE);
            Bundle secondary = this.getBundle(context, item, DuoConstants.SECONDARY_BUNDLE);
            Bundle admin = this.getBundle(context, item, DuoConstants.ADMIN_BUNDLE);

            // empty all of the bundles (no versioning)
            this.emptyBundle(original);
            this.emptyBundle(secondary);
            this.emptyBundle(admin);

            // populate each bundle from the bag

            // first the ORIGINAL bundle
            TreeMap<Integer, BaggedItem> sequencedPrimaries = bag.getSequencedFinals();
            this.addInSequence(context, sequencedPrimaries, original, derivedResources);

            // next the SECONDARY bundle
            TreeMap<Integer, BaggedItem> sequencedOpenSecondaries = bag.getSequencedSecondaries(DuoConstants.OPEN);
            this.addInSequence(context, sequencedOpenSecondaries, secondary, derivedResources);

            // now the restricted SECONDARY bitstreams
            TreeMap<Integer, BaggedItem> sequencedClosedSecondaries = bag.getSequencedSecondaries(DuoConstants.CLOSED);
            this.addInSequence(context, sequencedClosedSecondaries, secondary, derivedResources);

            // now the METADATA
            // Note: this deletes the old metadata file, as we only want one at any one time.
            BaggedItem metadataFile = bag.getMetadataFile();
            Bitstream mdBs = this.writeToBundle(context, admin, metadataFile);
            derivedResources.add(mdBs);

            // now we can crosswalk in the metadata
            if (deposit.isMetadataRelevant())
            {
                MetadataManager mdm = new MetadataManager();

                // remove the authority metadata
                mdm.removeAuthorityMetadata(context, item);

                // add the metadata from the metadata bitstream
                mdm.addMetadataFromBitstream(context, item, mdBs);
            }

            // finally write the item update
            boolean ignore = context.ignoreAuthorization();
            context.setIgnoreAuthorization(true);
            item.update();
            context.setIgnoreAuthorization(ignore);

            // now finish up and pass back
            verboseDescription.append("Ingest successful");

            result.setItem(item);
            result.setTreatment(this.getTreatment());
            result.setDerivedResources(derivedResources);

            return result;
        }
        catch (AuthorizeException e)
        {
            throw new SwordAuthException(e);
        }
        catch (SQLException e)
        {
            throw new DSpaceSwordException(e);
        }
        catch (IOException e)
        {
            throw new DSpaceSwordException(e);
        }
        catch (DuoException e)
        {
            throw new DSpaceSwordException(e);
        }
    }

    private void emptyBundle(Bundle bundle)
            throws AuthorizeException, SQLException, IOException
    {
        Bitstream[] bitstreams = bundle.getBitstreams();
        if (bitstreams != null)
        {
            for (Bitstream bs : bitstreams)
            {
                bundle.removeBitstream(bs);
            }
        }
    }

    private Bundle getBundle(Context context, Item item, String name)
            throws SQLException, AuthorizeException
    {
        Bundle[] bundles = item.getBundles(name);
        Bundle bundle = null;
        if (bundles.length > 0)
        {
            bundle = bundles[0];
        }
        else
        {
            bundle = item.createBundle(name);
        }

        return bundle;
    }

    private Bitstream writeToBundle(Context context, Bundle bundle, BaggedItem record)
                throws AuthorizeException, IOException, SQLException
    {
        Bitstream bitstream = bundle.createBitstream(record.getInputStream());
        bitstream.setName(record.getFilename());
        BitstreamFormat format = BitstreamFormat.findByMIMEType(context, record.getFormat());
        bitstream.setFormat(format);
        bitstream.update();
        return bitstream;
    }

    private void addInSequence(Context context, TreeMap<Integer, BaggedItem> seq, Bundle bundle, List<Bitstream> derivedResources)
            throws AuthorizeException, IOException, SQLException
    {
        for (Integer i : seq.keySet())
        {
            BaggedItem record = seq.get(i);
            Bitstream bitstream = this.writeToBundle(context, bundle, record);
            derivedResources.add(bitstream);
        }
    }

    private String getTreatment()
    {
        return "Document has been unpackaged, and metadata extracted";
    }

}
