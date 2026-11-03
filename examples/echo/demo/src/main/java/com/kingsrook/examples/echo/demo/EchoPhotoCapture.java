/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.kingsrook.examples.echo.EchoQBitProducer;
import com.kingsrook.qbits.collaboration.security.ParentRecordAccess;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qbits.mobilescanners.recordfiles.MobileCaptureIdentityProvider;
import com.kingsrook.qbits.mobilescanners.recordfiles.RecordFilesCaptureWidgetData;
import com.kingsrook.qbits.recordfiles.ingestion.UploadChunkStorage;
import com.kingsrook.qbits.recordfiles.ingestion.UploadConfiguration;
import com.kingsrook.qbits.recordfiles.ingestion.UploadManifest;
import com.kingsrook.qbits.recordfiles.ingestion.UploadQuota;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.values.QCustomPossibleValueProvider;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;


/*******************************************************************************
 ** Composes the existing mobile Record Files integration into the Echo demo: one
 ** ordinary QQQ process lets an approved scanner choose one of its own retained
 ** Echo scans and then shows the native photo capture control for that record.
 **
 ** The parent is never taken from the client. The table is the fixed Echo scan
 ** table; the record is chosen from a server-built list and re-read through the
 ** owner's visibility check (record security lock, attach feature) before the
 ** widget data is built from the record that check returned. Record Files then
 ** authorizes that parent independently on every upload operation. This adds no
 ** endpoint, storage, transport or authentication of its own.
 *******************************************************************************/
public final class EchoPhotoCapture
{
   public static final String PROCESS = "echoScanPhotos";
   public static final String WIDGET = "echoScanPhotos";
   public static final String CHOICES = "echoScanPhotoChoice";
   public static final String SELECTION_FIELD = "echoScanId";
   static final String TITLE = "Echo scan photos";
   static final String INSTRUCTIONS = "Capture or choose a photo for the selected Echo scan.";
   static final int MAXIMUM_CHOICES = 50;
   private static final int MAXIMUM_ID = 64;
   private static final long MAXIMUM_FILE_BYTES = 20L * 1024 * 1024;



   /***************************************************************************
    ** Namespace only.
    ***************************************************************************/
   private EchoPhotoCapture()
   {
   }



   /***************************************************************************
    ** Immutable server policy: images only, bounded size and count, and the
    ** scanner's verified device/session as the capture identity authority. The
    ** private staging store carries the chunks.
    ***************************************************************************/
   static UploadConfiguration uploadConfiguration()
   {
      return new UploadConfiguration(LocalStagingChunkStorage.ALIAS,
         new UploadManifest.Limits(MAXIMUM_FILE_BYTES, 2 * MAXIMUM_FILE_BYTES, 4, 262144, 600, Set.of("image/jpeg", "image/png")),
         new UploadQuota.Policy(10, 4 * MAXIMUM_FILE_BYTES, 100), new QCodeReference(MobileCaptureIdentityProvider.class))
      {
         /*********************************************************************
          ** This host's private directory instead of an object-storage bucket.
          *********************************************************************/
         @Override
         public UploadChunkStorage createStorage()
         {
            return new LocalStagingChunkStorage();
         }
      };
   }



   /***************************************************************************
    ** Register the choice source, the guarded widget and the process, and list
    ** the process in the existing Echo app. Run after Echo is installed.
    ***************************************************************************/
   static void register(QInstance instance) throws QException
   {
      QAppMetaData app = instance.getApp(EchoQBitProducer.APP);
      if(app == null || app.getSections() == null || app.getSections().isEmpty() || instance.getTable(EchoQBitProducer.SCAN_TABLE) == null)
      {
         throw new QException("The Echo app and scan table must be installed before the photo process.");
      }
      instance.addPossibleValueSource(new QPossibleValueSource().withName(CHOICES).withType(QPossibleValueSourceType.CUSTOM)
         .withIdType(QFieldType.STRING).withCustomCodeReference(new QCodeReference(Choices.class)));
      instance.addWidget(new QWidgetMetaData().withName(WIDGET).withType("composite").withLabel(TITLE)
         .withCodeReference(new QCodeReference(Widget.class)).withPermissionRules(rules()));
      instance.addProcess(new QProcessMetaData().withName(PROCESS).withLabel(TITLE).withPermissionRules(rules())
         .withStep(new QFrontendStepMetaData().withName("select").withLabel("Choose an Echo scan")
            .withFormField(new QFieldMetaData(SELECTION_FIELD, QFieldType.STRING).withLabel("Echo scan").withIsRequired(true)
               .withMaxLength(MAXIMUM_ID).withPossibleValueSourceName(CHOICES))
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM)))
         .withStep(new QBackendStepMetaData().withName("prepare").withCode(new QCodeReference(Prepare.class)))
         .withStep(new QFrontendStepMetaData().withName("capture").withLabel(TITLE)
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.WIDGET).withValue("widgetName", WIDGET))));
      QAppSection section = app.getSections().getFirst();
      List<String> processes = new ArrayList<>(section.getProcesses() == null ? List.of() : section.getProcesses());
      if(!processes.contains(PROCESS))
      {
         processes.add(PROCESS);
      }
      section.withProcesses(processes);
   }



   /***************************************************************************
    ** Approved scanner, current Echo access and the attach feature; permission
    ** to see this process never grants a record or an upload.
    ***************************************************************************/
   private static QPermissionRules rules()
   {
      return new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION).withCustomPermissionChecker(new QCodeReference(Permissions.class));
   }



   /***************************************************************************
    ** The only way the widget's parent is built: validated syntax, a record the
    ** current actor may see and attach to, and values taken from that record.
    ***************************************************************************/
   static RecordFilesCaptureWidgetData widgetFor(Serializable requested) throws QException
   {
      String id = requested == null ? null : String.valueOf(requested);
      if(id == null || id.isBlank() || id.length() > MAXIMUM_ID || id.codePoints().anyMatch(Character::isISOControl))
      {
         throw new QBadRequestException("Choose an Echo scan.");
      }
      QRecord parent = ParentRecordAccess.requireVisibleParent(EchoQBitProducer.SCAN_TABLE, id);
      ParentRecordAccess.requireFeature(EchoQBitProducer.SCAN_TABLE, id, "attach");
      return new RecordFilesCaptureWidgetData(EchoQBitProducer.SCAN_TABLE, Objects.requireNonNull(parent.getValueString("id")), TITLE, INSTRUCTIONS);
   }



   /***************************************************************************
    ** Approved device (the verified scanner session) plus Echo and attach grants.
    ***************************************************************************/
   public static final class Permissions implements CustomPermissionChecker
   {
      /*********************************************************************
       ** Missing, ended or revoked device authority refuses discovery and launch.
       *********************************************************************/
      @Override
      public void checkPermissionsThrowing(AbstractActionInput action, MetaDataWithPermissionRules metadata) throws QPermissionDeniedException
      {
         try
         {
            if(QContext.getQSession() == null || !QContext.getQSession().hasPermission(EchoQBitProducer.PERMISSION)
               || !QContext.getQSession().hasPermission(EchoQBitProducer.SCAN_TABLE + ".attach") || MobileDeviceAuthorization.captureCurrent().isEmpty())
            {
               throw new QPermissionDeniedException("An approved scanner with Echo access is required.");
            }
         }
         catch(QPermissionDeniedException denied)
         {
            throw denied;
         }
         catch(Exception failure)
         {
            throw new QPermissionDeniedException("An approved scanner with Echo access is required.");
         }
      }
   }



   /***************************************************************************
    ** The current actor's own retained Echo scans, newest first. The record
    ** security lock decides visibility; nothing here widens it.
    ***************************************************************************/
   public static final class Choices implements QCustomPossibleValueProvider<String>
   {
      /*********************************************************************
       ** Resolve one selected id back to its label, if it is still visible.
       *********************************************************************/
      @Override
      public QPossibleValue<String> getPossibleValue(Serializable id) throws QException
      {
         List<QPossibleValue<String>> found = select(new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, String.valueOf(id))), 1);
         return found.isEmpty() ? null : found.getFirst();
      }



      /*********************************************************************
       ** Bounded exact-id or plain-text search over this actor's scans.
       *********************************************************************/
      @Override
      public List<QPossibleValue<String>> search(SearchPossibleValueSourceInput input) throws QException
      {
         int limit = Math.max(1, Math.min(MAXIMUM_CHOICES, Objects.requireNonNullElse(input.getLimit(), MAXIMUM_CHOICES)));
         if(input.getIdList() != null)
         {
            if(input.getIdList().size() > MAXIMUM_CHOICES)
            {
               throw new QBadRequestException("Too many Echo scans requested.");
            }
            return select(new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, new ArrayList<Serializable>(input.getIdList()))), limit);
         }
         String term = Objects.requireNonNullElse(input.getSearchTerm(), "");
         if(term.length() > 128)
         {
            throw new QBadRequestException("Narrow the Echo scan search.");
         }
         QQueryFilter filter = new QQueryFilter();
         if(!term.isBlank())
         {
            filter.withCriteria(new QFilterCriteria("barcode", QCriteriaOperator.LIKE, "%" + term + "%"));
         }
         filter.withSkip(Math.max(0, Objects.requireNonNullElse(input.getSkip(), 0)));
         return select(filter, limit);
      }



      /*********************************************************************
       ** Query under the caller's session so the table's own lock and read rule apply.
       *********************************************************************/
      private static List<QPossibleValue<String>> select(QQueryFilter filter, int limit) throws QException
      {
         filter.withLimit(limit).withOrderBy(new QFilterOrderBy("receivedAt", false)).withOrderBy(new QFilterOrderBy("id", false));
         List<QPossibleValue<String>> choices = new ArrayList<>();
         for(QRecord record : new QueryAction().execute(new QueryInput(EchoQBitProducer.SCAN_TABLE).withFilter(filter)).getRecords())
         {
            choices.add(new QPossibleValue<>(record.getValueString("id"), label(record)));
         }
         return choices;
      }
   }



   /***************************************************************************
    ** A short, single-line label; control characters are not echoed.
    ***************************************************************************/
   static String label(QRecord record)
   {
      String barcode = Objects.requireNonNullElse(record.getValueString("barcode"), "").replaceAll("\\p{Cntrl}", "?");
      String shown = barcode.length() > 60 ? barcode.substring(0, 60) + "…" : barcode;
      return shown + " (" + Objects.requireNonNullElse(record.getValueString("symbology"), "unknown") + ")";
   }



   /***************************************************************************
    ** Build the widget values on the server from the validated selection. The
    ** frontend step never receives a client-supplied table or record reference.
    ***************************************************************************/
   public static final class Prepare implements BackendStep
   {
      /*********************************************************************
       ** Refuse an unavailable record before the capture step is shown.
       *********************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         output.addValue(WIDGET, widgetFor(input.getValue(SELECTION_FIELD)));
      }
   }



   /***************************************************************************
    ** The process supplies the data. Rendering this widget directly cannot name a
    ** parent, so a forged widget request yields nothing to attach to.
    ***************************************************************************/
   public static final class Widget extends AbstractWidgetRenderer
   {
      /*********************************************************************
       ** Always refused; only the process's prepared values carry a parent.
       *********************************************************************/
      @Override
      public RenderWidgetOutput render(RenderWidgetInput input) throws QException
      {
         throw new QBadRequestException("Open Echo scan photos from its process and choose a scan.");
      }
   }
}
