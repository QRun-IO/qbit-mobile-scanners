/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners.recordfiles;


import java.util.Optional;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qbits.recordfiles.api.CaptureIdentity;
import com.kingsrook.qbits.recordfiles.api.CaptureIdentityProvider;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;


/*******************************************************************************
 ** Optional integration: both qbits remain independent of one another.
 *******************************************************************************/
public final class MobileCaptureIdentityProvider implements CaptureIdentityProvider
{
   public static final String PROVIDER_KEY = "qqq-mobile-scanners/v1";

   /***************************************************************************
    ** Attribution comes exclusively from the scanner's verified server context.
    ***************************************************************************/
   @Override
   public Optional<CaptureIdentity> capture() throws QException
   {
      return MobileDeviceAuthorization.captureCurrent().map(identity -> new CaptureIdentity(PROVIDER_KEY,
         identity.actorId(), identity.deviceId(), identity.publicSessionId()));
   }

   /***************************************************************************
    ** Persisted attribution cannot confer authority or select another sign-in.
    ***************************************************************************/
   @Override
   public void revalidate(CaptureIdentity identity) throws QException
   {
      if(identity == null || !PROVIDER_KEY.equals(identity.providerKey()))
      {
         throw new QPermissionDeniedException("Unsupported mobile capture identity.");
      }
      MobileDeviceAuthorization.revalidate(new MobileDeviceAuthorization.Identity(identity.actorId(), identity.deviceId(), identity.publicSessionId()));
   }
}
