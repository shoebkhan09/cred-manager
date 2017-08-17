# Credential manager 

Please visit [wiki](https://github.com/GluuFederation/cred-manager/wiki/Cred-Manager-Project-Doc) to learn more. Contents of this [page](https://github.com/GluuFederation/cred-manager/wiki/Technical-considerations) are also worth to look at.


## Application Initialization

When starting up, the following is looked up:

* Location of configuration file: A file named `cred-manager.json` is expected to reside inside a `conf` directory placed in a folder whose location is given by the system property *gluu.base* (usually set in the `start.ini` file of the corresponding jetty base of this web app). If system property is not present, default value is /etc/gluu. 

This just follows the same pattern currently used in **oxAuth** and **oxTrust**.

For the first time, **oxd** registration should take place. See the appropriate [section](#oxd-registration).

## Configuration File Structure

Location example: `/etc/gluu/conf/cred-manager.json`

Contents example:
```
{
	"ldap_settings":{
		"ox-ldap_location": "/etc/gluu/conf/ox-ldap.properties",	//Location of ox-ldap.properties file
		"salt" : "/etc/gluu/conf/salt", //Optional. location of salt properties file
		"applianceInum": "@!3245.DF39.6A34.9E97!0002!CFBE.8F9E",
		"orgInum": "@!3245.DF39.6A34.9E97!0001!513A.9888"
	},
	"enable_pass_reset": true,	//optional
	"oxd_config": { "host": "localhost", "port": 8099, "oxd-id": "...", "authz_redirect_uri" : "..." , "post_logout_uri": "..." },	//"oxd-id" is optional
	"enabled_methods": [],		//optional
	"u2f_relative_uri" : "restv1/fido-u2f-configuration"	//optional. Endpoint for registration of fido devices
}
```
Unless otherwise stated, the params in the example are mandatory

### Notes on parameters inference:
* This app uses `ox-ldap.properties` file to lookup LDAP connection settings. Once connected to LDAP, this app will find:
	* displayName attribute in the `o` branch to determine the organization name
	* oxTrust's oxTrustConfCacheRefresh attribute to determine if a source backend LDAP is enabled ("sourceConfigs" property in JSON). If so, password reset won't be enabled regardless of the value of *"enable_pass_reset"*. If *"enable_pass_reset"* is not provided and there is no enabled backend LDAP detected, a default value of false is assumed.
	* oxAuth's oxAuthConfDynamic attribute to get:
		* the OIDC config endpoint (property "openIdConfigurationEndpoint" in JSON)
		* the issuer attribute
	* The entry corresponding to the OTP custom script to grab configurations needed in case OTP is recognized as an active method for 2FA. The file pointed by the `opt_conf_file` entry is parsed as well.
	
* app checks Implementation-Version entry in `MANIFEST.MF` file inside `oxauth.war` to guess Gluu version it is running on
* If *"enabled_methods"* is not present, or has empty value or null, then all supported methods will be enabled. The exact set of methods is deduced after inspecting acr_supported_values in the server. Depending on current server setup, this can lead to only password (no 2FA at all).
* If *"oxd-id"* is not present, or has empty value or null, registration of this app with **oxd** will take place.
* If *"u2f_relative_uri"* is not present, or has empty value or null, it will default to ".well-known/fido-u2f-configuration" for versions earlier than 3.1.0 or "restv1/fido-u2f-configuration" for version 3.1.0. The value of issuer property found in LDAP followed by a slash (/) will be prepended to this to get a correct endpoint URL for U2F devices enrolling.

### "Hidden" properties
These are extra properties that can be set in the JSON file to tweak certain behaviors. Mostly useful in development and testing scenarios:
* *"gluu_version"*: This will obviate the need for inspecting `oxauth.war` to find out the version being used. Value is provided as string.

## OXD Registration

If *"oxd-id"* is null, empty or non-existing, cred-manager will try to execute the "register site" step with the oxd server whose host and port is already provided in *"oxd_config"*. Once an oxdId is grabbed, the app. will update the config file so that it contains this value.

For registration, the following are also passed: *authz_redirect_uri*, *post_logout_uri*. All other parameters will be the defaults used by oxd.


## Logging

Log4j2 framework is used and configure via a file named `log4j2.xml` located at `/WEB-INF/classes`. It uses the system property *log.base* (found in the `start.ini` file of the corresponding jetty base of this app) to determine where to write logs.

## Installation

### Requisites

This application requires a working installation of Gluu Server with at least the following: Apache server, LDAP server and oxAuth server. The use of oxTrust is highly recommended.

* Enable the custom scripts required for your particular case (this can be done by tweaking LDAP directly or via oxTrust). Ensure that settings of scripts are properly configured - it's recommended to test if they are working fine by logging in to oxTrust by changing the authentication method: go to `Manage authentication` > `Default authentication method` > `oxTrust acr`.

For a deeper insight, check oxauth_script.log of gluu server.

* Set the following scopes to be default: openid, profile, user_name, email, mobile_phone, phone, and clientinfo. This can be done with oxTrust or manually in LDAP by setting the defaultScope attribute to true for the appropriate entries under the branch of `scopes`.

* Purchase an oxd license. You will be given 4 bits of data: license ID, public Key, public password, and license password.

### Oxd setup

Grab a .zip with complete distribution file for oxd from https://ox.gluu.org/maven/org/xdi/oxd-server/. Currently, version used for cred-manager is 3.1.0-SNAPSHOT

Follow the steps found at [oxd installation page](https://www.gluu.org/docs/oxd/install/#unix) to unzip and start. Take into account that license information has to be filled in `oxd-conf.json` file. Update the "op_host" property of oxd-config.json to point to your IDP, e.g. `https://mygluu.host.com`

Oxd can be installed in the same server where the IDP is running.


### Update LDAP schema


### Jetty base instance configuration

Use these [instructions](http://www.eclipse.org/jetty/documentation/current/quickstart-running-jetty.html) as a guide.

The following switch is recommended: `--add-to-start=jsp,servlets,ssl,http,deploy,https,console-capture`

Create a suitable cred-manager.json configuration file for your case and copy it wherever you like. For instance inside `etc` directory of your new jetty  base.

Edit `start.ini` inside the jetty base and add the following:

```
jetty.http.port=<http-port-for-app>
-Dgluu.base=<see section [application initialization](#application-initialization)>
-Dlog.base=<e.g path-to-jetty-base>
```

Replace content between angle brackets accordingly.

Make the SSL configurations required for your Jetty instance. This can be done in different ways. Use [this](http://www.eclipse.org/jetty/documentation/current/configuring-ssl.html) as a guide.

Running cred-manager over SSL is a requirement.

### First run of the app

Copy the .war file of cred-manager to the jetty base webapps directory. You can copy a exploded directory too.  Depending on previous setup, you may need to issue a command for starting the app, or if hot deployment is used (default behavior in Jetty) this is not needed. Wait a couple of minutes while the app starts. Check the log directory corresponding to `log.base` system variable to see the progress.

Once you see a message like "WebApp initialized successfully" you can hit the URL of the app. You will be redirected to the IDP for username and password (depending on settings specified in `cred-manager.json`).  Do not enter any credentials by now.

Open cred-manager.json, you should see a new attribute called "oxd-id" appears there. This means a new OIDC client was created for the app to use. This can be verified in oxTrust where a new client will appear with the name "cred-manager".

Double check your Gluu server has proper acr_values_supported in the OIDC metadata document and adjust `cred-manager.json` if you wish to restrict certain types of credentials. Do not provide "enabled_methods" if you just want to be able to use all credentials supported (currently limited to OTP device, verified mobile phone, U2F key, and super gluu device only).

Once changes are saved, you will have to restart the application.

Check the (troubleshooting guide)[#troubleshooting] for more information

## Troubleshooting

In the following some situations that may arise and corresponding solutions are summarized.

**Note: check the logs**

To work properly, cred-manager requires stable dependent components (filesystem, ldap, oxd, oxauth, servlet container...). Thus, it's important to determine if all expected services are working fine. 

At startup, the app gathers a good amount of information from its environment. If something is missing or went wrong, messages will be shown in the log. Some messages may warn you about parameters not supplied that were simply inferred for you. 

During normal use, the app will show feedback to users if operations were successful or failed. In the latter case, the log is also worth to look at to diagnose the anomalies.

### U2F keys enrolling not working from within cred-manager

To be able to enroll u2f keys, ensure all of the following are met:

* You are accessing the application via https (this a requirement by design of fido standard)
* Ensure the IDP URL (*"op_host"* property of oxd-default-site-config.json) matches the same host under which cred-manager is being served. Both FQDNs should match. If you cannot have both oxAuth and cred-manager at the same host, you have to set the property *"facet-id-url"* of cred-manager.json accordingly. For an example see "multi-facet apps" from [https://developers.yubico.com/U2F/App_ID.html](https://developers.yubico.com/U2F/App_ID.html)
* Ensure you are using Chrome, Opera (version greater than 40), or Firefox (with the proper [u2f add-on](https://addons.mozilla.org/en-US/firefox/addon/u2f-support-add-on/) installed) and javascript enabled. These are the only browsers supporting the FIDO U2F technology. Currently cred-manager does not support adding U2F devices from mobile browsers.
* Ensure plugging the security key before pressing the "ready" button: the enrolling process has a timeout period. Ensure you are pressing the key's button when your browser indicates to do so or when the key's button is blinking.

### The error *"incorrect email or password"* is shown when pressing the login button in the SSO form
This reveals a problem in oxAuth, not cred-manager itself. It's highly likely to be a problem with a custom interception script (when a user is trying to use a form of strong authentication to login).
Check if oxauth_script.log is showing an error related to the authentication method in question. If it does, check in oxTrust the parameters employed to configure the custom script are sensible. Visit https://www.gluu.org/docs/ce/, then choose your Gluu version and locate the documentation page associated to the problematic script under `Administration Guide` > `User authentication guide`.
If you still have trouble, please use the [support forum](https://support.gluu.org) to ask for help.

### The user interface is not showing any means to enroll credentials

Ensure the following are met:

* You have enabled in accordance to your needs the proper custom scripts. For instance, if you want to offer users the ability to authenticate using Google Authenticator, you have to enable the script "HOTP/TOPT authentication module"
* You specified a correct value for *"enabled-methods"* in `cred-manager.json`. Leave it empty or null to pick all enabled methods already supported by your Gluu server.

Whenever you enable or disable scripts, please wait a couple of minutes for oxAuth to pick the changes. Then you can restart cred-manager.

### The user interface is not showing means to enroll certain types credentials

Ensure you specified a correct value for *"enabled-methods"* in `cred-manager.json`. Leave it empty or null to pick all enabled methods already supported by your Gluu server.

### The preferred method for authentication is set to password and cannot be changed

To choose a strong method for authentication, the user has to have enrolled at least two credentials through the app. Only after this is met, he will be able to set his preferred method

### How to reset a user's preferred method of authentication

If a user has locked out for any reason (e.g. lost devices), you can reset his preferred method by deleting the `???` attribute from the user's entry in LDAP. This way, next time he/she enters, **password** will be his new preference and he won't be asked to present additional credentials.

### When accessing the application I get no more than a small error message on the top left of the screen

This can be caused by an unathorized access attempt (e.g. users requesting URLs without ever have logged in). 
Another source of the problem can be an interruption in the steps of authentication when second factor is used and this may cause the session to have an inconsistent state: close all browser tabs related to cred-manager and oxAuth, then clean your cookies (for instance by doing Ctrl+Shift+supr), and try accessing the (root) URL of the application again.

	