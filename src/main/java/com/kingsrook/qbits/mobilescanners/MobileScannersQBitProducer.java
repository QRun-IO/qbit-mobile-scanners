/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.List;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitProducer;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Native QQQ tables, processes and mobile scanner screen metadata.
 *******************************************************************************/
public class MobileScannersQBitProducer implements QBitProducer
{
   public static final String DEVICE = "mobileDevice";
   public static final String CREDENTIAL = "mobileDeviceCredential";
   public static final String SCAN = "mobileScan";
   public static final String CAPTURE = "mobileScanCapture";
   public static final String APPROVE = "mobileDeviceApprove";
   public static final String REVOKE = "mobileDeviceRevoke";
   public static final String WIDGET = "mobileScanner";
   public static final String APP = "mobileScanApp";
   public static final String ADMIN_APP = "mobileScannerAdmin";
   private MobileScannersQBitConfig config;

   /***************************************************************************
    ** Configure the storage backend.
    ***************************************************************************/
   public MobileScannersQBitProducer withConfig(MobileScannersQBitConfig config)
   {
      this.config = config;
      return this;
   }

   /***************************************************************************
    ** Install once per QInstance; names are intentionally stable for clients.
    ***************************************************************************/
   @Override
   public void produce(QInstance instance, String namespace) throws QException
   {
      if(config == null || instance.getTable(DEVICE) != null)
      {
         throw new QException("Configure and install Mobile Scanners exactly once per instance.");
      }
      config.validate(instance);
      instance.addQBit(new QBitMetaData().withGroupId("com.kingsrook.qbits").withArtifactId("qbit-mobile-scanners")
         .withVersion("0.1.0-SNAPSHOT").withNamespace(namespace).withConfig(config));
      QTableMetaData device = table(DEVICE, "Mobile Devices", 36);
      for(String field : List.of("installationId", "displayName", "manufacturer", "model", "osVersion", "appVersion", "status", "enrolledBy", "approvedBy"))
      {
         device.withField(text(field, 200));
      }
      device.withField(time("enrolledAt")).withField(time("approvedAt"));
      instance.addTable(device);
      instance.addTable(table(CREDENTIAL, "Device Credentials", 36).withField(text("tokenHash", 64).withIsHidden(true)));
      QTableMetaData scan = table(SCAN, "Barcode Scans", 64);
      for(String field : List.of("deviceId", "eventId", "userId", "appId", "processName", "symbology", "scanSource", "manufacturer", "model", "osVersion", "appVersion"))
      {
         scan.withField(text(field, 200));
      }
      scan.withField(text("barcode", 4096)).withField(time("capturedAt")).withField(time("receivedAt"));
      instance.addTable(scan);
      instance.addWidget(new QWidgetMetaData().withName(WIDGET).withLabel("Scan a barcode").withType("composite")
         .withCodeReference(new QCodeReference(MobileScannerWidget.class)).withPermissionRules(rules()));
      instance.addProcess(new QProcessMetaData().withName(CAPTURE).withLabel("Scan Capture").withPermissionRules(rules())
         .withStep(new QBackendStepMetaData().withName("prepare").withCode(new QCodeReference(MobilePrepareStep.class)))
         .withStep(new QFrontendStepMetaData().withName("scan").withLabel("Scan a barcode").withFormat("scanner")
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.WIDGET).withValue("widgetName", WIDGET)))
         .withStep(new QBackendStepMetaData().withName("save").withCode(new QCodeReference(MobileScanStep.class))));
      for(String process : List.of(APPROVE, REVOKE))
      {
         instance.addProcess(new QProcessMetaData().withName(process).withLabel(process.equals(APPROVE) ? "Approve Device" : "Revoke Device")
            .withTableName(DEVICE).withPermissionRules(rules())
            .withStep(new QFrontendStepMetaData().withName("input").withLabel("Device")
               .withFormField(text("deviceId", 36).withLabel("Device ID").withIsRequired(true))
               .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM)))
            .withStep(new QBackendStepMetaData().withName("apply").withCode(new QCodeReference(MobileDeviceAdminStep.class))));
      }
      instance.addApp(new QAppMetaData().withName(APP).withLabel("Scan Capture").withIcon(new QIcon("qr_code_scanner"))
         .withPermissionRules(rules()).withSection(new QAppSection().withName("scan").withLabel("Scanning").withProcesses(List.of(CAPTURE))));
      instance.addApp(new QAppMetaData().withName(ADMIN_APP).withLabel("Mobile Scanner Administration").withIcon(new QIcon("devices"))
         .withPermissionRules(rules()).withSection(new QAppSection().withName("devices").withLabel("Devices and Scans")
            .withTables(List.of(DEVICE, SCAN)).withProcesses(List.of(APPROVE, REVOKE))));
   }

   /***************************************************************************
    ** Every table action is guarded, including generic UI API mutations.
    ***************************************************************************/
   private QTableMetaData table(String name, String label, int idLength)
   {
      return new QTableMetaData().withName(name).withLabel(label).withBackendName(config.getBackendName())
         .withPrimaryKeyField("id").withField(text("id", idLength).withIsRequired(true)).withPermissionRules(rules());
   }

   /***************************************************************************
    ** Common guarded metadata rules.
    ***************************************************************************/
   public static QPermissionRules rules()
   {
      return new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
         .withCustomPermissionChecker(new QCodeReference(MobilePermissions.class));
   }

   /***************************************************************************
    ** Bound all text before it reaches persistence.
    ***************************************************************************/
   private static QFieldMetaData text(String name, int length)
   {
      return new QFieldMetaData(name, QFieldType.STRING).withMaxLength(length);
   }

   /***************************************************************************
    ** Native timestamp metadata.
    ***************************************************************************/
   private static QFieldMetaData time(String name)
   {
      return new QFieldMetaData(name, QFieldType.DATE_TIME);
   }
}
