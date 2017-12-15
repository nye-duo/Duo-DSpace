package no.uio.duo.livetest;

import no.uio.duo.BitstreamIterator;
import no.uio.duo.DuoConstants;
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
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

import java.io.*;
import java.util.*;

public class LiveReinstateTest extends LiveTest
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

        LiveReinstateTest lrt = new LiveReinstateTest(line.getOptionValue("e"), line.getOptionValue("b"), line.getOptionValue("u"), line.getOptionValue("m"), line.getOptionValue("o"));

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
            lrt.setRange(Integer.parseInt(from), Integer.parseInt(to));
        }
        lrt.runAll();
    }

    // private String baseUrl;
    private List<CSVRecord> testMatrix;
    // private String outPath;

    private Date past = new Date(0);
    private Date now = new Date();
    private Date nearFuture = new Date(3153600000000L);
    private Date farFuture = new Date(31535996400000L);     // has to be set to this specific date, because of rounding oddities in the java Date library


    /**
     * Create a new live reinstate test instance
     *
     * @param epersonEmail
     * @throws Exception
     */
    public LiveReinstateTest(String epersonEmail, String bitstreamPath, String baseUrl, String matrixPath, String outPath)
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

    private ItemMakeRecord makeItem(String grade, String embargo, String embargoType, String state)
            throws Exception
    {
        // make and print the information string
        System.out.println("Making test item with Grade:" + grade + "; Embargo: " + embargo + "; Embargo Type: " + embargoType + "; State: " + state);

        ItemMakeRecord result = new ItemMakeRecord();

        // make the item in the collection
        WorkspaceItem wsi = WorkspaceItem.create(this.context, this.collection, false);
        Item item = wsi.getItem();

        // this.applyMetadata(item, grade, embargo, embargoType);

        // add a bitstream to the item
        List<Bitstream> originals = new ArrayList<Bitstream>();
        Bitstream original = this.makeBitstream(item, "ORIGINAL", 1);
        originals.add(original);
        result.bitstreamIDs.add(original.getID());

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
}
