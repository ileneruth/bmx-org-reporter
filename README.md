# bmx-org-reporter
Generates csv files with lists of users, applications and services in every Bluemix organization that you manage.

## Run the Utility
You can simply download the executable jar BmxLookup.jar and run it with the command:
``
java -jar BmxLookup.jar
``

You will be prompted for a region and for your Bluemix credentials.

The utility will then lookup all the organizations you manage in the region, and prompt you to collect information for each organization or skip.

As the utility runs, all the REST calls that it is making are dumped to output.  These are all GETs and no changes are made to any Bluemix organization or space during the execution of the program.

One file named ``BMXOrganizations_<region>.csv`` will be generated.  The columns are:

* Bluemix Organization
* User
* Manager
* Billing Manager
* Auditor

For each user in the organization, an X in the Manager, Developer and Auditor columns will indicate if they have that role.

All Organizations will be listed in the same file, one after the other.

For every organization, another file will be generated with the name ``BMXSpaces_<org>_<region>.csv``.

This file includes information about users, apps and services in each space.  In the first section, columns are:

* Bluemix Organization
* Space
* User
* Developer
* Manager
* Auditor

For every space, all users are listed and their roles.

In the second section, the columns are:

* Bluemix organization
* Space
* Application
* Application State

For every space, all applications and their state are listed.

In the third section, the columns are:

* Bluemix organization
* Space
* Service Instance Name
* Service type

For every space, all provisioned services are listed.

## Look at the code

Code is very rough, poorly documented Java that makes a lot of REST calls and does a lot of looping.

Feel free to have a look.
