package no.uio.duo.policy;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;

/**
 * Class which represents the unique combination of a bitstream and the bundle that it appears
 * in
 */
public class ContextualBitstream
{
    private Bitstream bitstream = null;
    private Bundle bundle = null;

    public ContextualBitstream(Bitstream bitstream, Bundle bundle)
    {
        this.bitstream = bitstream;
        this.bundle = bundle;
    }

    public Bitstream getBitstream()
    {
        return bitstream;
    }

    public void setBitstream(Bitstream bitstream)
    {
        this.bitstream = bitstream;
    }

    public Bundle getBundle()
    {
        return bundle;
    }

    public void setBundle(Bundle bundle)
    {
        this.bundle = bundle;
    }
}
