package no.uio.duo;import no.nb.idservice.client.ws.v1_0.FailedLoginException_Exception;import no.nb.idservice.client.ws.v1_0.IdService;import no.nb.idservice.client.ws.v1_0.IdService_Service;import no.nb.idservice.client.ws.v1_0.URLInfo;import no.nb.idservice.client.ws.v1_0.URNInfo;import no.nb.idservice.client.ws.v1_0.URNNotFoundException_Exception;import org.apache.commons.cli.CommandLine;import org.apache.commons.cli.CommandLineParser;import org.apache.commons.cli.Options;import org.apache.commons.cli.PosixParser;import org.dspace.content.Bitstream;import org.dspace.content.Bundle;import org.dspace.content.DCValue;import org.dspace.content.DSpaceObject;import org.dspace.content.Item;import org.dspace.content.ItemIterator;import org.dspace.core.ConfigurationManager;import org.dspace.core.Context;import org.dspace.eperson.EPerson;import org.dspace.handle.HandleManager;import javax.xml.namespace.QName;import java.io.UnsupportedEncodingException;import java.net.MalformedURLException;import java.net.URL;import java.net.URLEncoder;import java.util.HashMap;import java.util.List;import java.util.Map;/** * <p>Command line script to enhance the metadata of an item with a URN retrieved from the * National Library and the urls of all the bitstreams associated with an item</p> * * <p><strong>Execution</strong></p> * * <p>The script can be run with the dsrun command of the dspace script.  If run without * arguments it will add URNs and bitstream urls to every time which does not have a URN already.</p> * * <pre> *     [dspace]/bin/dspace dsrun no.uio.duo.URNGenerator -e username [-f] [-a] * </pre> * * <p>So, to generate URNs and full-text urls for all items which do not currently have a URN:</p> * * <pre> *     [dspace]/bin/dspace dsrun no.uio.duo.URNGenerator -e username * </pre> * * <p>To generate URNs for items which do not currently have one, and to also (re)generate ALL bitstream urls</p> * * <pre> *     [dspace]/bin/dspace dsrun no.uio.duo.URNGenerator -e username -f * </pre> * * <p>To regenerate URNs for ALL items, and regenerate ALL bitstream urls</p> * * <pre> *     [dspace]/bin/dspace dsrun no.uio.duo.URNGenerator -e username -a * </pre> * */public class URNGenerator{    private QName serviceName = null;    private IdService_Service client = null;    private IdService service = null;    private String sessionToken = null;    private String series = null;    private String baseUrl = null;    String urnField = null;    String fulltextField = null;    /**     * Runs this script.  -e argument is mandatory, -f and -a arguments are optional     *     * See class documentation for details of how to run the script     *     * @param args     * @throws Exception     */    public static void main(String[] args)            throws Exception    {        CommandLineParser parser = new PosixParser();        Options options = new Options();        options.addOption("f", "fulltext", false, "should the fulltext links be updated.  In cases where the URN does not already exist, this will be done automatically");        options.addOption("a", "all", false, "regenerate all of the URNs");        options.addOption("e", "eperson", true, "eperson to carry out the operations as - recommended to be an administrator account");        options.addOption("h", "handle", true, "handle to operate on.  If omitted this will act on all items");        CommandLine line = parser.parse(options, args);        boolean ft = line.hasOption("f");        boolean all = line.hasOption("a");        if (!line.hasOption("e"))        {            System.out.println("You MUST provide a user account to carry out the actions as");            System.exit(0);        }        String ep = line.getOptionValue("e");        String handle = null;        if (line.hasOption("h"))        {            handle = line.getOptionValue("h");        }        // brute force load the DSpace config        ConfigurationManager.loadConfig(null);        URNGenerator urng = new URNGenerator();        Context context = new Context();        context.turnOffAuthorisationSystem();        EPerson eperson = EPerson.findByEmail(context, ep);        if (eperson == null)        {            eperson = EPerson.findByNetid(context, ep);        }        if (eperson == null)        {            System.out.println("Unable to find eperson by account " + ep);            context.abort();            System.exit(0);        }        context.setCurrentUser(eperson);        try        {            if (handle != null)            {                urng.addToHandle(context, handle, ft, all);            }            else            {                urng.addToAll(context, ft, all);            }        }        catch (Exception e)        {            if (context.isValid())            {                context.abort();            }            throw e;        }        finally        {            if (context.isValid())            {                context.complete();            }        }    }    /**     * Constructor, which does all the necessary bootstrapping to connect to the     * URN service, including authentication.     *     * @throws MalformedURLException     * @throws FailedLoginException_Exception     */    public URNGenerator()            throws MalformedURLException, FailedLoginException_Exception    {        String idServiceUrl = ConfigurationManager.getProperty("urn", "idservice.url");        String username = ConfigurationManager.getProperty("urn", "idservice.username");        String password = ConfigurationManager.getProperty("urn", "idservice.password");        if (idServiceUrl == null || "".equals(idServiceUrl))        {            throw new MalformedURLException("No URL provided in configuration");        }        URL serviceURL = new URL(idServiceUrl);        serviceName = new QName("http://nb.no/idservice/v1.0/", "IdService");        client = new IdService_Service(serviceURL, serviceName);        service = client.getIdServiceSOAP11Port();        sessionToken = service.login(username, password);        series = ConfigurationManager.getProperty("urn", "idservice.series");        baseUrl = ConfigurationManager.getProperty("urn", "item.base_url");        urnField = ConfigurationManager.getProperty("urn", "urn.field");        fulltextField = ConfigurationManager.getProperty("urn", "fulltext.field");    }    /**     * Request a URN to represent the provided URL.     *     * This method performs a web service request to the National Library API     *     * @param url     * @return  urn, or null if there was an error     */    private String getURN(String url)    {        try        {            URNInfo info = service.createURN(sessionToken, series, url);            String urn = info.getURN();            return urn;        }        catch (Exception e)        {            e.printStackTrace();            return null;        }    }    /**     * Replace the old url with the new url at the supplied urn     *     * This method serves to trap any SOAP errors from connecting to the web service, and returns     * true or false on success/failure depending on those errors     *     * @param urn   urn to replace the url in     * @param old   existing url in the id service     * @param url   url to replace the existing url     * @return  true on success, false on failure     */    private boolean replaceURL(String urn, String old, String url)    {        try        {            URNInfo info = service.replaceURL(sessionToken, urn, old, url);            return true;        }        catch (Exception e)        {            e.printStackTrace();            return false;        }    }    /**     * Delete the given url from the urn record     *     * This method serves to trap any SOAP errors from connecting to the web service, and returns     * true or false on success/failure depending on those errors     *     * @param urn   the urn to remove the url from     * @param url   the url to be removed     * @return  true if success, false if failure     */    private boolean deleteURL(String urn, String url)    {        try        {            URNInfo info = service.deleteURL(sessionToken, urn, url);            return true;        }        catch (Exception e)        {            e.printStackTrace();            return false;        }    }    public void addToItem(Context context, Item item, boolean doFulltext, boolean allURNs, Map<String, Integer> register)            throws Exception    {        String ep = context.getCurrentUser().getEmail();        boolean update = false;        // only do the urn operations if a configuration option is specified        if (urnField != null && !"".equals(urnField))        {            // determine whether the item already has a URN field - if it does not, we will            // add one            DCValue[] urns = item.getMetadata(urnField);            if (urns.length == 0)            {                String handle = item.getHandle();                String url = this.getItemUrl(item);                System.out.println("Obtaining URN for item with handle " + handle + " for url " + url);                // we need to provide the item with a URN                DCValue dcv = this.getDCValue(urnField);                String urn = this.getURN(url);                if (urn != null)                {                    System.out.println("for item " + handle + ": urn=" + urn);                    item.addMetadata(dcv.schema, dcv.element, dcv.qualifier, null, urn);                    update = true;                    register.put("genurn", register.get("genurn") + 1);                }                else                {                    System.out.println("unable to obtain URN for item " + handle);                    register.put("failures", register.get("failures") + 1);                }                // updating the urn means we always do the fulltext and we need to update the item                doFulltext = true;            }            // determine whether we are re-registering the urn (which we only do if            // we did not do the above            else if (allURNs)            {                // get the urn from the dcvalue - assuming there is only one                String urn = urns[0].value;                // generate the URL we need to record                String handle = item.getHandle();                String url = this.getItemUrl(item);                System.out.println("Updating URN for item with handle " + handle + " with url " + url);                // request the URN info from the service                URNInfo findurn = null;                try                {                    findurn = service.findURN(urn);                }                catch (URNNotFoundException_Exception e)                {                    // there is a problem with the existing URN, so we need to just create a new one                    System.out.println("Unable to locate URN " + urn + " in URN Service - creating a new one");                    item.clearMetadata(urns[0].schema, urns[0].element, urns[0].qualifier, Item.ANY);                    String newUrn = this.getURN(url);                    item.addMetadata(urns[0].schema, urns[0].element, urns[0].qualifier, null, newUrn);                    update = true;                    register.put("genurn", register.get("genurn") + 1);                    System.out.println("for item " + handle + ": urn=" + newUrn);                }                // if we found a URN in the remote service, we can do our updates                if (findurn != null)                {                    register.put("updated", register.get("updated") + 1);                    System.out.println("URN for existing item with handle " + handle + " confirmed as " + urn);                    List<URLInfo> urllist = findurn.getUrlList().getUrl();                    if (urllist.size() == 0)                    {                        // This can't actually happen, as far as I can tell from the documentation                        System.out.println("URN " + urn + " has no associated urls, adding " + url);                        service.addURL(sessionToken, urn, url);                    }                    else if (urllist.size() == 1)                    {                        String old = urllist.get(0).getURL();                        if (!old.equals(url))                        {                            System.out.println("URN " + urn + " has one associated url; replacing " + old + " with " + url);                            boolean replaced = this.replaceURL(urn, old, url);                            if (!replaced)                            {                                System.out.println("An error occurred replacing the url in " + urn);                                register.put("failures", register.get("failures") + 1);                            }                        }                        else                        {                            System.out.println("URN " + urn + " does not need updating - old and new urls are the same");                        }                    }                    else if (urllist.size() > 1)                    {                        System.out.println("URN " + urn + " has multiple urls - removing all and adding new url " + url);                        // delete all but one of the URLs                        for (int i = 0; i < urllist.size() - 1; i++)                        {                            String old = urllist.get(i).getURL();                            boolean deleted = this.deleteURL(urn, old); // don't really care about the response                        }                        // now replace the final url                        String old = urllist.get(urllist.size() - 1).getURL();                        boolean replaced = this.replaceURL(urn, old, url);                        if (!replaced)                        {                            System.out.println("An error occurred replacing the url in " + urn);                            register.put("failures", register.get("failures") + 1);                        }                    }                }                // all urns implicitly means also do the fulltext                doFulltext = true;            }            else            {                System.out.println("Not generating a URN for item " + item.getHandle() + " - it already has one");            }        }        // only do the fulltext urls if a configuration option is specified, and the doFulltext flag is set        if (fulltextField != null && !"".equals(fulltextField) && doFulltext)        {            System.out.println("(re)generating fulltext urls for item " + item.getHandle());            DCValue dcv = this.getDCValue(fulltextField);            // clear any pre-existing metadata in these fields            item.clearMetadata(dcv.schema, dcv.element, dcv.qualifier, Item.ANY);            String prefix = ConfigurationManager.getProperty("urn", "fulltext.prefix");            if (prefix != null && !"".equals(prefix))            {                if (!prefix.endsWith(" "))                {                    prefix += " ";                }            }            else            {                prefix = "";            }            // for each ORIGINAL bundle, do each bitstream            Bundle[] originals = item.getBundles("ORIGINAL");            for (Bundle original : originals)            {                Bitstream[] bitstreams = original.getBitstreams();                for (Bitstream bitstream : bitstreams)                {                    String url = this.getBitstreamUrl(item, bitstream);                    System.out.println("for item " + item.getHandle() + ": adding " + url);                    item.addMetadata(dcv.schema, dcv.element, dcv.qualifier, null, prefix + url);                    // this means we need to update the item                    update = true;                }            }        }        if (update)        {            System.out.println("writing updates to DSpace...");            item.update();            // write the changes a record at a time, so an error down the line doesn't            // undo our good work            context.commit();            // also clear the cache, to stop this taking up so much memory            context.clearCache();        }        System.out.println();    }    /**     * Add the URN and the fulltext urls to the item identified by the given handle     *     * @param context   The DSpace Context object     * @param doFulltext    Whether to force the regeneration of bitstream urls or not     *     * @throws Exception     */    public void addToHandle(Context context, String handle, boolean doFulltext, boolean allURNs)            throws Exception    {        this.printParameters(context, doFulltext, allURNs, true);        DSpaceObject dso = HandleManager.resolveToObject(context, handle);        if (dso == null || !(dso instanceof Item))        {            throw new Exception("handle does not resolve to an Item");        }        Map<String, Integer> register = new HashMap<String, Integer>();        register.put("total", 1);        register.put("failures", 0);        register.put("genurn", 0);        register.put("updated", 0);        this.addToItem(context, (Item) dso,  doFulltext, allURNs, register);        System.out.println("Processed " + Integer.toString(register.get("total")) + " items");        System.out.println("Generated " + Integer.toString(register.get("genurn")) + " URNs");        System.out.println("Updated " + Integer.toString(register.get("updated")) + " URNs");        System.out.println("with " + Integer.toString(register.get("failures")) + " failures");    }    public void printParameters(Context context, boolean doFulltext, boolean allURNs, boolean oneItem)    {        String ep = context.getCurrentUser().getEmail();        // output a report of the running parameters of the operation        System.out.println();        System.out.println("Running URN Generator as " + ep);        if (urnField != null)        {            System.out.println("URN Field: " + urnField);        }        else        {            System.out.println("No URN Field specified in configuration - will not assign URNs");        }        if (fulltextField != null)        {            System.out.println("Fulltext Field: " + fulltextField);        }        else        {            System.out.println("No Fulltext Field specified in configuration - will not add fulltext links");        }        if (doFulltext)        {            System.out.println("Fulltext urls explicitly requested to be regenerated");        }        else        {            System.out.println("Fulltext urls only for items with no existing URN");        }        if (allURNs)        {            System.out.println("Regenerating URNs for all selected items");        }        if (oneItem)        {            System.out.println("Running for just one specified item");        }        else        {            System.out.println("Running over all items in the archive");        }        System.out.println();    }    /**     * Add the URN and the fulltext urls to all of the relevant items in the repository     *     * @param context   The DSpace Context object     * @param doFulltext    Whether to force the regeneration of bitstream urls or not     *     * @throws Exception     */    public void addToAll(Context context, boolean doFulltext, boolean allURNs)            throws Exception    {        this.printParameters(context, doFulltext, allURNs, false);        ItemIterator ii = Item.findAll(context);        Map<String, Integer> register = new HashMap<String, Integer>();        register.put("total", 0);        register.put("failures", 0);        register.put("genurn", 0);        register.put("updated", 0);        while (ii.hasNext())        {            register.put("total", register.get("total") + 1);            Item item = ii.next();            this.addToItem(context, item, doFulltext, allURNs, register);        }        System.out.println("Processed " + Integer.toString(register.get("total")) + " items");        System.out.println("Generated " + Integer.toString(register.get("genurn")) + " URNs");        System.out.println("Updated " + Integer.toString(register.get("updated")) + " URNs");        System.out.println("with " + Integer.toString(register.get("failures")) + " failures");    }    /**     * Turn a configuration string into a DCValue object     *     * @param mdString  configuration string in the form [schema].[element].[qualifier]     * @return     */    private DCValue getDCValue(String mdString)    {        String bits[] = mdString.split("\\.");        DCValue dcv = new DCValue();        dcv.schema = bits[0];        dcv.element = bits[1];        if (bits.length == 3)        {            dcv.qualifier = bits[2];        }        return dcv;    }    /**     * Generate the bitstream url for the bitstream in the context of the supplied item     *     * @param item  The item the bitstream is part of     * @param bitstream The bitstream whose url to construct     * @return     */    private String getBitstreamUrl(Item item, Bitstream bitstream)    {        String base = ConfigurationManager.getProperty("urn", "bitstream.base_url");        String handle = item.getHandle();        int seq = bitstream.getSequenceID();        String name = bitstream.getName();        String urlName = null;        try        {            urlName = URLEncoder.encode(name, "UTF-8");        }        catch (UnsupportedEncodingException e)        {            // take a chance            urlName = name;        }        String url = base + handle + "/" + Integer.toString(seq) + "/" + urlName;        return url;    }    /**     * Get the URL of the item to register at the URN service     *     * @param item     * @return     */    private String getItemUrl(Item item)    {        String handle = item.getHandle();        if (!baseUrl.endsWith("/"))        {            baseUrl += "/";        }        return baseUrl + handle;    }}