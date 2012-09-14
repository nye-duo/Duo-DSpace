package no.uio.duo;

import javax.xml.namespace.QName;

public class DuoConstants
{
    public static String FS_NAMESPACE = "http://studentweb.no/terms/";

    public static QName GRADE_QNAME = new QName(FS_NAMESPACE, "grade");
    public static QName EMBARGO_END_DATE_QNAME = new QName(FS_NAMESPACE, "embargoEndDate");
    public static QName EMBARGO_TYPE_QNAME = new QName(FS_NAMESPACE, "embargoType");

    public static String ORIGINAL_BUNDLE = "ORIGINAL";
    public static String SECONDARY_BUNDLE = "SECONDARY";
    public static String SECONDARY_RESTRICTED_BUNDLE = "SECONDARY_CLOSED"; // must be under 16 characters
    public static String METADATA_BUNDLE = "METADATA";
    public static String LICENSE_BUNDLE = "LICENSE";

    public static String METADATA_FILE = "metadata.xml";

    public static String OPEN = "open";
    public static String CLOSED = "closed";

    public static String ADMIN_GROUP = "Administrator";
    public static String ANON_GROUP = "Anonymous";
}
