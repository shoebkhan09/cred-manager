# cred-manager
Application for a person to manage their trusted 2FA credentials that can be used to access resources protected by the Gluu Server. Supported authentication mechanisms include:

- U2F security keys (like Yubikeys)             
- Super Gluu mobile authentication (https://super.gluu.org)
- OTP mobile apps (like Google Authenticator)            
- Mobile phone numbers that can receive OTPs via SMS messages
- Passwords (only if stored in the associated Gluu Server's local OpenLDAP user store)                  

See [doc page](https://gluu.org/docs/creds/) to learn more