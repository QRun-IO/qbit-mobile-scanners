/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.examples.echo.EchoQBitProducer;
import com.kingsrook.qbits.collaboration.CollaborationSecurity;
import com.kingsrook.qbits.mobilescanners.demo.DemoAuthenticationModule;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** The existing demo password/session validation plus exactly the grants the
 ** owners' source requires. The validated account selects the role, never a
 ** request field. Printing and Record Files administration is granted to no one.
 *******************************************************************************/
public class EchoDemoAuthentication extends DemoAuthenticationModule
{
   /**
    ** Scanner: Echo itself (also the source-row read rule), Printing's operator
    ** catalog and submit, Generation view/generate/print, and Record Files
    ** view (retained reads) and manage (the owner's intake permission), plus the
    ** attachments feature permission on the Echo scan table (Collaboration's
    ** requireFeature for "attach"; the default feature mode is not implied by edit).
    */
   static final List<String> SCANNER = List.of(EchoQBitProducer.PERMISSION, "printing_operator", "document-generation.view",
      "document-generation.generate", "document-generation.print", "record-files.view", "record-files.manage",
      EchoQBitProducer.SCAN_TABLE + ".attach");

   /**
    ** Admin also gets Printing's own administrator permission, which its native
    ** device pairing/approval/setup processes require, and what is needed to
    ** prepare and publish templates. The scanner never receives these.
    */
   static final List<String> ADMIN_ADDITIONS = List.of("printing_admin", "document-generation.configure", "document-generation.publish");

   private static volatile String tenant;



   /***************************************************************************
    ** QQQ instantiates this module by class, so the host sets the Printing
    ** tenant once before the server accepts requests. Printing's rows lock to it.
    ***************************************************************************/
   static void configure(String printingTenant)
   {
      tenant = printingTenant;
   }



   /***************************************************************************
    ** Login keeps the existing validation, then adds the grants.
    ***************************************************************************/
   @Override
   public QSession createSession(QInstance instance, Map<String, String> context) throws QAuthenticationException
   {
      return grant(instance, super.createSession(instance, context));
   }



   /***************************************************************************
    ** Native actions revalidate sessions independently of the HTTP handler.
    ***************************************************************************/
   @Override
   public boolean isSessionValid(QInstance instance, QSession session)
   {
      boolean valid = super.isSessionValid(instance, session);
      if(valid)
      {
         grant(instance, session);
      }
      return valid;
   }



   /***************************************************************************
    ** Only the two seeded demo accounts receive access; only admin configures.
    ** Collaboration's owner key (collaboration_user, or its admin override) is
    ** applied last, after every grant, on every login and revalidation, so it is
    ** in place before the scanner's verified session is first pinned.
    ***************************************************************************/
   static QSession grant(QInstance instance, QSession session)
   {
      String user = session.getUser() == null ? null : session.getUser().getIdReference();
      if(tenant != null && ("scanner".equals(user) || "admin".equals(user)))
      {
         List<String> names = new ArrayList<>(SCANNER);
         if("admin".equals(user))
         {
            names.addAll(ADMIN_ADDITIONS);
         }
         for(String name : names)
         {
            session.withPermission(name);
         }
         // Re-derived from the fixed host tenant on every call: replace, never append, so revalidation
         // cannot grow the key map. All other keys are preserved.
         Map<String, List<Serializable>> keys = session.getSecurityKeyValues() == null ? new HashMap<>() : new HashMap<>(session.getSecurityKeyValues());
         if(!List.of(tenant).equals(keys.get("printing_tenant")))
         {
            keys.put("printing_tenant", new ArrayList<>(List.of(tenant)));
            session.withSecurityKeyValues(keys);
         }
      }
      CollaborationSecurity.applyToSession(instance, session);
      return session;
   }
}
