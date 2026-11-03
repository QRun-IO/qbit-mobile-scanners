/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.io.Serializable;
import java.util.Optional;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** Optional integrations consume public attribution without accessing credentials.
 ** Identity is a value, never proof: every use must revalidate current authority.
 *******************************************************************************/
public final class MobileDeviceAuthorization
{
   /***************************************************************************
    ** Static authorization boundary.
    ***************************************************************************/
   private MobileDeviceAuthorization()
   {
   }



   /***************************************************************************
    ** Public identifiers only; no authentication UUID or device secret escapes.
    ***************************************************************************/
   public record Identity(String actorId, String deviceId, String publicSessionId)
   {
   }



   /***************************************************************************
    ** Capture only a verified request or its server-created asynchronous session.
    ** An already bound mobile sign-in cannot fall back to desktop attribution.
    ***************************************************************************/
   public static Optional<Identity> captureCurrent() throws QException
   {
      return MobileSessionService.withActiveSession(() ->
      {
         QSession authentication = QContext.getQSession();
         if(authentication == null || authentication.getUser() == null || authentication.getUser().getIdReference() == null
            || authentication.getUuid() == null || authentication.getUuid().isBlank())
         {
            throw new QAuthenticationException("Sign in to continue.");
         }
         Serializable expected = authentication instanceof VerifiedDeviceSession checked ? checked.checkedAuthority() : VerifiedDeviceSession.authorityOf(authentication);
         requireCurrent(authentication, expected);
         QRecord binding = MobileService.get(MobileScannersQBitProducer.SESSION_BINDING, MobileService.hash(authentication.getUuid()));
         String device = MobilePermissions.REQUEST_DEVICE.get();
         if(authentication instanceof VerifiedDeviceSession verified)
         {
            if(device != null && !device.equals(verified.deviceId()))
            {
               throw new QPermissionDeniedException("Device attribution does not match this sign-in.");
            }
            device = verified.deviceId();
         }
         if(device == null)
         {
            String channel = authentication.getClientChannelName();
            if(binding != null || (channel != null && !"web".equals(channel)))
            {
               throw new QPermissionDeniedException("A verified device credential is required.");
            }
            return Optional.empty();
         }
         MobilePermissions.require(MobilePermissions.SCAN);
         MobileService.requireApproved(device);
         QRecord session = MobileSessionService.observe(device);
         binding = MobileService.get(MobileScannersQBitProducer.SESSION_BINDING, MobileService.hash(authentication.getUuid()));
         if(binding == null || !authentication.getUuid().equals(binding.getValueString("coreSessionUuid"))
            || !session.getValueString("id").equals(binding.getValueString("sessionId")))
         {
            throw new QPermissionDeniedException("Device session binding could not be verified.");
         }
         requireCurrent(authentication, expected);
         if(!(authentication instanceof VerifiedDeviceSession))
         {
            try
            {
               QContext.setQSession(new VerifiedDeviceSession(authentication, device));
            }
            catch(CloneNotSupportedException failure)
            {
               throw new QException("Device context could not be retained.", failure);
            }
         }
         return Optional.of(new Identity(MobileService.userId(), device, session.getValueString("id")));
      });
   }



   /***************************************************************************
    ** Approval, observation and host hooks may refresh or replace the session;
    ** only the same session with identical authority may be retained or used.
    ***************************************************************************/
   private static void requireCurrent(QSession authentication, Serializable expected) throws QPermissionDeniedException
   {
      if(QContext.getQSession() != authentication || !expected.equals(VerifiedDeviceSession.authorityOf(authentication)))
      {
         throw new QPermissionDeniedException("Device authorization changed; sign in again.");
      }
   }



   /***************************************************************************
    ** A persisted/public DTO must still match this authenticated private binding.
    ***************************************************************************/
   public static void revalidate(Identity identity) throws QException
   {
      executeAuthorized(identity, () -> null);
   }



   /***************************************************************************
    ** Keep local end/revoke and a protected operation under the same instance lock.
    ** Cross-node atomicity requires a host transactional authorization strategy.
    ***************************************************************************/
   public static <T> T executeAuthorized(Identity identity, Operation<T> operation) throws QException
   {
      if(identity == null)
      {
         throw new QPermissionDeniedException("Device attribution no longer matches this sign-in.");
      }
      return executeAuthorized(Optional.of(identity), operation);
   }



   /***************************************************************************
    ** Empty attribution is allowed only while the authenticated session is unbound.
    ** Retain the same exclusion through the callback's synchronous commits.
    ***************************************************************************/
   public static <T> T executeAuthorized(Optional<Identity> identity, Operation<T> operation) throws QException
   {
      return MobileSessionService.withActiveSession(() ->
      {
         Optional<Identity> current = captureCurrent();
         if(identity == null || !identity.equals(current))
         {
            throw new QPermissionDeniedException("Device attribution no longer matches this sign-in.");
         }
         return operation.run();
      });
   }



   /***************************************************************************
    ** Callback runs while local mobile authorization cannot be changed.
    ***************************************************************************/
   @FunctionalInterface
   public interface Operation<T>
   {
      /***********************************************************************
       ** Perform the authorized operation.
       ***********************************************************************/
      T run() throws QException;
   }
}
