package no.uio.duo;

import no.uio.duo.policy.PolicyPatternManager;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

/**
 * <p>Event consumer for Duo which responds to Item Installs.</p>
 *
 * <p>This consumer will first determine if an item has embargo metadata attached to it.  If not
 * it will apply the default access policies to the item as per the Duo requirements.  To do this
 * it delegates to the PolicyPatternManager.</p>
 *
 * <p><strong>Configuration</strong></p>
 *
 * <p>To enable the install consumer, in dspace.cfg we need to add the name of the consumer
 * ("duo") to the list of available consumers, and specify the class name (this class) and
 * the conditions on which it will be triggered:</p>
 *
 * <pre>
 * event.dispatcher.default.consumers = search, browse, eperson, harvester, duo
   event.consumer.duo.class = no.uio.duo.DuoInstallConsumer
   event.consumer.duo.filters = Item+Install
 * </pre>
 */
public class DuoInstallConsumer implements Consumer
{
    /**
     * Initialise the install consumer.  Does nothing.
     * @throws Exception
     */
    public void initialize() throws Exception { }

    /**
     * End the consumer.  Does nothing.
     *
     * @param context
     * @throws Exception
     */
    public void end(Context context) throws Exception { }

    /**
     * Finish the consumer.  Does nothing.
     *
     * @param context
     * @throws Exception
     */
    public void finish(Context context) throws Exception { }

    /**
     * Consume an Install event.  If the item is being installed and it is not
     * embargoed then we want to set the item's policies.  See {@link PolicyPatternManager}
     * for details
     *
     * @param context
     * @param event
     * @throws Exception
     */
    public void consume(Context context, Event event) throws Exception
    {
        Item item = (Item) event.getSubject(context);

        // check to see if the item has a StudentWeb grade of "fail"
        if (this.isFail(context, item))
        {
            // if so, withdraw the item
            item.withdraw();
            return;
        }

        // now simply apply the policy pattern, which will take the appropriate action
        // depending on the state of the item at the point we pick it up
        PolicyPatternManager ppm = new PolicyPatternManager();
        ppm.applyToNewItem(item, context);
    }

    private boolean isFail(Context context, Item item)
    {
        String gradeField = ConfigurationManager.getProperty("studentweb", "grade.field");
        DCValue[] dcvs = item.getMetadata(gradeField);
        for (DCValue dcv : dcvs)
        {
            if ("fail".equals(dcv.value.trim()))
            {
                return true;
            }
        }
        return false;
    }

}
