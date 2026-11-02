/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;


/*******************************************************************************
 ** Trusted host wiring. There are no fallback printers, fabricated media or
 ** in-process renderers when owner capabilities are unavailable.
 *******************************************************************************/
public final class EchoConfig implements QBitConfig
{
   final String backendName;
   final String printerPossibleValues;
   final String mediaPossibleValues;
   final EchoService service;
   final EchoDestinations destinations;
   final IdentitySource identities;



   /***************************************************************************
    ** Production always captures the mobile QBit's current verified identity.
    ***************************************************************************/
   public EchoConfig(String backendName, DataSource dataSource, String printerPossibleValues, String mediaPossibleValues,
      EchoDestinations destinations, EchoGenerator generator)
   {
      this(backendName, dataSource, printerPossibleValues, mediaPossibleValues, destinations, generator,
         () -> MobileDeviceAuthorization.captureCurrent().orElseThrow(() -> new QException("Echo requires an approved scanner.")));
   }



   /***************************************************************************
    ** Package-private fixtures exercise ordinary process/persistence behavior;
    ** production callers cannot replace the scanner authorization boundary.
    ***************************************************************************/
   EchoConfig(String backendName, DataSource dataSource, String printerPossibleValues, String mediaPossibleValues,
      EchoDestinations destinations, EchoGenerator generator, IdentitySource identities)
   {
      this.backendName = Objects.requireNonNull(backendName);
      this.printerPossibleValues = Objects.requireNonNull(printerPossibleValues);
      this.mediaPossibleValues = Objects.requireNonNull(mediaPossibleValues);
      this.identities = Objects.requireNonNull(identities);
      this.destinations = Objects.requireNonNull(destinations);
      service = new EchoService(dataSource, destinations, generator, identities);
   }



   /***************************************************************************
    ** Validate composition without changing a database or contacting a printer.
    ***************************************************************************/
   @Override
   public void validate(QInstance instance, List<String> errors)
   {
      if(instance.getBackend(backendName) == null || !Boolean.TRUE.equals(instance.getEnforcePermissionsInCoreActions()))
      {
         errors.add("Echo requires its configured backend and enforced core permissions.");
      }
      if(!hasChoices(instance, printerPossibleValues) || !hasChoices(instance, mediaPossibleValues))
      {
         errors.add("Echo requires authorized printer and supported-media possible-value sources.");
      }
   }



   /***************************************************************************
    ** A table's implicit possible-value source exists once the table is registered.
    ***************************************************************************/
   private static boolean hasChoices(QInstance instance, String name)
   {
      return instance.getPossibleValueSource(name) != null || instance.getTable(name) != null;
   }



   /***************************************************************************
    ** Resolve exactly one installed host configuration, never client values.
    ***************************************************************************/
   static EchoConfig current() throws QException
   {
      return QContext.getQInstance().getQBits().values().stream().map(bit -> bit.getConfig()).filter(EchoConfig.class::isInstance)
         .map(EchoConfig.class::cast).findFirst().orElseThrow(() -> new QException("Echo is not configured."));
   }



   /***************************************************************************
    ** Identity is captured before async execution and checked again per action.
    ***************************************************************************/
   @FunctionalInterface
   interface IdentitySource
   {
      /***********************************************************************
       ** Return the current server-authorized scanner identity.
       ***********************************************************************/
      MobileDeviceAuthorization.Identity current() throws QException;
   }
}
