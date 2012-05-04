package no.uio.duo;

import javax.xml.namespace.QName;

public class DuoConstants
{
    public static String FS_NAMESPACE = "http://studentweb.no/terms/";

    public static QName GRADE_QNAME = new QName(FS_NAMESPACE, "grade");
    public static QName EMBARGO_END_DATE_QNAME = new QName(FS_NAMESPACE, "embargoEndDate");
    public static QName EMBARGO_TYPE_QNAME = new QName(FS_NAMESPACE, "embargoType");

    public static String METADATA_BUNDLE = "METADATA";
    public static String METADATA_FILE = "metadata.xml";
}
