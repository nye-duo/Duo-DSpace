<?xml version="1.0" encoding="utf-8"?>

<xsl:stylesheet
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:fs="http://studentweb.no/terms/"
        xmlns:dcterms="http://purl.org/dc/terms/"
        xmlns:dim="http://www.dspace.org/xmlns/dspace/dim"
        version="1.0">

    <xsl:template match="text()"></xsl:template>

    <xsl:template match="/">
        <dim:dim>
            <xsl:apply-templates/>
        </dim:dim>
    </xsl:template>


    <!-- Title -->
    <xsl:template match="/fs:metadata/dcterms:title">
        <dim:field mdschema="dc" element="title">
            <xsl:if test="@xml:lang">
                <xsl:attribute name="lang"><xsl:value-of select="normalize-space(@xml:lang)"/></xsl:attribute>
            </xsl:if>
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- student number -->
    <!--
    <xsl:template match="/fs:metadata/fs:studentNumber">
        <dim:field mdschema="fs" element="studentnumber">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>
    -->

    <!-- UID -->
    <xsl:template match="/fs:metadata/fs:uid">
        <dim:field mdschema="fs" element="uid">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- postal address -->
    <!--
    <xsl:template match="/fs:metadata/fs:postalAddress">
        <dim:field mdschema="fs" element="postaladdress">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>
    -->

    <!-- email -->
    <!--
    <xsl:template match="/fs:metadata/fs:email">
        <dim:field mdschema="fs" element="email">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>
    -->

    <!-- Telephone number -->
    <!--
    <xsl:template match="/fs:metadata/fs:telephoneNumber">
        <dim:field mdschema="fs" element="telephonenumber">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>
    -->

    <!-- subject code -->
    <xsl:template match="/fs:metadata/fs:subject/fs:subjectCode">
        <dim:field mdschema="fs" element="subjectcode">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- subject title -->
    <xsl:template match="/fs:metadata/fs:subject/fs:subjectTitle">
        <dim:field mdschema="fs" element="subjecttitle">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- grade -->
    <xsl:template match="/fs:metadata/fs:grade">
        <dim:field mdschema="fs" element="grade">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- unit code -->
    <xsl:template match="/fs:metadata/fs:unitcode">
        <dim:field mdschema="fs" element="unitcode">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- unit name -->
    <xsl:template match="/fs:metadata/fs:unitName">
        <dim:field mdschema="fs" element="unitname">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- Author -->
    <xsl:template match="/fs:metadata/fs:familyName">
        <dim:field mdschema="dc" element="creator" qualifier="author">
            <xsl:value-of select="normalize-space(/fs:metadata/fs:familyName)"/>
            <xsl:text>, </xsl:text>
            <xsl:value-of select="normalize-space(/fs:metadata/fs:givenName)"/>
        </dim:field>
        <!-- note that we xwalk to both creator (for compliance with the norwegian recommendations)
            and contributor (for compliance with DSpace functionality) -->
        <dim:field mdschema="dc" element="contributor" qualifier="author">
            <xsl:value-of select="normalize-space(/fs:metadata/fs:familyName)"/>
            <xsl:text>, </xsl:text>
            <xsl:value-of select="normalize-space(/fs:metadata/fs:givenName)"/>
        </dim:field>
    </xsl:template>

    <!-- embargo type -->
    <xsl:template match="/fs:metadata/fs:embargoType">
        <dim:field mdschema="fs" element="embargotype">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- embargo end date -->
    <xsl:template match="/fs:metadata/fs:embargoEndDate">
        <dim:field mdschema="dc" element="date" qualifier="embargoenddate">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- abstract -->
    <xsl:template match="/fs:metadata/dcterms:abstract">
        <dim:field mdschema="dc" element="description" qualifier="abstract">
            <xsl:if test="@xml:lang">
                <xsl:attribute name="lang"><xsl:value-of select="normalize-space(@xml:lang)"/></xsl:attribute>
            </xsl:if>
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- language -->
    <xsl:template match="/fs:metadata/dcterms:language">
        <dim:field mdschema="dc" element="language" qualifier="iso">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

    <!-- type -->
    <xsl:template match="/fs:metadata/dcterms:type">
        <dim:field mdschema="dc" element="type">
            <xsl:value-of select="normalize-space(.)"/>
        </dim:field>
    </xsl:template>

</xsl:stylesheet>