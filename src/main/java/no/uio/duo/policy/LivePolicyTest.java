package no.uio.duo.policy;

import no.uio.duo.BitstreamIterator;
// import no.uio.duo.DuoState;
import no.uio.duo.MetadataManager;
import no.uio.duo.WorkflowManagerWrapper;
import no.uio.duo.livetest.LiveTest;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.*;
import org.dspace.content.Collection;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * <p>This file is to run against a live DSpace for the purposes of testing the code.</p>
 *
 * <p><strong>DO NOT RUN THIS AGAINST A PRODUCTION SYSTEM</strong></p>
 *
 * <p>This mitigates the fact that it is nearly impossible to cleanly unit test DSpace, especially
 * when writing add-ons like this one.</p>
 *
 * <p>Install this in your test DSpace, then run the main method of the class, and it will execute a suite
 * of tests.  The DSpace will not be in a clean state after.</p>
 */
public class LivePolicyTest extends LiveTest
{
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();

        Options options = new Options();
        options.addOption("e", "eperson", true, "EPerson to do all the submits as");
        options.addOption("b", "bistream", true, "File path to bitstream to use for testing");
        options.addOption("u", "url", true, "Interface base url");
        options.addOption("m", "matrix", true, "Path to test matrix file");
        options.addOption("o", "out", true, "Path to file to output manual check results to");
        options.addOption("t", "test", true, "test number or range of numbers to run");
        options.addOption("w", "workspace", false, "workspace mode - leave the reference item in the workspace");
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
        if (!line.hasOption("m"))
        {
            System.out.println("Please provide a path to the test matrix file with the -m option");
            System.exit(0);
        }
        if (!line.hasOption("o"))
        {
            System.out.println("Please provide a path to the output file with the -o option");
            System.exit(0);
        }

        boolean workspace = line.hasOption("w");

        LivePolicyTest lpt = new LivePolicyTest(line.getOptionValue("e"), line.getOptionValue("b"), line.getOptionValue("u"), line.getOptionValue("m"), line.getOptionValue("o"), workspace);

        if (line.hasOption("-t"))
        {
            String tests = line.getOptionValue("t");
            String[] bits = tests.split("-");
            String from = bits[0];
            String to = bits[0];
            if (bits.length > 1)
            {
                to = bits[1];
            }
            lpt.setRange(Integer.parseInt(from), Integer.parseInt(to));
        }
        lpt.runAll();
    }

    private PolicyPatternManager policyManager;
    private String baseUrl;
    private List<CSVRecord> testMatrix;
    private String outPath;

    private boolean workspaceMode = false;

    private Date past = new Date(0);
    private Date now = new Date();
    private Date nearFuture = new Date(3153600000000L);
    private Date farFuture = new Date(31535996400000L);     // has to be set to this specific date, because of rounding oddities in the java Date library

    public LivePolicyTest(String epersonEmail, String bitstreamPath, String baseUrl, String matrixPath, String outPath, boolean workspace)
            throws Exception
    {
        super(epersonEmail);

        System.out.println("===========================================");
        System.out.println("== Starting up                           ==");
        System.out.println("===========================================");

        this.workspaceMode = workspace;
        this.baseUrl = baseUrl;
        this.bitstream = new File(bitstreamPath);
        this.outPath = outPath;

        Reader in = new FileReader(matrixPath);
        CSVParser csv = CSVFormat.DEFAULT.withHeader(
                "name",
                "embargo",
                "anon_read_1",
                "anon_read_2",
                "admin_file",
                "admin_read",
                "item_type",
                "anon_read_1_result",
                "anon_read_2_result",
                "metadata",
                "duo.state_installed",
                "duo.state_state",
                "duo.state_embargo",
                "duo.state_restrictions",
                "notes"
            ).parse(in);
        this.testMatrix = csv.getRecords();

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

        int idx = 0;
        for (CSVRecord record : this.testMatrix)
        {
            // check we're not looking at the header row
            if (record.get("name").equals("name"))
            {
                continue;
            }

            idx++;
            if (!((this.from == -1 || idx >= this.from) &&
                    (this.to == -1 || idx <= this.to)))
            {
                continue;
            }

            // otherwise, run the test
            boolean adminFile = record.get("admin_file").equals("yes");
            boolean adminRead = record.get("admin_read").equals("yes");

            List<String> anonReads = new ArrayList<String>();
            if (!"".equals(record.get("anon_read_1")))
            {
                anonReads.add(record.get("anon_read_1"));
            }
            if (!"".equals(record.get("anon_read_2")))
            {
                anonReads.add(record.get("anon_read_2"));
            }

            List<String> readResults = new ArrayList<String>();
            if (!"".equals(record.get("anon_read_1_result")))
            {
                readResults.add(record.get("anon_read_1_result"));
            }
            if (!"".equals(record.get("anon_read_2_result")))
            {
                readResults.add(record.get("anon_read_2_result"));
            }

            this.runTest(
                    record.get("name"),
                    record.get("embargo"),
                    anonReads,
                    adminFile,
                    adminRead,
                    record.get("item_type"),
                    readResults,
                    record.get("metadata"),
                    record.get("duo.state_installed"),
                    record.get("duo.state_state"),
                    record.get("duo.state_embargo"),
                    record.get("duo.state_restrictions")
            );
        }

        System.out.println("===========================================");
        System.out.println("== All Tests complete                    ==");
        System.out.println("===========================================");
        System.out.println("\n\n");

        System.out.println("===========================================");
        System.out.println("== Items and Comparisons                 ==");
        System.out.println("===========================================");

        this.outputCheckList();

        System.out.println("\n\n");
        System.out.println("===========================================");
        System.out.println("== Test Failures                         ==");
        System.out.println("===========================================");

        this.outputFailures();
    }

    /////////////////////////////////////////////////
    // test running infrastructure

    private void runTest(String name,
                         String embargoDate,
                         List<String> anonReads,
                         boolean adminFile,
                         boolean adminRead,
                         String type,
                         List<String> anonReadResults,
                         String metadataResult,
                         String stateInstalled,
                         String stateState,
                         String stateEmbargo,
                         String stateRestrictions
                        )
            throws Exception
    {
        this.testStart(name);

        // prep the reference and action item
        String state = this.workspaceMode ? "workspace" : "archive";
        ItemMakeRecord reference = this.makeItem(embargoDate, anonReads, adminFile, adminRead, state);
        ItemMakeRecord actOn = this.makeItem(embargoDate, anonReads, adminFile, adminRead, "archive");

        // run the operation
        if ("existing".equals(type))
        {
            this.policyManager.applyToExistingItem(actOn.item, this.context);
        }
        else if ("new".equals(type))
        {
            this.policyManager.applyToNewItem(actOn.item, this.context);
        }

        // make a map from bitstream id to expected anonRead results
        Map<Integer, String> readMap = new HashMap<Integer, String>();
        for (int i = 0; i < actOn.bitstreamIDs.size(); i++)
        {
            readMap.put(actOn.bitstreamIDs.get(i), anonReadResults.get(i));
        }

        // check the item for appropriate policies
        this.checkAndPrint(name, actOn.item, readMap, metadataResult, stateInstalled, stateState, stateEmbargo, stateRestrictions);

        this.record(name, reference.item, actOn.item);

        this.testEnd(name);
    }

    private void checkAndPrint(String testName, Item item, Map<Integer, String> anonReadResults, String metadataResult,
                               String stateInstalled,
                               String stateState,
                               String stateEmbargo,
                               String stateRestrictions)
            throws Exception
    {
        String error = this.checkItem(item, anonReadResults, metadataResult, stateInstalled, stateState, stateEmbargo, stateRestrictions);
        if (error != null)
        {
            Map<String, String> errorRecord = new HashMap<String, String>();
            errorRecord.put(testName, error);
            this.failures.add(errorRecord);

            System.out.println("ASSERTION ERROR");
            System.out.println(error);
        }
        else
        {
            System.out.println("Automated checks passed");
        }
    }

    protected void record(String name, Item reference, Item actOn)
    {
        CheckReport report = new CheckReport();
        report.testName = name;
        report.referenceHandle = reference.getHandle();
        report.reference = reference.getID();
        report.changedHandle = actOn.getHandle();
        this.checkList.add(report);
    }

    /////////////////////////////////////////////////
    // utilities for making test data

    private ItemMakeRecord makeItem(String embargoDate, List<String> anonReads, boolean adminFile, boolean adminRead, String state)
            throws Exception
    {
        // make and print the information string
        String anonReadOut = "";
        for (String ar : anonReads)
        {
            if (!"".equals(anonReadOut))
            {
                anonReadOut += ",";
            }
            anonReadOut += ar;
        }
        System.out.println("Making test item with Embargo Date:" + embargoDate + "; anon READ: " + anonReadOut + "; admin READ: " + adminRead);

        ItemMakeRecord result = new ItemMakeRecord();

        // make the item in the collection
        WorkspaceItem wsi = WorkspaceItem.create(this.context, this.collection, false);
        Item item = wsi.getItem();

        if ("archive".equals(state))
        {
            WorkflowManagerWrapper.startWithoutNotify(this.context, wsi);
            item = Item.find(this.context, item.getID());
        }

        item.addMetadata("dc", "title", null, null, "Item ID " + item.getID());

        // set the embargo date
        String ed = null;
        SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");
        if ("past".equals(embargoDate))
        {
            // set to the start of the unix epoch
            ed = sdf.format(this.past);
        }
        else if ("present".equals(embargoDate))
        {
            // set to today
            ed = sdf.format(this.now);
        }
        else if ("future".equals(embargoDate))
        {
            // set in the far future (around 2970 or something)
            ed = sdf.format(this.farFuture);
        }
        else if ("near_future".equals(embargoDate))
        {
            // set in the near future (around 2170 or something)
            ed = sdf.format(this.nearFuture);
        }
        else if ("far_future".equals(embargoDate))
        {
            // set in the far future (around 2970 or something)
            ed = sdf.format(this.farFuture);
        }
        else if ("none".equals(embargoDate))
        {
            // don't set an embargo date
        }

        if (ed != null)
        {
            String md = ConfigurationManager.getProperty("embargo.field.terms");
            MetadataManager mm = new MetadataManager();
            DCValue dcv = mm.makeDCValue(md, null);
            item.addMetadata(dcv.schema, dcv.element, dcv.qualifier, null, ed);
        }

        // add some bitstreams to the item
        List<Bitstream> originals = new ArrayList<Bitstream>();
        //
        // first one or more in the ORIGINAL bundle
        int idx = 1;
        for (String ar : anonReads)
        {
            Bitstream original = this.makeBitstream(item, "ORIGINAL", idx++);
            originals.add(original);
            result.bitstreamIDs.add(original.getID());
        }

        // then the ADMIN bundle if required
        if (adminFile)
        {
            InputStream adminFileStream = new FileInputStream(this.bitstream);
            Bitstream admin = item.createSingleBitstream(adminFileStream, "ADMIN");
            admin.setName("adminfile.txt");
            admin.update();
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

        for (int i = 0; i < originals.size(); i++)
        {
            Bitstream original = originals.get(i);
            String anonRead = anonReads.get(i);

            if (adminRead)
            {
                ResourcePolicy arp = ResourcePolicy.create(this.context);
                arp.setAction(Constants.READ);
                arp.setGroup(Group.find(context, 1));
                arp.setResource(original);
                arp.setResourceType(Constants.BITSTREAM);
                arp.update();
            }

            // create the anonymous read policies for each of the bitstreams
            Date rsd = null;
            boolean unbound = false;
            if ("past".equals(anonRead))
            {
                // set the start date to the start of the unix epoch
                rsd = this.past;
            }
            else if ("present".equals(anonRead))
            {
                // set the start date to today
                rsd = this.now;
            }
            else if ("future".equals(anonRead))
            {
                // set in the far future (around 2970 or something)
                rsd = this.farFuture;
            }
            else if ("near_future".equals(anonRead))
            {
                // set in the near future (around 2170 or something)
                rsd = this.nearFuture;
            }
            else if ("far_future".equals(anonRead))
            {
                // set in the far future (around 2970 or something)
                rsd = this.farFuture;
            }
            else if ("unbound".equals(anonRead))
            {
                // don't set a date, just leave it unbound
                unbound = true;
            }
            else if ("none".equals(anonRead))
            {
                // don't set an anonymous read policy
            }

            if (rsd != null || unbound)
            {
                ResourcePolicy rp = ResourcePolicy.create(this.context);
                rp.setAction(Constants.READ);
                rp.setGroup(Group.find(context, 0));
                rp.setResource(original);
                rp.setResourceType(Constants.BITSTREAM);
                if (rsd != null)
                {
                    rp.setStartDate(rsd);
                }
                rp.update();
            }
        }

        item.update();
        this.context.commit();

        System.out.println("Created item with id " + item.getID());

        result.item = item;
        return result;
    }

    private String checkItem(Item item, Map<Integer, String> anonReadResults, String metadataResult,
                             String stateInstalled,
                             String stateState,
                             String stateEmbargo,
                             String stateRestrictions)
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

        // check all the bundles/bitstreams, and ensure that they have the right policies
        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            ContextualBitstream cbs = bsi.next();
            Bundle bundle = cbs.getBundle();
            Bitstream bitstream = cbs.getBitstream();
            List<ResourcePolicy> existing = AuthorizeManager.getPolicies(this.context, bitstream);
            String anonRead = anonReadResults.get(bitstream.getID());

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
                start = this.correctForTimeZone(start);

                // go through all possible anonRead states and evaluate the policy against them
                if ("past".equals(anonRead))
                {
                    if (start == null)
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle does not have a start date, but it should be in the past";
                    }
                    else if (!start.before(this.now))
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date which is not in the past, but it should be";
                    }
                }
                else if ("present".equals(anonRead))
                {
                    if (start == null)
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle does not have a start date, but it should be in the present";
                    }
                    else if (!start.equals(this.now))
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date, but it is not in the present";
                    }
                }
                else if ("future".equals(anonRead))
                {
                    if (start == null)
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle does not have a start date, but it should be in the future";
                    }
                    else if (!start.after(this.now))
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date, but it should be in the future";
                    }
                }
                else if ("near_future".equals(anonRead))
                {
                    if (start == null)
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle does not have a start date, but it should be in the future";
                    }
                    else if (!start.after(this.now))
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date, but it should be in the future";
                    }
                    else if (!start.equals(this.nearFuture))
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date in the far future (" + start.getTime() + "), but it should be in the near future (" + this.nearFuture.getTime() + ")";
                    }
                }
                else if ("far_future".equals(anonRead))
                {
                    if (start == null)
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle does not have a start date, but it should be in the future";
                    }
                    else if (!start.after(this.now))
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date, but it should be in the future";
                    }
                    else if (!start.equals(this.farFuture))
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date in the near future (" + start.getTime() + "), but it should be in the far future (" + this.farFuture.getTime() + ")";
                    }
                }
                else if ("unbound".equals(anonRead))
                {
                    // if we expect an unbound READ, but we have one with a start date, this is wrong
                    if (start != null)
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date, when it should be unbound";
                    }
                }
                else if ("none".equals(anonRead))
                {
                    // I don't think this happens, cross that bridge when we come to it
                }
            }
        }

        // check the embargo metadata to see if it matches expectations
        String liftDateField = ConfigurationManager.getProperty("embargo.field.lift");
        DCValue[] dcvs = item.getMetadata(liftDateField);
        String val = null;
        Date date = null;
        if (dcvs.length > 0)
        {
            val = dcvs[0].value;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (val != null)
        {
            date = sdf.parse(val);
        }
        date = this.correctForTimeZone(date);

        if ("none".equals(metadataResult))
        {
            if (val != null)
            {
                return "Item should have had no embargo metadata, had: " + val;
            }
        }
        else if ("past".equals(metadataResult))
        {
            if (val == null || date == null)
            {
                return "Item should have past embargo metadata, but date could not be found or could not be parsed";
            }
            if (date.equals(this.now) || date.after(this.now)) {
                return "Item should have a past embargo date, but was equal to or later than now";
            }
        }
        else if ("present".equals(metadataResult))
        {
            if (val == null || date == null)
            {
                return "Item should have present embargo metadata, but date could not be found or could not be parsed";
            }
            if (!date.equals(this.now)) {
                return "Item should have a present embargo date, but was either later than or earlier than now";
            }
        }
        else if ("future".equals(metadataResult))
        {
            if (val == null || date == null)
            {
                return "Item should have future embargo metadata, but date could not be found or could not be parsed";
            }
            else if (!date.after(this.now))
            {
                return "Item should have future embargo metadata, had: " + val;
            }
        }
        else if ("near_future".equals(metadataResult))
        {
            if (val == null || date == null)
            {
                return "Item should have (near) future embargo metadata, but date could not be found or could not be parsed";
            }
            else if (!date.equals(this.nearFuture))
            {
                return "Item should have (near) future embargo metadata, had: " + val;
            }
        }
        else if ("far_future".equals(metadataResult))
        {
            if (val == null || date == null)
            {
                return "Item should have (far) future embargo metadata, but date could not be found or could not be parsed";
            }
            else if (!date.equals(this.farFuture))
            {
                return "Item should have (far) future embargo metadata, had: " + val;
            }
        }

        /*
         * state is disabled for now - do not check
        // check the duo state metadata to ensure it matches up
        DuoState ds = new DuoState(item);

        if ("false".equals(stateInstalled))
        {
            if (ds.isInstalled())
            {
                return "Item should have duo.state installed=false/missing but has installed=true";
            }
        }
        else if ("true".equals(stateInstalled))
        {
            if (!ds.isInstalled())
            {
                return "Item should have duo.state installed=true but has installed=false/missing";
            }
        }

        if ("archived".equals(stateState))
        {
            if (!"archived".equals(ds.getState()))
            {
                return "Item should have duo.state state=archived, but has state=" + ds.getState();
            }
        }
        else if ("withdrawn".equals(stateState))
        {
            if (!"withdrawn".equals(ds.getState()))
            {
                return "Item should have duo.state state=withdrawn, but has state=" + ds.getState();
            }
        }
        else if ("workflow".equals(stateState))
        {
            if (!"workflow".equals(ds.getState()))
            {
                return "Item should have duo.state state=workflow, but has state=" + ds.getState();
            }
        }

        String seds = ds.getEmbargo();
        Date sed = null;
        if (seds != null)
        {
            sed = sdf.parse(seds);
        }
        sed = this.correctForTimeZone(sed);

        if ("none".equals(stateEmbargo))
        {
            if (seds != null)
            {
                return "Item should have had no duo.state embargo metadata, had: " + seds;
            }
        }
        else if ("past".equals(stateEmbargo))
        {
            if (seds == null || sed == null)
            {
                return "Item should have past duo.state embargo metadata, but date could not be found or could not be parsed";
            }
            if (sed.equals(this.now) || sed.after(this.now)) {
                return "Item should have a past duo.state embargo date, but was equal to or later than now";
            }
        }
        else if ("present".equals(stateEmbargo))
        {
            if (seds == null || sed == null)
            {
                return "Item should have present duo.state embargo metadata, but date could not be found or could not be parsed";
            }
            if (!sed.equals(this.now)) {
                return "Item should have a present duo.state embargo date, but was either later than or earlier than now";
            }
        }
        else if ("future".equals(stateEmbargo))
        {
            if (seds == null || sed == null)
            {
                return "Item should have future duo.state embargo metadata, but date could not be found or could not be parsed";
            }
            else if (!sed.after(this.now))
            {
                return "Item should have future duo.state embargo metadata, had: " + seds;
            }
        }
        else if ("near_future".equals(stateEmbargo))
        {
            if (seds == null || sed == null)
            {
                return "Item should have (near) future duo.state embargo metadata, but date could not be found or could not be parsed";
            }
            else if (!sed.equals(this.nearFuture))
            {
                return "Item should have (near) future duo.state embargo metadata, had: " + seds;
            }
        }
        else if ("far_future".equals(stateEmbargo))
        {
            if (seds == null || sed == null)
            {
                return "Item should have (far) future duo.state embargo metadata, but date could not be found or could not be parsed";
            }
            else if (!sed.equals(this.farFuture))
            {
                return "Item should have (far) future duo.state embargo metadata, had: " + seds;
            }
        }

        if ("none".equals(stateRestrictions))
        {
            if (ds.getRestrictions() != null)
            {
                return "Item should have no duo.state restrictions, but has " + ds.getRestrictions();
            }
        }
        */

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

    protected void outputCheckList()
            throws Exception
    {
        String csv = "Test Name,Reference Item,Affected Item";
        for (CheckReport report : this.checkList)
        {
            String reference = this.baseUrl + "/handle/" + report.referenceHandle;
            if (report.referenceHandle == null)
            {
                reference = this.baseUrl + "/admin/item?itemID=" + report.reference;
            }
            String changed = this.baseUrl + "/handle/" + report.changedHandle;
            String row = report.testName + "," + reference + "," + changed;
            System.out.println(row);
            csv += "\n" + row;
        }

        Writer out = new FileWriter(this.outPath);
        out.write(csv);
        out.flush();
        out.close();
    }
}
