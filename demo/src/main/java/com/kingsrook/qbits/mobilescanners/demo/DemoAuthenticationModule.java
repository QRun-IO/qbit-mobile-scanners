/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners.demo;


import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.TableBasedAuthenticationModule;


/*******************************************************************************
 ** Local demo roles layered onto QQQ's password and session validation.
 *******************************************************************************/
public class DemoAuthenticationModule extends TableBasedAuthenticationModule
{
   /*******************************************************************************
    ** Identity comes from QQQ's validated user record, never request role fields.
    *******************************************************************************/
   @Override
   public QSession createSession(QInstance instance, Map<String, String> context) throws QAuthenticationException
   {
      return grantDemoPermissions(super.createSession(instance, context));
   }



   /*******************************************************************************
    ** Native actions may revalidate a session independently of the HTTP handler.
    *******************************************************************************/
   @Override
   public boolean isSessionValid(QInstance instance, QSession session)
   {
      boolean valid = super.isSessionValid(instance, session);
      if(valid)
      {
         grantDemoPermissions(session);
      }
      return valid;
   }



   /*******************************************************************************
    ** Two explicit local accounts keep scanner and device administration separate.
    *******************************************************************************/
   private QSession grantDemoPermissions(QSession session)
   {
      if(session.getUser() != null)
      {
         String username = session.getUser().getIdReference();
         if("scanner".equals(username) || "admin".equals(username))
         {
            session.withPermission("mobile.scan");
         }
         if("admin".equals(username))
         {
            session.withPermission("mobile.admin");
         }
      }
      return session;
   }
}
