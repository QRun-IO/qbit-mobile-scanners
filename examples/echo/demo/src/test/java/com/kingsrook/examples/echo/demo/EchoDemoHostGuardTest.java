/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** The loopback host guard, including the native client's USB-forwarded Host.
 *******************************************************************************/
class EchoDemoHostGuardTest
{
   /***************************************************************************
    ** The device-side Host 127.0.0.1:8080 reaches the server on 57832 through adb
    ** reverse and must be allowed for public status and login, as must the
    ** listening port itself and the localhost name.
    ***************************************************************************/
   @Test
   void forwardedLoopbackHostPortIsAcceptedAndEverythingElseStaysDenied()
   {
      for(String host : new String[] { "127.0.0.1:8080", "127.0.0.1:57832", "localhost:8080", "LOCALHOST:8080", "127.0.0.1:1", "127.0.0.1:65535" })
      {
         assertTrue(EchoDemo.isLoopbackAuthority(host), host);
         assertNull(EchoDemo.refusal(host, null, null, "GET", false), "public status over " + host);
         assertNull(EchoDemo.refusal(host, "http://" + host, "same-origin", "POST", true), "login over " + host);
      }

      // not a literal loopback name with an explicit valid port
      for(String host : new String[] { null, "", "127.0.0.1", "localhost", "127.0.0.1:", "127.0.0.1:0", "127.0.0.1:65536", "127.0.0.1:99999", "127.0.0.1:080",
         "127.0.0.1:80a", "127.0.0.1:-1", "127.0.0.1:8080/path", "127.0.0.1:8080?x=1", "user@127.0.0.1:8080", "127.0.0.1:8080@evil.test", "evil.test:8080",
         "127.0.0.1.evil.test:8080", "localhost.evil.test:8080", "[::1]:8080", "0.0.0.0:8080", "127.0.0.2:8080", " 127.0.0.1:8080", "127.0.0.1:8080 ", "127.0.0.1:8080:9" })
      {
         assertFalse(EchoDemo.isLoopbackAuthority(host), String.valueOf(host));
         assertNotNull(EchoDemo.refusal(host, null, null, "GET", false), String.valueOf(host));
      }

      // Origin must be exactly http://<Host>, whatever the forwarded port is
      assertNotNull(EchoDemo.refusal("127.0.0.1:8080", "http://127.0.0.1:57832", null, "POST", true));
      assertNotNull(EchoDemo.refusal("127.0.0.1:8080", "http://localhost:8080", null, "GET", false));
      assertNotNull(EchoDemo.refusal("127.0.0.1:8080", "http://evil.test", null, "GET", false));
      assertNotNull(EchoDemo.refusal("127.0.0.1:8080", "https://127.0.0.1:8080", null, "GET", false));
      // cross-site fetches and cookie mutations without an Origin stay denied
      assertEquals("Cross-site access to the Echo demo is not allowed", EchoDemo.refusal("127.0.0.1:8080", null, "cross-site", "GET", false));
      assertEquals("Cross-site access to the Echo demo is not allowed", EchoDemo.refusal("127.0.0.1:8080", "http://127.0.0.1:8080", "Cross-Site", "POST", false));
      assertEquals("Cookie-authenticated changes require a same-origin request", EchoDemo.refusal("127.0.0.1:8080", null, null, "POST", true));
      // credentials without cookies and safe methods remain usable
      assertNull(EchoDemo.refusal("127.0.0.1:8080", null, null, "POST", false));
      assertNull(EchoDemo.refusal("127.0.0.1:8080", null, "none", "GET", true));
   }
}
