package no.uio.duo;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.sword2.DSpaceSwordException;
import org.dspace.sword2.SwordEntryDisseminator;
import org.swordapp.server.DepositReceipt;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;

public class DuoEntryDisseminator implements SwordEntryDisseminator
{
    public DepositReceipt disseminate(Context context, Item item, DepositReceipt depositReceipt)
            throws DSpaceSwordException, SwordError, SwordServerException
    {
        return null;
    }
}
