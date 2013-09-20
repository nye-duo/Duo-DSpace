# Plugins and their relationships

**Interface** - the name of the java interface that the plugin conforms to

**Feature**: - which feature of Duo this pertains to.

**Function** - what the purpose of the plugin is

**Co-Dependencies** - Other plugins which are **strongly recommended** to be included alongside the specified plugin

**Hard-Dependencies**: - Other plugins which are **required** to make the system behave as intended with this plugin

**Configured In** - Where to find the place to set this configuration

**Configuration** - Where to find configuration which this plugin uses

## no.uio.duo.CristinBundleVersioningStrategy 
**Interface**: org.dspace.harvest.BundleVersioningStrategy

**Feature**: OAI Harvesting from Cristin

**Function**: Tells the OAI harvester how to handle bundles of bitstreams when an item is updated.  This particular versioning strategy does nothing, it simply returns leaving all the bundles alone, as the versioning will be handled by the ORE ingester.

**Configured In**: dspace.cfg

**Configuration**: modules/oai.cfg

**Co-Dependencies**: 
* no.uio.duo.CristinIngestFilter
* no.uio.duo.CristinIngestionWorkflow
* no.uio.duo.CristinMetadataRemover
* no.uio.duo.CristinOAIDCCrosswalk
* no.uio.duo.CristinOREImporter 

**Hard-Dependencies**: None

## no.uio.duo.CristinIngestFilter 
**Interface**: org.dspace.harvest.IngestFilter

**Feature**: OAI Harvesting from Cristin

**Function**: Determines whether the incoming harvest record should be accepted or not

**Configured In**: dspace.cfg

**Configuration**: modules/oai.cfg

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestionWorkflow
* no.uio.duo.CristinMetadataRemover
* no.uio.duo.CristinOAIDCCrosswalk
* no.uio.duo.CristinOREImporter 

**Hard-Dependencies**: None

## no.uio.duo.CristinIngestionWorkflow 
**Interface**: org.dspace.harvest.IngestionWorkflow

**Feature**: OAI Harvesting from Cristin

**Function**: Tells the OAI harvester what to do with the new or updated items with regard to the DSpace workflow.  This particular plugin sends the items through the DSpace workflow, creating new versions if the incoming update is to an item which is already archived.

**Configured In**: dspace.cfg

**Configuration**: modules/oai.cfg

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestFilter 
* no.uio.duo.CristinMetadataRemover
* no.uio.duo.CristinOAIDCCrosswalk
* no.uio.duo.CristinOREImporter 

**Hard-Dependencies**: None

## no.uio.duo.CristinMetadataRemover 
**Interface**: org.dspace.harvest.MetadataRemover

**Feature**: OAI Harvesting from Cristin

**Function**: Chooses which metadata to delete before updating an item; allows us to selectively clear old metadata before the item is updated.

**Configured In**: dspace.cfg

**Configuration**: modules/cristin.cfg, modules/oai.cfg

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestFilter 
* no.uio.duo.CristinIngestionWorkflow 
* no.uio.duo.CristinOAIDCCrosswalk
* no.uio.duo.CristinOREImporter 

**Hard-Dependencies**: None

## no.uio.duo.CristinOAIDCCrosswalk 
**Interface**: org.dspace.content.crosswalk.IngestionCrosswalk

**Feature**: OAI Harvesting from Cristin

**Function**: A crosswalk to be used with the CRISTIN oai_dc document. This basically does nothing, as all the CRISTIN work is done by the ORE document ingester

**Configured In**: dspace.cfg

**Configuration**: None

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestFilter 
* no.uio.duo.CristinIngestionWorkflow 
* no.uio.duo.CristinMetadataRemover 
* no.uio.duo.CristinOREImporter 

**Hard-Dependencies**: None

## no.uio.duo.CristinOREImporter 
**Interface**: org.dspace.content.crosswalk.IngestionCrosswalk

**Feature**: OAI Harvesting from Cristin

**Function**: Import items from Cristin using the OAI-ORE metadata format retrieved over OAI-PMH.

**Configured In**: dspace.cfg

**Configuration**: None

**Co-Dependencies**:
* no.uio.duo.CristinBundleVersioningStrategy 
* no.uio.duo.CristinIngestFilter 
* no.uio.duo.CristinIngestionWorkflow 
* no.uio.duo.CristinMetadataRemover 
* no.uio.duo.CristinOAIDCCrosswalk 

**Hard-Dependencies**: None

## no.uio.duo.DSpace18FixedDispatcher 
**Interface**: org.dspace.event.Dispatcher

**Feature**: General Event Handling in DSpace, Required particularly for StudentWeb item withdraw feature

**Function**: Handles processing of events in an error-resistant way (fixing issues with the Basic Dispatcher in DSpace by default)

**Configured In**: dspace.cfg

**Configuration**: None

**Co-Dependencies**: None

**Hard-Dependencies**: None

## no.uio.duo.DuoEmbargoLifter 
**Interface**: org.dspace.embargo.EmbargoLifter

**Feature**: Embargo System in DSpace, Required particularly for lifting embargoes on StudentWeb items withdrawn

**Function**: Lifts embargoes on appropriate items, and applies standard Duo item access policies

**Configured In**: dspace.cfg

**Configuration**: dspace.cfg

**Co-Dependencies**:
* no.uio.duo.DuoInstallConsumer 

**Hard-Dependencies**: None

## no.uio.duo.DuoEntryDisseminator 
**Interface**: org.dspace.sword2.SwordEntryDisseminator

**Feature**: StudentWeb

**Function**: Used when sending metadata from Duo to StudentWeb; provide the embedded metadata for StudentWeb alongside a Dublin Core version of the metadata

**Configured In**: modules/swordv2-server.cfg

**Configuration**: None

**Co-Dependencies**: 
* no.uio.duo.FSBagItIngester 
* no.uio.duo.FSEntryIngester 

**Hard-Dependencies**: None

## no.uio.duo.DuoInstallConsumer 
**Interface**: org.dspace.event.Consumer

**Feature**: StudentWeb

**Function**: Used to embargo/withdraw StudentWeb items with a "fail" grade on submission to the archive

**Configured In**: dspace.cfg

**Configuration**: None

**Co-Dependencies**: None

**Hard-Dependencies**: 
* no.uio.duo.DSpace18FixedDispatcher

## no.uio.duo.FSBagItIngester 
**Interface**: org.dspace.sword2.SwordContentIngester

**Feature**: StudentWeb

**Function**: deal with the BagIt format used by the StudentWeb/FS integration

**Configured In**: modules/swordv2-server.cfg

**Configuration**: None

**Co-Dependencies**:
* no.uio.duo.DuoEntryDisseminator 
* no.uio.duo.FSEntryIngester 

**Hard-Dependencies**: None

## no.uio.duo.FSEntryIngester 
**Interface**: org.dspace.sword2.SwordEntryIngester

**Feature**: StudentWeb

**Function**:  takes the FS metadata as embedded in an atom entry and adds it to an existing object

**Configured In**: modules/swordv2-server.cfg

**Configuration**: modules/studentweb.cfg, modules/swordv2-server.cfg

**Co-Dependencies**:
* no.uio.duo.DuoEntryDisseminator 
* no.uio.duo.FSBagItIngester 

**Hard-Dependencies**: None


