# Developer notes

## About technologies

Credential manager is a Java EE 7 web module. For development the following was (and is still) used:

* Build tool: maven 3
* Java SE version (for development & runtime): 1.8.
* Contexts and Dependency Injection framework: Weld
* Application container: Jetty 9.3 or higher
* IDE: IntelliJ IDEA (should work on any other)

To be able to set up a working development/testing environment, a Gluu Server development/testing environment is required. The project should run well in versions 3.0.2 and 3.1.0. Current maven dependencies used are `3.1.0.Final` (see `pom` file of project in the [repo](https://github.com/GluuFederation/cred-manager/tree/master/app/)).

## Project composition

The projects consists of five Java packages. Most classes use a good amount of existing libraries found in Gluu's maven repository.

### Top-level package

*org.gluu.credmanager*

### User Interface package

Classes here serve the purpose of tying web UI components to the server side.

*ui.vm*: ZK's viewmodel POJOs (cred-manager uses the MVVM pattern, see [ZK documentation](https://www.zkoss.org/documentation) 

*ui.model*: Model classes (in MVVM pattern)

See also the [UI framework section](#ui-framework).


### Core packages

*core* 

*core.init*: Initialization of web-app, sessions, etc 

*core.ortho*: cross-cutting tasks (e.g. logging & auditing): concerns which are orthogonal to the application, e.g. implemented as weld Interceptors 

*core.navigation*: Session management and page navigation 

*core.credential*: POJOs that represent different credential types


### Services packages

*service*: Java classes that encapsulate important business logic and interactions with the outside (e.g. oxd-server, oxauth endpoints, ldap, etc).


### Configuration package

*conf*: Contains classes required to describe and store all kind of configurations used by the application.
	
### Miscelaneous package

*misc*: Utility classes that contains methods to facilitate tasks such as collections handling or bean introspection.


## UI Framework

Given the project's requirements regarding to usability, mobile-friendliness, responsiveness, and so on, the [ZK Community Edition](https://www.zkoss.org) framework was chosen. Advantages to highlight from it: it's open source, widely adopted, and has plenty of documentation. Additionally, it shows a good compatibility level with the toolset already used by Gluu developers and have also deserved good reviews from authoritative sources.

Pages built with ZK are lightweight, still preserving a Java backend binding typical of JSF interfaces. The lifecycle of ZK pages and components is simpler than its counterpart (JSF) but still resembles many aspects of JSF such as interfaces defined in terms of XML tags and usage of EL expressions. So it's basically less overhead and complexity using similar dialects.

Depending on the approach taken (there are many), resulting interfaces may end up looking a lot like facelets. Nonetheless, **.zul** pages (file extension used in ZK) can include features similar to those found in JSP, JSTL, taglibs or even static approaches such as (X)HTML+CSS. If needed, scripting can also take place with a variety of languages such Groovy, Ruby, JavaScript and Python.

The following document is the recommended reading to be able to edit, change or add behavior to cred-manager's UI: *ZK MVVM reference*. This can be found in PDF format from ZK's website. It's important to understand the operation flow and concepts of the MVVM pattern before undertaking any task.

The following documents are of general reference for developers; they can be found also at ZK's website and are listed in order of relevance:

* ZK Developer Reference
* ZK ZUML Reference
* ZK Component Reference

In the maven's pom file of cred-manager, the version of the framework actually being used can be seen as well as dependencies stuff.

Despite ZK does a lot of Ajax without requiring developers to know about Javascript, this app includes some javascript files that enable the integration of existing javascript files of Gluu with the framework. Fortunately, they are fairly simple: see the files ending in *-util.js* at https://github.com/GluuFederation/cred-manager/tree/master/app/src/main/webapp/scripts

## Weld

Cred-manager uses weld for CDI. The single-jar approach (weld-servlet-shaded) was embraced for simplicity. In weld jargon this application is an implicit bean archive (see file `beans.xml` in WEB-INF folder). 

Most of POJOs in the package `org.gluu.credmanager.services` are managed beans and encapsulate a lot of business logic. To expose them to other application's classes a mashup of services was created as a session-scoped bean, see `org.gluu.credmanager.services.ServiceMashup` so most injections are there.

The following image from the `weld-probe` app shows the most important managed beans in cred-manager and their associated dependencies:

![dependency graph](https://github.com/GluuFederation/cred-manager/imgs/weld_graph_20170825.png)

## Objects stored in session

ZK templates (.zul files) support bindings to managed bean properties through EL names as in JSF. Also, ViewModel classes (think of classic JSF backing-beans) are supposed to support normal dependency injection via the ZK's `@WireVariable` annotation, unfortunately, this does not hold for the Jetty server, thus, the `ServiceMashup` instance cannot be injected in ViewModel classes and was stored as a session attribute. See the class `org.gluu.credmanager.core.init.SessionListener` to learn more.

In addition, an instance of the class `org.gluu.credmanager.core.User` is stored in the session. All ViewModel classes use this object specially in conjunction with the `org.gluu.credmanager.services.UserService` bean to do most of credential management (CRUD).

## Logging

Log4j2 framework is used and configured via file `log4j2.xml` located at `/WEB-INF/classes`. It uses the system property *log.base* (found in the `start.ini` file of the app's jetty base) to determine where to write logs.

[Here](https://github.com/GluuFederation/cred-manager/blob/master/configurations/log4j2.xml) you will find a sample file for `log4j2.xml`.


## Installation and configuration instructions

Follow the instructions given in the [administration guide](admin-guide.md). There you will find details regarding to the setup of components required by this application, e.g. oxd-server, and LDAP.