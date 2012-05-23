package no.uio.duo;

import no.uio.duo.bagit.BagIt;
import no.uio.duo.bagit.BaggedItem;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.IngestionCrosswalk;
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


public class FSBagItIngester extends AbstractSwordContentIngester
{
    @Override
    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        return this.ingest(context, deposit, dso, verboseDescription, null);
    }

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

    // supports replace only, is not additive
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
            // SECONDARY - for all Secondary files which have access privileges "open"
            // SECONDARY_RESTRICTED - for all Secondary files which have access privileges "closed"
            // METADATA - for the metadata.xml file
            // LICENSE - for the licence file (note the US spelling)
            //
            // we can prepare these bundles up-front
            Bundle original = this.getBundle(context, item, DuoConstants.ORIGINAL_BUNDLE);
            Bundle secondary = this.getBundle(context, item, DuoConstants.SECONDARY_BUNDLE);
            Bundle secondaryRestricted = this.getBundle(context, item, DuoConstants.SECONDARY_RESTRICTED_BUNDLE);
            Bundle metadata = this.getBundle(context, item, DuoConstants.METADATA_BUNDLE);
            Bundle license = this.getBundle(context, item, DuoConstants.LICENSE_BUNDLE);

            // empty all of the bundles (no versioning)
            this.emptyBundle(original);
            this.emptyBundle(secondary);
            this.emptyBundle(secondaryRestricted);
            this.emptyBundle(metadata);
            this.emptyBundle(license);

            // populate each bundle from the bag

            // FIXME: we need to ensure that the access privileges on each of the bundles is set correctly

            // first the ORIGINAL bundle
            TreeMap<Integer, BaggedItem> sequencedPrimaries = bag.getSequencedFinals();
            this.addInSequence(context, sequencedPrimaries, original, derivedResources);

            // next the SECONDARY bundle
            TreeMap<Integer, BaggedItem> sequencedOpenSecondaries = bag.getSequencedSecondaries(DuoConstants.OPEN);
            this.addInSequence(context, sequencedOpenSecondaries, secondary, derivedResources);

            // now the SECONDARY_RESTRICTED
            TreeMap<Integer, BaggedItem> sequencedClosedSecondaries = bag.getSequencedSecondaries(DuoConstants.CLOSED);
            this.addInSequence(context, sequencedClosedSecondaries, secondaryRestricted, derivedResources);

            // now the METADATA
            // Note: this deletes the old metadata file, as we only want one at any one time.
            BaggedItem metadataFile = bag.getMetadataFile();
            /* this is no longer necessary, as the bundle is emptied in advance
            Bitstream oldMetadata = metadata.getBitstreamByName(DuoConstants.METADATA_FILE);
            if (oldMetadata != null)
            {
                metadata.removeBitstream(oldMetadata);
            }*/
            Bitstream mdBs = this.writeToBundle(context, metadata, metadataFile);
            derivedResources.add(mdBs);

            // finally the LICENCE
            BaggedItem licenceFile = bag.getLicenceFile();
            Bitstream lbs = this.writeToBundle(context, license, licenceFile);
            derivedResources.add(lbs);

            // now we can crosswalk in the metadata
            if (deposit.isMetadataRelevant())
            {
                this.addMetadataFromBitstream(context, item, mdBs);
            }

            // FIXME: we may want to annotate the item's metadata with an identifier so that
            // it gets properly identified by the event consumer later on (or that might not
            // be necessary, since DUO has a security profile for bundles which we are
            // adhering to)

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

    private void addMetadataFromBitstream(Context context, Item item, Bitstream bitstream)
            throws AuthorizeException, IOException, SQLException, DSpaceSwordException
    {
        try
        {
            IngestionCrosswalk inxwalk = (IngestionCrosswalk) PluginManager.getNamedPlugin(IngestionCrosswalk.class, "FS");
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(bitstream.retrieve());
            Element element = document.getRootElement();
            inxwalk.ingest(context, item, element);
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

    private String getTreatment()
    {
        return "Document has been unpackaged, and metadata extracted";
    }
}
