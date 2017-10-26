package no.uio.duo.livetest;

import org.dspace.content.*;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

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
}
