package no.uio.duo;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.sword2.DSpaceSwordException;
import org.dspace.sword2.DepositResult;
import org.dspace.sword2.SwordEntryIngester;
import org.dspace.sword2.VerboseDescription;
import org.swordapp.server.Deposit;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;

public class FSEntryIngester implements SwordEntryIngester
{
    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dSpaceObject, VerboseDescription verboseDescription)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        return null;
    }

    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dSpaceObject, VerboseDescription verboseDescription, DepositResult depositResult, boolean b)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        return null;
    }
}
