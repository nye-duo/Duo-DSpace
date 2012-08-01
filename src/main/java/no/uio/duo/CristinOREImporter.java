package no.uio.duo;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.FormatIdentifier;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.CrosswalkObjectNotSupported;
import org.dspace.content.crosswalk.IngestionCrosswalk;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CristinOREImporter implements IngestionCrosswalk
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



	public void ingest(Context context, DSpaceObject dso, List<Element> metadata) throws CrosswalkException, IOException, SQLException, AuthorizeException {

		// If this list contains only the root already, just pass it on
        if (metadata.size() == 1) {
			ingest(context, dso, metadata.get(0));
		}
		// Otherwise, wrap them up
		else {
			Element wrapper = new Element("wrap", metadata.get(0).getNamespace());
			wrapper.addContent(metadata);

			ingest(context,dso,wrapper);
		}
	}



	public void ingest(Context context, DSpaceObject dso, Element root) throws CrosswalkException, IOException, SQLException, AuthorizeException {

		Date timeStart = new Date();

		if (dso.getType() != Constants.ITEM)
        {
            throw new CrosswalkObjectNotSupported("OREIngestionCrosswalk can only crosswalk an Item.");
        }
        Item item = (Item)dso;

        if (root == null) {
        	System.err.println("The element received by ingest was null");
        	return;
        }

        Document doc = new Document();
        doc.addContent(root.detach());

        XPath xpathLinks;
        List<Element> aggregatedResources;
        String entryId;
		try {
			xpathLinks = XPath.newInstance("/atom:entry/atom:link[@rel=\"" + ORE_NS.getURI()+"aggregates" + "\"]");
			xpathLinks.addNamespace(ATOM_NS);
	        aggregatedResources = xpathLinks.selectNodes(doc);

	        xpathLinks = XPath.newInstance("/atom:entry/atom:link[@rel='alternate']/@href");
	        xpathLinks.addNamespace(ATOM_NS);
	        entryId = ((Attribute)xpathLinks.selectSingleNode(doc)).getValue();
		} catch (JDOMException e) {
			throw new CrosswalkException("JDOM exception occured while ingesting the ORE", e);
		}

        // FIXME: ensure that the bitstreams are created in the right place...
        
		// Next for each resource, create a bitstream
    	XPath xpathDesc;
    	NumberFormat nf=NumberFormat.getInstance();
		nf.setGroupingUsed(false);
		nf.setMinimumIntegerDigits(4);

        for (Element resource : aggregatedResources)
        {
        	String href = resource.getAttributeValue("href");
        	log.debug("ORE processing: " + href);

        	String bundleName;
        	Element desc = null;
        	try {
        		xpathDesc = XPath.newInstance("/atom:entry/oreatom:triples/rdf:Description[@rdf:about=\"" + this.encodeForURL(href) + "\"][1]");
        		xpathDesc.addNamespace(ATOM_NS);
        		xpathDesc.addNamespace(ORE_ATOM);
        		xpathDesc.addNamespace(RDF_NS);
        		desc = (Element)xpathDesc.selectSingleNode(doc);
        	} catch (JDOMException e) {
        		e.printStackTrace();
        	}

        	if (desc != null && desc.getChild("type", RDF_NS).getAttributeValue("resource", RDF_NS).equals(DS_NS.getURI() + "DSpaceBitstream"))
        	{
                bundleName = this.isMetadataBitstream(desc) ? "METADATA" : "ORIGINAL";
        		log.debug("Setting bundle name to: " + bundleName);
        	}
        	else {
        		log.info("Could not obtain bundle name; using 'ORIGINAL'");
        		bundleName = "ORIGINAL";
        	}

        	// Bundle names are not unique, so we just pick the first one if there's more than one.
        	Bundle[] targetBundles = item.getBundles(bundleName);
        	Bundle targetBundle;

        	// if null, create the new bundle and add it in
        	if (targetBundles.length == 0) {
        		targetBundle = item.createBundle(bundleName);
        		item.addBundle(targetBundle);
        	}
        	else {
        		targetBundle = targetBundles[0];
        	}

        	URL ARurl = null;
        	InputStream in = null;
        	if (href != null) {
        		try {
		        	// Make sure the url string escapes all the oddball characters
        			String processedURL = encodeForURL(href);
        			// Generate a requeset for the aggregated resource
        			ARurl = new URL(processedURL);
		        	in = ARurl.openStream();
        		}
        		catch(FileNotFoundException fe) {
            		log.error("The provided URI failed to return a resource: " + href);
            	}
        		catch(ConnectException fe) {
            		log.error("The provided URI was invalid: " + href);
            	}
        	}
        	else {
        		throw new CrosswalkException("Entry did not contain link to resource: " + entryId);
        	}

        	// ingest and update
        	if (in != null) {
	        	Bitstream newBitstream = targetBundle.createBitstream(in);

	        	String bsName = resource.getAttributeValue("title");
	        	newBitstream.setName(bsName);

	            // Identify the format
	        	String mimeString = resource.getAttributeValue("type");
	        	BitstreamFormat bsFormat = BitstreamFormat.findByMIMEType(context, mimeString);
	        	if (bsFormat == null) {
	        		bsFormat = FormatIdentifier.guessFormat(context, newBitstream);
	        	}
	        	newBitstream.setFormat(bsFormat);
	            newBitstream.update();

	            targetBundle.addBitstream(newBitstream);
	        	targetBundle.update();
        	}
        	else {
        		throw new CrosswalkException("Could not retrieve bitstream: " + entryId);
        	}
        }

        // FIXME: once ingested, we need to crosswalk from the METADATA bundle
        // to the item itself

        log.info("OREIngest for Item "+ item.getID() + " took: " + (new Date().getTime() - timeStart.getTime()) + "ms.");
	}

    private boolean isMetadataBitstream(Element desc)
    {
        // https://w3utv-dspace01.uio.no/dspace/xmlui/bitstream/handle/123456789/982/cristin-12087.xml?sequence=2
        
        Attribute about = desc.getAttribute("about", RDF_NS);
        String url = about.getValue();

        // FIXME: yeah yeah, this would look better with a regex
        String[] bits = url.split("\\?");
        String[] urlParts = bits[0].split("/");
        String filename = urlParts[urlParts.length - 1];

        if (filename.startsWith("cristin") && filename.endsWith(".xml"))
        {
            return true;
        }
        return false;
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
}
