package no.uio.duo;

import org.apache.log4j.Logger;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.dspace.event.ConsumerProfile;
import org.dspace.event.Dispatcher;
import org.dspace.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A near-clone of the BasicDispatcher with some cunning-ness/hackery built in
 * to circumvent the concurrent modification exceptions the basicdispatcher is
 * prone to.  Real solution to this issue is in DSpace 3.0, so this code will
 * go obsolete when Duo upgrades to that version; hence the ridiculous class
 * name :)
 */
@Deprecated
public class DSpace18FixedDispatcher extends Dispatcher
{

    public DSpace18FixedDispatcher(String name)
    {
        super(name);
    }

    /** log4j category */
    private static Logger log = Logger.getLogger(DSpace18FixedDispatcher.class);

    public void addConsumerProfile(ConsumerProfile cp)
            throws IllegalArgumentException
    {
        if (consumers.containsKey(cp.getName()))
        {
            throw new IllegalArgumentException(
                    "This dispatcher already has a consumer named \""
                            + cp.getName() + "\"");
        }

        consumers.put(cp.getName(), cp);

        if (log.isDebugEnabled())
        {
            int n = 0;
            for (Iterator i = cp.getFilters().iterator(); i.hasNext(); ++n)
            {
                int f[] = (int[]) i.next();
                log.debug("Adding Consumer=\"" + cp.getName() + "\", instance="
                        + cp.getConsumer().toString() + ", filter["
                        + String.valueOf(n) + "]=(ObjMask="
                        + String.valueOf(f[Event.SUBJECT_MASK])
                        + ", EventMask=" + String.valueOf(f[Event.EVENT_MASK])
                        + ")");
            }
        }
    }

    /**
     * Dispatch all events added to this Context according to configured
     * consumers.
     *
     * @param ctx
     *            the execution context
     */
    public void dispatch(Context ctx)
    {
        if (!consumers.isEmpty())
        {
            // just keep trying this until the event list is null or 0
            while (true)
            {
                // make a shallow copy of the events in the context, so that we don't
                // get bitten by concurrent modification exceptions
                List<Event> shallow = new ArrayList<Event>(ctx.getEvents());

                // make our synchronised list over that copy (why?)
                List<Event> events = Collections.synchronizedList(shallow);

                // now empty the context's list of events - so any new events fired by the tasks
                // below will start filling up an empty event list again
                List<Event> orig = ctx.getEvents();
                orig.removeAll(orig);

                // our exit condition from this loop - have we got any events to process?
                if (events == null || events.size() == 0)
                {
                    return;
                }

                if (log.isDebugEnabled())
                {
                    log.debug("Processing queue of "
                            + String.valueOf(events.size()) + " events.");
                }

                // transaction identifier applies to all events created in
                // this context for the current transaction. Prefix it with
                // some letters so RDF readers don't mistake it for an integer.
                String tid = "TX" + Utils.generateKey();

                for (Event event : events)
                {
                    event.setDispatcher(getIdentifier());
                    event.setTransactionID(tid);

                    if (log.isDebugEnabled())
                    {
                        log.debug("Iterating over "
                                + String.valueOf(consumers.values().size())
                                + " consumers...");
                    }

                    for (Iterator ci = consumers.values().iterator(); ci.hasNext();)
                    {
                        ConsumerProfile cp = (ConsumerProfile) ci.next();

                        if (event.pass(cp.getFilters()))
                        {
                            if (log.isDebugEnabled())
                            {
                                log.debug("Sending event to \"" + cp.getName()
                                        + "\": " + event.toString());
                            }

                            try
                            {
                                cp.getConsumer().consume(ctx, event);

                                // Record that the event has been consumed by this
                                // consumer
                                event.setBitSet(cp.getName());
                            }
                            catch (Exception e)
                            {
                                log.error("Consumer(\"" + cp.getName()
                                        + "\").consume threw: " + e.toString(), e);
                            }
                        }

                    }
                }

                // Call end on the consumers that got synchronous events.
                for (Iterator ci = consumers.values().iterator(); ci.hasNext();)
                {
                    ConsumerProfile cp = (ConsumerProfile) ci.next();
                    if (cp != null)
                    {
                        if (log.isDebugEnabled())
                        {
                            log.debug("Calling end for consumer \"" + cp.getName()
                                    + "\"");
                        }

                        try
                        {
                            cp.getConsumer().end(ctx);
                        }
                        catch (Exception e)
                        {
                            log.error("Error in Consumer(\"" + cp.getName()
                                    + "\").end: " + e.toString(), e);
                        }
                    }
                }
            }
        }
    }

}