package no.uio.duo;

import javax.xml.namespace.QName;

public class DuoConstants
{
    /** metadata namespace of the StudentWeb incoming metadata */
    public static String FS_NAMESPACE = "http://studentweb.no/terms/";

    /** QName object representing the metadata element the StudentWeb grade is held in */
    public static QName GRADE_QNAME = new QName(FS_NAMESPACE, "grade");

    /** QName object representing the metadata element the StudentWeb Embargo End Date is held in */
    public static QName EMBARGO_END_DATE_QNAME = new QName(FS_NAMESPACE, "embargoEndDate");

    /** QName object representing the metadata element the StudentWeb Embargo Type is held in */
    public static QName EMBARGO_TYPE_QNAME = new QName(FS_NAMESPACE, "embargoType");

    /** Name of the DSpace ORIGINAL bundle */
    public static String ORIGINAL_BUNDLE = "ORIGINAL";

    /** Name of the DSpace SECONDARY bundle */
    public static String SECONDARY_BUNDLE = "DUO_2NDRY_CLOSED";
    
    /** Name of the Duo Admin bundle */
    public static String ADMIN_BUNDLE = "DUO_ADMIN";

    /** Name of the file where metadata should be stored */
    public static String METADATA_FILE = "metadata.xml";

    /** "open" access condition for items coming from StudentWeb */
    public static String OPEN = "open";

    /** "closed" access condition for items coming from StudentWeb */
    public static String CLOSED = "closed";

    /** "pass" grade string for items coming from StudentWeb */
    public static String FS_PASS = "pass";

    /** embargo type which indicates an item is "restricted" from StudentWeb */
    public static String FS_RESTRICTED = "restricted";
}
