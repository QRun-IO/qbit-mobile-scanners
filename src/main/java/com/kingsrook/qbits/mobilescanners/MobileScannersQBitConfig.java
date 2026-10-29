/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;


/*******************************************************************************
 ** Storage backend supplied by the host application.
 *******************************************************************************/
public class MobileScannersQBitConfig implements QBitConfig
{
   private String backendName;

   /***************************************************************************
    ** Read the configured backend.
    ***************************************************************************/
   public String getBackendName()
   {
      return backendName;
   }

   /***************************************************************************
    ** Configure the host-owned durable backend.
    ***************************************************************************/
   public MobileScannersQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return this;
   }

   /***************************************************************************
    ** Fail startup when the backend is absent.
    ***************************************************************************/
   @Override
   public void validate(QInstance instance, List<String> errors)
   {
      if(backendName == null || instance.getBackend(backendName) == null)
      {
         errors.add("Mobile scanners requires an existing backendName.");
      }
      if(!Boolean.TRUE.equals(instance.getEnforcePermissionsInCoreActions()))
      {
         errors.add("Mobile scanners requires enforcePermissionsInCoreActions=true to protect device credentials and scan records.");
      }
   }
}
