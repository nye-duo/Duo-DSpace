package no.uio.duo.policy;

import no.uio.duo.WorkflowManagerWrapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.*;
import org.dspace.content.Collection;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This file is to run against a live DSpace for the purposes of testing the code.
 *
 * This mitigates the fact that it is nearly impossible to cleanly unit test DSpace, especially
 * when writing add-ons like this one.
 *
 * Install this in your test DSpace, then run the main method of the class, and it will execute a suite
 * of tests.  The DSpace may not be in a clean state after.
 */
public class LivePolicyTest
{
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();

        Options options = new Options();
        options.addOption("e", "eperson", true, "EPerson to do all the submits as");
        options.addOption("b", "bistream", true, "File path to bitstream to use for testing");
        options.addOption("u", "url", true, "Interface base url");
        CommandLine line = parser.parse(options, args);

        if (!line.hasOption("e"))
        {
            System.out.println("Please provide an eperson email with the -e argument");
            System.exit(0);
        }
        if (!line.hasOption("b"))
        {
            System.out.println("Please provide a path to a bitstream to use for testing with the -b option");
            System.exit(0);
        }
        if (!line.hasOption("u"))
        {
            System.out.println("Please provide an interface base url with the -u option");
            System.exit(0);
        }

        LivePolicyTest lpt = new LivePolicyTest(line.getOptionValue("e"), line.getOptionValue("b"), line.getOptionValue("u"));
        lpt.runAll();
    }

    private Context context;
    private Collection collection;
    private EPerson eperson;
    private File bitstream;
    private PolicyPatternManager policyManager;
    private List<Map<String, String>> checkList = new ArrayList<Map<String, String>>();
    private String baseUrl;

    public LivePolicyTest(String epersonEmail, String bitstreamPath, String baseUrl)
            throws Exception
    {
        System.out.println("===========================================");
        System.out.println("== Starting up                           ==");
        System.out.println("===========================================");

        this.baseUrl = baseUrl;
        this.bitstream = new File(bitstreamPath);

        this.context = new Context();
        this.context.setIgnoreAuthorization(true);

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);

        this.collection = this.makeCollection();

        this.policyManager = new PolicyPatternManager();

        System.out.println("===========================================");
        System.out.println("== Startup complete                      ==");
        System.out.println("===========================================");
        System.out.println("\n\n");
    }

    public void runAll()
            throws Exception
    {
        System.out.println("===========================================");
        System.out.println("== Running All Tests                     ==");
        System.out.println("===========================================");

        this.test1PastPastAdmin();

        System.out.println("===========================================");
        System.out.println("== All Tests complete                    ==");
        System.out.println("===========================================");
        System.out.println("\n\n");

        System.out.println("===========================================");
        System.out.println("== Items and Comparisons                 ==");
        System.out.println("===========================================");

        this.outputCheckList();
    }

    /**
     * Embargo Date: past
     * Anon READ: past
     * Admin READ: present
     */
    public void test1PastPastAdmin()
        throws Exception
    {
        System.out.println("-- Running test test1PastPastAdmin");

        Item reference = this.makeItem("past", "past", true);
        Item actOn = this.makeItem("past", "past", true);

        this.policyManager.applyToExistingItem(actOn, this.context);
        String error = this.checkItem(actOn, "unbound");
        if (error != null)
        {
            System.out.println("ASSERTION ERROR");
            System.out.println(error);
        }
        else
        {
            System.out.println("Automated checks passed");
        }

        this.context.commit();

        Map<String, String> compare = new HashMap<String, String>();
        compare.put(reference.getHandle(), actOn.getHandle());
        this.checkList.add(compare);

        System.out.println("-- Finished test test1PastPastAdmin");
        System.out.println("\n");
    }


    /////////////////////////////////////////////////
    // utilities for making test data

    private Collection makeCollection()
            throws Exception
    {
        Community community = Community.create(null, this.context);
        community.setMetadata("name", "Policy Test Community " + community.getID());
        community.update();

        Collection collection = community.createCollection();
        collection.setMetadata("name", "Policy Test Collection " + collection.getID());
        collection.update();

        this.context.commit();

        System.out.println("Created collection with id " + collection.getID());

        return collection;
    }

    private Item makeItem(String embargoDate, String anonRead, boolean adminRead)
            throws Exception
    {
        System.out.println("Making test item with Embargo Date :" + embargoDate + "; anon READ: " + anonRead + "; admin READ: " + adminRead);

        // make the item in the collection
        WorkspaceItem wsi = WorkspaceItem.create(this.context, this.collection, false);
        Item item = wsi.getItem();

        WorkflowManagerWrapper.startWithoutNotify(this.context, wsi);
        item = Item.find(this.context, item.getID());
        item.addMetadata("dc", "title", null, null, "Item ID " + item.getID());

        // add some bitstreams to the item
        InputStream originalFile = new FileInputStream(this.bitstream);
        Bitstream original = item.createSingleBitstream(originalFile, "ORIGINAL");
        original.setName("originalfile.txt");
        original.update();

        InputStream adminFile = new FileInputStream(this.bitstream);
        Bitstream admin = item.createSingleBitstream(adminFile, "ADMIN");
        admin.setName("adminfile.txt");
        admin.update();

        // set the embargo date
        String ed = null;
        if ("past".equals(embargoDate))
        {
            SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");
            ed = sdf.format(new Date(0));
        }

        if (ed != null)
        {
            String md = ConfigurationManager.getProperty("embargo.field.terms");
            DCValue dcv = this.stringToDC(md);
            item.addMetadata(dcv.schema, dcv.element, dcv.qualifier, null, ed);
        }

        // clear out any existing resource policies
        this.clearResourcePolicies(item);

        // make the item itself anon read
        ResourcePolicy irp = ResourcePolicy.create(this.context);
        irp.setAction(Constants.READ);
        irp.setGroup(Group.find(context, 0));
        irp.setResource(item);
        irp.setResourceType(Constants.ITEM);
        irp.update();

        if (adminRead)
        {
            ResourcePolicy arp = ResourcePolicy.create(this.context);
            arp.setAction(Constants.READ);
            arp.setGroup(Group.find(context, 1));
            arp.setResource(original);
            arp.setResourceType(Constants.BITSTREAM);
            arp.update();
        }

        // create an anonymous read policy
        Date rsd = null;
        if ("past".equals(anonRead))
        {
            rsd = new Date(0);
        }

        if (rsd != null)
        {
            BitstreamIterator bsi = new BitstreamIterator(item);
            while (bsi.hasNext())
            {
                Bitstream bitstream = bsi.next().getBitstream();

                ResourcePolicy rp = ResourcePolicy.create(this.context);
                rp.setAction(Constants.READ);
                rp.setGroup(Group.find(context, 0));
                rp.setResource(bitstream);
                rp.setResourceType(Constants.BITSTREAM);
                rp.setStartDate(rsd);
                rp.update();
            }
        }

        item.update();
        this.context.commit();

        System.out.println("Created item with id " + item.getID());

        return item;
    }

    private String checkItem(Item item, String anonRead)
            throws Exception
    {
        // check that there are no bundle policies
        Bundle[] bundles = item.getBundles();
        for (Bundle bundle : bundles)
        {
            List<ResourcePolicy> all = AuthorizeManager.getPolicies(context, bundle);
            if (all.size() > 0)
            {
                return "Bundle " + bundle.getName() + " has one or more policies";
            }
        }

        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            ContextualBitstream cbs = bsi.next();
            Bundle bundle = cbs.getBundle();
            Bitstream bitstream = cbs.getBitstream();
            List<ResourcePolicy> existing = AuthorizeManager.getPolicies(this.context, bitstream);

            if ("ADMIN".equals(bundle.getName()))
            {
                // an admin bundle we expect to have no resource policies
                if (existing.size() > 0)
                {
                    return "Bitstream " + bitstream.getName() + " in ADMIN bundle has one or more policies";
                }
            }
            else
            {
                if (existing.size() > 1)
                {
                    return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has two or more policies";
                }
                ResourcePolicy policy = existing.get(0);
                Date start = policy.getStartDate();

                // if we expect an unbound READ, but we have one with a start date, this is wrong
                if (start != null && "unbound".equals(anonRead))
                {
                    return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date, when it should be unbound";
                }
            }
        }
        return null;
    }

    private void clearResourcePolicies(Item item)
            throws Exception
    {
        List<ResourcePolicy> existing = AuthorizeManager.getPolicies(this.context, item);
        for (ResourcePolicy policy : existing)
        {
            policy.delete();
        }

        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            Bitstream bitstream = bsi.next().getBitstream();
            List<ResourcePolicy> bsPolicy = AuthorizeManager.getPolicies(this.context, bitstream);
            for (ResourcePolicy policy : bsPolicy)
            {
                policy.delete();
            }
        }
    }

    private DCValue stringToDC(String field)
    {
        String[] bits = field.split("\\.");
        DCValue dcv = new DCValue();
        dcv.schema = bits[0];
        dcv.element = bits[1];
        if (bits.length > 2)
        {
            dcv.qualifier = bits[2];
        }
        return dcv;
    }

    private void outputCheckList()
    {
        for (Map<String, String> pair : this.checkList)
        {
            for (String key : pair.keySet())
            {
                System.out.println(this.baseUrl + "/handle/" + key + "\t\t" + this.baseUrl + "/handle/" + pair.get(key));
            }
        }
    }
}
