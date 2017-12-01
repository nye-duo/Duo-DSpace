# Plugins and their relationships

This document describes each of the classes in this package which are DSpace plugins for use when deploying a Duo system.  Each plugin has the following information defined against it, so you may pick-and choose the plugins to deploy based on your specific requirements:

**Interface** - the name of the java interface that the plugin conforms to

**Feature** - which feature of Duo this pertains to.

**Function** - what the purpose of the plugin is

**Configured In** - Where to find the place to set this configuration

**Configuration** - Where to find configuration which this plugin uses

**Hard-Dependencies**: - Other plugins which are **required** to make the system behave as intended with this plugin

**Co-Dependencies** - Other plugins which are **strongly recommended** to be included alongside the specified plugin

## General use plugins

### no.uio.duo.DSpace18FixedDispatcher 
**Interface**: org.dspace.event.Dispatcher

**Feature**: General Event Handling in DSpace, StudentWeb

**Function**: Handles processing of events in an error-resistant way (fixing issues with the Basic Dispatcher in DSpace by default)

**Configured In**: dspace.cfg

**Configuration**: None

**Hard-Dependencies**: None

**Co-Dependencies**: None

### no.uio.duo.policy.DuoEmbargoSetter

**Interface**: org.dspace.embargo.EmbargoSetter

**Feature**: Sets Embargoes on incoming or existing items

**Function**: Applies the PolicyPatternManager appropriately to items

**Configured In**: dspace.cfg

**Configuration**: embargo.field.lift

**Hard-Dependencies**: None

**Co-Dependencies**: None


### no.uio.duo.policy.DupPolicyCurationTask
**Interface**: org.dspace.curate.CurationTask

**Feature**: Curation Task system

**Function**: Applies the PolicyPatternManager appropriately to items

**Configured In**: curate.cfg

**Configuration**:
* plugin.named.org.dspace.curate.CurationTask
* ui.tasknames

**Hard-Dependencies**: None

**Co-Dependencies**: None


### no.uio.duo.DuoEventConsumer 
**Interface**: org.dspace.event.Consumer

**Feature**: StudentWeb

**Function**: Used to embargo/withdraw StudentWeb items with a "fail" grade on submission to the archive, and subsequent modifications

**Configured In**: dspace.cfg

**Configuration**: None

**Hard-Dependencies**: 
* no.uio.duo.DSpace18FixedDispatcher

**Co-Dependencies**: None


## Plugins for use with Cristin integration

These plugins are to be used when integrating Duo with the Cristin OAI-PMH feed.  Note that although each plugin is has no *hard dependencies* on any other plugin, they are designed to work together as whole, so it is **strongly recommended** that they be deployed together.

### no.uio.duo.CristinBundleVersioningStrategy 
**Interface**: org.dspace.harvest.BundleVersioningStrategy

**Feature**: OAI Harvesting from Cristin

**Function**: Tells the OAI harvester how to handle bundles of bitstreams when an item is updated.  This particular versioning strategy does nothing, it simply returns leaving all the bundles alone, as the versioning will be handled by the ORE ingester.

**Configured In**: dspace.cfg

**Configuration**: modules/oai.cfg

**Hard-Dependencies**: None

**Co-Dependencies**: 
* no.uio.duo.CristinIngestFilter
* no.uio.duo.CristinIngestionWorkflow
* no.uio.duo.CristinMetadataRemover
* no.uio.duo.CristinOAIDCCrosswalk
* no.uio.duo.CristinOREImporter 

### no.uio.duo.CristinIngestFilter 
**Interface**: org.dspace.harvest.IngestFilter

**Feature**: OAI Harvesting from Cristin

**Function**: Determines whether the incoming harvest record should be accepted or not

**Configured In**: dspace.cfg

**Configuration**: modules/oai.cfg

**Hard-Dependencies**: None

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestionWorkflow
* no.uio.duo.CristinMetadataRemover
* no.uio.duo.CristinOAIDCCrosswalk
* no.uio.duo.CristinOREImporter 

### no.uio.duo.CristinIngestionWorkflow 
**Interface**: org.dspace.harvest.IngestionWorkflow

**Feature**: OAI Harvesting from Cristin

**Function**: Tells the OAI harvester what to do with the new or updated items with regard to the DSpace workflow.  This particular plugin sends the items through the DSpace workflow, creating new versions if the incoming update is to an item which is already archived.

**Configured In**: dspace.cfg

**Configuration**: modules/oai.cfg

**Hard-Dependencies**: None

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestFilter 
* no.uio.duo.CristinMetadataRemover
* no.uio.duo.CristinOAIDCCrosswalk
* no.uio.duo.CristinOREImporter 

### no.uio.duo.CristinMetadataRemover 
**Interface**: org.dspace.harvest.MetadataRemover

**Feature**: OAI Harvesting from Cristin

**Function**: Chooses which metadata to delete before updating an item; allows us to selectively clear old metadata before the item is updated.

**Configured In**: dspace.cfg

**Configuration**: modules/cristin.cfg, modules/oai.cfg

**Hard-Dependencies**: None

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestFilter 
* no.uio.duo.CristinIngestionWorkflow 
* no.uio.duo.CristinOAIDCCrosswalk
* no.uio.duo.CristinOREImporter 

### no.uio.duo.CristinOAIDCCrosswalk 
**Interface**: org.dspace.content.crosswalk.IngestionCrosswalk

**Feature**: OAI Harvesting from Cristin

**Function**: A crosswalk to be used with the CRISTIN oai_dc document. This basically does nothing, as all the CRISTIN work is done by the ORE document ingester

**Configured In**: dspace.cfg

**Configuration**: None

**Hard-Dependencies**: None

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestFilter 
* no.uio.duo.CristinIngestionWorkflow 
* no.uio.duo.CristinMetadataRemover 
* no.uio.duo.CristinOREImporter 

### no.uio.duo.CristinOREImporter 
**Interface**: org.dspace.content.crosswalk.IngestionCrosswalk

**Feature**: OAI Harvesting from Cristin

**Function**: Import items from Cristin using the OAI-ORE metadata format retrieved over OAI-PMH.

**Configured In**: dspace.cfg

**Configuration**: None

**Hard-Dependencies**: None

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestFilter 
* no.uio.duo.CristinIngestionWorkflow 
* no.uio.duo.CristinMetadataRemover 
* no.uio.duo.CristinOAIDCCrosswalk 

## Plugins for use with StudentWeb integration

These plugins handle two aspects of the StudentWeb integration:

* Applying and lifting embargoes on items depending on their grade
* Handling the incoming and outgoing content and metadata formats

It is **recommended** that all of these plugins be deployed if integrating Duo with StudentWeb.  If your use case does not require incoming items to be embargoed by their grade, then you may omit the *DuoInstallConsumer* and the *DuoEmbargoLifter*.  Nonetheless, it is *recommended* that you always use the *DSpace18FixedEventDispatcher*, as this fixes a critical bug in the standard DSpace event dispatcher.

Note also that the content handling plugins ( *DuoEntryDisseminator*, *FSBagItIngester*, and *FSEntryIngester* ) are not strictly dependent on each other but it is **strongly recommended** that they be deployed together, as they are designed to work as a coherent whole.

### no.uio.duo.DuoEntryDisseminator 
**Interface**: org.dspace.sword2.SwordEntryDisseminator

**Feature**: StudentWeb

**Function**: Used when sending metadata from Duo to StudentWeb; provide the embedded metadata for StudentWeb alongside a Dublin Core version of the metadata

**Configured In**: modules/swordv2-server.cfg

**Configuration**: None

**Hard-Dependencies**: None

**Co-Dependencies**: 
* no.uio.duo.FSBagItIngester 
* no.uio.duo.FSEntryIngester 

### no.uio.duo.FSBagItIngester 
**Interface**: org.dspace.sword2.SwordContentIngester

**Feature**: StudentWeb

**Function**: deal with the BagIt format used by the StudentWeb/FS integration

**Configured In**: modules/swordv2-server.cfg

**Configuration**: None

**Hard-Dependencies**: None

**Co-Dependencies**:
* no.uio.duo.DuoEntryDisseminator 
* no.uio.duo.FSEntryIngester 

### no.uio.duo.FSEntryIngester 
**Interface**: org.dspace.sword2.SwordEntryIngester

**Feature**: StudentWeb

**Function**:  takes the FS metadata as embedded in an atom entry and adds it to an existing object

**Configured In**: modules/swordv2-server.cfg

**Configuration**: modules/studentweb.cfg, modules/swordv2-server.cfg

**Hard-Dependencies**: None

**Co-Dependencies**:
* no.uio.duo.DuoEntryDisseminator 
* no.uio.duo.FSBagItIngester 


