/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import io.javalin.Javalin;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/*******************************************************************************
 ** Branding is public appearance data, never an authentication requirement.
 *******************************************************************************/
class MobileBrandingTest
{
   /***************************************************************************
    ** Reject markup, controls, CSS and credential-bearing or non-HTTPS logos.
    ***************************************************************************/
   @Test
   void rejectsInvalidConfiguration()
   {
      MobileScannersQBitConfig config = new MobileScannersQBitConfig();
      for(String name : List.of("", " ", " name", "name ", "x\ny", "<b>name</b>", "x".repeat(81)))
      {
         assertThrows(IllegalArgumentException.class, () -> config.withDisplayName(name));
      }
      for(String color : List.of("red", "#FFF", "#11223344", "#gg2233", " #112233"))
      {
         assertThrows(IllegalArgumentException.class, () -> config.withPrimaryColor(color));
      }
      for(String url : List.of("", "/logo.png", "http://example.com/logo.png", "data:image/png;base64,x", "file:///logo.png",
         "https://user:password@example.com/logo.png", "https://example.com/logo.png#fragment", "https://example.com:99999/logo.png",
         "https://example.com/" + "a".repeat(2048)))
      {
         assertThrows(IllegalArgumentException.class, () -> config.withLogoUrl(url));
      }
      assertThrows(IllegalArgumentException.class, () -> config.withDisplayName(null));
      assertThrows(IllegalArgumentException.class, () -> config.withPrimaryColor(null));
      assertEquals("#AABBCC", config.withPrimaryColor("#aabbcc").getPrimaryColor());
   }



   /***************************************************************************
    ** Defaults remain available with no session and stale device credentials.
    ***************************************************************************/
   @Test
   void publicDefaultsIgnoreStaleDeviceCredentials() throws Exception
   {
      QInstance instance = new QInstance();
      instance.addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("qbit-mobile-scanners")
         .withVersion("0.1.0-SNAPSHOT").withNamespace("mobile").withConfig(new MobileScannersQBitConfig().withBackendName("privateBackend")));
      MobileScannersRouteProvider provider = new MobileScannersRouteProvider(instance);
      Javalin server = Javalin.create(config ->
      {
         config.jetty.host = "127.0.0.1";
         config.routes.apiBuilder(provider.getJavalinEndpointGroup());
         config.routes.apiBuilder(() ->
         {
            MobileBrandingSpec brandingSpec = new MobileBrandingSpec();
            brandingSpec.setQInstance(instance);
            brandingSpec.defineRoute(new MiddlewareVersionV1(), "v1");
            MobileStatusSpec statusSpec = new MobileStatusSpec();
            statusSpec.setQInstance(instance);
            statusSpec.defineRoute(new MiddlewareVersionV1(), "v1");
         });
      }).start(0);
      try
      {
         HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/qqq/v1/qbit/mobile-scanners/branding"))
            .timeout(Duration.ofSeconds(5)).header(MobileScannersRouteProvider.DEVICE_HEADER, "stale")
            .header(MobileScannersRouteProvider.TOKEN_HEADER, "invalid").GET().build();
         var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
         assertEquals(200, response.statusCode());
         assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
         JSONObject branding = new JSONObject(response.body());
         assertEquals(2, branding.length());
         assertEquals("QQQ Mobile Scanners", branding.getString("displayName"));
         assertEquals("#1565C0", branding.getString("primaryColor"));
         HttpRequest heartbeat = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/qqq/v1/qbit/mobile-scanners/status"))
            .timeout(Duration.ofSeconds(5)).header(MobileScannersRouteProvider.DEVICE_HEADER, "stale")
            .header(MobileScannersRouteProvider.TOKEN_HEADER, "invalid").GET().build();
         var status = HttpClient.newHttpClient().send(heartbeat, HttpResponse.BodyHandlers.ofString());
         assertEquals(200, status.statusCode());
         assertEquals("no-store", status.headers().firstValue("Cache-Control").orElseThrow());
         JSONObject health = new JSONObject(status.body());
         assertEquals(1, health.length());
         assertEquals("ok", health.getString("status"));
      }
      finally
      {
         server.stop();
      }
   }
}
