package no.uio.duo;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FileManager
{
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

    public List<Element> listBitstreamsInBundle(Document doc, String bundleName)
            throws IOException
    {
        return this.listBitstreamsInBundle(doc, bundleName, true);
    }

    public List<Element> listBitstreamsInBundle(Document doc, String bundleName, boolean omitMetadata)
            throws IOException
    {
        List<Element> bitstreams = new ArrayList<Element>();
        List<Element> links = this.listBitstreams(doc);
        for (Element link : links)
        {
            String incomingBundle = this.getIncomingBundleName(doc, link);

            if (bundleName.equals(incomingBundle))
            {
                // this is a bitstream from the correct bundle
                // only register it if it is not a metadata bitstream
                boolean metadataBitstream = this.isMetadataBitstream(link.getAttributeValue("href"));
                if (!metadataBitstream || (metadataBitstream && !omitMetadata))
                {
                    bitstreams.add(link);
                }
            }
        }
        return bitstreams;
    }

    public boolean isMetadataBitstream(String url)
    {
        // https://w3utv-dspace01.uio.no/dspace/xmlui/bitstream/handle/123456789/982/cristin-12087.xml?sequence=2

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
}
