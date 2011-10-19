<?xml version="1.0" encoding="utf-8"?>

<xsl:stylesheet
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:fs="http://duo.uio.no/ns/fs/"
        xmlns:dcterms="http://purl.org/dc/terms/"
        xmlns:dim="http://www.dspace.org/xmlns/dspace/dim"
        version="1.0">

    <xsl:template match="text()"></xsl:template>

    <xsl:template match="/fs:metadata/dcterms:title">
    	<dim:dim>
    		<dim:field mdschema="dc" element="title">
    			<xsl:value-of select="normalize-space(.)"/>
    		</dim:field>
    	</dim:dim>
    </xsl:template>

</xsl:stylesheet>