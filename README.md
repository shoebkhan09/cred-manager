**Official documentation and installation instructions on the way--ETA: January 8, 2018.**

# cred-manager
Application for a person to manage their trusted 2FA credentials that can be used to access resources protected by the Gluu Server. Supported authentication mechanisms include:

- U2F security keys (like Yubikeys)             
- Super Gluu mobile authentication (https://super.gluu.org)
- OTP mobile apps (like Google Authenticator)            
- Mobile phone numbers that can receive OTPs via SMS messages
- Passwords (only if stored in the associated Gluu Server's local OpenLDAP user store)                  

See [wiki](https://github.com/GluuFederation/cred-manager/wiki/Cred-Manager-Project-Doc) to learn more

* *imgs* directory hosts images already used in wiki.
* *prototype* directory is a maven project with sources of prototype built in earlier stages of project
* *app* contains the actual code of project. The Readme file and developer notes have been moved to separate repo of [docs](https://github.com/GluuFederation/docs-credmanager-prod/tree/1.0.0-beta-1)
* *configurations* contains sample files for configuring/deploying the application. Includes custom scripts and pages for Gluu server 3.0.2 and 3.1.0
