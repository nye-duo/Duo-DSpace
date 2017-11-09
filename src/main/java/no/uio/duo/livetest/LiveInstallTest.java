package no.uio.duo.livetest;

import no.uio.duo.BitstreamIterator;
import no.uio.duo.DuoConstants;
import no.uio.duo.MetadataManager;
import no.uio.duo.WorkflowManagerWrapper;
import no.uio.duo.policy.ContextualBitstream;
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
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LiveInstallTest extends LiveTest
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

        LiveInstallTest lit = new LiveInstallTest(line.getOptionValue("e"), line.getOptionValue("b"), line.getOptionValue("u"), line.getOptionValue("m"), line.getOptionValue("o"));

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
            lit.setRange(Integer.parseInt(from), Integer.parseInt(to));
        }
        lit.runAll();
    }

    class CheckReport
    {
        public String testName;
        public int reference;
        public int changed;
    }

    class ItemMakeRecord
    {
        public Item item;
        List<Integer> bitstreamIDs = new ArrayList<Integer>();
    }

    private List<CheckReport> checkList = new ArrayList<CheckReport>();
    private String baseUrl;
    private List<CSVRecord> testMatrix;
    private List<Map<String, String>> failures = new ArrayList<Map<String, String>>();
    private String outPath;

    private int from = -1;
    private int to = -1;

    private Date past = new Date(0);
    private Date now = new Date();
    private Date nearFuture = new Date(3153600000000L);
    private Date farFuture = new Date(31535996400000L);     // has to be set to this specific date, because of rounding oddities in the java Date library


    /**
     * Create a new metadata cleanup test instance
     *
     * @param epersonEmail
     * @throws Exception
     */
    public LiveInstallTest(String epersonEmail, String bitstreamPath, String baseUrl, String matrixPath, String outPath)
            throws Exception
    {
        super(epersonEmail);

        System.out.println("===========================================");
        System.out.println("== Starting up                           ==");
        System.out.println("===========================================");

        this.baseUrl = baseUrl;
        this.bitstream = new File(bitstreamPath);
        this.outPath = outPath;

        Reader in = new FileReader(matrixPath);
        CSVParser csv = CSVFormat.DEFAULT.withHeader(
                "name",
                "grade",
                "embargo",
                "type",
                "result_status",
                "original_files",
                "admin_files",
                "anon_read_result"
        ).parse(in);
        this.testMatrix = csv.getRecords();

        this.context = new Context();
        this.context.setIgnoreAuthorization(true);

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);

        this.collection = this.makeCollection();

        System.out.println("===========================================");
        System.out.println("== Startup complete                      ==");
        System.out.println("===========================================");
        System.out.println("\n\n");
    }

    public void setRange(int from, int to)
    {
        this.from = from;
        this.to = to;
    }

    /**
     * Run the utility, which will produce a single item with HTML in the metadata
     *
     * @throws Exception
     */
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

            this.runTest(
                    record.get("name"),
                    record.get("grade"),
                    record.get("embargo"),
                    record.get("type"),
                    record.get("result_status"),
                    Integer.parseInt(record.get("original_files")),
                    Integer.parseInt(record.get("admin_files")),
                    record.get("anon_read_result")
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
                         String grade,
                         String embargo,
                         String embargoType,
                         String resultStatus,
                         int originalFiles,
                         int adminFiles,
                         String anonReadResult
    )
            throws Exception
    {
        this.testStart(name);

        // create two items

        // the first is the reference, it is in the submission state in the workflow
        ItemMakeRecord workflow = this.makeItem(grade, embargo, embargoType, "workspace");

        // the second is the item in the archive, which will get processed by the install consumer
        ItemMakeRecord archived = this.makeItem(grade, embargo, embargoType, "archive");

        // make a map from bitstream id to expected anonRead results
        Map<Integer, String> readMap = new HashMap<Integer, String>();
        for (int i = 0; i < archived.bitstreamIDs.size(); i++)
        {
            readMap.put(archived.bitstreamIDs.get(i), anonReadResult);
        }

        // check the item for appropriate policies
        this.checkAndPrint(name, archived.item, readMap, resultStatus, originalFiles, adminFiles);

        this.record(name, workflow.item, archived.item);

        this.testEnd(name);
    }

    private void testStart(String name)
    {
        System.out.println("-- Running test " + name);
    }

    private void checkAndPrint(String testName, Item item, Map<Integer, String> anonReadResults, String resultStatus, int originalFiles, int adminFiles)
            throws Exception
    {
        String error = this.checkItem(item, anonReadResults, resultStatus, originalFiles, adminFiles);
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

    private void record(String name, Item reference, Item actOn)
    {
        CheckReport report = new CheckReport();
        report.testName = name;
        report.reference = reference.getID();
        report.changed = actOn.getID();
        this.checkList.add(report);
    }

    private void testEnd(String name)
            throws Exception
    {
        // commit the context, and record the references to the before and after items
        this.context.commit();
        System.out.println("-- Finished test " + name);
        System.out.println("\n");
    }

    private ItemMakeRecord makeItem(String grade, String embargo, String embargoType, String state)
            throws Exception
    {
        // make and print the information string
        System.out.println("Making test item with Grade:" + grade + "; Embargo: " + embargo + "; Embargo Type: " + embargoType + "; State: " + state);

        ItemMakeRecord result = new ItemMakeRecord();

        // make the item in the collection
        WorkspaceItem wsi = WorkspaceItem.create(this.context, this.collection, false);
        Item item = wsi.getItem();

        MetadataManager mm = new MetadataManager();

        // set a convenient title
        item.addMetadata("dc", "title", null, null, "Item ID " + item.getID());

        // set the grade
        String gradeField = ConfigurationManager.getProperty("studentweb", "grade.field");
        DCValue gradeDcv = mm.makeDCValue(gradeField, null);
        item.addMetadata(gradeDcv.schema, gradeDcv.element, gradeDcv.qualifier, null, grade);

        // set the embargo date
        String ed = null;
        SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");
        if ("past".equals(embargo))
        {
            // set to the start of the unix epoch
            ed = sdf.format(this.past);
        }
        else if ("present".equals(embargo))
        {
            // set to today
            ed = sdf.format(this.now);
        }
        else if ("future".equals(embargo))
        {
            // set in the far future (around 2970 or something)
            ed = sdf.format(this.farFuture);
        }
        else if ("near_future".equals(embargo))
        {
            // set in the near future (around 2170 or something)
            ed = sdf.format(this.nearFuture);
        }
        else if ("far_future".equals(embargo))
        {
            // set in the far future (around 2970 or something)
            ed = sdf.format(this.farFuture);
        }
        else if ("none".equals(embargo))
        {
            // don't set an embargo date
        }

        if (ed != null)
        {
            String embargoField = ConfigurationManager.getProperty("embargo.field.terms");
            DCValue embargoDcv = mm.makeDCValue(embargoField, null);
            item.addMetadata(embargoDcv.schema, embargoDcv.element, embargoDcv.qualifier, null, ed);
        }

        // add the embargo type
        if (!"none".equals(embargoType))
        {
            String typeField = ConfigurationManager.getProperty("studentweb", "embargo-type.field");
            DCValue typeDcv = mm.makeDCValue(typeField, null);
            item.addMetadata(typeDcv.schema, typeDcv.element, typeDcv.qualifier, null, embargoType);
        }

        // add a bitstream to the item
        List<Bitstream> originals = new ArrayList<Bitstream>();
        Bitstream original = this.makeBitstream(item, "ORIGINAL", 1);
        originals.add(original);
        result.bitstreamIDs.add(original.getID());

        // clear out any existing resource policies
        this.clearResourcePolicies(item);

        if ("archive".equals(state))
        {
            WorkflowManagerWrapper.startWithoutNotify(this.context, wsi);
            item = Item.find(this.context, item.getID());
        }

        item.update();
        this.context.commit();

        System.out.println("Created item with id " + item.getID());

        result.item = item;
        return result;
    }

    private void outputCheckList()
            throws Exception
    {
        String csv = "Test Name,Reference Item,Affected Item";
        for (CheckReport report : this.checkList)
        {
            String reference = this.baseUrl + "/admin/item?itemID=" + report.reference;
            String changed = this.baseUrl + "/admin/item?itemID=" + report.changed;
            String row = report.testName + "," + reference + "," + changed;
            System.out.println(row);
            csv += "\n" + row;
        }

        Writer out = new FileWriter(this.outPath);
        out.write(csv);
        out.flush();
        out.close();
    }

    private void outputFailures()
    {
        for (Map<String, String> pair : this.failures)
        {
            for (String key : pair.keySet())
            {
                System.out.println(key + " - " + pair.get(key));
            }
        }
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

    private String checkItem(Item item, Map<Integer, String> anonReadResults, String resultStatus, int originalFiles, int adminFiles)
            throws Exception
    {
        int originals = 0;
        int admins = 0;

        // check all the bundles/bitstreams, and ensure that they have the right policies
        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            ContextualBitstream cbs = bsi.next();
            Bundle bundle = cbs.getBundle();
            Bitstream bitstream = cbs.getBitstream();
            List<ResourcePolicy> existing = AuthorizeManager.getPolicies(this.context, bitstream);
            String anonRead = anonReadResults.get(bitstream.getID());

            if (DuoConstants.ORIGINAL_BUNDLE.equals(bundle.getName()))
            {
                originals++;

                if (existing.size() > 1)
                {
                    return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has two or more policies";
                }
                ResourcePolicy policy = existing.get(0);
                Date start = policy.getStartDate();
                start = this.correctForTimeZone(start);

                // go through all possible anonRead states and evaluate the policy against them
                if ("future".equals(anonRead))
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
                else if ("unbound".equals(anonRead))
                {
                    // if we expect an unbound READ, but we have one with a start date, this is wrong
                    if (start != null)
                    {
                        return "Bitstream " + bitstream.getName() + " in ORIGINAL bundle has a start date, when it should be unbound";
                    }
                }
                else if ("n/a".equals(anonRead))
                {
                    // no need to check anything
                }
            }
            else if (DuoConstants.ADMIN_BUNDLE.equals(bundle.getName()))
            {
                admins++;
            }
        }

        // check the result status
        if ("archived".equals(resultStatus))
        {
            if (!item.isArchived())
            {
                return "Item should be archived, but it is not";
            }
        }
        else if ("withdrawn".equals(resultStatus))
        {
            if (!item.isWithdrawn())
            {
                return "Item should be withdrawn, but it is not";
            }
        }

        if (originalFiles != originals)
        {
            return "There should be " + originalFiles + " files in " + DuoConstants.ORIGINAL_BUNDLE + " but there are " + originals;
        }

        if (adminFiles != admins)
        {
            return "There should be " + adminFiles + " files in " + DuoConstants.ADMIN_BUNDLE + " but there are " + admins;
        }

        return null;
    }

    private Date correctForTimeZone(Date date)
    {
        // FIXME: this still doesn't work quite right for timezones west of Greenwich, but I'm not sure why
        // not going to work harder to fix it, as it won't be run in that timezone, and it's only a test script anyway
        //
        // but just in case you are interested, test 15 for example, yields an apparent difference between the resource policy
        // time and the far future time of 42 hours, and I have no idea how that's possible.
        if (date != null)
        {
            //System.out.println(date.getTime());

            // This bit of code corrects for the issue that arises once dates have been round-tripped into the database without the time portion or any time
            // zone information, such that those dates can still be compared to the absolute near/far future dates used in testing.
            //
            // first we get the local timezone and get the offset from UTC.
            TimeZone tz = Calendar.getInstance().getTimeZone();
            int offset = tz.getRawOffset();
            //System.out.println(offset);

            // if the offset is less than 0, this is a timezone west of Greenwich.  Since all the dates coming back from the database are without
            // time information, they are all treated as if they represent UTC.  This means dates actually in -ve UTC will appear to be further in the
            // future, not back in the past.  That is, a -6 UTC needs to actually be interpreted as a +18 UTC.  That's what this next bit of code
            // calculates.
            if (offset < 0)
            {
                offset = 86400000 + offset;
            }
            //System.out.println(offset);

            // now create a new date from the new number of milliseconds
            long startCompare = date.getTime() + (long) offset;
            date = new Date(startCompare);

            //System.out.println(date.getTime());
        }

        return date;
    }
}