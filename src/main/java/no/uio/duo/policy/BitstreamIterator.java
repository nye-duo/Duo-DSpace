package no.uio.duo.policy;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BitstreamIterator implements Iterator<ContextualBitstream>
{
    private Item item;
    private List<ContextualBitstream> entries = new ArrayList<ContextualBitstream>();
    private int index = 0;

    public BitstreamIterator(Item item)
            throws SQLException
    {
        this.item = item;
        Bundle[] bundles = this.item.getBundles();
        for (Bundle bundle : bundles)
        {
            Bitstream[] bitstreams = bundle.getBitstreams();
            for (Bitstream bitstream : bitstreams)
            {
                this.entries.add(new ContextualBitstream(bitstream, bundle));
            }
        }
    }

    @Override
    public boolean hasNext()
    {
        return this.entries.size() > this.index;
    }

    @Override
    public ContextualBitstream next()
    {
        return this.entries.get(this.index++);
    }
}
