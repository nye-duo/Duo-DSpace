package no.uio.duo.migrate201to30;

import edu.emory.mathcs.backport.java.util.Arrays;
import no.uio.duo.TraverseDSpace;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;

import java.io.FileWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Script which analyses the entire holdings of a DSpace instance and reports on those items which
 * have more than one file in the ORIGINAL bundle.  It also serialises the policies each of those files
 * has and outputs them so they can be easily compared.  Produces a CSV for easy analysis.
 */
public class MultiFileAnalyser extends TraverseDSpace
{
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();
        Options options = new Options();
        options.addOption("e", "eperson", true, "EPerson to run the script as");
        options.addOption("o", "out", true, "Path to file to output results to");
        options.addOption("v", "verbose", false, "Produce verbose output");
        options.addOption("n", "nohandle", false, "Include items with no handle in the output");
        options.addOption("b", "bitstreams", false, "Include full bitstream information");
        CommandLine line = parser.parse(options, args);

        if (!line.hasOption("e"))
        {
            System.out.println("Please provide an eperson email with the -e argument");
            System.exit(0);
        }

        if (!line.hasOption("o"))
        {
            System.out.println("Please provide a path to the output file with the -o option");
            System.exit(0);
        }

        MultiFileAnalyser mfa = new MultiFileAnalyser(line.getOptionValue("e"), line.getOptionValue("o"), line.hasOption("n"), line.hasOption("b"), line.hasOption("v"));
        mfa.analyse();
        mfa.report();
    }

    class ReportRow
    {
        public int id;
        public String handle;
        public int fileCount;
        public List<String> policies;
    }

    private String outPath;
    private int maxBitstreamCount = 0;
    private List<ReportRow> rows = new ArrayList<ReportRow>();
    private boolean verbose = false;
    private boolean noHandle = false;
    private boolean bitstreams = false;

    public MultiFileAnalyser(String epersonEmail, String outPath, boolean noHandle, boolean bitstreams, boolean verbose)
            throws Exception
    {
        super(epersonEmail);
        this.outPath = outPath;
        this.verbose = verbose;
        this.noHandle = noHandle;
        this.bitstreams = bitstreams;
    }

    public void analyse()
            throws Exception
    {
        this.doDSpace();
        this.output();
    }

    /**
     * Report on a single item
     *
     * @param item
     * @throws Exception
     */
    public void processItem(Item item)
            throws Exception
    {
        if (item.getHandle() == null && !this.noHandle)
        {
            return;
        }

        List<Bitstream> bitstreams = new ArrayList<Bitstream>();

        // get all the bitstreams in the original bundle (ensuring we handle the case where there
        // are two original bundles)
        Bundle[] originals = item.getBundles("ORIGINAL");
        for (Bundle bundle : originals)
        {
            Bitstream[] bss = bundle.getBitstreams();
            bitstreams.addAll(Arrays.asList(bss));
        }

        // no need to consider items with only one bitstream
        if (bitstreams.size() <= 1)
        {
            return;
        }

        ReportRow row = new ReportRow();
        row.id = item.getID();
        row.handle = item.getHandle();
        row.fileCount = bitstreams.size();

        if (this.bitstreams)
        {
            List<String> descs = this.describePolicies(bitstreams);
            row.policies = descs;
        }

        this.rows.add(row);

        // keep track of the largest number of bitstreams, which will matter when we produce the output
        if (bitstreams.size() > this.maxBitstreamCount)
        {
            this.maxBitstreamCount = bitstreams.size();
        }
    }

    private List<String> describePolicies(List<Bitstream> bitstreams)
            throws Exception
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List<String> descs = new ArrayList<String>();

        for (Bitstream bs : bitstreams)
        {
            StringBuilder desc = new StringBuilder();
            desc.append("Policies for bitstream ").append(bs.getID()).append(" name ").append(bs.getName()).append("\n");

            List<ResourcePolicy> all = AuthorizeManager.getPolicies(this.context, bs);
            for (ResourcePolicy rp : all)
            {
                String action = rp.getActionText();
                int epersonId = rp.getEPersonID();
                int groupId = rp.getGroupID();
                Date start = rp.getStartDate();

                StringBuilder sb = new StringBuilder();
                sb.append(action);
                if (epersonId != -1)
                {
                    sb.append(" by eperson ").append(epersonId);
                }

                if (groupId != -1)
                {
                    sb.append(" by group ").append(groupId);
                }

                if (start != null)
                {
                    sb.append(" starting ").append(sdf.format(start));
                }
                desc.append(sb).append("\n");
            }

            descs.add(desc.toString());
        }

        return descs;
    }

    private void output()
            throws Exception
    {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Handle,Number of Files");
        if (this.bitstreams)
        {
            for (int i = 0; i < this.maxBitstreamCount; i++)
            {
                csv.append(",Bitstream ").append(i + 1);
            }
        }
        csv.append("\n");

        for (ReportRow row : this.rows)
        {
            csv.append(row.id).append(",").append(row.handle).append(",").append(row.fileCount);
            if (this.bitstreams)
            {
                for (String policy : row.policies)
                {
                    policy = policy.replace("\"", "\\\"");
                    csv.append(",\"").append(policy).append("\"");
                }
                for (int i = row.policies.size(); i < this.maxBitstreamCount; i++)
                {
                    csv.append(",");
                }
            }
            csv.append("\n");
        }

        if (this.verbose)
        {
            System.out.println(csv.toString());
        }

        Writer out = new FileWriter(this.outPath);
        out.write(csv.toString());
        out.flush();
        out.close();
    }
}
