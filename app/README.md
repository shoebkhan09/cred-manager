# CREDENTIAL MANAGER 

Please visit [wiki](https://github.com/GluuFederation/cred-manager/wiki/Cred-Manager-Project-Doc) to learn more. Contents of this [page](https://github.com/GluuFederation/cred-manager/wiki/Technical-considerations) are also worth to look at.

# Preliminaries

## Application Initialization

When starting up, the following is looked up by the application:

* Location of configuration file: A file named `cred-manager.json` is expected to reside inside a `conf` directory placed in a folder whose location is given by the system property *gluu.base* (usually set in the `start.ini` file of the corresponding jetty base of this web app). If system property is not present, default value is /etc/gluu. 

This just follows the same pattern currently used in **oxAuth** and **oxTrust**.

For the first time, **oxd** registration takes place. See the appropriate [section](#oxd-registration).

## Configuration File Structure

Location example: `/etc/gluu/conf/cred-manager.json`

Contents example:
```
{
	"ldap_settings":{
		"ox-ldap_location": "/etc/gluu/conf/ox-ldap.properties",	//Location of ox-ldap.properties file
		"salt" : "/etc/gluu/conf/salt", //Optional. location of salt properties file
		"applianceInum": "@!...",
		"orgInum": "@!..."
	},
	"enable_pass_reset": true,	//optional
	"oxd_config": { "host": "localhost", "port": 8099, "oxd-id": "...", "authz_redirect_uri" : "..." , "post_logout_uri": "..." },	//"oxd-id" is optional
	"enabled_methods": [],		//optional
	"u2f_settings" : {
		"u2f_relative_uri" : "restv1/fido-u2f-configuration",	//optional. Endpoint for registration of fido devices
		"app_id": null	//optional. The U2F app ID
	},
	"branding_path" : "/opt/gluu/jetty/cred-manager/custom"		//optional. Used only if custom branding is required
}
```
Unless otherwise stated, the params in the example are mandatory.

You can find a sample configuration file [here](https://github.com/GluuFederation/cred-manager/blob/master/configurations/cred-manager.json). 

### Notes on parameters inference:
* This app uses `ox-ldap.properties` file to lookup LDAP connection settings. Once connected to LDAP, this app will find:
	* displayName attribute in the `o` branch to determine the organization name
	* oxTrust's oxTrustConfCacheRefresh attribute to determine if a source backend LDAP is enabled ("sourceConfigs" property in JSON). If so, password reset won't be enabled regardless of the value of *"enable_pass_reset"*. If *"enable_pass_reset"* is not provided and there is no enabled backend LDAP detected, a default value of false is assumed.
	* oxAuth's oxAuthConfDynamic attribute to get:
		* the OIDC config endpoint (property "openIdConfigurationEndpoint" in JSON)
		* the issuer attribute
	* The entry corresponding to the OTP custom script to grab configurations needed in case OTP is recognized as an active method for 2FA. The file pointed to by the `opt_conf_file` entry is parsed as well.
	* The entry corresponding to the SMS custom script to grab configurations needed in case SMS is used for 2FA.
	
* app checks Implementation-Version entry in `MANIFEST.MF` file inside `oxauth.war` to guess Gluu version it is running on
* If *"enabled_methods"* is not present, or has empty value or null, then all supported methods will be enabled. The exact set of methods is deduced after inspecting acr_supported_values in the server. Depending on current server setup, this can lead to only password (no 2FA at all).
* If *"oxd-id"* is not present, or has empty value or null, registration of this app with **oxd** will take place.
* If *"u2f_relative_uri"* is not present, or has empty value or null, it will default to ".well-known/fido-u2f-configuration". The value of issuer property found in LDAP followed by a slash (/) will be prepended to this to get a correct endpoint URL for U2F devices enrolling.
* If *"app_id"* is not present, or has empty value or null, it will default to the *issuer* value in oxAuth's oxAuthConfDynamic. Passing an *app_id* is useful for [multi-facets apps](https://developers.yubico.com/U2F/App_ID.html) where an HTTPS URL that resolves to a JSON list of facet IDs needs to be supplied.

### "Hidden" properties
These are extra properties that can be set in the JSON file to tweak certain behaviors. Mostly useful in development and testing scenarios:
* *"gluu_version"*: This will obviate the need for inspecting `oxauth.war` to find out the version being used. Value is provided as string.

## OXD Registration

If *"oxd-id"* is null, empty or non-existing, cred-manager will try to execute the "register site" step with the oxd server whose host and port is already provided in *"oxd_config"*. Once an `oxdId` is grabbed, the app will update the config file so that it contains this value.

For registration, the following are also passed: *authz_redirect_uri*, *post_logout_uri*. All other parameters will be the defaults used by oxd.


## Logging

Log4j2 framework is used and configure via a file named `log4j2.xml` located at `/WEB-INF/classes`. It uses the system property *log.base* (found in the `start.ini` file of the app's jetty base) to determine where to write logs.

[Here](https://github.com/GluuFederation/cred-manager/blob/master/configurations/log4j2.xml) you can find a sample file for `log4j2.xml`.

# Installation

## Requirements

This application requires a working installation of Gluu Server with at least the following: Apache server, LDAP server and oxAuth server. The use of oxTrust is highly recommended.

* Set the following scopes to be default: `openid`, `profile`, `user_name`, and `clientinfo`. This can be done with oxTrust or manually in LDAP by setting the *defaultScope* attribute to **true** for the appropriate entries under the `scopes` branch.
  
* Purchase an oxd [license](https://oxd.gluu.org). You will be given 4 bits of data: license ID, public Key, public password, and license password.


## Oxd setup

Grab a .zip with complete distribution file for oxd from https://ox.gluu.org/maven/org/xdi/oxd-server/. Currently, version used for cred-manager is 3.1.0-SNAPSHOT

Follow the steps found at [oxd installation page](https://www.gluu.org/docs/oxd/install/#unix) to unzip and start. Take into account that license information has to be filled in `oxd-conf.json` file. Update the "op_host" property of oxd-config.json to point to your IDP, e.g. `https://mygluu.host.com`

Oxd can be installed in the same server where the IDP is running.


## Update LDAP schema

TODO

## Jetty base instance configuration

Use these [instructions](http://www.eclipse.org/jetty/documentation/current/quickstart-running-jetty.html) as a guide.

The following switch is recommended: `--add-to-start=jsp,servlets,ssl,http,deploy,https,console-capture`

Create a suitable `cred-manager.json` configuration file for your case and copy it wherever you like. For instance inside `etc` directory of your new jetty  base.

Edit `start.ini` inside the jetty base and add the following:

```
jetty.http.port=<http-port-for-app>
-Dgluu.base=<see section [application initialization](#application-initialization)>
-Dlog.base=<e.g path-to-jetty-base>
```

Replace content between angle brackets accordingly.

[Here](https://github.com/GluuFederation/cred-manager/blob/master/configurations/start.ini) is a sample `start.ini` file.

Make the SSL configurations required for your Jetty instance. This can be done in different ways. You can use [this](http://www.eclipse.org/jetty/documentation/current/configuring-ssl.html) as a guide.

**Note**: Running cred-manager over SSL is a requirement.

## First run of the app

Add a new custom script with name **router** and whose contents are that of file `acr_router.py` that you can find [here](https://github.com/GluuFederation/cred-manager/blob/master/configurations). Set its level to 1. Enable the `basic` script also - search for a script whose name is "basic". Set it to level 2.

Copy the .war file of cred-manager to the jetty base webapps directory. You can copy a exploded directory too.  Depending on previous setup, you may need to issue a command for starting the app, or if hot deployment is used (default behavior in Jetty) this is not needed.

Wait a couple of minutes while the app starts. Check the log directory corresponding to `log.base` system variable to see the progress. You should see a message like "WEBAPP INITIALIZED SUCCESSFULLY" .

Do not hit the URL of the app by now. Open `cred-manager.json`: you should see a new attribute called "oxd-id" appears there. This means a new OIDC client was created for the app to use. This can be verified in oxTrust where a new client appears with the name "cred-manager".

## ACRs configuration

* Enable the custom scripts required for your particular case (this can be done by tweaking LDAP directly or via oxTrust). Ensure that settings of scripts are properly configured - it's recommended to test if they are working fine by logging into oxTrust and changing the authentication method: go to `Manage authentication` > `Default authentication method` > `oxTrust acr`. For a deeper insight, check `oxauth_script.log` of Gluu server. 

* Edit all scripts you have just enabled by changing the default page used in the `getPageForStep` method. That is, find the last line in such method (that looks like `return ""`) and replace by `return "/alter_login.xhtml"`. 

* Copy the custom login pages. These will make the login flow more pleasant for users by using an *identifer-first* approach for authentication. Use the following steps as a guide for this task:
  
  1. Still being logged inside chroot run the following:
  `# service oxauth stop`
  
  2. Copy the files `credmgr_login.xhtml`, `alter_login.page.xml` (if exists), and `alter_login.xhtml` found at [https://github.com/GluuFederation/cred-manager/blob/master/configurations](https://github.com/GluuFederation/cred-manager/blob/master/configurations) to the following location: `/opt/gluu/jetty/oxauth/custom/pages`. Use the version of files matching your Gluu server.
  
  3. Start oxauth:
  `# service oxauth start`
  
  4. Wait a couple of minutes and check the contents of `oxauth_script.log` (found at `/opt/gluu/jetty/oxauth/logs`). You should not see errors there but only successful initialization messages.

* Double check your Gluu server has proper values for *acr_values_supported* in the OIDC metadata document and adjust `cred-manager.json` if you wish to restrict certain types of credentials. Do not provide "enabled_methods" if you just want to be able to use all credentials supported (currently limited to OTP device, verified mobile phone, U2F key, and super gluu device only).

* Once all configurations are applied, you will have to restart the application.

Check the [troubleshooting guide](#troubleshooting) for more information.

# Custom branding

Cred-manager allows administrators to alter the appearance of the application to match their organizations look and feel. Intermediate level knowledge of CSS is required for this task.

## Getting default theme files

The design is driven by a few images and CSS stylesheets. These are inside app's war file so let's extract those to a separate location:

* `cd` to app's war file directory. For instance `/opt/gluu/jetty/cred-manager/webapps`

* Make Java bin directory available in your PATH. The following is an example for your reference:
```
	JAVA_HOME=/opt/gluu-server-3.0.2/opt/jdk1.8.0_112
	export JAVA_HOME
	PATH=$PATH:$JAVA_HOME/bin
```

* Run the following:

```
	$ jar -xf cred-manager.war images
	$ jar -xf cred-manager.war styles
``` 

* Now you should see two new directories appeared. It's required that you transfer those to a folder named **custom**. If this application was bundled with your Gluu Server, there is already a **custom** directory nearby, something like `/opt/gluu/jetty/cred-manager/custom`. In this case the *logs*, *webapps*, and *custom* directories are at the same level. The following is an example to move `images` and `styles`:

```
	$ mv images /opt/gluu/jetty/cred-manager/custom/images
	$ mv styles /opt/gluu/jetty/cred-manager/custom/styles
```

* Ensure the operating system user that runs Jetty has read permissions on the files added. You may do the following to solve it:

```
$ chown -R jetty:jetty /opt/gluu/jetty/cred-manager/custom/
```

Glance at the contents of both directories (`images` and `styles`). If you think that editing those CSS files and replacing some images will customize the look and feel, you are 
on the line.

## Enable the customizations

For cred-manager to read images and styles from the custom directory, you need to supply its location in the configuration file. So please do the following:

* Open `cred-manager.json` and add a property (if it's not already there) called *"branding_path"*, set it to the path of the custom directory. It might look like this:

```
		... ,
		"branding_path": "/opt/gluu/jetty/cred-manager/custom/"
	}
```

Remember that Json *name:value* pairs are separated by commas.

* Do a subtle edition of a file to test if things are going well. For instance, open the file `styles/common.css`, locate the CSS selector for *header* and change the background color with red. Something like this:

```
	.header{
		...
		background: red;
		...
	}
```

* Save `cred-manager.json` and `common.css`

* Close all browser windows where cred-manager is open. Clear your recent browser history (one day), and restart the application.

* Try login and you will see a beautiful red header!

## Applying your customizations

Here you have some tips to take into account for matching your company design:

* This app uses separate stylesheets for desktop and mobile environments (`desktop.css` and `mobile.css` respectively). File `common.css` stores styles that apply for both environments.

* File names for images must remain unchanged, thus, to use an image of your own, you need to replace the current version of the file. This holds for company logo, icons, etc.

* Inspect the DOM tree generated for application pages and determine the CSS selectors you need to edit or the kind of things your have to add in order to alter the appearance. Use your web browser's facilities to inspect web page composition: this is usually part of any browser's developer toolbar. Moreover, they allow you to change styles on the fly so you can play a lot before applying the real changes.

* Once you do editions and add/delete images, there is no need to restart the application to see changes, however most static files are cached by browsers so you will need to clear the browser history for the current day. The `shift+Ctrl+supr` combination does the job in most browsers. Leave the cookies option unchecked so there is no need to login after every refresh.

* Ignore file `jquery-ui-1.12.1.min`. You may even erase the file.

* If you are modifying files/images and not seeing the changes try hitting the resource URL directly in a new browser tab. For example to load the file `common.css` in your browser, you should visit `https://<host-name>/cred-manager/custom/styles/common.css`. That way you can determine if your changes are there; if they are not, strike **`F5`**. Still getting the same content? you are not deleting your cache properly... close all tabs, empty recent cache and try again.

## Reverting to default theme

If for any reason you wish to restore to the default (Gluu Inc) theme. Just open `cred-manager.json` and delete "branding_path" property or simply assign a **null** value for it, like this:

```
		... ,
		"branding_path": null
	}
```

Then, restart the application.

## Some examples

Here you have how to cope with certain common use cases:

### Use a different logo

* Replace the file at `custom/images/logo.png` with your own PNG file.

* Edit the *logo* CSS selector in files `desktop.css` and `mobile.css`. Assign the real dimensions to use for the image when displayed in desktop and mobile browsers. Dimensions should be coherent with the actual width/height ratio of original image.

### Use a different favicon

This the easiest customization: just replace `custom/images/favicon.ico`.

### Change the font used in texts

The vast majority of texts that appear in the application are using the same font. To alter the default font edit the *z-label* CSS selector in files `desktop.css` and `mobile.css`. Change the value for *font-family* (you may also alter *font-size*). Example:

```
	.z-label{
	...
		font-family: courier;
	...
	}
```

### Change colors of buttons

Say you like the buttons in blue, add the following rules to `common.css` (you may append them at the end of the file):

```
	.btn-success, .btn-success:hover, .btn-success:focus, .btn-success:active,
	.btn-success.active, .btn-success.disabled {
		background-color: #00F;
		border-color: #00F;
	}
```

### Adjust width of app's content area for desktop

Edit `desktop.css` file and change *mainDiv* by setting a different value for *width*:

```
	.mainDiv{
		...
			width:600px;
		...
	}
```

Now cred-manager should neatly accomodate to newer width.

# Troubleshooting

In the following some situations that may arise and corresponding solutions are summarized.

**Note: check the logs**

To work properly, cred-manager requires stable dependent components (filesystem, ldap, oxd, oxauth, servlet container...). Thus, it's important to determine if all expected services are working fine. 

At startup, the app gathers a good amount of information from its environment. If something is missing or went wrong, messages will be shown in the log. Some messages may warn you about parameters not supplied that were simply inferred for you. 

During normal use, the app will show feedback to users if operations were successful or failed. In the latter case, the log is also worth to look at to diagnose the anomalies.

## U2F keys enrolling not working from within cred-manager

To be able to enroll u2f keys, ensure all of the following are met:

* You are accessing the application via https (this a requirement by design of fido standard)
* Ensure the IDP URL (*"op_host"* property of oxd-default-site-config.json) matches the same host under which cred-manager is being served. Both FQDNs should match. If you cannot have both oxAuth and cred-manager at the same host, you have to set the property *"app_id"* of cred-manager.json accordingly. For an example see "multi-facet apps" from [https://developers.yubico.com/U2F/App_ID.html](https://developers.yubico.com/U2F/App_ID.html)
* Ensure you are using Chrome, Opera (version greater than 40), or Firefox (with the proper [u2f add-on](https://addons.mozilla.org/en-US/firefox/addon/u2f-support-add-on/) installed) and javascript enabled. These are the only browsers supporting the FIDO U2F technology. Currently cred-manager does not support adding U2F devices from mobile browsers.
* Ensure plugging the security key before pressing the "ready" button: the enrolling process has a timeout period. Ensure you are pressing the key's button when your browser indicates to do so or when the key's button is blinking.

## The error *"incorrect email or password"* is shown when pressing the login button in the SSO form
This reveals a problem in oxAuth, not cred-manager itself. It's highly likely to be a problem with a custom interception script (when a user is trying to use a form of strong authentication to login).

Check if `oxauth_script.log` is showing an error related to the authentication method in question. If it does, check in oxTrust if the parameters employed to configure the custom script are sensible. Visit https://www.gluu.org/docs/ce/, then choose your Gluu version and locate the documentation page associated to the problematic script under `Administration Guide` > `User authentication guide`.

If you still have trouble, please use the [support forum](https://support.gluu.org) to ask for help.

## The user interface is not showing any means to enroll credentials

Ensure the following are met:

* You have enabled in accordance to your needs the proper custom scripts. For instance, if you want to offer users the ability to authenticate using Google Authenticator, you have to enable the script "HOTP/TOPT authentication module"
* You specified a correct value for *"enabled-methods"* in `cred-manager.json`. Leave it empty or null to pick all enabled methods already supported by your Gluu server.

Whenever you enable or disable scripts, please wait a couple of minutes for oxAuth to pick the changes. Then you can restart cred-manager.

## The user interface is not showing means to enroll certain types credentials

Ensure you specified a correct value for *"enabled-methods"* in `cred-manager.json`. Leave it empty or null to pick all enabled methods already supported by your Gluu server.

## The top right-hand menu disappeared from the user interface

Try restoring and then maximizing your browser's window.

## The preferred method for authentication is set to password and cannot be changed

To choose a strong method for authentication, the user has to have enrolled at least two credentials through the app. Only after this is met, he will be able to set his preferred method

## How to reset a user's preferred method of authentication

If a user has locked out for any reason (e.g. lost devices), you can reset his preferred method by deleting the `???` attribute from the user's entry in LDAP. This way, next time he/she enters, **password** will be his new preference and he won't be asked to present additional credentials.

## The error "Unauthorized access" is shown when accessing the application

This is caused by an unathorized access attempt (e.g. users requesting URLs without ever have logged in).

## "An error occurred during authorization step" is shown when accessing the application

This is shown when there is no possible to initiate a "conversation" with the authorization server. Check OXD Server is up and running. Also check oxd settings are properly configured.

## A small error message on the top left of the screen is shown when accessing the application 

The source of the problem can be an interruption in the steps of authentication when second factor is used and this may cause the session to end up in an inconsistent state: close all browser tabs related to cred-manager and oxAuth, then clean your cookies (for instance by doing Ctrl+Shift+supr), and try accessing the (root) URL of the application again.