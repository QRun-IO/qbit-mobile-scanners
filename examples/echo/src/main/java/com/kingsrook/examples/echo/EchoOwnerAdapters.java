/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import javax.sql.DataSource;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationService;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.printing.PrintingTables;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;


/*******************************************************************************
 ** One-call host composition from the Printing and Document Generation owners'
 ** public APIs. Printers use Printing's own table possible values; media uses
 ** the custom authorized catalog source. Nothing here is a static catalog.
 *******************************************************************************/
public final class EchoOwnerAdapters
{
   /***************************************************************************
    ** Static composition only.
    ***************************************************************************/
   private EchoOwnerAdapters()
   {
   }



   /***************************************************************************
    ** Register the media possible values and return the Echo host configuration.
    ** Generation must already allow the echoScan table as a source table.
    ***************************************************************************/
   public static EchoConfig config(QInstance instance, String backendName, DataSource dataSource, DocumentGenerationService generation,
      EchoTemplates templates, GenerationPorts.Format format)
   {
      if(instance.getPossibleValueSource(EchoMediaChoices.NAME) == null)
      {
         instance.addPossibleValueSource(new QPossibleValueSource().withName(EchoMediaChoices.NAME).withType(QPossibleValueSourceType.CUSTOM)
            .withIdType(QFieldType.STRING).withCustomCodeReference(new QCodeReference(EchoMediaChoices.class)));
      }
      return new EchoConfig(backendName, dataSource, PrintingTables.PRINTER, EchoMediaChoices.NAME,
         new EchoPrintingDestinations(templates, format), new EchoDocumentGenerator(generation));
   }
}
