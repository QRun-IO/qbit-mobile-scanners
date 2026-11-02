/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.AuthScope;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;


/*******************************************************************************
 ** Test-only fixture in the mobile package exercises its real approval/session
 ** binding. No production class exposes enrollment secrets or private sessions.
 *******************************************************************************/
public final class EchoMobileFixture
{
   private final QInstance instance;
   private final QSession scanner;
   private final String device;



   /***************************************************************************
    ** Actual H2 mobile tables and ordinary native user permissions are used.
    ***************************************************************************/
   public EchoMobileFixture(QInstance instance, String jdbc) throws Exception
   {
      this.instance = instance;
      instance.setEnforcePermissionsInCoreActions(true);
      instance.registerAuthenticationProvider(AuthScope.instanceDefault(), new QAuthenticationMetaData().withName("fixture").withType(QAuthenticationType.MOCK));
      instance.addBackend(new RDBMSBackendMetaData().withName("db").withVendor("h2").withJdbcUrl(jdbc).withUsername("sa"));
      new MobileScannersQBitProducer().withConfig(new MobileScannersQBitConfig().withBackendName("db")).produce(instance, "mobile");
      for(var table : instance.getTables().values())
      {
         table.setBackendDetails(new RDBMSTableBackendDetails().withTableName(table.getName()));
      }
      try(var connection = DriverManager.getConnection(jdbc, "sa", "");
         var resource = getClass().getResourceAsStream("/db/mobile-scanners-h2.sql"))
      {
         String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
         for(String statement : sql.split(";"))
         {
            if(!statement.isBlank())
            {
               try(var command = connection.createStatement())
               {
                  command.execute(statement);
               }
            }
         }
      }
      scanner = session("operator");
      QContext.init(instance, scanner);
      Map<String, Object> enrolled = MobileService.enroll(Map.of("installationId", UUID.randomUUID().toString()));
      device = (String) enrolled.get("deviceId");
      QContext.init(instance, session("administrator").withPermission(MobilePermissions.ADMIN));
      MobileService.setApproval(device, true);
      bind();
   }



   /***************************************************************************
    ** Mirror the route's verified request attribute before process permission.
    ***************************************************************************/
   public void bind()
   {
      QContext.init(instance, scanner);
      MobilePermissions.REQUEST_DEVICE.set(device);
   }



   /***************************************************************************
    ** Preserve the same device with a different authenticated operator.
    ***************************************************************************/
   public void otherOperator()
   {
      QContext.init(instance, session("other-operator"));
      MobilePermissions.REQUEST_DEVICE.set(device);
   }



   /***************************************************************************
    ** Test cleanup removes both public and private request context.
    ***************************************************************************/
   public static void clear()
   {
      MobilePermissions.REQUEST_DEVICE.remove();
      QContext.clear();
   }



   /***************************************************************************
    ** No printer administration grant is present in the scanner fixture.
    ***************************************************************************/
   private static QSession session(String user)
   {
      QSession session = new QSession().withUser(new QUser().withIdReference(user)).withPermissions(MobilePermissions.SCAN, "echo.use");
      session.setUuid(UUID.randomUUID().toString());
      return session;
   }
}
