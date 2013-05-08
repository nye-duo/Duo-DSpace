#DUO-DSpace Configuration

In order to successfully deploy the Duo extensions to DSpace, you will need all of the following configuration files installed.  You may wish to update the configuration values for your particular requirements, and you should look in each of the configuration files for their own detailed documentation.  Here we will provide a brief overview of the configuration files, and where necessary go on to look at them in slightly more detail.

##Brief Overview

**dspace.cfg** - The additions/changes required to the standard dspace.cfg file in order to support the Duo extensions.  See the detailed documentation below for more details.

**inputforms.xml** - DSpace metadata entry forms which comply to the metadata standards used by StudentWeb and Cristin.

**workflow.xml** - XML workflow definition which encodes the Duo ingest workflow.  See the detailed documentation below for more details.

**xmlui.xconf** - XML UI configuration which turns on the XML Workflow required by Duo.  See the detailed documentation below for more details.

**crosswalks/cristin-generic.xsl** - Catch-all/generic crosswalk to handle all incoming content from Cristin.

**crosswalks/fs-submission.xsl** - Crosswalk to handle all incoming content from StudentWeb/FS

**emails/unitcodes** - Email template to send to administrators to alert them of a change to the unit codes of an updated item.  See the functional overview for details of where this appears in the workflow: [https://docs.google.com/a/cottagelabs.com/document/d/17Iiswcz_LkSMgdhEZIesV1BTrijRNsPWAyH29-L1rQg/edit](https://docs.google.com/a/cottagelabs.com/document/d/17Iiswcz_LkSMgdhEZIesV1BTrijRNsPWAyH29-L1rQg/edit)

**modules/cristin.cfg** - Configuration specific to the Cristin ingest.

**modules/curate.cfg** - deprecated, do not use.

**modules/oai.cfg** - Enhanced OAI harvester configuration.  See the detailed documentation below for more details.

**modules/studentweb.cfg** - Configuration specific to the StudentWeb ingest.

**modules/swordv2-server.cfg** - Enhanced SWORDv2 configuration, containing the specific values required by the Duo extensions.

**modules/workflow.cfg** - Configuration for the DSpace workflow, which just turns on the XML workflow required by Duo.

**registres/cristin-metadata.xml** - Metadata registry of fields required by the Cristin metadata schema.

**registries/fs-metadata.xml** - Metadata registry of fields reqiured by the StudentWeb/FS metadata schema.

**spring/api/workflow-actions.xml** - Bindings of workflow actions to supporting classes, required to support the Duo XML workflow.  See the detailed documentation below for more details.

**spring/xmlui/workflow-actions-xmlui.xml** - Bindings of workflow actions to supporting user interface components, required to support the Duo XML workflow.  See the detailed docmentation below for more details.

##dspace.cfg

Additions and modifications to the main, standard dspace.cfg file.  To deploy this, it cannot just be appended to the existing dspace.cfg, it must be merged with it.  It duplicates the following fields from the main dspace.cfg:

* embargo.field.terms
* embargo.field.lift
* plugin.single.org.dspace.embargo.EmbargoLifter
* event.dispatcher.default.consumers
* plugin.named.org.dspace.content.crosswalk.IngestionCrosswalk

This file also defines the specific crosswalks to be used for the different types of content coming from Cristin.  At this stage there is only a generic crosswalk, but this configuration can be updated to use different crosswalks whenever necessary:

    crosswalk.submission.CRISTIN_BOOK.stylesheet= crosswalks/cristin-generic.xsl
    crosswalk.submission.CRISTIN_ARTICLE.stylesheet= crosswalks/cristin-generic.xsl
    crosswalk.submission.CRISTIN_CHAPTER.stylesheet= crosswalks/cristin-generic.xsl
    crosswalk.submission.CRISTIN_CONFERENCE.stylesheet= crosswalks/cristin-generic.xsl
    crosswalk.submission.CRISTIN_REPORT.stylesheet= crosswalks/cristin-generic.xsl

It also includes the plugins for the enhanced OAI Harvester provided with the modified Duo DSpace.  These control the various aspects of the ingest, versioning and filtering when harvesting content from Cristin:

    plugin.named.org.dspace.harvest.IngestionWorkflow = \
      no.uio.duo.CristinIngestionWorkflow = cristin

    plugin.named.org.dspace.harvest.MetadataRemover = \
      no.uio.duo.CristinMetadataRemover = cristin

    plugin.named.org.dspace.harvest.BundleVersioningStrategy = \
      no.uio.duo.CristinBundleVersioningStrategy = cristin

    plugin.named.org.dspace.harvest.IngestFilter = \
      no.uio.duo.CristinIngestFilter = cristin

##workflow.xml

XML Workflow definitions which allow us to define the specific workflow used for items coming in from Cristin.  It is important that this configuration be correctly set for your repository before Cristin harvesting begins.

There are two key sections to this file which we are adding to the default workflow.xml.  The first is the workflow map, which maps collections to ingest workflows:

    <workflow-map>
        <name-map collection="default" workflow="default"/>
        <name-map collection="123456789/4404" workflow="cristin"/>
    </workflow-map>

This example maps the collection identified by the handle 123456789/4404 to the workflow id "cristin" (defined below).

The workflow itself is defined by this section of the file:
    
    <workflow start="bitstreamstep" id="cristin">
    
        <roles>
            <role id="filemanager" name="File Manager" 
                    description="The people responsible for this step are able to edit the
                                ordering of bitstreams and content of bundles" />
            <role id="editor" name="Editor" 
                    description="The people responsible for this step are able to edit the 
                                metadata of incoming submissions, and then accept or reject them." />
            <role id="assigner" name="Collection Assigner" 
                    description="people responsible for assigning the item to collections"/>
        </roles>

        <step id="bitstreamstep" role="filemanager" userSelectionMethod="claimaction">
            <outcomes>
                <step status="0">editstep</step>
            </outcomes>
            <actions>
                <action id="bitstreamaction"/>
            </actions>
        </step>
        
        <step id="editstep" role="editor" userSelectionMethod="claimaction">
            <outcomes>
                <step status="0">assignment</step>
            </outcomes>
            <actions>
                <action id="editaction"/>
            </actions>
        </step>
        
        <step id="assignment" role="assigner" userSelectionMethod="claimaction">
            <actions>
                <action id="assignmentaction"/>
            </actions>
        </step>
        
    </workflow>
    
The workflow starts with the "bitstreamstep" (where the user will reorganise the bitstreams), proceeds then to the "editstep" (where the user will update the metadata) and finally to the "assignment" step (where the user will assign the item to the relevant collections).  The underlying code for each of these stages can be found configured in **spring/api/workflow-actions.xml** and **spring/xmlui/workflow-actions-xmlui.xml**.

##xmlui.conf

This file allows us to configure the Aspects used by the XML UI.  For the Duo extensions, this allows us to activate the relevant Aspect for the XML Workflow.

Where we would originally find:

    <aspect name="Original Workflow" path="resource://aspects/Workflow/" />

we replace it with:

    <aspect name="XMLWorkflow" path="resource://aspects/XMLWorkflow/" />

##oai.cfg

This file configures the behaviour of the OAI-PMH harvester which pulls the content in from Cristin.

Much of the configuration values are left as default, but there are some which are worth looking at closer

    harvester.autoStart=false

This ensures that the harvester **does not** start when DSpace starts.  Since the download from Cristin is quite large, and should only be done on a relatively long schedule, it is unwise to start the harvester automatically.

As part of the new functionality added to the Duo version of DSpace, we have added new configurable user interface components to allow administrators to select the desired behaviour.  This configuration then gives the user a choice between the default DSpace behaviour, and the behaviour required to support harvesting from Cristin:

    harvester.ingest_filter.options = none:No filtering of incoming items, cristin:Core Cristin types with full-text only
    harvester.metadata_update.options = all:Remove all existing metadata and replace completely, cristin:Update only Cristin authority controlled metadata
    harvester.bundle_versioning.options = all:Remove all existing bundles and replace completely, cristin:Synchronise bitstreams with Cristin
    harvester.ingest_workflow.options = archive:All items go directly to the DSpace archive, cristin:All items go through the DSpace Workflow

The form of the configuration options is:

    harvester.<plugin>.options = <option name>:<human readable option text>, ....

the "option name" is then used to load the appropriate plugin, as defined in dspace.cfg (see above).

Finally we also added an administrator eperson account which will provide a context for asynchronous harvesting (i.e. harvesting operations run by the scheduler, rather
than on request by an administrator)

    admin.eperson = richard

This can be the administrators email address or netid.

##spring/api/workflow-actions.xml

This spring configuration provides the mappings from the actions defined in **workflow.xml** for each workflow stage to underlying Java classes that will handle the behaviour.

We first define the classes which will handle the actions:

    <bean id="assignmentactionAPI" class="no.uio.duo.XmlUICollectionAssignment" scope="prototype"/>
    <bean id="bitstreamactionAPI" class="no.uio.duo.XmlUIBitstreamReorder" scope="prototype"/>

We can then go on and define the relationship between the action defined in **workflow.xml** and the way that the actions (shown immediately above) are invoked.  We indicate that each action requires a UI, and this will ensure that the user interface components defined in **spring/xmlui/workflow-actions-xmlui.xml** are used.

    <bean id="assignmentaction" class="org.dspace.xmlworkflow.state.actions.WorkflowActionConfig" scope="prototype">
        <constructor-arg type="java.lang.String" value="assignmentaction"/>
        <property name="processingAction" ref="assignmentactionAPI"/>
        <property name="requiresUI" value="true"/>
    </bean>

    <bean id="bitstreamaction" class="org.dspace.xmlworkflow.state.actions.WorkflowActionConfig" scope="prototype">
        <constructor-arg type="java.lang.String" value="bitstreamaction"/>
        <property name="processingAction" ref="bitstreamactionAPI"/>
        <property name="requiresUI" value="true"/>
    </bean>

##spring/xmlui/workflow-actions-xmlui.xml

This spring confiruation provides the user interface components which are loaded by the XML Workflow actions (defined in the previous section)

    <bean id="assignmentaction_xmlui" class="no.uio.duo.XmlUICollectionAssignmentUI" scope="singleton"/>
    <bean id="bitstreamaction_xmlui" class="no.uio.duo.XmlUIBitstreamReorderUI" scope="singleton"/>

