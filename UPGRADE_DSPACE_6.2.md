# Upgrade to DSpace 6.2

* Follow the DSpace 6 install docs
* Use local.cfg.DUO
* Install the metadata registries

richard@holly:/srv/dspace62/bin$ ./dspace registry-loader -metadata ../config/registries/cristin-metadata.xml 
richard@holly:/srv/dspace62/bin$ ./dspace registry-loader -metadata ../config/registries/duo-metadata.xml 
richard@holly:/srv/dspace62/bin$ ./dspace registry-loader -metadata ../config/registries/fs-metadata.xml 

* Set up the Cristin Collection in
    * input-forms.xml
    * workflow.xml

