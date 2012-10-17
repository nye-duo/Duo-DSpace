package no.uio.duo;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.FormatIdentifier;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.core.Context;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;
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
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class FileManager
{
    /** log4j category */
    private static Logger log = Logger.getLogger(FileManager.class);

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
    
    public List<Bitstream> getExistingBitstreams(Item item, String bundleName)
            throws SQLException
    {
        List<Bitstream> bss = new ArrayList<Bitstream>();
        Bundle[] bundles = item.getBundles(bundleName);
        for (Bundle bundle : bundles)
        {
            Bitstream[] bitstreams = bundle.getBitstreams();
            for (Bitstream bitstream : bitstreams)
            {
                bss.add(bitstream);
            }
        }
        return bss;
    }

    public List<IncomingBitstream> listBitstreamsInBundle(Document doc, String bundleName)
            throws IOException
    {
        return this.listBitstreamsInBundle(doc, bundleName, true);
    }

    public List<IncomingBitstream> listBitstreamsInBundle(Document doc, String bundleName, boolean omitMetadata)
            throws IOException
    {
        String mdUrl = null;
        List<Element> bitstreams = new ArrayList<Element>();

        // first action is to get all the information we can from the ORE doc.
        // This will hopefully include a link to the cristin.xml bitstream
        // which we will need in order to learn about md5 checksums and file sequences
        List<Element> links = this.listBitstreams(doc);
        for (Element link : links)
        {
            String incomingBundle = this.getIncomingBundleName(doc, link);

            if (bundleName.equals(incomingBundle))
            {
                // this is a bitstream from the correct bundle
                // only register it if it is not a metadata bitstream
                boolean metadataBitstream = this.isMetadataBitstream(link.getAttributeValue("href"));
                if (metadataBitstream)
                {
                    mdUrl = link.getAttributeValue("href");
                }
                if (!metadataBitstream || (metadataBitstream && !omitMetadata))
                {
                    bitstreams.add(link);
                }
            }
        }

        // if we found a metadata bitstream, then let's get the information out of it
        // this is where we will discover md5 checksums and file sequences
        List<Element> fulltexts = null;
        if (mdUrl != null)
        {
            try
            {
                InputStream in = this.getInputStream(mdUrl);
                Document cristin = (new SAXBuilder()).build(in);

                // get the fulltekst elements
                XPath ftXpath = XPath.newInstance("/frida/forskningsresultat/fellesdata/fulltekst");
	            fulltexts = ftXpath.selectNodes(cristin);
            }
            catch (JDOMException e)
            {
                throw new IOException(e);
            }
        }

        List<IncomingBitstream> ibs = this.makeIncomingBitstreams(bitstreams, fulltexts);

        return ibs;
    }

    private List<IncomingBitstream> makeIncomingBitstreams(List<Element> links, List<Element> fulltexts)
    {
        List<IncomingBitstream> ibs = new ArrayList<IncomingBitstream>();
        for (Element link : links)
        {
            IncomingBitstream ib = new IncomingBitstream();
            ib.setUrl(link.getAttributeValue("href"));
            ib.setName(link.getAttributeValue("title"));
            ib.setMimetype(link.getAttributeValue("type"));
            if (fulltexts != null)
            {
                /*
                <fulltekst>
                    <nr>1</nr>
                    <type>preprint</type>
                    <navn>1-introduction.doc</navn>
                    <antallBytes>93696</antallBytes>
                    <dato>2011-07-27</dato>
                    <personreferanse>Marianne Elisabeth Lien, UIO</personreferanse>
                    <md5>checksum</md5>
                </fulltekst>
                */
                for (Element ft : fulltexts)
                {
                    // we're going to match by name on the assumption that the filenames
                    // are unique within the context of the item
                    // FIXME: once we have md5 checksums in the ORE feed, we will be able
                    // to match on checksum, which will be a more formal solution
                    if (ft.getChildTextTrim("navn").equals(ib.getName()))
                    {
                        String nr = ft.getChildTextTrim("nr");
                        String md5 = ft.getChildTextTrim("md5");

                        int seq = -1;
                        if (nr != null)
                        {
                            seq = Integer.parseInt(nr);
                        }
                        ib.setOrder(seq);

                        if (md5 != null)
                        {
                            ib.setMd5(md5);
                        }
                    }
                }
            }
            ibs.add(ib);
        }
        return ibs;
    }

    private String getFilename(String url)
    {
        // FIXME: yeah yeah, this would look better with a regex
        String[] bits = url.split("\\?");
        String[] urlParts = bits[0].split("/");
        String filename = urlParts[urlParts.length - 1];
        try
        {
            return URLDecoder.decode(filename, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean isMetadataBitstream(String url)
    {
        // https://w3utv-dspace01.uio.no/dspace/xmlui/bitstream/handle/123456789/982/cristin-12087.xml?sequence=2
        String filename = this.getFilename(url);
        if (filename.startsWith("cristin-") && filename.endsWith(".xml"))
        {
            return true;
        }
        return false;
    }

    public String getIncomingBundleName(Document doc, Element link)
            throws IOException
    {
        try
        {
            String href = link.getAttributeValue("href");
            XPath xpathDesc = XPath.newInstance("/atom:entry/oreatom:triples/rdf:Description[@rdf:about=\"" + href + "\"]");
            xpathDesc.addNamespace(ATOM_NS);
            xpathDesc.addNamespace(ORE_ATOM);
            xpathDesc.addNamespace(RDF_NS);
            List<Element> descs = xpathDesc.selectNodes(doc);
            for (Element desc : descs)
            {
                Element dcdesc = desc.getChild("description", DCTERMS_NS);
                return dcdesc.getText();
            }
        }
        catch (JDOMException e)
        {
            throw new IOException("JDOM exception occured while ingesting the ORE", e);
        }

        return null;
    }

    public List<Element> listBitstreams(Document doc)
            throws IOException
    {
        XPath xpathLinks;
        List<Element> aggregatedResources;
        // String entryId;
        try
        {
            xpathLinks = XPath.newInstance("/atom:entry/atom:link[@rel=\"" + ORE_NS.getURI() + "aggregates" + "\"]");
            xpathLinks.addNamespace(ATOM_NS);
            aggregatedResources = xpathLinks.selectNodes(doc);

            // xpathLinks = XPath.newInstance("/atom:entry/atom:link[@rel='alternate']/@href");
            // xpathLinks.addNamespace(ATOM_NS);
            // entryId = ((Attribute) xpathLinks.selectSingleNode(doc)).getValue();
        }
        catch (JDOMException e)
        {
            throw new IOException("JDOM exception occured while ingesting the ORE", e);
        }

        return aggregatedResources;
    }

    public boolean isMetadataBitstream(Element desc)
    {
        // https://w3utv-dspace01.uio.no/dspace/xmlui/bitstream/handle/123456789/982/cristin-12087.xml?sequence=2

        Attribute about = desc.getAttribute("about", RDF_NS);
        String url = about.getValue();

        // FIXME: yeah yeah, this would look better with a regex
        String[] bits = url.split("\\?");
        String[] urlParts = bits[0].split("/");
        String filename = urlParts[urlParts.length - 1];

        if (filename.startsWith("cristin-") && filename.endsWith(".xml"))
        {
            return true;
        }
        return false;
    }

    public InputStream getInputStream(String href)
            throws MalformedURLException, IOException
    {
        // ingest the bitstream from the remote url
        URL ARurl = null;
        InputStream in = null;
        if (href != null)
        {
            try
            {
                // Make sure the url string escapes all the oddball characters
                String processedURL = encodeForURL(href);
                // Generate a requeset for the aggregated resource
                ARurl = new URL(processedURL);
                in = ARurl.openStream();
            }
            catch(FileNotFoundException fe)
            {
                log.error("The provided URI failed to return a resource: " + href);
            }
            catch(ConnectException fe)
            {
                log.error("The provided URI was invalid: " + href);
            }
        }
        else
        {
            throw new IOException("Could not obtain resource from " + href);
        }
        return in;
    }

    public Bitstream ingestBitstream(Context context, String href, String bsName, String mimeString, Bundle targetBundle)
            throws MalformedURLException, IOException, CrosswalkException, AuthorizeException, SQLException
    {
        Bitstream bitstream = null;

        InputStream in = this.getInputStream(href);

        // ingest and update
        if (in != null)
        {
            bitstream = targetBundle.createBitstream(in);
            bitstream.setName(bsName);

            // Identify the format
            BitstreamFormat bsFormat = BitstreamFormat.findByMIMEType(context, mimeString);
            if (bsFormat == null)
            {
                bsFormat = FormatIdentifier.guessFormat(context, bitstream);
            }
            bitstream.setFormat(bsFormat);
            bitstream.update();

            targetBundle.addBitstream(bitstream);
            targetBundle.update();
        }
        else
        {
            throw new CrosswalkException("Could not retrieve bitstream");
        }

        return bitstream;
    }

    public int getBitstreamOrder(Context context, Bitstream bitstream)
            throws SQLException
    {
        String query = "SELECT bitstream_order FROM bundle2bitstream WHERE bitstream_id = ?";
        Object[] params = { bitstream.getID() };
        TableRowIterator tri = DatabaseManager.query(context, query, params);
        if (tri.hasNext())
        {
            TableRow row = tri.next();
            int order = row.getIntColumn("bitstream_order");
            tri.close();
            return order;
        }
        return -1;
    }

    /*
        Does the incoming bitstream exactly match (i.e. the checksum) of an existing bitstream
     */
    public boolean bitstreamInstanceAlreadyExists(IncomingBitstream ib, List<Bitstream> existingBitstreams)
    {
        for (Bitstream bs : existingBitstreams)
        {
            if (ib.getMd5() != null)
            {
                if (ib.getMd5().equals(bs.getChecksum()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean bitstreamNameAlreadyExists(IncomingBitstream ib, List<Bitstream> existingBitstreams)
    {
        for (Bitstream bs : existingBitstreams)
        {
            if (ib.getName() != null)
            {
                if (ib.getName().equals(bs.getName()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public Bitstream findBitstream(IncomingBitstream ib, Bitstream[] bitstreams)
    {
        for (Bitstream bs : bitstreams)
        {
            if (ib.getName() != null)
            {
                if (ib.getName().equals(bs.getName()))
                {
                    return bs;
                }
            }
        }
        return null;
    }

    public boolean isNewBitstream(IncomingBitstream ib, List<Bitstream> existingBitstreams)
    {
        for (Bitstream bs : existingBitstreams)
        {
            if (ib.getMd5() != null)
            {
                if (ib.getMd5().equals(bs.getChecksum()))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /*
        a bitstream is incoming if either the name or the checksum of an existing
         bitstream match an incoming bitstream
     */
    public boolean bitstreamIsIncoming(Bitstream bitstream, List<IncomingBitstream> ibs)
    {
        for (IncomingBitstream ib : ibs)
        {
            if (ib.getMd5() != null)
            {
                if (ib.getMd5().equals(bitstream.getChecksum()))
                {
                    return true;
                }
            }
            if (ib.getName().equals(bitstream.getName()))
            {
                return true;
            }
        }
        return false;
    }

    public void sequenceBitstreams(Item item, String bundleName, List<IncomingBitstream> ibs)
            throws SQLException, AuthorizeException
    {
        // get the bundle we are going to sequence in
        Bundle[] bundles = item.getBundles(bundleName);
        Bundle bundle = bundles.length > 0 ? bundles[0] : null;
        if (bundle == null)
        {
            return;
        }

        // get the existing bitstreams, and prepare the ordering array
        Bitstream[] bss = bundle.getBitstreams();
        int[] order = new int[bss.length];
        Map<Integer, Integer> orderMap = new TreeMap<Integer, Integer>();

        // go through the incoming bitstreams and find the corresponding
        // DSpace bitstream (this will ignore any incoming bitstreams
        // that didn't find their way into the bundle of interest)
        int offset = 1;
        for (IncomingBitstream ib : ibs)
        {
            if (ib.getMd5() != null)
            {
                for (Bitstream bitstream : bss)
                {
                    if (ib.getMd5().equals(bitstream.getChecksum()))
                    {
                        int pos = ib.getOrder();
                        if (pos == -1)
                        {
                            pos = -1 * offset;
                            offset++;
                        }
                        orderMap.put(pos, bitstream.getID());
                    }
                }
            }
            else
            {
                for (Bitstream bitstream : bss)
                {
                    if (ib.getName().equals(bitstream.getName()))
                    {
                        int pos = ib.getOrder();
                        if (pos == -1)
                        {
                            pos = -1 * offset;
                            offset++;
                        }
                        orderMap.put(pos, bitstream.getID());
                    }
                }
            }
        }

        // now convert the order map to the correct array format, normalising
        // the numbering as we go
        int idx = 0;
        for (Integer ord : orderMap.keySet())
        {
            order[idx] = orderMap.get(ord);
            idx++;
        }

        // finally, as the bundle to order the bitstreams
        bundle.setOrder(order);
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
