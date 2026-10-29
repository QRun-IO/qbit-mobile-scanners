/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.net.URI;
import java.util.List;
import java.util.Locale;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;


/*******************************************************************************
 ** Storage backend supplied by the host application.
 *******************************************************************************/
public class MobileScannersQBitConfig implements QBitConfig
{
   private String backendName;
   private String displayName = "QQQ Mobile Scanners";
   private String primaryColor = "#1565C0";
   private String logoUrl;

   /***************************************************************************
    ** Public customer name shown by the shared mobile client.
    ***************************************************************************/
   public String getDisplayName()
   {
      return displayName;
   }

   /***************************************************************************
    ** Plain display text only, never markup or control characters.
    ***************************************************************************/
   public MobileScannersQBitConfig withDisplayName(String displayName)
   {
      if(displayName == null || displayName.isBlank() || displayName.length() > 80 || !displayName.equals(displayName.strip())
         || displayName.chars().anyMatch(value -> Character.isISOControl(value) || value == '<' || value == '>'))
      {
         throw new IllegalArgumentException("Mobile displayName must be 1-80 trimmed plain-text characters.");
      }
      this.displayName = displayName;
      return this;
   }

   /***************************************************************************
    ** Opaque RGB theme color; clients choose readable foreground colors.
    ***************************************************************************/
   public String getPrimaryColor()
   {
      return primaryColor;
   }

   /***************************************************************************
    ** Restrict branding to a six-digit hex color, not arbitrary CSS.
    ***************************************************************************/
   public MobileScannersQBitConfig withPrimaryColor(String primaryColor)
   {
      if(primaryColor == null || !primaryColor.matches("#[0-9a-fA-F]{6}"))
      {
         throw new IllegalArgumentException("Mobile primaryColor must use #RRGGBB format.");
      }
      this.primaryColor = primaryColor.toUpperCase(Locale.ROOT);
      return this;
   }

   /***************************************************************************
    ** Optional public logo; no authenticated assets or embedded data.
    ***************************************************************************/
   public String getLogoUrl()
   {
      return logoUrl;
   }

   /***************************************************************************
    ** The client loads an HTTPS image without forwarding QQQ credentials.
    ***************************************************************************/
   public MobileScannersQBitConfig withLogoUrl(String logoUrl)
   {
      if(logoUrl != null)
      {
         URI uri;
         try
         {
            uri = URI.create(logoUrl);
         }
         catch(IllegalArgumentException e)
         {
            throw new IllegalArgumentException("Mobile logoUrl must be a public HTTPS URL.");
         }
         if(logoUrl.length() > 2048 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || uri.getUserInfo() != null || uri.getFragment() != null || uri.getPort() > 65535)
         {
            throw new IllegalArgumentException("Mobile logoUrl must be a public HTTPS URL without credentials or a fragment.");
         }
      }
      this.logoUrl = logoUrl;
      return this;
   }

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
