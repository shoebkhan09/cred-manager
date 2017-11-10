# oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
# Copyright (c) 2016, Gluu
#
# Author: Jose Gonzalez 

from org.xdi.oxauth.security import Identity
from org.xdi.oxauth.service import AuthenticationService, UserService, SessionIdService
from org.xdi.model.custom.script.type.auth import PersonAuthenticationType
from org.xdi.oxauth.model.config import Constants
from org.xdi.oxauth.service.fido.u2f import DeviceRegistrationService
from org.xdi.oxauth.client.fido.u2f import FidoU2fClientFactory
from org.xdi.util import StringHelper
from org.xdi.service.cdi.util import CdiUtil
from org.xdi.oxauth.util import ServerUtil
from org.xdi.oxauth.service.custom import CustomScriptService
from org.xdi.model.custom.script import CustomScriptType
from java.util import Collections, HashMap, ArrayList, Arrays

from com.twilio import Twilio
import com.twilio.rest.api.v2010.account.Message as TwMessage
from com.twilio.type import PhoneNumber
from org.codehaus.jettison.json import JSONArray
from com.google.common.io import BaseEncoding

from com.lochbridge.oath.otp import TOTP
from com.lochbridge.oath.otp import HOTP
from com.lochbridge.oath.otp import HOTPValidationResult
from com.lochbridge.oath.otp import HOTPValidator
from com.lochbridge.oath.otp import HmacShaAlgorithm

from com.lochbridge.oath.otp.keyprovisioning import OTPAuthURIBuilder
from com.lochbridge.oath.otp.keyprovisioning import OTPKey
from com.lochbridge.oath.otp.keyprovisioning.OTPKey import OTPType

from java.util.concurrent import TimeUnit

from org.gluu.jsf2.message import FacesMessages
from javax.faces.application import FacesMessage

import random
import java
import urllib
import sys

try:
    import json
except ImportError:
    import simplejson as json
    
class PersonAuthentication(PersonAuthenticationType):
    def __init__(self, currentTimeMillis):
        self.currentTimeMillis = currentTimeMillis

    def init(self, configurationAttributes):
     
        self.ACR_SG='super_gluu'
        self.ACR_SMS='twilio_sms'
        self.ACR_OTP='otp'
        self.ACR_U2F='u2f'
        self.ACRs=[self.ACR_SG, self.ACR_SMS, self.ACR_OTP, self.ACR_U2F]
        self.u2f_app_id=configurationAttributes.get("u2f_app_id").getValue2()
        self.supergluu_app_id=configurationAttributes.get("supergluu_app_id").getValue2()

        print "Cred-manager. init"        
        custScriptService=CdiUtil.bean(CustomScriptService)
        scriptsList = custScriptService.findCustomScripts(Collections.singletonList(CustomScriptType.PERSON_AUTHENTICATION), "oxConfigurationProperty", "displayName", "gluuStatus")

        cfgMap=HashMap()
        for custom_script in scriptsList:
            sname=custom_script.getName()
            if custom_script.isEnabled() and (sname in self.ACRs):
                innermap=HashMap()
                for prop in custom_script.getConfigurationProperties():
                    innermap.put(prop.getValue1(), prop.getValue2())
                cfgMap.put(sname, innermap)
 
        if cfgMap.keySet().contains(self.ACR_OTP):
            if not self.loadOtpConfigurations(cfgMap.get(self.ACR_OTP).get("otp_conf_file"), cfgMap):
                print "Cred-manager. init. Problem parsing otp configs, check custom script settings"
                cfgMap.remove(self.ACR_OTP)
                
        self.scriptsConfig=cfgMap
        
        print "Cred-manager. init. Loaded configs %s" % cfgMap.keySet().toString()
        print "Cred-manager. init. Initialized successfully"
        return True   

    def destroy(self, configurationAttributes):
        print "Cred-manager. Destroy"
        print "Cred-manager. Destroyed successfully"

        return True

    def getApiVersion(self):
        return 2

    def isValidAuthenticationMethod(self, usageType, configurationAttributes):
        print "Cred-manager. isValidAuthenticationMethod called"
        return True

    def getAlternativeAuthenticationMethod(self, usageType, configurationAttributes):
        return None

    def authenticate(self, configurationAttributes, requestParameters, step):
        
        print "Cred-manager. authenticate %s" % str(step)
        authenticationService = CdiUtil.bean(AuthenticationService)
        identity = CdiUtil.bean(Identity)
        
        facesMessages = CdiUtil.bean(FacesMessages)
        facesMessages.setKeepMessages()

        if step == 1:
            credentials = identity.getCredentials()
            user_name = credentials.getUsername()
            user_password = credentials.getPassword()
            
            if StringHelper.isNotEmptyString(user_name) and StringHelper.isNotEmptyString(user_password):
                logged_in = authenticationService.authenticate(user_name, user_password)
                
                if logged_in:
                    userService = CdiUtil.bean(UserService)
                    foundUser = userService.getUserByAttribute("uid", user_name)
                    acr=foundUser.getAttribute("oxPreferredMethod")
                    
                    identity.setWorkingParameter("skip2FA", acr == None)
                    if acr == None:
                        return True
                    else:
                        identity.setWorkingParameter("ACR", acr)
                        
                        if not (acr in self.ACRs):
                            print "%s not a valid cred-manager acr" % acr

                        return (acr in self.ACRs)
            
                return False
            else:
                return False
        elif step == 2:
            session_attributes=identity.getSessionId().getSessionAttributes()
            acr = session_attributes.get("ACR")
            alter = ServerUtil.getFirstValue(requestParameters, "alternativeMethod")
            
            user = authenticationService.getAuthenticatedUser()
            if user == None:
                print "Cred-manager. authenticate. Cannot retrieve logged user"
                return False
            
            identity.setWorkingParameter("methods", self.getAvailMethodsUser(user, acr))
            
            #bypass authentication if an alternative method was provided. This step will be retried (see getNextStep)
            if alter!=None:
                return True
            elif acr==self.ACR_U2F:
                token_response = ServerUtil.getFirstValue(requestParameters, "tokenResponse")
                
                if token_response == None:
                    print "Cred-manager. authenticate. tokenResponse is empty"
                    return False
                
                success=self.finishU2fAuthentication(user.getUserId(), token_response)
                print "Cred-manager. authenticate. U2F finish authentication result was %s" % success
                return success
            
            elif acr==self.ACR_SMS:
                code=session_attributes.get("randCode")
                form_passcode = ServerUtil.getFirstValue(requestParameters, "passcode")
                
                if form_passcode!=None and code==form_passcode:
                    print "Cred-manager. authenticate. 6-digit code matches with code sent via SMS"
                    return True
                else:
                    facesMessages.add(FacesMessage.SEVERITY_ERROR, "Wrong code entered")
                    return False
                
            elif acr==self.ACR_OTP:
                otpCfg=self.scriptsConfig.get(self.ACR_OTP)
                otpCode = ServerUtil.getFirstValue(requestParameters, "loginForm:otpCode")
                success=False
                
                if otpCfg.get("otp_type") == "hotp":
                    success=self.processHotpAuthentication(user, otpCode)
                elif otpCfg.get("otp_type") == "totp":
                    success=self.processTotpAuthentication(user, otpCode)              
                
                if not success:
                    facesMessages.add(FacesMessage.SEVERITY_ERROR, "Wrong code entered")
                return success
                
        return False

    def prepareForStep(self, configurationAttributes, requestParameters, step):
        print "Cred-manager. prepareForStep %s" % str(step)
        if step==1:
            return True
        elif step==2:
            identity=CdiUtil.bean(Identity)
            session_attributes = identity.getSessionId().getSessionAttributes()
            
            authenticationService = CdiUtil.bean(AuthenticationService)
            user = authenticationService.getAuthenticatedUser()
            
            if user==None:
                print "Cred-manager. prepareForStep. Cannot retrieve logged user"
                return False
                        
            acr=session_attributes.get("ACR")
            print "Cred-manager. prepareForStep. ACR=%s" % acr
            identity.setWorkingParameter("methods", self.getAvailMethodsUser(user, acr))
            
            if acr==self.ACR_U2F:
                authnRequest=self.getU2fAuthnRequest(user.getUserId())
                identity.setWorkingParameter("fido_u2f_authentication_request", authnRequest)
                return True        

            elif acr==self.ACR_SMS:
                mobiles = user.getAttributeValues("mobile")                 
                code = random.randint(100000, 999999)
                identity.setWorkingParameter("randCode", code)
                
                twilioCfg=self.scriptsConfig.get(self.ACR_SMS)
                for numb in mobiles:
                    try:                        
                        Twilio.init(twilioCfg.get("twilio_sid"), twilioCfg.get("twilio_token"))
                        print "Cred-manager. prepareForStep. Sending SMS message (%s) to %s" % (code, numb)
                        message = TwMessage.creator(PhoneNumber(numb), PhoneNumber(twilioCfg.get("from_number")), str(code)).create()
                        print "Cred-manager. prepareForStep. Message Sid: %s" % message.getSid()
                    except:
                        print "Cred-manager. prepareForStep. Error sending message", sys.exc_info()[1]
                return True

        return True

    def getExtraParametersForStep(self, configurationAttributes, step):
        print "Cred-manager. getExtraParametersForStep %s" % str(step)  
        if step==2:
            return Arrays.asList("ACR", "randCode", "methods")
        return None

    def getCountAuthenticationSteps(self, configurationAttributes):
        print "Cred-manager. getCountAuthenticationSteps called"
        
        if CdiUtil.bean(Identity).getWorkingParameter("skip2FA"):
           return 1
        return 2

    def getPageForStep(self, configurationAttributes, step):
        print "Cred-manager. getPageForStep called %s" % str(step)
                
        if step == 2:
            acr=CdiUtil.bean(Identity).getWorkingParameter("ACR")
            print "Cred-manager. getPageForStep ACR=%s" % acr
            if acr == self.ACR_SMS:
                page = "/cm/twiliosms.xhtml"
            elif acr == self.ACR_U2F:
                page = "/cm/login.xhtml"
            elif acr == self.ACR_OTP:
                page = "/cm/otplogin.xhtml"
            elif acr == self.ACR_SG:
                page = "/cm/sg_login.xhtml"
            else:
                page=None
                
            return page
        return ""
    
    def getNextStep(self, configurationAttributes, requestParameters, step):
        
        print "Cred-manager. getNextStep called %s" % str(step)
        if step==2:
            alter=ServerUtil.getFirstValue(requestParameters, "alternativeMethod")
            if alter != None:
                print "Cred-manager. getNextStep. Use alternative method %s" % alter
                CdiUtil.bean(Identity).setWorkingParameter("ACR", alter)
                #retry step with different acr
                return step
                
        return -1

    def logout(self, configurationAttributes, requestParameters):
        print "Cred-manager. logout called"
        return True

# AUXILIARY ROUTINES

    def getAvailMethodsUser(self, user, skip):
        methods=ArrayList()

        if (self.scriptsConfig.get(self.ACR_SMS)!=None) and (user.getAttribute("mobile")!=None):
            methods.add(self.ACR_SMS)
            
        if (self.scriptsConfig.get(self.ACR_OTP)!=None) and (user.getAttribute("oxExternalUid")!=None):
            methods.add(self.ACR_OTP)

        inum = user.getAttribute("inum")
        
        u2fConfig=self.scriptsConfig.get(self.ACR_U2F)
        if (u2fConfig!=None) and (self.hasFidoEnrollments(inum, self.u2f_app_id)):
            methods.add(self.ACR_U2F)
    
        sgConfig=self.scriptsConfig.get(self.ACR_SG)
        if (sgConfig!=None) and (self.hasFidoEnrollments(inum, self.supergluu_app_id)):
            methods.add(self.ACR_SG)
        
        if methods.size()>0:
            methods.remove(skip)
            
        print "Cred-manager. getAvailMethodsUser %s" % methods.toString()
        return methods
        
    #FIDO
    
    def hasFidoEnrollments(self, inum, app_id):
        
        devRegService = CdiUtil.bean(DeviceRegistrationService)
        userDevices=devRegService.findUserDeviceRegistrations(inum, app_id, "oxStatus")

        hasDevices=False
        for device in userDevices:
            if device.getStatus().getValue()=="active": 
                hasDevices=True
                break

        return hasDevices
        
    #U2F

    def getAuthnRequestService(self):
    
        configs=self.scriptsConfig.get(self.ACR_U2F)

        u2f_server_uri = configs.get("u2f_server_uri")
        #u2f_server_metadata_uri = u2f_server_uri + "/.well-known/fido-u2f-configuration"
        u2f_server_metadata_uri = u2f_server_uri + "/restv1/fido-u2f-configuration"

        u2fClient=FidoU2fClientFactory.instance()
        metaDataConfigurationService = u2fClient.createMetaDataConfigurationService(u2f_server_metadata_uri)
        u2fMetaDataConfig = metaDataConfigurationService.getMetadataConfiguration()

        return u2fClient.createAuthenticationRequestService(u2fMetaDataConfig)
    
    def getU2fAuthnRequest(self, userId):
        authnRequestService=self.getAuthnRequestService()
        configs=self.scriptsConfig.get(self.ACR_U2F)
        
        session_id = CdiUtil.bean(SessionIdService).getSessionIdFromCookie()
        authnRequest = authnRequestService.startAuthentication(userId, None, configs.get("u2f_application_id"), session_id)
        return ServerUtil.asJson(authnRequest)
        
    def finishU2fAuthentication(self, userId, token_response):
        authnRequestService=self.getAuthnRequestService()
        authenticationStatus = authnRequestService.finishAuthentication(userId, token_response)

        if authenticationStatus.getStatus()!= Constants.RESULT_SUCCESS:
            print "finishU2fAuthentication. Invalid authentication status from FIDO U2F server"
            return False

        return True

    #OTP

    def loadOtpConfigurations(self, otp_conf_file, cfgMap):
        print "Cred-manager. init. loadOtpConfigurations"
        
        if otp_conf_file==None:
            return False;

        # Load configuration from file
        f = open(otp_conf_file, 'r')
        try:
            otpConfiguration = json.loads(f.read())
        except:
            print "Cred-manager. init. loadOtpConfigurations. Failed to load configuration from file: %s" % otp_conf_file
            return False
        finally:
            f.close()
        
        # Check configuration file settings
        try:
            hotpConfiguration=otpConfiguration["htop"]
            totpConfiguration=otpConfiguration["totp"]
            
            hmacShaAlgorithm = totpConfiguration["hmacShaAlgorithm"]
            hmacShaAlgorithmType = None

            if hmacShaAlgorithm=="sha1":
                hmacShaAlgorithmType = HmacShaAlgorithm.HMAC_SHA_1
            elif hmacShaAlgorithm=="sha256":
                hmacShaAlgorithmType = HmacShaAlgorithm.HMAC_SHA_256
            elif hmacShaAlgorithm=="sha512":
                hmacShaAlgorithmType = HmacShaAlgorithm.HMAC_SHA_512
            else:
                print "Cred-manager. init. loadOtpConfigurations. Invalid TOTP HMAC SHA algorithm: %s" % hmacShaAlgorithm
                 
            totpConfiguration["hmacShaAlgorithmType"] = hmacShaAlgorithmType
            
            cfgMap.put("hotpConfiguration", hotpConfiguration)
            cfgMap.put("totpConfiguration", totpConfiguration)
        except:
            print "Cred-manager. init. loadOtpConfigurations. Invalid configuration file"
            return False

        return True
        
    def processHotpAuthentication(self, user, otpCode):

        user_enrollments=self.findOtpEnrollments(user, "hotp")
        userService = CdiUtil.bean(UserService)
        
        for user_enrollment in user_enrollments:
            user_enrollment_data = user_enrollment.split(";")
            otp_secret_key_encoded = user_enrollment_data[0]

            # Get current moving factor from user entry
            moving_factor = StringHelper.toInteger(user_enrollment_data[1])
            otp_secret_key = self.fromBase64Url(otp_secret_key_encoded)

            # Validate TOTP
            validation_result = self.validateHotpKey(otp_secret_key, moving_factor, otpCode)
            if (validation_result != None) and validation_result["result"]:
                print "processHotpAuthentication. otpCode is valid"
                otp_user_external_uid = "hotp:%s;%s" % ( otp_secret_key_encoded, moving_factor )
                new_otp_user_external_uid = "hotp:%s;%s" % ( otp_secret_key_encoded, validation_result["movingFactor"] )

                # Update moving factor in user entry
                find_user_by_external_uid = userService.replaceUserAttribute(user.getUserId(), "oxExternalUid", otp_user_external_uid, new_otp_user_external_uid)
                if find_user_by_external_uid != None:
                    return True

                print "processHotpAuthentication. Failed to update user entry"
        
        return False
        
    def processTotpAuthentication(self, user, otpCode):
    
        user_enrollments=self.findOtpEnrollments(user, "totp")
        
        for user_enrollment in user_enrollments:
            otp_secret_key = self.fromBase64Url(user_enrollment)

            # Validate TOTP
            validation_result = self.validateTotpKey(otp_secret_key, otpCode)
            if (validation_result != None) and validation_result["result"]:
                print "OTP. Process TOTP authentication during authentication. otpCode is valid"
                return True
        
        return False

    def findOtpEnrollments(self, user, otpType, skipPrefix = True):
        
        result = []
        userService = CdiUtil.bean(UserService)       
        user_custom_ext_attribute = userService.getCustomAttribute(user, "oxExternalUid")
        if user_custom_ext_attribute == None:
            return result

        otp_prefix = "%s:" % otpType        
        otp_prefix_length = len(otp_prefix) 
        
        for user_external_uid in user_custom_ext_attribute.getValues():
            index = user_external_uid.find(otp_prefix)
            if index != -1:
                if skipPrefix:
                    enrollment_uid = user_external_uid[otp_prefix_length:]
                else:
                    enrollment_uid = user_external_uid

                result.append(enrollment_uid)
        
        return result

    def validateHotpKey(self, secretKey, movingFactor, totpKey):
        hotpConfig=self.scriptsConfig.get("hotpConfiguration")
        digits = hotpConfig["digits"]

        htopValidationResult = HOTPValidator.lookAheadWindow(1).validate(secretKey, movingFactor, digits, totpKey)
        if htopValidationResult.isValid():
            return { "result": True, "movingFactor": htopValidationResult.getNewMovingFactor() }
        return { "result": False, "movingFactor": None }
        
    def validateTotpKey(self, secretKey, totpKey):
        localTotpKey = self.generateTotpKey(secretKey)
        if StringHelper.equals(localTotpKey, totpKey):
            return { "result": True }
        return { "result": False }
        
    def generateTotpKey(self, secretKey):
        
        totpConfig=self.scriptsConfig.get("totpConfiguration")
        digits = totpConfig["digits"]
        timeStep = totpConfig["timeStep"]
        hmacShaAlgorithmType = totpConfig["hmacShaAlgorithmType"]

        totp = TOTP.key(secretKey).digits(digits).timeStep(TimeUnit.SECONDS.toMillis(timeStep)).hmacSha(hmacShaAlgorithmType).build()
        return totp.value()

    def fromBase64Url(self, chars):
        return BaseEncoding.base64Url().decode(chars)