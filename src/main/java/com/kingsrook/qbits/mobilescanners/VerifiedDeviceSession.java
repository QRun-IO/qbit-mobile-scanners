/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** Server-created per-request session; survives QQQ asynchronous context capture.
 ** No client process value or shared stored session can manufacture this type.
 *******************************************************************************/
final class VerifiedDeviceSession extends QSession
{
   private final String deviceId;

   /***************************************************************************
    ** Preserve all user restrictions while isolating mutable session state.
    ***************************************************************************/
   VerifiedDeviceSession(QSession original, String deviceId) throws CloneNotSupportedException
   {
      QSession copy = original.clone();
      this.deviceId = deviceId;
      setIdReference(copy.getIdReference());
      setUuid(copy.getUuid());
      setUser(copy.getUser());
      setPermissions(copy.getPermissions());
      setSecurityKeyValues(copy.getSecurityKeyValues());
      setBackendVariants(copy.getBackendVariants());
      setValues(copy.getValues());
      setValuesForFrontend(copy.getValuesForFrontend());
      setClientChannel(copy.getClientChannel());
      setCredentialScope(copy.getCredentialScope());
   }

   /***************************************************************************
    ** Identity verified from the HTTP credential, never process input.
    ***************************************************************************/
   String deviceId()
   {
      return deviceId;
   }
}
