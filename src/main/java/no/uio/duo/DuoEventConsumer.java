package no.uio.duo;

import no.uio.duo.policy.PolicyApplicationFilter;
import no.uio.duo.policy.PolicyPatternManager;
import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

/**
 * <p>Event consumer for Duo which responds to Item Installs and Modify_Metadata.</p>
 *
 * <p>This consumer will first determine if an item has embargo metadata attached to it.  If not
 * it will apply the default access policies to the item as per the Duo requirements.  To do this
 * it delegates to the PolicyPatternManager.</p>
 *
 * <p><strong>Configuration</strong></p>
 *
 * <p>To enable the event consumer, in dspace.cfg we need to add the name of the consumer
 * ("duo") to the list of available consumers, and specify the class name (this class) and
 * the conditions on which it will be triggered:</p>
 *
 * <pre>
 * event.dispatcher.default.consumers = search, browse, eperson, harvester, duo
   event.consumer.duo.class = no.uio.duo.DuoEventConsumer
   event.consumer.duo.filters = Item+Install|Modify_Metadata
 * </pre>
 */
public class DuoEventConsumer implements Consumer
{
    /** log4j logger */
    private static Logger log = Logger.getLogger(DuoEventConsumer.class);

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

        if (Event.INSTALL == event.getEventType())
        {
            log.info("Processing Install of Item " + item.getID());
            this.onInstall(context, item);
        }
        else if (Event.MODIFY_METADATA == event.getEventType())
        {
            log.info("Processing Modify Metadata of Item " + item.getID());
            this.onModifyMetadata(context, item);
        }
        else if (Event.MODIFY == event.getEventType() && "REINSTATE".equals(event.getDetail()))
        {
            log.info("Processing Reinstate of Item " + item.getID());
            this.onReinstate(context, item);
        }
    }

    private void onInstall(Context context, Item item)
            throws Exception
    {
        if (FSRestrictionManager.consumes(item))
        {
            log.info("FSRestrictionManager will apply restrictions to item " + item.getID());
            FSRestrictionManager fsrm = new FSRestrictionManager();
            fsrm.onInstall(context, item);
        }
        else
        {
            // simply apply the policy pattern, which will take the appropriate action
            // depending on the state of the item at the point we pick it up
            if (PolicyApplicationFilter.allow(context, item))
            {
                log.info("Applying standard policy pattern to installed Item " + item.getID());
                PolicyPatternManager ppm = new PolicyPatternManager();
                ppm.applyToNewItem(item, context);
            }
            else
            {
                log.info("FSRestrictionManager and PolicyPatternManager not applicable; Not taking any action on installed Item " + item.getID());
            }
        }
    }

    private void onModifyMetadata(Context context, Item item)
            throws Exception
    {
        if (FSRestrictionManager.consumes(item))
        {
            log.info("FSRestrictionManager will apply restrictions to item " + item.getID());
            FSRestrictionManager fsrm = new FSRestrictionManager();
            fsrm.onModifyMetadata(context, item);
        }
        else
        {
            // simply apply the policy pattern, which will take the appropriate action
            // depending on the state of the item at the point we pick it up
            if (PolicyApplicationFilter.allow(context, item))
            {
                log.info("Applying standard policy pattern to modified Item " + item.getID());
                PolicyPatternManager ppm = new PolicyPatternManager();
                ppm.applyToExistingItem(item, context);
            }
            else
            {
                log.info("FSRestrictionManager and PolicyPatternManager not applicable; Not taking any action on modified Item " + item.getID());
            }
        }
    }

    private void onReinstate(Context context, Item item)
            throws Exception
    {
        if (FSRestrictionManager.consumes(item))
        {
            log.info("FSRestrictionManager will apply restrictions to item " + item.getID());
            FSRestrictionManager fsrm = new FSRestrictionManager();
            fsrm.onReinstate(context, item);
        }
        else
        {
            // simply apply the policy pattern, which will take the appropriate action
            // depending on the state of the item at the point we pick it up
            if (PolicyApplicationFilter.allow(context, item))
            {
                log.info("Applying standard policy pattern to reinstated Item " + item.getID());
                PolicyPatternManager ppm = new PolicyPatternManager();
                ppm.applyToExistingItem(item, context);      // Note that although the item is not new, reinstating it is treating it like a newly submitted item
            }
            else
            {
                log.info("FSRestrictionManager and PolicyPatternManager not applicable; Not taking any action on reinstated Item " + item.getID());
            }
        }
    }
}
