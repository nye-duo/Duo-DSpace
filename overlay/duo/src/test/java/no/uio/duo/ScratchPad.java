package no.uio.duo;

import org.apache.abdera.Abdera;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Element;
import org.apache.abdera.model.Entry;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ScratchPad
{
    @Test
    public void scratch()
            throws Exception
    {
        InputStream metadata = this.getClass().getClassLoader().getResourceAsStream("metadata.xml");

        Abdera ab = new Abdera();
        Document<Element> doc = ab.getParserFactory().getParser().parse(metadata);

        Element element = doc.getRoot();

        Entry entry = ab.newEntry();
        entry.addExtension(element);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        entry.writeTo(baos);
        System.out.println(baos.toString());
    }
}