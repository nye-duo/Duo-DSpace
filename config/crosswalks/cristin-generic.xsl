<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- The institution number for oslo is 181 -->
   <xsl:variable name="instnr" select='181' />

  <xsl:template match="/" >

    <metadata xmlns:dim="http://www.dspace.org/xmlns/dspace/dim">

        <!-- authors -->
          <xsl:for-each select="/frida/forskningsresultat/fellesdata/person">
              <dim:field mdschema="dc" element="contributor" qualifier="author"> <xsl:value-of select="concat(etternavn, string(', '),  fornavn)" /> </dim:field>
          </xsl:for-each>

        <!-- date issued -->
        <xsl:for-each select="/frida/forskningsresultat/fellesdata">
            <dim:field mdschema="dc" element="date" qualifier="issued">
               <xsl:value-of select="ar" />
            </dim:field>
        </xsl:for-each>

        <!-- ISSN -->
         <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift[1]">
           <xsl:for-each select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift">
             <dim:field mdschema="dc" element="identifier" qualifier="issn">
              <xsl:value-of select="issn" />
             </dim:field>
          </xsl:for-each>
         </xsl:if>


        <!-- abstract -->
        <xsl:if test="/frida/forskningsresultat/fellesdata/sammendrag">
          <dim:field mdschema="dc" element="description" qualifier="abstract">
           <xsl:for-each select="/frida/forskningsresultat/fellesdata/sammendrag">
                <xsl:value-of select="tekst" />
           </xsl:for-each>
          </dim:field>
        </xsl:if>


        <!-- Norwegian science index? -->
        <xsl:if test="/frida/forskningsresultat/fellesdata/vitenskapsdisiplin">
          <dim:field mdschema="dc" element="subject" qualifier="nsi">
           <xsl:for-each select="/frida/forskningsresultat/fellesdata/vitenskapsdisiplin">
             <xsl:text>VDP::</xsl:text>
             <xsl:value-of select="navn"/>
             <xsl:text>: </xsl:text>
             <xsl:value-of select="kode" />
           </xsl:for-each>
          </dim:field>
        </xsl:if>


        <!-- language -->
        <!-- Mapping from 2 letter to 3 letter language codes:
           - NO -> nob
           - SP ->spa
           - FR -> fra
           - RU -> rus
           - EN -> eng
           - DE -> der
           - SE -> smi
           - DK -> dan
           - FI -> fin
           - SW -> swe
        -->
       <xsl:if test="/frida/forskningsresultat/fellesdata/sprak">
          <xsl:for-each select="/frida/forskningsresultat/fellesdata/sprak">
           <dim:field mdschema="dc" element="language" qualifier="iso">
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'NO'">nob</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'SP'">spa</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'FR'">fra</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'RU'">rus</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'EN'">eng</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'DE'">ger</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'SE'">smi</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'DK'">dan</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'FI'">fin</xsl:if>
               <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode = 'SW'">swe</xsl:if>
            </dim:field>
         </xsl:for-each>
       </xsl:if>

        <!-- ??? - some sort of subject -->
       <xsl:if test="/frida/forskningsresultat/fellesdata/emneord">
          <xsl:for-each select="/frida/forskningsresultat/fellesdata/emneord">
           <dim:field mdschema="dc" element="subject">
               <xsl:value-of select="navn" />
            </dim:field>
           <dim:field mdschema="dc" element="subject">
               <xsl:value-of select="navnEngelsk" />
            </dim:field>
         </xsl:for-each>
       </xsl:if>

        <!-- publisher -->
       <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift">
          <xsl:for-each select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift">
           <dim:field mdschema="dc" element="publisher">
               <xsl:value-of select="utgivernavn" />
            </dim:field>
         </xsl:for-each>
       </xsl:if>

        <!-- Title -->
       <xsl:if test="/frida/forskningsresultat/fellesdata">
           <xsl:for-each select="/frida/forskningsresultat/fellesdata">
            <dim:field mdschema="dc" element="title">
                <xsl:value-of select="tittel" />
             </dim:field>
          </xsl:for-each>
        </xsl:if>

        <!-- alternative title -->
        <xsl:if test="/frida/forskningsresultat/fellesdata/alternativTittel">
           <xsl:for-each select="/frida/forskningsresultat/fellesdata">
            <dim:field mdschema="dc" element="title" qualifier="alternative">
                <xsl:value-of select="alternativTittel" />
             </dim:field>
          </xsl:for-each>
        </xsl:if>

        <!-- explicitly set the document type -->
        <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/navn = 'Tidsskriftspublikasjon' and /frida/forskningsresultat/fellesdata/kategori/underkategori/navn = 'Vitenskapelig artikkel'">
            <dim:field mdschema="dc" element="type">Journal article</dim:field>
            <dim:field mdschema="dc" element="type">Peer reviewed</dim:field>
        </xsl:if>

        <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/navn = 'Tidsskriftspublikasjon' and /frida/forskningsresultat/fellesdata/kategori/underkategori/navn = 'Vitenskapelig oversiktsartikkel/review'">
            <dim:field mdschema="dc" element="type">Journal article</dim:field>
        </xsl:if>

        <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/navn = 'Bok' and /frida/forskningsresultat/fellesdata/kategori/underkategori/navn = 'Vitenskapelig antologi'">
            <dim:field mdschema="dc" element="type">Book chapter</dim:field>
        </xsl:if>

        <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/navn = 'Bok' and /frida/forskningsresultat/fellesdata/kategori/underkategori/navn = 'Vitenskapelig monografi'">
            <dim:field mdschema="dc" element="type">Book</dim:field>
        </xsl:if>

        <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/navn = 'Bok' and /frida/forskningsresultat/fellesdata/kategori/underkategori/navn = 'Vitenskapelig kommentarutgave'">
            <dim:field mdschema="dc" element="type">Book</dim:field>
        </xsl:if>

        <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/navn = 'Del av bok/rapport' and /frida/forskningsresultat/fellesdata/kategori/underkategori/navn = 'Vitenskapelig kapittel/Artikkel'">
            <dim:field mdschema="dc" element="type">Book chapter</dim:field>
        </xsl:if>


        <!-- explicitly set the peer review type -->
        <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/kvalitetsniva[kode >= 1]">
            <dim:field mdschema="dc" element="type">Peer reviewed</dim:field>
        </xsl:if>

        <!-- DOI -->
        <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi">
            <dim:field mdschema="dc" element="identifier" qualifier="doi">
            	<xsl:choose>
            		<xsl:when test='starts-with(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi, "doi:")
            					or starts-with(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi, "DOI:")'>
            			<xsl:text>http://dx.doi.org/</xsl:text>
            			<xsl:value-of select="substring(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi, 5)" />
            		</xsl:when>
            		<xsl:when test='starts-with(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi, "10")'>
            			<xsl:text>http://dx.doi.org/</xsl:text>
            			<xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi" />
            		</xsl:when>
            		<xsl:otherwise>
            			<xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi" />
            		</xsl:otherwise>
            	</xsl:choose>
            </dim:field>
        </xsl:if>

        <!-- Citation (explicitly built) -->
        <!-- format of the citation is: <title of journal> <vol>(<nr>):<page range> -->
        <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse">
         <xsl:for-each select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel">
            <dim:field mdschema="dc" element="identifier" qualifier="citation">
             <xsl:value-of select="tidsskrift/navn" />
             <xsl:value-of select="string(' ')" />
             <xsl:value-of select="volum" />
             <!--
             <xsl:value-of select="string('(')" />
             <xsl:value-of select="/frida/forskningsresultat/fellesdata/ar" />
             <xsl:value-of select="string(')')" />
             -->
             <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/hefte">
             	<xsl:value-of select="string('(')" />
                <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/hefte" />
				<xsl:value-of select="string(')')" />
             </xsl:if>
             <xsl:text>:</xsl:text>
             <xsl:value-of select="sideangivelse/sideFra" />
             <xsl:value-of select="string('-')" />
             <xsl:value-of select="sideangivelse/sideTil" />
            </dim:field>
         </xsl:for-each>
        </xsl:if>

        <!-- fulltext version -->
        <!--
        <xsl:for-each select="/frida/forskningsresultat/fellesdata/fulltekst">
            <xsl:if test="type='preprint'">
                <dim:field mdschema="dc" element="type" qualifier="version">Submitted</dim:field>
            </xsl:if>
            <xsl:if test="type='postprint'">
                <dim:field mdschema="dc" element="type" qualifier="version">Accepted</dim:field>
            </xsl:if>
            <xsl:if test="type='versjoin'">
                <dim:field mdschema="dc" element="type" qualifier="version">Published</dim:field>
            </xsl:if>
        </xsl:for-each>
        -->
      </metadata>

  </xsl:template>
</xsl:stylesheet>