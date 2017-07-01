# Upgrade 2.0.x -> 3.0

This file contains notes about how to upgrade from duo 2.0.x to duo 3.0

## Update dspace.cfg

Note the following changes

* addition of configuration for plugin.single.org.dspace.embargo.EmbargoSetter
* new configuration values: plugin.named.org.dspace.embargo.EmbargoSetter and duo.embargo.communities


## Update modules/curate.cfg

This is a new file in duo 3.0, to extend the existing file in DSpace

* Add "no.uio.duo.policy.DuoPolicyCurationTask = duopolicy" to plugin.named.org.dspace.curate.CurationTask
* Add "duopolicy = Apply Duo Policy Pattern" to ui.tasknames

You will be able to execute the task from the UI with the name "Apply Duo Policy Pattern".  

You will also be able to execute the task from the command line with:

    [dspace]/bin/dspace curate -t duopolicy -i [community/collection/item handle]
    
