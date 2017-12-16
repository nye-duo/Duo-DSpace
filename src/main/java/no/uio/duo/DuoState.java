package no.uio.duo;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;

import java.sql.SQLException;

public class DuoState
{
    private MetadataManager metadataManager = new MetadataManager();

    private Item item;
    private String field;
    private StateDetails details;

    private class StateDetails
    {
        public String installed = null;
        public String state = null;
        public String grade = null;
        public String embargo = null;
        public String restrictions = null;
    }

    public DuoState(Item item)
            throws DuoException
    {
        String field = ConfigurationManager.getProperty("duo.state.field");
        if (field == null)
        {
            throw new DuoException("No configuration defined for duo.state.field");
        }

        DCValue[] dcvs = item.getMetadata(field);


        DCValue val = null;
        if (dcvs.length > 0)
        {
            val = dcvs[0];
        }

        this.item = item;
        this.field = field;
        if (val != null)
        {
            StateDetails sd = this.parseSource(val.value);
            this.details = sd;
        }
        else
        {
            this.details = new StateDetails();
        }
    }

    public boolean isInstalled()
    {
        return "true".equals(this.details.installed);
    }

    public void setInstalled(boolean installed)
    {
        this.details.installed = installed ? "true"  : "false";
    }

    public String getState()
    {
        return this.details.state;
    }

    public String getEmbargo()
    {
        return this.details.embargo;
    }

    public String getRestrictions()
    {
        return this.details.restrictions;
    }

    public String getGrade()
    {
        return this.details.grade;
    }

    public boolean hasChanged()
    {
        String state = "workflow";
        if (this.item.isArchived())
        {
            state = "archived";
        }
        else if (this.item.isWithdrawn())
        {
            state = "withdrawn";
        }
        String grade = this.getGradeMD(this.item);
        String emb = this.getRawEmbargoDate(this.item);
        String restriction = this.getRestriction(this.item);

        // check the state for a match
        if (!state.equals(this.details.state))
        {
            return true;
        }

        // check the grade for a match
        if (grade == null && this.details.grade != null)
        {
            return true;
        }
        if (grade != null && this.details.grade == null)
        {
            return true;
        }
        if (grade != null && !grade.equals(this.details.grade))
        {
            return true;
        }

        // check the embargo date for a match
        if (emb == null && this.details.embargo != null)
        {
            return true;
        }
        if (emb != null && this.details.embargo == null)
        {
            return true;
        }
        if (emb != null && !emb.equals(this.details.embargo))
        {
            return true;
        }

        // check the restrictions for a match
        if (restriction == null && this.details.restrictions != null)
        {
            return true;
        }
        if (restriction != null && this.details.restrictions == null)
        {
            return true;
        }
        if (restriction != null && !restriction.equals(this.details.restrictions))
        {
            return true;
        }

        return false;
    }

    public void sychroniseItemState(boolean itemUpdate)
            throws DuoException, SQLException, AuthorizeException
    {
        String state = "workflow";
        if (this.item.isArchived())
        {
            state = "archived";
        }
        else if (this.item.isWithdrawn())
        {
            state = "withdrawn";
        }
        String grade = this.getGradeMD(this.item);
        String emb = this.getRawEmbargoDate(this.item);
        String restriction = this.getRestriction(this.item);
        this.updateState(state, grade, emb, restriction);
        this.recordStateOnItem();
        if (itemUpdate)
        {
            this.item.update();
        }
    }

    public void updateState(String state, String grade, String embargo, String restrictions)
    {
        this.details.state = state;
        this.details.grade = grade;
        this.details.embargo = embargo;
        this.details.restrictions = restrictions;
    }

    public void recordStateOnItem()
            throws DuoException
    {
        DCValue dcv = this.metadataManager.makeDCValue(this.field, null);
        this.item.clearMetadata(dcv.schema, dcv.element, dcv.qualifier, Item.ANY);
        String stateVal = this.serialiseState(this.details);
        this.item.addMetadata(dcv.schema, dcv.element, dcv.qualifier, null, stateVal);
    }

    private String serialiseState(StateDetails sd)
    {
        StringBuilder sb = new StringBuilder();
        if (sd.installed != null)
        {
            sb.append("installed=").append(sd.installed);
        }
        if (sd.state != null)
        {
            if (sb.length() > 0)
            {
                sb.append(";");
            }
            sb.append("state=").append(sd.state);
        }
        if (sd.grade != null)
        {
            if (sb.length() > 0)
            {
                sb.append(";");
            }
            sb.append("grade=").append(sd.grade);
        }
        if (sd.embargo != null)
        {
            if (sb.length() > 0)
            {
                sb.append(";");
            }
            sb.append("embargo=").append(sd.embargo);
        }
        if (sd.restrictions != null)
        {
            if (sb.length() > 0)
            {
                sb.append(";");
            }
            sb.append("restrictions=").append(sd.restrictions);
        }
        return sb.toString();
    }

    private StateDetails parseSource(String source)
    {
        // System.out.println("State source " + source);
        StateDetails details = new StateDetails();

        String[] kvs = source.split(";");
        for (String kv : kvs)
        {
            String[] bits = kv.split("=");
            String key = bits[0];
            String value = bits[1];

            if ("installed".equals(key))
            {
                details.installed = value;
            }
            else if ("state".equals(key))
            {
                details.state = value;
            }
            else if ("grade".equals(key))
            {
                details.grade = value;
            }
            else if ("embargo".equals(key))
            {
                details.embargo = value;
            }
            else if ("restrictions".equals(key))
            {
                details.restrictions = value;
            }
        }

        return details;
    }

    private String getGradeMD(Item item)
    {
        String gradeField = ConfigurationManager.getProperty("studentweb", "grade.field");
        DCValue[] dcvs = item.getMetadata(gradeField);
        if (dcvs.length > 0)
        {
            return dcvs[0].value;
        }
        return null;
    }

    private String getRestriction(Item item)
    {
        String gradeField = ConfigurationManager.getProperty("studentweb", "embargo-type.field");
        DCValue[] dcvs = item.getMetadata(gradeField);
        if (dcvs.length > 0)
        {
            return dcvs[0].value;
        }
        return null;
    }

    private String getRawEmbargoDate(Item item)
    {
        // first check that there is a date
        String liftDateField = ConfigurationManager.getProperty("embargo.field.lift");
        if (liftDateField == null)
        {
            return null;
        }

        // if there is no embargo value, the item isn't embargoed
        DCValue[] embargoes = item.getMetadata(liftDateField);
        if (embargoes.length == 0)
        {
            return null;
        }

        return embargoes[0].value;
    }
}
