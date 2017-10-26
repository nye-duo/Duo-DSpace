package no.uio.duo.livetest;

import org.dspace.content.*;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class LiveTest
{
    protected Context context;
    protected EPerson eperson;
    protected Collection collection;
    protected File bitstream;

    public LiveTest(String epersonEmail)
            throws Exception
    {
        this.context = new Context();

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);
    }

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

    protected Bundle makeBundle(Item item, String bundle)
            throws Exception
    {
        return item.createBundle(bundle);
    }

    protected Bitstream makeBitstream(Item item, String bundle)
            throws Exception
    {
        return this.makeBitstream(item, bundle, 1);
    }

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
