package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.IngestionCrosswalk;
import org.dspace.core.Context;
import org.dspace.core.PluginManager;
import org.dspace.sword2.DSpaceSwordException;
import org.dspace.sword2.DepositResult;
import org.dspace.sword2.SwordContentIngester;
import org.dspace.sword2.VerboseDescription;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.swordapp.server.Deposit;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Original demonstrator class for ingesting BagIt files.  Do not use.
 *
 */
@Deprecated
public class FSPackager implements SwordContentIngester
{
    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso,
                                VerboseDescription verboseDescription)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        return this.ingest(context, deposit, dso, verboseDescription, null);
    }

    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso,
                                VerboseDescription verboseDescription, DepositResult result)
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

            // There are 3 bundles that we need to work with, so let's get them sorted
            // out up-front

            // ORIGINAL: for the main payload from the zip
            Bundle[] originals = item.getBundles("ORIGINAL");
            Bundle originalBundle = null;
            if (originals.length > 0)
            {
                originalBundle = originals[0];
            }
            else
            {
                originalBundle = item.createBundle("ORIGINAL");
            }

            // LICENSE: for the licence files
            Bundle[] licences = item.getBundles("LICENSE");
            Bundle licenceBundle = null;
            if (licences.length > 0)
            {
                licenceBundle = originals[0];
            }
            else
            {
                licenceBundle = item.createBundle("LICENSE");
            }

            // ADMINISTRATIVE: original metadata, file permissions, ordering and formats
            Bundle[] admins = item.getBundles("ADMINISTRATIVE");
            Bundle adminBundle = null;
            if (admins.length > 0)
            {
                adminBundle = originals[0];
            }
            else
            {
                adminBundle = item.createBundle("ADMINISTRATIVE");
            }

            // this is a zip file, so process our way through the contents
            ///////////////////////////////////////////////////////////////

            // get deposited file as file object
			File depositFile = deposit.getFile();

            // get the zip file into a usable form
            ZipFile zip = new ZipFile(depositFile);

            // The first thing we need to do is iterate through the zip file and get all
            // the information about the files, so that we know what to do with them
            // afterwards
            Map<String, String> ordering = new HashMap<String, String>();
            Map<String, String> formats = new HashMap<String, String>();
            Map<String, String> checksums = new HashMap<String, String>();
            Map<String, String> access = new HashMap<String, String>();
            Bitstream metadata = null;
            String root = "";

            Enumeration zenum = zip.entries();
			while (zenum.hasMoreElements())
			{
                // we are looking for the root directory, which is probably
                // the first one, but let's not make any assumptions
                ZipEntry entry = (ZipEntry) zenum.nextElement();

                if (entry.getName().endsWith("/"))
                {
                    String[] bits = entry.getName().split("/");
                    if (bits.length == 1)
                    {
                        // we have found our root
                        root = bits[0] + "/";
                        break; // no need to look further
                    }
                }
            }

            // specify the bundle mappings for files in sub directories
            Map<String, String> bundleMap = new HashMap<String, String>();
            bundleMap.put("licence/", licenceBundle.getName());
            bundleMap.put("metadata/", adminBundle.getName());

            ZipEntry entry = zip.getEntry(root + "tagfiles/access.txt");
            this.parseTagfileToHash(zip.getInputStream(entry), access, root);
            // write the hash to a file in the adminBundle
            this.writeLocalisedTagfile(context, access, bundleMap, root, "access.txt", adminBundle);

            entry = zip.getEntry(root + "tagfiles/formats.txt");
            this.parseTagfileToHash(zip.getInputStream(entry), formats, root);
            // write the hash to a file in the adminBundle
            this.writeLocalisedTagfile(context, formats, bundleMap, root, "formats.txt", adminBundle);

            entry = zip.getEntry(root + "tagfiles/sequence.txt");
            this.parseTagfileToHash(zip.getInputStream(entry), ordering, root);
            // write the hash to a file in the adminBundle
            this.writeLocalisedTagfile(context, ordering, bundleMap, root, "sequence.txt", adminBundle);

            entry = zip.getEntry(root + "manifest-md5.txt");
            this.parseTagfileToHash(zip.getInputStream(entry), checksums, root);
            // write the hash to a file in the adminBundle
            this.writeLocalisedTagfile(context, checksums, bundleMap, root, "checksums.txt", adminBundle);

            entry = zip.getEntry(root + "data/metadata/metadata.xml");
            metadata = this.writeToBundle(context, zip.getInputStream(entry), "metadata.xml", "text/xml", adminBundle);

            entry = zip.getEntry(root + "data/licence/licence.txt");
            this.writeToBundle(context, zip.getInputStream(entry), "licence.txt", "text/plain", licenceBundle);


			// Enumeration zenum = zip.entries();
			while (zenum.hasMoreElements())
			{
				entry = (ZipEntry) zenum.nextElement();

                /*
                // is this a ZipEntry that we are interested in?
                if (entry.getName().equals("tagfiles/access.txt"))
                {
                    this.parseTagfileToHash(zip.getInputStream(entry), access);
                    // write the hash to a file in the adminBundle
                    this.writeLocalisedTagfile(context, access, "access.txt", adminBundle);
                }
                else if (entry.getName().equals("tagfiles/formats.txt"))
                {
                    this.parseTagfileToHash(zip.getInputStream(entry), formats);
                    // write the hash to a file in the adminBundle
                    this.writeLocalisedTagfile(context, access, "formats.txt", adminBundle);
                }
                else if (entry.getName().equals("formats/sequence.txt"))
                {
                    this.parseTagfileToHash(zip.getInputStream(entry), ordering);
                    // write the hash to a file in the adminBundle
                    this.writeLocalisedTagfile(context, access, "sequence.txt", adminBundle);
                }
                else if (entry.getName().equals("manifest-md5.txt"))
                {
                    this.parseTagfileToHash(zip.getInputStream(entry), checksums);
                    // write the hash to a file in the adminBundle
                    this.writeLocalisedTagfile(context, access, "checksums.txt", adminBundle);
                }
                else if (entry.getName().equals("data/metadata/metadata.xml"))
                {
                    metadata = this.writeToBundle(context, zip.getInputStream(entry), "metadata.xml", "application/xml", adminBundle);
                }
                else if (entry.getName().equals("data/licence/licence.txt"))
                {
                    this.writeToBundle(context, zip.getInputStream(entry), "licence.txt", "text/plain", licenceBundle);
                }
                */
                // leave this loop in for debugging purposes
			}

            // now we know what we need to about the formats and sequences, we
            // can go through the documents in sequence order in the zip file and
            // write them to the original bundle
            TreeMap<Integer, String> seqs = this.getSequenceOrder(ordering);
            List<Bitstream> derivedResources = new ArrayList<Bitstream>();
            for (Integer i : seqs.keySet())
            {
                entry = zip.getEntry(seqs.get(i));

                String localisedName = this.getLocalisedName(entry.getName(), root);
                String format = formats.get(entry.getName());

                Bitstream bitstream = this.writeToBundle(context, zip.getInputStream(entry), localisedName, format, originalBundle);
                derivedResources.add(bitstream);
            }

            // we should now have a metadata bitstream which we can extract from
            if (metadata != null)
            {
                try
                {
                    IngestionCrosswalk inxwalk = (IngestionCrosswalk) PluginManager.getNamedPlugin(IngestionCrosswalk.class, "FS");
                    SAXBuilder builder = new SAXBuilder();
                    Document document = builder.build(metadata.retrieve());
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

            boolean ignore = context.ignoreAuthorization();
			context.setIgnoreAuthorization(true);
			item.update();
			context.setIgnoreAuthorization(ignore);

            // now finish up and pass back
            verboseDescription.append("Ingest successful");
			verboseDescription.append("Item created with internal identifier: " + item.getID());

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

    public DepositResult ingestToItem(Context context, Deposit deposit, Item item, VerboseDescription verboseDescription, DepositResult result)
			throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        // FIXME: do this later
        return null;
    }

    private void parseTagfileToHash(InputStream stream, Map<String, String> hash, String pathPrefix)
            throws IOException
    {
        byte[] bytes = new byte[stream.available()];
        stream.read(bytes);
        String s = new String(bytes);

        String[] lines = s.split("\\n");

        // FIXME: this is just for prototype purposes.  It assumes that all tag files are one line
        // per file, although the BagIt spec allows for entries to span multiple lines if the line
        // is indented.  A full treatment of BagIt therefore requires this method to do a bit more
        // work
        for (String line : lines)
        {
            String[] parts = line.split("\\s"); // FIXME: this means that files cannot include whitespace in their paths
            ArrayList<String> actualParts = new ArrayList<String>();
            for (String part : parts)
            {
                if (part != null && !"".equals(part))
                {
                    actualParts.add(part);
                }
            }
            if (actualParts.size() != 2)
            {
                // FIXME: this means that tag files may only contain one space per line, and that's the separator
                // between the tag and the file
                throw new IOException("Malformed tagfile line: " + line);
            }
            String tag = actualParts.get(0).trim();
            String file = pathPrefix + actualParts.get(1).trim();
            hash.put(file, tag);
        }
    }

    private Bitstream writeLocalisedTagfile(Context context, Map<String, String> hash,
                                            Map<String, String> bundleMap, String prefix,
                                            String filename, Bundle bundle)
            throws AuthorizeException, IOException, SQLException
    {
        StringBuilder content = new StringBuilder();
        for (String key : hash.keySet())
        {
            String localisedKey = key;
            String localBundleName = "ORIGINAL";

            // first strip the prefix
            if (key.startsWith(prefix + "data/"))
            {
                localisedKey = key.substring((prefix + "data/").length());
            }

            // now look to see if this has additional path info, and strip it
            // -> this should handle metadata and licence files
            String[] bits = localisedKey.split("/");
            if (bits.length > 1)
            {
                localisedKey = bits[bits.length - 1];
                StringBuilder subpath = new StringBuilder();
                for (int i = 0; i < bits.length - 1; i++)
                {
                    subpath.append(bits[i] + "/");
                }
                localBundleName = bundleMap.get(subpath.toString());
            }

            // finally prefix the relevant bundle name
            localisedKey = localBundleName + "/" + localisedKey;
            String value = hash.get(key);
            content.append(value + " " + localisedKey + "\n");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(content.toString().getBytes());
        Bitstream bitstream = bundle.createBitstream(bais);
        bitstream.setName(filename);
        BitstreamFormat format = BitstreamFormat.findByMIMEType(context, "text/plain");
        bitstream.setFormat(format);
        bitstream.update();
        return bitstream;
    }

    private Bitstream writeToBundle(Context context, InputStream stream, String filename, String mimeType, Bundle bundle)
            throws AuthorizeException, IOException, SQLException
    {
        Bitstream bitstream = bundle.createBitstream(stream);
        bitstream.setName(filename);
        BitstreamFormat format = BitstreamFormat.findByMIMEType(context, mimeType);
        bitstream.setFormat(format);
        bitstream.update();
        return bitstream;
    }

    private TreeMap<Integer, String> getSequenceOrder(Map<String, String> source)
    {
        TreeMap<Integer, String> sequences = new TreeMap<Integer, String>();
        for (String file : source.keySet())
        {
            String position = source.get(file);
            int seq = Integer.parseInt(position);
            sequences.put(seq, file);
        }
        return sequences;
    }

    private String getLocalisedName(String entryName, String prefix)
    {
        if (entryName.startsWith(prefix + "data/"))
        {
            return entryName.substring((prefix + "data/").length());
        }
        return entryName;
    }

    private String getTreatment()
    {
        return "Document has been unpackaged, and metadata extracted";
    }
}
