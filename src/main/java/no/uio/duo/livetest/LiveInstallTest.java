package no.uio.duo.livetest;

import no.uio.duo.*;
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

/**
 * Class which can apply a live test to ensure that the DuoEventConsumer is behaving correctly
 *
 */
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

    private List<CSVRecord> testMatrix;

    private Date past = new Date(0);
    private Date now = new Date();
    private Date nearFuture = new Date(3153600000000L);
    private Date farFuture = new Date(31535996400000L);     // has to be set to this specific date, because of rounding oddities in the java Date library


    /**
     * Create a new live install test instance
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
                "anon_read_result",
                "duo.state_installed",
                "duo.state_state",
                "duo.state_grade",
                "duo.state_embargo",
                "duo.state_restrictions"
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
                    record.get("anon_read_result"),
                    record.get("duo.state_installed"),
                    record.get("duo.state_state"),
                    record.get("duo.state_grade"),
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

    /**
     * Run an individual test, with the given parameters
     *
     * @param name
     * @param grade
     * @param embargo
     * @param embargoType
     * @param resultStatus
     * @param originalFiles
     * @param adminFiles
     * @param anonReadResult
     * @throws Exception
     */
    private void runTest(String name,
                         String grade,
                         String embargo,
                         String embargoType,
                         String resultStatus,
                         int originalFiles,
                         int adminFiles,
                         String anonReadResult,
                         String stateInstalled,
                         String stateState,
                         String stateGrade,
                         String stateEmbargo,
                         String stateRestrictions
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
        this.checkAndPrint(name, archived.item, readMap, resultStatus, originalFiles, adminFiles, stateInstalled, stateState, stateGrade, stateEmbargo, stateRestrictions);

        this.record(name, workflow.item, archived.item);

        this.testEnd(name);
    }

    private void checkAndPrint(String testName, Item item, Map<Integer, String> anonReadResults, String resultStatus, int originalFiles, int adminFiles,
                               String stateInstalled,
                               String stateState,
                               String stateGrade,
                               String stateEmbargo,
                               String stateRestrictions)
            throws Exception
    {
        String error = this.checkItem(item, anonReadResults, resultStatus, originalFiles, adminFiles, stateInstalled, stateState, stateGrade, stateEmbargo, stateRestrictions);
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
        if (!"none".equals(grade))
        {
            String gradeField = ConfigurationManager.getProperty("studentweb", "grade.field");
            DCValue gradeDcv = mm.makeDCValue(gradeField, null);
            item.addMetadata(gradeDcv.schema, gradeDcv.element, gradeDcv.qualifier, null, grade);
        }

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
        // this.clearResourcePolicies(item);

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

    private String checkItem(Item item, Map<Integer, String> anonReadResults, String resultStatus, int originalFiles, int adminFiles,
                             String stateInstalled,
                             String stateState,
                             String stateGrade,
                             String stateEmbargo,
                             String stateRestrictions)
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

        /*
        * state is disabled for now, do not check
        *
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

        if ("none".equals(stateGrade))
        {
            if (ds.getGrade() != null)
            {
                return "Item should have no duo.state grade, but has grade=" + ds.getGrade();
            }
        }
        else if ("pass".equals(stateGrade))
        {
            if (!"pass".equals(ds.getGrade()))
            {
                return "Item should have duo.state grade=pass, but has grade=" + ds.getGrade();
            }
        }
        else if ("fail".equals(stateGrade))
        {
            if (!"fail".equals(ds.getGrade()))
            {
                return "Item should have duo.state grade=fail, but has grade=" + ds.getGrade();
            }
        }


        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
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
        else if (stateRestrictions != null)
        {
            if (!stateRestrictions.equals(ds.getRestrictions()))
            {
                return "Item should have duo.state restrictions=" + stateRestrictions + " but has " + ds.getRestrictions();
            }
        }
        */

        return null;
    }

}
