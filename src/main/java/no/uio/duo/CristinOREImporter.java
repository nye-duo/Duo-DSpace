package no.uio.duo;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.FormatIdentifier;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.CrosswalkObjectNotSupported;
import org.dspace.content.crosswalk.IngestionCrosswalk;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.PluginManager;
import org.dspace.harvest.OAIConfigurableCrosswalk;
import org.dspace.sword2.DSpaceSwordException;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * <p>Importer which implements both the standard DSpace IngestionCrosswalk and the
 * more advanced OAIConfigurableCrosswalk.  This crosswalk is first configured with
 * parameters from the OAI harvester, and then run over the item retrieved from
 * the OAI-PMH feed</p>
 *
 * <p>This is effectively a clone with extensions of the standard DSpace ORE Importer
 * class, with specific features to handle items coming from Cristin.  It will ingest
 * all of the referenced bitstreams, and then identify the metadata bitstream (of the
 * form cristin-nnnnn.xml) and apply the configured crosswalk to it.</p>
 *
 * <p><strong>Configuration</strong></p>
 *
 * <p>Configure this in dspace.cfg as a named plugin implementation of the IngestionCrosswalk
 * interface, with the name "cristin_ore":</p>
 *
 * <pre>
 * plugin.named.org.dspace.content.crosswalk.IngestionCrosswalk = \
     org.dspace.content.crosswalk.AIPDIMCrosswalkx = DIM, \
     org.dspace.content.crosswalk.AIPTechMDCrosswalk = AIP-TECHMD, \
     org.dspace.content.crosswalk.PREMISCrosswalk = PREMIS, \
     org.dspace.content.crosswalk.OREIngestionCrosswalk = ore, \
     org.dspace.content.crosswalk.NullIngestionCrosswalk = NIL, \
     org.dspace.content.crosswalk.OAIDCIngestionCrosswalk = dc, \
     org.dspace.content.crosswalk.DIMIngestionCrosswalk = dim, \
     org.dspace.content.crosswalk.METSRightsCrosswalk = METSRIGHTS, \
     org.dspace.content.crosswalk.RoleCrosswalk = DSPACE-ROLES, \
     no.uio.duo.CristinOAIDCCrosswalk = cristin_dc, \
     no.uio.duo.CristinOREImporter = cristin_ore
 * </pre>
 */
public class CristinOREImporter implements IngestionCrosswalk, OAIConfigurableCrosswalk
{
    /** log4j category */
    private static Logger log = Logger.getLogger(CristinOREImporter.class);

    /* Namespaces */
    public static final Namespace ATOM_NS =
        Namespace.getNamespace("atom", "http://www.w3.org/2005/Atom");
    private static final Namespace ORE_ATOM =
        Namespace.getNamespace("oreatom", "http://www.openarchives.org/ore/atom/");
    private static final Namespace ORE_NS =
        Namespace.getNamespace("ore", "http://www.openarchives.org/ore/terms/");
    private static final Namespace RDF_NS =
        Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
    private static final Namespace DCTERMS_NS =
        Namespace.getNamespace("dcterms", "http://purl.org/dc/terms/");
    private static final Namespace DS_NS =
    	Namespace.getNamespace("ds","http://www.dspace.org/objectModel/");

    private boolean updateBitstreams = true;

    /**
     * Configure this crosswalk with the properties from the OAI-PMH harvester
     *
     * This looks for a property called "update_bitstreams" and uses that to determine
     * during the actual ingest whether to deal with the item's bitstreams as well as
     * its metadata
     *
     * @param props
     */
    public void configure(Properties props)
    {
        this.updateBitstreams = (Boolean) props.get("update_bitstreams");
    }

    /**
     * Ingest the metadata in the element list into the DSpace item
     *
     * @param context
     * @param dso
     * @param metadata
     * @throws CrosswalkException
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
	public void ingest(Context context, DSpaceObject dso, List<Element> metadata)
            throws CrosswalkException, IOException, SQLException, AuthorizeException
    {

		// If this list contains only the root already, just pass it on
        if (metadata.size() == 1)
        {
			ingest(context, dso, metadata.get(0));
		}
		// Otherwise, wrap them up
		else
        {
			Element wrapper = new Element("wrap", metadata.get(0).getNamespace());
			wrapper.addContent(metadata);

			ingest(context, dso, wrapper);
		}
	}

    /**
     * Ingest the metadata held in the root element into the DSpace object
     *
     * @param context
     * @param dso
     * @param root
     * @throws CrosswalkException
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
	public void ingest(Context context, DSpaceObject dso, Element root)
            throws CrosswalkException, IOException, SQLException, AuthorizeException
    {
		Date timeStart = new Date();

		if (dso.getType() != Constants.ITEM)
        {
            throw new CrosswalkObjectNotSupported("CristinOREImporter can only crosswalk an Item.");
        }
        Item item = (Item)dso;

        if (root == null)
        {
        	System.err.println("The element received by ingest was null");
        	return;
        }

        Document doc = new Document();
        doc.addContent(root.detach());

        // now, ingest the bitstreams (if necessary - this method will decide based on the config)
        Bitstream metadataBitstream = this.ingestBitstreams(context, doc, item);

        // update the metadata from the metadata bundle
        this.addMetadataFromBitstream(context, item, metadataBitstream);
        
        log.info("CristinOREImporter for Item "+ item.getID() + " took: " + (new Date().getTime() - timeStart.getTime()) + "ms.");
	}

    private void backupAndRemove(Item item, List<Bitstream> bitstreams)
            throws SQLException, AuthorizeException, IOException
    {
        // get the backup bundle
        Bundle deleted = this.getDeletedBundle(item);

        for (Bitstream bitstream : bitstreams)
        {
            this.backupAndRemove(item, bitstream, deleted);

        }
    }

    private void backupAndRemove(Item item, Bitstream bitstream)
            throws SQLException, AuthorizeException, IOException
    {
        // get the backup bundle
        Bundle deleted = this.getDeletedBundle(item);

        // do the backup
        this.backupAndRemove(item, bitstream, deleted);
    }

    private void backupAndRemove(Item item, Bitstream bitstream, Bundle backupBundle)
            throws SQLException, AuthorizeException, IOException
    {
        Bundle[] currentBundles = bitstream.getBundles();
        backupBundle.addBitstream(bitstream);

        for (Bundle current : currentBundles)
        {
            current.removeBitstream(bitstream);
        }
    }

    private void backupAndRemove(Item item, Bundle sourceBundle, IncomingBitstream ib)
            throws SQLException, AuthorizeException, IOException
    {
        Bitstream[] bitstreams = sourceBundle.getBitstreams();
        FileManager fm = new FileManager();
        Bitstream bs = fm.findBitstream(ib, bitstreams);
        Bundle deleted = this.getDeletedBundle(item);
        this.backupAndRemove(item, bs, deleted);
    }

    private Bundle getDeletedBundle(Item item)
            throws SQLException, AuthorizeException, IOException
    {
        Bundle deleted = null;
        Bundle[] deleteds = item.getBundles("DELETED");
        if (deleteds.length > 0)
        {
            deleted = deleteds[0];
        }
        else
        {
            deleted = item.createBundle("DELETED");
        }
        return deleted;
    }

    private Bitstream ingestBitstreams(Context context, Document doc, Item item)
            throws CrosswalkException, IOException, SQLException, AuthorizeException
    {
        // get a list of the aggregated resources in the ORIGINAL bundle
        FileManager fm = new FileManager();
        List<IncomingBitstream> incomingBitstreams = fm.listBitstreamsInBundle(doc, "ORIGINAL", false);
        List<Bitstream> originalBitstreams = fm.getExistingBitstreams(item, "ORIGINAL");
        List<Bitstream> metadataBitstreams = fm.getExistingBitstreams(item, "METADATA");

        // backup the existing bitstreams
        // this.backupAndRemove(item, originalBitstreams);
        // this.backupAndRemove(item, metadataBitstreams);

        Bitstream metadataBitstream = null;

        for (IncomingBitstream ib : incomingBitstreams)
        {
        	String href = ib.getUrl();
        	log.debug("Cristin ORE processing: " + href);

            boolean isMdBs = fm.isMetadataBitstream(href);
            String bundleName = isMdBs ? "METADATA" : "ORIGINAL";
        	log.debug("Setting bundle name to: " + bundleName);

            // now, determine if this is a non-metadata bitstream, and if we are allowed
            // to import them
            if (!isMdBs && !this.updateBitstreams)
            {
                continue;
            }

            // if we get to here then we want to update the bitstream

            // select the potential target bundle
        	// Bundle names are not unique, so we just pick the first one if there's more than one.
            Bundle targetBundle = this.getTargetBundle(item, bundleName);

            // now ingest based on the following rules:
            //
            // 0 - we always ingest the metadata bitstream
            // 1 - does the bitstream already exist - and is the same - in the item?  If so, don't update it
            // 2 - if the bitstream already exists, but has changed, replace it
            // 3 - if the bitstream does not exist, create it
            // 4 - if there is not a version of an existing bitstream in the incoming bitstreams, delete it
            // 5 - ensure that the resulting order of the bitstreams is commensurate with the incoming bitstreams

            // 0 - we always ingest the metadata bitstream
            if (isMdBs)
            {
                this.backupAndRemove(item, metadataBitstreams);
                metadataBitstream = fm.ingestBitstream(context, href, ib.getName(), ib.getMimetype(), targetBundle);
            }

            // 1 - does the bitstream already exist - and is the same - in the item?  If so, don't update it
            else if (fm.bitstreamInstanceAlreadyExists(ib, originalBitstreams))
            {
                continue;
            }

            // 2 - if the bitstream already exists, but has changed, replace it
            else if (fm.bitstreamNameAlreadyExists(ib, originalBitstreams))
            {
                this.backupAndRemove(item, targetBundle, ib);
                fm.ingestBitstream(context, href, ib.getName(), ib.getMimetype(), targetBundle);
            }

            // 3 - if the bitstream does not exist, create it
            else if (fm.isNewBitstream(ib, originalBitstreams))
            {
                fm.ingestBitstream(context, href, ib.getName(), ib.getMimetype(), targetBundle);
            }
        }

        // 4 - if there is not a version of an existing bitstream in the incoming bitstreams, delete it
        for (Bitstream bs : originalBitstreams)
        {
            if (!fm.bitstreamIsIncoming(bs, incomingBitstreams))
            {
                this.backupAndRemove(item, bs);
            }
        }

        // 5 - ensure that the resulting order of the bitstreams is commensurate with the incoming bitstreams
        fm.sequenceBitstreams(item, "ORIGINAL", incomingBitstreams);

        return metadataBitstream;
    }

    private Bundle getTargetBundle(Item item, String bundleName)
            throws SQLException, AuthorizeException
    {
        Bundle[] targetBundles = item.getBundles(bundleName);
        Bundle targetBundle;

        // if null, create the new bundle and add it in
        if (targetBundles.length == 0)
        {
            targetBundle = item.createBundle(bundleName);
            item.addBundle(targetBundle);
        }
        else
        {
            targetBundle = targetBundles[0];
        }
        return targetBundle;
    }

	/**
     * Helper method to escape all chaacters that are not part of the canon set
     * @param sourceString source unescaped string
     */
    private String encodeForURL(String sourceString) {
    	Character lowalpha[] = {'a' , 'b' , 'c' , 'd' , 'e' , 'f' , 'g' , 'h' , 'i' ,
				'j' , 'k' , 'l' , 'm' , 'n' , 'o' , 'p' , 'q' , 'r' ,
				's' , 't' , 'u' , 'v' , 'w' , 'x' , 'y' , 'z'};
		Character upalpha[] = {'A' , 'B' , 'C' , 'D' , 'E' , 'F' , 'G' , 'H' , 'I' ,
                'J' , 'K' , 'L' , 'M' , 'N' , 'O' , 'P' , 'Q' , 'R' ,
                'S' , 'T' , 'U' , 'V' , 'W' , 'X' , 'Y' , 'Z'};
		Character digit[] = {'0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' , '8' , '9'};
		Character mark[] = {'-' , '_' , '.' , '!' , '~' , '*' , '\'' , '(' , ')'};

		// reserved
		Character reserved[] = {';' , '/' , '?' , ':' , '@' , '&' , '=' , '+' , '$' , ',' ,'%', '#'};

		Set<Character> URLcharsSet = new HashSet<Character>();
		URLcharsSet.addAll(Arrays.asList(lowalpha));
		URLcharsSet.addAll(Arrays.asList(upalpha));
		URLcharsSet.addAll(Arrays.asList(digit));
		URLcharsSet.addAll(Arrays.asList(mark));
		URLcharsSet.addAll(Arrays.asList(reserved));

        StringBuilder processedString = new StringBuilder();
		for (int i=0; i<sourceString.length(); i++) {
			char ch = sourceString.charAt(i);
			if (URLcharsSet.contains(ch)) {
				processedString.append(ch);
			}
			else {
				processedString.append("%").append(Integer.toHexString((int)ch));
			}
		}

		return processedString.toString();
    }

    private String getType(Document document)
            throws JDOMException
    {
        Properties props = ConfigurationManager.getProperties("cristin");
        for (Object key : props.keySet())
        {
            if (((String) key).startsWith("xpath"))
            {
                String xp = (String) props.get(key);
                XPath xpath = XPath.newInstance(xp);
                List result = xpath.selectNodes(document);
                if (result.size() > 0)
                {
                    return ((String) key).substring("xpath.".length());
                }
            }
        }
        return null;
    }

    /**
     * Update the item's metadata from the provided bitstream.  The bitstream should be
     * the Cristin metadata file.
     *
     * @param context
     * @param item
     * @param bitstream
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     * @throws CrosswalkException
     */
    public void addMetadataFromBitstream(Context context, Item item, Bitstream bitstream)
            throws AuthorizeException, IOException, SQLException, CrosswalkException
    {
        try
        {
            // load the document into an Element
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(bitstream.retrieve());
            Element element = document.getRootElement();

            // we have to determine the publication type in order to choose the crosswalk
            String typeConfig = this.getType(document);
            if (typeConfig == null)
            {
                return;
            }

            // prep up the ingestion kit
            IngestionCrosswalk inxwalk = (IngestionCrosswalk) PluginManager.getNamedPlugin(IngestionCrosswalk.class, typeConfig);
            if (inxwalk == null)
            {
                throw new CrosswalkException("No IngestionCrosswalk configured for " + typeConfig);
            }
            
            // now we can do the ingest
            inxwalk.ingest(context, item, element);

            // finally, write the changes
            item.update();
        }
        catch (JDOMException e)
        {
            throw new CrosswalkException(e);
        }
        catch (CrosswalkException e)
        {
            throw new CrosswalkException(e);
        }
    }
}
