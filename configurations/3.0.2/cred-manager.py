# oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
# Copyright (c) 2016, Gluu
#
# Author: Jose Gonzalez 

from org.jboss.seam.security import Identity
from org.jboss.seam.contexts import Contexts
from org.xdi.oxauth.service import AuthenticationService, UserService, SessionStateService
from org.xdi.model.custom.script.type.auth import PersonAuthenticationType
from org.xdi.oxauth.model.config import Constants, ConfigurationFactory
from org.xdi.oxauth.service.fido.u2f import DeviceRegistrationService
from org.xdi.oxauth.client.fido.u2f import FidoU2fClientFactory
from org.xdi.util import StringHelper
from org.jboss.seam import Component
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

from javax.faces.context import FacesContext
from org.jboss.seam.faces import FacesMessages
from org.jboss.seam.international import StatusMessage

from com.google.android.gcm.server import Sender, Message
from com.notnoop.apns import APNS
from org.xdi.util.security import StringEncrypter
from org.xdi.oxauth.service.net import HttpService
from org.apache.http.params import CoreConnectionPNames

import random
import java
import datetime
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
        custScriptService=Component.getInstance(CustomScriptService)
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
                
        if cfgMap.keySet().contains(self.ACR_SG):
            if not self.initPushNotificationService(cfgMap.get(self.ACR_SG), cfgMap):
                print "Cred-manager. init. Could not initialize push services, check custom script settings"
                cfgMap.remove(self.ACR_SG)
        
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
        userService = Component.getInstance(UserService)
        context = Contexts.getEventContext()
        authenticationService = Component.getInstance(AuthenticationService)
        identity = Component.getInstance(Identity)
        
        facesMessages = FacesMessages.instance()
        FacesContext.getCurrentInstance().getExternalContext().getFlash().setKeepMessages(True)

        if step == 1:
            credentials = identity.getCredentials()
            user_name = credentials.getUsername()
            user_password = credentials.getPassword()
            
            if StringHelper.isNotEmptyString(user_name) and StringHelper.isNotEmptyString(user_password):
                logged_in = userService.authenticate(user_name, user_password)
                
                if logged_in:
                    foundUser = userService.getUserByAttribute("uid", user_name)
                    acr=foundUser.getAttribute("oxPreferredMethod")
                    
                    context.set("skip2FA", acr == None)
                    if acr == None:
                        return True
                    else:
                        context.set("ACR", acr)
                        
                        if not (acr in self.ACRs):
                            print "%s not a valid cred-manager acr" % acr

                        return (acr in self.ACRs)
            
                return False
            else:
                return False
        elif step == 2:
            session_attributes = context.get("sessionAttributes")
            acr = session_attributes.get("ACR")
            alter = ServerUtil.getFirstValue(requestParameters, "alternativeMethod")
            
            user = authenticationService.getAuthenticatedUser()
            if user == None:
                print "Cred-manager. authenticate. Cannot retrieve logged user"
                return False
            
            context.set("methods", self.getAvailMethodsUser(user, acr))
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
                    facesMessages.add(StatusMessage.Severity.ERROR, "Wrong code entered")
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
                    facesMessages.add(StatusMessage.Severity.ERROR, "Wrong code entered")
                    
                return success
                
            elif acr==self.ACR_SG:
                user_name=user.getUserId()
                user_inum=user.getAttribute("inum")
                
                session_device_status = self.getSessionDeviceStatus(session_attributes, user_name)
                if session_device_status == None:
                    return False

                u2f_device_id = session_device_status['device_id']
                validation_result = self.validateSessionDeviceStatus(self.supergluu_app_id, session_device_status, user_inum)
                
                if validation_result:
                    print "Cred-manager. authenticate. User '%s' successfully authenticated with u2f_device '%s'" % (user_name, u2f_device_id)
                    super_gluu_request = json.loads(session_device_status['super_gluu_request'])
                    validation_result= super_gluu_request['method']=="authenticate"
                
                return validation_result

            return False
        return False

    def prepareForStep(self, configurationAttributes, requestParameters, step):
        print "Cred-manager. prepareForStep %s" % str(step)
        if step==1:
            return True
        elif step==2:
            context = Contexts.getEventContext()
            session_attributes = context.get("sessionAttributes")
            
            authenticationService = Component.getInstance(AuthenticationService)
            user = authenticationService.getAuthenticatedUser()
            
            if user==None:
                print "Cred-manager. prepareForStep. Cannot retrieve logged user"
                return False
                        
            acr=session_attributes.get("ACR")
            print "Cred-manager. prepareForStep. ACR=%s" % acr
            context.set("methods", self.getAvailMethodsUser(user, acr))
            
            if acr==self.ACR_U2F:
                authnRequest=self.getU2fAuthnRequest(user.getUserId())
                context.set("fido_u2f_authentication_request", authnRequest)
                return True
            
            elif acr==self.ACR_SMS:
                mobiles = user.getAttributeValues("mobile")                 
                code = random.randint(100000, 999999)
                context.set("randCode", code)
                
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
                
            elif acr==self.ACR_SG:    
                session_state = Component.getInstance(SessionStateService).getSessionStateFromCookie()
                issuer = Component.getInstance(ConfigurationFactory).getConfiguration().getIssuer()
                client_redirect_uri = self.supergluu_app_id
                
                super_gluu_request_dictionary = {'username': user.getUserId(),
                               'app': client_redirect_uri,
                               'issuer': issuer,
                               'method': "authenticate",
                               'state': session_state,
                               'created': datetime.datetime.now().isoformat()}
                
                self.addGeolocationData(session_attributes, super_gluu_request_dictionary)
                super_gluu_request = json.dumps(super_gluu_request_dictionary, separators=(',',':'))
                print "Cred-manager. prepareForStep. Super gluu QR-code/push request prepared: %s" % super_gluu_request
                self.sendPushNotification(client_redirect_uri, user, super_gluu_request)
                
                sgCfg=self.scriptsConfig.get(self.ACR_SG)
                context.set("super_gluu_label", sgCfg.get("label"))        
                context.set("super_gluu_qr_options", sgCfg.get("qr_options"))
                context.set("super_gluu_request", super_gluu_request)
        
                return True
                
        return True

    def getExtraParametersForStep(self, configurationAttributes, step):
        print "Cred-manager. getExtraParametersForStep %s" % str(step)  
        if step==2:
            return Arrays.asList("ACR", "randCode", "methods", "super_gluu_request")
        return None

    def getCountAuthenticationSteps(self, configurationAttributes):
        print "Cred-manager. getCountAuthenticationSteps called"
        
        context = Contexts.getEventContext()
        if context.get("skip2FA"):
           return 1
        return 2

    def getPageForStep(self, configurationAttributes, step):
        print "Cred-manager. getPageForStep called %s" % str(step)
                
        context = Contexts.getEventContext()
        if step == 2:
            acr=context.get("ACR")
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
        
        context = Contexts.getEventContext()
        print "Cred-manager. getNextStep called %s" % str(step)
        if step==2:
            alter=ServerUtil.getFirstValue(requestParameters, "alternativeMethod")
            if alter != None:
                print "Cred-manager. getNextStep. Use alternative method %s" % alter
                context.set("ACR", alter)
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
        
        if methods.size()>0 and methods.contains(skip):
            methods.remove(skip)
        
        print "Cred-manager. getAvailMethodsUser %s" % methods.toString()
        return methods
        
    #FIDO
    
    def hasFidoEnrollments(self, inum, app_id):
        
        devRegService = Component.getInstance(DeviceRegistrationService)
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
        u2f_server_metadata_uri = u2f_server_uri + "/oxauth/seam/resource/restv1/oxauth/fido-u2f-configuration"

        u2fClient=FidoU2fClientFactory.instance()
        metaDataConfigurationService = u2fClient.createMetaDataConfigurationService(u2f_server_metadata_uri)
        u2fMetaDataConfig = metaDataConfigurationService.getMetadataConfiguration()

        return u2fClient.createAuthenticationRequestService(u2fMetaDataConfig)
    
    def getU2fAuthnRequest(self, userId):
        authnRequestService=self.getAuthnRequestService()
        configs=self.scriptsConfig.get(self.ACR_U2F)
        
        session_state = Component.getInstance(SessionStateService).getSessionStateFromCookie()
        authnRequest = authnRequestService.startAuthentication(userId, None, configs.get("u2f_application_id"), session_state)
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
        userService = UserService.instance()  
        
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
        print "Validating code against %s users devices" % str(len(user_enrollments))
        
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
        userService = UserService.instance()        
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
        
# SG

    def initPushNotificationService(self, configs, cfgMap):
        print "Super-Gluu. Initialize notification services"

        super_gluu_creds_file = configs.get("credentials_file")
        if super_gluu_creds_file==None:
            return False

        # Load credentials from file
        f = open(super_gluu_creds_file, 'r')
        try:
            creds = json.loads(f.read())
        except:
            print "Super-Gluu. Initialize notification services. Failed to load credentials from file:", super_gluu_creds_file
            return False
        finally:
            f.close()
        
        try:
            android_creds = creds["android"]["gcm"]
            ios_creads = creds["ios"]["apns"]
        except:
            print "Super-Gluu. Initialize notification services. Invalid credentials file '%s' format:" % super_gluu_creds_file
            return False
        
        if android_creds["enabled"]:
            cfgMap.put("pushAndroidService", Sender(android_creds["api_key"]))
            print "Super-Gluu. Initialize notification services. Created Android notification service"
        
        if ios_creads["enabled"]:
            p12_file_path = ios_creads["p12_file_path"]
            p12_password = ios_creads["p12_password"]

            try:
                stringEncrypter = StringEncrypter.defaultInstance()
                p12_password = stringEncrypter.decrypt(p12_password)
            except:
                # Ignore exception. Password is not encrypted
                print "Super-Gluu. Initialize notification services. Assuming that 'p12_password' password in not encrypted"
            
            apnsServiceBuilder =  APNS.newService().withCert(p12_file_path, p12_password)
            if ios_creads["production"]:
                cfgMap.put("pushAppleService", apnsServiceBuilder.withProductionDestination().build())
            else:
                cfgMap.put("pushAppleService", apnsServiceBuilder.withSandboxDestination().build())

            print "Super-Gluu. Initialize notification services. Created iOS notification service"
        
        enabled = cfgMap.containsKey("pushAndroidService") or cfgMap.containsKey("pushAppleService")
        return enabled

    def addGeolocationData(self, session_attributes, super_gluu_request_dictionary):
        if session_attributes.containsKey("remote_ip"):
            remote_ip = session_attributes.get("remote_ip")
            if StringHelper.isNotEmpty(remote_ip):
                print "Super-Gluu. Adding req_ip and req_loc to super_gluu_request"
                super_gluu_request_dictionary['req_ip'] = remote_ip

                remote_loc_dic = self.determineGeolocationData(remote_ip)
                if remote_loc_dic == None:
                    print "Super-Gluu. Failed to determine remote location by remote IP '%s'" % remote_ip
                    return

                remote_loc = "%s, %s, %s" % ( remote_loc_dic['country'], remote_loc_dic['regionName'], remote_loc_dic['city'] )
                remote_loc_encoded = urllib.quote(remote_loc.encode('utf-8'))
                super_gluu_request_dictionary['req_loc'] = remote_loc_encoded

    def determineGeolocationData(self, remote_ip):
        print "Super-Gluu. Determine remote location. remote_ip: '%s'" % remote_ip
        httpService = Component.getInstance(HttpService)

        http_client = httpService.getHttpsClient()
        http_client_params = http_client.getParams()
        http_client_params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 15 * 1000)
        
        geolocation_service_url = "http://ip-api.com/json/%s?fields=49177" % remote_ip
        geolocation_service_headers = { "Accept" : "application/json" }

        try:
            http_service_response = httpService.executeGet(http_client, geolocation_service_url,  geolocation_service_headers)
            http_response = http_service_response.getHttpResponse()
        except:
            print "Super-Gluu. Determine remote location. Exception"
            return None

        try:
            if not httpService.isResponseStastusCodeOk(http_response):
                print "Super-Gluu. Determine remote location. Get invalid response from validation server: ", str(http_response.getStatusLine().getStatusCode())
                httpService.consume(http_response)
                return None
    
            response_bytes = httpService.getResponseContent(http_response)
            response_string = httpService.convertEntityToString(response_bytes)
            httpService.consume(http_response)
        finally:
            http_service_response.closeConnection()

        if response_string == None:
            print "Super-Gluu. Determine remote location. Get empty response from location server"
            return None
        
        response = json.loads(response_string)
        
        if not StringHelper.equalsIgnoreCase(response['status'], "success"):
            print "Super-Gluu. Determine remote location. Get response with status: '%s'" % response['status']
            return None

        return response

    def sendPushNotification(self, client_redirect_uri, user, super_gluu_request):

        user_inum = user.getAttribute("inum")
        send_notification = False
        send_notification_result = True

        deviceRegistrationService = Component.getInstance(DeviceRegistrationService)
        u2f_devices_list = deviceRegistrationService.findUserDeviceRegistrations(user_inum, client_redirect_uri, "oxId", "oxDeviceData", "oxStatus")
        
        #list won't be empty
        for u2f_device in u2f_devices_list:
            if u2f_device.getStatus().getValue()=="active": 
            
                device_data = u2f_device.getDeviceData()
                if device_data == None:
                    continue

                platform = device_data.getPlatform()
                push_token = device_data.getPushToken()
                debug = False

                if StringHelper.equalsIgnoreCase(platform, "ios") and StringHelper.isNotEmpty(push_token):
                    # Sending notification to iOS user's device
                    if not self.scriptsConfig.containsKey("pushAppleService"):
                        print "Super-Gluu. Send push notification. Apple push notification service is not enabled"
                    else:
                        send_notification = True

                        title = "Super-Gluu"
                        message = "Super-Gluu login request to: %s" % client_redirect_uri
                        additional_fields = { "request" : super_gluu_request }

                        msgBuilder = APNS.newPayload().alertBody(message).alertTitle(title).sound("default")
                        msgBuilder.category('ACTIONABLE').badge(0)
                        msgBuilder.forNewsstand()
                        msgBuilder.customFields(additional_fields)
                        push_message = msgBuilder.build()

                        send_notification_result = self.scriptsConfig.get("pushAppleService").push(push_token, push_message)
                        if debug:
                            print "Super-Gluu. Send iOS push notification. token: '%s', message: '%s', send_notification_result: '%s'" % (push_token, push_message, send_notification_result)

                elif StringHelper.equalsIgnoreCase(platform, "android") and StringHelper.isNotEmpty(push_token):
                    # Sending notification to Android user's device
                    if not self.scriptsConfig.containsKey("pushAndroidService"):
                        print "Super-Gluu. Send push notification. Android push notification service is not enabled"
                    else:
                        send_notification = True

                        title = "Super-Gluu"
                        msgBuilder = Message.Builder().addData("message", super_gluu_request).addData("title", title).collapseKey("single").contentAvailable(True)
                        push_message = msgBuilder.build()

                        send_notification_result = self.scriptsConfig.get("pushAndroidService").send(push_message, push_token, 3)
                        if debug:
                            print "Super-Gluu. Send Android push notification. token: '%s', message: '%s', send_notification_result: '%s'" % (push_token, push_message, send_notification_result)

        print "Super-Gluu. Send push notification. send_notification: '%s', send_notification_result: '%s'" % (send_notification, send_notification_result)

    def getSessionDeviceStatus(self, session_attributes, user_name):
        print "Super-Gluu. Get session device status"

        if not session_attributes.containsKey("super_gluu_request"):
            print "Super-Gluu. Get session device status. There is no Super-Gluu request in session attributes"
            return None

        # Check session state extended
        if not session_attributes.containsKey("session_custom_state"):
            print "Super-Gluu. Get session device status. There is no session_custom_state in session attributes"
            return None

        session_custom_state = session_attributes.get("session_custom_state")
        if not StringHelper.equalsIgnoreCase("approved", session_custom_state):
            print "Super-Gluu. Get session device status. User '%s' not approve or not pass U2F authentication. session_custom_state: '%s'" % (user_name, session_custom_state)
            return None

        # Try to find device_id in session attribute
        if not session_attributes.containsKey("oxpush2_u2f_device_id"):
            print "Super-Gluu. Get session device status. There is no u2f_device associated with this request"
            return None

        # Try to find user_inum in session attribute
        if not session_attributes.containsKey("oxpush2_u2f_device_user_inum"):
            print "Super-Gluu. Get session device status. There is no user_inum associated with this request"
            return None
        
        enroll = False
        if session_attributes.containsKey("oxpush2_u2f_device_enroll"):
            enroll = StringHelper.equalsIgnoreCase("true", session_attributes.get("oxpush2_u2f_device_enroll"))

        one_step = False
        if session_attributes.containsKey("oxpush2_u2f_device_one_step"):
            one_step = StringHelper.equalsIgnoreCase("true", session_attributes.get("oxpush2_u2f_device_one_step"))
                        
        super_gluu_request = session_attributes.get("super_gluu_request")
        u2f_device_id = session_attributes.get("oxpush2_u2f_device_id")
        user_inum = session_attributes.get("oxpush2_u2f_device_user_inum")

        session_device_status = {"super_gluu_request": super_gluu_request, "device_id": u2f_device_id, "user_inum" : user_inum, "enroll" : enroll, "one_step" : one_step}
        print "Super-Gluu. Get session device status. session_device_status: '%s'" % (session_device_status)
        
        return session_device_status

    def validateSessionDeviceStatus(self, client_redirect_uri, session_device_status, user_inum = None):
        
        deviceRegistrationService = Component.getInstance(DeviceRegistrationService)
        u2f_device_id = session_device_status['device_id']

        u2f_device = None
        if session_device_status['enroll'] and session_device_status['one_step']:
            u2f_device = deviceRegistrationService.findOneStepUserDeviceRegistration(u2f_device_id)
            if u2f_device == None:
                print "Super-Gluu. Validate session device status. There is no one step u2f_device '%s'" % u2f_device_id
                return False
        else:
            if session_device_status['one_step']:
                user_inum = session_device_status['user_inum']
    
            u2f_device = deviceRegistrationService.findUserDeviceRegistration(user_inum, u2f_device_id)
            if u2f_device == None:
                print "Super-Gluu. Validate session device status. There is no u2f_device '%s' associated with user '%s'" % (u2f_device_id, user_inum)
                return False

        if not StringHelper.equalsIgnoreCase(client_redirect_uri, u2f_device.application):
            print "Super-Gluu. Validate session device status. u2f_device '%s' associated with other application '%s'" % (u2f_device_id, u2f_device.application)
            return False
        
        return True

