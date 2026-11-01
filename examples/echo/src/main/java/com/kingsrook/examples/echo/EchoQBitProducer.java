/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;


/*******************************************************************************
 ** Optional example app composed from ordinary QQQ processes and existing QBits.
 ** Registration never migrates data, publishes templates or emits printer work.
 *******************************************************************************/
public final class EchoQBitProducer
{
   public static final String APP = "echo";
   public static final String PROCESS = "echoPrint";
   public static final String WIDGET = "echoScanner";
   public static final String SCAN_TABLE = "echoScan";
   public static final String PERMISSION = "echo.use";



   /***************************************************************************
    ** Printer/media PVS are owner-supplied; their backend authority is rechecked.
    ***************************************************************************/
   public void produce(QInstance instance, EchoConfig config) throws com.kingsrook.qqq.backend.core.exceptions.QException
   {
      config.validate(instance);
      instance.addQBit(new QBitMetaData().withGroupId("com.kingsrook.examples").withArtifactId("qqq-echo").withVersion("0.1.0-SNAPSHOT").withConfig(config));
      if(instance.getSecurityKeyType("qqq.session.userId") == null)
      {
         instance.addSecurityKeyType(new QSecurityKeyType().withName("qqq.session.userId"));
      }
      QTableMetaData scan = new QTableMetaData().withName(SCAN_TABLE).withLabel("Echo Scans").withBackendName(config.backendName)
         .withBackendDetails(new RDBMSTableBackendDetails().withTableName("echo_scan")).withPrimaryKeyField("id")
         .withRecordSecurityLock(new RecordSecurityLock().withFieldName("actorId").withSecurityKeyType("qqq.session.userId"))
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
            .withCustomPermissionChecker(new QCodeReference(EchoSourceReadPermissions.class)));
      for(String name : List.of("id", "runId", "eventId", "actorId", "deviceId", "mobileScanId", "symbology", "rendererSymbology", "scanSource", "capturedAt", "receivedAt", "state", "printJobId"))
      {
         scan.withField(text(name, 200).withIsEditable(false));
      }
      scan.withField(text("barcode", 4096).withIsEditable(false));
      instance.addTable(scan);
      instance.addWidget(new QWidgetMetaData().withName(WIDGET).withType("composite").withLabel("Echo barcode scanner")
         .withCodeReference(new QCodeReference(EchoWidget.class)).withPermissionRules(rules()));
      instance.addProcess(new QProcessMetaData().withName(PROCESS).withLabel("Echo — scan to print").withPermissionRules(rules())
         .withCancelStep(new QBackendStepMetaData().withName("cancel").withCode(new QCodeReference(EchoSteps.Cancel.class)))
         .withStep(new QBackendStepMetaData().withName("begin").withCode(new QCodeReference(EchoSteps.Begin.class)))
         .withStep(new QFrontendStepMetaData().withName("setup").withLabel("Choose printer and paper or label")
            .withFormField(text("printerId", 200).withLabel("Printer").withIsRequired(true).withPossibleValueSourceName(config.printerPossibleValues))
            .withFormField(text("mediaId", 200).withLabel("Paper or label size").withIsRequired(true)
               .withPossibleValueSourceName(config.mediaPossibleValues).withPossibleValueSourceDependsOn(List.of("printerId")))
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM)))
         .withStep(new QBackendStepMetaData().withName("start").withCode(new QCodeReference(EchoSteps.Start.class)))
         .withStep(new QFrontendStepMetaData().withName("scan").withLabel("Echo — scan to print").withFormat("scanner")
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.WIDGET).withValue("widgetName", WIDGET)))
         .withStep(new QBackendStepMetaData().withName("print").withCode(new QCodeReference(EchoSteps.Print.class))));
      instance.addApp(new QAppMetaData().withName(APP).withLabel("Echo").withPermissionRules(rules())
         .withSection(new QAppSection().withName("echo").withLabel("Scan and print").withProcesses(List.of(PROCESS))));
   }



   /***************************************************************************
    ** No printer admin permission or raw table write grant is required.
    ***************************************************************************/
   private static QPermissionRules rules()
   {
      return new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION).withCustomPermissionChecker(new QCodeReference(EchoPermissions.class));
   }



   /***************************************************************************
    ** Bound identifiers and exact data values in the normal metadata contract.
    ***************************************************************************/
   private static QFieldMetaData text(String name, int maximum)
   {
      return new QFieldMetaData(name, QFieldType.STRING).withMaxLength(maximum);
   }
}
