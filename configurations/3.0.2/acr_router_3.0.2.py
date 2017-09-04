# oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
# Copyright (c) 2016, Gluu
#
# Author: Jose Gonzalez (based on acr_routerauthenticator.py)
#
# Notes:
#  * This script is not using /login.xhtml for first step (see getPageForStep)
#

from org.jboss.seam.security import Identity
from org.xdi.model.custom.script.type.auth import PersonAuthenticationType
from org.xdi.oxauth.service import UserService, AuthenticationService, SessionStateService
from org.xdi.util import StringHelper
from java.util import Arrays

import java

class PersonAuthentication(PersonAuthenticationType):
    def __init__(self, currentTimeMillis):
        self.currentTimeMillis = currentTimeMillis

    def init(self, configurationAttributes):
        print "ACR Router. Initialization"
        print "ACR Router. Initialized successfully"
        return True   

    def destroy(self, configurationAttributes):
        print "ACR Router. Destroy"
        print "ACR Router. Destroyed successfully"

        return True

    def getApiVersion(self):
        return 1

    def isValidAuthenticationMethod(self, usageType, configurationAttributes):
    	print "ACR Router. isValidAuthenticationMethod called"
        return False

    def getAlternativeAuthenticationMethod(self, usageType, configurationAttributes):
        print "ACR Router. getAlternativeAuthenticationMethod"

        identity = Identity.instance()
        user_name = identity.getCredentials().getUsername()       
        #user_password =identity.getCredentials().getPassword()
        print "ACR Router. Authenticating user %s" % user_name

	sessionService=SessionStateService.instance()
	attributes=sessionService.getSessionAttributes(sessionService.getSessionState())
	attributes.put("roUserName",user_name)
	print "session attrs %s" % str(attributes.size())
        #authenticationService = CdiUtil.bean(AuthenticationService)
        #logged_in = authenticationService.authenticate(user_name, user_password)
        acr = None
        logged_in=True
        if logged_in:	    
	    try:
	        userService = UserService.instance()
	        foundUser = userService.getUserByAttribute("uid", user_name)
	        
	        if (foundUser == None):
	            print "ACR Router. User does not exist"
	            return ""
	            
	        acr=foundUser.getAttribute("description")
	        #acr="u2f" 
	        #acr="otp" 
	        #acr="twilio_sms" 
	        if (acr == None):
	            acr = "auth_ldap_server"
            except:
                print "ACR Router. Error looking up user or his preferred method"        
        else:
            print "ACR Router. Error authenticating user"

	print "ACR Router. new acr value %s" % acr
        return acr

    def authenticate(self, configurationAttributes, requestParameters, step):
        return False

    def prepareForStep(self, configurationAttributes, requestParameters, step):
        print "ACR Router. prepareForStep %s" % str(step)
        return True

    def getExtraParametersForStep(self, configurationAttributes, step):
        print "ACR Router. getExtraParametersForStep %s" % str(step)  
        return None

    def getCountAuthenticationSteps(self, configurationAttributes):
        print "ACR Router. getCountAuthenticationSteps called"
        return 2

    def getPageForStep(self, configurationAttributes, step):
        print "ACR Router. getPageForStep called %s" % str(step)
        if step == 1:
            return "/credmgr_login.xhtml"
        return ""

    def logout(self, configurationAttributes, requestParameters):
        print "ACR Router. logout called"
        return True
