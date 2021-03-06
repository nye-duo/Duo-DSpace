package no.uio.duo;

import no.uio.duo.policy.PolicyApplicationFilter;
import no.uio.duo.policy.PolicyPatternManager;
import org.apache.log4j.Logger;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

/**
 * <p>Event consumer for Duo which responds to Item Installs and Modify (and potentially Modify_Metadata too).</p>
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
   event.consumer.duo.filters = Item+Install|Modify
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

    /**
     * When an item is installed, run this method.  This method will only work on an item
     * that does not have the duo.state installed=true flag.  When it runs it will either delegate
     * to the FSRestrictionManager, if the item is covered by FS rules, or it will call the PolicyPatternManager
     *
     * In either case, when complete, this method will set duo.state installed=true and any other relevant
     * state information on the item.
     *
     * @param context
     * @param item
     * @throws Exception
     */
    private void onInstall(Context context, Item item)
            throws Exception
    {
        /*
        * Re-enable DuoState if we ever decide to start monitoring on Modify_Metadata
        DuoState state = new DuoState(item);
        if (state.isInstalled())
        {
            log.info("Item " + item.getID() + " is already installed, no need for install consumer to run");
            return;
        }*/

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

        // re-activate if the code at the start of this function is ever re-activated.
        //state.setInstalled(true);
        //state.sychroniseItemState(false);
        item.update();
    }

    /**
     * When an items' metadata is modified, this method will run.
     *
     * This method will only run on items which have been installed by onInstall above.  It will also only run on
     * items where the DuoState has changed in a way that is relevant.
     *
     * When it does run, it will apply the FSRestrictionManager or the PolicyPatternManager as appropriate.
     *
     * On completion, it will re-synchronise the duo.state property with the item's new state.
     *
     * @param context
     * @param item
     * @throws Exception
     */
    private void onModifyMetadata(Context context, Item item)
            throws Exception
    {
        DuoState state = new DuoState(item);
        if (!state.isInstalled())
        {
            log.info("Modify_Metadata on an item not yet installed: " + item.getID() + " - no action taken");
            return;
        }

        if (!state.hasChanged())
        {
            log.info("Item " + item.getID() + " state has not changed since last run of Modify_Metadata - no action taken");
            return;
        }

        if (FSRestrictionManager.consumes(item))
        {
            log.info("FSRestrictionManager will apply restrictions to item " + item.getID());
            FSRestrictionManager fsrm = new FSRestrictionManager();
            fsrm.onModifyMetadata(context, item);

            state.sychroniseItemState(true);
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

                state.sychroniseItemState(true);
            }
            else
            {
                log.info("FSRestrictionManager and PolicyPatternManager not applicable; Not taking any action on modified Item " + item.getID());
            }
        }
    }

    /**
     * When an item is reinstated into the archive from withdrawn, this method will run
     *
     * It will apply the FSRestrictionManager or PolicyPatternManager as appropriate
     *
     * @param context
     * @param item
     * @throws Exception
     */
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
                ppm.applyToExistingItem(item, context);
            }
            else
            {
                log.info("FSRestrictionManager and PolicyPatternManager not applicable; Not taking any action on reinstated Item " + item.getID());
            }
        }
    }
}
