# Credential manager 

Please visit [wiki](https://github.com/GluuFederation/cred-manager/wiki/Cred-Manager-Project-Doc) to learn more. Most stuff here is correlated with the contents of this [page](https://github.com/GluuFederation/cred-manager/wiki/Technical-considerations).

## Java Packages Hierarchy

#### Top-level package
*org.gluu.credmanager*

#### User Interface packages

*ui.vm*: ZK's viewmodel (cred-manager uses the MVVM pattern, see [ZK documentation](https://www.zkoss.org/documentation)) 

*ui.model*: Model classes (in MVVM pattern)

#### Core logic packages

*core* 

*core.init*: Initialization of web-app, sessions, etc 

*core.ortho*: cross-cutting tasks (e.g. logging & auditing): concerns which are orthogonal to the application, e.g. implemented as WELD Interceptors 

*core.navigation*: Session management and page navigation 

#### Services packages

*services.ldap* 

*services.scim* 

*services.oxd* 

*services.filesystem* 

*services.oxauth* 

*services.sms* 

#### Configuration package

*conf*: Contains an application-scoped singleton to hold/sync config params
	
#### Miscelaneous package

*misc*: Contains static classes with utilities, wraps oxcore functionality

	
### Notes

Whenever possible:

*ui* uses: *conf*, *core* 

*core* uses: *conf*, *service*, *misc*

## Application Initialization

When starting up, the following is looked up:

* Location of configuration file: A file named `cred-manager.json` is expected to reside inside a `conf` directory placed in a folder whose location is given by the system property *gluu.base* (usually set in the `start.ini` file of the corresponding jetty base of this web app). If system property is not present, default value is /etc/gluu. 

This just follows the same pattern currently used in **oxAuth** and **oxTrust**.

For the first time, **oxd** registration should take place. See the appropriate section.

## Configuration File Structure

Location example: `/etc/gluu/conf/cred-manager.json`
Contents example:
```
{
	"ldap_settings":{
		"ox-ldap_location": "/etc/gluu/conf/ox-ldap.properties",	//mandatory. location of ox-ldap.properties file
		"salt" : "/etc/gluu/conf/salt", //Optional. location of salt properties file
		"applianceInum": "@!3245.DF39.6A34.9E97!0002!CFBE.8F9E",
		"orgInum": "@!3245.DF39.6A34.9E97!0001!513A.9888"
	},
	"enable_pass_reset": true,	//optional
	"oxd_config": { "host": "localhost", "port": 8099, "oxd-id": "...", "authz_redirect_uri" : "..." , "post_logout_uri": "..." },	//mandatory (except for "oxd-id")
	"enabled_methods": [],		//optional
	"twilio_settings": { "account_sid": "", "auth_token" : "", "from_number": }	//optional. Provide if sms was added in enabled_methods or if inferred methods will contain SMS
}
```

### Notes on parameters inference:
* This app uses `ox-ldap.properties` file to lookup LDAP connection settings. Once connected to LDAP, this app will find:
	* displayName attribute in the `o` branch to determine the organization name
	* oxTrust's oxTrustConfCacheRefresh attribute to determine if a source backend LDAP is enabled ("sourceConfigs" property in JSON). If so, password reset won't be enabled regardless of the value of *"enable_pass_reset"*. If *"enable_pass_reset"* is not provided and there is no enabled backend LDAP detected, a default value of false is assumed.
	* oxAuth's oxAuthConfDynamic attribute to get:
		* the OIDC config endpoint (property "openIdConfigurationEndpoint" in JSON)
* app checks Implementation-Version entry in `MANIFEST.MF` file inside `oxauth.war` to guess Gluu version it is running on
* If *"enabled_methods"* is not present, or has empty value or null, then all supported methods will be enabled. The exact set of methods is deduced after inspecting acr_supported_values in the server. Depending on current server setup, this can lead to only password (no 2FA at all).
* If *"oxd-id"* is not present, or has empty value or null, registration of this app with **oxd** will take place.

### "Hidden" properties
These are extra properties that can be set in the JSON file to tweak certain behaviors. Mostly useful in development and testing scenarios:
* *"gluu_version"*: This will obviate the need for inspecting `oxauth.war` to find out the version being used. Value is provided as string.

## OXD Registration

If *"oxd-id"* is null, empty or non-existing, cred-manager will try to execute the "register site" step with the oxd server whose host and port is already provided in *"oxd_config"*. Once an oxdId is grabbed, the app. will update the config file so that it contains this value.

For registration, the following are also passed: *authz_redirect_uri*, *post_logout_uri*. All other parameters will be the defaults used by oxd.


## Logging

Log4j2 framework is used and configure via a file named `log4j2.xml` located at `/WEB-INF/classes`. It uses the system property *log.base* (found in the `start.ini` file of the corresponding jetty base of this app) to determine where to write logs.
