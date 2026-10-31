/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;


/*******************************************************************************
 ** Ordinary QQQ record, child-list, widget and process metadata for sessions.
 *******************************************************************************/
final class MobileSessionMetadata
{
   /***************************************************************************
    ** Metadata utility.
    ***************************************************************************/
   private MobileSessionMetadata()
   {
   }

   /***************************************************************************
    ** Register public history separately from private authentication references.
    ***************************************************************************/
   static void register(QInstance instance, String backend)
   {
      QTableMetaData sessions = table(MobileScannersQBitProducer.SESSION, "Mobile Sessions", backend, 36);
      for(String field : List.of("deviceId", "userId", "status", "endedBy", "endReason", "appName", "processName", "processInstanceId", "stepName", "screenLabel"))
      {
         sessions.withField(text(field, 200));
      }
      for(String field : List.of("lastAction", "errorCode", "captureBlockedReason", "authCleanupStatus"))
      {
         sessions.withField(text(field, 80));
      }
      sessions.withField(text("traceId", 100));
      for(String field : List.of("startedAt", "lastSeenAt", "endedAt", "authCleanupAt"))
      {
         sessions.withField(new QFieldMetaData(field, QFieldType.DATE_TIME));
      }
      sessions.withField(new QFieldMetaData("foreground", QFieldType.BOOLEAN));
      sessions.withField(new QFieldMetaData("screenAvailable", QFieldType.BOOLEAN));
      sessions.withSection(new QFieldSection("activity", "Session activity", new QIcon("devices"), Tier.T2,
         List.of("id", "deviceId", "userId", "status", "startedAt", "lastSeenAt", "foreground", "appName", "processName", "processInstanceId", "stepName", "screenLabel", "lastAction", "errorCode", "traceId", "endedAt", "endedBy", "endReason", "screenAvailable", "captureBlockedReason", "authCleanupStatus", "authCleanupAt")));
      sessions.withSection(new QFieldSection("screen", new QIcon("screen_share"), Tier.T2).withLabel("Live app screen")
         .withWidgetName(MobileScannersQBitProducer.SESSION_SCREEN));
      instance.addTable(sessions);
      instance.addTable(table(MobileScannersQBitProducer.SESSION_BINDING, "Private Session Bindings", backend, 64)
         .withField(text("sessionId", 36).withIsHidden(true)).withField(text("coreSessionUuid", 200).withIsHidden(true).withIsSensitive(true)));
      instance.addWidget(new QWidgetMetaData().withName(MobileScannersQBitProducer.SESSION_SCREEN).withLabel("Live app screen")
         .withType("block").withCodeReference(new QCodeReference(MobileSessionScreenWidget.class))
         .withDefaultValue("refreshSeconds", 2).withPermissionRules(MobileScannersQBitProducer.rules()));
      QJoinMetaData join = new QJoinMetaData().withName("mobileDeviceSessions").withLeftTable(MobileScannersQBitProducer.DEVICE)
         .withRightTable(MobileScannersQBitProducer.SESSION).withType(JoinType.ONE_TO_MANY).withJoinOn(new JoinOn("id", "deviceId"));
      instance.addJoin(join);
      instance.addWidget(ChildRecordListRenderer.widgetMetaDataBuilder(join).withName("mobileDeviceSessions")
         .withLabel("Device Sessions").withCanAddChildRecord(false).getWidgetMetaData()
         .withDefaultValue("orderBy", new ArrayList<>(List.of(new QFilterOrderBy("startedAt", false))))
         .withDefaultValue("refreshSeconds", 2).withPermissionRules(MobileScannersQBitProducer.rules()));
      var device = instance.getTable(MobileScannersQBitProducer.DEVICE);
      List<String> deviceFields = new ArrayList<>(device.getFields().keySet());
      device.withSection(new QFieldSection("device", "Device", new QIcon("devices"), Tier.T2, deviceFields));
      instance.addWidget(new QWidgetMetaData().withName("mobileDeviceStatus").withLabel("Device status").withType("block")
         .withCodeReference(new QCodeReference(MobileDeviceStatusWidget.class)).withDefaultValue("refreshSeconds", 2)
         .withPermissionRules(MobileScannersQBitProducer.rules()));
      device.withSection(new QFieldSection("status", new QIcon("sensors"), Tier.T2).withLabel("Live status").withWidgetName("mobileDeviceStatus"));
      device.withSection(new QFieldSection("sessions", new QIcon("history"), Tier.T2).withLabel("Sessions").withWidgetName("mobileDeviceSessions"));
      instance.addProcess(new QProcessMetaData().withName(MobileScannersQBitProducer.SESSION_END).withLabel("End Session")
         .withTableName(MobileScannersQBitProducer.SESSION).withMaxInputRecords(100).withPermissionRules(MobileScannersQBitProducer.rules())
         .withStep(new QBackendStepMetaData().withName("prepare").withCode(new QCodeReference(MobileSessionAdminPrepareStep.class)))
         .withStep(new QFrontendStepMetaData().withName("input").withLabel("Sessions")
            .withViewField(text("selectionSummary", 30000).withLabel("Sessions to end"))
            .withFormField(text("sessionId", 3700).withLabel("Session IDs").withIsRequired(true))
            .withFormField(text("reason", 200).withLabel("Reason"))
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.VIEW_FORM))
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM)))
         .withStep(new QBackendStepMetaData().withName("apply").withCode(new QCodeReference(MobileSessionAdminStep.class)))
         .withStep(new QFrontendStepMetaData().withName("result").withLabel("Result")
            .withViewField(text("result", 200).withLabel("Result"))
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.VIEW_FORM))));
   }

   /***************************************************************************
    ** Guard native table reads and deny generic mutations.
    ***************************************************************************/
   private static QTableMetaData table(String name, String label, String backend, int idLength)
   {
      return new QTableMetaData().withName(name).withLabel(label).withBackendName(backend).withPrimaryKeyField("id")
         .withField(text("id", idLength).withIsRequired(true)).withPermissionRules(MobileScannersQBitProducer.rules());
   }

   /***************************************************************************
    ** Bounded semantic text fields.
    ***************************************************************************/
   private static QFieldMetaData text(String name, int maximum)
   {
      return new QFieldMetaData(name, QFieldType.STRING).withMaxLength(maximum);
   }
}
