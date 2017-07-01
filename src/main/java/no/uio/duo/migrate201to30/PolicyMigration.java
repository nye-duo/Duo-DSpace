package no.uio.duo.migrate201to30;

import no.uio.duo.BitstreamIterator;
import no.uio.duo.DuoConstants;
import no.uio.duo.policy.ContextualBitstream;
import no.uio.duo.policy.PolicyPatternManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.handle.HandleManager;
import org.dspace.workflow.WorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

import java.io.IOException;
import java.sql.SQLException;

public class PolicyMigration
{
    public static void main(String[] args)
            throws Exception
    {
        CommandLineParser parser = new PosixParser();
        Options options = new Options();
        options.addOption("e", "eperson", true, "EPerson to do the migration as");
        CommandLine line = parser.parse(options, args);

        if (!line.hasOption("e"))
        {
            System.out.println("Please provide an eperson email with the -e argument");
            System.exit(0);
        }

        PolicyMigration pm = new PolicyMigration(line.getOptionValue("e"));
        pm.migrate();
    }

    private Context context;
    private EPerson eperson;
    private int itemCount = 0;

    public PolicyMigration(String epersonEmail)
            throws SQLException, AuthorizeException
    {
        this.context = new Context();

        this.eperson = EPerson.findByEmail(this.context, epersonEmail);
        this.context.setCurrentUser(this.eperson);
    }

    public void migrate()
            throws Exception
    {
        try
        {
            this.doDSpace();
        }
        catch (Exception e)
        {
            this.context.abort();
            throw e;
        }
        finally
        {
            if (this.context.isValid())
            {
                this.context.complete();
            }
        }

        System.out.println("Processed " + this.itemCount + " Items");
    }

    public void doDSpace()
            throws Exception
    {
        Community[] comms = Community.findAllTop(this.context);
        for (int i = 0; i < comms.length; i++)
        {
            this.doCommunity(comms[i]);
        }
    }

    public void doCommunity(String handle)
            throws Exception
    {
        DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
        if (!(dso instanceof Community))
        {
            throw new Exception(handle + " does not resolve to a Community");
        }
        this.doCommunity((Community) dso);
    }

    public void doCommunity(Community community)
            throws Exception
    {
        Community[] comms = community.getSubcommunities();
        for (int i = 0; i < comms.length; i++)
        {
            this.doCommunity(comms[i]);
        }

        Collection[] cols = community.getCollections();
        for (int i = 0; i < cols.length; i++)
        {
            this.doCollection(cols[i]);
        }
    }

    public void doCollection(String handle)
            throws SQLException, Exception
    {
        DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
        if (!(dso instanceof Collection))
        {
            throw new Exception(handle + " does not resolve to a Collection");
        }
        this.doCollection((Collection) dso);
    }

    public void doCollection(Collection collection)
            throws SQLException, Exception
    {
        // do all the items in the collection, withdrawn or not
        ItemIterator ii = collection.getAllItems();
        while (ii.hasNext())
        {
            Item item = ii.next();
            this.doItem(item);
        }

        // do all the items in the collection's workflow (both normal and XML)
        WorkflowItem[] wfis = WorkflowItem.findByCollection(this.context, collection);
        for (int i = 0; i < wfis.length; i++)
        {
            WorkflowItem wfi = wfis[i];
            Item item = wfi.getItem();
            this.doItem(item);
        }

        XmlWorkflowItem[] xwfis = XmlWorkflowItem.findByCollection(this.context, collection);
        for (int i = 0; i < xwfis.length; i++)
        {
            XmlWorkflowItem xwfi = xwfis[i];
            Item item = xwfi.getItem();
            this.doItem(item);
        }

        // do all the items in the collection's workspace
        WorkspaceItem[] wsis = WorkspaceItem.findByCollection(this.context, collection);
        for (int i = 0; i < wsis.length; i++)
        {
            WorkspaceItem wsi = wsis[i];
            Item item = wsi.getItem();
            this.doItem(item);
        }
    }

    public void doItem(String handle)
            throws SQLException, Exception
    {
        DSpaceObject dso = HandleManager.resolveToObject(this.context, handle);
        if (!(dso instanceof Item))
        {
            throw new Exception(handle + " does not resolve to an Item");
        }
        this.doItem((Item) dso);
    }

    public void doWorkflowItem(int wfid)
            throws SQLException, Exception
    {
        InProgressSubmission wfi = null;
        wfi = WorkflowItem.find(this.context, wfid);
        if (wfi == null)
        {
            wfi = XmlWorkflowItem.find(this.context, wfid);
        }
        if (wfi == null)
        {
            throw new Exception(Integer.toString(wfid) + " does not resolve to a workflow item");
        }
        this.doItem(wfi.getItem());
    }

    public void doWorkspaceItem(int wsid)
            throws SQLException, Exception
    {
        WorkspaceItem wsi = WorkspaceItem.find(this.context, wsid);
        if (wsi == null)
        {
            throw new Exception(Integer.toString(wsid) + " does not resolve to a workspace item");
        }
        this.doItem(wsi.getItem());
    }

    public void doItem(Item item)
            throws SQLException, AuthorizeException, IOException, Exception
    {
        // first move all of the bitstreams
        Bundle secondary = null;
        Bundle admin = null;

        BitstreamIterator bsi = new BitstreamIterator(item);
        while (bsi.hasNext())
        {
            ContextualBitstream cb = bsi.next();
            Bundle bundle = cb.getBundle();
            String name = bundle.getName();

            Bitstream bs = cb.getBitstream();

            if ("SECONDARY".equals(name))
            {
                if (secondary == null)
                {
                    secondary = item.createBundle(DuoConstants.SECONDARY_BUNDLE);
                }
                secondary.addBitstream(bs);
                bundle.removeBitstream(bs);
            }
            else if ("SECONDARY_CLOSED".equals(name))
            {
                if (secondary == null)
                {
                    secondary = item.createBundle(DuoConstants.SECONDARY_BUNDLE);
                }
                secondary.addBitstream(bs);
                bundle.removeBitstream(bs);
            }
            else if ("RESTRICTED".equals(name))
            {
                if (admin == null)
                {
                    admin = item.createBundle(DuoConstants.ADMIN_BUNDLE);
                }
                admin.addBitstream(bs);
                bundle.removeBitstream(bs);
            }
            else if ("METADATA".equals(name))
            {
                if (admin == null)
                {
                    admin = item.createBundle(DuoConstants.ADMIN_BUNDLE);
                }
                admin.addBitstream(bs);
                bundle.removeBitstream(bs);
            }
            else if ("LICENSE".equals(name))
            {
                bundle.removeBitstream(bs);
            }
            else if ("DELETED".equals(name))
            {
                bundle.removeBitstream(bs);
            }

            if (bundle.getBitstreams().length == 0)
            {
                item.removeBundle(bundle);
            }
        }

        // after the first stage, commit the context
        this.context.commit();

        // apply the policy pattern manager to items in the repository
        if (item.isArchived())
        {
            PolicyPatternManager ppm = new PolicyPatternManager();
            ppm.applyToExistingItem(item, this.context);

            // after this final stage, commit the context again
            this.context.commit();
        }

        this.itemCount++;

    }
}
