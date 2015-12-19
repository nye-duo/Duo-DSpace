package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Class to manage item access policies (and bundles and bitstreams in those items)</p>
 *
 * <p>The Default policies that Duo items have are as follows:</p>
 *
 * <ul>
 * <li>ORIGINAL - Publicly readable</li>
 * <li>LICENSE - Publicly readable</li>
 * <li>METADATA - Administrator only</li>
 * <li>SECONDARY - Publicly readable</li>
 * <li>SECONDARY_CLOSED - Administrator only</li>
 * <li>SWORD - Administrator only</li>
 * </ul>
 *
 */
public class DuoPolicyManager
{
    /**
     * Apply the default access policies to an item (see class documentation for details)
     *
     * @param context
     * @param item
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void setDefaultPolicies(Context context, Item item)
            throws SQLException, AuthorizeException
    {
        String swordBundle = ConfigurationManager.getProperty("swordv2-server", "bundle.name");
        if (swordBundle == null)
        {
            swordBundle = "SWORD";
        }
        this.setReadPolicies(context, item, DuoConstants.ORIGINAL_BUNDLE, DuoConstants.ANON_GROUP, true);
        // no longer working with licences
        // this.setReadPolicies(context, item, DuoConstants.LICENSE_BUNDLE, DuoConstants.ANON_GROUP, true);
        this.setReadPolicies(context, item, DuoConstants.METADATA_BUNDLE, DuoConstants.ADMIN_GROUP, false);
        this.setReadPolicies(context, item, DuoConstants.SECONDARY_BUNDLE, DuoConstants.ANON_GROUP, true);
        this.setReadPolicies(context, item, DuoConstants.SECONDARY_RESTRICTED_BUNDLE, DuoConstants.ADMIN_GROUP, false);
        this.setReadPolicies(context, item, swordBundle, DuoConstants.ADMIN_GROUP, false);
    }

    private void setReadPolicies(Context context, Item item, String bundleName, String groupName, boolean respectDefault)
            throws SQLException, AuthorizeException
    {
        Bundle[] bundles = item.getBundles(bundleName);
        for (Bundle b : bundles)
        {
            boolean cascade = this.doPolicy(context, b, groupName, Constants.BUNDLE, respectDefault);

            if (cascade)
            {
                Bitstream[] bss = b.getBitstreams();
                for (Bitstream bs : bss)
                {
                    this.doPolicy(context, bs, groupName, Constants.BITSTREAM, respectDefault);
                }
            }
        }
    }

    private boolean doPolicy(Context context, DSpaceObject dso, String groupName, int resourceType, boolean respectDefault)
            throws SQLException, AuthorizeException
    {
        List<ResourcePolicy> read = this.getReadPolicies(context, dso);
        if (read.size() > 0 && respectDefault)
        {
            // if there is already read permissions and we are requested to respect
            // the default, then carry on to the next bundle
            return false;
        }
        if (!respectDefault)
        {
            this.removePolicies(read);
        }
        this.setReadPolicy(context, dso, resourceType, groupName);
        return true;
    }

    private void setReadPolicy(Context context, DSpaceObject dso, int resourceType, String groupName)
            throws SQLException, AuthorizeException
    {
        // set a hyper restrictive resource policy for testing purposes
        ResourcePolicy rp = ResourcePolicy.create(context);
        rp.setAction(Constants.READ);
        rp.setGroup(Group.findByName(context, groupName));
        rp.setResource(dso);
        rp.setResourceType(resourceType);
        rp.update();
    }

    private List<ResourcePolicy> getReadPolicies(Context context, DSpaceObject dso)
            throws SQLException
    {
        List<ResourcePolicy> read = new ArrayList<ResourcePolicy>();
        List<ResourcePolicy> all = AuthorizeManager.getPolicies(context, dso);
        for (ResourcePolicy rp : all)
        {
            if (rp.getAction() == Constants.READ)
            {
                read.add(rp);
            }
        }
        return read;
    }

    private void removePolicies(List<ResourcePolicy> policies)
            throws SQLException
    {
        for (ResourcePolicy policy : policies)
        {
            policy.delete();
        }
    }
}
