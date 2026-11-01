/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Example-owned run/event state. Short database transactions serialize admission;
 ** rendering and print acceptance execute only after their connection is closed.
 ** A crash leaves IN_FLIGHT evidence requiring reconciliation, never auto-reprint.
 *******************************************************************************/
final class EchoStore
{
   private final DataSource dataSource;



   /***************************************************************************
    ** The host owns the data source and migration lifecycle.
    ***************************************************************************/
   EchoStore(DataSource dataSource)
   {
      this.dataSource = Objects.requireNonNull(dataSource);
   }



   /***************************************************************************
    ** One server-generated run binds the operator, sign-in and setup choices.
    ***************************************************************************/
   void begin(String id, MobileDeviceAuthorization.Identity identity) throws QException
   {
      try(Connection connection = dataSource.getConnection())
      {
         write(connection, "INSERT INTO echo_run(id,actorId,deviceId,publicSessionId,printerRecordId,mediaId,state) VALUES (?,?,?,?,?,?,?)",
            id, identity.actorId(), identity.deviceId(), identity.publicSessionId(), "", "", "SETUP");
      }
      catch(SQLException failure)
      {
         throw unavailable();
      }
   }



   /***************************************************************************
    ** Freeze setup under the run lock; replay may confirm but never retarget it.
    ***************************************************************************/
   void select(String id, MobileDeviceAuthorization.Identity identity, EchoDestinations.Destination destination) throws QException
   {
      try(Connection connection = dataSource.getConnection())
      {
         connection.setAutoCommit(false);
         try
         {
            Run current = run(connection, id, identity);
            if("OPEN".equals(current.state()) && current.destination().equals(destination))
            {
               connection.commit();
               return;
            }
            if(!"SETUP".equals(current.state()))
            {
               throw new QException("Echo setup is already complete or closed. Leave Echo to choose again.");
            }
            write(connection, "UPDATE echo_run SET state='OPEN',printerRecordId=?,mediaId=?,mediaRevision=?,widthMicrons=?,heightMicrons=?,dpiX=?,dpiY=? WHERE id=?",
               destination.printerRecordId(), destination.mediaId(), destination.mediaRevision(), Integer.toString(destination.widthMicrons()),
               Integer.toString(destination.heightMicrons()), Integer.toString(destination.dpiX()), Integer.toString(destination.dpiY()), id);
            connection.commit();
         }
         catch(Exception failure)
         {
            rollback(connection, failure);
            if(failure instanceof QException expected)
            {
               throw expected;
            }
            throw unavailable();
         }
      }
      catch(SQLException failure)
      {
         throw unavailable();
      }
   }



   /***************************************************************************
    ** Admit one event per run; same-event accepted retries return their receipt.
    ** Different payloads, concurrent callbacks and other operators cannot reuse it.
    ***************************************************************************/
   Admission claim(String runId, MobileDeviceAuthorization.Identity identity, EchoService.Scan scan) throws QException
   {
      try(Connection connection = dataSource.getConnection())
      {
         connection.setAutoCommit(false);
         try
         {
            Run run = run(connection, runId, identity);
            if(!"OPEN".equals(run.state()))
            {
               throw new QException("This Echo run is closed. Open Echo again to choose a printer.");
            }
            String id = scan.mobileScanId();
            String acceptedJob = null;
            boolean exists = false;
            EchoDestinations.Plan pinned = null;
            try(PreparedStatement query = connection.prepareStatement("SELECT * FROM echo_scan WHERE id=?"))
            {
               query.setString(1, id);
               try(ResultSet row = query.executeQuery())
               {
                  if(row.next())
                  {
                     if(!runId.equals(row.getString("runId")))
                     {
                        throw new QException("This scan already belongs to another Echo run. Use a new physical scan event for another label.");
                     }
                     exists = true;
                     if(row.getObject("templateId") != null && row.getObject("templateRevisionId") != null && row.getString("format") != null)
                     {
                        pinned = new EchoDestinations.Plan(row.getInt("templateId"), row.getInt("templateRevisionId"),
                           new com.kingsrook.qbits.documentgeneration.GenerationPorts.Stock(run.destination().widthMicrons(), run.destination().heightMicrons(),
                              row.getInt("topMicrons"), row.getInt("rightMicrons"), row.getInt("bottomMicrons"), row.getInt("leftMicrons"), run.destination().dpiX()),
                           com.kingsrook.qbits.documentgeneration.GenerationPorts.Format.valueOf(row.getString("format")));
                     }
                     if(!scan.barcode().equals(row.getString("barcode")) || !scan.symbology().equals(row.getString("symbology"))
                        || !scan.scanSource().equals(row.getString("scanSource")) || !scan.capturedAt().equals(row.getString("capturedAt"))
                        || !scan.mobileScanId().equals(row.getString("mobileScanId")) || !scan.receivedAt().equals(row.getString("receivedAt")))
                     {
                        throw new QException("This scan event was already used for different barcode context.");
                     }
                     if("QUEUED".equals(row.getString("state")))
                     {
                        acceptedJob = row.getString("printJobId");
                     }
                  }
               }
            }
            EchoDestinations.Destination destination = run.destination();
            if(acceptedJob != null)
            {
               connection.commit();
               return new Admission(id, destination, acceptedJob, false, pinned);
            }
            if(run.pending() != null && !run.pending().equals(scan.eventId()))
            {
               throw new QException("The previous print is unconfirmed. Retry that scan or leave Echo before starting another.");
            }
            if(exists)
            {
               connection.commit();
               return new Admission(id, destination, null, true, pinned);
            }
            else
            {
               write(connection, "INSERT INTO echo_scan(id,runId,eventId,actorId,deviceId,barcode,symbology,rendererSymbology,scanSource,capturedAt,mobileScanId,receivedAt,state) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                  id, runId, scan.eventId(), identity.actorId(), identity.deviceId(), scan.barcode(), scan.symbology(), scan.rendererSymbology(), scan.scanSource(), scan.capturedAt(), scan.mobileScanId(), scan.receivedAt(), "IN_FLIGHT");
            }
            write(connection, "UPDATE echo_run SET pendingEventId=? WHERE id=?", scan.eventId(), runId);
            connection.commit();
            return new Admission(id, destination, null, false, null);
         }
         catch(Exception failure)
         {
            rollback(connection, failure);
            if(failure instanceof QException expected)
            {
               throw expected;
            }
            throw unavailable();
         }
      }
      catch(SQLException failure)
      {
         throw unavailable();
      }
   }



   /***************************************************************************
    ** Persist exact rendering intent before external work can accept a job.
    ***************************************************************************/
   void pin(String scanId, EchoDestinations.Plan plan) throws QException
   {
      try(Connection connection = dataSource.getConnection())
      {
         write(connection, "UPDATE echo_scan SET templateId=?,templateRevisionId=?,format=?,topMicrons=?,rightMicrons=?,bottomMicrons=?,leftMicrons=? WHERE id=? AND state='IN_FLIGHT' AND templateId IS NULL",
            plan.templateId().toString(), plan.templateRevisionId().toString(), plan.format().name(), plan.stock().topMicrons().toString(),
            plan.stock().rightMicrons().toString(), plan.stock().bottomMicrons().toString(), plan.stock().leftMicrons().toString(), scanId);
      }
      catch(SQLException failure)
      {
         throw unavailable();
      }
   }



   /***************************************************************************
    ** Commit the queue receipt before acknowledging the scanner's event identity.
    ** Closing a run concurrently never reopens it or cancels an accepted job.
    ***************************************************************************/
   void accepted(String runId, String scanId, String eventId, String job) throws QException
   {
      try(Connection connection = dataSource.getConnection())
      {
         connection.setAutoCommit(false);
         try
         {
            write(connection, "UPDATE echo_scan SET state='QUEUED', printJobId=? WHERE id=?", job, scanId);
            write(connection, "UPDATE echo_run SET pendingEventId=NULL WHERE id=? AND pendingEventId=?", runId, eventId);
            connection.commit();
         }
         catch(SQLException failure)
         {
            rollback(connection, failure);
            throw unavailable();
         }
      }
      catch(SQLException failure)
      {
         throw unavailable();
      }
   }



   /***************************************************************************
    ** A failed or lost response keeps its event identity and pending run binding.
    ** No automatic retry, replacement print request or success acknowledgment.
    ***************************************************************************/
   void attention(String scanId) throws QException
   {
      try(Connection connection = dataSource.getConnection())
      {
         write(connection, "UPDATE echo_scan SET state='ATTENTION' WHERE id=? AND state='IN_FLIGHT'", scanId);
      }
      catch(SQLException failure)
      {
         throw unavailable();
      }
   }



   /***************************************************************************
    ** Explicit end stops admission; existing queue receipts and jobs are retained.
    ***************************************************************************/
   void close(String runId, MobileDeviceAuthorization.Identity identity) throws QException
   {
      try(Connection connection = dataSource.getConnection())
      {
         connection.setAutoCommit(false);
         try
         {
            run(connection, runId, identity);
            write(connection, "UPDATE echo_run SET state='CLOSED' WHERE id=?", runId);
            connection.commit();
         }
         catch(Exception failure)
         {
            rollback(connection, failure);
            if(failure instanceof QException expected)
            {
               throw expected;
            }
            throw unavailable();
         }
      }
      catch(SQLException failure)
      {
         throw unavailable();
      }
   }



   /***************************************************************************
    ** Lock and check actual persisted ownership, never process-supplied actors.
    ***************************************************************************/
   private Run run(Connection connection, String id, MobileDeviceAuthorization.Identity identity) throws Exception
   {
      try(PreparedStatement query = connection.prepareStatement("SELECT * FROM echo_run WHERE id=? FOR UPDATE"))
      {
         query.setString(1, id);
         try(ResultSet row = query.executeQuery())
         {
            if(!row.next() || !identity.actorId().equals(row.getString("actorId")) || !identity.deviceId().equals(row.getString("deviceId"))
               || !identity.publicSessionId().equals(row.getString("publicSessionId")))
            {
               throw new QException("This Echo run does not belong to this scanner sign-in.");
            }
            return new Run(row.getString("state"), row.getString("pendingEventId"), new EchoDestinations.Destination(row.getString("printerRecordId"),
               row.getString("mediaId"), row.getString("mediaRevision"), row.getInt("widthMicrons"), row.getInt("heightMicrons"), row.getInt("dpiX"), row.getInt("dpiY")));
         }
      }
   }



   /***************************************************************************
    ** All SQL identifiers are fixed source; external values are bound parameters.
    ***************************************************************************/
   private static void write(Connection connection, String sql, String... values) throws SQLException
   {
      try(PreparedStatement statement = connection.prepareStatement(sql))
      {
         for(int index = 0; index < values.length; index++)
         {
            statement.setString(index + 1, values[index]);
         }
         statement.executeUpdate();
      }
   }



   /***************************************************************************
    ** Preserve the original failure; raw database diagnostics are not user text.
    ***************************************************************************/
   private static void rollback(Connection connection, Exception original)
   {
      try
      {
         connection.rollback();
      }
      catch(SQLException failure)
      {
         original.addSuppressed(failure);
      }
   }



   /***************************************************************************
    ** Never expose SQL payloads, private identifiers or vendor exception text.
    ***************************************************************************/
   private static QException unavailable()
   {
      return new QException("Echo state is unavailable. Do not resubmit as a new scan; reconcile the existing event.");
   }



   /***************************************************************************
    ** State is loaded only under its database admission lock.
    ***************************************************************************/
   private record Run(String state, String pending, EchoDestinations.Destination destination)
   {
   }



   /***************************************************************************
    ** Accepted replay has a job; new/reconciled work does not yet acknowledge.
    ***************************************************************************/
   record Admission(String scanId, EchoDestinations.Destination destination, String acceptedJob, boolean reconciliationOnly, EchoDestinations.Plan pinnedPlan)
   {
   }
}
