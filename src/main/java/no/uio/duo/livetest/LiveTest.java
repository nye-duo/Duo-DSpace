package no.uio.duo.livetest;

import org.dspace.content.*;
import org.dspace.content.Collection;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

import java.io.*;
import java.util.*;

/**
 * Superclass providing services to utilities which want to allow testing of a live DSpace instance
 *
 */
public class LiveTest
{
    protected Context context;
    protected EPerson eperson;
    protected Collection collection;
    protected File bitstream;
    protected String baseUrl;
    protected String outPath;

    protected List<CheckReport> checkList = new ArrayList<CheckReport>();
    protected List<Map<String, String>> failures = new ArrayList<Map<String, String>>();

    protected int from = -1;
    protected int to = -1;

    public class CheckReport
    {
        public String testName;
        public int reference = -1;
        public int changed = -1;
        public String referenceHandle;
        public String changedHandle;
    }

    public class ItemMakeRecord
    {
        public Item item;
        public List<Integer> bitstreamIDs = new ArrayList<Integer>();
    }

    /**
     * Create a new live test utility.  This will set up the context and the current user eperson
     *
     * @param epersonEmail
     * @throws Exception
     */
    public LiveTest(String epersonEmail)
            throws Exception
    {
        this.context = new Context();

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);
    }

    /**
     * Set the range of tests to be run
     *
     * @param from
     * @param to
     */
    public void setRange(int from, int to)
    {
        this.from = from;
        this.to = to;
    }

    protected void testStart(String name)
    {
        System.out.println("-- Running test " + name);
    }

    protected void record(String name, Item reference, Item actOn)
    {
        CheckReport report = new CheckReport();
        report.testName = name;
        report.reference = reference.getID();
        report.changed = actOn.getID();
        this.checkList.add(report);
    }

    protected void testEnd(String name)
            throws Exception
    {
        // commit the context, and record the references to the before and after items
        this.context.commit();
        System.out.println("-- Finished test " + name);
        System.out.println("\n");
    }

    protected void outputCheckList()
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

    protected void outputFailures()
    {
        for (Map<String, String> pair : this.failures)
        {
            for (String key : pair.keySet())
            {
                System.out.println(key + " - " + pair.get(key));
            }
        }
    }

    /**
     * Make a basic test collection
     *
     * @return
     * @throws Exception
     */
    protected Collection makeCollection()
            throws Exception
    {
        Community community = Community.create(null, this.context);
        community.setMetadata("name", "Test Community " + community.getID());
        community.update();

        Collection collection = community.createCollection();
        collection.setMetadata("name", "Test Collection " + collection.getID());
        collection.update();

        this.context.commit();

        System.out.println("Created community with id " + community.getID() + "; handle " + community.getHandle());
        System.out.println("Created collection with id " + collection.getID() + "; handle " + collection.getHandle());

        return collection;
    }

    /**
     * Create a bundle on the item with the given name
     * @param item
     * @param bundle
     * @return
     * @throws Exception
     */
    protected Bundle makeBundle(Item item, String bundle)
            throws Exception
    {
        return item.createBundle(bundle);
    }

    /**
     * Make a bitstream from this.bitstream on the given item int he given bundle
     *
     * @param item
     * @param bundle
     * @return
     * @throws Exception
     */
    protected Bitstream makeBitstream(Item item, String bundle)
            throws Exception
    {
        return this.makeBitstream(item, bundle, 1);
    }

    /**
     * Make a bitstream from this.bitstream on the given item in the given bundle, identified by
     * some sequential integer identifier (this will appear in the filename)
     *
     * @param item
     * @param bundle
     * @param ident
     * @return
     * @throws Exception
     */
    protected Bitstream makeBitstream(Item item, String bundle, int ident)
            throws Exception
    {
        InputStream originalFile = new FileInputStream(this.bitstream);
        Bundle[] bundles = item.getBundles();

        Bundle container = null;
        for (Bundle b : bundles)
        {
            if (b.getName().equals(bundle))
            {
                container = b;
                break;
            }
        }

        if (container == null)
        {
            container = item.createBundle(bundle);
        }

        Bitstream bs = container.createBitstream(originalFile);
        bs.setName(bundle + "file" + ident + ".txt");
        bs.update();
        return bs;
    }

    protected Date correctForTimeZone(Date date)
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
