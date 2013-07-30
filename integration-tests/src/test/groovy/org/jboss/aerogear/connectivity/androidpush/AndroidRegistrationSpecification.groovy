/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.connectivity.androidpush

import java.io.File;
import java.util.concurrent.Callable

import javax.ws.rs.core.Response.Status

import org.jboss.aerogear.connectivity.common.AndroidVariantUtils
import org.jboss.aerogear.connectivity.common.AuthenticationUtils
import org.jboss.aerogear.connectivity.common.Deployments;
import org.jboss.aerogear.connectivity.common.InstallationUtils
import org.jboss.aerogear.connectivity.common.PushApplicationUtils
import org.jboss.aerogear.connectivity.common.PushNotificationSenderUtils
import org.jboss.aerogear.connectivity.common.SimplePushVariantUtils
import org.jboss.aerogear.connectivity.common.iOSVariantUtils
import org.jboss.aerogear.connectivity.model.AndroidVariant
import org.jboss.aerogear.connectivity.model.InstallationImpl
import org.jboss.aerogear.connectivity.model.PushApplication
import org.jboss.aerogear.connectivity.model.SimplePushVariant
import org.jboss.aerogear.connectivity.rest.util.iOSApplicationUploadForm
import org.jboss.arquillian.container.test.api.Deployment
import org.jboss.arquillian.container.test.api.RunAsClient
import org.jboss.arquillian.spock.ArquillianSpecification
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.JavaArchive
import org.jboss.shrinkwrap.api.spec.WebArchive
import org.jboss.shrinkwrap.resolver.api.maven.Maven
import org.jboss.shrinkwrap.resolver.api.maven.archive.importer.MavenImporter

import spock.lang.Shared
import spock.lang.Specification

import com.google.android.gcm.server.Sender
import com.jayway.awaitility.Awaitility
import com.jayway.awaitility.Duration
import com.notnoop.apns.APNS
import com.notnoop.apns.ApnsService
import com.notnoop.apns.ApnsServiceBuilder
import com.notnoop.apns.PayloadBuilder
import com.notnoop.apns.internal.ApnsServiceImpl
import com.notnoop.exceptions.NetworkIOException


@ArquillianSpecification
@Mixin([AuthenticationUtils, PushApplicationUtils, AndroidVariantUtils,
	InstallationUtils, PushNotificationSenderUtils])
class AndroidRegistrationSpecification extends Specification {

	private final static String ANDROID_VARIANT_GOOGLE_KEY = "IDDASDASDSAQ__1"

	private final static String ANDROID_VARIANT_NAME = "AndroidVariant__1"

	private final static String ANDROID_VARIANT_DESC = "awesome variant__1"

	private final static String AUTHORIZED_LOGIN_NAME = "admin"

	private final static String AUTHORIZED_PASSWORD = "123"

	private final static String PUSH_APPLICATION_NAME = "TestPushApplication__1"

	private final static String PUSH_APPLICATION_DESC = "awesome app__1"

	private final static String ANDROID_DEVICE_TOKEN = "gsmToken__1"

	private final static String ANDROID_DEVICE_TOKEN_2 = "gsmToken__2"

	private final static String ANDROID_DEVICE_TOKEN_3 = "gsmToken__3"

	private final static String ANDROID_DEVICE_OS = "ANDROID"

	private final static String ANDROID_DEVICE_TYPE = "AndroidTablet"

	private final static String ANDROID_DEVICE_TYPE_2 = "AndroidPhone"

	private final static String ANDROID_DEVICE_OS_VERSION = "4.2.2"

	private final static String ANDROID_CLIENT_ALIAS = "qa_android_1@aerogear"

	private final static String ANDROID_CLIENT_ALIAS_2 = "qa_android_2@mobileteam"

	private final static String NOTIFICATION_ALERT_MSG = "Hello AeroGearers"

	private final static String COMMON_IOS_ANDROID_CLIENT_ALIAS = "qa_ios_android@aerogear"

	private final static URL root = new URL("http://localhost:8080/ag-push/")

	@Deployment(testable=false)
	def static WebArchive "create deployment"() {
		Deployments.unifiedPushServer()
	}

	@Shared def static authCookies

	@Shared def static pushApplicationId

	@Shared def static masterSecret

	@Shared def static androidVariantId

	@Shared def static androidSecret
	
	@RunAsClient
	def "Authenticate"() {
		when:
		authCookies = login(AUTHORIZED_LOGIN_NAME, AUTHORIZED_PASSWORD).getCookies()

		then:
		authCookies != null
	}

	@RunAsClient
	def "Register a Push Application"() {
		given: "A Push Application"
		PushApplication pushApp = createPushApplication(PUSH_APPLICATION_NAME, PUSH_APPLICATION_DESC,
				null, null, null)

		when: "Application is registered"
		def response = registerPushApplication(pushApp, authCookies, null)
		def body = response.body().jsonPath()
		pushApplicationId = body.get("pushApplicationID")
		masterSecret = body.get("masterSecret")

		then: "Response code 201 is returned"
		response.statusCode() == Status.CREATED.getStatusCode()

		and: "Push App Id is not null"
		pushApplicationId != null

		and: "Master secret is not null"
		masterSecret != null

		and: "Push App Name is the expected one"
		body.get("name") == PUSH_APPLICATION_NAME
	}

	@RunAsClient
	def "Register an Android Variant"() {
		given: "An Android Variant"
		AndroidVariant variant = createAndroidVariant(ANDROID_VARIANT_NAME, ANDROID_VARIANT_DESC,
				null, null, null, ANDROID_VARIANT_GOOGLE_KEY)

		when: "Android Variant is registered"
		def response = registerAndroidVariant(pushApplicationId, variant, authCookies)
		def body = response.body().jsonPath()
		androidVariantId = body.get("variantID")
		androidSecret = body.get("secret")

		then: "Push Application id is not empty"
		pushApplicationId != null

		and: "Response status code is 201"
		response != null && response.statusCode() == Status.CREATED.getStatusCode()

		and: "Android Variant id is not null"
		androidVariantId != null

		and: "Secret is not empty"
		androidSecret != null
	}

	@RunAsClient
	def "Register an Android Variant - Bad Case - Missing Google key"() {
		given: "An Android Variant"
		AndroidVariant variant = createAndroidVariant(ANDROID_VARIANT_NAME, ANDROID_VARIANT_DESC,
				null, null, null, null)

		when: "Android Variant is registered"
		def response = registerAndroidVariant(pushApplicationId, variant, authCookies)

		then: "Push Application id is not empty"
		pushApplicationId != null

		and: "Response status code is 400"
		response != null && response.statusCode() == Status.BAD_REQUEST.getStatusCode()
	}

	@RunAsClient
	def "Register an Android Variant - Bad Case - Missing auth cookies"() {
		given: "An Android Variant"
		AndroidVariant variant = createAndroidVariant(ANDROID_VARIANT_NAME, ANDROID_VARIANT_DESC,
				null, null, null, ANDROID_VARIANT_GOOGLE_KEY)

		when: "Android Variant is registered"
		def response = registerAndroidVariant(pushApplicationId, variant, new HashMap<String, ?>())

		then: "Push Application id is not empty"
		pushApplicationId != null

		and: "Response status code is 401"
		response != null && response.statusCode() == Status.UNAUTHORIZED.getStatusCode()
	}
	
	@RunAsClient
	def "Register an installation for an Android device"() {

		given: "An installation for an Android device"
		InstallationImpl androidInstallation = createInstallation(ANDROID_DEVICE_TOKEN, ANDROID_DEVICE_TYPE,
				ANDROID_DEVICE_OS, ANDROID_DEVICE_OS_VERSION, ANDROID_CLIENT_ALIAS, null)

		when: "Installation is registered"
		def response = registerInstallation(androidVariantId, androidSecret, androidInstallation)

		then: "Variant id and secret is not empty"
		androidVariantId != null && androidSecret != null

		and: "Response status code is 200"
		response != null && response.statusCode() == Status.OK.getStatusCode()
	}

	@RunAsClient
	def "Register a second installation for an Android device"() {

		given: "An installation for an Android device"
		InstallationImpl androidInstallation = createInstallation(ANDROID_DEVICE_TOKEN_2, ANDROID_DEVICE_TYPE_2,
				ANDROID_DEVICE_OS, ANDROID_DEVICE_OS_VERSION, ANDROID_CLIENT_ALIAS_2, null)

		when: "Installation is registered"
		def response = registerInstallation(androidVariantId, androidSecret, androidInstallation)

		then: "Variant id and secret is not empty"
		androidVariantId != null && androidSecret != null

		and: "Response status code is 200"
		response != null && response.statusCode() == Status.OK.getStatusCode()
	}

	@RunAsClient
	def "Register a third installation for an Android device"() {

		given: "An installation for an Android device"
		InstallationImpl androidInstallation = createInstallation(ANDROID_DEVICE_TOKEN_3, ANDROID_DEVICE_TYPE,
				ANDROID_DEVICE_OS, ANDROID_DEVICE_OS_VERSION, COMMON_IOS_ANDROID_CLIENT_ALIAS, null)

		when: "Installation is registered"
		def response = registerInstallation(androidVariantId, androidSecret, androidInstallation)

		then: "Variant id and secret is not empty"
		androidVariantId != null && androidSecret != null

		and: "Response status code is 200"
		response != null && response.statusCode() == Status.OK.getStatusCode()
	}
}
