<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:dim="http://www.dspace.org/xmlns/dspace/dim">

    <!-- The institution number for oslo is 185 -->
    <xsl:variable name="instnr" select='185'/>

    <xsl:template name="first-person">
        <xsl:param name="field-nodeset"/>
        <xsl:for-each select="$field-nodeset[1]">
            <dim:field mdschema="cristin" element="unitcode">
                <xsl:value-of select="institusjonsnr"/>
                <xsl:text>,</xsl:text>
                <xsl:value-of select="avdnr"/>
                <xsl:text>,</xsl:text>
                <xsl:value-of select="undavdnr"/>
                <xsl:text>,</xsl:text>
                <xsl:value-of select="gruppenr"/>
            </dim:field>
            <dim:field mdschema="cristin" element="unitname">
                <xsl:value-of select="navn"/>
            </dim:field>
        </xsl:for-each>
    </xsl:template>



    <xsl:template match="/">

        <metadata xmlns:dim="http://www.dspace.org/xmlns/dspace/dim">

            <!-- unit code (cristin.unitcode) -->
            <!-- (/frida/forskningsresultat/fellesdata/person/tilhorighet/sted/) institusjonsnr,avdnr,undavdnr,gruppenr -->
            <!-- "Must be fetched only for the first author (person) where
/frida/forskningsresultat/fellesdata/person/tilhorighet/sted/institusjonsnr = 185 (i.e. Universitetet i Oslo)" -->

            <!-- cristin.unitname	(/frida/forskningsresultat/fellesdata/person/tilhorighet/sted/) navn	"Must be fetched only for the first author (person) where
/frida/forskningsresultat/fellesdata/person/tilhorighet/sted/institusjonsnr = 185 (i.e. Universitetet i Oslo)" -->

            <xsl:call-template name="first-person">
                <xsl:with-param name="field-nodeset" select="/frida/forskningsresultat/fellesdata/person/tilhorighet/sted[institusjonsnr=$instnr]" />
            </xsl:call-template>

            <!-- cristin.ispublished	(/frida/forskningsresultat/fellesdata/) erPublisert -->
            <xsl:if test="/frida/forskningsresultat/fellesdata/erPublisert">
                <dim:field mdschema="cristin" element="ispublished">
                    <xsl:value-of select="/frida/forskningsresultat/fellesdata/erPublisert"/>
                </dim:field>
            </xsl:if>

            <!-- cristin.fulltext	(/frida/forskningsresultat/fellesdata/fulltekst/) type -->
            <xsl:if test="/frida/forskningsresultat/fellesdata/fulltekst/type">
                <xsl:for-each select="/frida/forskningsresultat/fellesdata/fulltekst/type">
                    <dim:field mdschema="cristin" element="fulltext">
                        <xsl:value-of select="."/>
                    </dim:field>
                </xsl:for-each>
            </xsl:if>

            <!-- cristin.qualitycode	"(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/kvalitetsniva/) kode
(/frida/forskningsresultat/kategoridata/bokRapport/forlag/kvalitetsniva/) kode"	Quality level of publication / journal. -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/kvalitetsniva/kode">
                <dim:field mdschema="cristin" element="qualitycode">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/kvalitetsniva/kode"/>
                </dim:field>
            </xsl:if>
            <xsl:if test="/frida/forskningsresultat/kategoridata/bokRapport/forlag/kvalitetsniva/kode">
                <dim:field mdschema="cristin" element="qualitycode">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/bokRapport/forlag/kvalitetsniva/kode"/>
                </dim:field>
            </xsl:if>

            <!-- dc.creator.author	(/frida/forskningsresultat/fellesdata/person/) etternavn, fornavn -->
            <xsl:for-each select="/frida/forskningsresultat/fellesdata/person">
                <dim:field mdschema="dc" element="creator" qualifier="author">
                    <xsl:value-of select="concat(etternavn, string(', '),  fornavn)"/>
                </dim:field>
            </xsl:for-each>

            <!-- dc.date.created	(/frida/forskningsresultat/fellesdata/registrert/) dato -->
            <xsl:if test="/frida/forskningsresultat/fellesdata/registrert/dato">
                <dim:field mdschema="dc" element="date" qualifier="created">
                    <xsl:value-of select="/frida/forskningsresultat/fellesdata/registrert/dato"/>
                </dim:field>
            </xsl:if>

            <!-- dc.date.issued	(/frida/forskningsresultat/fellesdata/) ar -->
            <xsl:for-each select="/frida/forskningsresultat/fellesdata">
                <dim:field mdschema="dc" element="date" qualifier="issued">
                    <xsl:value-of select="ar"/>
                </dim:field>
            </xsl:for-each>

            <!-- dc.description.abstract	(/frida/forskningsresultat/fellesdata/sammendrag/) tekst -->
            <xsl:for-each select="/frida/forskningsresultat/fellesdata/sammendrag/tekst">
                <dim:field mdschema="dc" element="description" qualifier="abstract">
                    <xsl:value-of select="."/>
                </dim:field>
            </xsl:for-each>

            <!-- dc.identifier.cristin	(/frida/forskningsresultat/fellesdata/) id -->
            <xsl:if test="/frida/forskningsresultat/fellesdata/id">
                <dim:field mdschema="dc" element="identifier" qualifier="cristin">
                    <xsl:value-of select="/frida/forskningsresultat/fellesdata/id"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.jtitle	(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/) navn -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/navn">
                <dim:field mdschema="dc" element="identifier" qualifier="jtitle">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/navn"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.volume	(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/) volum -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/volum">
                <dim:field mdschema="dc" element="identifier" qualifier="volume">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/volum"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.issue	(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/) hefte -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/hefte">
                <dim:field mdschema="dc" element="identifier" qualifier="issue">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/hefte"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.startpage	(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/) sideFra -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/sideFra">
                <dim:field mdschema="dc" element="identifier" qualifier="startpage">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/sideFra"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.endpage	(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/) sideTil -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/sideTil">
                <dim:field mdschema="dc" element="identifier" qualifier="endpage">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/sideTil"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.pagecount	"(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/) antallSider
(/frida/forskningsresultat/kategoridata/bokRapport/) antallSider" -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/antallSider">
                <dim:field mdschema="dc" element="identifier" qualifier="pagecount">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse/antallSider"/>
                </dim:field>
            </xsl:if>
            <xsl:if test="/frida/forskningsresultat/kategoridata/bokRapport/antallSider">
                <dim:field mdschema="dc" element="identifier" qualifier="pagecount">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/bokRapport/antallSider"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.isbn	(/frida/forskningsresultat/kategoridata/bokRapport/) isbn -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/bokRapport/isbn">
                <dim:field mdschema="dc" element="identifier" qualifier="isbn">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/bokRapport/isbn"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.issn	(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/) issn -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/issn">
                <dim:field mdschema="dc" element="identifier" qualifier="issn">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/issn"/>
                </dim:field>
            </xsl:if>

            <!-- dc.identifier.doi	(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/) doi -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi">
                <dim:field mdschema="dc" element="identifier" qualifier="doi">
                    <xsl:choose>
                        <xsl:when test='starts-with(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi, "doi:")
            					or starts-with(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi, "DOI:")'>
                            <xsl:text>http://dx.doi.org/</xsl:text>
                            <xsl:value-of
                                    select="substring(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi, 5)"/>
                        </xsl:when>
                        <xsl:when
                                test='starts-with(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi, "10")'>
                            <xsl:text>http://dx.doi.org/</xsl:text>
                            <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/doi"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </dim:field>
            </xsl:if>

            <!-- dc.language	(/frida/forskningsresultat/fellesdata/sprak/) kode -->
            <xsl:if test="/frida/forskningsresultat/fellesdata/sprak/kode">
                <dim:field mdschema="dc" element="language">
                    <xsl:value-of select="/frida/forskningsresultat/fellesdata/sprak/kode"/>
                </dim:field>
            </xsl:if>

            <!-- dc.publisher	"(/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/) utgivernavn
(/frida/forskningsresultat/kategoridata/bokRapport/forlag/) navn" -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/utgivernavn">
                <dim:field mdschema="dc" element="publisher">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/tidsskrift/utgivernavn"/>
                </dim:field>
            </xsl:if>
            <xsl:if test="/frida/forskningsresultat/kategoridata/bokRapport/forlag/navn">
                <dim:field mdschema="dc" element="publisher">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/bokRapport/forlag/navn"/>
                </dim:field>
            </xsl:if>

            <!-- dc.relation.ispartof	(/frida/forskningsresultat/kategoridata/bokRapport/serie/) navn -->
            <!-- dc.relation.ispartofseries	(/frida/forskningsresultat/kategoridata/bokRapport/serie/) navn -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/bokRapport/serie/navn">
                <dim:field mdschema="dc" element="relation" qualifier="ispartof">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/bokRapport/serie/navn"/>
                </dim:field>
                <dim:field mdschema="dc" element="relation" qualifier="ispartofseries">
                    <xsl:value-of select="/frida/forskningsresultat/kategoridata/bokRapport/serie/navn"/>
                </dim:field>
            </xsl:if>

            <!-- dc.subject.nvi	(/frida/forskningsresultat/fellesdata/) vitenskapsdisiplin -->
            <xsl:if test="/frida/forskningsresultat/fellesdata/vitenskapsdisiplin">
                <dim:field mdschema="dc" element="subject" qualifier="nvi">
                    <xsl:for-each select="/frida/forskningsresultat/fellesdata/vitenskapsdisiplin">
                        <xsl:text>VDP::</xsl:text>
                        <xsl:value-of select="navn"/>
                        <xsl:text>: </xsl:text>
                        <xsl:value-of select="kode"/>
                    </xsl:for-each>
                </dim:field>
            </xsl:if>

            <!-- dc.title.alternative	(/frida/forskningsresultat/fellesdata/) alternativTittel -->
            <xsl:if test="/frida/forskningsresultat/fellesdata/alternativTittel">
                <xsl:for-each select="/frida/forskningsresultat/fellesdata">
                    <dim:field mdschema="dc" element="title" qualifier="alternative">
                        <xsl:value-of select="alternativTittel"/>
                    </dim:field>
                </xsl:for-each>
            </xsl:if>

            <!-- dc.title	(/frida/forskningsresultat/fellesdata/) tittel -->
            <xsl:if test="/frida/forskningsresultat/fellesdata/tittel">
                <xsl:for-each select="/frida/forskningsresultat/fellesdata/tittel">
                    <dim:field mdschema="dc" element="title">
                        <xsl:value-of select="."/>
                    </dim:field>
                </xsl:for-each>
            </xsl:if>
            

            <!-- dc.type.document	(/frida/forskningsresultat/fellesdata/kategori/underkategori/) navn
            BOK/MONOGRAFI (Academic monograph)
            BOK/ANTOLOGI (Academic anthology)
            BOK/FAGBOK (Scientific book)
            BOKRAPPORTDEL/ANNET (Other)
            BOKRAPPORTDEL/KAPITTEL (Academic chapter/article)
            FOREDRAG/VIT_FOREDRAG (Academic lecture)
            RAPPORT/DRGRADAVH (Doctoral dissertation)
            RAPPORT/RAPPORT (Report)
            TIDSSKRIFTPUBL/ARTIKKEL (Academic article)
            TIDSSKRIFTPUBL/ARTIKKEL_POP (Popular scientific article)
            TIDSSKRIFTPUBL/OVERSIKTSART (Academic literature review) -->

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'BOK'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'MONOGRAFI'">
                <dim:field mdschema="dc" element="type">Academic monograph</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Monografi</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'BOK'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'ANTOLOGI'">
                <dim:field mdschema="dc" element="type">Acadamic anthology</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Antologi</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'BOK'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'FAGBOK'">
                <dim:field mdschema="dc" element="type">Scientific book</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Fagbok</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'BOKRAPPORTDEL'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'ANNET'">
                <dim:field mdschema="dc" element="type">Other</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Annet</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'BOKRAPPORTDEL'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'KAPITTEL'">
                <dim:field mdschema="dc" element="type">Academic chapter/article</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Kapittel</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'FOREDRAG'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'VIT_FOREDRAG'">
                <dim:field mdschema="dc" element="type">Academic lecture</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Vit Foredrag</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'RAPPORT'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'DRGRADAVH'">
                <dim:field mdschema="dc" element="type">Doctoral dissertation</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Drgradavh</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'RAPPORT'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'RAPPORT'">
                <dim:field mdschema="dc" element="type">Report</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Rapport</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'TIDSSKRIFTPUBL'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'ARTIKKEL'">
                <dim:field mdschema="dc" element="type">Academic article</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Artikkel</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'TIDSSKRIFTPUBL'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'ARTIKKEL_POP'">
                <dim:field mdschema="dc" element="type">Popular scientific article</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Artikkel Pop</dim:field>
            </xsl:if>

            <xsl:if test="/frida/forskningsresultat/fellesdata/kategori/hovedkategori/kode = 'TIDSSKRIFTPUBL'
                            and /frida/forskningsresultat/fellesdata/kategori/underkategori/kode = 'OVERSIKTSART'">
                <dim:field mdschema="dc" element="type">Academic literature review</dim:field>
                <dim:field mdschema="dc" element="type" qualifier="document">Oversiktsart</dim:field>
            </xsl:if>



            <!-- explicitly set the document type -->
            <!--
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
            </xsl:if> -->

            <!-- Citation (explicitly built) -->
            <!-- format of the citation is: <title of journal> <vol>(<nr>):<page range> -->
            <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/sideangivelse">
                <xsl:for-each select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel">

                    <dim:field mdschema="dc" element="identifier" qualifier="bibliographiccitation">
                        <xsl:text>info:ofi/fmt:kev:mtx:ctx&amp;ctx_ver=Z39.88-2004&amp;rft_val_fmt=info:ofi/fmt:kev:mtx:journal&amp;rft.jtitle=</xsl:text>
                        <xsl:value-of select="tidsskrift/navn"/>
                        <xsl:text>&amp;rft.volume=</xsl:text>
                        <xsl:value-of select="volum"/>
                        <xsl:text>&amp;rft.date=</xsl:text>
                        <xsl:value-of select="/frida/forskningsresultat/fellesdata/ar"/>
                    </dim:field>

                    <dim:field mdschema="dc" element="identifier" qualifier="citation">

                        <xsl:for-each select="/frida/forskningsresultat/fellesdata/person">
                            <xsl:value-of select="concat(etternavn, string(', '),  fornavn)"/>
                            <xsl:choose>
                                <xsl:when test="not(position()=last())"><xsl:text>, </xsl:text></xsl:when>
                                <xsl:otherwise><xsl:text>. </xsl:text></xsl:otherwise>
                            </xsl:choose>
                        </xsl:for-each>

                        <xsl:if test="/frida/forskningsresultat/fellesdata/tittel">
                            <xsl:value-of select="/frida/forskningsresultat/fellesdata/tittel"/>
                            <xsl:text>. </xsl:text>
                        </xsl:if>

                        <xsl:value-of select="tidsskrift/navn"/>
                        <xsl:value-of select="string(' ')"/>
                        <xsl:value-of select="volum"/>
                        <!--
                        <xsl:value-of select="string('(')" />
                        <xsl:value-of select="/frida/forskningsresultat/fellesdata/ar" />
                        <xsl:value-of select="string(')')" />
                        -->
                        <xsl:if test="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/hefte">
                            <xsl:value-of select="string('(')"/>
                            <xsl:value-of select="/frida/forskningsresultat/kategoridata/tidsskriftsartikkel/hefte"/>
                            <xsl:value-of select="string(')')"/>
                        </xsl:if>

                        <xsl:if test="sideangivelse/sideFra or sideangivelse/sideTil">
                            <xsl:text>:</xsl:text>
                            <xsl:choose>
                                <xsl:when test="sideangivelse/sideFra"><xsl:value-of select="sideangivelse/sideFra"/></xsl:when>
                                <xsl:otherwise><xsl:text>?</xsl:text></xsl:otherwise>
                            </xsl:choose>

                            <xsl:value-of select="string('-')"/>

                            <xsl:choose>
                                <xsl:when test="sideangivelse/sideTil"><xsl:value-of select="sideangivelse/sideTil"/></xsl:when>
                                <xsl:otherwise><xsl:text>?</xsl:text></xsl:otherwise>
                            </xsl:choose>
                        </xsl:if>
                    </dim:field>
                </xsl:for-each>
            </xsl:if>

            
        </metadata>

    </xsl:template>
</xsl:stylesheet>